/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class BazelTestConfigProducerTest extends AbstractDartElementTest {

  private static final String fileContents = "void main() {\n" +
                                             "  test('test 1', () {});\n" +
                                             "}";

  @Test
  public void producesFileConfigurationInsideABazelWorkspace() throws Exception {
    run(() -> {
      final PsiElement mainIdentifier = setUpDartElement("workspace/foo/bar.dart", fileContents, "main", LeafPsiElement.class);
      final PsiElement main =
        PsiTreeUtil.findFirstParent(mainIdentifier, element -> element instanceof DartFunctionDeclarationWithBodyOrNative);
      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(true, true);
      final BazelTestConfig config =
        new BazelTestConfig(fixture.getProject(), new TestConfigurationFactory(FlutterBazelTestConfigurationType.getInstance()),
                            "Test config");
      final ConfigurationContext context = new TestConfigurationContext(main);
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(true));
      assertThat(config.getFields().getTestName(), equalTo(null));
      assertThat(config.getFields().getEntryFile(), equalTo("/workspace/foo/bar.dart"));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }

  @Test
  public void producesTestNameConfigurationInsideABazelWorkspace() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("workspace/foo/bar.dart", fileContents, "test 1", LeafPsiElement.class);
      final PsiElement test =
        PsiTreeUtil.findFirstParent(testIdentifier, element -> element instanceof DartStringLiteralExpression);
      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(true, true);
      final BazelTestConfig config =
        new BazelTestConfig(fixture.getProject(), new TestConfigurationFactory(FlutterBazelTestConfigurationType.getInstance()),
                            "Test config");
      final ConfigurationContext context = new TestConfigurationContext(test);
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(true));
      assertThat(config.getFields().getTestName(), equalTo("test 1"));
      assertThat(config.getFields().getEntryFile(), equalTo("/workspace/foo/bar.dart"));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }


  @Test
  public void producesNoConfigurationOutsideABazelWorkspace() throws Exception {
    run(() -> {
      // Set up fake source code.
      final PsiElement testIdentifier = setUpDartElement("workspace/foo/bar.dart", fileContents, "test 1", LeafPsiElement.class);
      final PsiElement test =
        PsiTreeUtil.findFirstParent(testIdentifier, element -> element instanceof DartStringLiteralExpression);

      // Set up the configuration
      final ConfigurationContext context = new TestConfigurationContext(test);
      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(false, true);
      final BazelTestConfig config =
        new BazelTestConfig(fixture.getProject(), new TestConfigurationFactory(FlutterBazelTestConfigurationType.getInstance()),
                            "Test config");
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(false));
      assertThat(config.getFields().getTestName(), equalTo(null));
      assertThat(config.getFields().getEntryFile(), equalTo(null));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }

  @Test
  public void producesNoConfigurationWithAnInvalidTestFile() throws Exception {
    run(() -> {
      final PsiElement testIdentifier = setUpDartElement("workspace/foo/bar.dart", fileContents, "test 1", LeafPsiElement.class);
      final PsiElement test =
        PsiTreeUtil.findFirstParent(testIdentifier, element -> element instanceof DartStringLiteralExpression);

      final BazelTestConfigProducer testConfigProducer = new TestBazelConfigProducer(false, false);
      final BazelTestConfig config =
        new BazelTestConfig(fixture.getProject(), new TestConfigurationFactory(FlutterBazelTestConfigurationType.getInstance()),
                            "Test config");
      final ConfigurationContext context = new TestConfigurationContext(test);
      final boolean result = testConfigProducer.setupConfigurationFromContext(config, context, new Ref<>());
      assertThat(result, equalTo(false));
      assertThat(config.getFields().getTestName(), equalTo(null));
      assertThat(config.getFields().getEntryFile(), equalTo(null));
      assertThat(config.getFields().getBazelTarget(), equalTo(null));
    });
  }
}

class TestBazelConfigProducer extends BazelTestConfigProducer {
  final MockVirtualFileSystem fs;
  final Workspace fakeWorkspace;
  final boolean hasWorkspace;
  final boolean testFileValid;

  TestBazelConfigProducer(boolean hasWorkspace,
                          boolean testFileValid) {
    super(BazelTestConfigUtils.getInstance());
    fs = new MockVirtualFileSystem();
    fs.file("/workspace/WORKSPACE", "");
    fakeWorkspace = Workspace.forTest(
      fs.findFileByPath("/workspace/"),
      PluginConfig.forTest("", "", "", "")
    );
    this.hasWorkspace = hasWorkspace;
    this.testFileValid = testFileValid;
  }

  @Nullable
  @Override
  protected Workspace getWorkspace(@NotNull Project project) {
    return hasWorkspace ? fakeWorkspace : null;
  }

  @Nullable
  @Override
  VirtualFile verifyFlutterTestFile(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context, @NotNull DartFile file) {
    return testFileValid ? file.getVirtualFile() : null;
  }
}

class TestConfigurationContext extends ConfigurationContext {

  public TestConfigurationContext(PsiElement element) {
    super(element);
  }
}

class TestConfigurationFactory extends ConfigurationFactory {
  RunConfiguration runConfiguration;


  protected TestConfigurationFactory(@NotNull ConfigurationType type) {
    super(type);
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return runConfiguration;
  }
}