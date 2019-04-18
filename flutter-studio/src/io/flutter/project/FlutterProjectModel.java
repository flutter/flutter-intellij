/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import io.flutter.module.FlutterProjectType;
import io.flutter.samples.FlutterSample;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Note that a single instance of this class is shared among all the steps in the wizard.
 * <p>
 * There is some inconsistency in which values are saved and which reset when switching from
 * one project type to another. The AS New Module wizard has similar inconsistencies (as of beta 5).
 * <p>
 * TODO(messick): Add tests to simulate clicking Next/Previous buttons and choosing different project types.
 */
public class FlutterProjectModel extends WizardModel {
  private static final String DEFAULT_DOMAIN = "example.com"; // Keep this in (reversed) sync with 'flutter create'.
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
  @NotNull final private BoolValueProperty myIsOfflineSelected = new BoolValueProperty();
  private FlutterSample mySelectedSample;

  public FlutterProjectModel(@NotNull FlutterProjectType type) {
    myProjectType.set(new OptionalValueProperty<>(type));

    myCompanyDomain.addListener(() -> {
      String domain = myCompanyDomain.get();
      if (domain.isEmpty()) {
        domain = null; // Keys with null values are deleted.
      }
      PropertiesComponent.getInstance().setValue(PROPERTIES_DOMAIN_KEY, domain);
    });

    myProjectName.addConstraint(String::trim);

    myKotlin.set(getInitialKotlinSupport());
    myKotlin.addListener(() -> setInitialKotlinSupport(myKotlin.get()));

    mySwift.set(getInitialSwiftSupport());
    mySwift.addListener(() -> setInitialSwiftSupport(mySwift.get()));
  }

  public void setSample(@Nullable FlutterSample sample) {
    mySelectedSample = sample;
  }

  @Nullable
  public FlutterSample getSample() {
    return mySelectedSample;
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

  public boolean isModule() {
    return projectType().getValue() == FlutterProjectType.MODULE;
  }

  @Override
  protected void handleFinished() {
    String location = myProjectLocation.get();
    assert (!myProjectName.get().isEmpty());
    assert (!myFlutterSdk.get().isEmpty());
    assert (!location.isEmpty());
    if (location.endsWith("/")) {
      location = location.substring(0, location.length() - 1);
    }
    if (!FlutterProjectCreator.finalValidityCheckPassed(location)) {
      // TODO(messick): Navigate to the step that sets location (if that becomes possible in the AS wizard framework).
      // See NewProjectModel.doDryRun();
      return;
    }
    new FlutterProjectCreator(this).createProject();
  }

  public BoolValueProperty isOfflineSelected() {
    return myIsOfflineSelected;
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
}
