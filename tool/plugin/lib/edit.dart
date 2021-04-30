/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

// @dart = 2.10

import 'dart:async';
import 'dart:io';

import 'build_spec.dart';
import 'util.dart';

Set<EditCommand> appliedEditCommands = {};

void checkAndClearAppliedEditCommands() {
  if (appliedEditCommands.length != editCommands.length) {
    var commands = <EditCommand>{};
    commands.addAll(editCommands);
    commands.removeAll(appliedEditCommands);
    separator("UNUSED EditCommand");
    commands.forEach((cmd) => log(cmd.toString()));
  }
  appliedEditCommands.clear();
}

List<EditCommand> editCommands = [
  EditAndroidModuleLibraryManager(),
  Subst(
    path: 'build.gradle',
    initial: 'localPath "\${project.rootDir.absolutePath}/artifacts/\$ide"',
    replacement: 'type = "IC"\n  version = "211.6693.111"',
    version: '2021.1',
  ),
  Subst(
    path: 'flutter-idea/build.gradle',
    initial: 'localPath "\${project.rootDir.absolutePath}/artifacts/\$ide"',
    replacement: 'type = "IC"\n  version = "211.6693.111"',
    version: '2021.1',
  ),
  Subst(
    path: 'src/io/flutter/FlutterUtils.java',
    initial: 'ProjectUtil.isSameProject(Paths.get(path), project)',
    replacement: 'ProjectUtil.isSameProject(path, project)',
    versions: ['4.1'],
  ),
  Subst(
    path: 'src/io/flutter/perf/EditorPerfDecorations.java',
    initial: 'highlighter.getTextAttributes(null)',
    replacement: 'highlighter.getTextAttributes()',
    versions: ['4.1'],
  ),
  MultiSubst(
    path: 'src/io/flutter/sdk/FlutterSdk.java',
    initials: [
      'branch = git4idea.light.LightGitUtilKt.getLocation(dir, GitExecutableManager.getInstance().getExecutable((Project)null));',
      'catch (VcsException e)'
    ],
    replacements: [
      // This means we don't show platforms in IJ 2020.1, until stable channel supports them.
      'return FlutterSdkChannel.fromText("stable");',
      'catch (RuntimeException e)'
    ],
    versions: ['4.1'],
  ),
  MultiSubst(
    path: 'src/io/flutter/editor/FlutterIconLineMarkerProvider.java',
    initials: [
      'import com.intellij.codeInsight.daemon.GutterName;',
      '@GutterName'
    ],
    replacements: ['', ''],
    versions: ['4.1'],
  ),
  Subst(
    path: 'src/io/flutter/preview/PreviewView.java',
    initial:
        'Arrays.asList(expandAllAction, collapseAllAction, showOnlyWidgetsAction)',
    replacement: 'expandAllAction, collapseAllAction, showOnlyWidgetsAction',
    versions: ['4.1'],
  ),
  Subst(
    path: 'src/io/flutter/FlutterErrorReportSubmitter.java',
    initial: 'Consumer<? super SubmittedReportInfo> consumer',
    replacement: 'Consumer<SubmittedReportInfo> consumer',
    versions: ['4.1', '4.2'],
  ),
  Subst(
    path: 'src/io/flutter/utils/CollectionUtils.java',
    initial: 'Predicate<T> predicate',
    replacement: 'Predicate<? super T> predicate',
    version: 'AF.3.1',
  ),
  Subst(
    path: 'resources/META-INF/studio-contribs.xml',
    initial: 'JavaNewProjectOrModuleGroup',
    replacement: 'NewProjectOrModuleGroup',
    version: 'AF.3.1',
  ),
  MultiSubst(
    path: 'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java',
    initials: [
      'import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;',
      'ProjectSystemBuildManager',
      'gradleProjectSystem.getBuildManager()',
    ],
    replacements: [
      '',
      'Object',
      'null',
    ],
    versions: ['4.1', '4.2'],
  ),
  Subst(
    path: 'flutter-studio/src/io/flutter/project/FlutterProjectCreator.java',
    initial: 'cannotWriteToFiles(List<Path> files)',
    replacement: 'cannotWriteToFiles(List<? extends File> files)',
    versions: ['4.1', '4.2'],
  ),
  Subst(
    path: 'src/io/flutter/sdk/FlutterSdkUtil.java',
    initial: 'JsonParser.parseString(contents)',
    replacement: 'new JsonParser().parse(contents)',
    version: 'AF.3.1',
  ),
  Subst(
    path: 'src/io/flutter/jxbrowser/JxBrowserManager.java',
    initial: 'loadClasses(fileNames)',
    replacement: 'loadClasses2021(fileNames)',
    versions: ['2021.1'],
  ),
  Subst(
    path: 'src/io/flutter/utils/FileUtils.java',
    initial: '//urlClassLoader.addFiles(paths)',
    replacement: 'urlClassLoader.addFiles(paths)',
    versions: ['2021.1'],
  ),
];

// Used to test checkAndClearAppliedEditCommands()
class Unused extends EditCommand {
  @override
  String get path => 'unused';

  @override
  String convert(BuildSpec spec) {
    return null;
  }
}

class EditAndroidModuleLibraryManager extends EditCommand {
  @override
  String get path =>
      'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java';

