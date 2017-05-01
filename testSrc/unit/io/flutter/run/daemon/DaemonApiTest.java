/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static io.flutter.testing.JsonTesting.curly;
import static org.junit.Assert.*;

/**
 * Verifies that we can send commands and read replies using the Flutter daemon protocol.
 */
public class DaemonApiTest {
  private List<String> log;
  private DaemonApi api;

  @Before
  public void setUp() {
    log = new ArrayList<>();
    api = new DaemonApi(log::add);
  }

  // app domain

  @Test
  public void canRestartApp() throws Exception {
    final Future<DaemonApi.RestartResult> result = api.restartApp("foo", true, false);
    checkSent(result, "app.restart",
              curly("appId:\"foo\"", "fullRestart:true", "pause:false"));

    replyWithResult(result, curly("code:42", "message:\"sorry\""));
    assertFalse(result.get().ok());
    assertEquals(42, result.get().getCode());
    assertEquals("sorry", result.get().getMessage());
  }

  @Test
  public void canStopApp() throws Exception {
    final Future<Boolean> result = api.stopApp("foo");
    checkSent(result, "app.stop", curly("appId:\"foo\""));

    replyWithResult(result, "true");
    assertEquals(true, result.get());
  }

  @Test
  public void canCallServiceExtension() throws Exception {
    final Map<String, Object> params = ImmutableMap.of("reversed", true);
    final Future<JsonObject> result = api.callAppServiceExtension("foo", "rearrange", params);
    checkSent(result, "app.callServiceExtension",
              curly("appId:\"foo\"", "methodName:\"rearrange\"", "params:" + curly("reversed:true")));

    replyWithResult(result, curly("type:_extensionType", "method:\"rearrange\""));
    assertEquals("_extensionType", result.get().get("type").getAsString());
  }

  // device domain

  @Test
  public void canEnableDeviceEvents() throws Exception {
    final Future result = api.enableDeviceEvents();
    checkLog("{\"method\":\"device.enable\",\"id\":0}");
    assertFalse(result.isDone());

    api.dispatch("{id: \"0\"}", null);
    assertTrue(result.isDone());
    assertNull(result.get());
  }

  // helpers

  private void checkSent(Future result, String expectedMethod, String expectedParamsJson) {
    checkLog("{\"method\":\"" + expectedMethod + "\",\"params\":" + expectedParamsJson + ",\"id\":0}");
    assertFalse(result.isDone());
  }

  private void replyWithResult(Future result, String resultJson) {
    api.dispatch("{id: \"0\", result: " + resultJson + "}", null);
    assertTrue(result.isDone());
  }

  private void checkLog(String... expectedEntries) {
    assertEquals("log entries are different", Arrays.asList(expectedEntries), log);
    log.clear();
  }
}
