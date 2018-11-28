/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import io.flutter.dart.DartPlugin;
import io.flutter.run.MainFile;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.testing.FlutterModuleFixture;
import io.flutter.testing.FlutterTestUtils;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.*;

/**
 * Verifies run configuration persistence.
 */
public class BazelFieldsTest {
  @Test
  public void shouldReadFieldsFromXml() {
    final Element elt = new Element("test");
    addOption(elt, "entryFile", "/tmp/test/dir/lib/main.dart");
    addOption(elt, "launchingScript", "path/to/bazel-run.sh");
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello");
    addOption(elt, "enableReleaseMode", "true");
    addOption(elt, "additionalArgs", "--android_cpu=x86");

    final BazelFields fields = new BazelFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("/tmp/test/dir/lib/main.dart", fields.getEntryFile());
    assertEquals("path/to/bazel-run.sh", fields.getLaunchingScript());
    assertEquals("//path/to/flutter/app:hello", fields.getBazelTarget());
    assertEquals(true, fields.getEnableReleaseMode());
    assertEquals("--android_cpu=x86", fields.getAdditionalArgs());
  }

  @Test
  public void shouldUpgradeFieldsFromOldXml() {
    final Element elt = new Element("test");
    addOption(elt, "workingDirectory", "/tmp/test/dir"); // obsolete
    addOption(elt, "launchingScript", "path/to/bazel-run.sh");
    addOption(elt, "bazelTarget", "//path/to/flutter/app:hello");
    addOption(elt, "additionalArgs", "--android_cpu=x86");

    final BazelFields fields = new BazelFields();
    XmlSerializer.deserializeInto(fields, elt);
    assertEquals("/tmp/test/dir/lib/main.dart", fields.getEntryFile());
    assertEquals("path/to/bazel-run.sh", fields.getLaunchingScript());
    assertEquals("//path/to/flutter/app:hello", fields.getBazelTarget());
    assertEquals("--android_cpu=x86", fields.getAdditionalArgs());
  }

  @Test
  public void roundTripShouldPreserveFields() {
    final BazelFields before = new BazelFields();
    before.setEntryFile("/tmp/foo/lib/main.dart");
    before.setLaunchingScript("launch");
    before.setBazelTarget("target");
    before.setEnableReleaseMode(true);
    before.setAdditionalArgs("args");

    final Element elt = new Element("test");
    XmlSerializer.serializeInto(before, elt, new SkipDefaultValuesSerializationFilters());

    // Verify that we no longer write workingDirectory.
    assertArrayEquals(
      new String[]{"additionalArgs", "bazelTarget", "enableReleaseMode", "entryFile", "launchingScript"},
      getOptionNames(elt).toArray());

    final BazelFields after = new BazelFields();
    XmlSerializer.deserializeInto(after, elt);

    assertEquals("/tmp/foo/lib/main.dart", after.getEntryFile());
    assertEquals("launch", after.getLaunchingScript());
    assertEquals("target", after.getBazelTarget());
    assertEquals(true, after.getEnableReleaseMode());
    assertEquals("args", after.getAdditionalArgs());
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
