/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import io.flutter.AbstractDartElementTest;
import io.flutter.dart.DartSyntax;
import io.flutter.run.test.TestConfigUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CommonTestConfigUtilsTest extends AbstractDartElementTest {
  @Test
  public void extractTestNameShouldNotEscapeNonAscii() throws Exception {
    run(() -> {
      final PsiElement testKeyword = setUpDartElement(
        "main() { test('テスト', () {}); }", "test", LeafPsiElement.class);
      final DartCallExpression call =
        DartSyntax.findEnclosingFunctionCall(testKeyword, "test");
      assertNotNull(call);

      final String name = TestConfigUtils.getInstance().extractTestName(call);
      assertEquals("テスト", name);
    });
  }
}
