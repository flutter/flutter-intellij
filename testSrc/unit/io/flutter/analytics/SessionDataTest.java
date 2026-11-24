/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import io.flutter.run.FlutterDevice;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.LaunchState;

import java.util.ArrayList;
import java.util.List;

public class SessionDataTest extends BasePlatformTestCase {
  private static final String[] NO_ARGS = new String[0];

  public void testRunSessionShape() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_APP, DefaultRunExecutor.getRunExecutorInstance(), getProject(),
      AnalyticsConstants.BUILD_MODE_RELEASE)));

    // Events without a project are dropped downstream, after the observer sees them.
    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.MECHANISM_FLUTTER_APP, event.getId());
    assertEquals(AnalyticsConstants.RUN_SESSION_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.EXECUTOR_RUN, event.getData().get("executor"));
    assertEquals(AnalyticsConstants.BUILD_MODE_RELEASE, event.getData().get("buildMode"));
  }

  public void testDebugSessionShape() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_ATTACH, DefaultDebugExecutor.getDebugExecutorInstance(), getProject(),
      AnalyticsConstants.BUILD_MODE_DEBUG)));

    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.MECHANISM_FLUTTER_ATTACH, event.getId());
    assertEquals(AnalyticsConstants.DEBUG_SESSION_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.EXECUTOR_DEBUG, event.getData().get("executor"));
    assertEquals(AnalyticsConstants.BUILD_MODE_DEBUG, event.getData().get("buildMode"));
  }

  public void testTestSessionOmitsBuildMode() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_TESTS, DefaultRunExecutor.getRunExecutorInstance(), getProject())));

    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.MECHANISM_FLUTTER_TESTS, event.getId());
    assertEquals(AnalyticsConstants.RUN_SESSION_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.EXECUTOR_RUN, event.getData().get("executor"));
    assertFalse(event.getData().containsKey("buildMode"));
  }

  public void testExecutorFor() {
    assertEquals(AnalyticsConstants.EXECUTOR_RUN, Analytics.executorFor(DefaultRunExecutor.EXECUTOR_ID));
    assertEquals(AnalyticsConstants.EXECUTOR_DEBUG, Analytics.executorFor(DefaultDebugExecutor.EXECUTOR_ID));
    assertEquals(AnalyticsConstants.EXECUTOR_COVERAGE, Analytics.executorFor("Coverage"));
    assertEquals(AnalyticsConstants.EXECUTOR_PROFILER, Analytics.executorFor(LaunchState.ANDROID_PROFILER_EXECUTOR_ID));
    // Never the raw id.
    assertEquals(AnalyticsConstants.EXECUTOR_OTHER, Analytics.executorFor("SomeThirdPartyExecutor"));
    assertEquals(AnalyticsConstants.EXECUTOR_OTHER, Analytics.executorFor(null));
  }

  public void testBuildModeFromEnvironment() {
    assertEquals(AnalyticsConstants.BUILD_MODE_DEBUG,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultRunExecutor.EXECUTOR_ID, NO_ARGS));
    assertEquals(AnalyticsConstants.BUILD_MODE_PROFILE,
                 Analytics.buildModeFor(FlutterLaunchMode.PROFILE, DefaultRunExecutor.EXECUTOR_ID, NO_ARGS));
    assertEquals(AnalyticsConstants.BUILD_MODE_RELEASE,
                 Analytics.buildModeFor(FlutterLaunchMode.RELEASE, DefaultRunExecutor.EXECUTOR_ID, NO_ARGS));
  }

  public void testBuildModeForProfilerExecutor() {
    // The profiler executor only switches the environment to profile later, in LaunchState.launch.
    assertEquals(AnalyticsConstants.BUILD_MODE_PROFILE,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, LaunchState.ANDROID_PROFILER_EXECUTOR_ID, NO_ARGS));
  }

  public void testBuildModeFromExtraArgs() {
    assertEquals(AnalyticsConstants.BUILD_MODE_RELEASE,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultRunExecutor.EXECUTOR_ID,
                                         new String[]{"--dart-define=X=1", "--release"}));
    assertEquals(AnalyticsConstants.BUILD_MODE_PROFILE,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultDebugExecutor.EXECUTOR_ID, new String[]{"--profile"}));
    // Only exact flags count.
    assertEquals(AnalyticsConstants.BUILD_MODE_DEBUG,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultRunExecutor.EXECUTOR_ID,
                                         new String[]{"--dart-define=MODE=--release"}));
  }

  public void testAppSessionWithDevice() {
    final FlutterDevice device = new FlutterDevice("emulator-5554", "sdk gphone64", "android-arm64", true, "mobile", "android", true);
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_APP, DefaultDebugExecutor.getDebugExecutorInstance(), getProject(),
      AnalyticsConstants.BUILD_MODE_DEBUG, device, true)));

    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.PLATFORM_TYPE_ANDROID, event.getData().get("platformType"));
    assertEquals(AnalyticsConstants.DEVICE_CATEGORY_MOBILE, event.getData().get("deviceCategory"));
    assertEquals(Boolean.TRUE, event.getData().get("isEmulator"));
    assertEquals(Boolean.TRUE, event.getData().get("hasFlavor"));
    // Not flutter_tools' targetPlatform, and never the device id or name.
    assertFalse(event.getData().containsKey("targetPlatform"));
    assertFalse(event.getData().containsValue("emulator-5554"));
    assertFalse(event.getData().containsValue("sdk gphone64"));
  }

  public void testAttachSessionOmitsHasFlavor() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_ATTACH, DefaultDebugExecutor.getDebugExecutorInstance(), getProject(),
      AnalyticsConstants.BUILD_MODE_DEBUG, device("ios", "ios", "mobile"))));

    assertEquals(AnalyticsConstants.PLATFORM_TYPE_IOS, event.getData().get("platformType"));
    assertEquals(Boolean.FALSE, event.getData().get("isEmulator"));
    assertFalse(event.getData().containsKey("hasFlavor"));
  }

  public void testIsEmulatorOnlyForAndroidAndIos() {
    for (FlutterDevice device : new FlutterDevice[]{
      device("web-javascript", "web", "web"), device("linux-x64", "linux", "desktop"), device("pi", "custom", "mobile")}) {
      final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
        AnalyticsConstants.MECHANISM_FLUTTER_APP, DefaultRunExecutor.getRunExecutorInstance(), getProject(),
        AnalyticsConstants.BUILD_MODE_DEBUG, device, false)));
      assertFalse(device.platformType(), event.getData().containsKey("isEmulator"));
    }
  }

  public void testTestSessionOmitsDeviceFields() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_TESTS, DefaultRunExecutor.getRunExecutorInstance(), getProject())));

    for (String key : new String[]{"platformType", "deviceCategory", "isEmulator", "hasFlavor"}) {
      assertFalse(key, event.getData().containsKey(key));
    }
  }

  public void testPlatformTypeFor() {
    assertEquals(AnalyticsConstants.PLATFORM_TYPE_IOS, Analytics.platformTypeFor(device("ios", "ios", "mobile")));
    assertEquals(AnalyticsConstants.PLATFORM_TYPE_WEB, Analytics.platformTypeFor(device("web-javascript", "web", "web")));
    assertEquals(AnalyticsConstants.PLATFORM_TYPE_MACOS, Analytics.platformTypeFor(device("darwin", "macos", "desktop")));
    assertEquals(AnalyticsConstants.PLATFORM_TYPE_CUSTOM, Analytics.platformTypeFor(device("pi", "custom", "mobile")));
    // Unknown or missing values become "other", never the raw value.
    assertEquals(AnalyticsConstants.PLATFORM_TYPE_OTHER, Analytics.platformTypeFor(device("x", "something-new", null)));
    assertEquals(AnalyticsConstants.PLATFORM_TYPE_OTHER, Analytics.platformTypeFor(device("android-arm64", null, null)));
  }

  public void testDeviceCategoryFor() {
    assertEquals(AnalyticsConstants.DEVICE_CATEGORY_DESKTOP, Analytics.deviceCategoryFor(device("linux-x64", "linux", "desktop")));
    assertEquals(AnalyticsConstants.DEVICE_CATEGORY_WEB, Analytics.deviceCategoryFor(device("web-javascript", "web", "web")));
    assertEquals(AnalyticsConstants.DEVICE_CATEGORY_OTHER, Analytics.deviceCategoryFor(device("android-arm64", "android", null)));
    assertEquals(AnalyticsConstants.DEVICE_CATEGORY_OTHER, Analytics.deviceCategoryFor(device("x", "x", "watch")));
  }

  public void testHasFlavor() {
    assertTrue(Analytics.hasFlavor("staging", NO_ARGS));
    assertTrue(Analytics.hasFlavor(null, new String[]{"--flavor=staging"}));
    assertTrue(Analytics.hasFlavor(null, new String[]{"--flavor", "staging"}));
    assertFalse(Analytics.hasFlavor(null, NO_ARGS));
    assertFalse(Analytics.hasFlavor(" ", new String[]{"--dart-define=flavor=x"}));
  }

  private static FlutterDevice device(String platform, String platformType, String category) {
    return new FlutterDevice("id", "name", platform, false, category, platformType, null);
  }

  private static List<AnalyticsData> record(Runnable action) {
    final List<AnalyticsData> events = new ArrayList<>();
    com.jetbrains.lang.dart.analytics.Analytics.withReportObserver(events::add, action);
    return events;
  }
}
