/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlutterCreateAddtionalSettingsTest {
  @Test
  public void includeDriverPropertyTest() {
    FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(true).build();
    FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(false).build();
    FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setIncludeDriverTest(null).build();

    List<String> args1 = addtionalSettings1.getArgs();
    List<String> args2 = addtionalSettings2.getArgs();
    List<String> args3 = addtionalSettings3.getArgs();

    assertTrue(args1.size() == 1);
    assertEquals("--with-driver-test", args1.get(0));

    assertTrue(args2.size() == 0);
    assertTrue(args3.size() == 0);
  }

  @Test
  public void generatePluginPropertyTest() {
    FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setGeneratePlugin(true).build();
    FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setGeneratePlugin(false).build();
    FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setGeneratePlugin(null).build();

    List<String> args1 = addtionalSettings1.getArgs();
    List<String> args2 = addtionalSettings2.getArgs();
    List<String> args3 = addtionalSettings3.getArgs();

    assertTrue(args1.size() == 1);
    assertEquals("--plugin", args1.get(0));

    assertTrue(args2.size() == 0);
    assertTrue(args3.size() == 0);
  }

  @Test
  public void descriptionPropertyTest() {
    String d = "My description.";
    FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setDescription(d).build();
    FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setDescription(" ").build();
    FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setDescription(null).build();

    List<String> args1 = addtionalSettings1.getArgs();
    List<String> args2 = addtionalSettings2.getArgs();
    List<String> args3 = addtionalSettings3.getArgs();

    assertTrue(args1.size() == 2);
    assertEquals("--description", args1.get(0));
    assertEquals(d, args1.get(1));

    assertTrue(args2.size() == 0);
    assertTrue(args3.size() == 0);
  }

  @Test
  public void orgPropertyTest() {
    String d = "tld.domain";
    FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setOrg(d).build();
    FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setOrg(" ").build();
    FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setOrg(null).build();

    List<String> args1 = addtionalSettings1.getArgs();
    List<String> args2 = addtionalSettings2.getArgs();
    List<String> args3 = addtionalSettings3.getArgs();

    assertTrue(args1.size() == 2);
    assertEquals("--org", args1.get(0));
    assertEquals(d, args1.get(1));

    assertTrue(args2.size() == 0);
    assertTrue(args3.size() == 0);
  }

  @Test
  public void iosPropertyTest() {
    FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setSwift(true).build();
    FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setSwift(false).build();
    FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setSwift(null).build();

    List<String> args1 = addtionalSettings1.getArgs();
    List<String> args2 = addtionalSettings2.getArgs();
    List<String> args3 = addtionalSettings3.getArgs();

    assertTrue(args1.size() == 2);
    assertEquals("--ios-language", args1.get(0));

    assertTrue(args2.size() == 0);
    assertTrue(args3.size() == 0);
  }

  @Test
  public void kotlinPropertyTest() {
    FlutterCreateAdditionalSettings addtionalSettings1 = new FlutterCreateAdditionalSettings.Builder().setKotlin(true).build();
    FlutterCreateAdditionalSettings addtionalSettings2 = new FlutterCreateAdditionalSettings.Builder().setKotlin(false).build();
    FlutterCreateAdditionalSettings addtionalSettings3 = new FlutterCreateAdditionalSettings.Builder().setKotlin(null).build();

    List<String> args1 = addtionalSettings1.getArgs();
    List<String> args2 = addtionalSettings2.getArgs();
    List<String> args3 = addtionalSettings3.getArgs();

    assertTrue(args1.size() == 2);
    assertEquals("--android-language", args1.get(0));

    assertTrue(args2.size() == 0);
    assertTrue(args3.size() == 0);
  }

  @Test
  public void testMultipleProperties() {
    FlutterCreateAdditionalSettings addtionalSettings = new FlutterCreateAdditionalSettings.Builder()
      .setOrg("tld.domain")
      .setGeneratePlugin(true)
      .setDescription("a b c")
      .setSwift(true)
      .setKotlin(true)
      .build();

    List<String> args = addtionalSettings.getArgs();

    String line = String.join(" ", args);

    assertTrue(args.size() == 9);
    assertEquals("--plugin --description a b c --org tld.domain --ios-language swift --android-language kotlin", line);
  }
}
