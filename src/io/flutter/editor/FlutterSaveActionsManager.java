/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.PsiErrorElementUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.assists.AssistUtils;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.SourceEdit;
import org.dartlang.analysis.server.protocol.SourceFileEdit;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * A manager class to run actions on save (formatting, organize imports, ...).
 */
public class FlutterSaveActionsManager {
  private static final Logger LOG = Logger.getInstance(FlutterSaveActionsManager.class);

  /**
   * Initialize the save actions manager for the given project.
   */
  public static void init(@NotNull Project project) {
    // Call getInstance() will init FlutterFormatManager for the given project.
    getInstance(project);
  }

  public static FlutterSaveActionsManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterSaveActionsManager.class);
  }

  private final @NotNull Project myProject;

  private FlutterSaveActionsManager(@NotNull Project project) {
    this.myProject = project;

    final MessageBus bus = project.getMessageBus();
    final MessageBusConnection connection = bus.connect();
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        handleBeforeDocumentSaving(document);
      }
    });
  }

  private void handleBeforeDocumentSaving(@NotNull Document document) {
    final FlutterSettings settings = FlutterSettings.getInstance();
    if (!settings.isFormatCodeOnSave()) {
      return;
    }

    if (!myProject.isInitialized() || myProject.isDisposed()) {
      return;
    }

    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) {
      return;
    }

    if (!FlutterUtils.isDartFile(file)) {
      return;
    }

    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null || !psiFile.isValid()) {
      return;
    }

    final Module module = ModuleUtil.findModuleForFile(file, myProject);
    if (module == null) {
      return;
    }

    if (!DartPlugin.isDartSdkEnabled(module)) {
      return;
    }

    // check for errors
    if (PsiErrorElementUtil.hasErrors(myProject, psiFile.getVirtualFile())) {
      return;
    }

    DartAnalysisServerService.getInstance(myProject).serverReadyForRequest(myProject);

    if (settings.isOrganizeImportsOnSaveKey()) {
      performOrganizeThenFormat(document, file);
    }
    else {
      performFormat(document, file, false);
    }
  }

  private void performOrganizeThenFormat(@NotNull Document document, @NotNull VirtualFile file) {
    final String filePath = file.getPath();
    final SourceFileEdit fileEdit = DartAnalysisServerService.getInstance(myProject).edit_organizeDirectives(filePath);

    if (fileEdit != null) {
      ApplicationManager.getApplication().invokeLater(() -> new WriteCommandAction.Simple(myProject) {
        @Override
        protected void run() {
          if (myProject.isDisposed()) {
            return;
          }

          AssistUtils.applySourceEdits(myProject, file, document, fileEdit.getEdits(), Collections.emptySet());

          // Committing a document here is required in order to guarantee that DartPostFormatProcessor.processText() is called afterwards.
          PsiDocumentManager.getInstance(myProject).commitDocument(document);

          // Run this in an invoke later so that we don't exeucte the initial part of performFormat in a write action.
          //noinspection CodeBlock2Expr
          ApplicationManager.getApplication().invokeLater(() -> {
            performFormat(document, file, true);
          });
        }
      }.execute());
    }
  }

  private void performFormat(@NotNull Document document, @NotNull VirtualFile file, boolean reSave) {
    final int lineLength = getRightMargin(myProject);
    final DartAnalysisServerService das = DartAnalysisServerService.getInstance(myProject);

    das.updateFilesContent();

    final DartAnalysisServerService.FormatResult formatResult = das.edit_format(file, 0, 0, lineLength);

    if (formatResult == null) {
      if (reSave) {
        FileDocumentManager.getInstance().saveDocument(document);
      }
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> new WriteCommandAction.Simple(myProject) {
      @Override
      protected void run() {
        if (myProject.isDisposed()) {
          return;
        }

        boolean didFormat = false;

        final List<SourceEdit> edits = formatResult.getEdits();
        if (edits != null && edits.size() == 1) {
          final String replacement = StringUtil.convertLineSeparators(edits.get(0).getReplacement());
          document.replaceString(0, document.getTextLength(), replacement);
          PsiDocumentManager.getInstance(myProject).commitDocument(document);

          didFormat = true;
        }

        // Don't perform the save in a write action - it could invoke EDT work.
        if (reSave || didFormat) {
          //noinspection CodeBlock2Expr
          ApplicationManager.getApplication().invokeLater(() -> {
            FileDocumentManager.getInstance().saveDocument(document);
          });
        }
      }
    }.execute());
  }

  private static int getRightMargin(@NotNull Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCommonSettings(DartLanguage.INSTANCE).RIGHT_MARGIN;
  }
}
