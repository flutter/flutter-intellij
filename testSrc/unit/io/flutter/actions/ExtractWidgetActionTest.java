/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import io.flutter.ide.FlutterCodeInsightFixtureTestCase;
import io.flutter.refactoring.FlutterLspRefactoringHelper;
import org.jetbrains.annotations.NotNull;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class ExtractWidgetActionTest extends FlutterCodeInsightFixtureTestCase {

  private final ExtractWidgetAction action = new ExtractWidgetAction();

  public void testExtractWidgetActionUsesBgt() {
    assertEquals(ActionUpdateThread.BGT, action.getActionUpdateThread());
  }

  public void testIsVisibleFor_dartFile() {
    final PsiFile dartPsiFile = myFixture.configureByText("test.dart", "void main() {}");
    final VirtualFile dartFile = dartPsiFile.getVirtualFile();

    final DataContext dataContext = dataId -> {
      if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) {
        return dartFile;
      }
      return null;
    };

    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
    assertTrue(ExtractWidgetAction.isVisibleFor(event));

    action.update(event);
    assertTrue(event.getPresentation().isVisible());
  }

  public void testIsVisibleFor_nonDartFile() {
    final PsiFile txtPsiFile = myFixture.configureByText("test.txt", "hello world");
    final VirtualFile txtFile = txtPsiFile.getVirtualFile();

    final DataContext dataContext = dataId -> {
      if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) {
        return txtFile;
      }
      return null;
    };

    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
    assertFalse(ExtractWidgetAction.isVisibleFor(event));

    action.update(event);
    assertFalse(event.getPresentation().isVisible());
  }

  public void testIsVisibleFor_nullFile() {
    final DataContext dataContext = dataId -> null;

    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
    assertFalse(ExtractWidgetAction.isVisibleFor(event));

    action.update(event);
    assertFalse(event.getPresentation().isVisible());
  }

  public void testActionPerformedDelegatesToLspWhenEnabled() {
    final PsiFile psiFile = myFixture.configureByText("test.dart", "void main() {\n  var a = 1;\n}");
    myFixture.getEditor().getSelectionModel().setSelection(16, 26);

    final DataContext dataContext = dataId -> {
      if (PlatformDataKeys.PROJECT.is(dataId)) {
        return getProject();
      }
      if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) {
        return psiFile.getVirtualFile();
      }
      if (PlatformDataKeys.EDITOR.is(dataId)) {
        return myFixture.getEditor();
      }
      if (PlatformDataKeys.CARET.is(dataId)) {
        return myFixture.getEditor().getCaretModel().getPrimaryCaret();
      }
      return null;
    };

    final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);

    try (MockedStatic<DartConfigurable> mockedConfigurable = Mockito.mockStatic(DartConfigurable.class, Mockito.CALLS_REAL_METHODS);
         MockedStatic<FlutterLspRefactoringHelper> mockedHelper = Mockito.mockStatic(FlutterLspRefactoringHelper.class)) {
      mockedConfigurable.when(() -> DartConfigurable.isLspCodeActionsEnabled(getProject())).thenReturn(true);

      action.actionPerformed(event);

      mockedHelper.verify(() -> FlutterLspRefactoringHelper.performExtractWidget(
        Mockito.eq(getProject()),
        Mockito.eq(myFixture.getEditor()),
        Mockito.eq(psiFile),
        Mockito.eq(16),
        Mockito.eq(10)
      ));
    }
  }
}
