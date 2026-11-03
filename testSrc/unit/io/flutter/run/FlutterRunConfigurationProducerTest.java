/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.actions.RunConfigurationProducer;
import io.flutter.run.test.FlutterTestConfigProducer;
import io.flutter.run.test.FlutterTestConfigType;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Unit tests for {@link FlutterRunConfigurationProducer} and {@link FlutterTestConfigProducer}.
 */
public class FlutterRunConfigurationProducerTest {

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void flutterRunConfigurationProducerLazilyResolvesFactoryAndType() {
    final FlutterRunConfigurationProducer registered =
      RunConfigurationProducer.getInstance(FlutterRunConfigurationProducer.class);
    assertNotNull(registered);
    assertSame(FlutterRunConfigurationType.getInstance().getFactory(), registered.getConfigurationFactory());
    assertSame(FlutterRunConfigurationType.getInstance(), registered.getConfigurationType());

    final FlutterRunConfigurationProducer direct = new FlutterRunConfigurationProducer();
    assertSame(FlutterRunConfigurationType.getInstance().getFactory(), direct.getConfigurationFactory());
    assertSame(FlutterRunConfigurationType.getInstance(), direct.getConfigurationType());
  }

  @Test
  public void flutterTestConfigProducerLazilyResolvesFactoryAndType() {
    final FlutterTestConfigProducer registered =
      RunConfigurationProducer.getInstance(FlutterTestConfigProducer.class);
    assertNotNull(registered);
    assertSame(FlutterTestConfigType.getInstance().getConfigurationFactories()[0], registered.getConfigurationFactory());
    assertSame(FlutterTestConfigType.getInstance(), registered.getConfigurationType());

    final FlutterTestConfigProducer direct = new FlutterTestConfigProducer();
    assertSame(FlutterTestConfigType.getInstance().getConfigurationFactories()[0], direct.getConfigurationFactory());
    assertSame(FlutterTestConfigType.getInstance(), direct.getConfigurationType());
  }
}
