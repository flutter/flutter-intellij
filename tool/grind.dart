// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:io';

import 'package:grinder/grinder.dart';
import 'package:http/http.dart' as http;

void main(List<String> args) => grind(args);

@Task('Check plugin and documentation URLs for liveness')
Future<void> checkUrls() async {
  var client = http.Client();
  var failedUrls = <String>[];
  var headers = {
    'User-Agent': 'Mozilla/5.0 (flutter-intellij CI link check)',
  };

  Future<void> verifyUrl(String url, String source) async {
    log('checking: $url...');
    try {
      var response = await client
          .get(Uri.parse(url), headers: headers)
          .timeout(const Duration(seconds: 15));
      if (response.statusCode >= 400) {
        failedUrls.add(
          '$source: $url GET failed [${response.statusCode}] ${response.reasonPhrase}',
        );
      }
    } catch (e) {
      failedUrls.add('$source: $url failed with exception: $e');
    }
  }

  try {
    log('checking URLs in FlutterBundle.properties...');
    var bundleFile = File('src/io/flutter/FlutterBundle.properties');
    if (bundleFile.existsSync()) {
      var lines = await bundleFile.readAsLines();
      for (var line in lines) {
        var split = line.split('=');
        if (split.length == 2) {
          // flutter.io.gettingStarted.url | flutter.analytics.privacyUrl
          if (split[0].toLowerCase().endsWith('url')) {
            var url = split[1].trim();
            await verifyUrl(url, 'FlutterBundle.properties');
          }
        }
      }
    }

    var docFiles = [
      'CONTRIBUTING.md',
      'README.md',
      'CODE_OF_CONDUCT.md',
    ];
    var urlRegex = RegExp(r'https?://[^\s\)\]\>\"`]+');

    for (var docPath in docFiles) {
      var file = File(docPath);
      if (!file.existsSync()) continue;
      log('checking URLs in $docPath...');
      var content = await file.readAsString();
      var urls = <String>{};
      for (var match in urlRegex.allMatches(content)) {
        var url = match.group(0)!;
        while (url.endsWith('.') ||
            url.endsWith(',') ||
            url.endsWith(';') ||
            url.endsWith(':')) {
          url = url.substring(0, url.length - 1);
        }
        if (url.contains('<') ||
            url.contains('>') ||
            url.contains('localhost')) {
          continue;
        }
        urls.add(url);
      }
      for (var url in urls) {
        await verifyUrl(url, docPath);
      }
    }

    if (failedUrls.isNotEmpty) {
      fail('The following URLs failed:\n${failedUrls.join('\n')}');
    }
    log('OK!');
  } finally {
    client.close();
  }
}
