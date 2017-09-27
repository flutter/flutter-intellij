/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import io.flutter.module.FlutterProjectType;
import org.jetbrains.annotations.NotNull;

/**
 * Note that a single instance of this class is shared among all the steps in the wizard.
 *
 * There is some inconsistency in which values are saved and which reset when switching from
 * one project type to another. The AS New Module wizard has similar inconsistencies (as of beta 5).
 *
 * TODO(messick): Add tests to simulate clicking Next/Previous buttons and choosing different project types.
 */
public class FlutterProjectModel extends WizardModel {
  private static final String DEFAULT_DOMAIN = "yourcompany.com"; // Keep this in (reversed) sync with 'flutter create'.
  private static final String PROPERTIES_DOMAIN_KEY = "FLUTTER_COMPANY_DOMAIN";
  private static final String PROPERTIES_KOTLIN_SUPPORT_KEY = "FLUTTER_PROJECT_KOTLIN_SUPPORT";
  private static final String PROPERTIES_SWIFT_SUPPORT_KEY = "FLUTTER_PROJECT_SWIFT_SUPPORT";

  @NotNull final private OptionalValueProperty<FlutterProjectType> myProjectType = new OptionalValueProperty<>();
  @NotNull final private StringProperty myFlutterSdk = new StringValueProperty();
  @NotNull final private StringProperty myProjectName = new StringValueProperty();
  @NotNull final private StringProperty myPackageName = new StringValueProperty();
  @NotNull final private StringProperty myProjectLocation = new StringValueProperty();
  @NotNull final private StringProperty myCompanyDomain = new StringValueProperty(getInitialDomain());
  @NotNull final private StringProperty myDescription = new StringValueProperty();
  @NotNull final private BoolValueProperty myKotlin = new BoolValueProperty();
  @NotNull final private BoolValueProperty mySwift = new BoolValueProperty();
  @NotNull final private OptionalProperty<Project> myProject = new OptionalValueProperty<>();

  public FlutterProjectModel(@NotNull FlutterProjectType type) {
    myProjectType.set(new OptionalValueProperty<>(type));

    myCompanyDomain.addListener(sender -> {
      String domain = myCompanyDomain.get();
      if (domain.isEmpty()) {
        domain = null; // Keys with null values are deleted.
      }
      PropertiesComponent.getInstance().setValue(PROPERTIES_DOMAIN_KEY, domain);
    });

    myProjectName.addConstraint(String::trim);

    myKotlin.set(getInitialKotlinSupport());
    myKotlin.addListener(sender -> setInitialKotlinSupport(myKotlin.get()));

    mySwift.set(getInitialKotlinSupport());
    mySwift.addListener(sender -> setInitialKotlinSupport(mySwift.get()));
  }

  @NotNull
  private static String getInitialDomain() {
    String domain = PropertiesComponent.getInstance().getValue(PROPERTIES_DOMAIN_KEY);
    return domain == null ? DEFAULT_DOMAIN : domain;
  }

  private static boolean getInitialKotlinSupport() {
    return PropertiesComponent.getInstance().isTrueValue(PROPERTIES_KOTLIN_SUPPORT_KEY);
  }

  private static void setInitialKotlinSupport(boolean isSupported) {
    PropertiesComponent.getInstance().setValue(PROPERTIES_KOTLIN_SUPPORT_KEY, isSupported);
  }

  private static boolean getInitialSwiftSupport() {
    return PropertiesComponent.getInstance().isTrueValue(PROPERTIES_SWIFT_SUPPORT_KEY);
  }

  private static void setInitialSwiftSupport(boolean isSupported) {
    PropertiesComponent.getInstance().setValue(PROPERTIES_SWIFT_SUPPORT_KEY, isSupported);
  }

  @NotNull
  public OptionalValueProperty<FlutterProjectType> projectType() {
    return myProjectType;
  }

  @NotNull
  public StringProperty projectName() {
    return myProjectName;
  }

  @NotNull
  public StringProperty projectLocation() {
    return myProjectLocation;
  }

  @NotNull
  public StringProperty companyDomain() {
    return myCompanyDomain;
  }

  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  @NotNull
  public StringProperty description() {
    return myDescription;
  }

  @NotNull
  public BoolValueProperty useKotlin() {
    return myKotlin;
  }

  @NotNull
  public BoolValueProperty useSwift() {
    return mySwift;
  }

  @NotNull
  public StringProperty flutterSdk() {
    return myFlutterSdk;
  }

  @NotNull
  public OptionalProperty<Project> project() {
    return myProject;
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @Override
  protected void handleFinished() {
    String location = myProjectLocation.get();
    assert (!myProjectName.get().isEmpty());
    assert (!myFlutterSdk.get().isEmpty());
    assert (!location.isEmpty());
    if (!FlutterProjectCreator.finalValidityCheckPassed(location)) {
      // TODO(messick): Navigate to the step that sets location (if that becomes possible in the AS wizard framework).
      // See NewProjectModel.doDryRun();
      return;
    }
    new FlutterProjectCreator(this).createProject();
  }
}
