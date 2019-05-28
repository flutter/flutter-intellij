/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
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
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URISyntaxException;

public class FlutterSettingsConfigurable implements SearchableConfigurable {
  private static final Logger LOG = Logger.getInstance(FlutterSettingsConfigurable.class);

  public static final String FLUTTER_SETTINGS_PAGE_NAME = FlutterBundle.message("flutter.title");
  private static final String FLUTTER_SETTINGS_HELP_TOPIC = "flutter.settings.help";

  private JPanel mainPanel;
  private ComboboxWithBrowseButton mySdkCombo;
  private JBLabel myVersionLabel;
  private JCheckBox myReportUsageInformationCheckBox;
  private JLabel myPrivacyPolicy;
  private JCheckBox myHotReloadOnSaveCheckBox;
  private JCheckBox myHotReloadIgnoreErrorCheckBox;
  private JCheckBox myEnableVerboseLoggingCheckBox;
  private JCheckBox myOpenInspectorOnAppLaunchCheckBox;
  private JCheckBox myFormatCodeOnSaveCheckBox;
  private JCheckBox myOrganizeImportsOnSaveCheckBox;
  private JCheckBox myDisableTrackWidgetCreationCheckBox;
  private JCheckBox myUseLogViewCheckBox;
  private JCheckBox mySyncAndroidLibrariesCheckBox;
  private JCheckBox myUseBazelByDefaultCheckBox;

  // Settings for UI as Code experiments:
  private JCheckBox myShowBuildMethodGuides;
  private JCheckBox myShowMultipleChildrenGuides;
  private JCheckBox myShowBuildMethodsOnScrollbar;

  private final @NotNull Project myProject;

  private boolean ignoringSdkChanges = false;

  FlutterSettingsConfigurable(@NotNull Project project) {
    this.myProject = project;

    init();

    myVersionLabel.setText("");
    myVersionLabel.setCopyable(true);
  }

