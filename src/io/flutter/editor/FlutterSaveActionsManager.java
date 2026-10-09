/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PsiErrorElementUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A manager class to run actions on save (formatting, organize imports, ...).
 */
public class FlutterSaveActionsManager {

  /**
   * Initialize the save actions manager for the given project.
   */
  public static void init(@NotNull Project project) {
    // Call getInstance() will init FlutterSaveActionsManager for the given project by calling the constructor below.
    getInstance(project);
  }

  @Nullable
  public static FlutterSaveActionsManager getInstance(@NotNull Project project) {
    return project.getService(FlutterSaveActionsManager.class);
  }

  private final @NotNull Project myProject;
  private final @NotNull Set<Document> myPendingDocuments = Collections.newSetFromMap(new WeakHashMap<>());
  private final @NotNull Set<Document> mySavingDocuments = Collections.newSetFromMap(new WeakHashMap<>());

  public FlutterSaveActionsManager(@NotNull Project project) {
    this(project, project);
  }

  @VisibleForTesting
  FlutterSaveActionsManager(@NotNull Project project, @NotNull Disposable parentDisposable) {
    this.myProject = project;

    final MessageBus bus = project.getMessageBus();
    final MessageBusConnection connection = bus.connect(parentDisposable);
    connection.subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        // Don't try and format read only docs.
        if (!document.isWritable()) {
          return;
        }

        handleBeforeDocumentSaving(document);
      }
    });
  }

  @VisibleForTesting
  void handleBeforeDocumentSaving(@NotNull Document document) {
    if (mySavingDocuments.contains(document) || myPendingDocuments.contains(document)) {
      return;
    }

    final FlutterSettings settings = FlutterSettings.getInstance();
    if (!settings.isFormatCodeOnSave() || !myProject.isInitialized() || myProject.isDisposed()) {
      return;
    }

    final PsiFile psiFile = getFormattablePsiFile(document);
    if (psiFile == null) {
      return;
    }

    scheduleFormatAndSave(document, psiFile, settings.isOrganizeImportsOnSave());
  }

  @Nullable
  private PsiFile getFormattablePsiFile(@NotNull Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null || !FlutterUtils.isDartFile(file)) {
      return null;
    }

    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null || !psiFile.isValid()) {
      return null;
    }

    final Module module = ModuleUtil.findModuleForFile(file, myProject);
    if (module == null || !isDartSdkEnabled(module) || hasSyntaxErrors(psiFile, file)) {
      return null;
    }

    return psiFile;
  }

  private void scheduleFormatAndSave(@NotNull Document document, @NotNull PsiFile psiFile, boolean organizeImports) {
    final VirtualFile file = psiFile.getVirtualFile();
    myPendingDocuments.add(document);
    OpenApiUtils.safeInvokeLater(() -> {
      myPendingDocuments.remove(document);
      if (myProject.isDisposed() || !psiFile.isValid() || file == null || !file.isValid()) {
        return;
      }
      if (hasSyntaxErrors(psiFile, file)) {
        return;
      }

      performFormatAndSave(document, psiFile, organizeImports);
    });
  }

  private boolean hasSyntaxErrors(@NotNull PsiFile psiFile, @NotNull VirtualFile file) {
    return PsiTreeUtil.hasErrorElements(psiFile) || PsiErrorElementUtil.hasErrors(myProject, file);
  }

  @VisibleForTesting
  void performFormatAndSave(@NotNull Document document, @NotNull PsiFile psiFile, boolean organizeImports) {
    final AbstractLayoutCodeProcessor processor = createProcessor(psiFile, organizeImports);
    final Runnable postRunnable = () -> {
      if (myProject.isDisposed() || !document.isWritable()) {
        return;
      }
      mySavingDocuments.add(document);
      try {
        FileDocumentManager.getInstance().saveDocument(document);
      }
      finally {
        mySavingDocuments.remove(document);
      }
    };
    processor.setPostRunnable(postRunnable);
    runProcessor(processor, postRunnable);
  }

  @VisibleForTesting
  @NotNull
  AbstractLayoutCodeProcessor createProcessor(@NotNull PsiFile psiFile, boolean organizeImports) {
    if (organizeImports) {
      return new ReformatCodeProcessor(createOptimizeImportsProcessor(psiFile), false);
    }
    return new ReformatCodeProcessor(psiFile, false);
  }

  @VisibleForTesting
  @NotNull
  OptimizeImportsProcessor createOptimizeImportsProcessor(@NotNull PsiFile psiFile) {
    return new OptimizeImportsProcessor(myProject, psiFile);
  }

  @VisibleForTesting
  void runProcessor(@NotNull AbstractLayoutCodeProcessor processor, @NotNull Runnable postRunnable) {
    processor.run();
  }

  @VisibleForTesting
  boolean isDartSdkEnabled(@NotNull Module module) {
    return DartPlugin.isDartSdkEnabled(module);
  }
}
