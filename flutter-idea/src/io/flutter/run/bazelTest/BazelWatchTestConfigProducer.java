/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Modifies a bazel test configuration to watch a test instead of one-off running it.
 */
public class BazelWatchTestConfigProducer extends BazelTestConfigProducer {
  BazelWatchTestConfigProducer() {
    super(FlutterBazelTestConfigurationType.getInstance().watchFactory);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull BazelTestConfig config,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    if (!super.setupConfigurationFromContext(config, context, sourceElement)) return false;
    config.setName(config.getName().replaceFirst("Run", "Watch"));
    return true;
  }
}
