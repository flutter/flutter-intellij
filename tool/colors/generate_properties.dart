// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

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

  print('Generating property files:');
  generatePropertiesFiles();
}

void generatePropertiesFiles() {
  final output = 'resources/flutter/';
  generateProperties(material.colors, '$output/colors.properties');
  generateProperties(cupertino.colors, '$output/cupertino_colors.properties');
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
