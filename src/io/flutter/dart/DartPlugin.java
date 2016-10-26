/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Version;

/**
 * Provides access to the Dart Plugin.
 */
public class DartPlugin {

  /**
   * Tracks the minimum required Dart Plugin version.
   */
  private static final String MINIMUM_REQUIRED_PLUGIN_VERSION = "162.2485";
  private static final Version MINIMUM_VERSION = Version.parseVersion(MINIMUM_REQUIRED_PLUGIN_VERSION);

  private static final DartPlugin INSTANCE = new DartPlugin();

  private Version myVersion;

  public static DartPlugin getInstance() {
    return INSTANCE;
  }

  /**
   * @return the minimum required version of the Dart Plugin
   */
  public Version getMinimumVersion() {
    return MINIMUM_VERSION;
  }

  /**
   * @return the version of the currently installed Dart Plugin
   */
  public Version getVersion() {
    if (myVersion == null) {
      IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId("Dart"));
      assert (descriptor != null);
      myVersion = Version.parseVersion(descriptor.getVersion());
    }
    return myVersion;
  }
}
