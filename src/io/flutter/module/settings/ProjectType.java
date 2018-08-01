/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.openapi.ui.ComboBox;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterProjectType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ProjectType {
  // TODO(pq): replace w/ simple EnumComboBoxModel when add-to-app is complete and we no longer need to filter out MODULE types.
  private static final class ProjectTypeComboBoxModel extends AbstractListModel<FlutterProjectType>
    implements ComboBoxModel<FlutterProjectType> {
    private final List<FlutterProjectType> myList = new ArrayList<>(EnumSet.allOf(FlutterProjectType.class));
    private FlutterProjectType mySelected;

    public ProjectTypeComboBoxModel() {
      // Remove MODULE type until add-to-app is complete.
      if (System.getProperty("flutter.experimental.modules", null) == null) {
        myList.remove(FlutterProjectType.MODULE);
      }
      mySelected = myList.get(0);
    }

    @Override
    public int getSize() {
      return myList.size();
    }

    @Override
    public FlutterProjectType getElementAt(int index) {
      return myList.get(index);
    }

    @Override
    public void setSelectedItem(Object item) {
      setSelectedItem((FlutterProjectType)item);
    }

    @Override
    public FlutterProjectType getSelectedItem() {
      return mySelected;
    }

    public void setSelectedItem(FlutterProjectType item) {
      mySelected = item;
      fireContentsChanged(this, 0, getSize());
    }
  }

  private JPanel projectTypePanel;
  private ComboBox projectTypeCombo;

  private void createUIComponents() {
    projectTypeCombo = new ComboBox<>();
    //noinspection unchecked
    projectTypeCombo.setModel(new ProjectTypeComboBoxModel());
    projectTypeCombo.setToolTipText(FlutterBundle.message("flutter.module.create.settings.type.tip"));
  }

  @NotNull
  public JComponent getComponent() {
    return projectTypePanel;
  }

  public FlutterProjectType getType() {
    return (FlutterProjectType)projectTypeCombo.getSelectedItem();
  }

  public void addListener(ItemListener listener) {
    projectTypeCombo.addItemListener(listener);
  }
}
