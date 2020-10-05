/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable.IconFlags;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.LayeredIcon;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.psi.DartFile;
import icons.FlutterIcons;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRootCache;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class FlutterIconProvider extends IconProvider {
  private static final Icon TEST_FILE = overlayIcons(DartFileType.INSTANCE.getIcon(), AllIcons.Nodes.JunitTestMark);

  @Nullable
  public Icon getIcon(@NotNull PsiElement element, @IconFlags int flags) {
    final Project project = element.getProject();
    if (!FlutterModuleUtils.declaresFlutter(project)) return null;

    // Directories.
    if (element instanceof PsiDirectory) {
      final VirtualFile dir = ((PsiDirectory)element).getVirtualFile();
      if (!dir.isInLocalFileSystem()) return null;

      final PubRootCache pubRootCache = PubRootCache.getInstance(project);

      // Show an icon for flutter modules.
      final PubRoot pubRoot = pubRootCache.getRoot(dir);
      if (pubRoot != null && dir.equals(pubRoot.getRoot()) && pubRoot.declaresFlutter()) {
        return FlutterIcons.Flutter;
      }

      final PubRoot root = pubRootCache.getRoot(dir.getParent());
      if (root == null) return null;

      // TODO(devoncarew): should we just make the folder a source kind?
      if (dir.equals(root.getLib())) return AllIcons.Modules.SourceRoot;

      if (Objects.equals(dir, root.getAndroidDir())) return AllIcons.Nodes.KeymapTools;
      if (Objects.equals(dir, root.getiOsDir())) return AllIcons.Nodes.KeymapTools;

      if (dir.isDirectory() && dir.getName().equals(".idea")) return AllIcons.Modules.GeneratedFolder;
    }

    // Files.
    if (element instanceof DartFile) {
      final DartFile dartFile = (DartFile)element;
      final VirtualFile file = dartFile.getVirtualFile();
      if (file == null || !file.isInLocalFileSystem()) return null;

      // Use a simple naming convention heuristic to identify test files.
      // TODO(pq): consider pushing up to the Dart Plugin.
      if (FlutterUtils.isInTestDir(dartFile) && file.getName().endsWith("_test.dart")) {
        return TEST_FILE;
      }
    }

    return null;
  }

  @NotNull
  private static Icon overlayIcons(@NotNull Icon... icons) {
    final LayeredIcon result = new LayeredIcon(icons.length);

    for (int layer = 0; layer < icons.length; layer++) {
      result.setIcon(icons[layer], layer);
    }

    return result;
  }
}
