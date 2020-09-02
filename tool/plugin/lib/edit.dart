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
  EditFlutterProjectSystem(),
  EditFlutterDescriptionProvider(),
  Subst(
    path: 'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java',
    initial:
        'gradleProjectSystem.getAndroidFacetsWithPackageName(project, packageName, scope)',
    replacement: 'Collections.emptyList()',
    version: '4.0',
  ),
  Subst(
    path:
        'flutter-studio/src/io/flutter/actions/FlutterShowStructureSettingsAction.java',
    initial:
        'import com.android.tools.idea.gradle.structure.actions.AndroidShowStructureSettingsAction;',
    replacement:
        'import com.android.tools.idea.gradle.actions.AndroidShowStructureSettingsAction;',
    versions: ['3.6', '4.0', '2020.2'],
  ),
  MultiSubst(
    path: 'flutter-studio/src/io/flutter/actions/OpenAndroidModule.java',
    initials: [
      'findGradleTarget',
      'importAndOpenProjectCore(null, true, projectFile)',
    ],
    replacements: [
      'findImportTarget',
      'importProjectCore(projectFile)',
    ],
    versions: ['3.6', '4.0', '2020.2'],
  ),
  Subst(
    path: 'flutter-studio/src/io/flutter/utils/AddToAppUtils.java',
    initial: '.project.importing.GradleProjectImporter',
    replacement: '.project.importing.NewProjectSetup',
    versions: ['3.6', '4.0', '2020.2'],
  ),
  Subst(
    path: 'src/io/flutter/utils/AndroidUtils.java',
    initial:
        'import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;',
    replacement:
        'import com.android.tools.idea.gradle.dsl.model.BuildModelContext;',
    versions: ['4.1', '4.2'],
  ),
  Subst(
    path:
        'flutter-studio/src/io/flutter/assistant/whatsnew/FlutterNewsBundleCreator.java',
    initial: """
    PluginManager pluginManager = PluginManager.getInstance();
    IdeaPluginDescriptor descriptor = pluginManager.findEnabledPlugin(PluginId.getId("io.flutter"));
""",
    replacement: """
    IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId("io.flutter"));
""",
    versions: ['3.6', '4.0'],
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
    if (spec.version.startsWith('3.6')) {
      var processedFile, source;
      processedFile = File(
          'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java');
      source = processedFile.readAsStringSync();
      var original = source;
      source = source.replaceAll(
        'super(filePath',
        'super(filePath.toString()',
      );
      // Starting with 3.6 we need to call a simplified init().
      // This is where the $PROJECT_FILE$ macro is defined, #registerComponents.
      source = source.replaceAll(
        'getStateStore().setPath(path',
        'getStateStore().setPath(path.toString()',
      );
      source = source.replaceAll(
          "androidProject.init41", "androidProject.initPre41");
      source = source.replaceAll("ProjectExImpl", "ProjectImpl");
      source = source.replaceAll(
        "import com.intellij.openapi.project.impl.ProjectExImpl;", ""
      );
      processedFile.writeAsStringSync(source);
      return original;
    } else if (spec.version.startsWith("4.0") ||
        spec.version.startsWith("4.1")) {
      var processedFile, source;
      processedFile = File(
          'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java');
      source = processedFile.readAsStringSync();
      var original = source;
      source = source.replaceAll(
          "androidProject.init41", "androidProject.initPre41");
      source = source.replaceAll("ProjectExImpl", "ProjectImpl");
      source = source.replaceAll(
          "import com.intellij.openapi.project.impl.ProjectExImpl;", ""
      );
      processedFile.writeAsStringSync(source);
      return original;
    } else {
      return null;
    }
  }
}

class EditFlutterProjectSystem extends EditCommand {
  @override
  String get path =>
      'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java';

  @override
  String convert(BuildSpec spec) {
    if (!spec.version.startsWith('4.')) {
      var processedFile, source;
      processedFile = File(
          'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java');
      source = processedFile.readAsStringSync();
      var original = source;
      source = source.replaceAll(
        'import com.android.tools.idea.projectsystem.SourceProvidersFactory;',
        '',
      );
      source = source.replaceAll(' SourceProvidersFactory ', ' Object ');
      source = source.replaceAll(
        'gradleProjectSystem.getSourceProvidersFactory()',
        'new Object()',
      );
      source = source.replaceAll(
        'gradleProjectSystem.getAndroidFacetsWithPackageName(project, packageName, scope)',
        'Collections.emptyList()',
      );
      if (spec.version.startsWith('3.6')) {
        source = source.replaceAll(
          'gradleProjectSystem.getSubmodules()',
          'new java.util.ArrayList()',
        );
      }
      processedFile.writeAsStringSync(source);
      return original;
    } else {
      return null;
    }
  }
}

class EditFlutterDescriptionProvider extends EditCommand {
  final String CODE_TO_DELETE = """
    abstract public SkippableWizardStep createStep(@NotNull Project model, @NotNull ProjectSyncInvoker invoker, String parent);

    @SuppressWarnings("override")
    public SkippableWizardStep createStep(@NotNull Project model, String parent, @NotNull ProjectSyncInvoker invoker) {
      // 4.2 canary 2 swapped the order of two args.
      // This whole framework is planned to be deleted at some future date, so we need to revise our templates
      // to work with the new extendible model.
      return createStep(model, invoker, parent);
    }
""";

  @override
  String get path =>
      'flutter-studio/src/io/flutter/module/FlutterDescriptionProvider.java';

  @override
  String convert(BuildSpec spec) {
    if (!spec.version.startsWith('4.')) {
      var processedFile, source;
      processedFile = File(
          'flutter-studio/src/io/flutter/module/FlutterDescriptionProvider.java');
      source = processedFile.readAsStringSync();
      var original = source;
      source = source.replaceAll(
        'import com.android.tools.idea.npw.model.ProjectSyncInvoker',
        'import com.android.tools.idea.npw.model.NewModuleModel',
      );
      source = source.replaceAll(CODE_TO_DELETE, '');
      source = source.replaceAll(
        'createStep(@NotNull Project model, @NotNull ProjectSyncInvoker invoker, String parent)',
        'createStep(@NotNull NewModuleModel model)',
      );
      source = source.replaceAll(
        'FlutterProjectModel model(@NotNull Project project,',
        'FlutterProjectModel model(@NotNull NewModuleModel project,',
      );
      source = source.replaceAll(
        'mySharedModel.getValue().project().setValue(project);',
        'mySharedModel.getValue().project().setValue(project.getProject().getValue());',
      );
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
      if (entity is File) {
        var stubFile = File('${file}_stub');
        if (stubFile.existsSync()) {
          await stubFile.copy('$file');
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
