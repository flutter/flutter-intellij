/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.CapturingProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;


public class FlutterSettingsConfigurable implements SearchableConfigurable {

  private static final Logger LOG = Logger.getInstance(FlutterSettingsConfigurable.class.getName());

  private static final String FLUTTER_SETTINGS_PAGE_ID = "flutter.settings";
  private static final String FLUTTER_SETTINGS_PAGE_NAME = FlutterBundle.message("flutter.title");
  private static final String FLUTTER_SETTINGS_HELP_TOPIC = "flutter.settings.help";

  private JPanel mainPanel;
  private ComboboxWithBrowseButton sdkCombo;
  private JBLabel errorLabel;
  private JTextArea versionDetails;

  FlutterSettingsConfigurable() {
    init();
    errorLabel.setIcon(AllIcons.Actions.Lightning);
  }

  private void init() {
    sdkCombo.getComboBox().setEditable(true);
    final JTextComponent sdkEditor = (JTextComponent)sdkCombo.getComboBox().getEditor().getEditorComponent();
    sdkEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateErrorLabel();
        updateVersionText();
      }
    });

    sdkCombo.addBrowseFolderListener("Select Flutter SDK Path", null, null,
                                     FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                     TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    versionDetails.setBackground(UIUtil.getPanelBackground());
  }

  private void createUIComponents() {
    sdkCombo = new ComboboxWithBrowseButton(new ComboBox<>());
  }

  @Override
  @NotNull
  public String getId() {
    return FLUTTER_SETTINGS_PAGE_ID;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return mainPanel;
  }

  @Override
  public boolean isModified() {
    final FlutterSdk sdk = FlutterSdk.getGlobalFlutterSdk();
    final String sdkPathInModel = sdk == null ? "" : sdk.getHomePath();
    final String sdkPathInUI = FileUtilRt.toSystemIndependentName(getSdkPathText());

    return !sdkPathInModel.equals(sdkPathInUI);
  }

  @Override
  public void apply() throws ConfigurationException {
    final Runnable runnable = () -> {
      final String sdkHomePath = getSdkPathText();
      if (FlutterSdkUtil.isFlutterSdkHome(sdkHomePath)) {
        FlutterSdkGlobalLibUtil.ensureFlutterSdkConfigured(sdkHomePath);
        FlutterSdkUtil.setDartSdkPathIfUnset(sdkHomePath);
      }
    };

    ApplicationManager.getApplication().runWriteAction(runnable);

    reset(); // because we rely on remembering initial state
  }

  @Override
  public void reset() {
    final FlutterSdk sdk = FlutterSdk.getGlobalFlutterSdk();
    final String path = sdk != null ? sdk.getHomePath() : "";
    sdkCombo.getComboBox().getEditor().setItem(path);

    updateVersionText();
    updateErrorLabel();
  }

  private void updateVersionText() {
    final FlutterSdk sdk = FlutterSdk.forPath(getSdkPathText());
    if (sdk == null) {
      versionDetails.setVisible(false);
    }
    else {
      try {
        final ModalityState modalityState = ModalityState.current();
        sdk.run(FlutterSdk.Command.VERSION, null, null, new CapturingProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            final ProcessOutput output = getOutput();
            final String stdout = output.getStdout();
            ApplicationManager.getApplication().invokeLater(() -> {
              versionDetails.setText(stdout);
              versionDetails.setVisible(true);
            }, modalityState);
          }
        });
      }
      catch (ExecutionException e) {
        LOG.warn(e);
      }
    }
  }

  @Override
  public void disposeUIResources() {

  }

  @Override
  @Nls
  public String getDisplayName() {
    return FLUTTER_SETTINGS_PAGE_NAME;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return FLUTTER_SETTINGS_HELP_TOPIC;
  }

  private void updateErrorLabel() {
    final String message = getErrorMessage();
    errorLabel
      .setText(XmlStringUtil.wrapInHtml("<font color='#" + ColorUtil.toHex(JBColor.RED) + "'><left>" + message + "</left></font>"));
    errorLabel.setVisible(message != null);
  }

  @Nullable
  private String getErrorMessage() {
    return
      FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(getSdkPathText());
  }

  private String getSdkPathText() {
    return FileUtilRt.toSystemIndependentName(sdkCombo.getComboBox().getEditor().getItem().toString().trim());
  }
}
