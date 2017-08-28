// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'colors.dart';

void main() {
  // https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/material/colors.dart

  StringBuffer buf = new StringBuffer();
  buf.writeln('# Generated file - do not edit.');
  buf.writeln();
  buf.writeln('# suppress inspection "UnusedProperty" for whole file');
  buf.writeln();

  // colors
  final Map<String, Color> colors = <String, Color>{
    'transparent': Colors.transparent,
    'black': Colors.black,
    'black87': Colors.black87,
    'black54': Colors.black54,
    'black38': Colors.black38,
    'black45': Colors.black45,
    'black26': Colors.black26,
    'black12': Colors.black12,
    'white': Colors.white,
    'white70': Colors.white70,
    'white30': Colors.white30,
    'white12': Colors.white12,
    'white10': Colors.white10
  };

  buf.writeln('# colors');
  for (String name in colors.keys) {
    Color color = colors[name];
    buf.writeln('${name}=${color.toString()}');
  }
  buf.writeln();

  // primaries + grey
  final Map<String, ColorSwatch> primaryMap = <String, ColorSwatch>{
    'red': Colors.red,
    'pink': Colors.pink,
    'purple': Colors.purple,
    'deepPurple': Colors.deepPurple,
    'indigo': Colors.indigo,
    'blue': Colors.blue,
    'lightBlue': Colors.lightBlue,
    'cyan': Colors.cyan,
    'teal': Colors.teal,
    'green': Colors.green,
    'lightGreen': Colors.lightGreen,
    'lime': Colors.lime,
    'yellow': Colors.yellow,
    'amber': Colors.amber,
    'orange': Colors.orange,
    'deepOrange': Colors.deepOrange,
    'brown': Colors.brown,
    // grey intentionally omitted
    'blueGrey': Colors.blueGrey
  };

  buf.writeln('# primaries');
  if (Colors.primaries.length != primaryMap.length) {
    throw 'unexpected length for Colors.primaries';
  }
  _writeColorSet(buf, 'grey', Colors.grey);
  for (String name in primaryMap.keys) {
    _writeColorSet(buf, name, primaryMap[name]);
  }
  buf.writeln();

  // accents
  final Map<String, MaterialAccentColor> accentsMap = {
    'redAccent': Colors.redAccent,
    'pinkAccent': Colors.pinkAccent,
    'purpleAccent': Colors.purpleAccent,
    'deepPurpleAccent': Colors.deepPurpleAccent,
    'indigoAccent': Colors.indigoAccent,
    'blueAccent': Colors.blueAccent,
    'lightBlueAccent': Colors.lightBlueAccent,
    'cyanAccent': Colors.cyanAccent,
    'tealAccent': Colors.tealAccent,
    'greenAccent': Colors.greenAccent,
    'lightGreenAccent': Colors.lightGreenAccent,
    'limeAccent': Colors.limeAccent,
    'yellowAccent': Colors.yellowAccent,
    'amberAccent': Colors.amberAccent,
    'orangeAccent': Colors.orangeAccent,
    'deepOrangeAccent': Colors.deepOrangeAccent
  };

  buf.writeln('# accents');
  if (Colors.accents.length != accentsMap.length) {
    throw 'unexpected length for Colors.accents';
  }
  for (String name in accentsMap.keys) {
    _writeColorSet(buf, name, accentsMap[name]);
  }

  print(buf.toString().trim());
}

void _writeColorSet(StringBuffer buf, String name, ColorSwatch colorSwatch) {
  // write the primary color
  Color mainColor = new Color(colorSwatch.value);
  buf.writeln('${name}.primary=${mainColor.toString()}');

  // write the individual colors
  for (int index in [
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
  ]) {
    Color color = colorSwatch[index];
    if (color != null) {
      buf.writeln('${name}[${index}]=${color.toString()}');
    }
  }
}

class Color {
  final int value;

  const Color(this.value);

  String toString() => '${value.toRadixString(16).padLeft(8, '0')}';
}

class ColorSwatch<T> extends Color {
  final Map<T, Color> swatch;

  const ColorSwatch(int primary, this.swatch) : super(primary);

  /// Returns an element of the swatch table.
  Color operator [](T index) => swatch[index];
}

int hashValues(Object arg01, Object arg02, Object arg03) {
  int result = 0;
  result = _Jenkins.combine(result, arg01);
  result = _Jenkins.combine(result, arg02);
  result = _Jenkins.combine(result, arg03);
  return _Jenkins.finish(result);
}

/// Jenkins hash function, optimized for small integers.
//
// Borrowed from the dart sdk: sdk/lib/math/jenkins_smi_hash.dart.
class _Jenkins {
  static int combine(int hash, Object o) {
    assert(o is! Iterable);
    hash = 0x1fffffff & (hash + o.hashCode);
    hash = 0x1fffffff & (hash + ((0x0007ffff & hash) << 10));
    return hash ^ (hash >> 6);
  }

  static int finish(int hash) {
    hash = 0x1fffffff & (hash + ((0x03ffffff & hash) << 3));
    hash = hash ^ (hash >> 11);
    return 0x1fffffff & (hash + ((0x00003fff & hash) << 15));
  }
}
