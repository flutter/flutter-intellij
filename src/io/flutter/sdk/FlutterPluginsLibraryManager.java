/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileContentsChangedAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the Flutter Plugins library, which hooks the packages used by plugins referenced in a project
 * into the project, so full editing support is available.
 *
 * @see FlutterPluginLibraryType
 * @see FlutterPluginLibraryProperties
 */
public class FlutterPluginsLibraryManager extends AbstractLibraryManager<FlutterPluginLibraryProperties> {

  private final AtomicBoolean isUpdating = new AtomicBoolean(false);

  public FlutterPluginsLibraryManager(@NotNull Project project) {
    super(project);
  }

  public void startWatching() {
    var project = getProject();
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileContentsChangedAdapter() {
      @Override
      protected void onFileChange(@NotNull VirtualFile file) {
        fileChanged(getProject(), file);
      }

      @Override
      protected void onBeforeFileChange(@NotNull VirtualFile file) {
      }
    }, FlutterDartAnalysisServer.getInstance(project));

    project.getMessageBus().connect().subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        scheduleUpdate();
      }
    });

    scheduleUpdate();
  }

  @Override
  @NotNull
  protected String getLibraryName() {
    return FlutterPluginLibraryType.FLUTTER_PLUGINS_LIBRARY_NAME;
  }

  @Override
  @NotNull
  protected PersistentLibraryKind<FlutterPluginLibraryProperties> getLibraryKind() {
    return FlutterPluginLibraryType.LIBRARY_KIND;
  }

  private void fileChanged(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (LocalFileSystem.getInstance() != file.getFileSystem() && !ApplicationManager.getApplication().isUnitTestMode()) return;

    scheduleUpdate();
  }

  private void scheduleUpdate() {
    if (isUpdating.get()) {
      return;
    }

    final Runnable runnable = this::updateFlutterPlugins;
    DumbService.getInstance(getProject()).smartInvokeLater(runnable, ModalityState.nonModal());
  }

  private void updateFlutterPlugins() {
    if (!isUpdating.compareAndSet(false, true)) {
      return;
    }

    try {
      updateFlutterPluginsImpl();
    }
    finally {
      isUpdating.set(false);
    }
  }

  private void updateFlutterPluginsImpl() {
    Project project = getProject();

    ReadAction.nonBlocking(() -> getFlutterPluginPaths(PubRoots.forProject(project)))
      .expireWith(FlutterDartAnalysisServer.getInstance(project))
      .finishOnUiThread(ModalityState.nonModal(), flutterPluginPaths -> {
        final Set<String> flutterPluginUrls = new HashSet<>();
        for (String path : flutterPluginPaths) {
          flutterPluginUrls.add(VfsUtilCore.pathToUrl(path));
        }
        updateLibraryContent(flutterPluginUrls);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static @NotNull Set<@NotNull String> getFlutterPluginPaths(@NotNull List<@NotNull PubRoot> roots) {
    final Set<String> paths = new HashSet<>();

    for (PubRoot pubRoot : roots) {
      final var packagesMap = pubRoot.getPackagesMap();
      if (packagesMap == null) {
        continue;
      }

      for (String packagePath : packagesMap.values()) {
        if (packagePath == null) continue;
        final VirtualFile libFolder = LocalFileSystem.getInstance().findFileByPath(packagePath);
        if (libFolder == null) {
          continue;
        }
        final PubRoot pluginRoot = PubRoot.forDirectory(libFolder.getParent());
        if (pluginRoot == null) {
          continue;
        }

        if (pluginRoot.isFlutterPlugin()) {
          paths.add(pluginRoot.getPath());
        }
      }
    }

    return paths;
  }
}
