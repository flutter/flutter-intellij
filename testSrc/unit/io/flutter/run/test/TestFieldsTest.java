/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import io.flutter.run.test.TestFields.Scope;
import org.jdom.Element;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies that we can read and write test configurations.
 */
public class TestFieldsTest {

  @Test
  public void shouldReadFileFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "testFile", "hello_test.dart");

    final TestFields after = TestFields.readFrom(elt);
    assertEquals(Scope.FILE, after.getScope());
    assertEquals("hello_test.dart", after.getTestFile());
    assertEquals(null, after.getTestDir());
  }

  @Test
  public void shouldReadDirectoryFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "testDir", "test/dir");

    final TestFields after = TestFields.readFrom(elt);
    assertEquals(Scope.DIRECTORY, after.getScope());
    assertEquals(null, after.getTestFile());
    assertEquals("test/dir", after.getTestDir());
  }

  @Test
  public void roundTripShouldPreserveFileSettings() {
    final Element elt = new Element("test");
    TestFields.forFile("hello_test.dart").writeTo(elt);

    final TestFields after = TestFields.readFrom(elt);
    assertEquals(Scope.FILE, after.getScope());
    assertEquals("hello_test.dart", after.getTestFile());
    assertEquals(null, after.getTestDir());
  }

  @Test
  public void roundTripShouldPreserveDirectorySettings() {
    final Element elt = new Element("test");
    TestFields.forDir("test/dir").writeTo(elt);

    final TestFields after = TestFields.readFrom(elt);
    assertEquals(Scope.DIRECTORY, after.getScope());
    assertEquals(null, after.getTestFile());
    assertEquals("test/dir", after.getTestDir());
  }

  private void addOption(Element elt, String name, String value) {
    final Element child = new Element("option");
    child.setAttribute("name", name);
    child.setAttribute("value", value);
    elt.addContent(child);
  }
}
