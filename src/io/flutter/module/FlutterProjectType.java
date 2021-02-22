/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import io.flutter.FlutterBundle;

public enum FlutterProjectType {
  APP(FlutterBundle.message("flutter.module.create.settings.type.application"), "app", true),
  PLUGIN(FlutterBundle.message("flutter.module.create.settings.type.plugin"), "plugin", true),
  PACKAGE(FlutterBundle.message("flutter.module.create.settings.type.package"), "package", false),
  MODULE(FlutterBundle.message("flutter.module.create.settings.type.module"), "module", false),
  IMPORT(FlutterBundle.message("flutter.module.create.settings.type.import_module"), "module", false);

  final public String title;
  final public String arg;
  final public boolean requiresPlatform;

  FlutterProjectType(String title, String arg, boolean requiresPlatform) {
    this.title = title;
    this.arg = arg;
    this.requiresPlatform = requiresPlatform;
  }

  public String toString() {
    return title;
  }
}
