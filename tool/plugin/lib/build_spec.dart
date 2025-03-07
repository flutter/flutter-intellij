// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'dart:io';

import 'package:markdown/markdown.dart';

import 'util.dart';

class BuildSpec {
  // Build targets
  // TODO (jwren) can we get rid of "name"
  final String name;

  // TODO (jwren) these two can be consilidated

  final String version;
  final String? ijVersion;
  final bool isTestTarget;
  final bool isUnitTestTarget;
  final String ideaProduct;
  final String ideaVersion;
  final String androidPluginVersion;
  final String dartPluginVersion;
  final String javaVersion;

  // TODO (jwren) can baseVersion be removed?
  final String baseVersion;

  // plugin.xml variables
  final String sinceBuild;
  final String untilBuild;
  final String pluginId = 'io.flutter';
  final String? release;
  final List<String> filesToSkip;
  String channel;
  String? _changeLog;

  BuildSpec.fromJson(Map<String, Object?> json, this.release)
    : name = json['name'] as String,
      channel = json['channel'] as String,
      version = json['version'] as String,
      ijVersion = json['ijVersion'] as String?,
      ideaProduct = json['ideaProduct'] as String,
      ideaVersion = json['ideaVersion'] as String,
      baseVersion = (json['baseVersion'] ?? json['ideaVersion']) as String,
      androidPluginVersion = json['androidPluginVersion'] as String,
      dartPluginVersion = json['dartPluginVersion'] as String,
      sinceBuild = json['sinceBuild'] as String,
      untilBuild = json['untilBuild'] as String,
      filesToSkip = json['filesToSkip'] as List<String>? ?? [],
      isUnitTestTarget = json['isUnitTestTarget'] == 'true',
      isTestTarget = json['isTestTarget'] == 'true',
      javaVersion = json['javaVersion'] as String;

  bool get copyIjVersion => isAndroidStudio && ijVersion != null;

  bool get isAndroidStudio => ideaProduct.contains('android-studio');

  bool get isDevChannel => channel == 'dev';

  bool get isStableChannel => channel == 'stable';

  bool get isReleaseMode => release != null;

  bool get isSynthetic => false;

  String get productFile => isAndroidStudio ? "$ideaProduct-ide" : ideaProduct;

  String get changeLog {
    if (_changeLog case final changelog?) {
      return changelog;
    }

    if (channel == 'stable') {
      return _changeLog = _parseChangelog();
    } else {
      return _changeLog = '';
    }
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
    return 'BuildSpec('
        'ideaProduct: $ideaProduct, '
        'ideaVersion: $ideaVersion, '
        'baseVersion: $baseVersion, '
        'dartPluginVersion: $dartPluginVersion, '
        'javaVersion: $javaVersion, '
        'since: $sinceBuild, '
        'until: $untilBuild, '
        'version: "$release")';
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
  late final BuildSpec alternate;

  SyntheticBuildSpec.fromJson(
    super.json,
    super.releaseNum,
    List<BuildSpec> specs,
  ) : super.fromJson() {
    try {
      // 'isUnitTestTarget' should always be in the spec for the latest IntelliJ (not AS).
      alternate = specs.firstWhere((s) => s.isUnitTestTarget);
    } on StateError catch (_) {
      log('No build spec defines "isUnitTestTarget"');
      exit(1);
    }
  }

  @override
  String get sinceBuild => alternate.sinceBuild;

  @override
  String get untilBuild => alternate.untilBuild;

  @override
  bool get isSynthetic => true;
}