  @override
  String convert(BuildSpec spec) {
    // Starting with 3.6 we need to call a simplified init().
    // This is where the $PROJECT_FILE$ macro is defined, #registerComponents.
    if (spec.version.startsWith("4.1") || spec.version.startsWith('4.2')) {
      var processedFile, source;
      processedFile = File(
          'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java');
      source = processedFile.readAsStringSync();
      var original = source;
      source = source.replaceAll("ProjectExImpl", "ProjectImpl");
      source = source.replaceAll(
          "super.init(false, indicator);", "super.init(indicator);");
      source = source.replaceAll(
          "import com.intellij.openapi.project.impl.ProjectExImpl;", "");
      if (spec.version.startsWith("4.1")) {
        source = source.replaceAll(
            "import com.intellij.openapi.components.impl.stores.IProjectStore;",
            "");
        source = source.replaceAll("IProjectStore", "Object");
        source = source.replaceAll(
            "androidProject.init42", "androidProject.initPre41");
        source = source.replaceAll("PluginManagerCore.getLoadedPlugins(), null",
            "PluginManagerCore.getLoadedPlugins(), false");
      }
      if (spec.version.startsWith('4.2')) {
        source = source.replaceAll(
            "androidProject.init42", "androidProject.init41");
        source = source.replaceAll("PluginManagerCore.getLoadedPlugins(), null",
            "PluginManagerCore.getLoadedPlugins()");
        source = source.replaceAll("getStateStore1", "getStateStore");
        source = source.replaceAll("getEarlyDisposable1", "getEarlyDisposable");
        source = source.replaceAll("getWorkspaceFile1", "getWorkspaceFile");
        source = source.replaceAll("getProjectFilePath1", "getProjectFilePath");
        source = source.replaceAll("getProjectFile1", "getProjectFile");
        source = source.replaceAll("getBasePath1", "getBasePath");
        source = source.replaceAll("getBaseDir1", "getBaseDir");
        source = source.replaceAll("super(filePath, TEMPLATE_PROJECT_NAME)",
            "super((ProjectImpl)getProject())");
      }
      processedFile.writeAsStringSync(source);
      return original;
    } else {
      return null;
    }
  }
}

/// Apply all the editCommands applicable to a given BuildSpec.
Future<int> applyEdits(BuildSpec spec, Function compileFn) async {
  // Handle skipped files.
  for (String file in spec.filesToSkip) {
    var entity =
        FileSystemEntity.isFileSync(file) ? File(file) : Directory(file);
    if (entity.existsSync()) {
      await entity.rename('$file~');
      log('renamed $file');
      if (entity is File) {
        var stubFile = File('${file}_stub');
        if (stubFile.existsSync()) {
          await stubFile.copy('$file');
          log('copied ${file}_stub');
        }
      }
    }
  }

  var edited = <EditCommand, String>{};
  try {
    editCommands.forEach((edit) {
      var source = edit.convert(spec);
      if (source != null) {
        edited[edit] = source;
        appliedEditCommands.add(edit);
      }
    });

    return await compileFn.call();
  } finally {
    // Restore sources.
    edited.forEach((edit, source) {
      log('Restoring ${edit.path}');
      edit.restore(source);
    });

    // Restore skipped files.
    for (var file in spec.filesToSkip) {
      var name = '$file~';
      var entity =
          FileSystemEntity.isFileSync(name) ? File(name) : Directory(name);
      if (entity.existsSync()) {
        await entity.rename(file);
      }
    }
  }
}

/// Make some changes to a source file prior to compiling for a specific IDE.
abstract class EditCommand {
  String convert(BuildSpec spec);

  void restore(String source) {
    var processedFile = File(path);
    processedFile.writeAsStringSync(source);
  }

  String get path;
}

/// Single substitution in a file for one or more IDE versions.
class Subst extends EditCommand {
  @override
  String path;
  String initial;
  String replacement;
  List<String> versions;

  Subst({this.versions, this.initial, this.replacement, this.path, version})
      : assert(initial != null),
        assert(replacement != null),
        assert(path != null) {
    if (version != null) {
      // Disallow both version and versions keywords.
      assert(versions == null);
      versions = [version];
    }
  }

  @override
  String convert(BuildSpec spec) {
    if (versionMatches(spec)) {
      var processedFile = File(path);
      var source = processedFile.readAsStringSync();
      var original = source;
      source = source.replaceAll(initial, replacement);
      processedFile.writeAsStringSync(source);
      log ('edited $path');
      return original;
    } else {
      return null;
    }
  }

  String toString() => "Subst(path: $path, versions: $versions)";

  bool versionMatches(BuildSpec spec) {
    return versions.any((v) => spec.version.startsWith(v));
  }
}

// TODO Unify Subst and MultiSubst, and find a better name.
class MultiSubst extends EditCommand {
  @override
  String path;
  List<String> initials;
  List<String> replacements;
  List<String> versions;

  MultiSubst({
    this.versions,
    this.initials,
    this.replacements,
    this.path,
  }) {
    assert(initials.length == replacements.length);
    assert(path != null);
    assert(versions.isNotEmpty);
    assert(initials.isNotEmpty);
  }

  @override
  String convert(BuildSpec spec) {
    if (versionMatches(spec)) {
      var processedFile = File(path);
      var source = processedFile.readAsStringSync();
      var original = source;
      for (var i = 0; i < initials.length; i++) {
        source = source.replaceAll(initials[i], replacements[i]);
      }
      processedFile.writeAsStringSync(source);
      return original;
    } else {
      return null;
    }
  }

  String toString() => "Subst(path: $path, versions: $versions)";

  bool versionMatches(BuildSpec spec) {
    return versions.any((v) => spec.version.startsWith(v));
  }
}
