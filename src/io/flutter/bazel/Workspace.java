/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The directory tree for a Bazel workspace.
 * <p>
 * <p>Bazel workspaces are identified by looking for a WORKSPACE file.
 * <p>
 * <p>Also includes the flutter.json config file, which is loaded at the same time.
 */
public class Workspace {
  private static final String PLUGIN_CONFIG_PATH = "dart/config/ide/flutter.json";

  // TODO(jacobr): find a way to load the bazel uri scheme rather than hard coding it
  // as this scheme may change in the future.
  public static final String BAZEL_URI_SCHEME = "google3://";

  @NotNull private final VirtualFile root;
  @Nullable private final PluginConfig config;
  @Nullable private final String daemonScript;
  @Nullable private final String devToolsScript;
  @Nullable private final String doctorScript;
  @Nullable private final String testScript;
  @Nullable private final String runScript;
  @Nullable private final String syncScript;
  @Nullable private final String toolsScript;
  @Nullable private final String sdkHome;
  @Nullable private final String requiredIJPluginID;
  @Nullable private final String requiredIJPluginMessage;
  @Nullable private final String configWarningPrefix;
  @Nullable private final String updatedIosRunMessage;

  private Workspace(@NotNull VirtualFile root,
                    @Nullable PluginConfig config,
                    @Nullable String daemonScript,
                    @Nullable String devToolsScript,
                    @Nullable String doctorScript,
                    @Nullable String testScript,
                    @Nullable String runScript,
                    @Nullable String syncScript,
                    @Nullable String toolsScript,
                    @Nullable String sdkHome,
                    @Nullable String requiredIJPluginID,
                    @Nullable String requiredIJPluginMessage,
                    @Nullable String configWarningPrefix,
                    @Nullable String updatedIosRunMessage) {
    this.root = root;
    this.config = config;
    this.daemonScript = daemonScript;
    this.devToolsScript = devToolsScript;
    this.doctorScript = doctorScript;
    this.testScript = testScript;
    this.runScript = runScript;
    this.syncScript = syncScript;
    this.toolsScript = toolsScript;
    this.sdkHome = sdkHome;
    this.requiredIJPluginID = requiredIJPluginID;
    this.requiredIJPluginMessage = requiredIJPluginMessage;
    this.configWarningPrefix = configWarningPrefix;
    this.updatedIosRunMessage = updatedIosRunMessage;
  }

  /**
   * Returns the path to each content root within the module that is below the workspace root.
   * <p>
   * <p>Each path will be relative to the workspace root directory.
   */
  @NotNull
  public ImmutableSet<String> getContentPaths(@NotNull final Module module) {
    // Find all the content roots within this workspace.
    final VirtualFile[] contentRoots = OpenApiUtils.getContentRoots(module);
    final ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (VirtualFile root : contentRoots) {
      final String path = getRelativePath(root);
      if (path != null) {
        result.add(path);
      }
    }
    return result.build();
  }

  /**
   * Returns a VirtualFile's path relative to the workspace's root directory.
   * <p>
   * <p>Returns null for the workspace root or anything outside the workspace.
   */
  @Nullable
  public String getRelativePath(@Nullable VirtualFile file) {
    final List<String> path = new ArrayList<>();
    while (file != null) {
      if (file.equals(root)) {
        if (path.isEmpty()) {
          return null; // This is the root.
        }
        Collections.reverse(path);
        return Joiner.on('/').join(path);
      }
      path.add(file.getName());
      file = file.getParent();
    }
    return null;
  }

  /**
   * Returns the directory containing the WORKSPACE file.
   */
  @NotNull
  public VirtualFile getRoot() {
    return root;
  }

  /**
   * Returns the script that starts 'flutter daemon', or null if not configured.
   */
  @Nullable
  public String getDaemonScript() {
    return daemonScript;
  }

  /**
   * Returns the script that starts DevTools, or null if not configured.
   */
  @Nullable
  public String getDevToolsScript() {
    return devToolsScript;
  }

