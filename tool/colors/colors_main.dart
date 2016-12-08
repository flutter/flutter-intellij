// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'colors.dart';

void main() {
  // https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/material/colors.dart

  StringBuffer buf = new StringBuffer();
  buf.writeln('# Generated file - do not edit.');
  buf.writeln();

  // colors
  final Map<String, Color> colors = {
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
  final Map<String, Map<int, Color>> primaryMap = {
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
  final Map<String, Map<int, Color>> accentsMap = {
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

void _writeColorSet(StringBuffer buf, String name, Map<int, Color> colors) {
  for (int index in colors.keys) {
    Color color = colors[index];
    buf.writeln('${name}[${index}]=${color.toString()}');
  }
}

class Color {
  final int hexValue;
  const Color(this.hexValue);
  String toString() => '${hexValue.toRadixString(16).padLeft(8, '0')}';
}
