/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import io.flutter.AbstractDartElementTest;
import io.flutter.editor.ActiveEditorsOutlineService;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * Verifies that named test targets can be identified correctly as part of a group or as an individual test target.
 */
public class CommonTestConfigUtilsTest extends AbstractDartElementTest {

  /**
   * The outline of data/test_file.dart, read from data/flutter_outline.dart.
   */
  private FlutterOutline fileOutline;

  /**
   * The contents of data/test_file.dart.
   */
  private String fileContents;

  CommonTestConfigUtils utils = new CommonTestConfigUtils() {
    @Override
    public TestType asTestCall(@NotNull PsiElement element) {
      return null;
    }

    @Override
    protected ActiveEditorsOutlineService getActiveEditorsOutlineService(@NotNull Project project) {
      return new ActiveEditorsOutlineService(project) {
        @Override
        public @Nullable FlutterOutline get(String path) {
          return fileOutline;
        }
      };
    }
  };

  @Before
  public void setUp() throws Exception {
    fileContents = new String(Files.readAllBytes(Paths.get("data/test_file.dart")));

    final String outlineContents = new String(Files.readAllBytes(Paths.get("data/flutter_outline.dart")));
    fileOutline = FlutterOutline.fromJson(new JsonParser().parse(outlineContents).getAsJsonObject());

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
