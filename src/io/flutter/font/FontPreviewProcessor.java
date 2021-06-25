/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.font;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectAndLibrariesScope;
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
    final GlobalSearchScope scope = new ProjectAndLibrariesScope(project);
    final Collection<VirtualFile> files = DartLibraryIndex.getFilesByLibName(scope, packageName);
    Set<DartComponentName> classNames = new THashSet<>();

    for (VirtualFile file : files) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) {
        continue;
      }
      final String path = file.getPath();
      final int packageIndex = path.indexOf(packageName);
      if (packageIndex < 0) continue;
      DartPsiScopeProcessor processor = new ClassNameScopeProcessor(classNames);
      DartResolveUtil.processTopLevelDeclarations(psiFile, processor, file, null);
      for (DartComponentName name : classNames) {
        if (name.getContainingFile().equals(psiFile)) {
          FlutterIconLineMarkerProvider.KnownPaths.put(name.getName(), path);
        }
      }
    }
  }
}
