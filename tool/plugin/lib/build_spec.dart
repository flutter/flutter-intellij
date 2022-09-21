// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.10

import 'dart:async';
import 'dart:io';

import 'package:markdown/markdown.dart';

import 'artifact.dart';
import 'util.dart';

enum Platforms { linux, mac, mac_arm, windows }

class BuildSpec {
  // Build targets
  final String name;
  final String version;
  final String ijVersion;
  final bool isTestTarget;
  final bool isUnitTestTarget;
  final String ideaProduct;
  final String ideaVersion;
  final String dartPluginVersion;
  final String baseVersion;
  final Platforms platform;

  // plugin.xml variables
  final String sinceBuild;
  final String untilBuild;
  final String pluginId = 'io.flutter';
  final String release;
  final List<dynamic> filesToSkip;
  String channel;
  String _changeLog;

  ArtifactManager artifacts = ArtifactManager();

  Artifact product;
  Artifact dartPlugin;

  BuildSpec.fromJson(Map json, this.release, {this.platform = Platforms.linux})
      : name = json['ideaPluginName'],
        channel = json['channel'],
        version = json['version'],
        ijVersion = json['ijVersion'],
        ideaProduct = json['ideaProduct'],
        ideaVersion = json['ideaVersion'],
        baseVersion = json['baseVersion'] ?? json['ideaVersion'],
        dartPluginVersion = json['dartPluginVersion'],
        sinceBuild = json['sinceBuild'],
        untilBuild = json['untilBuild'],
        filesToSkip = json['filesToSkip'] ?? [],
        isUnitTestTarget = (json['isUnitTestTarget'] ?? 'false') == 'true',
        isTestTarget = (json['isTestTarget'] ?? 'false') == 'true' {
    // We need Dart and an IDE but...
    artifacts.add(
        Artifact('Dart', dartPluginVersion, 'Dart-$dartPluginVersion.zip'));

    String ideFileName;
    // ...we're not supporting canaries...
    if (ideaVersion != 'LATEST-EAP-SNAPSHOT') {
      double version = double.parse(ideaVersion.split('.').take(2).join('.'));
      // ...just
      if (ideaProduct == 'android-studio') {
        // Oldest supported version is currently 2021.1
        //   versions < 2020 had a prefix of '$ideaProduct-ide
        String prefix = ideaProduct;
        String suffix = platform == Platforms.linux && version > 183.5452
            ? 'tar.gz'
            : 'zip'; // Versions prior to 183.5452 used zip
        ideFileName = '$prefix-$ideaVersion-${platform.name}.$suffix';
      } else {
        // ...and Intellij
        String ext = () {
          switch (platform) {
            case Platforms.linux:
              return 'tar.gz';
            case Platforms.mac:
              return 'dmg';
            case Platforms.mac_arm:
              return 'aarch64.dmg';
            case Platforms.windows:
              return 'win.zip';
          }
        }();

        ideFileName = '$ideaProduct-$ideaVersion.$ext';
      }
      artifacts.add(Artifact(ideaProduct, ideaVersion, ideFileName));
    }
  }

  bool get copyIjVersion => isAndroidStudio && ijVersion != null;

  bool get isAndroidStudio => ideaProduct.contains('android-studio');

  bool get isDevChannel => channel == 'dev';

  bool get isStableChannel => channel == 'stable';

  bool get isReleaseMode => release != null;

  bool get isSynthetic => false;

  String get productFile => isAndroidStudio ? "$ideaProduct-ide" : ideaProduct;

  String get changeLog {
    if (_changeLog == null) {
      if (channel == 'stable') {
        _changeLog = _parseChangelog();
      } else {
        _changeLog = '';
      }
    }

    return _changeLog;
  }

  String _parseChangelog() {
    var text = File('CHANGELOG.md').readAsStringSync();
    return _parseChanges(text);
  }

  String _parseChanges(String text) {
    var html = markdownToHtml(text);

    // Translate our markdown based changelog into html; remove unwanted
    // paragraph tags.
    return html
        .replaceAll('</h2><ul>', '</h2>\n<ul>')
        .replaceAll('<ul>\n<li>', '<ul>\n  <li>')
        .replaceAll('</li>\n<li>', '</li>\n  <li>')
        .replaceAll('</li></ul>', '</li>\n</ul>')
        .replaceAll('\n<p>', '')
        .replaceAll('<p>', '')
        .replaceAll('</p>\n', '')
        .replaceAll('</p>', '');
  }

  @override
  String toString() {
    return 'BuildSpec($ideaProduct $ideaVersion $dartPluginVersion $sinceBuild '
        '$untilBuild version: $version release: $release")';
  }

  Future<BuildSpec> initChangeLog() async {
    if (channel == 'dev') {
      _changeLog = _parseChanges(await makeDevLog(this));
    }
    return this;
  }

  void buildForDev() {
    // Build everything. For release builds we do not build specs on the dev channel.
    if (channel == 'stable') channel = 'dev';
  }

  void buildForMaster() {
    // Ensure the dev-channel-only files are stored in release_master.
    if (channel == 'dev') channel = 'stable';
  }
}

/// This represents a BuildSpec that is used to generate the plugin.xml
/// that is used during development. It needs to span all possible versions.
/// The product-matrix.json file lists the versions in increasing build order.
/// The first one is the earliest version used during development and the
/// last one is the latest used during development. This BuildSpec combines
/// those two.
class SyntheticBuildSpec extends BuildSpec {
  BuildSpec alternate;

  SyntheticBuildSpec.fromJson(
      Map json, String releaseNum, List<BuildSpec> specs)
      : super.fromJson(json, releaseNum) {
    try {
      // 'isUnitTestTarget' should always be in the spec for the latest IntelliJ (not AS).
      alternate = specs.firstWhere((s) => s.isUnitTestTarget);
    } on StateError catch (_) {
      log('No build spec defines "isUnitTestTarget"');
      exit(1);
    }
  }

  @override
  String get untilBuild => alternate.untilBuild;

  @override
  bool get isSynthetic => true;
}
