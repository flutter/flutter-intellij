/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.font;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
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
import io.flutter.editor.FlutterIconLineMarkerProvider;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.flutter.editor.FlutterIconLineMarkerProvider.KnownPaths;

public class FontPreviewProcessor {

  public static final String PACKAGE_SEPARATORS = "[,\r\n]";
  public static final Map<String, String> UNSUPPORTED_PACKAGES = new THashMap<>();

  // If there are triple quotes around a package URL they won't be recognized.
  private static final Pattern EXPORT_STATEMENT_PATTERN = Pattern.compile("^\\s*export\\s+[\"']([-_. $A-Za-z0-9/]+\\.dart)[\"'].*");
  private static final Pattern IMPORT_STATEMENT_PATTERN = Pattern.compile("^\\s*import\\s+[\"']([-_. $A-Za-z0-9/]+\\.dart)[\"'].*");
  private static final Map<String, Set<String>> ANALYZED_PROJECT_FILES = new THashMap<>();
  private static final Map<String, WorkItem> WORK_ITEMS = new THashMap<>();
  private static Logger LOG = Logger.getInstance(FontPreviewProcessor.class);

  static {
    UNSUPPORTED_PACKAGES.put("flutter_icons", FlutterBundle.message("icon.preview.disallow.flutter_icons"));
    UNSUPPORTED_PACKAGES.put("flutter_vector_icons", FlutterBundle.message("icon.preview.disallow.flutter_vector_icons"));
    UNSUPPORTED_PACKAGES.put("material_design_icons_flutter", FlutterBundle.message("icon.preview.disallow.material_design_icons_flutter"));
  }

  public static void analyze(@NotNull Project project) {
    final FontPreviewProcessor service = ApplicationManager.getApplication().getService(FontPreviewProcessor.class);
    service.generate(project);
  }

  public static void reanalyze(@NotNull Project project) {
    clearProjectCaches(project);
    analyze(project);
  }

