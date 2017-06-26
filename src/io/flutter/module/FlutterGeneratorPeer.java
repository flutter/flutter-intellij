/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBProgressBar;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ReflectionUtil;
import com.intellij.xml.util.XmlStringUtil;
import io.flutter.FlutterBundle;
import io.flutter.actions.InstallSdkAction;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FlutterGeneratorPeer {
  private final WizardContext myContext;
  private JPanel myMainPanel;
  private ComboboxWithBrowseButton mySdkPathComboWithBrowse;
  private JBLabel myVersionContent;
  private JLabel errorIcon;
  private JTextPane errorText;
  private JScrollPane errorPane;
  private LinkLabel myInstallActionLink;
  private JBProgressBar myProgressBar;
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
    validate();
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
      protected void textChanged(DocumentEvent e) {
        validate();
      }
    });

    myInstallActionLink.setIcon(myInstallSdkAction.getLinkIcon());
    myInstallActionLink.setText(myInstallSdkAction.getLinkText());

    //noinspection unchecked
    myInstallActionLink.setListener((label, linkUrl) -> myInstallSdkAction.actionPerformed(null), null);

    // Some feedback on hover.
    myCancelProgressButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myCancelProgressButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myListener.actionCanceled();
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
    return FileUtilRt.toSystemIndependentName(getSdkEditor().getItem().toString().trim());
  }

  @NotNull
  public ComboBoxEditor getSdkEditor() {
    return mySdkPathComboWithBrowse.getComboBox().getEditor();
  }

  @NotNull
  public ComboboxWithBrowseButton getSdkComboBox() {
    return mySdkPathComboWithBrowse;
  }

  public void setSdkPath(@NotNull String sdkPath) {
    getSdkEditor().setItem(sdkPath);
  }

  public JBProgressBar getProgressBar() {
    return myProgressBar;
  }

  public LinkLabel getInstallActionLink() {
    return myInstallActionLink;
  }

  public JTextPane getProgressText() {
    return myProgressText;
  }

  public JLabel getCancelProgressButton() {
    return myCancelProgressButton;
  }

  /**
   * Set error details (pass null to hide).
   */
  public void setErrorDetails(@Nullable String details) {
    final boolean makeVisible = details != null;
    if (makeVisible) {
      errorText.setText(details);
    }
    errorIcon.setVisible(makeVisible);
    errorPane.setVisible(makeVisible);
  }

  public void addCancelActionListener(InstallSdkAction.CancelActionListener listener) {
    myListener = listener;
  }

  public void requestNextStep() {
    final AbstractWizard wizard = myContext.getWizard();
    if (wizard != null) {
      // AbstractProjectWizard makes `doNextAction` public but we can't reference it directly since it does not exist in WebStorm.
      final Method nextAction = ReflectionUtil.getMethod(wizard.getClass(), "doNextAction");
      if (nextAction != null) {
        try {
          nextAction.invoke(wizard);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          // Ignore.
        }
      }
    }
  }
}
