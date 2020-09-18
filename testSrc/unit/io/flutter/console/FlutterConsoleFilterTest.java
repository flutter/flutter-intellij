/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.TestDir;
import io.flutter.testing.Testing;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class FlutterConsoleFilterTest {
  @ClassRule
  public static final ProjectFixture fixture = Testing.makeEmptyProject();

  @ClassRule
  public static final TestDir tmp = new TestDir();

  static VirtualFile contentRoot;
  static String appDir;

  @BeforeClass
  public static void setUp() throws Exception {
    contentRoot = tmp.ensureDir("root");
    appDir = tmp.ensureDir("root/test").getPath();
    tmp.writeFile("root/test/widget_test.dart", "");
    Testing.runOnDispatchThread(
      () -> ModuleRootModificationUtil.addContentRoot(fixture.getModule(), contentRoot.getPath()));
  }

  @Test
  public void checkTestFileUrlLink() {
    final String line = "#4      main.<anonymous closure> (file://" + appDir + "/widget_test.dart:23:18)\n";
    final Filter.Result link = new FlutterConsoleFilter(fixture.getModule()).applyFilter(line, 659);
    assertNotNull(link);
  }

  @Test
  public void checkLaunchingLink() {
    String line = "Launching test/widget_test.dart on Android SDK built for x86 in debug mode...\n";
    Filter.Result link = new FlutterConsoleFilter(fixture.getModule()).applyFilter(line, line.length());
    assertNotNull(link);
  }

  @Test
  public void checkErrorMessage() {
    final String line = "test/widget_test.dart:23:18: Error: Expected ';' after this.";
    final Filter.Result link = new FlutterConsoleFilter(fixture.getModule()).applyFilter(line, line.length());
    assertNotNull(link);
  }

  @Test(timeout=1000)
  public void checkBadErrorMessage() throws Exception {
    final Filter.Result link = new FlutterConsoleFilter(fixture.getModule()).applyFilter(backtracker, backtracker.length());
    assertNull(link);
  }

  private static final String backtracker =
    "export HEADER_SEARCH_PATHS=\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/include"+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/FMDB/FMDB.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/GPUImage/GPUImage.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/Mantle/Mantle.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/Reachability/Reachability.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/Regift/Regift.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/UICKeyChainStore/UICKeyChainStore.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/agora/agora.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/amap_location/amap_location.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/android_intent/android_intent.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/audioplayer2/audioplayer2.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/audioplayers/audioplayers.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/connectivity/connectivity.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/default_plugin/default_plugin.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/device_info/device_info.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/dfa/dfa.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_aliyun_account_certify/flutter_aliyun_account_certify.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_bugly/flutter_bugly.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_drag_scale/flutter_drag_scale.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_image_compress/flutter_image_compress.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_inapp_purchase/flutter_inapp_purchase.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_local_notifications/flutter_local_notifications.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_picker/flutter_picker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_qq/flutter_qq.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_sound/flutter_sound.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_statusbar_manager/flutter_statusbar_manager.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_video_compress/flutter_video_compress.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/fluwx/fluwx.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/image_editor/image_editor.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/image_picker/image_picker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/keyboard_visibility/keyboard_visibility.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/local_auth/local_auth.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/location/location.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/onekey/onekey.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/package_info/package_info.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/path_provider/path_provider.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/permission_handler/permission_handler.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/photo_manager/photo_manager.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/quick_actions/quick_actions.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/rongcloud/rongcloud.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/root_checker/root_checker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/screen/screen.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/sensors/sensors.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/share/share.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/shared_preferences/shared_preferences.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/sqflite/sqflite.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/tracker/tracker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/uni_links/uni_links.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/url_launcher/url_launcher.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/video_player/video_player.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/webview_flutter/webview_flutter.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/FMDB/FMDB.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/GPUImage/GPUImage.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/Mantle/Mantle.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/Reachability/Reachability.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/Regift/Regift.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/UICKeyChainStore/UICKeyChainStore.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/agora/agora.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/amap_location/amap_location.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/android_intent/android_intent.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/audioplayer2/audioplayer2.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/audioplayers/audioplayers.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/connectivity/connectivity.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/default_plugin/default_plugin.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/device_info/device_info.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/dfa/dfa.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_aliyun_account_certify/flutter_aliyun_account_certify.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_bugly/flutter_bugly.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_drag_scale/flutter_drag_scale.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_image_compress/flutter_image_compress.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_inapp_purchase/flutter_inapp_purchase.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_local_notifications/flutter_local_notifications.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_picker/flutter_picker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_qq/flutter_qq.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_sound/flutter_sound.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_statusbar_manager/flutter_statusbar_manager.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/flutter_video_compress/flutter_video_compress.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/fluwx/fluwx.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/image_editor/image_editor.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/image_picker/image_picker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/keyboard_visibility/keyboard_visibility.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/local_auth/local_auth.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/location/location.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/onekey/onekey.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/package_info/package_info.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/path_provider/path_provider.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/permission_handler/permission_handler.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/photo_manager/photo_manager.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/quick_actions/quick_actions.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/rongcloud/rongcloud.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/root_checker/root_checker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/screen/screen.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/sensors/sensors.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/share/share.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/shared_preferences/shared_preferences.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/sqflite/sqflite.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/tracker/tracker.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/uni_links/uni_links.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/url_launcher/url_launcher.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/video_player/video_player.framework/Headers\""+
    "\"/Users/user/workspace/eec/foobar/build/ios/Debug-iphonesimulator/webview_flutter/webview_flutter.framework/Headers\"\"";
}
