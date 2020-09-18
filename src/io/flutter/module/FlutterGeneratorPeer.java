/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import io.flutter.FlutterBundle;
import io.flutter.actions.InstallSdkAction;
import io.flutter.sdk.FlutterSdkUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class FlutterGeneratorPeer implements InstallSdkAction.Model {
  private final WizardContext myContext;
  private JPanel myMainPanel;
  private ComboboxWithBrowseButton mySdkPathComboWithBrowse;
  private JBLabel myVersionContent;
  private JLabel errorIcon;
  private JTextPane errorText;
  private JScrollPane errorPane;
  private LinkLabel myInstallActionLink;
  private JProgressBar myProgressBar;
  private JTextPane myProgressText;
  private JScrollPane myProgressScrollPane;
  private JLabel myCancelProgressButton;

  private final InstallSdkAction myInstallSdkAction;
  private InstallSdkAction.CancelActionListener myListener;

  public FlutterGeneratorPeer(WizardContext context) {
    myContext = context;
    myInstallSdkAction = new InstallSdkAction(this);

    errorIcon.setText("");
    errorIcon.setIcon(AllIcons.Actions.Lightning);
    Messages.installHyperlinkSupport(errorText);

    // Hide pending real content.
    myVersionContent.setVisible(false);
    myProgressBar.setVisible(false);
    myProgressText.setVisible(false);
    myCancelProgressButton.setVisible(false);

    init();
  }

  private void init() {
    mySdkPathComboWithBrowse.getComboBox().setEditable(true);
    FlutterSdkUtil.addKnownSDKPathsToCombo(mySdkPathComboWithBrowse.getComboBox());

    mySdkPathComboWithBrowse.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                     TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);

    final JTextComponent editorComponent = (JTextComponent)getSdkEditor().getEditorComponent();
    editorComponent.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        validate();
      }
    });

    // When this changes the corresponding parts of FlutterProjectStep should also be changed.
    myInstallActionLink.setIcon(myInstallSdkAction.getLinkIcon());
    myInstallActionLink.setDisabledIcon(IconLoader.getDisabledIcon(myInstallSdkAction.getLinkIcon()));

    myInstallActionLink.setText(myInstallSdkAction.getLinkText());

    //noinspection unchecked
    myInstallActionLink.setListener((label, linkUrl) -> myInstallSdkAction.actionPerformed(null), null);

    myProgressText.setFont(UIUtil.getLabelFont(UIUtil.FontSize.NORMAL).deriveFont(Font.ITALIC));

    // Some feedback on hover.
    myCancelProgressButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myCancelProgressButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myListener.actionCanceled();
      }
    });

    myInstallActionLink.setEnabled(getSdkComboPath().trim().isEmpty());

    errorIcon.setVisible(false);
    errorPane.setVisible(false);
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

  @Override
  public boolean validate() {
    final ValidationInfo info = validateSdk();
    if (info != null) {
      errorText.setText(XmlStringUtil.wrapInHtml(info.message));
    }
    errorIcon.setVisible(info != null);
    errorPane.setVisible(info != null);

    myInstallActionLink.setEnabled(info != null || getSdkComboPath().trim().isEmpty());

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

  @Override
  @NotNull
  public ComboboxWithBrowseButton getSdkComboBox() {
    return mySdkPathComboWithBrowse;
  }

  @Override
  public void setSdkPath(@NotNull String sdkPath) {
    getSdkEditor().setItem(sdkPath);
  }

  @Override
  public JProgressBar getProgressBar() {
    return myProgressBar;
  }

  @Override
  public LinkLabel getInstallActionLink() {
    return myInstallActionLink;
  }

  @Override
  public JTextPane getProgressText() {
    return myProgressText;
  }

  @Override
  public JLabel getCancelProgressButton() {
    return myCancelProgressButton;
  }

  /**
   * Set error details (pass null to hide).
   */
  @Override
  public void setErrorDetails(@Nullable String details) {
    final boolean makeVisible = details != null;
    if (makeVisible) {
      errorText.setText(details);
    }
    errorIcon.setVisible(makeVisible);
    errorPane.setVisible(makeVisible);
  }

  @Override
  public void addCancelActionListener(InstallSdkAction.CancelActionListener listener) {
    myListener = listener;
  }

  @Override
  public void requestNextStep() {
    final AbstractProjectWizard wizard = (AbstractProjectWizard)myContext.getWizard();
    if (wizard != null) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> wizard.doNextAction());
    }
  }
}
