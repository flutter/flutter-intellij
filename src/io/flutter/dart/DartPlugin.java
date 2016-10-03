/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.util.Version;

import java.util.Arrays;
import java.util.Objects;

/**
 * Provides access to the Dart Plugin.
 */
public class DartPlugin {

  /**
   * Tracks the minimum required Dart Plugin version.
   */
  private static final String MINIMUM_REQUIRED_PLUGIN_VERSION = "162.2233";
  private static final Version MINIMUM_VERSION = Version.parseVersion(MINIMUM_REQUIRED_PLUGIN_VERSION);

  private static final DartPlugin INSTANCE = new DartPlugin();

  private Version myVersion;

  public static DartPlugin getInstance() {
    return INSTANCE;
  }

  private IdeaPluginDescriptor getIdeaPluginDescriptor() {
    final IdeaPluginDescriptor[] plugins = PluginManager.getPlugins();
    return Arrays.asList(plugins).stream().filter(p -> Objects.equals(p.getName(), "Dart")).findFirst().get();
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
      IdeaPluginDescriptor descriptor = getIdeaPluginDescriptor();
      myVersion = Version.parseVersion(descriptor.getVersion());
    }
    return myVersion;
  }
}
