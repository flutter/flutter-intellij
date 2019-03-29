/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterProjectType;
import io.flutter.samples.FlutterSample;
import io.flutter.samples.FlutterSampleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
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
        myList.remove(FlutterProjectType.IMPORT);
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

  private static final class FlutterSampleComboBoxModel extends AbstractListModel<FlutterSample>
    implements ComboBoxModel<FlutterSample> {
    private final List<FlutterSample> myList = FlutterSampleManager.getSamples();
    private FlutterSample mySelected;

    public FlutterSampleComboBoxModel() {
      mySelected = myList.isEmpty() ? null : myList.get(0);
    }

    @Override
    public int getSize() {
      return myList.size();
    }

    @Override
    public FlutterSample getElementAt(int index) {
      return myList.isEmpty() ? null : myList.get(index);
    }

    @Override
    public void setSelectedItem(Object item) {
      setSelectedItem((FlutterSample)item);
    }

    @Override
    public FlutterSample getSelectedItem() {
      return mySelected;
    }

    public void setSelectedItem(FlutterSample item) {
      mySelected = item;
      fireContentsChanged(this, 0, getSize());
    }
  }

  private class FlutterSampleCellRenderer extends ColoredListCellRenderer<FlutterSample> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends FlutterSample> list,
                                         @Nullable FlutterSample sample,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      final SimpleTextAttributes style =
        snippetSelectorCombo.isEnabled() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
      append(sample == null ? "" : sample.getDisplayLabel(), style);
    }
  }

  private JPanel projectTypePanel;
  private ComboBox projectTypeCombo;
  private ComboBox<FlutterSample> snippetSelectorCombo;
  private JCheckBox generateSampleContentCheckBox;

  private void createUIComponents() {
    projectTypeCombo = new ComboBox<>();
    //noinspection unchecked
    projectTypeCombo.setModel(new ProjectTypeComboBoxModel());
    projectTypeCombo.setToolTipText(FlutterBundle.message("flutter.module.create.settings.type.tip"));
    projectTypeCombo.addItemListener(e -> {
      final boolean appType = getType() == FlutterProjectType.APP;
      if (!appType) {
        // Make sure sample generation is de-selected in non-app contexts.
        generateSampleContentCheckBox.setSelected(false);
      }
      generateSampleContentCheckBox.setEnabled(appType);
    });

    snippetSelectorCombo = new ComboBox<>();
    snippetSelectorCombo.setModel(new FlutterSampleComboBoxModel());
    snippetSelectorCombo.setRenderer(new FlutterSampleCellRenderer());
    snippetSelectorCombo.setToolTipText(FlutterBundle.message("flutter.module.create.settings.sample.tip"));
    snippetSelectorCombo.setEnabled(false);

    generateSampleContentCheckBox = new JCheckBox();
    generateSampleContentCheckBox.setText(FlutterBundle.message("flutter.module.create.settings.sample.text"));
    generateSampleContentCheckBox.addItemListener(e -> snippetSelectorCombo.setEnabled(e.getStateChange() == ItemEvent.SELECTED));
  }

  @NotNull
  public JComponent getComponent() {
    return projectTypePanel;
  }

  public FlutterProjectType getType() {
    return (FlutterProjectType)projectTypeCombo.getSelectedItem();
  }

  public FlutterSample getSample() {
    return generateSampleContentCheckBox.isVisible() && generateSampleContentCheckBox.isSelected() ? (FlutterSample)snippetSelectorCombo
      .getSelectedItem() : null;
  }

  public ComboBox getProjectTypeCombo() {
    return projectTypeCombo;
  }

  public void addListener(ItemListener listener) {
    projectTypeCombo.addItemListener(listener);
    snippetSelectorCombo.addItemListener(listener);
  }
}
