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

import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Verifies run configuration persistence.
 */
public class BazelFieldsTest {

  @Test
  public void shouldReadFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "entryFile", "/tmp/test/dir/lib/main.dart");
    addOption(elt, "additionalArgs", "--android_cpu=x86");
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello");
    addOption(elt, "launchingScript", "path/to/bazel-run.sh");

    final BazelFields fields = new BazelFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("/tmp/test/dir/lib/main.dart", fields.getEntryFile());
    assertEquals("--android_cpu=x86", fields.getAdditionalArgs());
    assertEquals("//path/to/flutter/app:hello", fields.getBazelTarget());
    assertEquals("path/to/bazel-run.sh", fields.getLaunchingScript());
  }

  @Test
  public void shouldUpgradeFieldsFromOldXml() {
    final Element elt = new Element("test");
    addOption(elt, "additionalArgs", "--android_cpu=x86");
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello");
    addOption(elt, "launchingScript", "path/to/bazel-run.sh");
    addOption(elt, "workingDirectory", "/tmp/test/dir"); // obsolete

    final BazelFields fields = new BazelFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("/tmp/test/dir/lib/main.dart", fields.getEntryFile());
    assertEquals("--android_cpu=x86", fields.getAdditionalArgs());
    assertEquals("//path/to/flutter/app:hello", fields.getBazelTarget());
    assertEquals("path/to/bazel-run.sh", fields.getLaunchingScript());
  }

  @Test
  public void roundTripShouldPreserveFields() {
    final BazelFields before = new BazelFields();
    before.setEntryFile("/tmp/foo/lib/main.dart");
    before.setAdditionalArgs("args");
    before.setBazelTarget("target");
    before.setLaunchingScript("launch");

    final Element elt = new Element("test");
    XmlSerializer.serializeInto(before, elt, new SkipDefaultValuesSerializationFilters());

    // Verify that we no longer write workingDirectory.
    assertArrayEquals(
      new String[]{"additionalArgs", "bazelTarget", "entryFile", "launchingScript"},
      getOptionNames(elt).toArray());

    final BazelFields after = new BazelFields();
    XmlSerializer.deserializeInto(after, elt);

    assertEquals("args", after.getAdditionalArgs());
    assertEquals("target", after.getBazelTarget());
    assertEquals("launch", after.getLaunchingScript());
    assertEquals("/tmp/foo/lib/main.dart", after.getEntryFile());
  }

  private void addOption(Element elt, String name, String value) {
    final Element child = new Element("option");
    child.setAttribute("name", name);
    child.setAttribute("value", value);
    elt.addContent(child);
  }

  private Set<String> getOptionNames(Element elt) {
    final Set<String> result = new TreeSet<>();
    for (Element child : elt.getChildren()) {
      result.add(child.getAttributeValue("name"));
    }
    return result;
  }
}
