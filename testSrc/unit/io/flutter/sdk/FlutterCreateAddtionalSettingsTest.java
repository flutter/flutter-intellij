/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import io.flutter.module.FlutterProjectType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class FlutterCreateAddtionalSettingsTest {
  @Test
  public void includeDriverPropertyTest() {
    final FlutterCreateAdditionalSettings addtionalSettings1 =
      new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(true).build();
    final FlutterCreateAdditionalSettings addtionalSettings2 =
      new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(false).build();
    final FlutterCreateAdditionalSettings addtionalSettings3 =
      new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(null).build();

    final List<String> args1 = addtionalSettings1.getArgs();
    final List<String> args2 = addtionalSettings2.getArgs();
    final List<String> args3 = addtionalSettings3.getArgs();

    assertEquals(1, args1.size());
    assertEquals("--with-driver-test", args1.get(0));

    assertEquals(0, args2.size());
    assertEquals(0, args3.size());
  }

  @Test
  public void generatePluginPropertyTest() {
    final FlutterCreateAdditionalSettings addtionalSettings1 =
      new FlutterCreateAdditionalSettings.Builder().setType(FlutterProjectType.APP).build();
    final FlutterCreateAdditionalSettings addtionalSettings2 =
      new FlutterCreateAdditionalSettings.Builder().setType(FlutterProjectType.PLUGIN).build();
    final FlutterCreateAdditionalSettings addtionalSettings3 =
      new FlutterCreateAdditionalSettings.Builder().setType(FlutterProjectType.PACKAGE).build();
    final FlutterCreateAdditionalSettings addtionalSettings4 =
      new FlutterCreateAdditionalSettings.Builder().setType(null).build();

    final List<String> args1 = addtionalSettings1.getArgs();
    final List<String> args2 = addtionalSettings2.getArgs();
    final List<String> args3 = addtionalSettings3.getArgs();
    final List<String> args4 = addtionalSettings4.getArgs();

    assertEquals(2, args1.size());
    assertEquals("app", args1.get(1));

    assertEquals(2, args2.size());
    assertEquals("plugin", args2.get(1));

    assertEquals(2, args3.size());
    assertEquals("package", args3.get(1));

    assertEquals(0, args4.size());
  }

  @Test
  public void descriptionPropertyTest() {
    final String d = "My description.";
    final FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setDescription(d).build();
    final FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setDescription(" ").build();
    final FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setDescription(null).build();

    final List<String> args1 = addtionalSettings1.getArgs();
    final List<String> args2 = addtionalSettings2.getArgs();
    final List<String> args3 = addtionalSettings3.getArgs();

    assertEquals(2, args1.size());
    assertEquals("--description", args1.get(0));
    assertEquals(d, args1.get(1));

    assertEquals(0, args2.size());
    assertEquals(0, args3.size());
  }

  @Test
  public void orgPropertyTest() {
    final String d = "tld.domain";
    final FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setOrg(d).build();
    final FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setOrg(" ").build();
    final FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setOrg(null).build();

    final List<String> args1 = addtionalSettings1.getArgs();
    final List<String> args2 = addtionalSettings2.getArgs();
    final List<String> args3 = addtionalSettings3.getArgs();

    assertEquals(2, args1.size());
    assertEquals("--org", args1.get(0));
    assertEquals(d, args1.get(1));

    assertEquals(0, args2.size());
    assertEquals(0, args3.size());
  }

  @Test
  public void iosPropertyTest() {
    final FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setSwift(true).build();
    final FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setSwift(false).build();
    final FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setSwift(null).build();

    final List<String> args1 = addtionalSettings1.getArgs();
    final List<String> args2 = addtionalSettings2.getArgs();
    final List<String> args3 = addtionalSettings3.getArgs();

    assertEquals(2, args1.size());
    assertEquals("--ios-language", args1.get(0));

    assertEquals(0, args2.size());
    assertEquals(0, args3.size());
  }

  @Test
  public void kotlinPropertyTest() {
    final FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setKotlin(true).build();
    final FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setKotlin(false).build();
    final FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setKotlin(null).build();

    final List<String> args1 = addtionalSettings1.getArgs();
    final List<String> args2 = addtionalSettings2.getArgs();
    final List<String> args3 = addtionalSettings3.getArgs();

    assertEquals(2, args1.size());
    assertEquals("--android-language", args1.get(0));

    assertEquals(0, args2.size());
    assertEquals(0, args3.size());
  }

  @Test
  public void testMultipleProperties() {
    final FlutterCreateAdditionalSettings addtionalSettings = new FlutterCreateAdditionalSettings.Builder()
      .setOrg("tld.domain")
      .setType(FlutterProjectType.PLUGIN)
      .setDescription("a b c")
      .setSwift(true)
      .setKotlin(true)
      .build();

    final List<String> args = addtionalSettings.getArgs();

    final String line = String.join(" ", args);

    assertEquals(10, args.size());
    assertEquals("--template plugin --description a b c --org tld.domain --ios-language swift --android-language kotlin", line);
  }
}
