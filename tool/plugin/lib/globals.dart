// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.12

// Map plugin ID to JetBrains registry ID.
const Map<String, String> pluginRegistryIds = {
  'io.flutter': '9212',
  'io.flutter.as': '10139', // Currently unused.
};

const int cloudErrorFileMaxSize = 1000; // In bytes.

// Globals are initialized early in ProductCommand. These are used in various
// top-level functions. This is not ideal, but the "proper" solution would be
// to move nearly all the top-level functions to methods in ProductCommand.
String rootPath = '';
String lastReleaseName = '';
DateTime lastReleaseDate = DateTime.now();
int pluginCount = 0;
