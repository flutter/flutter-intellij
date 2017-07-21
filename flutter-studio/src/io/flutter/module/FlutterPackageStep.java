/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.validator.ModuleValidator;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.idea.wizard.model.WizardModel;
import com.android.tools.swing.util.FormScalingUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ComboboxWithBrowseButton;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;

import static com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse;

public class FlutterPackageStep extends SkippableWizardStep<FlutterModuleModel> {

  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final ValidatorPanel myValidatorPanel;
  private JPanel myPanel;
  private JTextField myPackageName;
  private TextFieldWithBrowseButton myContentRoot;
  private TextFieldWithBrowseButton myPackageFileLocation;
  private ComboboxWithBrowseButton myFlutterSdkPath;
  // TODO(messick): Update the root panel with whatever the latest and greatest is.
  @NotNull private final StudioWizardStepPanel myRootPanel;

  public FlutterPackageStep(@NotNull FlutterModuleModel model, @NotNull String title, @NotNull Icon icon) {
    super(model, title, icon);
    myBindings.bindTwoWay(new TextProperty(myPackageName), model.packageName());
    myBindings.bindTwoWay(new TextProperty(myContentRoot.getTextField()), model.contentRoot());
    myBindings.bindTwoWay(new TextProperty(myContentRoot.getTextField()), model.packageFileLocation());
    myBindings.bindTwoWay(new TextProperty(myPackageFileLocation.getTextField()), model.packageFileLocation());
    initDartFileTextWithBrowse(model.getProject(), myContentRoot);
    initDartFileTextWithBrowse(model.getProject(), myPackageFileLocation);
    myFlutterSdkPath.getComboBox().setEditable(true);
    FlutterSdkUtil.addKnownSDKPathsToCombo(myFlutterSdkPath.getComboBox());
    myFlutterSdkPath.addBrowseFolderListener(FlutterBundle.message("flutter.sdk.browse.path.label"), null, null,
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                                                     TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
    myFlutterSdkPath.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        model.flutterSdk().set((String)myFlutterSdkPath.getComboBox().getSelectedItem());
      }
    });

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myValidatorPanel.registerValidator(model.packageName(), new ModuleValidator(model.getProject()));
    myValidatorPanel.registerValidator(model.contentRoot(), contentRoot ->
      !contentRoot.isEmpty()
      ? Validator.Result.OK
      : new Validator.Result(Validator.Severity.ERROR, FlutterBundle.message("flutter.wizard.please_select_content_root")));
    myValidatorPanel.registerValidator(model.packageFileLocation(), packageFileLocation ->
      !packageFileLocation.isEmpty()
      ? Validator.Result.OK
      : new Validator.Result(Validator.Severity.ERROR, FlutterBundle.message("flutter.wizard.please_select_package_file_location")));
    // TODO(messick): Finish validator initialization.

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, FlutterBundle.message("module.wizard.package_step_body"));
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myPackageName;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return new ArrayList<>();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }
}
