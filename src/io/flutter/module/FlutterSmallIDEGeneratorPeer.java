/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.module;

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.platform.WebProjectGenerator;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;

public class FlutterSmallIDEGeneratorPeer implements WebProjectGenerator.GeneratorPeer<String> {
  private ComboboxWithBrowseButton sdkPathComboWithBrowse;

  public FlutterSmallIDEGeneratorPeer() {
    createUIComponents();
  }

  private void createUIComponents() {
    final FlutterSdk sdkInitial = FlutterSdk.getGlobalFlutterSdk();
    final String sdkPathInitial = sdkInitial == null ? "" : FileUtil.toSystemDependentName(sdkInitial.getHomePath());

    sdkPathComboWithBrowse = new ComboboxWithBrowseButton(new ComboBox<>());
    sdkPathComboWithBrowse.getComboBox().setEditable(true);
    sdkPathComboWithBrowse.getComboBox().getEditor().setItem(sdkPathInitial);
    FlutterSdkUtil.addKnownSDKPathsToCombo(sdkPathComboWithBrowse.getComboBox());

    sdkPathComboWithBrowse.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                                   FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                   TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return sdkPathComboWithBrowse;
  }

  @Override
  public void buildUI(@NotNull SettingsStep settingsStep) {
    settingsStep.addSettingsField("Flutter SDK", sdkPathComboWithBrowse);
  }

  @NotNull
  @Override
  public String getSettings() {
    return getSdkComboPath();
  }

  @Nullable
  @Override
  public ValidationInfo validate() {
    final String sdkPath = getSdkComboPath();
    if (sdkPath.isEmpty()) {
      return new ValidationInfo(FlutterBundle.message("flutter.sdk.notAvailable.title"), sdkPathComboWithBrowse);
    }

    final String message = FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(sdkPath);
    if (message != null) {
      return new ValidationInfo(message, sdkPathComboWithBrowse);
    }

    return null;
  }

  @Override
  public boolean isBackgroundJobRunning() {
    return false;
  }

  @Override
  public void addSettingsStateListener(@NotNull WebProjectGenerator.SettingsStateListener stateListener) {
    final JTextComponent editorComponent = (JTextComponent)sdkPathComboWithBrowse.getComboBox().getEditor().getEditorComponent();
    editorComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        stateListener.stateChanged(validate() == null);
      }
    });
  }

  @NotNull
  private String getSdkComboPath() {
    return FileUtilRt.toSystemIndependentName(sdkPathComboWithBrowse.getComboBox().getEditor().getItem().toString().trim());
  }
}
