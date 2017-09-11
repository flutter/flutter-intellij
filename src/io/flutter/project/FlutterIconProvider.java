/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.psi.DartFile;
import icons.FlutterIcons;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

import static com.intellij.psi.impl.ElementBase.overlayIcons;

public class FlutterIconProvider extends IconProvider {

  private static final Icon TEST_FILE = overlayIcons(DartFileType.INSTANCE.getIcon(), AllIcons.Nodes.JunitTestMark);

  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, @Iconable.IconFlags final int flags) {
    final Project project = element.getProject();
    if (!FlutterModuleUtils.hasFlutterModule(project)) return null;

    // Directories.
    if (element instanceof PsiDirectory) {
      final VirtualFile file = ((PsiDirectory)element).getVirtualFile();
      if (!file.isInLocalFileSystem()) return null;

      // Project root.
      if (Objects.equals(file.getPath(), project.getBaseDir().getPath())) {
        return FlutterIcons.Flutter;
      }

      final PubRoot root = PubRoot.forDirectory(file.getParent());
      if (root == null) return null;

      // TODO(devoncarew): should we just make the folder a source kind?
      if (file.equals(root.getLib())) return AllIcons.Modules.SourceRoot;

      if (Objects.equals(file, root.getAndroidDir())) return AllIcons.Nodes.KeymapTools;
      if (Objects.equals(file, root.getiOsDir())) return AllIcons.Nodes.KeymapTools;

      if (file.isDirectory() && file.getName().equals(".idea")) return AllIcons.Modules.GeneratedFolder;
    }

    // Files.
    if (element instanceof DartFile) {
      final DartFile dartFile = (DartFile)element;
      final VirtualFile file = dartFile.getVirtualFile();
      if (!file.isInLocalFileSystem()) return null;

      // Use a simple naming convention heuristic to identify test files.
      // TODO(pq): consider pushing up to the Dart Plugin.
      if (FlutterUtils.isInTestDir(dartFile) && file.getName().endsWith("_test.dart")) {
        return TEST_FILE;
      }
    }

    return null;
  }
}
