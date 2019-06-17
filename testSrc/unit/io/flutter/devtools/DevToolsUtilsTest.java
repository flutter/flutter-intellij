/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.FlutterUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static io.flutter.devtools.DevToolsUtils.generateDevToolsUrl;
import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PrepareForTest(FlutterUtils.class)
public class DevToolsUtilsTest {
  @Test
  public void validDevToolsUrl() {
    final String devtoolsHost = "127.0.0.1";
    final int devtoolsPort = 9100;
    final String serviceProtocolUri = "http://127.0.0.1:50224/WTFTYus3IPU=/";
    final String page = "timeline";

    assertEquals(
      generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page),
      "http://127.0.0.1:9100/?ide=IntelliJ&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F#timeline"
    );

    assertEquals(
      generateDevToolsUrl(devtoolsHost, devtoolsPort, null, null),
      "http://127.0.0.1:9100/?ide=IntelliJ"
    );

    PowerMockito.mockStatic(FlutterUtils.class);
    PowerMockito.when(FlutterUtils.isAndroidStudio()).thenReturn(true);

    assertEquals(
      generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page),
      "http://127.0.0.1:9100/?ide=AndroidStudio&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F#timeline"
    );
  }
}
