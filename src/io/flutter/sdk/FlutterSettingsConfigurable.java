/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.net.URI;
import java.net.URISyntaxException;

public class FlutterSettingsConfigurable implements SearchableConfigurable {
  private static final boolean DISABLE_INSPECTOR = true;

  private static final Logger LOG = Logger.getInstance(FlutterSettingsConfigurable.class);

  private static final String FLUTTER_SETTINGS_PAGE_NAME = FlutterBundle.message("flutter.title");
  private static final String FLUTTER_SETTINGS_HELP_TOPIC = "flutter.settings.help";

  private JPanel mainPanel;
  private ComboboxWithBrowseButton mySdkCombo;
  private JBLabel myVersionLabel;
  private JCheckBox myReportUsageInformationCheckBox;
  private LinkLabel<String> myPrivacyPolicy;
  private JCheckBox myHotReloadOnSaveCheckBox;
  private JCheckBox myEnableVerboseLoggingCheckBox;
  private JCheckBox myEnableMemoryDashboardCheckBox;
  private JCheckBox myEnableWidgetInspectorCheckBox;
  private LinkLabel<String> myInspectorFeedbackLink;
  private final @NotNull Project myProject;

  FlutterSettingsConfigurable(@NotNull Project project) {
    this.myProject = project;

    init();

    myVersionLabel.setText("");
    myVersionLabel.setCopyable(true);

    myEnableWidgetInspectorCheckBox.setVisible(!DISABLE_INSPECTOR);
    myInspectorFeedbackLink.setVisible(!DISABLE_INSPECTOR);
  }

  private void init() {
    mySdkCombo.getComboBox().setEditable(true);

    final JTextComponent sdkEditor = (JTextComponent)mySdkCombo.getComboBox().getEditor().getEditorComponent();
    sdkEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateVersionText();
      }
    });

    mySdkCombo.addBrowseFolderListener("Select Flutter SDK Path", null, null,
                                       FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                       TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    myPrivacyPolicy.setIcon(null);
    myPrivacyPolicy.setListener((label, linkUrl) -> {
      try {
        BrowserLauncher.getInstance().browse(new URI(linkUrl));
      }
      catch (URISyntaxException ignore) {
      }
    }, FlutterBundle.message("flutter.analytics.privacyUrl"));

    myInspectorFeedbackLink.setIcon(null);
    myInspectorFeedbackLink.setListener((label, linkUrl) -> {
      try {
        BrowserLauncher.getInstance().browse(new URI(linkUrl));
      }
      catch (URISyntaxException ignore) {
      }
    }, "https://goo.gl/WrMB43");
  }

  private void createUIComponents() {
    mySdkCombo = new ComboboxWithBrowseButton(new ComboBox<>());
  }

  @Override
  @NotNull
  public String getId() {
    return FlutterConstants.FLUTTER_SETTINGS_PAGE_ID;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String s) {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return mainPanel;
  }

  @Override
  public boolean isModified() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(myProject);
    final FlutterSettings settings = FlutterSettings.getInstance();
    final String sdkPathInModel = sdk == null ? "" : sdk.getHomePath();
    final String sdkPathInUI = FileUtilRt.toSystemIndependentName(getSdkPathText());

    if (!sdkPathInModel.equals(sdkPathInUI)) {
      return true;
    }

    if (FlutterInitializer.getCanReportAnalytics() != myReportUsageInformationCheckBox.isSelected()) {
      return true;
    }

    if (settings.isReloadOnSave() != myHotReloadOnSaveCheckBox.isSelected()) {
      return true;
    }

    if (settings.isMemoryDashboardEnabled() != myEnableMemoryDashboardCheckBox.isSelected()) {
      return true;
    }

    if (settings.isWidgetInspectorEnabled() != myEnableWidgetInspectorCheckBox.isSelected()) {
      return true;
    }

    //noinspection RedundantIfStatement
    if (settings.isVerboseLogging() != myEnableVerboseLoggingCheckBox.isSelected()) {
      return true;
    }

    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    final String errorMessage = FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(getSdkPathText());
    if (errorMessage != null) {
      throw new ConfigurationException(errorMessage);
    }

    final String sdkHomePath = getSdkPathText();
    if (FlutterSdkUtil.isFlutterSdkHome(sdkHomePath)) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        FlutterSdkUtil.setFlutterSdkPath(myProject, sdkHomePath);
        FlutterSdkUtil.enableDartSdk(myProject);
      });
    }

    FlutterInitializer.setCanReportAnalaytics(myReportUsageInformationCheckBox.isSelected());

    final FlutterSettings settings = FlutterSettings.getInstance();
    settings.setReloadOnSave(myHotReloadOnSaveCheckBox.isSelected());
    settings.setVerboseLogging(myEnableVerboseLoggingCheckBox.isSelected());
    settings.setMemoryDashboardEnabled(myEnableMemoryDashboardCheckBox.isSelected());
    settings.setWidgetInspectorEnabled(myEnableWidgetInspectorCheckBox.isSelected());

    reset(); // because we rely on remembering initial state
  }

  @Override
  public void reset() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(myProject);
    final String path = sdk != null ? sdk.getHomePath() : "";
    FlutterSdkUtil.addKnownSDKPathsToCombo(mySdkCombo.getComboBox());

    // Set this after populating the combo box to display correctly when the Flutter SDK is unset.
    // (This can happen if the user changed the Dart SDK.)
    mySdkCombo.getComboBox().getEditor().setItem(FileUtil.toSystemDependentName(path));

    updateVersionText();

    myReportUsageInformationCheckBox.setSelected(FlutterInitializer.getCanReportAnalytics());

    final FlutterSettings settings = FlutterSettings.getInstance();
    myHotReloadOnSaveCheckBox.setSelected(settings.isReloadOnSave());
    myEnableVerboseLoggingCheckBox.setSelected(settings.isVerboseLogging());
    myEnableMemoryDashboardCheckBox.setSelected(settings.isMemoryDashboardEnabled());
    myEnableWidgetInspectorCheckBox.setSelected(settings.isWidgetInspectorEnabled());
  }

  private void updateVersionText() {
    final FlutterSdk sdk = FlutterSdk.forPath(getSdkPathText());
    if (sdk == null) {
      myVersionLabel.setText("");
      return;
    }
    final ModalityState modalityState = ModalityState.current();

    sdk.flutterVersion().start((ProcessOutput output) -> {
      final String stdout = output.getStdout();
      final String htmlText = "<html>" + StringUtil.replace(StringUtil.escapeXml(stdout.trim()), "\n", "<br/>") + "</html>";
      ApplicationManager.getApplication().invokeLater(() -> updateVersionTextIfCurrent(sdk, htmlText), modalityState);
    }, null);
  }

  /***
   * Sets the version text but only if we don't have stale data.
   *
   * @param sdk the SDK that was current at the time.
   */
  private void updateVersionTextIfCurrent(@NotNull FlutterSdk sdk, @NotNull String value) {
    final FlutterSdk current = FlutterSdk.forPath(getSdkPathText());
    if (current == null) {
      myVersionLabel.setText("");
    }
    else {
      myVersionLabel.setText(value);
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

  @NotNull
  private String getSdkPathText() {
    return FileUtilRt.toSystemIndependentName(mySdkCombo.getComboBox().getEditor().getItem().toString().trim());
  }
}
