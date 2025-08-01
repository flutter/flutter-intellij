/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.PlatformIcons;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterMessages;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.font.FontPreviewProcessor;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.text.JTextComponent;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

// Note: when updating the settings here, update FlutterSearchableOptionContributor as well.

public class FlutterSettingsConfigurable implements SearchableConfigurable {

  public static final String FLUTTER_SETTINGS_PAGE_NAME = FlutterBundle.message("flutter.title");
  private static final String FLUTTER_SETTINGS_HELP_TOPIC = "flutter.settings.help";

  private JPanel mainPanel;
  private ComboBox<String> mySdkCombo;
  private JBLabel myVersionLabel;
  private JCheckBox myHotReloadOnSaveCheckBox;
  private JCheckBox myEnableVerboseLoggingCheckBox;
  private JCheckBox myOpenInspectorOnAppLaunchCheckBox;
  private JCheckBox myFormatCodeOnSaveCheckBox;
  private JCheckBox myOrganizeImportsOnSaveCheckBox;
  private JCheckBox myShowStructuredErrors;
  private JCheckBox myIncludeAllStackTraces;
  private JCheckBox myEnableBazelHotRestartCheckBox;

  private JCheckBox myEnableJcefBrowserCheckBox;

  private JCheckBox myShowBuildMethodGuides;
  private JCheckBox myShowClosingLabels;
  private FixedSizeButton myCopyButton;
  private JTextArea myFontPackagesTextArea; // This should be changed to a structured list some day.
  private JCheckBox myAllowTestsInSourcesRoot;
  private ActionLink settingsLink;
  private JCheckBox myEnableLogsPreserveAfterHotReloadOrRestart;
  private @NotNull JCheckBox myEnableFilePathLogging;

  private final @NotNull Project myProject;
  private final WorkspaceCache workspaceCache;

  private boolean ignoringSdkChanges = false;

  private String fullVersionString;
  private FlutterSdkVersion previousSdkVersion;

  /**
   * Semaphore used to synchronize flutter commands so we don't try to do two at once.
   */
  private final Semaphore lock = new Semaphore(1, true);
  private Process updater;

  FlutterSettingsConfigurable(@NotNull Project project) {
    this.myProject = project;
    workspaceCache = WorkspaceCache.getInstance(project);
    init();
    myVersionLabel.setText(" ");
  }

  private void init() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(myProject);
    if (sdk != null) {
      previousSdkVersion = sdk.getVersion();
    }
    mySdkCombo.setEditable(true);

    myCopyButton.setSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    myCopyButton.setIcon(PlatformIcons.COPY_ICON);
    myCopyButton.addActionListener(e -> {
      if (fullVersionString != null) {
        CopyPasteManager.getInstance().setContents(new StringSelection(fullVersionString));
      }
    });

