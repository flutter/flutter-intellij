/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

// @dart = 2.10

import 'dart:async';
import 'dart:io';

import 'package:meta/meta.dart';
import 'package:path/path.dart' as p;

import 'build_spec.dart';
import 'util.dart';

Set<EditCommand> appliedEditCommands = {};

void checkAndClearAppliedEditCommands() {
  if (appliedEditCommands.length != editCommands.length) {
    var commands = <EditCommand>{};
    commands.addAll(editCommands);
    commands.removeAll(appliedEditCommands);
    separator("UNUSED EditCommand");
    for (var cmd in commands) {
      log(cmd.toString());
    }
  }
  appliedEditCommands.clear();
}

// TODO(messick): Remove edits for AS 4.2 when confident they won't be needed again.
List<EditCommand> editCommands = [
  EditAndroidModuleLibraryManager(),
  Subst(
    path: 'build.gradle.kts',
    initial:
        'localPath.set("\${project.rootDir.absolutePath}/artifacts/\$ide")',
    replacement: 'type.set("IC")\n  version.set("LATEST-EAP-SNAPSHOT")',
    version: '2022.1',
  ),
  Subst(
    path: 'flutter-idea/build.gradle.kts',
    initial:
        'localPath.set("\${project.rootDir.absolutePath}/artifacts/\$ide")',
    replacement: 'type.set("IC")\n  version.set("LATEST-EAP-SNAPSHOT")',
    version: '2022.1',
  ),
  Subst(
    path: 'flutter-idea/testSrc/unit/io/flutter/testing/Testing.java',
    initial: '.createLightFixtureBuilder()',
    replacement: '.createLightFixtureBuilder("test")',
    version: '2022.1',
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/utils/CollectionUtils.java',
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
  Subst(
    path: 'flutter-idea/src/io/flutter/sdk/FlutterSdkUtil.java',
    initial: 'JsonParser.parseString(contents)',
    replacement: 'new JsonParser().parse(contents)',
    version: 'AF.3.1',
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/jxbrowser/JxBrowserManager.java',
    initial: 'loadClasses(fileNames)',
    replacement: 'loadClasses2020(fileNames)',
    versions: ['AF.3.1'],
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/utils/FileUtils.java',
    initial: 'urlClassLoader.addFiles(paths)',
    replacement: '//urlClassLoader.addFiles(paths)',
    versions: ['AF.3.1'],
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
  final _moduleManagerPath = p.join('flutter-studio', 'src', 'io', 'flutter',
      'android', 'AndroidModuleLibraryManager.java');

  @override
  String get path => _moduleManagerPath;

  @override
  String convert(BuildSpec spec) {
    // Starting with 3.6 we need to call a simplified init().
    // This is where the $PROJECT_FILE$ macro is defined, #registerComponents.

    var libMgrPath = _moduleManagerPath;
    if (spec.version.startsWith('4.2')) {
      var processedFile = File(libMgrPath);
      var source = processedFile.readAsStringSync();
      var original = source;
      source = source.replaceAll("ProjectExImpl", "ProjectImpl");
      source = source.replaceAll(
          "super.init(false, indicator);", "super.init(indicator);");
      source = source.replaceAll(
          "import com.intellij.openapi.project.impl.ProjectExImpl;", "");
      if (spec.version.startsWith('4.2')) {
        source =
            source.replaceAll("androidProject.init42", "androidProject.init41");
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
      if (spec.version.startsWith('2021.2')) {
        var processedFile = File(libMgrPath);
        var source = processedFile.readAsStringSync();
        var original = source;
        source = source.replaceAll(
            "PluginManagerCore.getLoadedPlugins(), null);",
            "PluginManagerCore.getLoadedPlugins(), null, null, null);");
        processedFile.writeAsStringSync(source);
        return original;
      } else {
        return null;
      }
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
          await stubFile.copy(file);
          log('copied ${file}_stub');
        }
      }
    }
  }

  var edited = <EditCommand, String>{};
  try {
    for (var edit in editCommands) {
      var source = edit.convert(spec);
      if (source != null) {
        edited[edit] = source;
        appliedEditCommands.add(edit);
      }
    }

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

  /// The list of platform versions to perform the substitution for.
  List<String> versions;

  Subst(
      {this.versions,
      @required this.initial,
      @required this.replacement,
      @required this.path,
      String version})
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
      log('edited $path');
      return original;
    } else {
      return null;
    }
  }

  @override
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
    @required this.path,
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

  @override
  String toString() => "Subst(path: $path, versions: $versions)";

  bool versionMatches(BuildSpec spec) {
    return versions.any((v) => spec.version.startsWith(v));
  }
}
