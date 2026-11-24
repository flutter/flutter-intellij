/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.CoverageRunner;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterRunConfigurationType;
import io.flutter.run.SdkRunConfig;
import io.flutter.run.test.FlutterTestConfigType;
import io.flutter.run.test.TestConfig;
import io.flutter.run.test.TestFields;

import java.io.File;

/**
 * Verifies {@link FlutterCoverageEnabledConfiguration}.
 */
public class FlutterCoverageEnabledConfigurationTest extends BasePlatformTestCase {

  private TestConfig createTestConfig() {
    return (TestConfig)FlutterTestConfigType.getInstance()
      .getConfigurationFactories()[0]
      .createTemplateConfiguration(getProject());
  }

  private VirtualFile createTempPubRoot() throws Exception {
    final File dir = FileUtil.createTempDirectory("flutter_coverage_test", null);
    Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(dir));

    assertTrue(new File(dir, "pubspec.yaml").createNewFile());
    assertTrue(new File(dir, "test").mkdirs());
    assertTrue(new File(dir, "test/widget_test.dart").createNewFile());

    final VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    vDir.refresh(false, true);
    return vDir;
  }

  public void testCreateCoverageFileReturnsEmptyWhenTestFileIsUnset() {
    final TestConfig testConfig = createTestConfig();
    testConfig.setFields(TestFields.forFile(""));

    final FlutterCoverageEnabledConfiguration coverageConfig = new FlutterCoverageEnabledConfiguration(testConfig);
    assertEquals("", coverageConfig.createCoverageFile());
    assertEquals("", coverageConfig.getCoverageFilePath());
  }

  public void testCreateCoverageFileReturnsEmptyWhenFileIsOutsidePubRoot() throws Exception {
    final File outsideDir = FileUtil.createTempDirectory("outside_pub_root", null);
    Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(outsideDir));
    final File outsideFile = new File(outsideDir, "orphan_test.dart");
    assertTrue(outsideFile.createNewFile());
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outsideFile);

    final TestConfig testConfig = createTestConfig();
    testConfig.setFields(TestFields.forFile(outsideFile.getPath()));

    final FlutterCoverageEnabledConfiguration coverageConfig = new FlutterCoverageEnabledConfiguration(testConfig);
    assertEquals("", coverageConfig.createCoverageFile());
  }

  public void testCreateCoverageFileForTestFileInPubRoot() throws Exception {
    final VirtualFile pubRootDir = createTempPubRoot();
    final VirtualFile testFile = pubRootDir.findFileByRelativePath("test/widget_test.dart");
    assertNotNull(testFile);

    final TestConfig testConfig = createTestConfig();
    testConfig.setFields(TestFields.forFile(testFile.getPath()));

    final FlutterCoverageEnabledConfiguration coverageConfig = new FlutterCoverageEnabledConfiguration(testConfig);
    assertEquals(pubRootDir.getPath() + "/coverage/lcov.info", coverageConfig.getCoverageFilePath());
  }

  public void testCreateCoverageFileForPubRootDirectoryItself() throws Exception {
    final VirtualFile pubRootDir = createTempPubRoot();
    PsiTestUtil.addContentRoot(getModule(), pubRootDir);
    Disposer.register(getTestRootDisposable(), () -> PsiTestUtil.removeContentEntry(getModule(), pubRootDir));

    // PubRoot.forFile returns null when given the pub root directory itself,
    // whereas TestFields.getPubRoot falls back to PubRoot.forDescendant.
    assertNull(PubRoot.forFile(pubRootDir));

    final TestConfig testConfig = createTestConfig();
    testConfig.setFields(TestFields.forDir(pubRootDir.getPath()));

    final FlutterCoverageEnabledConfiguration coverageConfig = new FlutterCoverageEnabledConfiguration(testConfig);
    assertEquals(pubRootDir.getPath() + "/coverage/lcov.info", coverageConfig.getCoverageFilePath());
  }

  public void testNotApplicableToNonTestConfig() {
    final SdkRunConfig sdkRunConfig = (SdkRunConfig)FlutterRunConfigurationType.getInstance()
      .getConfigurationFactories()[0]
      .createTemplateConfiguration(getProject());

    assertNull(CoverageEnabledConfiguration.getOrCreateIfApplicable(sdkRunConfig));
  }

  public void testSetCoverageRunnerAndExtensionRemovedPreserveCoverageFilePath() throws Exception {
    final VirtualFile pubRootDir = createTempPubRoot();
    final VirtualFile testFile = pubRootDir.findFileByRelativePath("test/widget_test.dart");
    assertNotNull(testFile);

    final TestConfig testConfig = createTestConfig();
    testConfig.setFields(TestFields.forFile(testFile.getPath()));

    final FlutterCoverageEnabledConfiguration coverageConfig = new FlutterCoverageEnabledConfiguration(testConfig);
    final String expectedPath = pubRootDir.getPath() + "/coverage/lcov.info";
    assertEquals(expectedPath, coverageConfig.getCoverageFilePath());

    final CoverageRunner runner = CoverageRunner.getInstance(FlutterCoverageRunner.class);
    coverageConfig.setCoverageRunner(runner);
    assertEquals(expectedPath, coverageConfig.getCoverageFilePath());

    coverageConfig.coverageRunnerExtensionRemoved(runner);
    assertEquals(expectedPath, coverageConfig.getCoverageFilePath());
  }
}
