/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import org.jetbrains.annotations.NotNull;

public class FlutterIdeaSdkService extends FlutterSdkService {
  public FlutterIdeaSdkService(Project project) {
    super(project);
  }

  @Override
  public void configureDartSdk(@NotNull Module module) {
    //TODO(pq): consider a service that sets this value or seeds the dialog with a proposed path
    DartConfigurable.openDartSettings(module.getProject());
  }

}
