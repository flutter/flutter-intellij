/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.xml.util.XmlStringUtil;
import io.flutter.FlutterBundle;
import io.flutter.actions.InstallSdkAction;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;

public class FlutterGeneratorPeer {

  private JPanel myMainPanel;
  private ComboboxWithBrowseButton mySdkPathComboWithBrowse;
  private JBLabel myVersionContent;
  private JLabel errorIcon;
  private JTextPane errorText;
  private JScrollPane errorPane;
  private LinkLabel myInstallActionLink;

  private static final InstallSdkAction ourInstallAction = new InstallSdkAction();

  public FlutterGeneratorPeer() {
    errorIcon.setText("");
    errorIcon.setIcon(AllIcons.Actions.Lightning);
    Messages.installHyperlinkSupport(errorText);

    // Hide pending real content.
    myVersionContent.setVisible(false);

    init();
    validate();
  }

  private void init() {
    mySdkPathComboWithBrowse.getComboBox().setEditable(true);
    FlutterSdkUtil.addKnownSDKPathsToCombo(mySdkPathComboWithBrowse.getComboBox());

    mySdkPathComboWithBrowse.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                     TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    final JTextComponent editorComponent = (JTextComponent)mySdkPathComboWithBrowse.getComboBox().getEditor().getEditorComponent();
    editorComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validate();
      }
    });

    myInstallActionLink.setIcon(null);
    myInstallActionLink.setText(ourInstallAction.getLinkText());
    myInstallActionLink.setListener((label, linkUrl) -> {
        ourInstallAction.actionPerformed(null);
    }, null);

  }

  @SuppressWarnings("EmptyMethod")
  void apply() {
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
    if (info != null) {
      errorText.setText(XmlStringUtil.wrapInHtml(info.message));
    }
    errorIcon.setVisible(info != null);
    errorPane.setVisible(info != null);
    myInstallActionLink.setVisible(info != null || getSdkComboPath().trim().isEmpty());

    return info == null;
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
  public String getSdkComboPath() {
    return FileUtilRt.toSystemIndependentName(mySdkPathComboWithBrowse.getComboBox().getEditor().getItem().toString().trim());
  }
}
