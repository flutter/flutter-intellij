/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
// @dart = 2.10
import 'package:path/path.dart' as p;
import 'package:plugin_tool/util.dart';
import 'package:test/test.dart';

void main() {
  group('Validate `stripComponents` function', () {
    test('Correct components are removed', () {
      var testPath = p.join('a', 'b', 'c', 'd');
      expect(stripComponents(testPath, 1), p.join('b', 'c', 'd'));
      expect(stripComponents(testPath, 2), p.join('c', 'd'));
      expect(stripComponents(testPath, 3), p.join('d'));
    });

    test('Last element is always returned', () {
      var testPath = p.join('a', 'b', 'c', 'd');

      expect(stripComponents(testPath, 4), p.join('d'));
      expect(stripComponents(testPath, 5), p.join('d'));
    });
  });

}
