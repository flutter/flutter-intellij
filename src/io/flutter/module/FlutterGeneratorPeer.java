/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.xml.util.XmlStringUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.module.settings.SettingsHelpForm;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import javax.swing.ComboBoxEditor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterGeneratorPeer {
  private final WizardContext myContext;
  private JPanel myMainPanel;
  private ComboboxWithBrowseButton mySdkPathComboWithBrowse;
  private JBLabel myVersionContent;
  private JLabel errorIcon;
  private JTextPane errorText;
  private JScrollPane errorPane;
  private SettingsHelpForm myHelpForm;

  public FlutterGeneratorPeer(WizardContext context) {
    myContext = context;

    errorIcon.setText("");
    errorIcon.setIcon(AllIcons.Actions.Lightning);
    Messages.installHyperlinkSupport(errorText);

    // Hide pending real content.
    myVersionContent.setVisible(false);

    // TODO(messick) Remove this field.
    myHelpForm.getComponent().setVisible(false);

    init();
  }

  private void init() {
    mySdkPathComboWithBrowse.getComboBox().setEditable(true);
    FlutterSdkUtil.addKnownSDKPathsToCombo(mySdkPathComboWithBrowse.getComboBox());

    mySdkPathComboWithBrowse.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                     TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
    mySdkPathComboWithBrowse.getComboBox().addActionListener(e -> fillSdkCache());
    fillSdkCache();

    final JTextComponent editorComponent = (JTextComponent)getSdkEditor().getEditorComponent();
    editorComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        validate();
      }
    });

    errorIcon.setVisible(false);
    errorPane.setVisible(false);
  }

  private void fillSdkCache() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String path = (String)mySdkPathComboWithBrowse.getComboBox().getSelectedItem();
      if (path != null) {
        FlutterSdk sdk = FlutterSdk.forPath(path);
        if (sdk != null) {
          sdk.queryConfiguredPlatforms(false);
          sdk.queryFlutterChannel(false);
        }
      }
    });
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

  // TODO Link this to actual validation.
  public boolean validate() {
    final ValidationInfo info = validateSdk();
    if (info != null) {
      errorText.setText(XmlStringUtil.wrapInHtml(info.message));
    }
    errorIcon.setVisible(info != null);
    errorPane.setVisible(info != null);

    return info == null;
  }

  @Nullable
  private ValidationInfo validateSdk() {
    final String sdkPath = getSdkComboPath();
    if (StringUtils.isEmpty(sdkPath)) {
      return new ValidationInfo("A Flutter SDK must be specified for project creation.", mySdkPathComboWithBrowse);
    }
    final String message = FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(sdkPath);
    if (message != null) {
      return new ValidationInfo(message, mySdkPathComboWithBrowse);
    }

    return null;
  }

  @NotNull
  public String getSdkComboPath() {
    return FileUtilRt.toSystemIndependentName(getSdkEditor().getItem().toString().trim());
  }

  @NotNull
  public ComboBoxEditor getSdkEditor() {
    return mySdkPathComboWithBrowse.getComboBox().getEditor();
  }

  public SettingsHelpForm getHelpForm() {
    return myHelpForm;
  }
}
