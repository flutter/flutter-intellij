/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import io.flutter.AbstractDartElementTest;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * Verifies that named test targets can be identified correctly as part of a group or as an individual test target.
 */
public class CommonTestConfigUtilsTest extends AbstractDartElementTest {

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
  CommonTestConfigUtils utils = new CommonTestConfigUtils() {
    @Override
    public TestType asTestCall(@NotNull PsiElement element) {
      return null;
    }
  };

  @Before
  public void setUp() throws Exception {
    Testing.runInWriteAction(() -> DartSdkLibUtil.enableDartSdk(fixture.getModule()));
  }

  @Test
  public void shouldMatchGroup() throws Exception {
    run(() -> {
      final PsiElement group0 = getGroupCall();
      assertThat(utils.asTestCall(group0), equalTo(TestType.GROUP));
      assertThat(utils.findTestName(group0), equalTo("group 0"));
    });
  }


  @Test
  public void shouldMatchTest0() throws Exception {
    run(() -> {
      final PsiElement test0 = getTestCall("test", "test 0");
      assertThat(utils.asTestCall(test0), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(test0), equalTo("group 0"));
    });
  }

  @Test
  public void shouldMatchTestWidgets0() throws Exception {
    run(() -> {
      final PsiElement testWidgets0 = getTestCall("testWidgets", "test widgets 0");
      assertThat(utils.asTestCall(testWidgets0), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(testWidgets0), equalTo("test widgets 0"));
    });
  }

  @Test
  public void shouldMatchTestFooBarWidgets0() throws Exception {
    run(() -> {
      final PsiElement testFooBarWidgets0 = getTestCall("testFooBarWidgets", "test foobar widgets 0");
      assertThat(utils.asTestCall(testFooBarWidgets0), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(testFooBarWidgets0), equalTo("test foobar widgets 0"));
    });
  }

  @Test
  public void shouldMatchTest1() throws Exception {
    run(() -> {
      final PsiElement test1 = getTestCall("test", "test 1");
      assertThat(utils.asTestCall(test1), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(test1), equalTo("test 1"));
    });
  }

  @Test
  public void shouldNotMatchTestingWidgets() throws Exception {
    run(() -> {
      final PsiElement testingWidgets = getTestCall("testingWidgets", "does not test widgets");
      assertThat(utils.asTestCall(testingWidgets), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(testingWidgets), equalTo("test 1"));
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
