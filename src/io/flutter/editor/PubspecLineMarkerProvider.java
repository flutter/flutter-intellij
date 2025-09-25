/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ide.actions.RevealFileAction;
import icons.FlutterIcons;
import io.flutter.pub.PubRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLDocument;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLValue;

import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides line markers for package dependencies in pubspec.yaml files.
 * Shows a clickable icon next to package names that opens a menu with options to
 * view the package on pub.dev or view a specific version.
 */
public class PubspecLineMarkerProvider implements LineMarkerProvider {

  // Pattern to extract version from various formats like ^1.0.0, >=1.0.0, <2.0.0, 1.0.0, etc.
  private static final Pattern VERSION_PATTERN = Pattern.compile("([\\^>=<~]*)([0-9]+\\.[0-9]+\\.[0-9]+(?:-[a-zA-Z0-9.-]+)?)");

  // Pattern to extract Git URLs from various formats
  private static final Pattern GIT_URL_PATTERN = Pattern.compile("https?://[^\\s]+");

  /**
   * Represents information about a package dependency.
   */
  private static class PackageInfo {
    final String name;
    final String version;
    final String gitUrl;
    final String localPath;
    final boolean isGitDependency;
    final boolean isPathDependency;

    // Original dependency info (before overrides)
    final PackageInfo originalInfo;
    final boolean isOverridden;

    PackageInfo(String name, String version, String gitUrl, String localPath,
                boolean isGitDependency, boolean isPathDependency,
                PackageInfo originalInfo, boolean isOverridden) {
      this.name = name;
      this.version = version;
      this.gitUrl = gitUrl;
      this.localPath = localPath;
      this.isGitDependency = isGitDependency;
      this.isPathDependency = isPathDependency;
      this.originalInfo = originalInfo;
      this.isOverridden = isOverridden;
    }

    // Convenience constructors for non-overridden dependencies
    static PackageInfo createRegular(String name, String version) {
      return new PackageInfo(name, version, null, null, false, false, null, false);
    }

    static PackageInfo createGit(String name, String version, String gitUrl) {
      return new PackageInfo(name, version, gitUrl, null, true, false, null, false);
    }

    static PackageInfo createPath(String name, String version, String localPath) {
      return new PackageInfo(name, version, null, localPath, false, true, null, false);
    }

    // Create overridden version with original info preserved
    PackageInfo withOverride(PackageInfo overrideInfo) {
      return new PackageInfo(
        overrideInfo.name,
        overrideInfo.version,
        overrideInfo.gitUrl,
        overrideInfo.localPath,
        overrideInfo.isGitDependency,
        overrideInfo.isPathDependency,
        this, // Keep original as reference
        true  // Mark as overridden
      );
    }
  }
  @Nullable
  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    // Check if this is a pubspec.yaml file first
    if (!isPubspecFile(element)) {
      return null;
    }

    // Only process if this element is the key part of a YAMLKeyValue
    if (!(element.getParent() instanceof YAMLKeyValue)) {
      return null;
    }

    YAMLKeyValue keyValue = (YAMLKeyValue) element.getParent();

    // Make sure this element is actually the key (not the value)
    if (!element.equals(keyValue.getKey())) {
      return null;
    }

    // Check if this key is under dependencies or dev_dependencies
    if (!isPackageDependency(keyValue)) {
      return null;
    }

    String packageName = keyValue.getKeyText();
    if (packageName == null || packageName.trim().isEmpty()) {
      return null;
    }

    // Skip Flutter SDK dependencies
    if ("flutter".equals(packageName) || "flutter_test".equals(packageName)) {
      return null;
    }

    // Extract package information (version, git URL, etc.)
    PackageInfo packageInfo = extractPackageInfo(keyValue, packageName);
    if (packageInfo == null) {
      return null;
    }

    // Determine the dependency section type
    String dependencySection = getDependencySection(keyValue);

    PackageInfo finalPackageInfo;
    if ("dependency_overrides".equals(dependencySection)) {
      // This is an override dependency, find the original dependency
      PackageInfo originalInfo = findOriginalDependency(packageName, element);
      if (originalInfo != null) {
        finalPackageInfo = originalInfo.withOverride(packageInfo);
      } else {
        // No original dependency found, treat as standalone override
        finalPackageInfo = packageInfo;
      }
    } else {
      // This is a regular dependency, check for overrides and apply them
      finalPackageInfo = applyOverrides(packageInfo, element);
    }

