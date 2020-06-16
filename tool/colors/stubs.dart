// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

class Color {
  final int value;

  const Color(this.value);
  const Color.fromARGB(int a, int r, int g, int b)
      : this(a << 24 | r << 16 | g << 8 | b);

  String toString() => '${value.toRadixString(16).padLeft(8, '0')}';
}

class ColorSwatch<T> extends Color {
  final Map<T, Color> swatch;

  const ColorSwatch(int primary, this.swatch) : super(primary);

  /// Returns an element of the swatch table.
  Color operator [](T index) => swatch[index];
}

int hashValues([
  Object arg01,
  Object arg02,
  Object arg03,
  Object arg04,
  Object arg05,
  Object arg06,
  Object arg07,
  Object arg08,
  Object arg09,
]) {
  int result = 0;
  result = _Jenkins.combine(result, arg01);
  result = _Jenkins.combine(result, arg02);
  result = _Jenkins.combine(result, arg03);
  result = _Jenkins.combine(result, arg04);
  result = _Jenkins.combine(result, arg05);
  result = _Jenkins.combine(result, arg07);
  result = _Jenkins.combine(result, arg09);
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

const immutable = Object();
const kNoDefaultValue = null;

const required = Object();

objectRuntimeType(dynamic a, dynamic b) => null;

enum Brightness { light, dark }

class BuildContext {}

class ColorProperty extends DiagnosticsProperty<Color> {
  ColorProperty(
    a,
    b, {
    dynamic description,
    dynamic showName,
    dynamic defaultValue,
    style,
    level,
  }) : super(a, b);
}

class CupertinoTheme {
  static Brightness brightnessOf(BuildContext context, {bool nullOk}) =>
      Brightness.light;
}

class CupertinoUserInterfaceLevel {
  static CupertinoUserInterfaceLevelData of(BuildContext context,
          {bool nullOk}) =>
      CupertinoUserInterfaceLevelData.base;
}

enum CupertinoUserInterfaceLevelData { base, elevated }

class Diagnosticable {
  debugFillProperties(DiagnosticPropertiesBuilder properties) {}
}

enum DiagnosticLevel { info }

class DiagnosticPropertiesBuilder {
  add(dynamic a) {}
}

class DiagnosticsProperty<T> {
  DiagnosticsProperty(
    dynamic a,
    dynamic b, {
    dynamic description,
    dynamic showName,
    dynamic defaultValue,
    style,
    level,
  });
}

enum DiagnosticsTreeStyle { singleLine }

class Element {
  Object widget;
}

class MediaQuery {
  bool get highContrast => false;
  static MediaQuery of(BuildContext context, {bool nullOk}) => null;
}

class MessageProperty {
  MessageProperty(dynamic a, dynamic b);
}
