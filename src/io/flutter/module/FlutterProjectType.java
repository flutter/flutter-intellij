/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import io.flutter.FlutterBundle;

public enum FlutterProjectType {
  APP(FlutterBundle.message("flutter.module.create.settings.type.application"), "app"),
  PLUGIN(FlutterBundle.message("flutter.module.create.settings.type.plugin"), "plugin"),
  PACKAGE(FlutterBundle.message("flutter.module.create.settings.type.package"), "package");

  final public String title;
  final public String arg;

  FlutterProjectType(String title, String arg) {
    this.title = title;
    this.arg = arg;
  }

  public String toString() {
    return title;
  }
}
