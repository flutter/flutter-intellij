/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.font;

import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.*;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.ide.index.DartComponentIndex;
import com.jetbrains.lang.dart.ide.index.DartLibraryIndex;
import com.jetbrains.lang.dart.psi.DartComponentName;
import com.jetbrains.lang.dart.resolve.ClassNameScopeProcessor;
import com.jetbrains.lang.dart.resolve.DartPsiScopeProcessor;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import gnu.trove.THashSet;
import io.flutter.editor.FlutterIconLineMarkerProvider;
import io.flutter.settings.FlutterSettings;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class FontPreviewProcessor {

  public void generate(@NotNull Project project) {
    final String packagesText = FlutterSettings.getInstance().getFontPackages();
    final String[] packages = packagesText.split("[,\r\n]"); // or invert the set of allowed package name characters
    for (String pack : packages) {
      findFontClasses(project, pack.trim());
    }
  }

  private void findFontClasses(@NotNull Project project, @NotNull String packageName) {
    GlobalSearchScope scope = new ProjectAndLibrariesScope(project);
    Collection<VirtualFile> files = DartLibraryIndex.getFilesByLibName(scope, packageName);
    if (files.isEmpty()) {
      scope = GlobalSearchScope.everythingScope(project);
      scope = GlobalSearchScope.allScope(project);
      files = FileTypeIndex.getFiles(DartFileType.INSTANCE, scope);
    }
    final Set<DartComponentName> classNames = new THashSet<>();

    for (VirtualFile file : files) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) {
        continue;
      }
      final String path = file.getPath();
      if (path.contains("flutter/packages/flutter/lib") || path.contains("flutter/bin/cache/dart-sdk")) {
        continue;
      }
      final int packageIndex = path.indexOf(packageName);
      if (packageIndex < 0) continue;
      final DartPsiScopeProcessor processor = new ClassNameScopeProcessor(classNames);
      DartResolveUtil.processTopLevelDeclarations(psiFile, processor, file, null);
      for (DartComponentName name : classNames) {
        final String declPath = name.getContainingFile().getVirtualFile().getPath();
        if (declPath.contains("flutter/packages/flutter/lib") || declPath.contains("flutter/bin/cache/dart-sdk")) {
          continue;
        }
        System.out.println(declPath);
        if (name.getContainingFile().equals(psiFile)) {
          FlutterIconLineMarkerProvider.KnownPaths.put(name.getName(), path);
        }
      }
    }
  }
}
