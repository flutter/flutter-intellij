/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.run.common.CommonTestConfigUtils;
import io.flutter.run.common.TestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BazelTestConfigUtils extends CommonTestConfigUtils {

  @VisibleForTesting
  BazelTestConfigUtils() {
  }

  private static BazelTestConfigUtils instance;

  public static BazelTestConfigUtils getInstance() {
    if (instance == null) {
      instance = new BazelTestConfigUtils();
    }
    return instance;
  }


  private boolean isBazelFlutterCode(@Nullable DartFile file) {
    return file != null && Workspace.load(file.getProject()) != null;
  }

  @Nullable
  @Override
  public TestType asTestCall(@NotNull PsiElement element) {
    if (!isBazelFlutterCode(FlutterUtils.getDartFile(element))) return null;

    return super.asTestCall(element);
  }
}
