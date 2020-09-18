/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

public class ProjectType {
  private final class ProjectTypeComboBoxModel extends AbstractListModel<FlutterProjectType>
    implements ComboBoxModel<FlutterProjectType> {
    private final List<FlutterProjectType> myList = new ArrayList<>(EnumSet.allOf(FlutterProjectType.class));
    private FlutterProjectType mySelected;

    private ProjectTypeComboBoxModel() {
      // TODO(messick) Remove this filter in 2019Q4, assuming add-to-app is complete then.
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
      cacheState();
      mySelected = item;
      fireContentsChanged(this, 0, getSize());
    }
  }

  private Supplier<? extends FlutterSdk> getSdk;

  private JPanel projectTypePanel;
  private ComboBox projectTypeCombo;
  private JCheckBox androidxCheckbox;
  private boolean androidxCheckboxSet = false;
  private boolean androidxCheckboxValue = false;

  public ProjectType(@Nullable Supplier<? extends FlutterSdk> getSdk) {
    this.getSdk = getSdk;
    computeAndroidXAvailability();
  }

  @SuppressWarnings("unused")
  public ProjectType() {
    // Required by AS NPW
  }

  private void createUIComponents() {
    projectTypeCombo = new ComboBox<>();
    //noinspection unchecked
    projectTypeCombo.setModel(new ProjectTypeComboBoxModel());
    projectTypeCombo.setToolTipText(FlutterBundle.message("flutter.module.create.settings.type.tip"));
    androidxCheckbox = new JCheckBox();
  }

  @NotNull
  public JComponent getComponent() {
    return projectTypePanel;
  }

  @NotNull
  public JCheckBox getAndroidxCheckbox() {
    return androidxCheckbox;
  }

  public FlutterProjectType getType() {
    return (FlutterProjectType)projectTypeCombo.getSelectedItem();
  }

  public ComboBox getProjectTypeCombo() {
    return projectTypeCombo;
  }

  public void setSdk(@NotNull Supplier<? extends FlutterSdk> sdk) {
    this.getSdk = sdk;
    computeAndroidXAvailability();
  }

  public void addListener(ItemListener listener) {
    projectTypeCombo.addItemListener(listener);
  }

  public void cacheState() {
    if (getType() != FlutterProjectType.PACKAGE) {
      androidxCheckboxValue = androidxCheckbox.isSelected();
    }
  }

  public void computeAndroidXAvailability(@Nullable Project project) {
    if (project != null) {
      androidxCheckbox.setVisible(false);
      androidxCheckbox.setSelected(FlutterUtils.isAndroidxProject(project));
    }
    computeAndroidXAvailability();
  }

  private void computeAndroidXAvailability() {
    if (!androidxCheckbox.isVisible()) {
      return;
    }
    assert getSdk != null;
    FlutterSdk sdk = getSdk.get();
    if (sdk != null && sdk.getVersion().isAndroidxSupported() && getType() != FlutterProjectType.PACKAGE) {
      // It would be nice to save and restore the selection based on current state when the Prev/Next buttons
      // are used. The wizard doesn't provide navigation hooks to do that. This does the right thing when
      // switching project types.
      if (!androidxCheckboxSet) {
        androidxCheckbox.setSelected(true);
        androidxCheckboxSet = true;
        androidxCheckboxValue = true;
      }
      else {
        androidxCheckbox.setSelected(androidxCheckboxValue);
      }
      androidxCheckbox.setEnabled(true);
    }
    else {
      androidxCheckboxValue = androidxCheckbox.isSelected();
      androidxCheckbox.setSelected(false);
      androidxCheckbox.setEnabled(false);
    }
  }
}
