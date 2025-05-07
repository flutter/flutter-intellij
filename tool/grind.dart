// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:io';

import 'package:grinder/grinder.dart';
import 'package:http/http.dart' as http;

void main(List<String> args) => grind(args);

@Task('Check plugin URLs for live-ness')
void checkUrls() async {
  log('checking URLs in FlutterBundle.properties...');
  var lines =
      await File(
        'flutter-idea/src/io/flutter/FlutterBundle.properties',
      ).readAsLines();
  for (var line in lines) {
    var split = line.split('=');
    if (split.length == 2) {
      // flutter.io.gettingStarted.url | flutter.analytics.privacyUrl
      if (split[0].toLowerCase().endsWith('url')) {
        var url = split[1];
        var response = await http.get(Uri.parse(url));
        log('checking: $url...');
        if (response.statusCode != 200) {
          fail(
            '$url GET failed: [${response.statusCode}] ${response.reasonPhrase}',
          );
        }
      }
    }
  }
  log('OK!');
}
