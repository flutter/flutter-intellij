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
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
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
  boolean isModified;
  private JPanel mainPanel;
  private JPanel sdkSettings;
  private ComboboxWithBrowseButton sdkCombo;
  private JBLabel errorLabel;
  private JTextArea versionDetails;

  FlutterSettingsConfigurable() {
    init();
    errorLabel.setIcon(AllIcons.Actions.Lightning);
  }

  @SuppressWarnings("Duplicates")
  private void init() {
    sdkCombo.getComboBox().setEditable(true);
    final JTextComponent sdkEditor = (JTextComponent)sdkCombo.getComboBox().getEditor().getEditorComponent();
    sdkEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateErrorLabel();
        updateVersionText();
      }
    });

    final TextComponentAccessor<JComboBox> textComponentAccessor = new TextComponentAccessor<JComboBox>() {
      @Override
      public String getText(final JComboBox component) {
        return component.getEditor().getItem().toString();
      }

      @Override
      public void setText(@NotNull final JComboBox component, @NotNull final String text) {
        if (!text.isEmpty() && !FlutterSdkUtil.isFlutterSdkHome(text)) {
          final String probablySdkPath = text + "/dart-sdk";
          if (FlutterSdkUtil.isFlutterSdkHome(probablySdkPath)) {
            component.getEditor().setItem(FileUtilRt.toSystemDependentName(probablySdkPath));
            return;
          }
        }

        component.getEditor().setItem(FileUtilRt.toSystemDependentName(text));
        isModified = true;
      }
    };


    final ComponentWithBrowseButton.BrowseFolderActionListener<JComboBox> browseFolderListener =
      new ComponentWithBrowseButton.BrowseFolderActionListener<>("Select Flutter SDK Path", null, sdkCombo,
                                                                 null,
                                                                 FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                                 textComponentAccessor);
    sdkCombo.addBrowseFolderListener(null, browseFolderListener);
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
  public Runnable enableSearch(String option) {
    //TODO(pq): enable search
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return mainPanel;
  }

  @Override
  public boolean isModified() {
    return isModified;
  }

  @Override
  public void apply() throws ConfigurationException {
    final Runnable runnable = () -> {
      final String sdkHomePath =
        FileUtilRt.toSystemIndependentName(getSdkPathText());
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
    isModified = false;

    final FlutterSdk sdk = FlutterSdk.getGlobalFlutterSdk();
    final String path = sdk != null ? FileUtilRt.toSystemDependentName(sdk.getHomePath()) : "";
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
        sdk.run(FlutterSdk.Command.VERSION, null, null, new CapturingProcessAdapter() {
          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            final ProcessOutput output = getOutput();
            final String stdout = output.getStdout();
            ApplicationManager.getApplication().invokeLater(() -> {
              versionDetails.setText(stdout);
              versionDetails.setVisible(true);
            }, ModalityState.current());
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
    final String message =
      FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(getSdkPathText());
    if (message != null) return message;

    return null;
  }

  private String getSdkPathText() {
    return sdkCombo.getComboBox().getEditor().getItem().toString().trim();
  }
}
