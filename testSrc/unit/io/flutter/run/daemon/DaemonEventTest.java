/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.flutter.testing.JsonTesting.curly;
import static org.junit.Assert.assertEquals;

/**
 * Verifies that we can read events sent using the Flutter daemon protocol.
 */
public class DaemonEventTest {
  private List<String> log;
  private DaemonEvent.Listener listener;

  @Before
  public void setUp() {
    log = new ArrayList<>();
    listener = new DaemonEvent.Listener() {

      // daemon domain

      @Override
      public void onDaemonLogMessage(DaemonEvent.DaemonLogMessage event) {
        logEvent(event, event.level, event.message, event.stackTrace);
      }

      @Override
      public void onDaemonShowMessage(DaemonEvent.DaemonShowMessage event) {
        logEvent(event, event.level, event.title, event.message);
      }

      // app domain

      @Override
      public void onAppStarting(DaemonEvent.AppStarting event) {
        logEvent(event, event.appId, event.deviceId, event.directory, event.launchMode);
      }

      @Override
      public void onAppDebugPort(DaemonEvent.AppDebugPort event) {
        logEvent(event, event.appId, event.wsUri, event.baseUri);
      }

      @Override
      public void onAppStarted(DaemonEvent.AppStarted event) {
        logEvent(event, event.appId);
      }

      @Override
      public void onAppLog(DaemonEvent.AppLog event) {
        logEvent(event, event.appId, event.log, event.error);
      }

      @Override
      public void onAppProgressStarting(DaemonEvent.AppProgress event) {
        logEvent(event, "starting", event.appId, event.id, event.getType(), event.message);
      }

      @Override
      public void onAppProgressFinished(DaemonEvent.AppProgress event) {
        logEvent(event, "finished", event.appId, event.id, event.getType(), event.message);
      }

      @Override
      public void onAppStopped(DaemonEvent.AppStopped event) {
        if (event.error != null) {
          logEvent(event, event.appId, event.error);
        }
        else {
          logEvent(event, event.appId);
        }
      }

      // device domain

      @Override
      public void onDeviceAdded(DaemonEvent.DeviceAdded event) {
        logEvent(event, event.id, event.name, event.platform);
      }

      @Override
      public void onDeviceRemoved(DaemonEvent.DeviceRemoved event) {
        logEvent(event, event.id, event.name, event.platform);
      }
    };
  }

  @Test
  public void shouldIgnoreUnknownEvent() {
    send("unknown.message", curly());
    checkLog();
  }

  // daemon domain

  @Test
  public void canReceiveLogMessage() {
    send("daemon.logMessage", curly("level:\"spam\"", "message:\"Make money fast\"", "stackTrace:\"Las Vegas\""));
    checkLog("DaemonLogMessage: spam, Make money fast, Las Vegas");
  }

  @Test
  public void canReceiveShowMessage() {
    send("daemon.showMessage", curly("level:\"info\"", "title:\"Spam\"", "message:\"Make money fast\""));
    checkLog("DaemonShowMessage: info, Spam, Make money fast");
  }

  // app domain

  @Test
  public void canReceiveAppStarting() {
    send("app.start", curly("appId:42", "deviceId:456", "directory:somedir", "launchMode:run"));
    checkLog("AppStarting: 42, 456, somedir, run");
  }

  @Test
  public void canReceiveDebugPort() {
    // The port parameter is deprecated; should ignore it.
    send("app.debugPort", curly("appId:42", "port:456", "wsUri:\"example.com\"", "baseUri:\"belongto:us\""));
    checkLog("AppDebugPort: 42, example.com, belongto:us");
  }

  @Test
  public void canReceiveAppStarted() {
    send("app.started", curly("appId:42"));
    checkLog("AppStarted: 42");
  }

  @Test
  public void canReceiveAppLog() {
    send("app.log", curly("appId:42", "log:\"Oh no!\"", "error:true"));
    checkLog("AppLog: 42, Oh no!, true");
  }

  @Test
  public void canReceiveProgressStarting() {
    send("app.progress", curly("appId:42", "id:opaque", "progressId:very.hot", "message:\"Please wait\""));
    checkLog("AppProgress: starting, 42, opaque, very.hot, Please wait");
  }

  @Test
  public void canReceiveProgressFinished() {
    send("app.progress", curly("appId:42", "id:opaque", "progressId:very.hot", "message:\"All done!\"", "finished:true"));
    checkLog("AppProgress: finished, 42, opaque, very.hot, All done!");
  }

  @Test
  public void canReceiveAppStopped() {
    send("app.stop", curly("appId:42"));
    checkLog("AppStopped: 42");
  }

  @Test
  public void canReceiveAppStoppedWithError() {
    send("app.stop", curly("appId:42", "error:\"foobar\""));
    checkLog("AppStopped: 42, foobar");
  }

  // device domain

  @Test
  public void canReceiveDeviceAdded() {
    send("device.added", curly("id:9000", "name:\"Banana Jr\"", "platform:\"feet\""));
    checkLog("DeviceAdded: 9000, Banana Jr, feet");
  }

  @Test
  public void canReceiveDeviceRemoved() {
    send("device.removed", curly("id:9000", "name:\"Banana Jr\"", "platform:\"feet\""));
    checkLog("DeviceRemoved: 9000, Banana Jr, feet");
  }

  private void send(String eventName, String params) {
    DaemonEvent.dispatch(
      GSON.fromJson(curly("event:\"" + eventName + "\"", "params:" + params),
                    JsonObject.class), listener);
  }

  private void logEvent(DaemonEvent event, Object... items) {
    log.add(event.getClass().getSimpleName() + ": " + Joiner.on(", ").join(items));
  }

  private void checkLog(String... expectedEntries) {
    assertEquals("log entries are different", Arrays.asList(expectedEntries), log);
    log.clear();
  }

  private static final Gson GSON = new Gson();
}
