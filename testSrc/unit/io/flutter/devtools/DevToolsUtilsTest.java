/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import io.flutter.sdk.FlutterSdkUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static io.flutter.devtools.DevToolsUtils.generateDevToolsUrl;
import static org.junit.Assert.assertEquals;

@RunWith(PowerMockRunner.class)
@PrepareForTest(FlutterSdkUtil.class)
public class DevToolsUtilsTest {
  @Test
  public void validDevToolsUrl() {
    final String devtoolsHost = "127.0.0.1";
    final int devtoolsPort = 9100;
    final String serviceProtocolUri = "http://127.0.0.1:50224/WTFTYus3IPU=/";
    final String page = "timeline";
    final String pageName = "timeline";

    PowerMockito.mockStatic(FlutterSdkUtil.class);
    PowerMockito.when(FlutterSdkUtil.getFlutterHostEnvValue()).thenReturn("IntelliJ-IDEA");

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F#timeline",
      generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, null)
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA&embed=true&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F#timeline",
      generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, true, null)
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA&page=timeline&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F#timeline",
      generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, pageName)
    );

    assertEquals(
      "http://127.0.0.1:9100/?ide=IntelliJ-IDEA",
      generateDevToolsUrl(devtoolsHost, devtoolsPort, null, null, false, null)
    );

    PowerMockito.when(FlutterSdkUtil.getFlutterHostEnvValue()).thenReturn("Android-Studio");

    assertEquals(
      generateDevToolsUrl(devtoolsHost, devtoolsPort, serviceProtocolUri, page, false, null),
      "http://127.0.0.1:9100/?ide=Android-Studio&uri=http%3A%2F%2F127.0.0.1%3A50224%2FWTFTYus3IPU%3D%2F#timeline"
    );
  }
}
