/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.ide;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Inspired by similar class in the Dart plugin.
 */
@SuppressWarnings("ConstantConditions")
abstract public class FlutterCodeInsightFixtureTestCase extends BasePlatformTestCase implements IdeaProjectTestFixture {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    WriteAction.runAndWait(() -> {
      try {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
        if (rootManager != null) {
          final VirtualFile[] allContentRoots = rootManager.getContentRoots();
          final VirtualFile contentRoot = allContentRoots[0];
          final VirtualFile pubspec = contentRoot.createChildData(this, "pubspec.yaml");
          pubspec.setBinaryContent(SamplePubspec.getBytes(StandardCharsets.UTF_8));
          final VirtualFile dartTool = contentRoot.createChildDirectory(this, ".dart_tool");
          final VirtualFile config = dartTool.createChildData(this, "package_config.json");
          config.setBinaryContent(SampleConfig.getBytes(StandardCharsets.UTF_8));
        }
      }
      catch (IOException e) {
        fail(Arrays.toString(e.getCause().getStackTrace()));
      }
    });
  }

  @Override
  public void tearDown() throws Exception {
    try {
      final VirtualFile root = Objects.requireNonNull(ModuleRootManager.getInstance(getModule())).getContentRoots()[0];
      assert root != null;
      final VirtualFile pubspec = root.findChild("pubspec.yaml");
      final VirtualFile dartTool = root.findChild(".dart_tool");
      if (pubspec != null && dartTool != null) {
        final VirtualFile config = dartTool.findChild("package_config.json");
        WriteAction.run(() -> {
          pubspec.delete(this);
          if (config != null) config.delete(this);
          dartTool.delete(this);
        });
        final List<String> toUnexclude = Arrays.asList(root.getUrl() + "/build", root.getUrl() + "/.pub", root.getUrl() + "/.dart_tool");
        ModuleRootModificationUtil.updateExcludedFolders(getModule(), root, toUnexclude, Collections.emptyList());
      }
    }
    catch (Throwable ex) {
      addSuppressedException(ex);
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Creates the syntax tree for a Dart file at a specific path and returns the innermost element with the given text.
   */
  @NotNull
  protected <E extends PsiElement> E setUpDartElement(String filePath, String fileText, String elementText, Class<E> expectedClass) {
    assert fileText != null && elementText != null && expectedClass != null && myFixture != null;
    return DartTestUtils.setUpDartElement(filePath, fileText, elementText, expectedClass, Objects.requireNonNull(myFixture.getProject()));
  }

  /**
   * Creates the syntax tree for a Dart file and returns the innermost element with the given text.
   */
  @NotNull
  protected <E extends PsiElement> E setUpDartElement(String fileText, String elementText, Class<E> expectedClass) {
    assert fileText != null && elementText != null && expectedClass != null && myFixture != null;
    return DartTestUtils.setUpDartElement(null, fileText, elementText, expectedClass, Objects.requireNonNull(myFixture.getProject()));
  }

  @Override
  @NotNull
  protected String getTestDataPath() {
    return DartTestUtils.BASE_TEST_DATA_PATH + getBasePath();
  }

  public void addStandardPackage(@NotNull final String packageName) {
    assert myFixture != null;
    myFixture.copyDirectoryToProject("../packages/" + packageName, "packages/" + packageName);
  }

  @NotNull
  @Override
  public Project getProject() {
    assert myFixture != null;
    return Objects.requireNonNull(myFixture.getProject());
  }

  @NotNull
  @Override
  public Module getModule() {
    assert myFixture != null;
    return Objects.requireNonNull(myFixture.getModule());
  }

  private static final String SamplePubspec =
    "name: hello_world\n" +
    "\n" +
    "environment:\n" +
    "  sdk: \">=2.12.0-0 <3.0.0\"\n" +
    "\n" +
    "dependencies:\n" +
    "  flutter:\n" +
    "    sdk: flutter\n";

  private static final String SampleConfig =
    "{\n" +
    "  \"configVersion\": 2,\n" +
    "  \"packages\": [\n" +
    "    {\n" +
    "      \"name\": \"cupertino_icons\",\n" +
    "      \"rootUri\": \"file:///Users/messick/.pub-cache/hosted/pub.dartlang.org/cupertino_icons-1.0.3\",\n" +
    "      \"packageUri\": \"lib/\",\n" +
    "      \"languageVersion\": \"2.12\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"name\": \"flutter\",\n" +
    "      \"rootUri\": \"file:///Users/messick/src/flutter/flutter/packages/flutter\",\n" +
    "      \"packageUri\": \"lib/\",\n" +
    "      \"languageVersion\": \"2.12\"\n" +
    "    },\n" +
    "  ],\n" +
    "  \"generated\": \"2021-05-12T16:34:11.007747Z\",\n" +
    "  \"generator\": \"pub\",\n" +
    "  \"generatorVersion\": \"2.14.0-48.0.dev\"\n" +
    "}";
}
