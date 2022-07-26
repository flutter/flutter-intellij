/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.io.URLUtil;
import com.jetbrains.lang.dart.util.DotPackagesFileUtil;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jetbrains.lang.dart.util.PubspecYamlUtil.PUBSPEC_YAML;

/**
 * Manages the Flutter Plugins library, which hooks the packages used by plugins referenced in a project
 * into the project, so full editing support is available.
 *
 * @see FlutterPluginLibraryType
 * @see FlutterPluginLibraryProperties
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
      public void rootsChanged(@NotNull ModuleRootEvent event) {
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

  private void fileChanged(@NotNull final Project project, @NotNull final VirtualFile file) {
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

      final Map<String, String> map;
      if (pubRoot.getPackagesFile() == null) {
        @Nullable VirtualFile configFile = pubRoot.getPackageConfigFile();
        if (configFile == null) {
          continue;
        }
        // TODO(messick) Use the code in the Dart plugin when available.
        // This is just a backup in case we need it. It does not have a proper cache, but the Dart plugin does.
        map = loadPackagesMap(configFile);
      }
      else {
        map = DotPackagesFileUtil.getPackagesMap(pubRoot.getPackagesFile());
        if (map == null) {
          continue;
        }
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

  private static Map<String, String> loadPackagesMap(@NotNull VirtualFile root) {
    Map<String, String> result = new HashMap<>();
    try {
      JsonElement element = JsonUtils.parseString(new String(root.contentsToByteArray(), StandardCharsets.UTF_8));
      if (element != null) {
        JsonElement packages = element.getAsJsonObject().get("packages");
        if (packages != null) {
          JsonArray array = packages.getAsJsonArray();
          for (int i = 0; i < array.size(); i++) {
            JsonObject pkg = array.get(i).getAsJsonObject();
            String name = pkg.get("name").getAsString();
            String rootUri = pkg.get("rootUri").getAsString();
            if (name != null && rootUri != null) {
              // need to protect '+' chars because URLDecoder.decode replaces '+' with space
              final String encodedUriWithoutPluses = StringUtil.replace(rootUri, "+", "%2B");
              final String uri = URLUtil.decode(encodedUriWithoutPluses);
              final String packageUri = getAbsolutePackageRootPath(root.getParent().getParent(), uri);
              result.put(name, packageUri);
            }
          }
        }
      }
    }
    catch (IOException | JsonSyntaxException ignored) {
    }
    return result;
  }

  @Nullable
  private static String getAbsolutePackageRootPath(@NotNull final VirtualFile baseDir, @NotNull final String uri) {
    // Copied from the Dart plugin.
    if (uri.startsWith("file:/")) {
      final String pathAfterSlashes = StringUtil.trimEnd(StringUtil.trimLeading(StringUtil.trimStart(uri, "file:/"), '/'), "/");
      if (SystemInfo.isWindows && !ApplicationManager.getApplication().isUnitTestMode()) {
        if (pathAfterSlashes.length() > 2 && Character.isLetter(pathAfterSlashes.charAt(0)) && ':' == pathAfterSlashes.charAt(1)) {
          return pathAfterSlashes;
        }
      }
      else {
        return "/" + pathAfterSlashes;
      }
    }
    else {
      return FileUtil.toCanonicalPath(baseDir.getPath() + "/" + uri);
    }

    return null;
  }
}