    return createLineMarker(element, finalPackageInfo);
  }



  /**
   * Checks if the current file is a pubspec.yaml file.
   */
  private boolean isPubspecFile(@NotNull PsiElement element) {
    return PubRoot.isPubspec(element.getContainingFile().getVirtualFile());
  }

  /**
   * Checks if the YAML key is under dependencies, dev_dependencies, or dependency_overrides section.
   */
  private boolean isPackageDependency(@NotNull YAMLKeyValue keyValue) {
    PsiElement parent = keyValue.getParent();
    if (!(parent instanceof YAMLMapping)) {
      return false;
    }

    PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof YAMLKeyValue)) {
      return false;
    }

    YAMLKeyValue parentKeyValue = (YAMLKeyValue) grandParent;
    String parentKey = parentKeyValue.getKeyText();

    return "dependencies".equals(parentKey) ||
           "dev_dependencies".equals(parentKey) ||
           "dependency_overrides".equals(parentKey);
  }

  /**
   * Gets the dependency section name (dependencies, dev_dependencies, or dependency_overrides).
   */
  @Nullable
  private String getDependencySection(@NotNull YAMLKeyValue keyValue) {
    PsiElement parent = keyValue.getParent();
    if (!(parent instanceof YAMLMapping)) {
      return null;
    }

    PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof YAMLKeyValue)) {
      return null;
    }

    YAMLKeyValue parentKeyValue = (YAMLKeyValue) grandParent;
    return parentKeyValue.getKeyText();
  }

  /**
   * Finds the original dependency information from dependencies or dev_dependencies sections.
   */
  @Nullable
  private PackageInfo findOriginalDependency(@NotNull String packageName, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (!(file instanceof YAMLFile)) {
      return null;
    }

    YAMLFile yamlFile = (YAMLFile) file;
    YAMLDocument document = yamlFile.getDocuments().get(0);
    if (document == null) {
      return null;
    }

    YAMLValue topLevelValue = document.getTopLevelValue();
    if (!(topLevelValue instanceof YAMLMapping)) {
      return null;
    }

    YAMLMapping rootMapping = (YAMLMapping) topLevelValue;

    // Check dependencies section first
    PackageInfo dependencyInfo = findPackageInSection(rootMapping, "dependencies", packageName);
    if (dependencyInfo != null) {
      return dependencyInfo;
    }

    // Then check dev_dependencies section
    return findPackageInSection(rootMapping, "dev_dependencies", packageName);
  }

  /**
   * Finds package information in a specific dependency section.
   */
  @Nullable
  private PackageInfo findPackageInSection(@NotNull YAMLMapping rootMapping, @NotNull String sectionName, @NotNull String packageName) {
    YAMLKeyValue sectionKeyValue = rootMapping.getKeyValueByKey(sectionName);
    if (sectionKeyValue == null) {
      return null;
    }

    YAMLValue sectionValue = sectionKeyValue.getValue();
    if (!(sectionValue instanceof YAMLMapping)) {
      return null;
    }

    YAMLMapping sectionMapping = (YAMLMapping) sectionValue;
    YAMLKeyValue packageKeyValue = sectionMapping.getKeyValueByKey(packageName);
    if (packageKeyValue == null) {
      return null;
    }

    return extractPackageInfo(packageKeyValue, packageName);
  }

  /**
   * Extracts package information including version and Git URL.
   * Handles various formats:
   * - Simple version: "1.0.0"
   * - Caret version: "^1.0.0"
   * - Range version: ">=1.0.0 <2.0.0"
   * - Complex dependency with version key
   * - Git dependency: git: https://github.com/user/repo.git
   * - Git dependency with URL: git: url: https://github.com/user/repo.git
   * - "any" keyword
   */
  @Nullable
  private PackageInfo extractPackageInfo(@NotNull YAMLKeyValue keyValue, @NotNull String packageName) {
    YAMLValue value = keyValue.getValue();
    if (value == null) {
      return null;
    }

    String valueText = value.getText().trim();
    if (valueText == null || valueText.isEmpty()) {
      return null;
    }

    // Handle simple "any" case
    if ("any".equals(valueText)) {
      return PackageInfo.createRegular(packageName, null);
    }

    // Handle complex dependency (mapping)
    if (value instanceof YAMLMapping) {
      YAMLMapping mapping = (YAMLMapping) value;

      // Check for path dependency
      YAMLKeyValue pathKeyValue = mapping.getKeyValueByKey("path");
      if (pathKeyValue != null) {
        String localPath = extractPath(pathKeyValue);
        if (localPath != null) {
          // For path dependencies, also try to extract version if present
          YAMLKeyValue versionKeyValue = mapping.getKeyValueByKey("version");
          String version = null;
          if (versionKeyValue != null) {
            version = extractVersionFromValue(versionKeyValue.getValue());
          }
          return PackageInfo.createPath(packageName, version, localPath);
        }
      }

      // Check for Git dependency
      YAMLKeyValue gitKeyValue = mapping.getKeyValueByKey("git");
      if (gitKeyValue != null) {
        String gitUrl = extractGitUrl(gitKeyValue);
        if (gitUrl != null) {
          // For Git dependencies, also try to extract version if present
          YAMLKeyValue versionKeyValue = mapping.getKeyValueByKey("version");
          String version = null;
          if (versionKeyValue != null) {
            version = extractVersionFromValue(versionKeyValue.getValue());
          }
          return PackageInfo.createGit(packageName, version, gitUrl);
        }
      }

      // Check for regular version in complex dependency
      YAMLKeyValue versionKeyValue = mapping.getKeyValueByKey("version");
      if (versionKeyValue != null && versionKeyValue.getValue() != null) {
        String version = extractVersionFromValue(versionKeyValue.getValue());
        return PackageInfo.createRegular(packageName, version);
      }

      return null; // No version, git, or path specified in complex dependency
    }

    // Handle simple version string
    String version = extractVersionFromText(valueText);
    return PackageInfo.createRegular(packageName, version);
  }

  /**
   * Extracts Git URL from a git key value.
   * Handles formats:
   * - git: https://github.com/user/repo.git
   * - git:
   *     url: https://github.com/user/repo.git
   */
  @Nullable
  private String extractGitUrl(@NotNull YAMLKeyValue gitKeyValue) {
    YAMLValue gitValue = gitKeyValue.getValue();
    if (gitValue == null) {
      return null;
    }

    String gitText = gitValue.getText().trim();

    // Handle simple format: git: https://github.com/user/repo.git
    if (GIT_URL_PATTERN.matcher(gitText).find()) {
      return extractUrlFromText(gitText);
    }

    // Handle complex format with url key
    if (gitValue instanceof YAMLMapping) {
      YAMLMapping gitMapping = (YAMLMapping) gitValue;
      YAMLKeyValue urlKeyValue = gitMapping.getKeyValueByKey("url");
      if (urlKeyValue != null && urlKeyValue.getValue() != null) {
        String urlText = urlKeyValue.getValue().getText().trim();
        return extractUrlFromText(urlText);
      }
    }

    return null;
  }

  /**
   * Extracts URL from text, removing quotes if present.
   */
  @Nullable
  private String extractUrlFromText(@NotNull String text) {
    // Remove quotes if present
    if ((text.startsWith("\"") && text.endsWith("\"")) ||
        (text.startsWith("'") && text.endsWith("'"))) {
      text = text.substring(1, text.length() - 1);
    }

    java.util.regex.Matcher matcher = GIT_URL_PATTERN.matcher(text);
    if (matcher.find()) {
      return matcher.group();
    }

    return null;
  }

  /**
   * Extracts local path from a path dependency.
   */
  @Nullable
  private String extractPath(@NotNull YAMLKeyValue pathKeyValue) {
    YAMLValue pathValue = pathKeyValue.getValue();
    if (pathValue == null) {
      return null;
    }

    String text = pathValue.getText().trim();
    if (text == null || text.isEmpty()) {
      return null;
    }

    // Remove quotes if present
    if ((text.startsWith("\"") && text.endsWith("\"")) ||
        (text.startsWith("'") && text.endsWith("'"))) {
      text = text.substring(1, text.length() - 1);
    }

    return text;
  }

  /**
   * Extracts version from a YAML value.
   */
  @Nullable
  private String extractVersionFromValue(@NotNull YAMLValue value) {
    String valueText = value.getText().trim();
    return extractVersionFromText(valueText);
  }

  /**
   * Extracts version from text string.
   */
  @Nullable
  private String extractVersionFromText(@NotNull String valueText) {
    // Remove quotes if present
    if ((valueText.startsWith("\"") && valueText.endsWith("\"")) ||
        (valueText.startsWith("'") && valueText.endsWith("'"))) {
      valueText = valueText.substring(1, valueText.length() - 1);
    }

    // Handle range versions like ">=1.0.0 <2.0.0" - these should not show version-specific actions
    if (valueText.contains(" ") && (valueText.contains(">=") || valueText.contains("<"))) {
      return null; // Don't show version-specific actions for ranges
    }

    // Handle single constraint versions like "<2.0.0" or ">=1.0.0" - these should not show version-specific actions
    if (valueText.startsWith("<") || valueText.startsWith(">=")) {
      return null; // Don't show version-specific actions for constraints
    }

    // Extract version using regex - handle various prefixes for specific versions
    java.util.regex.Matcher matcher = VERSION_PATTERN.matcher(valueText);
    if (matcher.find()) {
      String prefix = matcher.group(1);
      String version = matcher.group(2);

      // Only show version-specific actions for exact versions or caret/tilde constraints
      if (prefix.isEmpty() || prefix.equals("^") || prefix.equals("~")) {
        return version; // Return the version number without prefix
      }
    }

    return null;
  }

  /**
   * Creates a line marker with the pub.dev icon and click handler.
   */
  @NotNull
  private LineMarkerInfo<PsiElement> createLineMarker(@NotNull PsiElement element,
                                                      @NotNull PackageInfo packageInfo) {
    return new LineMarkerInfo<>(
      element,
      element.getTextRange(),
      FlutterIcons.Dart_16, // Use the dart_16 icon
      null, // No tooltip function
      (e, elt) -> showPackageMenu(e, packageInfo, elt),
      GutterIconRenderer.Alignment.LEFT,
      () -> {
        String baseTooltip;
        if (packageInfo.isPathDependency) {
          baseTooltip = "Open local package folder";
        } else if (packageInfo.isGitDependency) {
          baseTooltip = "Open Git repository";
        } else {
          baseTooltip = "Open package on pub.dev";
        }

        if (packageInfo.isOverridden) {
          baseTooltip += " (overridden)";
        }

        return baseTooltip;
      }
    );
  }

  /**
   * Shows a popup menu with options to open the package on pub.dev, Git repository, or local folder.
   * For overridden dependencies, shows options for both original and overridden types.
   */
  private void showPackageMenu(@NotNull MouseEvent event,
                               @NotNull PackageInfo packageInfo,
                               @NotNull PsiElement element) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    // Add current (possibly overridden) dependency actions (excluding pub.dev)
    addSpecificDependencyActions(actionGroup, packageInfo, element, "");

    // If this is an overridden dependency, also add original dependency actions (excluding pub.dev)
    if (packageInfo.isOverridden && packageInfo.originalInfo != null) {
      actionGroup.addSeparator("Original Dependency");
      addSpecificDependencyActions(actionGroup, packageInfo.originalInfo, element, "Original: ");
    }

    // Add common actions that should appear only once
    actionGroup.addSeparator("Package Info");

    // Add pub.dev option (only once)
    actionGroup.add(new AnAction("Open on pub.dev",
                                 "Open " + packageInfo.name + " package page on pub.dev",
                                 FlutterIcons.Dart_16) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        String url = buildPackageUrl(packageInfo.name);
        BrowserUtil.browse(url);
      }
    });

    // Add version-specific actions if available
    addVersionActions(actionGroup, packageInfo);
    if (packageInfo.isOverridden && packageInfo.originalInfo != null) {
      addVersionActions(actionGroup, packageInfo.originalInfo);
    }

    // Add API Documentation option
    actionGroup.add(new AnAction("Check API Documentation",
                                 "Open " + packageInfo.name + " API documentation on pub.dev",
                                 FlutterIcons.Dart_16) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        String url = buildApiDocUrl(packageInfo.name);
        BrowserUtil.browse(url);
      }
    });

    // Create a simple data context with the PSI element
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PSI_ELEMENT, element)
      .build();

    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(
        "Package Actions",
        actionGroup,
        dataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        false
      );

    // Show popup at the mouse event location (near the icon)
    popup.show(new RelativePoint(event));
  }

  /**
   * Adds dependency-specific actions to the action group (excluding common actions like pub.dev).
   */
  private void addSpecificDependencyActions(@NotNull DefaultActionGroup actionGroup,
                                            @NotNull PackageInfo packageInfo,
                                            @NotNull PsiElement element,
                                            @NotNull String prefix) {
    if (packageInfo.isPathDependency && packageInfo.localPath != null) {
      // Path dependency actions
      actionGroup.add(new AnAction(prefix + "Open Local Folder",
                                   "Open " + packageInfo.name + " local folder",
                                   FlutterIcons.Dart_16) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          openLocalFolder(packageInfo.localPath, element);
        }
      });
    } else if (packageInfo.isGitDependency && packageInfo.gitUrl != null) {
      // Git dependency actions
      actionGroup.add(new AnAction(prefix + "Open Git Repository",
                                   "Open " + packageInfo.name + " Git repository",
                                   FlutterIcons.Dart_16) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          BrowserUtil.browse(packageInfo.gitUrl);
        }
      });
    }
  }

  /**
   * Adds version-specific actions to the action group.
   */
  private void addVersionActions(@NotNull DefaultActionGroup actionGroup,
                                 @NotNull PackageInfo packageInfo) {
    if (packageInfo.version != null && !packageInfo.version.trim().isEmpty()) {
      String prefix = packageInfo.isOverridden && packageInfo.originalInfo != null ? "Original: " : "";
      actionGroup.add(new AnAction(prefix + "Check " + packageInfo.version + " on pub.dev",
                                   "Check " + packageInfo.name + " " + packageInfo.version + " page on pub.dev",
                                   FlutterIcons.Dart_16) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String url = buildVersionUrl(packageInfo.name, packageInfo.version);
          BrowserUtil.browse(url);
        }
      });
    }
  }

  /**
   * Adds dependency-specific actions to the action group (legacy method for compatibility).
   */
  private void addDependencyActions(@NotNull DefaultActionGroup actionGroup,
                                    @NotNull PackageInfo packageInfo,
                                    @NotNull PsiElement element,
                                    @NotNull String prefix) {
    addSpecificDependencyActions(actionGroup, packageInfo, element, prefix);

    // Always add pub.dev option
    actionGroup.add(new AnAction(prefix + "Open on pub.dev",
                                 "Open " + packageInfo.name + " package page on pub.dev",
                                 FlutterIcons.Dart_16) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        String url = buildPackageUrl(packageInfo.name);
        BrowserUtil.browse(url);
      }
    });

    addVersionActions(actionGroup, packageInfo);
  }

  /**
   * Builds the URL for the package page on pub.dev.
   */
  @NotNull
  private String buildPackageUrl(@NotNull String packageName) {
    return "https://pub.dev/packages/" + packageName;
  }

  /**
   * Builds the URL for the specific version page on pub.dev.
   */
  @NotNull
  private String buildVersionUrl(@NotNull String packageName, @NotNull String version) {
    return "https://pub.dev/packages/" + packageName + "/versions/" + version;
  }

  /**
   * Builds the URL for the API documentation on pub.dev.
   */
  @NotNull
  private String buildApiDocUrl(@NotNull String packageName) {
    return "https://pub.dev/documentation/" + packageName + "/latest/";
  }

  /**
   * Opens a local folder in the system file manager.
   */
  private void openLocalFolder(@NotNull String localPath, @NotNull PsiElement element) {
    try {
      // Resolve the path relative to the project root
      VirtualFile projectRoot = element.getProject().getBaseDir();
      if (projectRoot == null) {
        return;
      }

      VirtualFile targetFolder;
      if (localPath.startsWith("/") || localPath.contains(":")) {
        // Absolute path
        targetFolder = LocalFileSystem.getInstance().findFileByPath(localPath);
      } else {
        // Relative path
        targetFolder = projectRoot.findFileByRelativePath(localPath);
      }

      if (targetFolder != null && targetFolder.exists()) {
        // Use RevealFileAction to show the folder in the system file manager
        RevealFileAction.openDirectory(targetFolder.toNioPath());
      } else {
        // Fallback: try to open the path as-is
        java.nio.file.Path path = java.nio.file.Paths.get(localPath);
        if (!path.isAbsolute()) {
          path = java.nio.file.Paths.get(projectRoot.getPath(), localPath);
        }
        if (java.nio.file.Files.exists(path)) {
          RevealFileAction.openDirectory(path);
        }
      }
    } catch (Exception e) {
      // If all else fails, silently ignore the error
      // Could add a notification here if needed
    }
  }

  /**
   * Applies dependency overrides to the package info.
   * Checks both dependency_overrides in the current file and pubspec_overrides.yaml.
   * Preserves original info for dual menu options.
   */
  @NotNull
  private PackageInfo applyOverrides(@NotNull PackageInfo originalInfo, @NotNull PsiElement element) {
    // First check for dependency_overrides in the current pubspec.yaml
    PackageInfo overrideInfo = findOverrideInCurrentFile(originalInfo.name, element);
    if (overrideInfo != null) {
      return originalInfo.withOverride(overrideInfo);
    }

    // Then check for pubspec_overrides.yaml
    PackageInfo pubspecOverrideInfo = findOverrideInPubspecOverrides(originalInfo.name, element);
    if (pubspecOverrideInfo != null) {
      return originalInfo.withOverride(pubspecOverrideInfo);
    }

    return originalInfo;
  }

  /**
   * Finds override information in the current pubspec.yaml file's dependency_overrides section.
   */
  @Nullable
  private PackageInfo findOverrideInCurrentFile(@NotNull String packageName, @NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (!(file instanceof YAMLFile)) {
      return null;
    }

    YAMLFile yamlFile = (YAMLFile) file;
    YAMLDocument document = yamlFile.getDocuments().get(0);
    if (document == null) {
      return null;
    }

    YAMLValue topLevelValue = document.getTopLevelValue();
    if (!(topLevelValue instanceof YAMLMapping)) {
      return null;
    }

    YAMLMapping topLevelMapping = (YAMLMapping) topLevelValue;
    YAMLKeyValue overridesKeyValue = topLevelMapping.getKeyValueByKey("dependency_overrides");
    if (overridesKeyValue == null || !(overridesKeyValue.getValue() instanceof YAMLMapping)) {
      return null;
    }

    YAMLMapping overridesMapping = (YAMLMapping) overridesKeyValue.getValue();
    YAMLKeyValue packageKeyValue = overridesMapping.getKeyValueByKey(packageName);
    if (packageKeyValue == null) {
      return null;
    }

    return extractPackageInfo(packageKeyValue, packageName);
  }

  /**
   * Finds override information in pubspec_overrides.yaml file.
   */
  @Nullable
  private PackageInfo findOverrideInPubspecOverrides(@NotNull String packageName, @NotNull PsiElement element) {
    // Find pubspec_overrides.yaml in the same directory as pubspec.yaml
    PsiFile currentFile = element.getContainingFile();
    PsiDirectory directory = currentFile.getContainingDirectory();
    if (directory == null) {
      return null;
    }

    PsiFile overridesFile = directory.findFile("pubspec_overrides.yaml");
    if (!(overridesFile instanceof YAMLFile)) {
      return null;
    }

    YAMLFile yamlFile = (YAMLFile) overridesFile;
    YAMLDocument document = yamlFile.getDocuments().get(0);
    if (document == null) {
      return null;
    }

    YAMLValue topLevelValue = document.getTopLevelValue();
    if (!(topLevelValue instanceof YAMLMapping)) {
      return null;
    }

    YAMLMapping topLevelMapping = (YAMLMapping) topLevelValue;
    YAMLKeyValue overridesKeyValue = topLevelMapping.getKeyValueByKey("dependency_overrides");
    if (overridesKeyValue == null || !(overridesKeyValue.getValue() instanceof YAMLMapping)) {
      return null;
    }

    YAMLMapping overridesMapping = (YAMLMapping) overridesKeyValue.getValue();
    YAMLKeyValue packageKeyValue = overridesMapping.getKeyValueByKey(packageName);
    if (packageKeyValue == null) {
      return null;
    }

    return extractPackageInfo(packageKeyValue, packageName);
  }
}
