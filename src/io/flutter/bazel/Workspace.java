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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
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
  private static final String PLUGIN_CONFIG_PATH = "dart/config/intellij-plugins/flutter.json";

  // TODO(jacobr): find a way to load the bazel uri scheme rather than hard coding it
  // as this scheme may change in the future.
  public static final String BAZEL_URI_SCHEME = "google3://";

  @NotNull private final VirtualFile root;
  @Nullable private final PluginConfig config;
  @Nullable private final String daemonScript;
  @Nullable private final String doctorScript;
  @Nullable private final String testScript;
  @Nullable private final String runScript;
  @Nullable private final String syncScript;
  @Nullable private final String sdkHome;
  @Nullable private final String versionFile;
  @Nullable private final String requiredIJPluginID;
  @Nullable private final String requiredIJPluginMessage;

  private Workspace(@NotNull VirtualFile root,
                    @Nullable PluginConfig config,
                    @Nullable String daemonScript,
                    @Nullable String doctorScript,
                    @Nullable String testScript,
                    @Nullable String runScript,
                    @Nullable String syncScript,
                    @Nullable String sdkHome,
                    @Nullable String versionFile,
                    @Nullable String requiredIJPluginID,
                    @Nullable String requiredIJPluginMessage) {
    this.root = root;
    this.config = config;
    this.daemonScript = daemonScript;
    this.doctorScript = doctorScript;
    this.testScript = testScript;
    this.runScript = runScript;
    this.syncScript = syncScript;
    this.sdkHome = sdkHome;
    this.versionFile = versionFile;
    this.requiredIJPluginID = requiredIJPluginID;
    this.requiredIJPluginMessage = requiredIJPluginMessage;
  }

  /**
   * Returns the path to each content root within the module that is below the workspace root.
   * <p>
   * <p>Each path will be relative to the workspace root directory.
   */
  @NotNull
  public ImmutableSet<String> getContentPaths(@NotNull final Module module) {
    // Find all the content roots within this workspace.
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
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
   * Returns the directory that contains the flutter SDK commands, or null if not configured.
   */
  @Nullable
  public String getSdkHome() {
    return sdkHome;
  }

  /**
   * Returns the file for the in-use version of Flutter, or null if not configured.
   */
  @Nullable
  public String getVersionFile() {
    return versionFile;
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
    if (!(obj instanceof Workspace)) return false;
    final Workspace other = (Workspace)obj;
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
    final String readonlyPath = "../READONLY/" + root.getName();
    final VirtualFile readonlyRoot = root.findFileByRelativePath(readonlyPath);
    VirtualFile configFile = root.findFileByRelativePath(PLUGIN_CONFIG_PATH);
    if (configFile == null && readonlyRoot != null) {
      configFile = readonlyRoot.findFileByRelativePath(PLUGIN_CONFIG_PATH);
    }
    if (configFile == null) return null;

    final PluginConfig config = PluginConfig.load(configFile);

    final String daemonScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getDaemonScript());

    final String doctorScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getDoctorScript());

    final String testScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getTestScript());

    final String runScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getRunScript());

    final String syncScript = config == null ? null : getScriptFromPath(root, readonlyPath, config.getSyncScript());

    final String sdkHome = config == null ? null : getScriptFromPath(root, readonlyPath, config.getSdkHome());

    final String versionFile = config == null ? null : getScriptFromPath(root, readonlyPath, config.getVersionFile());

    final String requiredIJPluginID = config == null ? null : getScriptFromPath(root, readonlyPath, config.getRequiredIJPluginID());

    final String requiredIJPluginMessage = config == null ? null : getScriptFromPath(root, readonlyPath, config.getRequiredIJPluginMessage());

    return new Workspace(root, config, daemonScript, doctorScript, testScript, runScript, syncScript, sdkHome, versionFile, requiredIJPluginID, requiredIJPluginMessage);
  }

  @VisibleForTesting
  public static Workspace forTest(VirtualFile workspaceRoot, PluginConfig pluginConfig) {
    return new Workspace(
      workspaceRoot,
      pluginConfig,
      pluginConfig.getDaemonScript(),
      pluginConfig.getDoctorScript(),
      pluginConfig.getTestScript(),
      pluginConfig.getRunScript(),
      pluginConfig.getSyncScript(),
      pluginConfig.getSdkHome(),
      pluginConfig.getVersionFile(),
      pluginConfig.getRequiredIJPluginID(),
      pluginConfig.getRequiredIJPluginMessage());
  }

  /**
   * Attempts to find a script inside of the workspace.
   *
   * @param root               the workspace root.
   * @param readonlyPath       the relative path to the readonly contents of the workspace.
   * @param relativeScriptPath the relative path to the desired script inside of the workspace.
   * @return the script's path relative to the workspace, or null if it was not found.
   */
  private static String getScriptFromPath(@NotNull VirtualFile root, @NotNull String readonlyPath, @Nullable String relativeScriptPath) {
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
      final Map<String, VirtualFile> candidates = new HashMap<>();
      for (VirtualFile contentRoot : ProjectRootManager.getInstance(p).getContentRoots()) {
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
    return ApplicationManager.getApplication().runReadAction(readAction);
  }

  /**
   * Returns the closest WORKSPACE file within or above the given directory, or null if not found.
   */
  @Nullable
  private static VirtualFile findContainingWorkspaceFile(@NotNull VirtualFile dir) {
    while (dir != null) {
      final VirtualFile child = dir.findChild("WORKSPACE");
      if (child != null && child.exists() && !child.isDirectory()) {
        return child;
      }
      dir = dir.getParent();
    }

    // not found
    return null;
  }

  private static final Logger LOG = Logger.getInstance(Workspace.class);

  public String convertPath(String path) {
    if (path.startsWith(Workspace.BAZEL_URI_SCHEME)) {
      return getRoot().getPath() + path.substring(Workspace.BAZEL_URI_SCHEME.length());
    }
    return path;
  }
}
