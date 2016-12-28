// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:io';

import 'package:path/path.dart' as path;

import 'update_icons.dart' show kIdentifierRewrites;

void main() {
  // Check for the material-design-icons directory.
  Directory iconsDir = new Directory('material-design-icons');
  if (!iconsDir.existsSync()) {
    print("Please run 'git clone https://github.com/google/material-design-icons'"
      " from the project directory.");
    exit(1);
  }

  print('Found ${iconsDir.path}.');

  // Parse codepoints.
  List<Icon> icons = [];
  Map<String, Icon> iconMap = {};

  File codepointsFile = new File('material-design-icons/iconfont/codepoints');
  List<String> lines = codepointsFile.readAsLinesSync();
  for (String line in lines) {
    line = line.trim();
    if (line.isNotEmpty) {
      List<String> s = line.split(' ');
      Icon icon = new Icon(s[0], s[1]);
      icons.add(icon);
      iconMap[icon.name] = icon;
    }
  }

  final String prefix = 'ic_';
  final String suffix = '_black_24dp.png';

  // navigation/1x_web/ic_apps_black_24dp.png
  for (FileSystemEntity entity in iconsDir.listSync(recursive: true, followLinks: false)) {
    String p = entity.path;
    if (!p.endsWith(suffix)) continue;
    if (!p.contains('/2x_web/')) continue;

    File blackFile = new File(p.replaceFirst('_black_', '_white_'));
    if (!blackFile.existsSync()) {
      print('  no cooresponding black file: ${p}');
      continue;
    }

    String name = path.basename(p);
    name = name.substring(prefix.length);
    name = name.substring(0, name.length - suffix.length);

    Icon icon = iconMap[name];
    if (icon == null) continue;

    icon.fullPath = p;
    icon.category = path.basename(entity.parent.parent.path);
  }

  for (Icon icon in icons.where((icon) => icon.fullPath == null)) {
    print('  no image found for ${icon.name}');
  }

  print('Parsed ${codepointsFile.path}.');

  // Generate properties file.
  List<Icon> filteredIcons = icons.where((icon) => icon.fullPath != null).toList();

  StringBuffer buf = new StringBuffer();
  buf.writeln('# Generated file - do not edit.');
  buf.writeln();
  buf.writeln('# suppress inspection "UnusedProperty" for whole file');

  for (Icon icon in filteredIcons) {
    buf.writeln();
    buf.writeln('${icon.codepoint}.codepoint=${icon.identifier}');
    buf.writeln('${icon.identifier}=/flutter/icons/${icon.category}/${icon.identifier}.png');
  }

  File propertiesFile = new File('resources/flutter/icons.properties');
  propertiesFile.writeAsStringSync(buf.toString());
  print('Wrote ${propertiesFile.path}.');

  // Copy over icons.
  int count = 0;

  print('Resizing normal 16x16 icons...');

  for (Icon icon in filteredIcons) {
    File dest = new File('resources/flutter/icons/${icon.category}/${icon.identifier}.png');
    File source = new File(icon.fullPath);
    count += _resize(source, dest, 16, black: false);
  }

  print('Resizing normal 32x32 icons...');

  for (Icon icon in filteredIcons) {
    File dest = new File('resources/flutter/icons/${icon.category}/${icon.identifier}@2x.png');
    File source = new File(icon.fullPath);
    count += _resize(source, dest, 32, black: false);
  }

  print('Resizing Darcula 16x16 icons...');

  for (Icon icon in filteredIcons) {
    File dest = new File('resources/flutter/icons/${icon.category}/${icon.identifier}_dark.png');
    File source = new File(icon.fullPath.replaceFirst('_black_', '_white_'));
    count += _resize(source, dest, 16, black: true);
  }

  print('Resizing Darcula 32x32 icons...');

  for (Icon icon in filteredIcons) {
    File dest = new File('resources/flutter/icons/${icon.category}/${icon.identifier}@2x_dark.png');
    File source = new File(icon.fullPath.replaceFirst('_black_', '_white_'));
    count += _resize(source, dest, 32, black: true);
  }

  print('Copied ${count} icons.');
}

int _resize(File source, File dest, int size, { bool black: true}) {
  dest.parent.createSync(recursive: true);

  ProcessResult result = Process.runSync('convert', [
    source.path,
    '-resize', '${size}x${size}',
    '-fill', black ? 'black' : 'white', '-colorize', black ? '50%' : '35%',
    dest.path
  ]);

  if (result.exitCode !=0 ) {
    print('Error resizing image with imagemagick: ${result.stdout}\n${result.stderr}');
    exit(1);
  }

  return 1;
}

class Icon {
  final String name;
  final String codepoint;

  String category;
  String fullPath;

  Icon(this.name, this.codepoint);

  String get identifier => kIdentifierRewrites[name] ?? name;
}