  /**
   * Returns the script that starts 'flutter doctor', or null if not configured.
   */
  @Nullable
  public String getDoctorScript() {
    return doctorScript;
  }

  /**
   * Returns the script that starts 'flutter test', or null if not configured.
   */
  @Nullable
  public String getTestScript() {
    return testScript;
  }

  /**
   * Returns the script that starts 'flutter run', or null if not configured.
   */
  @Nullable
  public String getRunScript() {
    return runScript;
  }

  /**
   * Returns the script that starts 'flutter sync', or null if not configured.
   */
  @Nullable
  public String getSyncScript() {
    return syncScript;
  }

  /**
   * Returns the generic script for running flutter actions, or null if not configured.
   */
  @Nullable
  public String getToolsScript() {
    return toolsScript;
  }

  /**
   * Returns the directory that contains the flutter SDK commands, or null if not configured.
   */
  @Nullable
  public String getSdkHome() {
    return sdkHome;
  }

  /**
   * Returns the required IJ plugin ID, or null if not configured.
   */
  @Nullable
  public String getRequiredIJPluginID() {
    return requiredIJPluginID;
  }

  /**
   * Returns the required IJ plugin message, if the plugin id is not installed, or null if not configured.
   */
  @Nullable
  public String getRequiredIJPluginMessage() {
    return requiredIJPluginMessage;
  }

  /**
   * Returns the prefix associated with configuration warnings.
   */
  @Nullable
  public String getConfigWarningPrefix() {
    return configWarningPrefix;
  }

  /**
   * Returns the message notifying users that running iOS apps has improved.
   */
  @Nullable
  public String getUpdatedIosRunMessage() {
    return updatedIosRunMessage;
  }


  /**
   * Returns true if the plugin config was loaded.
   */
  public boolean hasPluginConfig() {
    return config != null;
  }

