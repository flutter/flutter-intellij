/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;
import com.jetbrains.lang.dart.ide.refactoring.ServerRefactoringDialog;
import com.jetbrains.lang.dart.ide.refactoring.status.RefactoringStatus;
import io.flutter.FlutterUtils;
import io.flutter.refactoring.ExtractWidgetRefactoring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class ExtractWidgetAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    //noinspection DataFlowIssue
    final Project project = dataContext.getData(PlatformDataKeys.PROJECT);
    //noinspection DataFlowIssue
    final VirtualFile file = dataContext.getData(PlatformDataKeys.VIRTUAL_FILE);
    //noinspection DataFlowIssue
    final Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
    //noinspection DataFlowIssue
    final Caret caret = dataContext.getData(PlatformDataKeys.CARET);

    if (project != null && file != null && editor != null && caret != null) {
      final int offset = caret.getSelectionStart();
      final int length = caret.getSelectionEnd() - offset;
      final ExtractWidgetRefactoring refactoring = new ExtractWidgetRefactoring(project, file, offset, length);

      // Validate the initial status.
      final RefactoringStatus initialStatus = refactoring.checkInitialConditions();
      if (initialStatus == null) {
        return;
      }
      if (initialStatus.hasError()) {
        final String message = initialStatus.getMessage();
        assert message != null;
        String title = CommonBundle.getErrorTitle();
        assert title != null;
        CommonRefactoringUtil.showErrorHint(project, editor, message, title, null);
        return;
      }

      new ExtractWidgetDialog(project, editor, refactoring).show();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(isVisibleFor(e));
    super.update(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected static boolean isVisibleFor(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    //noinspection DataFlowIssue
    final VirtualFile file = dataContext.getData(PlatformDataKeys.VIRTUAL_FILE);
    return file != null && FlutterUtils.isDartFile(file);
  }
}

class ExtractWidgetDialog extends ServerRefactoringDialog<ExtractWidgetRefactoring> {
  final @NotNull ExtractWidgetRefactoring myRefactoring;
  private final @NotNull JTextField myNameField = new JTextField();

  public ExtractWidgetDialog(@NotNull Project project,
                             @Nullable Editor editor,
                             @NotNull ExtractWidgetRefactoring refactoring) {
    super(project, editor, refactoring);
    myRefactoring = refactoring;
    setTitle("Extract Widget");
    init();

    myNameField.setText("NewWidget");
    myNameField.selectAll();
    var document = myNameField.getDocument();
    if (document != null) {
      document.addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent event) {
          updateRefactoringOptions();
        }
      });
    }

    updateRefactoringOptions();
  }

  private void updateRefactoringOptions() {
    //noinspection DataFlowIssue
    myRefactoring.setName(myNameField.getText());
    myRefactoring.sendOptions();
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insetsBottom(4);
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 0;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.anchor = GridBagConstraints.WEST;
    final JLabel nameLabel = new JLabel("Widget name:");
    panel.add(nameLabel, gbConstraints);

    gbConstraints.insets = JBUI.insets(0, 4, 4, 0);
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(myNameField, gbConstraints);
    //noinspection DataFlowIssue
    myNameField.setPreferredSize(new Dimension(200, myNameField.getPreferredSize().height));

    return panel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }
}