  private void init() {
    mySdkCombo.getComboBox().setEditable(true);

    final JTextComponent sdkEditor = (JTextComponent)mySdkCombo.getComboBox().getEditor().getEditorComponent();
    sdkEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        if (!ignoringSdkChanges) {
          onVersionChanged();
        }
      }
    });

    mySdkCombo.addBrowseFolderListener("Select Flutter SDK Path", null, null,
                                       FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                       TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    myPrivacyPolicy.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        try {
          BrowserLauncher.getInstance().browse(new URI(FlutterBundle.message("flutter.analytics.privacyUrl")));
        }
        catch (URISyntaxException ignore) {
        }
      }
    });

    myHotReloadOnSaveCheckBox.addChangeListener(
      (e) -> myHotReloadIgnoreErrorCheckBox.setEnabled(myHotReloadOnSaveCheckBox.isSelected()));
    myFormatCodeOnSaveCheckBox.addChangeListener(
      (e) -> myOrganizeImportsOnSaveCheckBox.setEnabled(myFormatCodeOnSaveCheckBox.isSelected()));

    // These options are only enabled if build method guides are enabled as the
    // same class handles all these cases.
    myShowBuildMethodGuides.addChangeListener((e) -> {
      myShowMultipleChildrenGuides.setEnabled(myShowBuildMethodGuides.isSelected());
      myShowBuildMethodsOnScrollbar.setEnabled(myShowBuildMethodGuides.isSelected());
    });

    mySyncAndroidLibrariesCheckBox.setVisible(FlutterUtils.isAndroidStudio());
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

    if (settings.isReloadWithError() != myHotReloadIgnoreErrorCheckBox.isSelected()) {
      return true;
    }


    if (settings.isFormatCodeOnSave() != myFormatCodeOnSaveCheckBox.isSelected()) {
      return true;
    }

    if (settings.isOrganizeImportsOnSaveKey() != myOrganizeImportsOnSaveCheckBox.isSelected()) {
      return true;
    }

    if (settings.isShowBuildMethodGuides() != myShowBuildMethodGuides.isSelected()) {
      return true;
    }
    if (settings.isShowMultipleChildrenGuides() != myShowMultipleChildrenGuides.isSelected()) {
      return true;
    }

    if (settings.isShowBuildMethodsOnScrollbar() != myShowBuildMethodsOnScrollbar.isSelected()) {
      return true;
    }

    if (settings.useFlutterLogView() != myUseLogViewCheckBox.isSelected()) {
      return true;
    }

    if (settings.isOpenInspectorOnAppLaunch() != myOpenInspectorOnAppLaunchCheckBox.isSelected()) {
      return true;
    }

    if (settings.isDisableTrackWidgetCreation() != myDisableTrackWidgetCreationCheckBox.isSelected()) {
      return true;
    }

    if (settings.isVerboseLogging() != myEnableVerboseLoggingCheckBox.isSelected()) {
      return true;
    }

    if (settings.isSyncingAndroidLibraries() != mySyncAndroidLibrariesCheckBox.isSelected()) {
      return true;
    }

    //noinspection RedundantIfStatement
    if (settings.shouldUseBazel() != myUseBazelByDefaultCheckBox.isSelected()) {
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

    FlutterInitializer.setCanReportAnalytics(myReportUsageInformationCheckBox.isSelected());

    final FlutterSettings settings = FlutterSettings.getInstance();
    settings.setReloadOnSave(myHotReloadOnSaveCheckBox.isSelected());
    settings.setReloadWithError(myHotReloadIgnoreErrorCheckBox.isSelected());
    settings.setFormatCodeOnSave(myFormatCodeOnSaveCheckBox.isSelected());
    settings.setOrganizeImportsOnSaveKey(myOrganizeImportsOnSaveCheckBox.isSelected());

    settings.setShowBuildMethodGuides(myShowBuildMethodGuides.isSelected());
    settings.setShowMultipleChildrenGuides(myShowMultipleChildrenGuides.isSelected());
    settings.setShowBuildMethodsOnScrollbar(myShowBuildMethodsOnScrollbar.isSelected());
    settings.setUseFlutterLogView(myUseLogViewCheckBox.isSelected());
    settings.setOpenInspectorOnAppLaunch(myOpenInspectorOnAppLaunchCheckBox.isSelected());
    settings.setDisableTrackWidgetCreation(myDisableTrackWidgetCreationCheckBox.isSelected());
    settings.setVerboseLogging(myEnableVerboseLoggingCheckBox.isSelected());
    settings.setSyncingAndroidLibraries(mySyncAndroidLibrariesCheckBox.isSelected());
    settings.setShouldUseBazel(myUseBazelByDefaultCheckBox.isSelected());

    reset(); // because we rely on remembering initial state
  }

  @Override
  public void reset() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(myProject);
    final String path = sdk != null ? sdk.getHomePath() : "";

    // Set this after populating the combo box to display correctly when the Flutter SDK is unset.
    // (This can happen if the user changed the Dart SDK.)
    try {
      ignoringSdkChanges = true;
      FlutterSdkUtil.addKnownSDKPathsToCombo(mySdkCombo.getComboBox());
      mySdkCombo.getComboBox().getEditor().setItem(FileUtil.toSystemDependentName(path));
    }
    finally {
      ignoringSdkChanges = false;
    }

    onVersionChanged();

    myReportUsageInformationCheckBox.setSelected(FlutterInitializer.getCanReportAnalytics());

    final FlutterSettings settings = FlutterSettings.getInstance();
    myHotReloadOnSaveCheckBox.setSelected(settings.isReloadOnSave());
    myHotReloadIgnoreErrorCheckBox.setSelected(settings.isReloadWithError());
    myFormatCodeOnSaveCheckBox.setSelected(settings.isFormatCodeOnSave());
    myOrganizeImportsOnSaveCheckBox.setSelected(settings.isOrganizeImportsOnSaveKey());

    myShowBuildMethodGuides.setSelected(settings.isShowBuildMethodGuides());
    myShowMultipleChildrenGuides.setSelected(settings.isShowMultipleChildrenGuides());
    myShowBuildMethodsOnScrollbar.setSelected(settings.isShowBuildMethodsOnScrollbar());

    myUseLogViewCheckBox.setSelected(settings.useFlutterLogView());
    myOpenInspectorOnAppLaunchCheckBox.setSelected(settings.isOpenInspectorOnAppLaunch());
    myDisableTrackWidgetCreationCheckBox.setSelected(settings.isDisableTrackWidgetCreation());
    myEnableVerboseLoggingCheckBox.setSelected(settings.isVerboseLogging());
    mySyncAndroidLibrariesCheckBox.setSelected(settings.isSyncingAndroidLibraries());

    myHotReloadIgnoreErrorCheckBox.setEnabled(myHotReloadOnSaveCheckBox.isSelected());

    myUseBazelByDefaultCheckBox.setSelected(settings.shouldUseBazel());
    // We only show the bazel by default checkbox inside of a bazel project.
    myUseBazelByDefaultCheckBox.setVisible(FlutterModuleUtils.isFlutterBazelProject(myProject));

    myOrganizeImportsOnSaveCheckBox.setEnabled(myFormatCodeOnSaveCheckBox.isSelected());

    // These options are only enabled if build method guides are enabled as the
    // same class handles all these cases.
    myShowMultipleChildrenGuides.setEnabled(myShowBuildMethodGuides.isSelected());
    myShowBuildMethodsOnScrollbar.setEnabled(myShowBuildMethodGuides.isSelected());
  }

  private void onVersionChanged() {
    final FlutterSdk sdk = FlutterSdk.forPath(getSdkPathText());
    if (sdk == null) {
      myVersionLabel.setText("");
      return;
    }

    final ModalityState modalityState = ModalityState.current();

    final boolean trackWidgetCreationRecommended = sdk.getVersion().isTrackWidgetCreationRecommended();
    myDisableTrackWidgetCreationCheckBox.setVisible(trackWidgetCreationRecommended);

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

  public static void openFlutterSettings(@NotNull final Project project) {
    ShowSettingsUtilImpl.showSettingsDialog(project, FlutterConstants.FLUTTER_SETTINGS_PAGE_ID, "");
  }
}