    final JTextComponent sdkEditor = (JTextComponent)mySdkCombo.getEditor().getEditorComponent();
    sdkEditor.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull final DocumentEvent e) {
        if (!ignoringSdkChanges) {
          onVersionChanged();
        }
      }
    });
    workspaceCache.subscribe(this::onVersionChanged);
    myFormatCodeOnSaveCheckBox.addChangeListener(
      (e) -> myOrganizeImportsOnSaveCheckBox.setEnabled(myFormatCodeOnSaveCheckBox.isSelected()));
    myShowStructuredErrors.addChangeListener(
      (e) -> myIncludeAllStackTraces.setEnabled(myShowStructuredErrors.isSelected()));

    myEnableBazelHotRestartCheckBox.setVisible(WorkspaceCache.getInstance(myProject).isBazel());
  }

  private void createUIComponents() {
    //noinspection DialogTitleCapitalization
    ExtendableTextComponent.Extension browseExtension =
      ExtendableTextComponent.Extension.create(
        AllIcons.General.OpenDisk,
        AllIcons.General.OpenDiskHover,
        "Select Flutter SDK Path",
        () -> {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
          VirtualFile file = FileChooser.chooseFile(descriptor, mySdkCombo, null, null);
          if (file != null) {
            //noinspection DataFlowIssue (sure to be set before the extension is invoked)
            mySdkCombo.setItem(file.getPath());
          }
        });
    mySdkCombo = new ComboBox<>();
    mySdkCombo.setEditor(new BasicComboBoxEditor() {
      @Override
      protected JTextField createEditorComponent() {
        ExtendableTextField ecbEditor = new ExtendableTextField();
        ecbEditor.addExtension(browseExtension);
        ecbEditor.setBorder(null);
        return ecbEditor;
      }
    });
    settingsLink = ActionsOnSaveConfigurable.createGoToActionsOnSavePageLink();
  }

  @Override
  @NotNull
  public String getId() {
    return FlutterConstants.FLUTTER_SETTINGS_PAGE_ID;
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

    if (settings.isReloadOnSave() != myHotReloadOnSaveCheckBox.isSelected()) {
      return true;
    }

    if (settings.isFormatCodeOnSave() != myFormatCodeOnSaveCheckBox.isSelected()) {
      return true;
    }

    if (settings.isOrganizeImportsOnSave() != myOrganizeImportsOnSaveCheckBox.isSelected()) {
      return true;
    }

    if (settings.isShowBuildMethodGuides() != myShowBuildMethodGuides.isSelected()) {
      return true;
    }

    if (settings.isShowClosingLabels() != myShowClosingLabels.isSelected()) {
      return true;
    }

    if (settings.isShowStructuredErrors() != myShowStructuredErrors.isSelected()) {
      return true;
    }

    if (settings.isIncludeAllStackTraces() != myIncludeAllStackTraces.isSelected()) {
      return true;
    }

    if (settings.isOpenInspectorOnAppLaunch() != myOpenInspectorOnAppLaunchCheckBox.isSelected()) {
      return true;
    }

    if (settings.isPerserveLogsDuringHotReloadAndRestart() != myEnableLogsPreserveAfterHotReloadOrRestart.isSelected()) {
      return true;
    }

    if (settings.isVerboseLogging() != myEnableVerboseLoggingCheckBox.isSelected()) {
      return true;
    }

    if (settings.isEnableBazelHotRestart() != myEnableBazelHotRestartCheckBox.isSelected()) {
      return true;
    }

    if (settings.isAllowTestsInSourcesRoot() != myAllowTestsInSourcesRoot.isSelected()) {
      return true;
    }

    if (!Objects.equals(settings.getFontPackages(), myFontPackagesTextArea.getText())) {
      return true;
    }

    if (settings.isFilePathLoggingEnabled() != myEnableFilePathLogging.isSelected()) {
      return true;
    }

    return settings.isEnableJcefBrowser() != myEnableJcefBrowserCheckBox.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    // Bazel workspaces do not specify a sdk path so we do not need to update the sdk path if using
    // a bazel workspace.
    if (!workspaceCache.isBazel()) {
      final String errorMessage = FlutterSdkUtil.getErrorMessageIfWrongSdkRootPath(getSdkPathText());
      if (errorMessage != null) {
        throw new ConfigurationException(errorMessage);
      }

      final String sdkHomePath = getSdkPathText();
      if (FlutterSdkUtil.isFlutterSdkHome(sdkHomePath)) {

        OpenApiUtils.safeRunWriteAction(() -> {
          FlutterSdkUtil.setFlutterSdkPath(myProject, sdkHomePath);
          FlutterSdkUtil.enableDartSdk(myProject);

          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            final FlutterSdk sdk = FlutterSdk.forPath(sdkHomePath);
            if (sdk != null) {
              try {
                lock.acquire();
                sdk.queryFlutterChannel(false);
                lock.release();
              }
              catch (InterruptedException e) {
                // do nothing
              }
            }
          });
        });
      }
    }

    final FlutterSettings settings = FlutterSettings.getInstance();
    final String oldFontPackages = settings.getFontPackages();
    settings.setReloadOnSave(myHotReloadOnSaveCheckBox.isSelected());
    settings.setFormatCodeOnSave(myFormatCodeOnSaveCheckBox.isSelected());
    settings.setOrganizeImportsOnSave(myOrganizeImportsOnSaveCheckBox.isSelected());

    settings.setShowBuildMethodGuides(myShowBuildMethodGuides.isSelected());
    settings.setShowClosingLabels(myShowClosingLabels.isSelected());
    settings.setShowStructuredErrors(myShowStructuredErrors.isSelected());
    settings.setIncludeAllStackTraces(myIncludeAllStackTraces.isSelected());
    settings.setOpenInspectorOnAppLaunch(myOpenInspectorOnAppLaunchCheckBox.isSelected());
    settings.setPerserveLogsDuringHotReloadAndRestart(myEnableLogsPreserveAfterHotReloadOrRestart.isSelected());
    settings.setVerboseLogging(myEnableVerboseLoggingCheckBox.isSelected());
    settings.setEnableBazelHotRestart(myEnableBazelHotRestartCheckBox.isSelected());
    settings.setAllowTestsInSourcesRoot(myAllowTestsInSourcesRoot.isSelected());
    settings.setFontPackages(myFontPackagesTextArea.getText());
    settings.setEnableJcefBrowser(myEnableJcefBrowserCheckBox.isSelected());
    settings.setFilePathLoggingEnabled(myEnableFilePathLogging.isSelected());

    reset(); // because we rely on remembering initial state
    checkFontPackages(settings.getFontPackages(), oldFontPackages);
  }

  @Override
  public void reset() {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(myProject);
    final String path = sdk != null ? sdk.getHomePath() : "";

    // Set this after populating the combo box to display correctly when the Flutter SDK is unset.
    // (This can happen if the user changed the Dart SDK.)
    try {
      ignoringSdkChanges = true;
      FlutterSdkUtil.addKnownSDKPathsToCombo(mySdkCombo);
      mySdkCombo.getEditor().setItem(FileUtil.toSystemDependentName(path));
    }
    finally {
      ignoringSdkChanges = false;
    }

    onVersionChanged();
    if (sdk != null) {
      if (previousSdkVersion != null) {
        if (previousSdkVersion.compareTo(sdk.getVersion()) != 0) {
          final List<PubRoot> roots = PubRoots.forProject(myProject);
          try {
            lock.acquire();
            for (PubRoot root : roots) {
              sdk.startPubGet(root, myProject);
            }
            lock.release();
          }
          catch (InterruptedException e) {
            // do nothing
          }
          previousSdkVersion = sdk.getVersion();
        }
      }
    }
    else {
      previousSdkVersion = null;
    }

    final FlutterSettings settings = FlutterSettings.getInstance();
    myHotReloadOnSaveCheckBox.setSelected(settings.isReloadOnSave());
    myFormatCodeOnSaveCheckBox.setSelected(settings.isFormatCodeOnSave());
    myOrganizeImportsOnSaveCheckBox.setSelected(settings.isOrganizeImportsOnSave());

    myShowBuildMethodGuides.setSelected(settings.isShowBuildMethodGuides());

    myShowClosingLabels.setSelected(settings.isShowClosingLabels());

    myShowStructuredErrors.setSelected(settings.isShowStructuredErrors());
    myIncludeAllStackTraces.setSelected(settings.isIncludeAllStackTraces());
    myOpenInspectorOnAppLaunchCheckBox.setSelected(settings.isOpenInspectorOnAppLaunch());
    myEnableLogsPreserveAfterHotReloadOrRestart.setSelected(settings.isPerserveLogsDuringHotReloadAndRestart());
    myEnableVerboseLoggingCheckBox.setSelected(settings.isVerboseLogging());

    myEnableBazelHotRestartCheckBox.setSelected(settings.isEnableBazelHotRestart());
    myAllowTestsInSourcesRoot.setSelected(settings.isAllowTestsInSourcesRoot());

    myOrganizeImportsOnSaveCheckBox.setEnabled(myFormatCodeOnSaveCheckBox.isSelected());
    myIncludeAllStackTraces.setEnabled(myShowStructuredErrors.isSelected());

    myEnableJcefBrowserCheckBox.setSelected(settings.isEnableJcefBrowser());
    myFontPackagesTextArea.setText(settings.getFontPackages());
    myEnableFilePathLogging.setSelected(settings.isFilePathLoggingEnabled());
  }

  private void onVersionChanged() {
    final Workspace workspace = workspaceCache.get();
    if (workspaceCache.isBazel()) {
      if (mySdkCombo.isEnabled()) {
        // The workspace is not null if workspaceCache.isBazel() is true.
        assert (workspace != null);

        mySdkCombo.setEnabled(false);
        mySdkCombo.getEditor()
          .setItem(workspace.getRoot().getPath() + '/' + workspace.getSdkHome() + " <set by bazel project>");
      }
    }
    else {
      mySdkCombo.setEnabled(true);
    }

    final FlutterSdk sdk = FlutterSdk.forPath(getSdkPathText());
    if (sdk == null) {
      // Clear the label out with a non-empty string, so that the layout doesn't give this element 0 height.
      myVersionLabel.setText(" ");
      fullVersionString = null;
      return;
    }

    // Moved launching the version updater to a background thread to avoid deadlock
    // when the semaphone was locked for a long time on the EDT.
    final ModalityState modalityState = ModalityState.current();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        if (updater != null) {
          // If we get back here before the previous one finished then just kill it.
          // This isn't perfect, but does help avoid printing this message most times:
          // Waiting for another flutter command to release the startup lock...
          updater.destroy();
          lock.release();
        }
        Thread.sleep(100L);
        lock.acquire();

        OpenApiUtils.safeInvokeLater(() -> {
          // "flutter --version" can take a long time on a slow network.
          updater = sdk.flutterVersion().start((ProcessOutput output) -> {
            fullVersionString = output.getStdout();
            final String[] lines = StringUtil.splitByLines(fullVersionString);
            final String singleLineVersion = lines.length > 0 ? lines[0] : "";

            OpenApiUtils.safeInvokeLater(() -> {
              updater = null;
              lock.release();
              updateVersionTextIfCurrent(sdk, singleLineVersion);
            }, modalityState);
          }, null);
        }, modalityState);
      }
      catch (InterruptedException e) {
        // do nothing
      }
    });
  }

  /***
   * Sets the version text but only if we don't have stale data.
   *
   * @param sdk the SDK that was current at the time.
   */
  private void updateVersionTextIfCurrent(@NotNull FlutterSdk sdk, @NotNull String value) {
    final FlutterSdk current = FlutterSdk.forPath(getSdkPathText());
    if (current == null) {
      myVersionLabel.setText(" ");
    }
    else {
      myVersionLabel.setText(value);
    }
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
    return FileUtilRt.toSystemIndependentName(mySdkCombo.getEditor().getItem().toString().trim());
  }

  private void checkFontPackages(String value, String previous) {
    if (value != null) {
      final String[] packages = value.split(FontPreviewProcessor.PACKAGE_SEPARATORS);
      for (String name : packages) {
        final String message = FontPreviewProcessor.UNSUPPORTED_PACKAGES.get(name.trim());
        if (message != null) {
          displayNotification(message);
        }
      }
      if (!value.equals(previous)) {
        FontPreviewProcessor.reanalyze(myProject);
      }
    }
  }

  private void displayNotification(String message) {
    final Notification notification = new Notification(
      FlutterMessages.FLUTTER_LOGGING_NOTIFICATION_GROUP_ID,
      FlutterBundle.message("icon.preview.disallow.package.title"),
      message,
      NotificationType.INFORMATION);
    notification.setIcon(FlutterIcons.Flutter);
    Notifications.Bus.notify(notification, myProject);
  }

  public static void openFlutterSettings(@NotNull final Project project) {
    ShowSettingsUtilImpl.showSettingsDialog(project, FlutterConstants.FLUTTER_SETTINGS_PAGE_ID, "");
  }
}
