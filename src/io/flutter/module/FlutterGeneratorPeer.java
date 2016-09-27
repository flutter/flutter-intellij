/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.xml.util.XmlStringUtil;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkGlobalLibUtil;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;

public class FlutterGeneratorPeer {

  private JPanel myMainPanel;
  private ComboboxWithBrowseButton mySdkPathComboWithBrowse;
  private JBLabel myVersionLabel;
  private JBLabel myVersionContent;
  private JBLabel myErrorLabel; // shown in IntelliJ IDEA only

  public FlutterGeneratorPeer() {
    myErrorLabel.setIcon(AllIcons.Actions.Lightning);
    myErrorLabel.setVisible(false);

    // Hide pending real content -- TODO: #83.
    myVersionLabel.setVisible(false);
    myVersionContent.setVisible(false);

    initSdkCombo();
    validate();
  }

  private void initSdkCombo() {

    final FlutterSdk sdkInitial = FlutterSdk.getGlobalFlutterSdk();
    final String sdkPathInitial = sdkInitial == null ? "" : FileUtil.toSystemDependentName(sdkInitial.getHomePath());

    mySdkPathComboWithBrowse.getComboBox().setEditable(true);
    mySdkPathComboWithBrowse.getComboBox().getEditor().setItem(sdkPathInitial);

    final TextComponentAccessor<JComboBox> textComponentAccessor = new TextComponentAccessor<JComboBox>() {
      @Override
      public String getText(final JComboBox component) {
        return component.getEditor().getItem().toString();
      }

      @Override
      public void setText(@NotNull final JComboBox component, @NotNull final String text) {
        if (text.isEmpty() || FlutterSdkUtil.isFlutterSdkHome(text)) {
          component.getEditor().setItem(FileUtilRt.toSystemDependentName(text));
        }
        validate();
      }
    };

    final ComponentWithBrowseButton.BrowseFolderActionListener<JComboBox> browseFolderListener =
      new ComponentWithBrowseButton.BrowseFolderActionListener<>(FlutterBundle.message("flutter.sdk.browse.path.label"), null,
                                                                 mySdkPathComboWithBrowse, null,
                                                                 FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                                 textComponentAccessor);
    mySdkPathComboWithBrowse.addBrowseFolderListener(null, browseFolderListener);

    final JTextComponent editorComponent = (JTextComponent)mySdkPathComboWithBrowse.getComboBox().getEditor().getEditorComponent();
    editorComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validate();
      }
    });
  }

  void apply() {
    final Runnable runnable = () -> {
      final String sdkHomePath =
        FileUtilRt.toSystemIndependentName(getSdkComboPath());
      if (FlutterSdkUtil.isFlutterSdkHome(sdkHomePath)) {
        FlutterSdkGlobalLibUtil.ensureFlutterSdkConfigured(sdkHomePath);
        FlutterSdkUtil.setDartSdkPathIfUnset(sdkHomePath);
      }
    };

    ApplicationManager.getApplication().runWriteAction(runnable);
  }



  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }

  private void createUIComponents() {
    mySdkPathComboWithBrowse = new ComboboxWithBrowseButton(new ComboBox<>());
  }

  public boolean validate() {
    final ValidationInfo info = validateSdk();
    if (info == null) {
      myErrorLabel.setVisible(false);
      return true;
    }
    else {
      myErrorLabel.setVisible(true);
      myErrorLabel
        .setText(XmlStringUtil.wrapInHtml("<font color='#" + ColorUtil.toHex(JBColor.RED) + "'><left>" + info.message + "</left></font>"));
      return false;
    }
  }

  @Nullable
  private ValidationInfo validateSdk() {
    final String sdkPath = getSdkComboPath();
    final String message = FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(sdkPath);
    if (message != null) {
      return new ValidationInfo(message, mySdkPathComboWithBrowse);
    }

    return null;
  }

  @NotNull
  private String getSdkComboPath() {
    return mySdkPathComboWithBrowse.getComboBox().getEditor().getItem().toString().trim();
  }
}
