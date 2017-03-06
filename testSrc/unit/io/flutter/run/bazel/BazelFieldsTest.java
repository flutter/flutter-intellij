/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies run configuration persistence.
 */
public class BazelFieldsTest {

  @Test
  public void shouldReadFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "additionalArgs", "--android_cpu=x86");
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello");
    addOption(elt, "launchingScript", "path/to/bazel-run.sh");
    addOption(elt, "workingDirectory", "/tmp/test/dir");

    final BazelFields fields = new BazelFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("--android_cpu=x86", fields.getAdditionalArgs());
    assertEquals("//path/to/flutter/app:hello", fields.getBazelTarget());
    assertEquals("path/to/bazel-run.sh", fields.getLaunchingScript());
    assertEquals("/tmp/test/dir", fields.getWorkingDirectory());
  }

  @Test
  public void roundTripShouldPreserveFields() {
    final BazelFields before = new BazelFields();
    before.setAdditionalArgs("args");
    before.setBazelTarget("target");
    before.setLaunchingScript("launch");
    before.setWorkingDirectory("work");

    final Element elt = new Element("test");
    XmlSerializer.serializeInto(before, elt, new SkipDefaultValuesSerializationFilters());

    final BazelFields after = new BazelFields();
    XmlSerializer.deserializeInto(after, elt);

    assertEquals("args", after.getAdditionalArgs());
    assertEquals("target", after.getBazelTarget());
    assertEquals("launch", after.getLaunchingScript());
    assertEquals("work", after.getWorkingDirectory());
  }

  private void addOption(Element elt, String name, String value) {
    final Element child = new Element("option");
    child.setAttribute("name", name);
    child.setAttribute("value", value);
    elt.addContent(child);
  }
}
