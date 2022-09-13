// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.10

import 'dart:async';
import 'dart:io';

import 'package:path/path.dart' as p;

import 'globals.dart';
import 'util.dart';

String artifactDir = p.join(rootPath, 'artifacts');

class Artifact {
  final String ideaProduct;
  final String ideaVersion;
  final String fileName;
  final String outputFolderName;

  Artifact(this.ideaProduct, this.ideaVersion, this.fileName)
      : outputFolderName = '$ideaProduct-$ideaVersion';

  bool get isZip => fileName.endsWith('.zip');

  Future<bool> exists() {
    return FileSystemEntity.isFile(filePath);
  }

  bool existsSync() {
    return FileSystemEntity.isFileSync(filePath);
  }

  String get outPath => p.join(artifactDir, outputFolderName);

  String get filePath => p.join(artifactDir, fileName);
}

class ArtifactManager {
  // We currently only resolve linux packages
  final String baseUrl =
      'https://storage.googleapis.com/flutter_infra_release/flutter/intellij';

  final List<Artifact> artifacts = [];

  Artifact add(Artifact artifact) {
    artifacts.add(artifact);
    return artifact;
  }

  Future<int> provision({bool rebuildCache = false}) async {
    int result = 0;

    // Artifacts should remain immutable, only work on copies.
    for (var artifact in artifacts.sublist(0)) {
      log('\nChecking artifact ${artifact.fileName}');
      await artifact.exists().then((exists) async {
        //  If the artifact doesn't exist...
        if (exists) {
          log('Artifact exists at ${artifact.fileName}');
        } else {
          // ...we need to download it
          await downloadArtifact(artifact);
        }

        // Force delete existing content
        if (rebuildCache) {
          log('Removing ${artifact.outPath}');
          await removeAll(artifact.outPath);
        }

        // Check if we need to unpack the artifact
        if (isUnpacked(artifact)) {
          log('Artifact directory ${artifact.outPath} has content.');
        } else {
          log('Unpacking ${artifact.fileName} to ${artifact.outPath}');
          await unpackArtifact(artifact, targetDirectory: artifact.outPath);
          log('${artifact.fileName} unpacked');
        }
      }).onError((error, stackTrace) {
        log("Unable to validate ${artifact.fileName}", asError: true);
        log('$error', asError: true);
        result = -1;
      });
    }

    return result;
  }

  Future<void> downloadArtifact(Artifact artifact) async {
    var artifactUrl = "$baseUrl/${artifact.fileName}";

    // Have to make sure the target parent dir exists...
    var targetDir = Directory(artifact.filePath).parent;
    if (!targetDir.existsSync()) {
      targetDir.createSync(recursive: true);
    }

    log('Downloading ${artifact.fileName} from $artifactUrl');

    await curl(artifactUrl, to: artifact.filePath).then((result) async {
      File localFile = File(artifact.filePath);
      // Storage API returns errors as files,
      // so peek the size and try the alt if needed.
      if (localFile.lengthSync() < 1000 &&
          artifact.ideaProduct == 'android-studio') {
        // We can try downloading Android Studio from android.com
        localFile.deleteSync();
        String altUrl =
            'https://redirector.gvt1.com/edgedl/android/studio/ide-zips/${artifact.ideaVersion}/${artifact.fileName}';
        result = await curl(altUrl, to: artifact.filePath);
      }

      if (localFile.lengthSync() < 1000) {
        localFile.deleteSync();
        throw FileSystemException(
            "Unable to download $artifactUrl to ${artifact.filePath}");
      }
    });
  }

  bool isUnpacked(Artifact artifact) {
    var directory = Directory(artifact.outPath);

    return directory.existsSync() &&
        directory.listSync().isNotEmpty &&
        directory
            .statSync()
            .modified
            .isAfter(File(artifact.fileName).lastModifiedSync());
  }
}