  public void generate(@NotNull Project project) {
    if (ANALYZED_PROJECT_FILES.containsKey(project.getBasePath())) {
      return;
    }
    LOG = FlutterSettings.getInstance().isVerboseLogging() ? Logger.getInstance(FontPreviewProcessor.class) : null;
    log("Analyzing project ", project.getName());
    ANALYZED_PROJECT_FILES.put(project.getBasePath(), new THashSet<>());
    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        clearProjectCaches(project);
        ProjectManagerListener.super.projectClosed(project);
      }
    });
    final String projectPath = project.getBasePath();
    final WorkItem item = new WorkItem(projectPath);
    WORK_ITEMS.put(projectPath, item);

    final String packagesText = FlutterSettings.getInstance().getFontPackages();
    final String[] packages = packagesText.split(PACKAGE_SEPARATORS);
    item.addPackages(
      Arrays.stream(packages)
        .map(String::trim)
        .filter((each) -> !each.isEmpty() || FontPreviewProcessor.UNSUPPORTED_PACKAGES.get(each) != null)
        .collect(Collectors.toList()));
    processItems(project);
  }

  void processItems(@NotNull Project project) {
    final Task.Backgroundable task = new Task.Backgroundable(project, FlutterBundle.message("icon.preview.analysis"), true) {

      public void run(@NotNull final ProgressIndicator indicator) {
        final AtomicBoolean isRunning = new AtomicBoolean(true);
        final long startTime = System.currentTimeMillis();
        while (isRunning.get()) {
          final String path = project.getBasePath();
          final WorkItem item = WORK_ITEMS.get(path);
          if (!processNextItem(project, item)) {
            if (item.filesWithNoClasses.isEmpty()) {
              // Finished, clean up and exit.
              synchronized (this) {
                if (item == WORK_ITEMS.get(path)) {
                  WORK_ITEMS.remove(path);
                }
                isRunning.set(false);
                if (System.currentTimeMillis() - startTime > 1000) {
                  // If this analysis takes too long there is a good chance the highlighting pass completed before all
                  // icon classes were found. That might cause some icons to not get displayed, so just run it again.
                  DaemonCodeAnalyzer.getInstance(project).restart();
                }
              }
            }
            else {
              for (String key : item.filesWithNoClasses.keySet()) {
                final PathInfo info = item.filesWithNoClasses.get(key);
                final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(info.filePath);
                if (virtualFile != null) {
                  item.addFileToCheck(info.packageName, info.filePath, virtualFile);
                }
              }
              item.filesWithNoClasses.clear();
            }
          }
        }
      }

      public void onCancel() {
        if (project.isDisposed()) {
          return;
        }
        clearProjectCaches(project);
        DaemonCodeAnalyzer.getInstance(project).restart();
      }
    };

    ProgressManager.getInstance().run(task);
  }

  // Analyze the next item in the queue. Leave it in the queue until analysis is complete.
  // Any switch to dumb mode during a read action or a write action will cancel this analysis,
  // which will restart with the same item.
  boolean processNextItem(@NotNull Project project, @NotNull WorkItem item) {
    if (item.isCancelled) {
      return false;
    }
    final Application app = ApplicationManager.getApplication();
    if (item.hasFilesToRewrite()) {
      app.invokeLater(() -> rewriteNextFile(project, item));
      return true;
    }
    else if (item.hasClasses()) {
      analyzeNextClass(project, item);
      return true;
    }
    else if (item.hasFilesToAnalyze()) {
      analyzeNextFile(project, item);
      return true;
    }
    else if (item.hasPackages()) {
      analyzeNextPackage(project, item);
      return true;
    }
    else if (item.hasFilesToCheck()) {
      checkNextFile(project, item);
      return true;
    }
    else if (item.hasFilesToDelete()) {
      app.invokeLater(() -> deleteNextFile(project, item));
      return true;
    }
    return false;
  }

  private void analyzeNextPackage(@NotNull Project project, @NotNull WorkItem item) {
    if (project.isDisposed() || item.isCancelled) {
      return;
    }
    final String info = item.getPackage();
    if (info == null) {
      return;
    }
    findFontFiles(project, info, item);
    item.removePackage();
  }

  private void rewriteNextFile(@NotNull Project project, @NotNull WorkItem item) {
    if (project.isDisposed() || item.isCancelled) {
      return;
    }
    final FileInfo info = item.getFileToRewrite();
    if (info == null) {
      return;
    }
    final VirtualFile file = info.file;
    final String packageName = info.packageName;
    final String path = file.getPath();
    final int packageIndex = path.indexOf(packageName);
    if (ANALYZED_PROJECT_FILES.get(item.projectPath).contains(path)) {
      item.removeFileToRewrite();
      return;
    }
    log("Rewriting file ", file.getName(), " in ", packageName);
    // Remove import statements in an attempt to minimize extraneous analysis.
    final VirtualFile filteredFile = filterImports(file);
    if (filteredFile == null) {
      log("Cannot filter imports in ", file.getName());
      return;
    }
    item.filesWithNoClasses.put(path, new PathInfo(packageName, path));
    item.addFileToAnalyze(packageName, path, filteredFile);
    item.addFileToDelete(filteredFile);
    item.removeFileToRewrite();
  }

  private void analyzeNextFile(@NotNull Project project, @NotNull WorkItem item) {
    if (project.isDisposed() || item.isCancelled) {
      return;
    }
    final FileInfo info = item.getFileToAnalyze();
    if (info == null) {
      return;
    }
    final String packageName = info.packageName;
    final String path = info.originalPath;
    final VirtualFile file = info.file;
    final Set<String> analyzedProjectFiles = ANALYZED_PROJECT_FILES.get(item.projectPath);
    if (analyzedProjectFiles.contains(path)) {
      item.removeFileToAnalyze();
      return;
    }
    log("Analyzing file ", file.getPath(), " path ", path);
    analyzedProjectFiles.add(path);
    final PsiFile psiFile = DumbService.getInstance(project).runReadActionInSmartMode(() -> PsiManager.getInstance(project).findFile(file));
    if (psiFile == null) {
      log("Cannot get PSI file for ", file.getName());
      return;
    }
    final Set<DartComponentName> classNames = new THashSet<>();
    final DartPsiScopeProcessor processor = new ClassNameScopeProcessor(classNames);
    final boolean success = DumbService.getInstance(project).runReadActionInSmartMode(() -> {
      final boolean result = DartResolveUtil.processTopLevelDeclarations(psiFile, processor, file, null);
      if (result) {
        final Set<DartComponentName> keep = new THashSet<>();
        for (DartComponentName name : classNames) {
          if (file.equals(name.getContainingFile().getVirtualFile())) {
            keep.add(name);
          }
        }
        classNames.clear();
        classNames.addAll(keep);
      }
      return result; // Return from lambda, not method, setting value of success.
    });
    if (success) {
      log("Queueing ", String.valueOf(classNames.size()), " classes for ", path);
      item.addClasses(packageName, path, classNames);
    }
    else {
      log("Resolution failed for ", path);
    }
    item.removeFileToAnalyze();
  }

  private void analyzeNextClass(@NotNull Project project, @NotNull WorkItem item) {
    if (project.isDisposed() || item.isCancelled) {
      return;
    }
    final ClassInfo info = item.getClassToCheck();
    if (info == null) {
      return;
    }
    final String packageName = info.packageName;
    final String path = info.filePath;
    final PsiFile psiFile = DumbService.getInstance(project).runReadActionInSmartMode(info.name::getContainingFile);
    if (path.contains(packageName)) {
      final String name = DumbService.getInstance(project).runReadActionInSmartMode(info.name::getName);
      log("Adding ", name, " -> ", path);
      final Set<String> knownPaths = KnownPaths.get(name);
      if (knownPaths == null) {
        KnownPaths.put(name, new THashSet<>(Collections.singleton(path)));
      }
      else {
        knownPaths.add(path);
      }
      item.filesWithNoClasses.remove(path);
    }
    item.removeClassToCheck();
  }

  private void checkNextFile(@NotNull Project project, @NotNull WorkItem item) {
    if (project.isDisposed() || item.isCancelled) {
      return;
    }
    // If no classes were found then the file may be a list of export statements that refer to files that do define icons.
    try {
      final FileInfo info = item.getFileToCheck();
      if (info == null) {
        return;
      }
      final VirtualFile file = info.file;
      log("Checking for exports in ", file.getPath(), " path ", info.originalPath);
      final String packageName = info.packageName;
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
          if (ANALYZED_PROJECT_FILES.get(project.getBasePath()).contains(nextPath)) {
            continue;
          }
          item.addFileToAnalyze(packageName, nextPath, next);
        }
      }
      item.removeFileToCheck();
    }
    catch (IOException e) {
      // ignored
      log("IOException", e);
    }
  }

  private void deleteNextFile(Project project, WorkItem item) {
    final VirtualFile filteredFile = item.getFileToDelete();
    if (filteredFile != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          log("Deleting file ", filteredFile.getName());
          filteredFile.delete(this); // need write access
          item.removeFileToDelete();
        }
        catch (IOException e) {
          // ignored
        }
      });
    }
  }

  private static void clearProjectCaches(@NotNull Project project) {
    ANALYZED_PROJECT_FILES.remove(project.getBasePath());
    WORK_ITEMS.remove(project.getBasePath());
    FlutterIconLineMarkerProvider.initialize();
  }

  // Look for classes in a package that define static variables with named icons.
  // We may have to analyze exports to get to them, as is done by some icon aggregator packages.
  private void findFontFiles(@NotNull Project project, @NotNull String packageName, @NotNull WorkItem item) {
    if (packageName.isEmpty() || FontPreviewProcessor.UNSUPPORTED_PACKAGES.get(packageName) != null) {
      return;
    }
    log("Analyzing package ", packageName);
    final Application application = ApplicationManager.getApplication();
    final GlobalSearchScope projectScope = new ProjectAndLibrariesScope(project);
    Collection<VirtualFile> files = DumbService.getInstance(project)
      .runReadActionInSmartMode(() -> DartLibraryIndex.getFilesByLibName(projectScope, packageName));
    if (files.isEmpty()) {
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      files = DumbService.getInstance(project).runReadActionInSmartMode(() -> FileTypeIndex.getFiles(DartFileType.INSTANCE, scope));
      // TODO(messick) This finds way too many files. Optimize.
    }
    item.addFilesToRewrite(packageName, files);
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
      final Application app = ApplicationManager.getApplication();
      app.invokeAndWait(() -> {
        app.runWriteAction(() -> {
          try {
            newFile.setBinaryContent(newSource.toString().getBytes(StandardCharsets.UTF_8));
            newFile.setCharset(StandardCharsets.UTF_8);
          }
          catch (IOException ex) {
            // ignored
          }
        });
      });
      return newFile;
    }
    catch (IOException e) {
      // ignored
    }
    return null;
  }

  private static boolean isInSdk(String path) {
    return path.contains("flutter/packages/flutter/lib") || path.contains("flutter/bin/cache/dart-sdk");
  }

  private static void log(String msg, String... msgs) {
    if (LOG != null) {
      final StringBuilder b = new StringBuilder("ICONS -- ");
      b.append(msg);
      for (String s : msgs) {
        b.append(" ").append(s);
      }
      LOG.info(b.toString());
    }
  }

  private static void log(String msg, Exception ex) {
    if (LOG != null) {
      LOG.info("ICONS--" + msg, ex);
    }
  }

  static class WorkItem {
    final Queue<String> packagesToAnalyze = new LinkedList<>();
    final Queue<FileInfo> filesToAnalyze = new LinkedList<>();
    final Queue<VirtualFile> filesToDelete = new LinkedList<>();
    final Queue<FileInfo> filesToRewrite = new LinkedList<>();
    final Queue<ClassInfo> classesToAnalyze = new LinkedList<>();
    final Queue<FileInfo> filesToCheck = new LinkedList<>();
    final Map<String, PathInfo> filesWithNoClasses = new THashMap<>();
    final String projectPath;
    boolean isCancelled = false;

    WorkItem(String packageName) {
      this.projectPath = packageName;
    }

    boolean hasClasses() {
      return !classesToAnalyze.isEmpty();
    }

    boolean hasFilesToAnalyze() {
      return !filesToAnalyze.isEmpty();
    }

    boolean hasFilesToCheck() {
      return !filesToCheck.isEmpty();
    }

    boolean hasFilesToDelete() {
      return !filesToDelete.isEmpty();
    }

    boolean hasFilesToRewrite() {
      return !filesToRewrite.isEmpty();
    }

    boolean hasPackages() {
      return !packagesToAnalyze.isEmpty();
    }

    void addFileToAnalyze(@NotNull String packageName, @NotNull String path, @NotNull VirtualFile name) {
      filesToAnalyze.add(new FileInfo(packageName, path, name));
    }

    void addFileToCheck(@NotNull String packageName, @NotNull String path, @NotNull VirtualFile name) {
      filesToCheck.add(new FileInfo(packageName, path, name));
    }

    public void addFileToDelete(@NotNull VirtualFile file) {
      filesToDelete.add(file);
    }

    void addFileToRewrite(@NotNull String packageName, @NotNull String path, @NotNull VirtualFile name) {
      filesToRewrite.add(new FileInfo(packageName, path, name));
    }

    @Nullable
    FileInfo getFileToAnalyze() {
      return filesToAnalyze.peek();
    }

    @Nullable
    ClassInfo getClassToCheck() {
      return classesToAnalyze.peek();
    }

    void removeClassToCheck() {
      classesToAnalyze.remove();
    }

    void removeFileToAnalyze() {
      filesToAnalyze.remove();
    }

    @Nullable
    FileInfo getFileToCheck() {
      return filesToCheck.peek();
    }

    void removeFileToCheck() {
      filesToCheck.remove();
    }

    @Nullable
    FileInfo getFileToRewrite() {
      return filesToRewrite.peek();
    }

    void removeFileToRewrite() {
      synchronized (this) {
        if (!filesToRewrite.isEmpty()) {
          filesToRewrite.remove();
        }
      }
    }

    @Nullable
    VirtualFile getFileToDelete() {
      return filesToDelete.peek();
    }

    void removeFileToDelete() {
      synchronized (this) {
        if (!filesToDelete.isEmpty()) {
          filesToDelete.remove();
        }
      }
    }

    @Nullable
    String getPackage() {
      return packagesToAnalyze.peek();
    }

    void removePackage() {
      packagesToAnalyze.remove();
    }

    void addPackages(@NotNull List<String> packages) {
      packagesToAnalyze.addAll(packages);
    }

    public void addClasses(@NotNull String packageName, @NotNull String filePath, @NotNull Set<DartComponentName> names) {
      classesToAnalyze.addAll(
        names.stream()
          .map((each) -> new ClassInfo(packageName, filePath, each))
          .collect(Collectors.toList()));
    }

    void addFilesToRewrite(@NotNull String packageName, @NotNull Collection<VirtualFile> files) {
      filesToRewrite.addAll(
        files.stream()
          .filter((each) -> {
            final String path = each.getPath();
            final int packageIndex = path.indexOf(packageName);
            return !(packageIndex < 0 || isInSdk(path));
          })
          .map((each) -> new FileInfo(packageName, each.getPath(), each))
          .collect(Collectors.toList()));
    }
  }

  static class ClassInfo {
    private final String packageName;
    private final String filePath;
    private final DartComponentName name;

    ClassInfo(String packageName, String filePath, DartComponentName name) {
      this.packageName = packageName;
      this.filePath = filePath;
      this.name = name;
    }
  }

  static class FileInfo {
    private final String packageName;
    private final String originalPath;
    private final VirtualFile file;

    FileInfo(String packageName, String path, VirtualFile file) {
      this.packageName = packageName;
      this.originalPath = path;
      this.file = file;
    }
  }

  static class PathInfo {
    private final String packageName;
    private final String filePath;

    PathInfo(String packageName, String filePath) {
      this.packageName = packageName;
      this.filePath = filePath;
    }
  }
}
