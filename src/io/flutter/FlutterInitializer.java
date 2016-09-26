/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.ide.ApplicationLoadListener;
import com.intellij.openapi.application.Application;
import io.flutter.run.daemon.FlutterDaemonService;
import org.jetbrains.annotations.NotNull;

public class FlutterInitializer implements ApplicationLoadListener {

  public void beforeComponentsCreated() {
    FlutterDaemonService.getInstance();
  }

  @Override // TODO Delete this method after moving to next stable release
  public void beforeApplicationLoaded(@NotNull Application application, @NotNull String s) {
    FlutterDaemonService.getInstance();
  }
}
