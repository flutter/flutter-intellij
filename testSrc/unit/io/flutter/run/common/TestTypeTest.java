/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import io.flutter.AbstractDartElementTest;
import io.flutter.dart.DartSyntax;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * Verifies run configuration persistence.
 */
public class TestTypeTest extends AbstractDartElementTest {

  private static final String fileContents = "void main() {\n" +
                                             "  group('group 0', () {\n" +
                                             "    test('test 0', () {\n" +
                                             "      print('test contents');\n" +
                                             "    });\n" +
                                             "    testWidgets('test widgets 0', (tester) {\n" +
                                             "      print('test widgets contents');\n" +
                                             "    });\n" +
                                             "    testFooBarWidgets('test foobar widgets 0', (testers) {\n" +
                                             "      print('test foobar widgets contents');\n" +
                                             "    });\n" +
                                             "  });\n" +
                                             "  test('test 1', () {});\n" +
                                             "  testingWidgets('does not test widgets');\n" +
                                             "}";

  @Test
  public void shouldMatchMain() throws Exception {
    run(() -> {
      final DartCallExpression mainCall = getMainCall();
      assertThat(TestType.MAIN.matchesFunction(mainCall), equalTo(true));
      assertThat(TestType.GROUP.matchesFunction(mainCall), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(mainCall), equalTo(false));
    });
  }


  @Test
  public void shouldMatchGroup() throws Exception {
    run(() -> {
      final DartCallExpression group = getGroupCall();
      assertThat(TestType.MAIN.matchesFunction(group), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(group), equalTo(true));
      assertThat(TestType.SINGLE.matchesFunction(group), equalTo(false));
    });
  }


  @Test
  public void shouldMatchTest0() throws Exception {
    run(() -> {
      final DartCallExpression test0 = getTestCall("test", "test 0");
      assertThat(TestType.MAIN.matchesFunction(test0), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(test0), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(test0), equalTo(true));
    });
  }

  @Test
  public void shouldMatchTestWidgets0() throws Exception {
    run(() -> {
      final DartCallExpression testWidgets0 = getTestCall("testWidgets", "test widgets 0");
      assertThat(TestType.MAIN.matchesFunction(testWidgets0), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(testWidgets0), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(testWidgets0), equalTo(true));
    });
  }

  @Test
  public void shouldMatchTestFooBarWidgets0() throws Exception {
    run(() -> {
      final DartCallExpression testFooBarWidgets0 = getTestCall("testFooBarWidgets", "test foobar widgets 0");
      assertThat(TestType.MAIN.matchesFunction(testFooBarWidgets0), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(testFooBarWidgets0), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(testFooBarWidgets0), equalTo(true));
    });
  }

  @Test
  public void shouldMatchTest1() throws Exception {
    run(() -> {
      final DartCallExpression test1 = getTestCall("test", "test 1");
      assertThat(TestType.MAIN.matchesFunction(test1), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(test1), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(test1), equalTo(true));
    });
  }

  @Test
  public void shouldNotMatchTestingWidgets() throws Exception {
    run(() -> {
      final DartCallExpression testingWidgets = getTestCall("testingWidgets", "does not test widgets");
      assertThat(TestType.MAIN.matchesFunction(testingWidgets), equalTo(false));
      assertThat(TestType.GROUP.matchesFunction(testingWidgets), equalTo(false));
      assertThat(TestType.SINGLE.matchesFunction(testingWidgets), equalTo(false));
    });
  }

  @NotNull
  private DartCallExpression getMainCall() {
    // Set up fake source code.
    final PsiElement mainIdentifier = setUpDartElement(
      fileContents, "main", LeafPsiElement.class);
    assertThat(mainIdentifier, not(equalTo(null)));

    final DartCallExpression main = DartSyntax.findEnclosingFunctionCall(mainIdentifier, "main");
    assertThat(main, not(equalTo(null)));

    return main;
  }


  @NotNull
  private DartCallExpression getGroupCall() {
    // Set up fake source code.
    final PsiElement groupIdentifier = setUpDartElement(
      fileContents, "group 0", LeafPsiElement.class);

    final DartCallExpression group = DartSyntax.findEnclosingFunctionCall(groupIdentifier, "group");
    assertThat(group, not(equalTo(null)));

    return group;
  }

  /**
   * Gets a specific test call.
   *
   * @param functionName The name of the function being called, eg test() or testWidgets()
   * @param testName     The name of the test desired, such as 'test 0' or 'test widgets 0'
   * @return
   */
  @NotNull
  private DartCallExpression getTestCall(String functionName, String testName) {
    // Set up fake source code.
    final PsiElement testIdentifier = setUpDartElement(
      fileContents, testName, LeafPsiElement.class);
    assertThat(testIdentifier, not(equalTo(null)));

    final DartCallExpression test = DartSyntax.findEnclosingFunctionCall(testIdentifier, functionName);
    assertThat(test, not(equalTo(null)));

    return test;
  }
}
