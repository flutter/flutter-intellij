/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import io.flutter.module.FlutterProjectType;
import java.util.List;
import org.junit.Test;

public class FlutterCreateAdditionalSettingsTest {
  @Test
  public void includeDriverPropertyTest() {
    final FlutterCreateAdditionalSettings additionalSettings1 =
      new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(true).build();
    final FlutterCreateAdditionalSettings additionalSettings2 =
      new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(false).build();
    final FlutterCreateAdditionalSettings additionalSettings3 =
      new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(null).build();

    final List<String> args1 = additionalSettings1.getArgs();
    final List<String> args2 = additionalSettings2.getArgs();
    final List<String> args3 = additionalSettings3.getArgs();

    assertEquals("--with-driver-test", args1.get(0));
    assertEquals(args2.size(), args3.size());
  }

  @Test
  public void generatePluginPropertyTest() {
    final FlutterCreateAdditionalSettings additionalSettings1 =
      new FlutterCreateAdditionalSettings.Builder().setType(FlutterProjectType.APP).build();
    final FlutterCreateAdditionalSettings additionalSettings2 =
      new FlutterCreateAdditionalSettings.Builder().setType(FlutterProjectType.PLUGIN).build();
    final FlutterCreateAdditionalSettings additionalSettings3 =
      new FlutterCreateAdditionalSettings.Builder().setType(FlutterProjectType.PACKAGE).build();
    final FlutterCreateAdditionalSettings additionalSettings4 =
      new FlutterCreateAdditionalSettings.Builder().setType(null).build();

    final List<String> args1 = additionalSettings1.getArgs();
    final List<String> args2 = additionalSettings2.getArgs();
    final List<String> args3 = additionalSettings3.getArgs();
    final List<String> args4 = additionalSettings4.getArgs();

    final int base = args4.size();
    assertEquals(base + 2, args1.size());
    assertEquals("app", args1.get(1));

    assertEquals(base + 2, args2.size());
    assertEquals("plugin", args2.get(1));

    assertEquals(base + 2, args3.size());
    assertEquals("package", args3.get(1));
  }

  @Test
  public void descriptionPropertyTest() {
    final String d = "My description.";
    final FlutterCreateAdditionalSettings additionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setDescription(d).build();
    final FlutterCreateAdditionalSettings additionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setDescription(" ").build();
    final FlutterCreateAdditionalSettings additionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setDescription(null).build();

    final List<String> args1 = additionalSettings1.getArgs();
    final List<String> args2 = additionalSettings2.getArgs();
    final List<String> args3 = additionalSettings3.getArgs();

    assertEquals(args2.size() + 2, args1.size());
    assertEquals("--description", args1.get(0));
    assertEquals(d, args1.get(1));

    assertEquals(args2.size(), args3.size());
  }

  @Test
  public void orgPropertyTest() {
    final String d = "tld.domain";
    final FlutterCreateAdditionalSettings additionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setOrg(d).build();
    final FlutterCreateAdditionalSettings additionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setOrg(" ").build();
    final FlutterCreateAdditionalSettings additionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setOrg(null).build();

    final List<String> args1 = additionalSettings1.getArgs();
    final List<String> args2 = additionalSettings2.getArgs();
    final List<String> args3 = additionalSettings3.getArgs();

    assertEquals(args2.size() + 2, args1.size());
    assertEquals("--org", args1.get(0));
    assertEquals(d, args1.get(1));

    assertEquals(args2.size(), args3.size());
  }

  @Test
  public void iosPropertyTest() {
    final FlutterCreateAdditionalSettings additionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setSwift(true).build();
    final FlutterCreateAdditionalSettings additionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setSwift(false).build();
    final FlutterCreateAdditionalSettings additionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setSwift(null).build();

    final List<String> args1 = additionalSettings1.getArgs();
    final List<String> args2 = additionalSettings2.getArgs();
    final List<String> args3 = additionalSettings3.getArgs();

    assertEquals(2, args1.size());
    assertNotEquals("--ios-language", args1.get(0));

    assertEquals(args2.size(), args3.size());
  }

  @Test
  public void kotlinPropertyTest() {
    final FlutterCreateAdditionalSettings additionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setKotlin(true).build();
    final FlutterCreateAdditionalSettings additionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setKotlin(false).build();
    final FlutterCreateAdditionalSettings additionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setKotlin(null).build();

    final List<String> args1 = additionalSettings1.getArgs();
    final List<String> args2 = additionalSettings2.getArgs();
    final List<String> args3 = additionalSettings3.getArgs();

    assertEquals(args2.size() - 2, args1.size());
    assertNotEquals("--android-language", args1.get(0));

    assertEquals(args2.size(), args3.size());
  }

  @Test
  public void testPlatforms() {
    // None of the non-platform tests set these properties so they all implicitly test the case where all are null.
    final FlutterCreateAdditionalSettings additionalSettings = new FlutterCreateAdditionalSettings.Builder()
      .setPlatformAndroid(true)
      .setPlatformIos(true)
      .setPlatformLinux(true)
      .setPlatformMacos(true)
      .setPlatformWeb(true)
      .setPlatformWindows(true)
      .build();
    final List<String> args = additionalSettings.getArgs();
    assertEquals(6, args.size());
    assertEquals("--platforms", args.get(4));
    assertEquals("android,ios,web,linux,macos,windows", args.get(5));
  }

  @Test
  public void testPartialPlatforms() {
    final FlutterCreateAdditionalSettings additionalSettings = new FlutterCreateAdditionalSettings.Builder()
      .setPlatformIos(true)
      .setPlatformMacos(true)
      .setPlatformWindows(true)
      .build();
    final List<String> args = additionalSettings.getArgs();
    assertEquals(6, args.size());
    assertEquals("--platforms", args.get(4));
    assertEquals("ios,macos,windows", args.get(5));
  }

  @Test
  public void testSinglePlatforms() {
    final FlutterCreateAdditionalSettings additionalSettings = new FlutterCreateAdditionalSettings.Builder()
      .setPlatformAndroid(true)
      .build();
    final List<String> args = additionalSettings.getArgs();
    assertEquals(6, args.size());
    assertEquals("--platforms", args.get(4));
    assertEquals("android", args.get(5));
  }

  @Test
  public void testNoPlatforms() {
    final FlutterCreateAdditionalSettings additionalSettings = new FlutterCreateAdditionalSettings.Builder()
      .setPlatformAndroid(false)
      .build();
    final List<String> args = additionalSettings.getArgs();
    assertEquals(4, args.size());
  }

  @Test
  public void testMultipleProperties() {
    final FlutterCreateAdditionalSettings additionalSettings = new FlutterCreateAdditionalSettings.Builder()
      .setOrg("tld.domain")
      .setType(FlutterProjectType.PLUGIN)
      .setDescription("a b c")
      .setSwift(true)
      .setKotlin(true)
      .build();

    final List<String> args = additionalSettings.getArgs();

    final String line = String.join(" ", args);

    assertEquals(6, args.size());
    assertEquals("--template plugin --description a b c --org tld.domain", line);
  }
}
