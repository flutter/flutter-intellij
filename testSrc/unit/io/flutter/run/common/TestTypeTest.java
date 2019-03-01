/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative;
import io.flutter.AbstractDartElementTest;
import io.flutter.dart.DartSyntax;
import io.flutter.run.bazelTest.BazelTestConfig;
import io.flutter.run.bazelTest.BazelTestConfigProducerTest;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * Verifies run configuration persistence.
 */
public class TestTypeTest extends AbstractDartElementTest {

  private static final String fileContents = "void main() {\n" +
                                             "  test('test 1', () {});\n" +
                                             "}";

  private static final String fileContents1 = "void main() {\n" +
                                              "  group('group 0', () {\n" +
                                              "    test('test 0', () {\n" +
                                              "      print('test contents');\n" +
                                              "    });\n" +
                                              "    testWidgets('test widgets 0', () {\n" +
                                              "      print('test widgets contents');\n" +
                                              "    });\n" +
                                              "    testFooBarWidgets('test foobar widgets 0', () {\n" +
                                              "      print('test foobar widgets contents');\n" +
                                              "    });\n" +
                                              "  });\n" +
                                              "  test('test 1', () {});\n" +
                                              "  testingWidgets('does not test widgets');\n" +
                                              "}";

  @Test
  public void shouldMatchMain() throws Exception {
    run(() -> {
      DartCallExpression mainCall = getMainCall();
      assertThat(TestType.MAIN.matchesFunction(mainCall), equalTo(true));
      assertThat(TestType.GROUP.matchesFunction(mainCall), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(mainCall), equalTo(false));
    });
  }


  @Test
  public void shouldMatchGroup() throws Exception {
    run(() -> {
      assertThat(TestType.MAIN.matchesFunction(getGroupCall()), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(getGroupCall()), equalTo(true));
      assertThat(TestType.SINGLE.matchesFunction(getGroupCall()), equalTo(false));
    });
  }


  @Test
  public void shouldMatchTest0() throws Exception {
    run(() -> {
      assertThat(TestType.MAIN.matchesFunction(getTestCall("test 0")), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(getTestCall("test 0")), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(getTestCall("test 0")), equalTo(true));
    });
  }

  @Test
  public void shouldMatchTestWidgets0() throws Exception {
    run(() -> {
      assertThat(TestType.MAIN.matchesFunction(getTestCall("test widgets 0")), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(getTestCall("test widgets 0")), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(getTestCall("test widgets 0")), equalTo(true));
    });
  }

  @Test
  public void shouldMatchTestFooBarWidgets0() throws Exception {
    run(() -> {
      assertThat(TestType.MAIN.matchesFunction(getTestCall("test foobar widgets 0")), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(getTestCall("test foobar widgets 0")), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(getTestCall("test foobar widgets 0")), equalTo(true));
    });
  }

  @Test
  public void shouldMatchTest1() throws Exception {
    run(() -> {
      assertThat(TestType.MAIN.matchesFunction(getTestCall("test 1")), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(getTestCall("test 1")), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(getTestCall("test 1")), equalTo(true));
    });
  }

  @Test
  public void shouldNotMatchTestingWidgets() throws Exception {
    run(() -> {
      assertThat(TestType.MAIN.matchesFunction(getTestCall("does not test widgets")), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(getTestCall("does not test widgets")), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(getTestCall("does not test widgets")), equalTo(true));
    });
  }

  private DartCallExpression getMainCall() {
    // Set up fake source code.
    final PsiElement mainIdentifier = setUpDartElement(
      fileContents, "main", LeafPsiElement.class);
    final PsiElement main = PsiTreeUtil.findFirstParent(
      mainIdentifier, element -> element instanceof DartFunctionDeclarationWithBodyOrNative);
    assertThat(main, not(equalTo(null)));

    return DartSyntax.findEnclosingFunctionCall(main, "main");
  }


  private DartCallExpression getGroupCall() {
    // Set up fake source code.
    final PsiElement groupIdentifier = setUpDartElement(
      fileContents, "group 0", LeafPsiElement.class);

    return DartSyntax.findEnclosingFunctionCall(groupIdentifier, "group");
  }

  private DartCallExpression getTestCall(String testName) {
    // Set up fake source code.
    final PsiElement testIdentifier = setUpDartElement(
      fileContents, testName, LeafPsiElement.class);
    assertThat(testIdentifier, not(equalTo(null)));

    return DartSyntax.findEnclosingFunctionCall(testIdentifier, "test");
  }
}
