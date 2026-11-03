/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.jdom.Element;
import org.junit.Rule;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SdkRunConfig} XML serialization.
 */
public class SdkRunConfigTest {

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void writeExternalOmitsDefaultSdkFields() {
    final SdkRunConfig config =
      new SdkRunConfig(fixture.getProject(), FlutterRunConfigurationType.getInstance().getFactory(), "Flutter");

    final Element element = new Element("configuration");
    config.writeExternal(element);

    final Set<String> optionNames = getOptionNames(element);
    assertFalse(optionNames.contains("filePath"));
    assertFalse(optionNames.contains("buildFlavor"));
    assertFalse(optionNames.contains("additionalArgs"));
    assertFalse(optionNames.contains("attachArgs"));
    assertTrue(element.getChildren("envs").isEmpty());
  }

  @Test
  public void writeAndReadExternalRoundTripsNonDefaultSdkFields() {
    final SdkRunConfig original =
      new SdkRunConfig(fixture.getProject(), FlutterRunConfigurationType.getInstance().getFactory(), "Flutter");
    final SdkFields fields = new SdkFields();
    fields.setFilePath("lib/main.dart");
    fields.setBuildFlavor("staging");
    fields.setAdditionalArgs("--trace-startup");
    fields.setAttachArgs("--debug-uri=http://127.0.0.1:8181/");
    fields.setEnvs(Map.of("FLUTTER_TEST_ENV", "1"));
    original.setFields(fields);

    final Element element = new Element("configuration");
    original.writeExternal(element);

    final Set<String> optionNames = getOptionNames(element);
    assertTrue(optionNames.contains("filePath"));
    assertTrue(optionNames.contains("buildFlavor"));
    assertTrue(optionNames.contains("additionalArgs"));
    assertTrue(optionNames.contains("attachArgs"));

    final SdkRunConfig restored =
      new SdkRunConfig(fixture.getProject(), FlutterRunConfigurationType.getInstance().getFactory(), "Flutter");
    restored.readExternal(element);

    assertEquals("lib/main.dart", restored.getFields().getFilePath());
    assertEquals("staging", restored.getFields().getBuildFlavor());
    assertEquals("--trace-startup", restored.getFields().getAdditionalArgs());
    assertEquals("--debug-uri=http://127.0.0.1:8181/", restored.getFields().getAttachArgs());
    assertEquals(Map.of("FLUTTER_TEST_ENV", "1"), restored.getFields().getEnvs());
  }

  private static Set<String> getOptionNames(Element element) {
    final Set<String> names = new TreeSet<>();
    for (Element child : element.getChildren("option")) {
      final String name = child.getAttributeValue("name");
      if (name != null) {
        names.add(name);
      }
    }
    return names;
  }
}
