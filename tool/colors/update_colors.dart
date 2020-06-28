// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as path;

import 'flutter/colors_cupertino.dart';
import 'flutter/colors_material.dart';
import 'generated/colors_cupertino.dart' as cupertino;
import 'generated/colors_material.dart' as material;
import 'stubs.dart';

void main(List<String> args) async {
  // Verify that we're running from the project root.
  if (path.basename(Directory.current.path) != 'flutter-intellij') {
    print('Please run this script from the directory root.');
    exit(1);
  }

  // TODO(dantup): Split into two scripts instead of using a flag? It's to allow
  // easier debugging of the second step.
  if (args.contains('--generate-properties')) {
    print('Generating property files:');
    generatePropertiesFiles();

    exit(0);
  } else {
    print('Generating dart files:');
    await generateDartFiles();

    // Re-run the script with `--generate-properties` so it can load the files
    // we generated.
    final results = Process.runSync(Platform.resolvedExecutable,
        [Platform.script.toFilePath(), '--generate-propertes']);
    stdout.write(results.stdout);
    stderr.write(results.stderr);

    exit(0);
  }
}

Future generateDartFiles() async {
  // TODO: Use the files from the local flutter checkout instead of a download
  // here.

  // download material/colors.dart and cupertino/colors.dart
  String materialData =
      await downloadUrl('https://raw.githubusercontent.com/flutter/flutter/'
          'master/packages/flutter/lib/src/material/colors.dart');
  String cupertinoData =
      await downloadUrl('https://raw.githubusercontent.com/flutter/flutter/'
          'master/packages/flutter/lib/src/cupertino/colors.dart');

  // parse into metadata
  List<String> materialColors = extractColorNames(materialData);
  List<String> cupertinoColors = extractColorNames(cupertinoData);

  // generate .properties files
  generateDart(materialColors, 'colors_material.dart', 'Colors');
  generateDart(cupertinoColors, 'colors_cupertino.dart', 'CupertinoColors');
}

void generatePropertiesFiles() {
  final output = 'resources/flutter/';
  generateProperties(material.colors, '$output/colors.properties');
  generateProperties(cupertino.colors, '$output/cupertino_colors.properties');
}

Future<String> downloadUrl(String url) async {
  HttpClient client = new HttpClient();
  try {
    HttpClientRequest request = await client.getUrl(Uri.parse(url));
    HttpClientResponse response = await request.close();
    List<String> data = await utf8.decoder.bind(response).toList();
    return data.join('');
  } finally {
    client.close();
  }
}

// The pattern below is meant to match lines like:
//   'static const Color black45 = Color(0x73000000);'
//   'static const MaterialColor cyan = MaterialColor('
final RegExp regexpColor1 =
    new RegExp(r'static const \w*Color (\S+) = \w*Color\(');
// The pattern below is meant to match lines like:
//   'static const CupertinoDynamicColor activeBlue = systemBlue;'
final RegExp regexpColor2 = new RegExp(r'static const \w*Color (\S+) = \w+;');
// The pattern below is meant to match lines like:
//   'static const CupertinoDynamicColor systemGreen = CupertinoDynamicColor.withBrightnessAndContrast('
final RegExp regexpColor3 =
    new RegExp(r'static const \w*Color (\S+) = \w+.\w+\(');

List<String> extractColorNames(String data) {
  List<String> names = [
    ...regexpColor1.allMatches(data).map((Match match) => match.group(1)),
    ...regexpColor2.allMatches(data).map((Match match) => match.group(1)),
    ...regexpColor3.allMatches(data).map((Match match) => match.group(1)),
  ];

  // Remove any duplicates.
  return Set<String>.from(names).toList();
}

void generateDart(List<String> colors, String filename, String className) {
  StringBuffer buf = StringBuffer();
  buf.writeln('''
// Generated file - do not edit.

import '../stubs.dart';
import '../flutter/${filename}';

final Map<String, Color> colors = <String, Color>{''');

  for (String colorName in colors) {
    buf.writeln("  '${colorName}': ${className}.${colorName},");
  }

  buf.writeln('};');

  File out = new File('tool/colors/generated/$filename');
  out.writeAsStringSync(buf.toString());

  print('wrote ${out.path}');
}

void generateProperties(Map<String, Color> colors, String filename) {
  const validShades = [
    50,
    100,
    200,
    300,
    350,
    400,
    500,
    600,
    700,
    800,
    850,
    900
  ];
  StringBuffer buf = StringBuffer();
  buf.writeln('# Generated file - do not edit.');
  buf.writeln();
  buf.writeln('# suppress inspection "UnusedProperty" for whole file');

  for (String name in colors.keys) {
    Color color = colors[name];
    if (color is MaterialColor) {
      buf.writeln('$name.primary=${color}');
      for (var shade in validShades) {
        if (color[shade] != null) {
          buf.writeln('$name[$shade]=${color[shade]}');
        }
      }
    } else if (color is MaterialAccentColor) {
      buf.writeln('$name.primary=${color}');
      for (var shade in validShades) {
        if (color[shade] != null) {
          buf.writeln('$name[$shade]=${color[shade]}');
        }
      }
    } else if (color is CupertinoDynamicColor) {
      buf.writeln('$name=${color.color}');
      buf.writeln('$name.darkColor=${color.darkColor}');
      buf.writeln('$name.darkElevatedColor=${color.darkElevatedColor}');
      buf.writeln('$name.darkHighContrastColor=${color.darkHighContrastColor}');
      buf.writeln(
          '$name.darkHighContrastElevatedColor=${color.darkHighContrastElevatedColor}');
      buf.writeln('$name.elevatedColor=${color.elevatedColor}');
      buf.writeln('$name.highContrastColor=${color.highContrastColor}');
      buf.writeln(
          '$name.highContrastElevatedColor=${color.highContrastElevatedColor}');
    } else {
      buf.writeln('$name=$color');
    }
  }

  new File(filename).writeAsStringSync(buf.toString());

  print('wrote $filename');
}
