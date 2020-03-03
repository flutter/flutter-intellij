/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw.importing;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.ui.MessageType;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Panel for setting up individual module import.
 */
public final class ModuleImportSettingsPane extends JPanel implements ModuleImportSettings {
  public static final int COLUMN_COUNT = 4;

  private final JTextField myModuleName = new JTextField();
  private final JLabel myStatusMessage = new JBLabel();
  private final JCheckBox myImportModule = new JCheckBox("Import");
  private final JLabel mySourcePath = new JLabel();
  private final List<ActionListener> myListenerList = Lists.newLinkedList();
  private int componentNumber = 0;

  public ModuleImportSettingsPane() {
    setLayout(new GridLayoutManager(2, COLUMN_COUNT, UIUtil.PANEL_REGULAR_INSETS, -1, -1));
    addToGrid(new JLabel("Source location:"), false, 1);
    mySourcePath.setPreferredSize(new Dimension(JBUI.scale(20), -1));
    addToGrid(mySourcePath, true, 2);
    GridConstraints checkBoxConstraints = createGridConstraints(false, 1);
    checkBoxConstraints.setAnchor(GridConstraints.ANCHOR_EAST);
    checkBoxConstraints.setFill(GridConstraints.FILL_NONE);
    add(myImportModule, checkBoxConstraints);

    addToGrid(new JLabel("Module name:"), false, 1);
    GridConstraints moduleNameConstraint = createGridConstraints(true, 1);
    moduleNameConstraint.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    myModuleName.setColumns(15);
    add(myModuleName, moduleNameConstraint);
    myStatusMessage.setPreferredSize(new Dimension(JBUI.scale(20), -1));
    addToGrid(myStatusMessage, true, 2);

    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        fireModuleUpdated();
      }
    });
    myImportModule.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fireModuleUpdated();
      }
    });
  }

  private void fireModuleUpdated() {
    ActionEvent actionEvent = new ActionEvent(this, 0, null);
    for (ActionListener actionListener : myListenerList) {
      actionListener.actionPerformed(actionEvent);
    }
  }

  private void addToGrid(JComponent component, boolean grab, int columnSpan) {
    add(component, createGridConstraints(grab, columnSpan));
  }

  private GridConstraints createGridConstraints(boolean grab, int columnSpan) {
    GridConstraints gridConstraints = new GridConstraints();
    gridConstraints.setRow(componentNumber / COLUMN_COUNT);
    final int column = componentNumber % COLUMN_COUNT;
    gridConstraints.setColumn(column);
    if (grab) {
      gridConstraints.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW);
    }
    gridConstraints.setFill(GridConstraints.FILL_HORIZONTAL);
    gridConstraints.setColSpan(columnSpan);
    componentNumber += columnSpan;
    return gridConstraints;
  }

  @Override
  public String getModuleName() {
    return myModuleName.getText();
  }

  @Override
  public void setModuleName(String moduleName) {
    if (!Objects.equal(myModuleName.getText(), moduleName)) {
      myModuleName.setText(moduleName);
    }
  }

  @Override
  public void setValidationStatus(@Nullable MessageType type, @Nullable String message) {
    myStatusMessage.setText(ImportUIUtil.makeHtmlString(message));
    myStatusMessage.setIcon(type == null ? null : type.getDefaultIcon());
  }

  @Override
  public void addActionListener(ActionListener listener) {
    myListenerList.add(listener);
  }

  @Override
  public void setModuleSourcePath(String path) {
    mySourcePath.setText(ImportUIUtil.makeHtmlString(path));
  }

  @Override
  public boolean isModuleSelected() {
    return myImportModule.isSelected();
  }

  @Override
  public void setModuleSelected(boolean moduleSelected) {
    if (moduleSelected != myImportModule.isSelected()) {
      myImportModule.setSelected(moduleSelected);
    }
    Color label = moduleSelected ? UIUtil.getLabelTextForeground() : UIUtil.getLabelDisabledForeground();
    mySourcePath.setForeground(label);
  }

  @Override
  public void setCanToggleModuleSelection(boolean canToggleModuleSelection) {
    myImportModule.setEnabled(canToggleModuleSelection);
  }

  @Override
  public void setCanRenameModule(boolean canRenameModule) {
    myModuleName.setEnabled(canRenameModule);
  }
}
