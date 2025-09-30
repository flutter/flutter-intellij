/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.HyperlinkLabel;
import com.jetbrains.lang.dart.psi.DartClass;
import icons.FlutterIcons;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class FlutterSampleNotificationProvider implements EditorNotificationProvider {
  @NotNull final Project project;

  public FlutterSampleNotificationProvider(@NotNull Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public Function<? super FileEditor, ? extends JComponent> collectNotificationData(@NotNull Project project,
                                                                                                       @NotNull VirtualFile file) {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    final String flutterPackagePath = sdk.getHomePath() + "/packages/flutter/lib/src/";

    // Only show for files in the flutter sdk.
    final String filePath = file.getPath();
    if (!filePath.startsWith(flutterPackagePath)) {
      return null;
    }

    return fileEditor -> createPanelForSamples(fileEditor, project, file, filePath, sdk, flutterPackagePath);
  }

  @Nullable
  private EditorNotificationPanel createPanelForSamples(@NotNull FileEditor fileEditor,
                                                        @NotNull Project project,
                                                        @NotNull VirtualFile file,
                                                        @NotNull String filePath,
                                                        @NotNull FlutterSdk sdk,
                                                        @NotNull String flutterPackagePath) {
    if (!(fileEditor instanceof TextEditor textEditor)) {
      return null;
    }

    final Editor editor = textEditor.getEditor();
    final Document document = editor.getDocument();
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    if (psiDocumentManager == null) {
      return null;
    }

    final PsiFile psiFile = psiDocumentManager.getPsiFile(document);
    if (psiFile == null || !psiFile.isValid()) {
      return null;
    }

    // Run the code to query the document in a read action.
    final List<FlutterSample> samples = OpenApiUtils.safeRunReadAction(() -> {
      return getSamplesFromDoc(flutterPackagePath, document, filePath);
    });

    if (samples != null && !samples.isEmpty()) {
      return new FlutterSampleActionsPanel(samples);
    }
    return null;
  }

  @NotNull
  private List<FlutterSample> getSamplesFromDoc(@NotNull String flutterPackagePath, @NotNull Document document, @NotNull String filePath) {
    final List<FlutterSample> samples = new ArrayList<>();

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    if (documentManager == null) return samples;

    // Find all candidate class definitions.
    final PsiFile psiFile = documentManager.getPsiFile(document);
    assert (psiFile != null);

    final DartClass[] classes = PsiTreeUtil.getChildrenOfType(psiFile, DartClass.class);
    if (classes == null) {
      return Collections.emptyList();
    }

    // Get the dartdoc for the classes and use a regex to identify which ones have
    // "/// {@tool dartpad ...}".

    for (DartClass declaration : classes) {
      if (declaration == null) continue;
      final String name = declaration.getName();
      if (name == null || name.startsWith("_")) {
        continue;
      }

      List<String> dartdoc = null;
      try {
        // Context: https://github.com/flutter/flutter-intellij/issues/5634
        dartdoc = DartDocumentUtils.getDartdocFor(document, declaration);
      }
      catch (IndexOutOfBoundsException e) {
        // ignore
      }
      if (dartdoc != null && containsDartdocFlutterSample(dartdoc)) {
        assert (declaration.getName() != null);

        String libraryName = filePath.substring(flutterPackagePath.length());
        final int index = libraryName.indexOf('/');
        if (index != -1) {
          libraryName = libraryName.substring(0, index);

          final FlutterSample sample = new FlutterSample(libraryName, declaration.getName());
          samples.add(sample);
        }
      }
    }
    return samples;
  }

  // "/// {@tool dartpad ...}"
  private static final Pattern DARTPAD_TOOL_PATTERN = Pattern.compile("\\{@tool.*\\sdartpad.*}");

  /**
   * Return whether the given lines of dartdoc text contain a reference to an embedded dartpad Flutter
   * widget sample, eg. <code>"/// {\@tool dartpad ...}"</code>(.
   */
  @VisibleForTesting
  public static boolean containsDartdocFlutterSample(@NotNull List<String> lines) {
    if (lines.isEmpty()) {
      return false;
    }

    for (String line : lines) {
      if (line != null && DARTPAD_TOOL_PATTERN.matcher(line).find()) {
        return true;
      }
    }

    return false;
  }
}

class FlutterSampleActionsPanel extends EditorNotificationPanel {
  FlutterSampleActionsPanel(@NotNull List<FlutterSample> samples) {
    //noinspection DataFlowIssue
    super(EditorColors.GUTTER_BACKGROUND);

    icon(FlutterIcons.Flutter);
    text("View example on flutter.dev");

    for (int i = 0; i < samples.size(); i++) {
      if (i != 0) {
        //noinspection DataFlowIssue
        myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      }

      final FlutterSample sample = samples.get(i);
      assert sample != null;

      final HyperlinkLabel label = createActionLabel(sample.getClassName(), () -> browseTo(sample));
      label.setToolTipText(sample.getHostedDocsUrl());
    }
  }

  private void browseTo(@NotNull FlutterSample sample) {
    BrowserUtil.browse(sample.getHostedDocsUrl());
  }
}

