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

import static org.junit.Assert.assertEquals;

/**
 * Verifies run configuration persistence.
 */
public class SdkFieldsTest {

  @Test
  public void shouldReadFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "filePath", "lib/main.dart");
    addOption(elt, "workingDirectory", "/tmp/test/example");

    final SdkFields fields = new SdkFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("lib/main.dart", fields.getFilePath());
    assertEquals("/tmp/test/example", fields.getWorkingDirectory());
  }

  @Test
  public void roundTripShouldPreserveFields() {
    final SdkFields before = new SdkFields();
    before.setFilePath("main.dart");
    before.setWorkingDirectory("work");

    final Element elt = new Element("test");
    XmlSerializer.serializeInto(before, elt, new SkipDefaultValuesSerializationFilters());

    final SdkFields after = new SdkFields();
    XmlSerializer.deserializeInto(after, elt);
    assertEquals("main.dart", before.getFilePath());
    assertEquals("work", after.getWorkingDirectory());
  }


  private void addOption(Element elt, String name, String value) {
    final Element child = new Element("option");
    child.setAttribute("name", name);
    child.setAttribute("value", value);
    elt.addContent(child);
  }
}
