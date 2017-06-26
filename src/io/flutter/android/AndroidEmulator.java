/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

public class AndroidEmulator {
  final AndroidSdk androidSdk;
  final String id;
  final String name;

  AndroidEmulator(AndroidSdk androidSdk, String id) {
    this.androidSdk = androidSdk;
    this.id = id;
    // TODO: How to best clean up the name?
    this.name = id.replaceAll("_", "-");
  }

  public String getName() {
    return name;
  }
}