  /**
   * Returns relative paths to the files within the workspace that it depends on.
   * <p>
   * <p>When they change, the Workspace should be reloaded.
   */
  @NotNull
  public Set<String> getDependencies() {
    return ImmutableSet.of("WORKSPACE", PLUGIN_CONFIG_PATH);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Workspace other)) return false;
    return Objects.equal(root, other.root) && Objects.equal(config, other.config);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(root, config);
  }

  /**
   * Loads the Bazel workspace from the filesystem.
   * <p>
   * <p>Also loads flutter.plugin if present.
   * <p>
   * <p>(Note that we might not load all the way from disk due to the VirtualFileSystem's caching.)
   * <p> Never call this method directly. Instead use WorkespaceCache.getInstance().
   * </p>
   *
   * @return the Workspace, or null if there is none.
   */
  @Nullable
  static Workspace loadUncached(@NotNull Project project) {
    if (project.isDisposed()) {
      return null;
    }
    final VirtualFile workspaceFile = findWorkspaceFile(project);
    if (workspaceFile == null) return null;

    final VirtualFile root = workspaceFile.getParent();
    if (root == null) return null;
    final String readonlyPath = "../READONLY/" + root.getName();
    final VirtualFile readonlyRoot = root.findFileByRelativePath(readonlyPath);
    VirtualFile configFile = root.findFileByRelativePath(PLUGIN_CONFIG_PATH);
    if (configFile == null && readonlyRoot != null) {
      configFile = readonlyRoot.findFileByRelativePath(PLUGIN_CONFIG_PATH);
    }
    if (configFile == null) return null;

    final PluginConfig config = PluginConfig.load(configFile);

    final String daemonScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getDaemonScript());

    final String devToolsScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getDevToolsScript());

    final String doctorScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getDoctorScript());

    final String testScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getTestScript());

    final String runScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getRunScript());

    final String syncScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getSyncScript());

    final String toolsScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getToolsScript());

    final String sdkHome = config == null ? null : getScriptFromPath(root, readonlyPath, config.getSdkHome());

    final String requiredIJPluginID = config == null ? null : config.getRequiredIJPluginID();

    final String requiredIJPluginMessage = config == null ? null : config.getRequiredIJPluginMessage();

    final String configWarningPrefix = config == null ? null : config.getConfigWarningPrefix();

    final String updatedIosRunMessage = config == null ? null : config.getUpdatedIosRunMessage();

    return new Workspace(root, config, daemonScript, devToolsScript, doctorScript, testScript, runScript, syncScript, toolsScript, sdkHome,
                         requiredIJPluginID, requiredIJPluginMessage, configWarningPrefix, updatedIosRunMessage);
  }

  @VisibleForTesting
  public static Workspace forTest(@NotNull VirtualFile workspaceRoot, @NotNull PluginConfig pluginConfig) {
    return new Workspace(
      workspaceRoot,
      pluginConfig,
      pluginConfig.getDaemonScript(),
      pluginConfig.getDevToolsScript(),
      pluginConfig.getDoctorScript(),
      pluginConfig.getTestScript(),
      pluginConfig.getRunScript(),
      pluginConfig.getSyncScript(),
      pluginConfig.getToolsScript(),
      pluginConfig.getSdkHome(),
      pluginConfig.getRequiredIJPluginID(),
      pluginConfig.getRequiredIJPluginMessage(),
      pluginConfig.getConfigWarningPrefix(),
      pluginConfig.getUpdatedIosRunMessage());
  }

  /**
   * Attempts to find a script inside of the workspace.
   *
   * @param root               the workspace root.
   * @param readonlyPath       the relative path to the readonly contents of the workspace.
   * @param relativeScriptPath the relative path to the desired script inside of the workspace.
   * @return the script's path relative to the workspace, or null if it was not found.
   */
  private static @Nullable String getScriptFromPath(@NotNull VirtualFile root,
                                                    @NotNull String readonlyPath,
                                                    @Nullable String relativeScriptPath) {
    if (relativeScriptPath == null) {
      return null;
    }
    final String readonlyScriptPath = readonlyPath + "/" + relativeScriptPath;
    if (root.findFileByRelativePath(relativeScriptPath) != null) {
      return relativeScriptPath;
    }
    if (root.findFileByRelativePath(readonlyScriptPath) != null) {
      return readonlyScriptPath;
    }
    return null;
  }


  /**
   * Returns the Bazel WORKSPACE file for a Project, or null if not using Bazel.
   * <p>
   * At least one content root must be within the workspace, and the project cannot have
   * content roots in more than one workspace.
   */
  @Nullable
  private static VirtualFile findWorkspaceFile(@NotNull Project p) {
    final Computable<VirtualFile> readAction = () -> {
      ProjectRootManager rootManager = ProjectRootManager.getInstance(p);
      if (rootManager == null) return null;
      
      final Map<String, VirtualFile> candidates = new HashMap<>();
      for (VirtualFile contentRoot : rootManager.getContentRoots()) {
        if (contentRoot == null) continue;
        final VirtualFile wf = findContainingWorkspaceFile(contentRoot);
        if (wf != null) {
          candidates.put(wf.getPath(), wf);
        }
      }

      if (candidates.size() == 1) {
        return candidates.values().iterator().next();
      }

      // not found
      return null;
    };
    return OpenApiUtils.safeRunReadAction(readAction);
  }

  /**
   * Returns the closest WORKSPACE file within or above the given directory, or null if not found.
   */
  @Nullable
  private static VirtualFile findContainingWorkspaceFile(@NotNull VirtualFile dir) {
    while (dir != null) {
      try {
        final VirtualFile child = dir.findChild("WORKSPACE");
        if (child != null && child.exists() && !child.isDirectory()) {
          return child;
        }
        dir = dir.getParent();
      }
      catch (InvalidVirtualFileAccessException ex) {
        // The VFS is out of sync.
        return null;
      }
    }

    // not found
    return null;
  }

  public String convertPath(@NotNull String path) {
    if (path.startsWith(Workspace.BAZEL_URI_SCHEME)) {
      return getRoot().getPath() + path.substring(Workspace.BAZEL_URI_SCHEME.length());
    }
    return path;
  }
}
