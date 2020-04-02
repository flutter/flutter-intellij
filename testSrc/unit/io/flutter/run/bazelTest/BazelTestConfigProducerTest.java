/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.AbstractDartElementTest;
import io.flutter.bazel.PluginConfig;
import io.flutter.bazel.Workspace;
import io.flutter.editor.ActiveEditorsOutlineService;
import io.flutter.testing.FakeActiveEditorsOutlineService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

public class BazelTestConfigProducerTest extends AbstractDartElementTest {

  private static final String TEST_FILE_PATH = "workspace/foo/bar.dart";
  private String fileContents;

  private final BazelTestConfigUtils bazelTestConfigUtils = new BazelTestConfigUtils() {
    @Override
    protected ActiveEditorsOutlineService getActiveEditorsOutlineService(@NotNull Project project) {
      return new FakeActiveEditorsOutlineService(project, "/" + TEST_FILE_PATH, FakeActiveEditorsOutlineService.SIMPLE_OUTLINE_PATH);
    }
  };

  @Before
  public void setUp() throws IOException {
    fileContents = new String(Files.readAllBytes(Paths.get(FakeActiveEditorsOutlineService.SIMPLE_TEST_PATH)));
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void producesFileConfigurationInsideABazelWorkspace() throws Exception {
    run(() -> {
      // Set up the configuration producer.
      final ConfigurationContext context = getMainContext();
      final BazelTestConfig config = getEmptyBazelTestConfig();
      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(true, true, bazelTestConfigUtils);

      // Produce and check a run configuration.
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(true));
      assertThat(config.getFields().getTestName(), equalTo(null));
      assertThat(config.getFields().getEntryFile(), equalTo("/workspace/foo/bar.dart"));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void producesTestNameConfigurationInsideABazelWorkspace() throws Exception {
    run(() -> {
      // Set up the configuration producer.
      final ConfigurationContext context = getTest1Context();
      final BazelTestConfig config = getEmptyBazelTestConfig();
      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(true, true, bazelTestConfigUtils);

      // Produce and check a run configuration.
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(true));
      assertThat(config.getFields().getTestName(), equalTo("test 1"));
      assertThat(config.getFields().getEntryFile(), equalTo("/workspace/foo/bar.dart"));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }


  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void producesNoConfigurationOutsideABazelWorkspace() throws Exception {
    run(() -> {
      // Set up the configuration producer.
      final ConfigurationContext context = getTest1Context();
      final BazelTestConfig config = getEmptyBazelTestConfig();
      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(false, true, bazelTestConfigUtils);

      // Produce and check a run configuration.
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(false));
      assertThat(config.getFields().getTestName(), equalTo(null));
      assertThat(config.getFields().getEntryFile(), equalTo(null));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }

  @Test
  @Ignore("https://github.com/flutter/flutter-intellij/issues/3583")
  public void producesNoConfigurationWithAnInvalidTestFile() throws Exception {
    run(() -> {
      // Set up the configuration producer.
      final ConfigurationContext context = getTest1Context();
      final BazelTestConfig config = getEmptyBazelTestConfig();
      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(true, false, bazelTestConfigUtils);

      // Produce and check a run configuration.
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(false));
      assertThat(config.getFields().getTestName(), equalTo(null));
      assertThat(config.getFields().getEntryFile(), equalTo(null));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }

  private ConfigurationContext getMainContext() {
    // Set up fake source code.
    final PsiElement mainIdentifier = setUpDartElement(
      "workspace/foo/bar.dart", fileContents, "main", LeafPsiElement.class);
    final PsiElement main = PsiTreeUtil.findFirstParent(
      mainIdentifier, element -> element instanceof DartFunctionDeclarationWithBodyOrNative);
    assertThat(main, not(equalTo(null)));

    return new ConfigurationContext(main);
  }

  private ConfigurationContext getTest1Context() {
    // Set up fake source code.
    final PsiElement testIdentifier = setUpDartElement(
      TEST_FILE_PATH, fileContents, "test 1", LeafPsiElement.class);
    final PsiElement test =
      PsiTreeUtil.findFirstParent(testIdentifier, element -> element instanceof DartStringLiteralExpression);
    assertThat(test, not(equalTo(null)));
    return new ConfigurationContext(test);
  }

  private BazelTestConfig getEmptyBazelTestConfig() {
    return new BazelTestConfig(fixture.getProject(), new TestConfigurationFactory(), "Test config");
  }

  private static class TestBazelConfigProducer extends BazelTestConfigProducer {
    final MockVirtualFileSystem fs;
    final Workspace fakeWorkspace;
    final boolean hasWorkspace;
    final boolean hasValidTestFile;

    TestBazelConfigProducer(boolean hasWorkspace,
                            boolean hasValidTestFile,
                            BazelTestConfigUtils bazelTestConfigUtils) {
      super(bazelTestConfigUtils);
      fs = new MockVirtualFileSystem();
      fs.file("/workspace/WORKSPACE", "");
      fakeWorkspace = Workspace.forTest(
        fs.findFileByPath("/workspace/"),
        PluginConfig.forTest("", "", "", "", "", "", "")
      );
      this.hasWorkspace = hasWorkspace;
      this.hasValidTestFile = hasValidTestFile;
    }

    @Nullable
    @Override
    protected Workspace getWorkspace(@NotNull Project project) {
      return hasWorkspace ? fakeWorkspace : null;
    }

    @Nullable
    @Override
    VirtualFile verifyFlutterTestFile(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context, @NotNull DartFile file) {
      return hasValidTestFile ? file.getVirtualFile() : null;
    }
  }

  private static class TestConfigurationFactory extends ConfigurationFactory {
    RunConfiguration runConfiguration;


    protected TestConfigurationFactory() {
      super(FlutterBazelTestConfigurationType.getInstance());
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return runConfiguration;
    }
  }
}
