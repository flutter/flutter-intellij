/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.font;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import io.flutter.FlutterBundle;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.flutter.editor.FlutterIconLineMarkerProvider.KnownPaths;

public class FontPreviewProcessor {

  public static final String PACKAGE_SEPARATORS = "[,\r\n]";
  public static final Map<String, String> UNSUPPORTED_PACKAGES = new THashMap<>();

  // If there are triple quotes around a package URL they won't be recognized.
  private static final Pattern EXPORT_STATEMENT_PATTERN = Pattern.compile("^\\s*export\\s+[\"']([-_. $A-Za-z0-9/]+\\.dart)[\"'].*");
  private static final Pattern IMPORT_STATEMENT_PATTERN = Pattern.compile("^\\s*import\\s+[\"']([-_. $A-Za-z0-9/]+\\.dart)[\"'].*");
  private static final Map<String, Set<String>> ANALYZED_PROJECT_FILES = new THashMap<>();

  static {
    UNSUPPORTED_PACKAGES.put("flutter_icons", FlutterBundle.message("icon.preview.disallow.flutter_icons"));
    UNSUPPORTED_PACKAGES.put("flutter_vector_icons", FlutterBundle.message("icon.preview.disallow.flutter_vector_icons"));
    UNSUPPORTED_PACKAGES.put("material_design_icons_flutter", FlutterBundle.message("icon.preview.disallow.material_design_icons_flutter"));
  }

  public static void reanalyze(@NotNull Project project) {
    final FontPreviewProcessor service = ApplicationManager.getApplication().getService(FontPreviewProcessor.class);
    service.clearProjectCaches(project);
    service.generate(project);
  }

  public void generate(@NotNull Project project) {
    if (ANALYZED_PROJECT_FILES.containsKey(project.getBasePath())) {
      return;
    }
    ANALYZED_PROJECT_FILES.put(project.getBasePath(), new THashSet<>());
    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        clearProjectCaches(project);
        ProjectManagerListener.super.projectClosed(project);
      }
    });
    final String packagesText = FlutterSettings.getInstance().getFontPackages();
    final String[] packages = packagesText.split(PACKAGE_SEPARATORS);
    for (String pack : packages) {
      findFontClasses(project, pack.trim());
    }
  }

  private void clearProjectCaches(@NotNull Project project) {
    ANALYZED_PROJECT_FILES.remove(project.getBasePath());
    // TODO Find a way to clean up KnownPaths.
  }

  // Look for classes in a package that define static variables with named icons.
  // We may have to analyze exports to get to them, as is done by some icon aggregator packages.
  private void findFontClasses(@NotNull Project project, @NotNull String packageName) {
    if (packageName.isEmpty() || FontPreviewProcessor.UNSUPPORTED_PACKAGES.get(packageName) != null) {
      return;
    }
    GlobalSearchScope scope = new ProjectAndLibrariesScope(project);
    Collection<VirtualFile> files = DartLibraryIndex.getFilesByLibName(scope, packageName);
    if (files.isEmpty()) {
      scope = GlobalSearchScope.allScope(project);
      files = FileTypeIndex.getFiles(DartFileType.INSTANCE, scope);
    }

    final Set<String> analyzedProjectFiles = ANALYZED_PROJECT_FILES.get(project.getBasePath());
    final Application application = ApplicationManager.getApplication();
    final List<VirtualFile> fileList = new ArrayList<>(files);
    int index = 0;
    while (index < fileList.size()) {
      final VirtualFile file = fileList.get(index++);
      final String path = file.getPath();
      final int packageIndex = path.indexOf(packageName);
      if (packageIndex < 0 || isInSdk(path)) {
        continue;
      }
      if (analyzedProjectFiles.contains(path)) {
        continue;
      }
      analyzedProjectFiles.add(path);
      // Remove import statements in an attempt to minimize extraneous analysis.
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
      boolean wasModified = false;
      for (DartComponentName name : classNames) {
        final String declPath = name.getContainingFile().getVirtualFile().getPath();
        if (isInSdk(declPath)) {
          continue;
        }
        if (declPath.contains(packageName)) {
          final Set<String> knownPaths = KnownPaths.get(name.getName());
          if (knownPaths == null) {
            KnownPaths.put(name.getName(), new THashSet<>(Collections.singleton(path)));
          }
          else {
            knownPaths.add(path);
          }
          wasModified = true;
        }
      }
      application.runWriteAction(() -> {
        try {
          filteredFile.delete(this); // need write access
        }
        catch (IOException e) {
          // ignored
        }
      });
      if (!wasModified) {
        // If no classes were found then the file may be a list of export statements that refer to files that do define icons.
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
              if (analyzedProjectFiles.contains(nextPath)) {
                continue;
              }
              if (!fileList.contains(next)) {
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
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          newFile.setBinaryContent(newSource.toString().getBytes(StandardCharsets.UTF_8));
          newFile.setCharset(StandardCharsets.UTF_8);
        } catch (IOException ex) {
          // ignored
        }
      });
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
