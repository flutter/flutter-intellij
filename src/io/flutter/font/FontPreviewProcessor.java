/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.font;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectAndLibrariesScope;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.ide.index.DartLibraryIndex;
import com.jetbrains.lang.dart.psi.DartComponentName;
import com.jetbrains.lang.dart.resolve.ClassNameScopeProcessor;
import com.jetbrains.lang.dart.resolve.DartPsiScopeProcessor;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import gnu.trove.THashSet;
import io.flutter.editor.FlutterIconLineMarkerProvider;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FontPreviewProcessor {

  // If there are triple quotes around a file name they won't be recognized.
  private static final Pattern EXPORT_STATEMENT_PATTERN = Pattern.compile("^\\s*export\\s+[\"']([-_. $A-Za-z0-9/]+\\.dart)[\"'].*");
  private static final Pattern IMPORT_STATEMENT_PATTERN = Pattern.compile("^\\s*import\\s+[\"']([-_. $A-Za-z0-9/]+\\.dart)[\"'].*");
  private static final Set<String> ANALYZED_FILES = new THashSet<>();
  private static Instant LAST_RUN = Instant.MIN;

  public void generate(@NotNull Project project) {
    if (wasRunRecently()) {
      return;
    }
    final String packagesText = FlutterSettings.getInstance().getFontPackages();
    final String[] packages = packagesText.split("[,\r\n]"); // or invert the set of allowed package name characters
    for (String pack : packages) {
      findFontClasses(project, pack.trim());
    }
  }

  private boolean wasRunRecently() {
    final Instant current = Instant.now();
    try {
      final Instant delta = current.minusSeconds(LAST_RUN.getEpochSecond());
      if (delta.toEpochMilli() < 60) {
        return true;
      }
    } catch (DateTimeException ex) {
      // ignored
    }
    LAST_RUN = current;
    return false;
  }

  private void findFontClasses(@NotNull Project project, @NotNull String packageName) {
    GlobalSearchScope scope = new ProjectAndLibrariesScope(project);
    Collection<VirtualFile> files = DartLibraryIndex.getFilesByLibName(scope, packageName);
    if (files.isEmpty()) {
      //scope = GlobalSearchScope.everythingScope(project);
      scope = GlobalSearchScope.allScope(project);
      files = FileTypeIndex.getFiles(DartFileType.INSTANCE, scope);
    }

    final List<VirtualFile> fileList = new ArrayList<>(files);
    int index = 0;
    while (index < fileList.size()) {
      final VirtualFile file = fileList.get(index++);
      final String path = file.getPath();
      if (ANALYZED_FILES.contains(path)) {
        continue;
      }
      ANALYZED_FILES.add(path);
      if (isInSdk(path)) {
        continue;
      }
      final int packageIndex = path.indexOf(packageName);
      if (packageIndex < 0) continue;
      final VirtualFile filteredFile = filterImports(file);
      if (filteredFile == null) {
        continue;
      }
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(filteredFile);
      if (psiFile == null) {
        continue;
      }
      final Set<DartComponentName> classNames = new THashSet<>();
      final DartPsiScopeProcessor processor = new ClassNameScopeProcessor(classNames);
      DartResolveUtil.processTopLevelDeclarations(psiFile, processor, file, null);
      final int size = FlutterIconLineMarkerProvider.KnownPaths.size();
      for (DartComponentName name : classNames) {
        final String declPath = name.getContainingFile().getVirtualFile().getPath();
        if (isInSdk(declPath)) {
          continue;
        }
        System.out.println(declPath);
        if (declPath.contains(packageName)) {
          final Set<String> knownPaths = FlutterIconLineMarkerProvider.KnownPaths.get(name.getName());
          if (knownPaths == null) {
            FlutterIconLineMarkerProvider.KnownPaths.put(name.getName(), new THashSet<String>(Collections.singleton(path)));
          } else {
            knownPaths.add(name.getName());
          }
        }
      }
      try {
        filteredFile.delete(this);
      }
      catch (IOException e) {
        // ignored
      }
      if (size == FlutterIconLineMarkerProvider.KnownPaths.size()) {
        if (!file.getPath().equals("/Users/messick/.pub-cache/hosted/pub.dartlang.org/flutter_icons-1.1.0/lib/flutter_icons.dart")) {
          continue;
        }
        try {
          final String source = new String(file.contentsToByteArray());
          final BufferedReader reader = new BufferedReader(new StringReader(source));
          String line;
          while ((line = reader.readLine()) != null) {
            final Matcher matcher = EXPORT_STATEMENT_PATTERN.matcher(line);
            if (!matcher.matches()) {
              continue;
            }
            final String name = matcher.group(1);
            if (name != null) {
              final VirtualFile next = LocalFileSystem.getInstance().findFileByNioFile(Paths.get(file.getParent().getPath(), name));
              final String nextPath;
              if (next == null || isInSdk(nextPath = next.getPath())) {
                continue;
              }
              if (ANALYZED_FILES.contains(nextPath)) {
                continue;
              }
              if (!files.contains(next)) {
                fileList.add(next);
              }
            }
          }
        }
        catch (IOException e) {
          // ignored
        }
      }
    }
  }

  private VirtualFile filterImports(VirtualFile file) {
    final StringBuilder newSource = new StringBuilder();
    final String source;
    try {
      source = new String(file.contentsToByteArray());
      final BufferedReader reader = new BufferedReader(new StringReader(source));
      String line;
      while ((line = reader.readLine()) != null) {
        final Matcher matcher = IMPORT_STATEMENT_PATTERN.matcher(line);
        if (matcher.matches()) {
          continue;
        }
        newSource.append(line);
        newSource.append('\n');
      }
      final File ioFile = FileUtil.createTempFile(file.getNameWithoutExtension(), ".dart");
      final VirtualFile newFile = LocalFileSystem.getInstance().findFileByIoFile(ioFile);
      if (newFile == null) {
        return null;
      }
      newFile.setBinaryContent(newSource.toString().getBytes(StandardCharsets.UTF_8));
      newFile.setCharset(StandardCharsets.UTF_8);
      return newFile;
    }
    catch (IOException e) {
      // ignored
    }
    return null;
  }

  private boolean isInSdk(String path) {
    return path.contains("flutter/packages/flutter/lib") || path.contains("flutter/bin/cache/dart-sdk");
  }
}
