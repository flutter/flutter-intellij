/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.util.xmlb.XmlSerializer;
import io.flutter.run.daemon.DevToolsInstance;
import org.jdom.Element;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * Verifies run configuration persistence.
 */
public class BazelFieldsTest {
  @Before
  public void setUp() {
    final CompletableFuture<DevToolsInstance> future = new CompletableFuture<>();
    future.complete(new DevToolsInstance("http://localhost", 1234));
  }

  @Test
  public void shouldReadFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "target", "//path/to/flutter/app:hello");
    addOption(elt, "enableReleaseMode", "false");
    addOption(elt, "additionalArgs", "--android_cpu=x86");
    addOption(elt, "bazelArgs", "--define=release_channel=beta3");

    final BazelFields fields = BazelFields.readFrom(elt);
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("//path/to/flutter/app:hello", fields.getTarget());
    assertEquals("--define=release_channel=beta3", fields.getBazelArgs());
    assertEquals("--android_cpu=x86", fields.getAdditionalArgs());
    assertFalse(fields.getEnableReleaseMode());
  }

  @Test
  public void shouldUpgradeFieldsFromOldXml() {
    final Element elt = new Element("test");
    addOption(elt, "entryFile", "/tmp/test/dir/lib/main.dart"); // obsolete
    addOption(elt, "launchingScript", "path/to/bazel-run.sh"); // obsolete
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello"); // obsolete
    addOption(elt, "enableReleaseMode", "true");
    addOption(elt, "additionalArgs", "--android_cpu=x86");

    final BazelFields fields = BazelFields.readFrom(elt);
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("//path/to/flutter/app:hello", fields.getTarget());
    assertNull(fields.getBazelArgs());
    assertEquals("--android_cpu=x86", fields.getAdditionalArgs());
    assertTrue(fields.getEnableReleaseMode());
  }

  @Test
  public void roundTripShouldPreserveFields() {
    final BazelFields before = new BazelFields(
      "bazel_or_dart_target",
      "bazel_args --1 -2=3",
      "additional_args --1 --2=3",
      true
    );

    final Element elt = new Element("test");
    before.writeTo(elt);

    // Verify that we no longer write workingDirectory.
    assertArrayEquals(
      new String[]{"additionalArgs", "bazelArgs", "enableReleaseMode", "target"},
      getOptionNames(elt).toArray());

    final BazelFields after = BazelFields.readFrom(elt);

    assertEquals("bazel_or_dart_target", after.getTarget());
    assertEquals("bazel_args --1 -2=3", after.getBazelArgs());
    assertEquals("additional_args --1 --2=3", after.getAdditionalArgs());
    assertTrue(after.getEnableReleaseMode());
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
