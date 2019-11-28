/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.lang.dart.psi.DartClass;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class FlutterSampleNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("flutter.sample");

  @NotNull final Project project;

  public FlutterSampleNotificationProvider(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(
    @NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (!(fileEditor instanceof TextEditor)) {
      return null;
    }

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    final String flutterPackagePath = FileUtil.normalize(sdk.getHomePath()) + "/packages/flutter/lib/src/";
    final String filePath = FileUtil.normalize(file.getPath());

    // Only show for files in the flutter sdk.
    if (!filePath.startsWith(flutterPackagePath)) {
      return null;
    }

    final TextEditor textEditor = (TextEditor)fileEditor;
    final Editor editor = textEditor.getEditor();
    final Document document = editor.getDocument();

    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null || !psiFile.isValid()) {
      return null;
    }

    // Run the code to query the document in a read action.
    final List<FlutterSample> samples = ApplicationManager.getApplication().
      runReadAction((Computable<List<FlutterSample>>)() -> {
        //noinspection CodeBlock2Expr
        return getSamplesFromDoc(flutterPackagePath, document, filePath);
      });

    return samples.isEmpty() ? null : new FlutterSampleActionsPanel(samples);
  }

  private List<FlutterSample> getSamplesFromDoc(String flutterPackagePath, Document document, String filePath) {
    final List<FlutterSample> samples = new ArrayList<>();

    // Find all candidate class definitions.
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    assert (psiFile != null);

    final DartClass[] classes = PsiTreeUtil.getChildrenOfType(psiFile, DartClass.class);
    if (classes == null) {
      return Collections.emptyList();
    }

    // Get the dartdoc for the classes and use a regex to identify which ones have
    // "/// {@tool dartpad ...}".

    for (DartClass declaration : classes) {
      final String name = declaration.getName();
      if (name == null || name.startsWith("_")) {
        continue;
      }

      final List<String> dartdoc = DartDocumentUtils.getDartdocFor(document, declaration);
      if (containsDartdocFlutterSample(dartdoc)) {
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
      if (DARTPAD_TOOL_PATTERN.matcher(line).find()) {
        return true;
      }
    }

    return false;
  }
}
