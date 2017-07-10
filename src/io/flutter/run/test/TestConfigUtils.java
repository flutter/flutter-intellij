/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;


import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartSyntax;
import io.flutter.run.FlutterRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class TestConfigUtils {

  enum TestType {
    GROUP(AllIcons.RunConfigurations.TestState.Run_run, "group"),
    MAIN(AllIcons.RunConfigurations.TestState.Run_run) {
      @NotNull
      String getTooltip(@NotNull PsiElement element) {
        return "Run Tests";
      }
    },
    SINGLE(AllIcons.RunConfigurations.TestState.Run, "test"
           //TODO(pq): add to enable support for widget tests (pending fixing location issues).
           //,  "testWidgets"
    );

    @NotNull
    private final Icon myIcon;
    private final List<String> myTestFunctionNames;

    TestType(@NotNull Icon icon, String... testFunctionNames) {
      myIcon = icon;
      myTestFunctionNames = Arrays.asList(testFunctionNames);
    }

    @NotNull
    Icon getIcon() {
      return myIcon;
    }

    boolean matchesFunction(@NotNull PsiElement element) {
      return myTestFunctionNames.stream().anyMatch(name -> DartSyntax.isCallToFunctionNamed(element, name));
    }

    @NotNull
    String getTooltip(@NotNull PsiElement element) {
      final String testName = findTestName(element);
      if (testName != null && !testName.isEmpty()) {
        return "Run '" + testName + "'";
      }

      return "Run Test";
    }

    @Nullable
    public DartCallExpression findCorrespondingCall(@NotNull PsiElement element) {
      for (String name : myTestFunctionNames) {
        final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(element, name);
        if (call != null) {
          return call;
        }
      }
      return null;
    }

    @Nullable
    public static DartCallExpression findTestCall(@NotNull PsiElement element) {
      for (TestType type : TestType.values()) {
        final DartCallExpression call = type.findCorrespondingCall(element);
        if (call != null) return call;
      }
      return null;
    }
  }

  @Nullable
  public static TestType asTestCall(@NotNull PsiElement element) {
    final DartFile file = FlutterRunConfigurationProducer.getDartFile(element);
    if (file != null && FlutterUtils.isInTestDir(file)) {
      // Named tests.
      for (TestType type : TestType.values()) {
        if (type.matchesFunction(element)) return type;
      }
      // Main.
      if (DartSyntax.isFunctionDeclarationNamed(element, "main")) return TestType.MAIN;
    }

    return null;
  }


  /**
   * Returns the name of the test containing this element, or null if it can't be calculated.
   */
  @Nullable
  public static String findTestName(@Nullable PsiElement elt) {
    if (elt == null) return null;

    final DartCallExpression call = TestType.findTestCall(elt);
    if (call == null) return null;

    final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
    if (lit == null) return null;

    return DartSyntax.unquote(lit);
  }
}
