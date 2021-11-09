/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.testing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.jetbrains.lang.dart.util.DartTestUtils;
import io.flutter.sdk.FlutterSdk;
import org.junit.rules.ExternalResource;

/**
 * Provides a Flutter Module with the Flutter SDK configured.
 *
 * <p>Depends on a {@link ProjectFixture} already being installed.
 */
public class FlutterModuleFixture extends ExternalResource {
  private final ProjectFixture parent;
  private final Disposable testRoot = () -> {};
  private final boolean realSdk;

  public FlutterModuleFixture(ProjectFixture parent) {
    this(parent, true);
  }

  public FlutterModuleFixture(ProjectFixture parent, boolean realSdk) {
    this.parent = parent;
    this.realSdk = realSdk;
  }

  @Override
  protected void before() throws Exception {
    Testing.runOnDispatchThread(() -> {
      FlutterTestUtils.configureFlutterSdk(parent.getModule(), testRoot, realSdk);
      if (realSdk) {
        final FlutterSdk sdk = FlutterSdk.getFlutterSdk(parent.getProject());
        assert (sdk != null);
        final String path = sdk.getHomePath();
        final String dartSdkPath = path + "/bin/cache/dart-sdk";
        System.setProperty("dart.sdk", dartSdkPath);
      }
      DartTestUtils.configureDartSdk(parent.getModule(), testRoot, realSdk);
    });
  }

  @Override
  protected void after() {
    testRoot.dispose();
  }

  public Module getModule() {
    return parent.getModule();
  }
}
