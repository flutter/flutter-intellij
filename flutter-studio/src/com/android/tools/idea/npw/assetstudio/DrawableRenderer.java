/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderProblem;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.parsers.ILayoutPullParserFactory;
import com.android.tools.idea.rendering.parsers.LayoutPsiPullParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightVirtualFile;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * Renders XML drawables to raster images.
 */
public class DrawableRenderer implements Disposable {
  @NotNull private final CompletableFuture<RenderTask> myRenderTaskFuture;
  @NotNull private final Object myRenderLock = new Object();
  @NotNull private final MyLayoutPullParserFactory myParserFactory;
  @NotNull private final AtomicInteger myCounter = new AtomicInteger();

  /**
   * Initializes the renderer. Every renderer has to be disposed by calling {@link #dispose()}.
   * Please keep in mind that each renderer instance allocates significant resources inside Layoutlib.
   *
   * @param facet the Android facet
   */
  public DrawableRenderer(@NotNull AndroidFacet facet) {
    this(facet, ThemeEditorUtils.getConfigurationForModule(facet.getModule()));
  }

  /**
   * Initializes the renderer. Every renderer has to be disposed by calling {@link #dispose()}.
   * Please keep in mind that each renderer instance allocates significant resources inside Layoutlib.
   *
   * @param facet the Android facet
   * @param configuration the configuration to use for rendering
   */
  public DrawableRenderer(@NotNull AndroidFacet facet, @NotNull Configuration configuration) {
    Module module = facet.getModule();
    RenderLogger logger = new RenderLogger(LauncherIconGenerator.class.getSimpleName(), module);
    myParserFactory = new MyLayoutPullParserFactory(module.getProject(), logger);
    // The ThemeEditorUtils.getConfigurationForModule and RenderService.createTask calls are pretty expensive.
    // Executing them off the UI thread.
    myRenderTaskFuture = CompletableFuture.supplyAsync(() -> {
      try {
        RenderService service = RenderService.getInstance(module.getProject());
        RenderTask renderTask = service.taskBuilder(facet, configuration)
          .withLogger(logger)
          .withParserFactory(myParserFactory)
          .buildSynchronously();
        assert renderTask != null;
        renderTask.getLayoutlibCallback().setLogger(logger);
        if (logger.hasProblems()) {
          getLog().error(RenderProblem.format(logger.getMessages()));
        }
        return renderTask;
      } catch (RuntimeException | Error e) {
        getLog().error(e);
        return null;
      }
    }, PooledThreadExecutor.INSTANCE);
  }

  /**
   * Produces a raster image for the given XML drawable.
   *
   * @param xmlDrawableText the text of the XML drawable
   * @param size the size of the produced raster image
   * @return the rendering of the drawable in form of a future
   */
  @NotNull
  public CompletableFuture<BufferedImage> renderDrawable(@NotNull String xmlDrawableText, @NotNull Dimension size) {
    String xmlText = VectorDrawableTransformer.transform(xmlDrawableText, size, 1, null, null, 1);
    String resourceName = String.format("preview_%x.xml", myCounter.getAndIncrement());
    ResourceValue value = new ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "ic_image_preview",
                                                "file://" + resourceName);

    return myRenderTaskFuture.thenCompose(renderTask -> {
      if (renderTask == null) {
        return CompletableFuture.completedFuture(AssetStudioUtils.createDummyImage());
      }

      synchronized (myRenderLock) {
        myParserFactory.addFileContent(new PathString(resourceName), xmlText);
        renderTask.setOverrideRenderSize(size.width, size.height);
        renderTask.setMaxRenderSize(size.width, size.height);

        return renderTask.renderDrawable(value);
      }
    });
  }

  @Override
  public void dispose() {
    myRenderTaskFuture.whenComplete((renderTask, throwable) -> {
      if (renderTask != null) {
        synchronized (myRenderLock) {
          renderTask.dispose();
        }
      }
    });
  }

  private static class MyLayoutPullParserFactory implements ILayoutPullParserFactory {
    @NotNull private final ConcurrentMap<PathString, String> myFileContent = new ConcurrentHashMap<>();
    @NotNull private final Project myProject;
    @NotNull private final RenderLogger myLogger;

    MyLayoutPullParserFactory(@NotNull Project project, @NotNull RenderLogger logger) {
      myProject = project;
      myLogger = logger;
    }

    @Override
    @Nullable
    public ILayoutPullParser create(@NotNull PathString file, @NotNull LayoutlibCallback layoutlibCallback) {
      String content = myFileContent.remove(file); // File contents is removed upon use to avoid leaking memory.
      if (content == null) {
        return null;
      }

      XmlFile xmlFile = (XmlFile)createEphemeralPsiFile(myProject, file.getFileName(), StdFileTypes.XML, content);
      return LayoutPsiPullParser.create(xmlFile, myLogger);
    }

    void addFileContent(@NotNull PathString file, @NotNull String content) {
      myFileContent.put(file, content);
    }

    /**
     * Creates a PsiFile with the given name and contents corresponding to the given language without storing it on disk.
     *
     * @param project the project to associate the file with
     * @param filename path relative to a source root
     * @param fileType the type of the file
     * @param contents the content of the file
     * @return the created ephemeral file
     */
    @NotNull
    private static PsiFile createEphemeralPsiFile(@NotNull Project project, @NotNull String filename, @NotNull LanguageFileType fileType,
                                                  @NotNull String contents) {
      PsiManager psiManager = PsiManager.getInstance(project);
      VirtualFile virtualFile = new LightVirtualFile(filename, fileType, contents);
      SingleRootFileViewProvider viewProvider = new SingleRootFileViewProvider(psiManager, virtualFile);
      PsiFile psiFile = viewProvider.getPsi(fileType.getLanguage());
      if (psiFile == null) {
        throw new IllegalArgumentException("Unsupported language: " + fileType.getLanguage().getDisplayName());
      }
      return psiFile;
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(DrawableRenderer.class);
  }
}
