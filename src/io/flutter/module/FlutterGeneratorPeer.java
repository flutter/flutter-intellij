/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.xml.util.XmlStringUtil;
import io.flutter.FlutterBundle;
import io.flutter.module.settings.SettingsHelpForm;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.ComboBoxEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.text.JTextComponent;

public class FlutterGeneratorPeer {
  private final WizardContext myContext;
  private JPanel myMainPanel;
  private ComboBox<String> mySdkPathComboWithBrowse;
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
    mySdkPathComboWithBrowse.setEditable(true);

    // The combo may not be showing yet (e.g. switching project type mid-wizard); wait until
    // it is so FlutterSdkUtil can resolve the dialog's real modality state.
    UiNotifyConnector.doWhenFirstShown(mySdkPathComboWithBrowse, () -> populateSdkCombo(mySdkPathComboWithBrowse));

    mySdkPathComboWithBrowse.addActionListener(e -> fillSdkCache());
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

  private void populateSdkCombo(@NotNull JComboBox<String> combo) {
    populateSdkCombo(combo, System.getenv("FLUTTER_SDK"));
  }

  @VisibleForTesting
  static void populateSdkCombo(@NotNull JComboBox<String> combo, @Nullable String envFlutterSdkPath) {
    FlutterSdkUtil.addKnownSDKPathsToCombo(combo);
    if (combo.getModel().getSize() == 0 && StringUtil.isNotEmpty(envFlutterSdkPath)) {
      // If no SDKs are found, try to use the one from the FLUTTER_SDK environment variable.
      // This ensures the SDK path is pre-filled when the combo box is empty, not requiring
      // a running Application which is the case for users and bots on the initial startup
      // experience.
      combo.setSelectedItem(envFlutterSdkPath);
    }
  }

  private void fillSdkCache() {
    final Object selectedItem = mySdkPathComboWithBrowse.getSelectedItem();
    final String path = selectedItem != null ? FileUtilRt.toSystemIndependentName(selectedItem.toString().trim()) : "";
    if (path.isEmpty()) {
      return;
    }
    OpenApiUtils.safeExecuteOnPooledThread(() -> {
      final FlutterSdk sdk = FlutterSdk.forPath(path);
      if (sdk != null) {
        sdk.queryConfiguredPlatforms(false);
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
    final ExtendableTextComponent.Extension browseExtension =
      ExtendableTextComponent.Extension.create(
        AllIcons.General.OpenDisk,
        AllIcons.General.OpenDiskHover,
        FlutterBundle.message("flutter.sdk.browse.path.label"),
        this::chooseSdkPath);
    mySdkPathComboWithBrowse = new ComboBox<>();
    mySdkPathComboWithBrowse.setEditor(new BasicComboBoxEditor() {
      @Override
      protected JTextField createEditorComponent() {
        final ExtendableTextField editor = new ExtendableTextField();
        editor.addExtension(browseExtension);
        editor.setBorder(null);
        return editor;
      }
    });
  }

  private void chooseSdkPath() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.singleDir()
      .withTitle(FlutterBundle.message("flutter.sdk.browse.path.label"));
    final String currentPath = getSdkComboPath();
    final LocalFileSystem localFileSystem =
      ApplicationManager.getApplication() == null ? null : LocalFileSystem.getInstance();
    final VirtualFile toSelect =
      currentPath.isEmpty() || localFileSystem == null ? null : localFileSystem.findFileByPath(currentPath);
    final VirtualFile file = FileChooser.chooseFile(descriptor, mySdkPathComboWithBrowse, null, toSelect);
    if (file != null) {
      mySdkPathComboWithBrowse.setSelectedItem(FileUtilRt.toSystemDependentName(file.getPath()));
    }
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
    if (StringUtil.isEmpty(sdkPath)) {
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
    final Object item = getSdkEditor().getItem();
    return item != null ? FileUtilRt.toSystemIndependentName(item.toString().trim()) : "";
  }

  @NotNull
  public ComboBoxEditor getSdkEditor() {
    return mySdkPathComboWithBrowse.getEditor();
  }

  @VisibleForTesting
  @NotNull
  ComboBox<String> getSdkComboBox() {
    return mySdkPathComboWithBrowse;
  }

  public SettingsHelpForm getHelpForm() {
    return myHelpForm;
  }
}
