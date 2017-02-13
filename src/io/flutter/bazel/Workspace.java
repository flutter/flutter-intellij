/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The directory tree for a Bazel workspace.
 *
 * <p>Bazel workspaces are identified by looking for a WORKSPACE file.
 *
 * <p>Also includes the flutter.json config file, which is loaded at the same time.
 */
public class Workspace {
  private static final String PLUGIN_CONFIG_PATH = "dart/config/intellij-plugins/flutter.json";

  private @NotNull final VirtualFile root;
  private @Nullable final PluginConfig config;

  private Workspace(@NotNull VirtualFile root, @Nullable PluginConfig config) {
    this.root = root;
    this.config = config;
  }

  /**
   * Returns the directory containing the WORKSPACE file.
   */
  public @NotNull VirtualFile getRoot() {
    return root;
  }

  /**
   * Returns the flutter plugin configuration or null if not available.
   */
  public @Nullable PluginConfig getPluginConfig() {
    return config;
  }

  /**
   * Returns relative paths to the files within the workspace that it depends on.
   *
   * <p>When they change, the Workspace should be reloaded.
   */
  public @NotNull Set<String> getDependencies() {
    return ImmutableSet.of("WORKSPACE", PLUGIN_CONFIG_PATH);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Workspace)) return false;
    final Workspace other = (Workspace) obj;
    return Objects.equal(root, other.root) && Objects.equal(config, other.config);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(root, config);
  }

  /**
   * Loads the Bazel workspace from the filesystem.
   *
   * <p>Also loads flutter.plugin if present.
   *
   * <p>(Note that we might not load all the way from disk due to the VirtualFileSystem's caching.)
   *
   * @return the Workspace, or null if there is none.
   */
  public static @Nullable Workspace load(@NotNull Project project) {
    final VirtualFile workspaceFile = findWorkspaceFile(project);
    if (workspaceFile == null) return null;

    final VirtualFile root = workspaceFile.getParent();
    final VirtualFile configFile = root.findFileByRelativePath(PLUGIN_CONFIG_PATH);
    final PluginConfig config = configFile == null ? null : PluginConfig.load(configFile);
    return new Workspace(root, config);
  }

  /**
   * Returns the Bazel WORKSPACE file for a Project, or null if not using Bazel.
   *
   * At least one content root must be within the workspace, and the project cannot have
   * content roots in more than one workspace.
   */
  private static @Nullable VirtualFile findWorkspaceFile(@NotNull Project p) {
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
  private static @Nullable VirtualFile findContainingWorkspaceFile(@NotNull VirtualFile dir) {
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
}
