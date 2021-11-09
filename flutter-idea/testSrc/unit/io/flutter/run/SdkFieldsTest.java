/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

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
public class SdkFieldsTest {

  @Test
  public void shouldReadFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "filePath", "lib/main.dart");
    addOption(elt, "additionalArgs", "--trace-startup");

    final SdkFields fields = new SdkFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("lib/main.dart", fields.getFilePath());
    assertEquals("--trace-startup", fields.getAdditionalArgs());
  }

  @Test
  public void shouldReadFieldsFromOldXml() {
    final Element elt = new Element("test");
    addOption(elt, "filePath", "lib/main.dart");
    addOption(elt, "additionalArgs", "--trace-startup");
    addOption(elt, "workingDirectory", "/tmp/test/example"); // obsolete

    final SdkFields fields = new SdkFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("lib/main.dart", fields.getFilePath());
    assertEquals("--trace-startup", fields.getAdditionalArgs());
  }

  @Test
  public void roundTripShouldPreserveFields() {
    final SdkFields before = new SdkFields();
    before.setFilePath("main.dart");
    before.setAdditionalArgs("--trace-startup");

    final Element elt = new Element("test");
    XmlSerializer.serializeInto(before, elt, new SkipDefaultValuesSerializationFilters());

    // Make sure we no longer serialize workingDirectory
    assertArrayEquals(new String[]{"additionalArgs", "filePath"}, getOptionNames(elt).toArray());

    final SdkFields after = new SdkFields();
    XmlSerializer.deserializeInto(after, elt);
    assertEquals("main.dart", before.getFilePath());
    assertEquals("--trace-startup", before.getAdditionalArgs());
  }

  @Test
  public void supportsSpacesInAdditionalArgs() {
    final SdkFields sdkFields = new SdkFields();
    sdkFields.setAdditionalArgs("--dart-define='VALUE=foo bar' --other=baz");

    assertArrayEquals(new String[]{
      "--dart-define=VALUE=foo bar",
      "--other=baz"
    }, sdkFields.getAdditionalArgsParsed());
  }

  @Test
  public void supportsSpacesInAttachArgs() {
    final SdkFields sdkFields = new SdkFields();
    sdkFields.setAttachArgs("--dart-define='VALUE=foo bar' --other=baz");

    assertArrayEquals(new String[]{
      "--dart-define=VALUE=foo bar",
      "--other=baz"
    }, sdkFields.getAttachArgsParsed());
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
