/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
import 'dart:async';

import 'package:meta/meta.dart';
import 'package:test/test.dart';

/// Test data for the CommonTestConfigUtils test.
/// The outline for this file is pretty-printed in custom_outline.txt.
///
/// To update the outline, open this file in a debugging IntelliJ instance, then
/// set a breakpoint in the ActiveEditorsOutlineService's OutlineListener call
/// to `notifyOutlineUpdated`, then look at the Flutter Outline for this file.
/// This will break at the outline and allow you to copy its json value.
///
/// You can pretty-print the json using the pretty_print.dart script at
/// testData/sample_tests/bin/pretty_print.dart.
// TODO: use an API on the Dart Analyzer to update this outline more easily.
void main() {
  group('group 0', () {
    test('test 0', () {
      print('test contents');
    });
    testWidgets('test widgets 0', (tester) async {
      print('test widgets contents');
    });
    nonTest('not a test');
  });
  test('test 1', () {});
  nonGroup('not a group', () {
    g('custom group', () {
      t('custom test');
    });
  });
}

@isTestGroup
void g(String name, void Function() callback) {}

@isTest
void t(String name) {}

void nonGroup(String name, void Function() callback) {}

void nonTest(String name) {}

// This is a similar signature to the testWidgets method from Flutter.
@isTest
void testWidgets(String name, Future<void> Function(Object tester) test) {
  test(Object());
}
