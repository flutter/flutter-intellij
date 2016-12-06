// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:io';

import 'package:path/path.dart' as path;

import 'update_icons.dart' show kIdentifierRewrites;

// TODO: Do we need both black and white images?

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
    if (!p.contains('/1x_web/')) continue;

    String name = path.basename(p);
    name = name.substring(prefix.length);
    name = name.substring(0, name.length - suffix.length);

    Icon icon = iconMap[name];
    if (icon == null) continue;

    icon.fullPath = p;
    icon.category = path.basename(entity.parent.parent.path);
  }

  print('Parsed ${codepointsFile.path}.');

  // Generate properties file.
  StringBuffer buf = new StringBuffer();
  buf.writeln('# Generated file - do not edit.');

  for (Icon icon in icons.where((icon) => icon.fullPath != null)) {
    buf.writeln();
    buf.writeln('${icon.codepoint}.codepoint=${icon.identifier}');
    buf.writeln('${icon.identifier}=/flutter/icons/${icon.category}/${icon.identifier}.png');
  }

  File propertiesFile = new File('resources/flutter/icons.properties');
  propertiesFile.writeAsStringSync(buf.toString());
  print('Wrote ${propertiesFile.path}.');

  // Copy over icons.
  print('Resizing images...');

  int count = 0;

  for (Icon icon in icons.where((icon) => icon.fullPath != null)) {
    File dest = new File('resources/flutter/icons/${icon.category}/${icon.identifier}@2x.png');
    File source = new File(icon.fullPath);

    // 24x24
    count++;
    dest.parent.createSync();
    dest.writeAsBytesSync(source.readAsBytesSync());

    // 12x12
    count++;
    ProcessResult result = Process.runSync('convert', [
      source.path,
      '-resize',
      '12x12',
      'resources/flutter/icons/${icon.category}/${icon.identifier}.png'
    ]);

    if (result.exitCode !=0 ) {
      print('Error resizing image with imagemagick: ${result.stdout}\n${result.stderr}');
      exit(1);
    }
  }

  print('Copied ${count} icons.');
}

class Icon {
  final String name;
  final String codepoint;

  String category;
  String fullPath;

  Icon(this.name, this.codepoint);

  String get identifier => kIdentifierRewrites[name] ?? name;
}
