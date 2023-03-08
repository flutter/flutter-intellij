/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

// @dart = 2.12

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
    for (var cmd in commands) {
      log(cmd.toString());
    }
  }
  appliedEditCommands.clear();
}

List<EditCommand> editCommands = [
  // When using LATEST-EAP-SNAPSHOT, also set baseVersion to LATEST-EAP-SNAPSHOT in the build spec.
  Subst(
    path: 'build.gradle.kts',
    initial:
        'localPath.set("\${project.rootDir.absolutePath}/artifacts/\$ide")',
    replacement: 'type.set("IC")\n  version.set("LATEST-EAP-SNAPSHOT")',
    version: '2023.2',
  ),
  Subst(
    path: 'flutter-idea/build.gradle.kts',
    initial:
        'localPath.set("\${project.rootDir.absolutePath}/artifacts/\$ide")',
    replacement: 'type.set("IC")\n  version.set("LATEST-EAP-SNAPSHOT")',
    version: '2023.2',
  ),
  MultiSubst(
    path: 'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java',
    initials: [
      '//import ',
      'extends ProjectImpl '
    ],
    replacements: [
      'import ',
      'extends ProjectExImpl '
    ],
    versions: ['2022.1', '2022.2', '2031.1'],
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/vmService/frame/DartVmServiceValue.java',
    initial: '@NotNull',
    replacement: '',
    versions: ['2022.1', '2022.2'],
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/analytics/FlutterAnalysisServerListener.java',
    initial: '@NotNull',
    replacement: '',
    versions: ['2022.1', '2022.2'],
  ),
  Subst(
    path: 'resources/META-INF/plugin_template.xml',
    initial: '<add-to-group group-id="MainToolbarRight" />',
    replacement: '',
    versions: ['2022.1', '2022.2'],
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/actions/AttachDebuggerAction.java',
    initial: '''
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
''',
    replacement: '',
    version: '2022.1',
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/actions/DeviceSelectorAction.java',
    initial: '''
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
''',
    replacement: '',
    version: '2022.1',
  ),
];

// Used to test checkAndClearAppliedEditCommands()
class Unused extends EditCommand {
  @override
  String get path => 'unused';

  @override
  String? convert(BuildSpec spec) {
    return null;
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
  String? convert(BuildSpec spec);

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
      {this.versions = const [''],
      required this.initial,
      required this.replacement,
      required this.path,
      String? version}) {
    if (version != null) {
      // Disallow both version and versions keywords.
      versions = [version];
    }
  }

  @override
  String? convert(BuildSpec spec) {
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
    required this.versions,
    required this.initials,
    required this.replacements,
    required this.path,
  }) {
    assert(initials.length == replacements.length);
    assert(versions.isNotEmpty);
    assert(initials.isNotEmpty);
  }

  @override
  String? convert(BuildSpec spec) {
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
