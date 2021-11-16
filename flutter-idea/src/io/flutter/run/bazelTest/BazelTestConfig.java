/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * The Bazel version of the {@link io.flutter.run.test.TestConfig}.
 */
public class BazelTestConfig extends LocatableConfigurationBase<CommandLineState> {
  @NotNull private BazelTestFields fields;

  BazelTestConfig(@NotNull final Project project, @NotNull final ConfigurationFactory factory, @NotNull final String name) {
    super(project, factory, name);
    fields = new BazelTestFields(null, null, null, null);
  }

  @NotNull
  BazelTestFields getFields() {
    return fields;
  }

  void setFields(@NotNull BazelTestFields newFields) {
    fields = newFields;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    fields.checkRunnable(getProject());
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new FlutterBazelTestConfigurationEditorForm(getProject());
  }

  @NotNull
  @Override
  public CommandLineState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    return BazelTestLaunchState.create(env, this);
  }

  @NotNull
  public BazelTestConfig clone() {
    final BazelTestConfig clone = (BazelTestConfig)super.clone();
    clone.fields = fields.copy();
    return clone;
  }

  @NotNull
  RunConfiguration copyTemplateToNonTemplate(String name) {
    final BazelTestConfig copy = (BazelTestConfig)super.clone();
    copy.setName(name);
    copy.fields = fields.copyTemplateToNonTemplate(getProject());
    return copy;
  }

  @Override
  public void writeExternal(@NotNull final Element element) throws WriteExternalException {
    super.writeExternal(element);
    fields.writeTo(element);
  }

  @Override
  public void readExternal(@NotNull final Element element) throws InvalidDataException {
    super.readExternal(element);
    fields = BazelTestFields.readFrom(element);
  }
}
