/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import io.flutter.AbstractDartElementTest;
import io.flutter.dart.DartSyntax;
import io.flutter.editor.ActiveEditorsOutlineService;
import io.flutter.testing.FakeActiveEditorsOutlineService;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * Verifies that named test targets can be identified correctly as part of a group or as an individual test target.
 */
public class CommonTestConfigUtilsTest extends AbstractDartElementTest {

  /**
   * The contents of data/test_file.dart.
   */
  private Map<String, String> fileContents = new HashMap<>();

  CommonTestConfigUtils utils;
  FakeActiveEditorsOutlineService service;

  static final String CUSTOM_TEST_LOCAL_PATH = "test/custom_test.dart";
  static final String SIMPLE_TEST_LOCAL_PATH = "test/simple_test.dart";

  @Before
  public void setUp() throws Exception {
    fileContents.put(
      CUSTOM_TEST_LOCAL_PATH,
      new String(Files.readAllBytes(Paths.get(FakeActiveEditorsOutlineService.CUSTOM_TEST_PATH)))
    );
    service = new FakeActiveEditorsOutlineService(fixture.getProject(), "/" + CUSTOM_TEST_LOCAL_PATH, FakeActiveEditorsOutlineService.CUSTOM_OUTLINE_PATH);
    utils = new CommonTestConfigUtils() {
      @Override
      protected ActiveEditorsOutlineService getActiveEditorsOutlineService(@NotNull Project project) {
        return service;
      }
    };
  }

  @Test @Ignore
  public void shouldMatchGroup() throws Exception {
    run(() -> {
      final PsiElement group0 = getTestCallWithName("group", "group 0");
      assertThat(utils.asTestCall(group0), equalTo(TestType.GROUP));
      assertThat(utils.findTestName(group0), equalTo("group 0"));
    });
  }


  @Test @Ignore
  public void shouldMatchTest0() throws Exception {
    run(() -> {
      final PsiElement test0 = getTestCallWithName("test", "test 0");
      assertThat(utils.asTestCall(test0), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(test0), equalTo("test 0"));
    });
  }

  @Test @Ignore
  public void shouldMatchTestWidgets0() throws Exception {
    run(() -> {
      final PsiElement testWidgets0 = getTestCallWithName("testWidgets", "test widgets 0");
      assertThat(utils.asTestCall(testWidgets0), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(testWidgets0), equalTo("test widgets 0"));
    });
  }

  @Test @Ignore
  public void shouldMatchTest1() throws Exception {
    run(() -> {
      final PsiElement test1 = getTestCallWithName("test", "test 1");
      assertThat(utils.asTestCall(test1), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(test1), equalTo("test 1"));
    });
  }

  @Test @Ignore
  public void shouldNotMatchNonTest() throws Exception {
    run(() -> {
      final PsiElement nonTest = getTestCallWithName("nonTest", "not a test");
      // The test call site of nonTest is not a runnable test, so asTestCall should return null.
      assertThat(utils.asTestCall(nonTest), equalTo(null));
      // Looking for the test that contains nonTest will find the test name of the enclosing group.
      // findTestName is supposed to get the name of the enclosing runnable test, so we allow it to look farther up the tree to find
      // the runnable test group.
      assertThat(utils.findTestName(nonTest), equalTo("group 0"));
    });
  }

  @Test @Ignore
  public void shouldNotMatchNonGroup() throws Exception {
    run(() -> {
      final PsiElement nonGroup = getTestCallWithName("nonGroup", "not a group");
      assertThat(utils.asTestCall(nonGroup), equalTo(null));
      assertThat(utils.findTestName(nonGroup), equalTo(null));
    });
  }

  @Test @Ignore
  public void shouldMatchCustomGroup() throws Exception {
    run(() -> {
      final PsiElement customGroup = getTestCallWithName("g", "custom group");
      assertThat(utils.asTestCall(customGroup), equalTo(TestType.GROUP));
      assertThat(utils.findTestName(customGroup), equalTo("custom group"));
    });
  }

  @Test @Ignore
  public void shouldMatchCustomTest() throws Exception {
    run(() -> {
      final PsiElement customTest = getTestCallWithName("t", "custom test");
      assertThat(utils.asTestCall(customTest), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(customTest), equalTo("custom test"));
    });
  }

  @Test @Ignore
  public void shouldMatchWhenMultipleFilesLoad() throws Exception {
    run(() -> {
      fileContents.put(
        SIMPLE_TEST_LOCAL_PATH,
        new String(Files.readAllBytes(Paths.get(FakeActiveEditorsOutlineService.SIMPLE_TEST_PATH)))
      );
      service.loadOutline("/" + SIMPLE_TEST_LOCAL_PATH, FakeActiveEditorsOutlineService.SIMPLE_OUTLINE_PATH);

      final PsiElement singleTest = getTestCallWithName("test", "test 1", SIMPLE_TEST_LOCAL_PATH);
      assertThat(utils.asTestCall(singleTest), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(singleTest), equalTo("test 1"));

      final PsiElement customTest = getTestCallWithName("t", "custom test", CUSTOM_TEST_LOCAL_PATH);
      assertThat(utils.asTestCall(customTest), equalTo(TestType.SINGLE));
      assertThat(utils.findTestName(customTest), equalTo("custom test"));
    });
  }

  @Test @Ignore
  public void shouldNotMatchWhenAOutlineIsOutOfDate() throws Exception {
    run(() -> {
      // We'll replace the correct outline with an incorrect outline, which will flag the outline as invalid.
      // Results should be null.
      service.loadOutline("/" + CUSTOM_TEST_LOCAL_PATH, FakeActiveEditorsOutlineService.SIMPLE_OUTLINE_PATH);

      final PsiElement customTest = getTestCallWithName("t", "custom test", CUSTOM_TEST_LOCAL_PATH);
      assertThat(utils.asTestCall(customTest), equalTo(null));
      assertThat(utils.findTestName(customTest), equalTo(null));
    });
  }

  /**
   * Gets a specific test or test group call.
   *
   * @param functionName The name of the function being called, eg test() or testWidgets()
   * @param testName     The name of the test desired, such as 'test 0' or 'test widgets 0'
   * @param filePath     The file containing the desired test.
   */
  @NotNull
  private DartCallExpression getTestCallWithName(String functionName, String testName, String filePath) {
    final PsiElement testIdentifier = setUpDartElement(filePath,
                                                       fileContents.get(filePath), testName, LeafPsiElement.class);
    assertThat(testIdentifier, not(equalTo(null)));

    final DartCallExpression result = DartSyntax.findClosestEnclosingFunctionCall(testIdentifier);
    assertThat(result, not(equalTo(null)));
    return result;
  }

  /**
   * Gets a specific test or test group call from {@code CUSTOM_TEST_LOCAL_PATH}.
   *
   * @param functionName The name of the function being called, eg test() or testWidgets()
   * @param testName     The name of the test desired, such as 'test 0' or 'test widgets 0'
   */
  @NotNull
  private DartCallExpression getTestCallWithName(String functionName, String testName) {
    return getTestCallWithName(functionName, testName, CUSTOM_TEST_LOCAL_PATH);
  }
}
