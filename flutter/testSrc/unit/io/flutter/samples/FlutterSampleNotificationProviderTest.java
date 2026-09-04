/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.google.common.collect.Lists;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlutterSampleNotificationProviderTest {
  @Test
  public void testContainsDartdocFlutterSample() {
    assertTrue(FlutterSampleNotificationProvider.containsDartdocFlutterSample(
      Lists.newArrayList("/// {@tool dartpad ...}")));
    assertTrue(FlutterSampleNotificationProvider.containsDartdocFlutterSample(
      Lists.newArrayList("///    {@tool dartpad ...}")));
    assertTrue(FlutterSampleNotificationProvider.containsDartdocFlutterSample(
      Lists.newArrayList("/// {@tool dartpad --template=stateless_widget_material}")));
    assertTrue(FlutterSampleNotificationProvider.containsDartdocFlutterSample(
      Lists.newArrayList("/// {@tool --template=stateless_widget_material dartpad}")));

    assertFalse(FlutterSampleNotificationProvider.containsDartdocFlutterSample(
      Lists.newArrayList("/// {tool dartpad ...}")));
    assertFalse(FlutterSampleNotificationProvider.containsDartdocFlutterSample(
      Lists.newArrayList("/// {@tool dartpad ...")));
  }
}
