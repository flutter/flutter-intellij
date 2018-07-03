/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.vfs.*;
import com.jetbrains.lang.dart.util.DotPackagesFileUtil;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jetbrains.lang.dart.util.PubspecYamlUtil.PUBSPEC_YAML;

/**
 * Manages the Flutter Plugins library, which hooks the packages used by plugins referenced in a project
 * into the project, so full editing support is available.
 *
 * @see io.flutter.sdk.FlutterPluginLibraryType
 * @see io.flutter.sdk.FlutterPluginLibraryProperties
 */
public class FlutterPluginsLibraryManager extends AbstractLibraryManager<FlutterPluginLibraryProperties> {

  private final AtomicBoolean isUpdating = new AtomicBoolean(false);

  public FlutterPluginsLibraryManager(@NotNull Project project) {
    super(project);
  }

  public void startWatching() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileContentsChangedAdapter() {
      @Override
      protected void onFileChange(@NotNull VirtualFile file) {
        fileChanged(getProject(), file);
      }

      @Override
      protected void onBeforeFileChange(@NotNull VirtualFile file) {
      }
    }, getProject());

    getProject().getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        scheduleUpdate();
      }
    });

    scheduleUpdate();
  }

  @Override
  @NotNull
  protected String getLibraryName() {
    return FlutterPluginLibraryType.FLUTTER_PLUGINS_LIBRARY_NAME;
  }

  @Override
  @NotNull
  protected PersistentLibraryKind<FlutterPluginLibraryProperties> getLibraryKind() {
    return FlutterPluginLibraryType.LIBRARY_KIND;
  }

  private void fileChanged(@SuppressWarnings("unused") @NotNull final Project project, @NotNull final VirtualFile file) {
    if (!DotPackagesFileUtil.DOT_PACKAGES.equals(file.getName())) return;
    if (LocalFileSystem.getInstance() != file.getFileSystem() && !ApplicationManager.getApplication().isUnitTestMode()) return;

    final VirtualFile parent = file.getParent();
    final VirtualFile pubspec = parent == null ? null : parent.findChild(PUBSPEC_YAML);

    if (pubspec != null) {
      scheduleUpdate();
    }
  }

  private void scheduleUpdate() {
    if (isUpdating.get()) {
      return;
    }

    final Runnable runnable = this::updateFlutterPlugins;
    DumbService.getInstance(getProject()).smartInvokeLater(runnable, ModalityState.NON_MODAL);
  }

  private void updateFlutterPlugins() {
    if (!isUpdating.compareAndSet(false, true)) {
      return;
    }

    try {
      updateFlutterPluginsImpl();
    }
    finally {
      isUpdating.set(false);
    }
  }

  private void updateFlutterPluginsImpl() {
    final Set<String> flutterPluginPaths = getFlutterPluginPaths(PubRoots.forProject(getProject()));
    final Set<String> flutterPluginUrls = new HashSet<>();
    for (String path : flutterPluginPaths) {
      flutterPluginUrls.add(VfsUtilCore.pathToUrl(path));
    }
    updateLibraryContent(flutterPluginUrls);
  }

  private static Set<String> getFlutterPluginPaths(List<PubRoot> roots) {
    final Set<String> paths = new HashSet<>();

    for (PubRoot pubRoot : roots) {
      if (pubRoot.getPackagesFile() == null) {
        continue;
      }

      final Map<String, String> map = DotPackagesFileUtil.getPackagesMap(pubRoot.getPackagesFile());
      if (map == null) {
        continue;
      }

      for (String packagePath : map.values()) {
        final VirtualFile libFolder = LocalFileSystem.getInstance().findFileByPath(packagePath);
        if (libFolder == null) {
          continue;
        }
        final PubRoot pluginRoot = PubRoot.forDirectory(libFolder.getParent());
        if (pluginRoot == null) {
          continue;
        }

        if (pluginRoot.isFlutterPlugin()) {
          paths.add(pluginRoot.getPath());
        }
      }
    }

    return paths;
  }
}
