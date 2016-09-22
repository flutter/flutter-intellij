/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.ide.ApplicationLoadListener;
import io.flutter.run.daemon.FlutterDaemonService;

public class FlutterInitializer implements ApplicationLoadListener {

  public void beforeComponentsCreated() {
    FlutterDaemonService.getInstance();
  }
}
