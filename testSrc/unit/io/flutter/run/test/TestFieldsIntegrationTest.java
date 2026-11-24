/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import io.flutter.analytics.Analytics;

import java.util.ArrayList;
import java.util.List;

/** {@link TestFields#isIntegrationTest}: in or under the pub root's {@code integration_test} directory. */
public class TestFieldsIntegrationTest extends BasePlatformTestCase {
  private final TempDirTestFixtureImpl tmp = new TempDirTestFixtureImpl();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    tmp.setUp();
    write("app/pubspec.yaml", "name: app\n");
    write("app/integration_test/app_test.dart", "void main() {}\n");
    write("app/test/widget_test.dart", "void main() {}\n");
    write("app/test/integration_test/helper_test.dart", "void main() {}\n");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      tmp.tearDown();
    }
    finally {
      super.tearDown();
    }
  }

  public void testFileInIntegrationTestDir() {
    assertTrue(TestFields.forFile(path("app/integration_test/app_test.dart")).isIntegrationTest(getProject()));
  }

  public void testIntegrationTestDir() {
    assertTrue(TestFields.forDir(path("app/integration_test")).isIntegrationTest(getProject()));
  }

  public void testUnitTestFile() {
    assertFalse(TestFields.forFile(path("app/test/widget_test.dart")).isIntegrationTest(getProject()));
  }

  public void testIntegrationTestFolderUnderTestIsNotIntegrationTest() {
    // flutter_tools runs these as widget tests: only the pub root's integration_test directory counts.
    assertFalse(TestFields.forFile(path("app/test/integration_test/helper_test.dart")).isIntegrationTest(getProject()));
  }

  public void testPackageRootIsNotIntegrationTest() {
    // Running the whole package runs only test/.
    assertFalse(TestFields.forDir(path("app")).isIntegrationTest(getProject()));
  }

  public void testMissingFileIsNotIntegrationTest() {
    assertFalse(TestFields.forFile(path("app/integration_test/missing_test.dart")).isIntegrationTest(getProject()));
  }

  public void testTestSessionReportsIntegrationTest() {
    final List<AnalyticsData> events = new ArrayList<>();
    com.jetbrains.lang.dart.analytics.Analytics.withReportObserver(events::add, () -> Analytics.recordTestSession(
      DefaultRunExecutor.getRunExecutorInstance(), getProject(), TestFields.forDir(path("app/integration_test"))));

    final AnalyticsData event = assertOneElement(events);
    assertEquals(Boolean.TRUE, event.getData().get("isIntegrationTest"));
  }

  private void write(String relativePath, String text) throws Exception {
    WriteAction.computeAndWait(() -> tmp.createFile(relativePath, text));
  }

  private String path(String relativePath) {
    return tmp.getTempDirPath() + "/" + relativePath;
  }
}
