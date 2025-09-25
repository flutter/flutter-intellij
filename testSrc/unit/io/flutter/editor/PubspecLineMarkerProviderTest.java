/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class PubspecLineMarkerProviderTest {
  @Rule
  public final ProjectFixture<CodeInsightTestFixture> fixture = Testing.makeCodeInsightModule();

  private PubspecLineMarkerProvider provider = new PubspecLineMarkerProvider();

  protected void run(@NotNull Testing.RunnableThatThrows callback) throws Exception {
    Testing.runOnDispatchThread(callback);
  }

  @Test
  public void testBasicPackageDependency() throws Exception {
    run(() -> {
      String pubspecContent = """
        name: test_app
        dependencies:
          dio: ^5.9.0
          http: 1.2.0
        """;

      fixture.getInner().configureByText("pubspec.yaml", pubspecContent);
      PsiElement[] elements = fixture.getInner().getFile().getChildren();

      // Find the dio package key
      YAMLKeyValue dioKey = findYamlKeyValue(elements, "dio");
      assertNotNull("Should find dio key", dioKey);

      LineMarkerInfo<?> lineMarker = provider.getLineMarkerInfo(dioKey.getKey());
      assertNotNull("Should create line marker for dio package", lineMarker);
      assertEquals("Should use correct tooltip", "Open on pub.dev", lineMarker.getLineMarkerTooltip());
    });
  }

  @Test
  public void testGitDependency() throws Exception {
    run(() -> {
      String pubspecContent = """
        name: test_app
        dependencies:
          my_package:
            git: https://github.com/user/repo.git
        """;

      fixture.getInner().configureByText("pubspec.yaml", pubspecContent);
      PsiElement[] elements = fixture.getInner().getFile().getChildren();

      YAMLKeyValue packageKey = findYamlKeyValue(elements, "my_package");
      assertNotNull("Should find my_package key", packageKey);

      LineMarkerInfo<?> lineMarker = provider.getLineMarkerInfo(packageKey.getKey());
      assertNotNull("Should create line marker for git package", lineMarker);
      assertEquals("Should use git tooltip", "Open Git repository", lineMarker.getLineMarkerTooltip());
    });
  }

  @Test
  public void testGitDependencyWithUrl() throws Exception {
    run(() -> {
      String pubspecContent = """
        name: test_app
        dependencies:
          my_package:
            git:
              url: https://github.com/user/repo.git
        """;

      fixture.getInner().configureByText("pubspec.yaml", pubspecContent);
      PsiElement[] elements = fixture.getInner().getFile().getChildren();

      YAMLKeyValue packageKey = findYamlKeyValue(elements, "my_package");
      assertNotNull("Should find my_package key", packageKey);

      LineMarkerInfo<?> lineMarker = provider.getLineMarkerInfo(packageKey.getKey());
      assertNotNull("Should create line marker for git package with url", lineMarker);
      assertEquals("Should use git tooltip", "Open Git repository", lineMarker.getLineMarkerTooltip());
    });
  }

  @Test
  public void testDependencyOverrides() throws Exception {
    run(() -> {
      String pubspecContent = """
        name: test_app
        dependencies:
          dio: ^5.9.0
        dependency_overrides:
          dio: 5.8.0
        """;

      fixture.getInner().configureByText("pubspec.yaml", pubspecContent);
      PsiElement[] elements = fixture.getInner().getFile().getChildren();

      // Test both the original dependency and the override
      YAMLKeyValue dioKey = findYamlKeyValue(elements, "dio");
      assertNotNull("Should find dio key", dioKey);

      LineMarkerInfo<?> lineMarker = provider.getLineMarkerInfo(dioKey.getKey());
      assertNotNull("Should create line marker for overridden package", lineMarker);

      // Test the override section
      YAMLKeyValue overrideKey = findYamlKeyValueInSection(elements, "dependency_overrides", "dio");
      assertNotNull("Should find dio override key", overrideKey);

      LineMarkerInfo<?> overrideLineMarker = provider.getLineMarkerInfo(overrideKey.getKey());
      assertNotNull("Should create line marker for override", overrideLineMarker);
    });
  }

  @Test
  public void testFlutterSdkDependenciesIgnored() throws Exception {
    run(() -> {
      String pubspecContent = """
        name: test_app
        dependencies:
          flutter:
            sdk: flutter
          dio: ^5.9.0
        dev_dependencies:
          flutter_test:
            sdk: flutter
        """;

      fixture.getInner().configureByText("pubspec.yaml", pubspecContent);
      PsiElement[] elements = fixture.getInner().getFile().getChildren();

      // Flutter SDK dependencies should not have line markers
      YAMLKeyValue flutterKey = findYamlKeyValue(elements, "flutter");
      assertNotNull("Should find flutter key", flutterKey);

      LineMarkerInfo<?> flutterLineMarker = provider.getLineMarkerInfo(flutterKey.getKey());
      assertNull("Should not create line marker for flutter SDK", flutterLineMarker);

      YAMLKeyValue flutterTestKey = findYamlKeyValue(elements, "flutter_test");
      assertNotNull("Should find flutter_test key", flutterTestKey);

      LineMarkerInfo<?> flutterTestLineMarker = provider.getLineMarkerInfo(flutterTestKey.getKey());
      assertNull("Should not create line marker for flutter_test SDK", flutterTestLineMarker);

      // Regular packages should have line markers
      YAMLKeyValue dioKey = findYamlKeyValue(elements, "dio");
      assertNotNull("Should find dio key", dioKey);

      LineMarkerInfo<?> dioLineMarker = provider.getLineMarkerInfo(dioKey.getKey());
      assertNotNull("Should create line marker for regular package", dioLineMarker);
    });
  }

  @Test
  public void testNonPubspecFileIgnored() throws Exception {
    run(() -> {
      String yamlContent = """
        name: test_app
        dependencies:
          dio: ^5.9.0
        """;

      fixture.getInner().configureByText("other.yaml", yamlContent);
      PsiElement[] elements = fixture.getInner().getFile().getChildren();

      YAMLKeyValue dioKey = findYamlKeyValue(elements, "dio");
      assertNotNull("Should find dio key", dioKey);

      LineMarkerInfo<?> lineMarker = provider.getLineMarkerInfo(dioKey.getKey());
      assertNull("Should not create line marker for non-pubspec file", lineMarker);
    });
  }

  /**
   * Helper method to find a YAMLKeyValue by key name.
   */
  private YAMLKeyValue findYamlKeyValue(PsiElement[] elements, String keyName) {
    for (PsiElement element : elements) {
      YAMLKeyValue found = findYamlKeyValueRecursive(element, keyName);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /**
   * Helper method to find a YAMLKeyValue within a specific section.
   */
  private YAMLKeyValue findYamlKeyValueInSection(PsiElement[] elements, String sectionName, String keyName) {
    YAMLKeyValue section = findYamlKeyValue(elements, sectionName);
    if (section != null && section.getValue() != null) {
      return findYamlKeyValueRecursive(section.getValue(), keyName);
    }
    return null;
  }

  /**
   * Recursively search for a YAMLKeyValue by key name.
   */
  private YAMLKeyValue findYamlKeyValueRecursive(PsiElement element, String keyName) {
    if (element instanceof YAMLKeyValue) {
      YAMLKeyValue keyValue = (YAMLKeyValue) element;
      if (keyName.equals(keyValue.getKeyText())) {
        return keyValue;
      }
    }

    for (PsiElement child : element.getChildren()) {
      YAMLKeyValue found = findYamlKeyValueRecursive(child, keyName);
      if (found != null) {
        return found;
      }
    }

    return null;
  }
}
