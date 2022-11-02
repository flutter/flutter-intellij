/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

// @dart = 2.10

import 'dart:async';
import 'dart:io';

import 'package:meta/meta.dart';

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
    version: '2022.4',
  ),
  Subst(
    path: 'flutter-idea/build.gradle.kts',
    initial:
        'localPath.set("\${project.rootDir.absolutePath}/artifacts/\$ide")',
    replacement: 'type.set("IC")\n  version.set("LATEST-EAP-SNAPSHOT")',
    version: '2022.4',
  ),
  Subst(
    path: 'flutter-idea/src/io/flutter/pub/PubRoot.java',
    initial: 'String @NotNull [] TEST_DIRS',
    replacement: 'String [] TEST_DIRS',
    versions: ['AS.211', 'AS.212'],
  ),
  Subst(
    path:
        'flutter-idea/src/io/flutter/analytics/FlutterAnalysisServerListener.java',
    initial: '<@NotNull Analytics>',
    replacement: '<Analytics>',
    versions: ['AS.211', 'AS.212', 'AS.213'],
  ),
  MultiSubst(
    path: 'flutter-idea/src/io/flutter/actions/DeviceSelectorAction.java',
    initials: [
      'import com.intellij.util.ModalityUiUtil;',
      '''
    ModalityUiUtil.invokeLaterIfNeeded(
      ModalityState.defaultModalityState(),
      () -> update(project, presentation));
''',
    ],
    replacements: [
      'import com.intellij.ui.GuiUtils;',
      '''
    GuiUtils.invokeLaterIfNeeded(
      () -> update(project, presentation),
      ModalityState.defaultModalityState());
''',
    ],
    versions: ['AS.211', 'AS.212', 'AS.213'],
  ),
  MultiSubst(
    path:
        'flutter-idea/src/io/flutter/run/coverage/FlutterCoverageEnabledConfiguration.java',
    initials: [
      'import com.intellij.util.ModalityUiUtil;',
      '''
    ModalityUiUtil.invokeLaterIfNeeded(
      ModalityState.any(),
      () -> setCurrentCoverageSuite(CoverageDataManager.getInstance(configuration.getProject()).addCoverageSuite(this)));
''',
    ],
    replacements: [
      'import com.intellij.ui.GuiUtils;',
      '''
    GuiUtils.invokeLaterIfNeeded(
      () -> setCurrentCoverageSuite(CoverageDataManager.getInstance(configuration.getProject()).addCoverageSuite(this)),
      ModalityState.any());
''',
    ],
    versions: ['AS.211', 'AS.212', 'AS.213'],
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
