/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.FlutterUtils;
import io.flutter.run.common.CommonTestConfigUtils;
import io.flutter.run.common.TestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestConfigUtils extends CommonTestConfigUtils {
  private static TestConfigUtils instance;

  public static TestConfigUtils getInstance() {
    if (instance == null) {
      instance = new TestConfigUtils();
    }
    return instance;
  }

  private TestConfigUtils() {
  }

  @Nullable
  @Override
  public TestType asTestCall(@NotNull PsiElement element) {
    final DartFile file = FlutterUtils.getDartFile(element);
    if (FlutterUtils.isInTestDir(file) && FlutterUtils.isInFlutterProject(element)) {
      // Named tests.
      final TestType namedTestCall = findNamedTestCall(element);
      if (namedTestCall != null) return namedTestCall;

      // Main.
      if (isMainFunctionDeclarationWithTests(element)) return TestType.MAIN;
    }

    return null;
  }
}
