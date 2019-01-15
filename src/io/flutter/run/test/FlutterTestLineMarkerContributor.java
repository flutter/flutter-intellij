/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.Function;
import com.intellij.util.Time;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;
import java.util.Map;


public class FlutterTestLineMarkerContributor extends RunLineMarkerContributor {

  private static final int SCANNED_TEST_RESULT_LIMIT = 1024;

  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    final TestConfigUtils.TestType testCall = TestConfigUtils.asTestCall(element);
    if (testCall != null) {
      final Icon icon = getTestStateIcon(element, testCall.getIcon());
      final Function<PsiElement, String> tooltipProvider =
        psiElement -> testCall.getTooltip(element);
      return new Info(icon, tooltipProvider, ExecutorAction.getActions());
    }

    return null;
  }

  @NotNull
  private static Icon getTestStateIcon(@NotNull PsiElement element, @NotNull Icon defaultIcon) {
    // SMTTestProxy maps test run data to a URI derived from a location hint produced by `package:test`.
    // If we can find corresponding data, we can provide state-aware icons. If not, we default to
    // a standard Run state.

    PsiFile containingFile;
    try {
      containingFile = element.getContainingFile();
    }
    catch (PsiInvalidElementAccessException e) {
      containingFile = null;
    }

    final Project project = element.getProject();
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);

    final Document document = containingFile == null ? null : psiDocumentManager.getDocument(containingFile);
    if (document != null) {
      final int textOffset = element.getTextOffset();
      final int lineNumber = document.getLineNumber(textOffset);

      // e.g., dart_location:///Users/pq/IdeaProjects/untitled1298891289891/test/unit_test.dart,3,2,["my first unit test"]
      final String path = containingFile.getVirtualFile().getPath();
      final String testLocationPrefix = "dart_location://" + path + "," + lineNumber;

      final TestStateStorage storage = TestStateStorage.getInstance(project);
      if (storage != null) {
        final Map<String, TestStateStorage.Record> tests =
          storage.getRecentTests(SCANNED_TEST_RESULT_LIMIT, getSinceDate());
        if (tests != null) {
          //TODO(pq): investigate performance implications.
          for (Map.Entry<String, TestStateStorage.Record> entry : tests.entrySet()) {
            if (entry.getKey().startsWith(testLocationPrefix)) {
              final TestStateStorage.Record state = entry.getValue();
              final TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(state.magnitude);
              if (magnitude != null) {
                switch (magnitude) {
                  case IGNORED_INDEX:
                    return AllIcons.RunConfigurations.TestState.Yellow2;
                  case ERROR_INDEX:
                  case FAILED_INDEX:
                    return AllIcons.RunConfigurations.TestState.Red2;
                  case PASSED_INDEX:
                  case COMPLETE_INDEX:
                    return AllIcons.RunConfigurations.TestState.Green2;
                  default:
                }
              }
            }
          }
        }
      }
    }

    return defaultIcon;
  }

  private static Date getSinceDate() {
    return new Date(System.currentTimeMillis() - Time.DAY);
  }
}
