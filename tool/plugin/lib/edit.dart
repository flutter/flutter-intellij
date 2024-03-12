/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import 'dart:async';
import 'dart:io';

import 'build_spec.dart';
import 'util.dart';

Set<EditCommand> appliedEditCommands = {};

void checkAndClearAppliedEditCommands() {
  if (appliedEditCommands.length != editCommands.length) {
    final commands = <EditCommand>{};
    commands.addAll(editCommands);
    commands.removeAll(appliedEditCommands);
    separator("UNUSED EditCommand");
    for (final cmd in commands) {
      log(cmd.toString());
    }
  }
  appliedEditCommands.clear();
}

List<EditCommand> editCommands = [
  EditCommand(
    path: 'flutter-idea/src/io/flutter/FlutterUtils.java',
    initials: [
      '''
final Yaml yaml = new Yaml(new SafeConstructor(), new Representer(), new DumperOptions(), new Resolver() {
'''
    ],
    replacements: [
      '''
final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(new DumperOptions()), new DumperOptions(), new Resolver() {
'''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path:
        'flutter-idea/src/org/jetbrains/android/facet/AndroidFrameworkDetector.java',
    initials: [
      '''
import com.android.tools.idea.gradle.project.GradleProjectInfo;
'''
    ],
    replacements: [
      '''
import com.android.tools.idea.gradle.project.Info;
'''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path:
        'flutter-idea/src/org/jetbrains/android/facet/AndroidFrameworkDetector.java',
    initials: [
      '''
GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(project);
'''
    ],
    replacements: [
      '''
Info gradleProjectInfo = Info.getInstance(project);
'''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path: 'flutter-studio/src/io/flutter/utils/AndroidLocationProvider.java',
    initials: [
      '''
import com.android.tools.idea.gradle.util.GradleUtil;
'''
    ],
    replacements: [
      '''
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
'''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path: 'flutter-studio/src/io/flutter/utils/AndroidLocationProvider.java',
    initials: [
      '''
GradleModuleModel moduleModel = GradleUtil.getGradleModuleModel(module);
'''
    ],
    replacements: [
      '''
GradleModuleModel moduleModel = GradleProjectSystemUtil.getGradleModuleModel(module);
'''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path:
        'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java',
    initials: [
      '''
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
'''
    ],
    replacements: [
      '''
import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;
'''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path:
        'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java',
    initials: [
      '''
import com.intellij.openapi.vfs.VirtualFileManager;
'''
    ],
    replacements: [
      '''
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.serviceContainer.ComponentManagerImpl;
 '''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path:
        'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java',
    initials: [
      '''
super(filePath, TEMPLATE_PROJECT_NAME);
'''
    ],
    replacements: [
      '''
super((ComponentManagerImpl) ApplicationManager.getApplication(), filePath, TEMPLATE_PROJECT_NAME);
 '''
    ],
    versions: ['2023.2'],
  ),
  EditCommand(
    path: 'flutter-studio/src/io/flutter/utils/GradleUtils.java',
    initials: [
      '''
import com.android.tools.idea.gradle.util.GradleUtil;
'''
    ],
    replacements: [
      '''
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
'''
    ],
    versions: ['2023.2'],
  ),

  EditCommand(
    path: 'flutter-studio/src/io/flutter/utils/GradleUtils.java',
    initials: [
      '''
buildFile = GradleUtil.getGradleBuildFile(flutterModuleRoot);
'''
    ],
    replacements: [
      '''
buildFile = GradleProjectSystemUtil.getGradleBuildFile(flutterModuleRoot);
'''
    ],
    versions: ['2023.2'],
  ),
  // When using LATEST-EAP-SNAPSHOT, also set baseVersion to LATEST-EAP-SNAPSHOT in the build spec.
  // EditCommand(
  //   path: 'build.gradle.kts',
  //   initials: ['version.set(ideVersion)'],
  //   replacements: ['version.set("LATEST-EAP-SNAPSHOT")'],
  //   versions: ['2023.3'],
  // ),
  // EditCommand(
  //   path: 'flutter-idea/build.gradle.kts',
  //   initials: ['version.set(ideVersion)'],
  //   replacements: ['version.set("LATEST-EAP-SNAPSHOT")'],
  //   versions: ['2023.3'],
  // ),
];

/// Apply all the editCommands applicable to a given BuildSpec.
Future<int> applyEdits(BuildSpec spec, Function compileFn) async {
  // Handle skipped files.
  for (String file in spec.filesToSkip) {
    final entity =
        FileSystemEntity.isFileSync(file) ? File(file) : Directory(file);
    if (entity.existsSync()) {
      await entity.rename('$file~');
      log('renamed $file');
      if (entity is File) {
        final stubFile = File('${file}_stub');
        if (stubFile.existsSync()) {
          await stubFile.copy(file);
          log('copied ${file}_stub');
        }
      }
    }
  }

  final edited = <EditCommand, String>{};
  try {
    for (final edit in editCommands) {
      final source = edit.convert(spec);
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
    for (final file in spec.filesToSkip) {
      final name = '$file~';
      final entity =
          FileSystemEntity.isFileSync(name) ? File(name) : Directory(name);
      if (entity.existsSync()) {
        await entity.rename(file);
      }
    }
  }
}

/// Make some changes to a source file prior to compiling for a specific IDE.
class EditCommand {
  EditCommand({
    required this.path,
    required List<String> initials,
    required List<String> replacements,
    this.versions = const [],
  })  : assert(initials.length == replacements.length),
        assert(initials.isNotEmpty),
        assert(versions.isNotEmpty),
        initials = initials.map(_platformAdaptiveString).toList(),
        replacements = replacements.map(_platformAdaptiveString).toList();

  /// The target file path.
  final String path;

  final List<String> initials;
  final List<String> replacements;

  /// The list of platform versions to perform the substitution for.
  final List<String> versions;

  String? convert(BuildSpec spec) {
    if (versionMatches(spec)) {
      final processedFile = File(path);
      String source = processedFile.readAsStringSync();
      final original = source;
      for (int i = 0; i < initials.length; i++) {
        source = source.replaceAll(initials[i], replacements[i]);
      }
      processedFile.writeAsStringSync(source);
      return original;
    } else {
      return null;
    }
  }

  bool versionMatches(BuildSpec spec) {
    return versions.any((v) => spec.version.startsWith(v));
  }

  void restore(String source) {
    final processedFile = File(path);
    processedFile.writeAsStringSync(source);
  }

  @override
  String toString() => "EditCommand(path: $path, versions: $versions)";
}

String _platformAdaptiveString(String value) {
  if (value.isEmpty) {
    return value;
  } else if (Platform.isWindows) {
    return value.replaceAll('\n', '\r\n');
  } else {
    return value.replaceAll('\r\n', '\n');
  }
}
