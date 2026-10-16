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
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CommonTestConfigUtilsTest extends AbstractDartElementTest {
  private final CommonTestConfigUtils testConfigUtils = new CommonTestConfigUtils() {
  };

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

  @NotNull
  private DartCallExpression findCallWithArg(@NotNull String fileContents, @NotNull String stringLiteralText) {
    final PsiElement arg = setUpDartElement(fileContents, stringLiteralText, PsiElement.class);
    final DartCallExpression call = DartSyntax.findClosestEnclosingFunctionCall(arg);
    assertNotNull(call);
    return call;
  }

  @Test
  public void shouldIdentifyStandardAndCustomTests() throws Exception {
    final String customTestContents = Files.readString(Path.of("testData/sample_tests/test/custom_test.dart"));
    run(() -> {
      final DartCallExpression groupCall = findCallWithArg(customTestContents, "'group 0'");
      assertEquals(TestType.GROUP, testConfigUtils.asTestCall(groupCall));
      assertEquals("group 0", testConfigUtils.findTestName(groupCall));

      final DartCallExpression test0Call = findCallWithArg(customTestContents, "'test 0'");
      assertEquals(TestType.SINGLE, testConfigUtils.asTestCall(test0Call));
      assertEquals("test 0", testConfigUtils.findTestName(test0Call));

      final DartCallExpression testWidgetsCall = findCallWithArg(customTestContents, "'test widgets 0'");
      assertEquals(TestType.SINGLE, testConfigUtils.asTestCall(testWidgetsCall));
      assertEquals("test widgets 0", testConfigUtils.findTestName(testWidgetsCall));

      final DartCallExpression nonTestCall = findCallWithArg(customTestContents, "'not a test'");
      assertNull(testConfigUtils.asTestCall(nonTestCall));
      assertEquals("group 0", testConfigUtils.findTestName(nonTestCall));

      final DartCallExpression nonGroupCall = findCallWithArg(customTestContents, "'not a group'");
      assertNull(testConfigUtils.asTestCall(nonGroupCall));
      assertNull(testConfigUtils.findTestName(nonGroupCall));

      final DartCallExpression customGroupCall = findCallWithArg(customTestContents, "'custom group'");
      assertEquals(TestType.GROUP, testConfigUtils.asTestCall(customGroupCall));
      assertEquals("custom group", testConfigUtils.findTestName(customGroupCall));

      final DartCallExpression customTestCall = findCallWithArg(customTestContents, "'custom test'");
      assertEquals(TestType.SINGLE, testConfigUtils.asTestCall(customTestCall));
      assertEquals("custom test", testConfigUtils.findTestName(customTestCall));

      final DartCallExpression parameterTestCall = setUpDartElement(
        customTestContents, "test(Object())", DartCallExpression.class);
      assertNull(testConfigUtils.asTestCall(parameterTestCall));
      assertNull(testConfigUtils.findTestName(parameterTestCall));

      final DartCallExpression interpolatedGroupCall = findCallWithArg(
        "void main() { group('$TestFileArgs', () {}); }", "'$TestFileArgs'");
      assertEquals(TestType.GROUP, testConfigUtils.asTestCall(interpolatedGroupCall));
      assertNull(testConfigUtils.findTestName(interpolatedGroupCall));
    });
  }
}
