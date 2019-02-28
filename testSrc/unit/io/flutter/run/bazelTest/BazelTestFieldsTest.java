/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BazelTestFieldsTest {

  @Test
  public void shouldReadFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "testName", "Test number one");
    addOption(elt, "entryFile", "/tmp/test/dir/lib/main.dart");
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello");

    final BazelTestFields fields = BazelTestFields.readFrom(elt);
    assertEquals("Test number one", fields.getTestName());
    assertEquals("/tmp/test/dir/lib/main.dart", fields.getEntryFile());
    assertEquals("//path/to/flutter/app:hello", fields.getBazelTarget());
  }

  @Test
  public void shouldUpgradeFieldsFromOldXml() {
    final Element elt = new Element("test");
    addOption(elt, "launchingScript", "path/to/bazel-run.sh"); // obsolete
    addOption(elt, "entryFile", "/tmp/test/dir/lib/main.dart"); // obsolete
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello");

    final BazelTestFields fields = BazelTestFields.readFrom(elt);
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals(null, fields.getTestName());
    assertEquals("/tmp/test/dir/lib/main.dart", fields.getEntryFile());
    assertEquals("//path/to/flutter/app:hello", fields.getBazelTarget());
  }

  @Test
  public void roundTripShouldPreserveFields() {
    final BazelTestFields before = new BazelTestFields(
      "Test number two",
      "/tmp/foo/lib/main_two.dart",
      "//path/to/flutter/app:hello2"
    );

    final Element elt = new Element("test");
    before.writeTo(elt);

    // Verify that we no longer write workingDirectory.
    assertArrayEquals(
      new String[]{"bazelTarget", "entryFile", "testName"},
      getOptionNames(elt).toArray());

    final BazelTestFields after = BazelTestFields.readFrom(elt);
    assertEquals("Test number two", after.getTestName());
    assertEquals("/tmp/foo/lib/main_two.dart", after.getEntryFile());
    assertEquals("//path/to/flutter/app:hello2", after.getBazelTarget());
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
