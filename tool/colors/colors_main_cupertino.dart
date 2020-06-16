// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'flutter/colors_cupertino.dart';
import 'stubs.dart';

void main() {
  // https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/cupertino/colors.dart

  StringBuffer buf = new StringBuffer();
  buf.writeln('# Generated file - do not edit.');
  buf.writeln();
  buf.writeln('# suppress inspection "UnusedProperty" for whole file');
  buf.writeln();

  // colors
  final Map<String, Color> colors = <String, Color>{
    'activeBlue': CupertinoColors.activeBlue,
    'activeGreen': CupertinoColors.activeGreen,
    'activeOrange': CupertinoColors.activeOrange,
    'black': CupertinoColors.black,
    'darkBackgroundGray': CupertinoColors.darkBackgroundGray,
    'destructiveRed': CupertinoColors.destructiveRed,
    'extraLightBackgroundGray': CupertinoColors.extraLightBackgroundGray,
    'inactiveGray': CupertinoColors.inactiveGray,
    'label': CupertinoColors.label,
    'lightBackgroundGray': CupertinoColors.lightBackgroundGray,
    'link': CupertinoColors.link,
    'opaqueSeparator': CupertinoColors.opaqueSeparator,
    'placeholderText': CupertinoColors.placeholderText,
    'quaternaryLabel': CupertinoColors.quaternaryLabel,
    'quaternarySystemFill': CupertinoColors.quaternarySystemFill,
    'secondaryLabel': CupertinoColors.secondaryLabel,
    'secondarySystemBackground': CupertinoColors.secondarySystemBackground,
    'secondarySystemFill': CupertinoColors.secondarySystemFill,
    'secondarySystemGroupedBackground':
        CupertinoColors.secondarySystemGroupedBackground,
    'separator': CupertinoColors.separator,
    'systemBackground': CupertinoColors.systemBackground,
    'systemBlue': CupertinoColors.systemBlue,
    'systemFill': CupertinoColors.systemFill,
    'systemGreen': CupertinoColors.systemGreen,
    'systemGrey': CupertinoColors.systemGrey,
    'systemGrey2': CupertinoColors.systemGrey2,
    'systemGrey3': CupertinoColors.systemGrey3,
    'systemGrey4': CupertinoColors.systemGrey4,
    'systemGrey5': CupertinoColors.systemGrey5,
    'systemGrey6': CupertinoColors.systemGrey6,
    'systemGroupedBackground': CupertinoColors.systemGroupedBackground,
    'systemIndigo': CupertinoColors.systemIndigo,
    'systemOrange': CupertinoColors.systemOrange,
    'systemPink': CupertinoColors.systemPink,
    'systemPurple': CupertinoColors.systemPurple,
    'systemRed': CupertinoColors.systemRed,
    'systemTeal': CupertinoColors.systemTeal,
    'systemYellow': CupertinoColors.systemYellow,
    'tertiaryLabel': CupertinoColors.tertiaryLabel,
    'tertiarySystemBackground': CupertinoColors.tertiarySystemBackground,
    'tertiarySystemFill': CupertinoColors.tertiarySystemFill,
    'tertiarySystemGroupedBackground':
        CupertinoColors.tertiarySystemGroupedBackground,
    'white': CupertinoColors.white,
  };

  buf.writeln('# colors');
  for (String name in colors.keys) {
    Color color = colors[name];
    if (color is CupertinoDynamicColor) {
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
  buf.writeln();

  print(buf.toString().trim());
}
