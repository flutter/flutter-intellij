/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import io.flutter.sdk.FlutterSdk;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A configuration for running Flutter tests.
 */
public class TestConfig extends LocatableConfigurationBase {
  @NotNull
  private TestFields fields = TestFields.forFile("");

  protected TestConfig(@NotNull Project project,
                       @NotNull ConfigurationFactory factory, String name) {
    super(project, factory, name);
  }

  @NotNull
  TestFields getFields() {
    return fields;
  }

  void setFields(@NotNull TestFields fields) {
    this.fields = fields;
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    super.writeExternal(element);
    fields.writeTo(element);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    fields = TestFields.readFrom(element);
  }

  @Override
  public RunConfiguration clone() {
    final TestConfig result = (TestConfig)super.clone();
    result.fields = fields;
    return result;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new TestForm(getProject());
  }

  @Nullable
  @Override
  public String suggestedName() {
    return fields.getSuggestedName(getProject(), "Flutter Tests");
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    fields.checkRunnable(getProject());
  }

  @Nullable
  public FlutterSdk getSdk() {
    return FlutterSdk.getFlutterSdk(getProject());
  }

  @NotNull
  @Override
  public CommandLineState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return TestLaunchState.create(env, this);
  }
}
