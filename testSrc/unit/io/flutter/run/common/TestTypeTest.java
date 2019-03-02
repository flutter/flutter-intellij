/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import io.flutter.AbstractDartElementTest;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * Verifies that named test targets can be identified correctly as part of a group or as an individual test target.
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
  public void shouldMatchGroup() throws Exception {
    run(() -> {
      final PsiElement group0 = getGroupCall();
      assertThat(TestType.GROUP.findCorrespondingCall(group0), not(equalTo(null)));
      assertThat(TestType.SINGLE.findCorrespondingCall(group0), equalTo(null));
    });
  }


  @Test
  public void shouldMatchTest0() throws Exception {
    run(() -> {
      final PsiElement test0 = getTestCall("test", "test 0");
      assertThat(TestType.GROUP.findCorrespondingCall(test0), not(equalTo(null)));
      assertThat(TestType.SINGLE.findCorrespondingCall(test0), not(equalTo(null)));
    });
  }

  @Test
  public void shouldMatchTestWidgets0() throws Exception {
    run(() -> {
      final PsiElement testWidgets0 = getTestCall("testWidgets", "test widgets 0");
      assertThat(TestType.GROUP.findCorrespondingCall(testWidgets0), not(equalTo(null)));
      assertThat(TestType.SINGLE.findCorrespondingCall(testWidgets0), not(equalTo(null)));
    });
  }

  @Test
  public void shouldMatchTestFooBarWidgets0() throws Exception {
    run(() -> {
      final PsiElement testFooBarWidgets0 = getTestCall("testFooBarWidgets", "test foobar widgets 0");
      assertThat(TestType.GROUP.findCorrespondingCall(testFooBarWidgets0), not(equalTo(null)));
      assertThat(TestType.SINGLE.findCorrespondingCall(testFooBarWidgets0), not(equalTo(null)));
    });
  }

  @Test
  public void shouldMatchTest1() throws Exception {
    run(() -> {
      final PsiElement test1 = getTestCall("test", "test 1");
      assertThat(TestType.GROUP.findCorrespondingCall(test1), equalTo(null));
      assertThat(TestType.SINGLE.findCorrespondingCall(test1), not(equalTo(null)));
    });
  }

  @Test
  public void shouldNotMatchTestingWidgets() throws Exception {
    run(() -> {
      final PsiElement testingWidgets = getTestCall("testingWidgets", "does not test widgets");
      assertThat(TestType.GROUP.findCorrespondingCall(testingWidgets), equalTo(null));
      assertThat(TestType.SINGLE.findCorrespondingCall(testingWidgets), equalTo(null));
    });
  }

  @NotNull
  private PsiElement getGroupCall() {
    // Set up fake source code.
    final PsiElement groupIdentifier = setUpDartElement(
      fileContents, "group 0", LeafPsiElement.class);
    assertThat(groupIdentifier, not(equalTo(null)));

    return groupIdentifier;
  }

  /**
   * Gets a specific test call.
   *
   * @param functionName The name of the function being called, eg test() or testWidgets()
   * @param testName     The name of the test desired, such as 'test 0' or 'test widgets 0'
   * @return
   */
  @NotNull
  private PsiElement getTestCall(String functionName, String testName) {
    // Set up fake source code.
    final PsiElement testIdentifier = setUpDartElement(
      fileContents, testName, LeafPsiElement.class);
    assertThat(testIdentifier, not(equalTo(null)));

    return testIdentifier;
  }
}
