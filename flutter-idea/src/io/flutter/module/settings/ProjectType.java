/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.openapi.ui.ComboBox;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

public class ProjectType {
  private static final class ProjectTypeComboBoxModel extends AbstractListModel<FlutterProjectType>
    implements ComboBoxModel<FlutterProjectType> {
    private final List<FlutterProjectType> myList = new ArrayList<>(EnumSet.allOf(FlutterProjectType.class));
    private FlutterProjectType mySelected;

    private ProjectTypeComboBoxModel() {
      if (System.getProperty("flutter.experimental.modules", null) == null) {
        if (!FlutterUtils.isAndroidStudio()) {
          myList.remove(FlutterProjectType.MODULE);
        }
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

    void addSkeleton() {
      if (!myList.contains(FlutterProjectType.SKELETON)) {
        myList.add(FlutterProjectType.SKELETON);
      }
    }

    void removeSkeleton() {
      myList.remove(FlutterProjectType.SKELETON);
    }

    void addPluginFfi() {
      if (!myList.contains(FlutterProjectType.PLUGIN_FFI)) {
        myList.add(FlutterProjectType.PLUGIN_FFI);
      }
    }

    void removePluginFfi() {
      myList.remove(FlutterProjectType.PLUGIN_FFI);
    }

    void addEmptyProject() {
      if (!myList.contains(FlutterProjectType.EMPTY_PROJECT)) {
        myList.add(FlutterProjectType.EMPTY_PROJECT);
      }
    }

    void removeEmptyProject() {
      myList.remove(FlutterProjectType.EMPTY_PROJECT);
    }
  }

  private Supplier<? extends FlutterSdk> getSdk;

  private JPanel projectTypePanel;
  private ComboBox<FlutterProjectType> projectTypeCombo;

  public ProjectType(@Nullable Supplier<? extends FlutterSdk> getSdk) {
    this.getSdk = getSdk;
  }

  @SuppressWarnings("unused")
  public ProjectType() {
    // Required by AS NPW
  }

  private void createUIComponents() {
    projectTypeCombo = new ComboBox<>();
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

  public ComboBox<FlutterProjectType> getProjectTypeCombo() {
    return projectTypeCombo;
  }

  public void setSdk(@NotNull Supplier<? extends FlutterSdk> sdk) {
    this.getSdk = sdk;
  }

  public void addListener(ItemListener listener) {
    projectTypeCombo.addItemListener(listener);
  }

  public void updateProjectTypes() {
    FlutterSdkVersion version = getSdk.get().getVersion();
    ProjectTypeComboBoxModel model = (ProjectTypeComboBoxModel)projectTypeCombo.getModel();
    if (version.isSkeletonTemplateAvailable()) {
      model.addSkeleton();
    }
    else {
      model.removeSkeleton();
    }
    if (version.isPluginFfiTemplateAvailable()) {
      model.addPluginFfi();
    }
    else {
      model.removePluginFfi();
    }
    if (version.isEmptyProjectAvailable()) {
      model.addEmptyProject();
    }
    else {
      model.removeEmptyProject();
    }
  }
}
