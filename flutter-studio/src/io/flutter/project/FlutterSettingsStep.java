/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import static io.flutter.project.FlutterProjectStep.wrappedWithVScroll;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBScrollPane;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import java.awt.Container;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configure Flutter project parameters that relate to platform-specific code.
 * These values are preserved (by the model) for use as defaults when other projects are created.
 */
public class FlutterSettingsStep extends ModelWizardStep<FlutterProjectModel> {
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JBScrollPane myRoot;
  private JPanel myRootPanel;
  private ValidatorPanel myValidatorPanel;

  private JTextField myPackageName;
  private JCheckBox myKotlinCheckBox;
  private JCheckBox mySwiftCheckBox;
  private JLabel myLanguageLabel;
  private boolean hasEntered = false;
  private FocusListener focusListener;

  public FlutterSettingsStep(FlutterProjectModel model, String title, Icon icon) {
    super(model, title, icon);
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    myRoot = wrappedWithVScroll(myRootPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRoot);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    FlutterProjectModel model = getModel();
    myKotlinCheckBox.setText(FlutterBundle.message("module.wizard.language.name_kotlin"));
    mySwiftCheckBox.setText(FlutterBundle.message("module.wizard.language.name_swift"));

    TextProperty packageNameText = new TextProperty(myPackageName);

    Expression<String> computedPackageName = new DomainToPackageExpression(model.companyDomain(), model.projectName()) {
      @Override
      public String get() {
        return super.get().replaceAll("_", "");
      }
    };
    BoolProperty isPackageSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.listen(packageNameText, value -> isPackageSynced.set(value.equals(computedPackageName.get())));

    // The wizard changed substantially in 3.5. Something causes this page to not get properly validated
    // after it is added to the Swing tree. Here we check that we have to validate the tree, then do so.
    // It only needs to be done once, so we remove the listener to prevent possible flicker.
    focusListener = new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        super.focusGained(e);
        Container parent = myRoot;
        while (parent != null && !(parent instanceof JBLayeredPane)) {
          parent = parent.getParent();
        }
        if (parent != null) {
          parent.validate();
        }
        myPackageName.removeFocusListener(focusListener);
      }
    };
    myPackageName.addFocusListener(focusListener);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRoot;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myPackageName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onEntering() {
    Project project = getModel().project().getValueOrNull();
    FlutterSdk sdk = FlutterSdk.forPath(getModel().flutterSdk().get());
    if (hasEntered) {
      return;
    }
    // The language options are not used for modules. Since their values are persistent we cannot
    // establish bindings before knowing the project type.
    if (getModel().isModule()) {
      myLanguageLabel.setVisible(false);
      myKotlinCheckBox.setVisible(false);
      mySwiftCheckBox.setVisible(false);
    }
    else {
      myLanguageLabel.setVisible(true);
      myKotlinCheckBox.setVisible(true);
      mySwiftCheckBox.setVisible(true);
      myBindings.bindTwoWay(new SelectedProperty(myKotlinCheckBox), getModel().useKotlin());
      myBindings.bindTwoWay(new SelectedProperty(mySwiftCheckBox), getModel().useSwift());
    }
    hasEntered = true;
  }

  @Override
  protected void onProceeding() {
    String fullPackageName = getModel().packageName().get();
    String name = getDomainFromPackage(fullPackageName);
    if (name != null) {
      // Saving the domain resets the package name to default because package name is used by two wizard pages' binding managers.
      getModel().companyDomain().set(name);
      // Using getModel().packageName().set() triggers the same reset.
      // Setting the text field restores the model's package name to what the user edited it to be w/o triggering the reset.
      myPackageName.setText(fullPackageName);
    }
  }

  @Nullable
  private static String getDomainFromPackage(String packageName) {
    List<String> split = new ArrayList<>(Arrays.asList(packageName.split("\\.")));
    if (split.isEmpty()) {
      return null;
    }
    split.remove(split.size() - 1);
    Collections.reverse(split);
    StringBuilder builder = new StringBuilder();
    StringUtil.join(split, ".", builder);
    return builder.toString();
  }
}
