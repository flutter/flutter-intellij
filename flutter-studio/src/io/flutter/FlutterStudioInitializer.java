/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

public class FlutterStudioInitializer implements Runnable {
  @Override
  public void run() {
    // TODO(messick): Remove this class if it is never needed.
    // Unlike StartupActivity, this runs before the welcome screen (FlatWelcomeFrame) is displayed.
    // StartupActivity runs just before a project is opened.
  }
}
