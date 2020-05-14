/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;

import 'build_spec.dart';
import 'globals.dart';

class Edit {
  f(BuildSpec spec) {
    // TODO: Remove this when we no longer support the corresponding version.
    try {
      if (spec.version.startsWith('3.5') || spec.version.startsWith('3.6')) {
        processedFile = File(
            'flutter-studio/src/io/flutter/android/AndroidModuleLibraryManager.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        if (spec.version.startsWith('3.5')) {
          source = source.replaceAll(
            'androidProject.init(null);',
            'androidProject.init();',
          );
        }
        if (spec.version.startsWith('3.5')) {
          // This version does not define the init() method, so remove it.
          var last = 'static final String TEMPLATE_PROJECT_NAME = ';
          var end = source.indexOf(last);
          // Change the initializer to use null, equivalent to original code.
          source = '${source.substring(0, end + last.length)}null;\n}}';
        }
        source = source.replaceAll(
          'super(filePath',
          'super(filePath.toString()',
        );
        if (spec.version.startsWith('3.6')) {
          // Starting with 3.6 we need to call a simplified init().
          // This is where the $PROJECT_FILE$ macro is defined, #registerComponents.
          source = source.replaceAll(
            'getStateStore().setPath(path',
            'getStateStore().setPath(path.toString()',
          );
        }
        processedFile.writeAsStringSync(source);
      }

      if (spec.version.startsWith('3.5')) {
        processedFile =
            File('flutter-studio/src/io/flutter/utils/AddToAppUtils.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'connection.subscribe(DebuggerManagerListener.TOPIC, makeAddToAppAttachListener(project));',
          '',
        );
        processedFile.writeAsStringSync(source);
      }

      if (!spec.version.startsWith('4.')) {
        processedFile = File(
            'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
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
        if (spec.version.startsWith('3.5') ||
            spec.version.startsWith('3.6')) {
          source = source.replaceAll(
            'gradleProjectSystem.getSubmodules()',
            'new java.util.ArrayList()',
          );
        }
        processedFile.writeAsStringSync(source);

        processedFile = File(
            'flutter-studio/src/io/flutter/module/FlutterDescriptionProvider.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'import com.android.tools.idea.npw.model.ProjectSyncInvoker',
          'import com.android.tools.idea.npw.model.NewModuleModel',
        );
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
      } else if (spec.version.startsWith('4.0')) {
        processedFile = File(
            'flutter-studio/src/io/flutter/project/FlutterProjectSystem.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'gradleProjectSystem.getAndroidFacetsWithPackageName(project, packageName, scope)',
          'Collections.emptyList()',
        );
        processedFile.writeAsStringSync(source);
      }
      if (!spec.version.startsWith('4.1')) {
        processedFile = File(
            'flutter-studio/src/io/flutter/actions/FlutterShowStructureSettingsAction.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'import com.android.tools.idea.gradle.structure.actions.AndroidShowStructureSettingsAction;',
          'import com.android.tools.idea.gradle.actions.AndroidShowStructureSettingsAction;',
        );
        processedFile.writeAsStringSync(source);

        processedFile = File(
            'flutter-studio/src/io/flutter/actions/OpenAndroidModule.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'findGradleTarget', // 3 matches
          'findImportTarget',
        );
        processedFile.writeAsStringSync(source);
      } else {
        processedFile = File(
            'src/io/flutter/utils/AndroidUtils.java');
        source = processedFile.readAsStringSync();
        files[processedFile] = source;
        source = source.replaceAll(
          'import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;',
          'import com.android.tools.idea.gradle.dsl.model.BuildModelContext;',
        );
        processedFile.writeAsStringSync(source);
      }
    } catch (ex) {
      // Restore sources.
      files.forEach((file, src) {
        log('Restoring ${file.path}');
        file.writeAsStringSync(src);
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
      throw ex;
    }
  }
}