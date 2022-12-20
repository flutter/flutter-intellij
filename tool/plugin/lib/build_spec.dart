// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.12

import 'dart:async';
import 'dart:io';

import 'package:markdown/markdown.dart';

import 'artifact.dart';
import 'util.dart';

class BuildSpec {
  // Build targets
  final String name;
  final String version;
  final String? ijVersion;
  final bool isTestTarget;
  final bool isUnitTestTarget;
  final String ideaProduct;
  final String ideaVersion;
  final String dartPluginVersion;
  final String baseVersion;

  // plugin.xml variables
  final String sinceBuild;
  final String untilBuild;
  final String pluginId = 'io.flutter';
  final String? release;
  final List<dynamic> filesToSkip;
  String channel;
  String? _changeLog;

  ArtifactManager artifacts = ArtifactManager();

  late Artifact product;
  late Artifact dartPlugin;

  BuildSpec.fromJson(Map json, this.release)
      : name = json['name'],
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
    createArtifacts();
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

    return _changeLog!;
  }

  void createArtifacts() {
    if (ideaVersion != 'LATEST-EAP-SNAPSHOT') {
      if (ideaProduct == 'android-studio') {
        product = Artifact('$ideaProduct-ide-$ideaVersion-mac.zip',
            bareArchive: true, output: ideaProduct);
        if (product.exists()) {
          artifacts.add(product);
        } else {
          product = Artifact('$ideaProduct-ide-$ideaVersion-linux.zip',
              output: ideaProduct);
          if (product.exists()) {
            artifacts.add(product);
          } else {
            product = Artifact('$ideaProduct-$ideaVersion-mac.zip',
                bareArchive: true, output: ideaProduct);
            if (product.exists()) {
              artifacts.add(product);
            } else {
              product = Artifact('$ideaProduct-$ideaVersion-linux.zip',
                  output: ideaProduct);
              if (product.exists()) {
                artifacts.add(product);
              } else {
                // We don't know which one we need, so add both.
                // We only put Linux versions in cloud storage.
                artifacts.add(Artifact('$ideaProduct-$ideaVersion-linux.tar.gz',
                    output: ideaProduct));
                artifacts.add(Artifact('$ideaProduct-$ideaVersion-linux.tar.gz',
                    output: ideaProduct));
                artifacts.add(Artifact(
                    '$ideaProduct-ide-$ideaVersion-linux.tar.gz',
                    output: ideaProduct));
              }
            }
          }
        }
      } else {
        product = artifacts.add(
            Artifact('$ideaProduct-$ideaVersion.tar.gz', output: ideaProduct));
      }
    }
    dartPlugin = artifacts.add(Artifact('Dart-$dartPluginVersion.zip'));
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
        '$untilBuild version: "$release")';
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
  late BuildSpec alternate;

  SyntheticBuildSpec.fromJson(
      Map json, String? releaseNum, List<BuildSpec> specs)
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
  String get sinceBuild => alternate.sinceBuild;

  @override
  String get untilBuild => alternate.untilBuild;

  @override
  bool get isSynthetic => true;
}
