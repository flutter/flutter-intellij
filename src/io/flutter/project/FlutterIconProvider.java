/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import io.flutter.FlutterConstants;
import io.flutter.module.FlutterModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterIconProvider extends IconProvider {
  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, @Iconable.IconFlags final int flags) {
    if (element instanceof PsiDirectory && ModuleUtil.hasModulesOfType(element.getProject(), FlutterModuleType.getInstance())) {
      final VirtualFile folder = ((PsiDirectory)element).getVirtualFile();
      if (isFolderNearPubspecYaml(folder, "lib")) return AllIcons.Modules.SourceRoot;
      if (isFolderNearPubspecYaml(folder, ".idea")) return AllIcons.Modules.GeneratedFolder;
    }

    return null;
  }

  private static boolean isFolderNearPubspecYaml(final @Nullable VirtualFile folder, final @NotNull String folderName) {
    if (folder != null && folder.isDirectory() && folder.isInLocalFileSystem() && folderName.equals(folder.getName())) {
      final VirtualFile parentFolder = folder.getParent();
      final VirtualFile pubspecYamlFile = parentFolder != null ? parentFolder.findChild(FlutterConstants.PUBSPEC_YAML) : null;
      return pubspecYamlFile != null;
    }
    return false;
  }
}
