// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

const Map<String, String> plugins = const {
  'io.flutter': '9212',
  'io.flutter.as': '10139', // Currently unused.
};

const int cloudErrorFileMaxSize = 1000; // In bytes.

// Globals are initialized early in ProductCommand.
String rootPath;
String lastReleaseName;
DateTime lastReleaseDate;
int pluginCount = 0;
