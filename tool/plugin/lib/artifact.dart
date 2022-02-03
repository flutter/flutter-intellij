// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.10

import 'dart:async';
import 'dart:io';

import 'package:path/path.dart' as p;

import 'globals.dart';
import 'util.dart';

class Artifact {
  String file;
  final bool bareArchive;
  String output;

  Artifact(this.file, {this.bareArchive = false, this.output}) {
    output ??= file.substring(0, file.lastIndexOf('-'));
  }

  bool get isZip => file.endsWith('.zip');

  bool exists() {
    if (FileSystemEntity.isFileSync('artifacts/$file')) return true;
    convertToTar();
    return FileSystemEntity.isFileSync('artifacts/$file');
  }

  String get outPath => p.join(rootPath, 'artifacts', output);

  // Historically, Android Studio has been distributed as a zip file.
  // Recent distros are packaged as gzip'd tar files.
  void convertToTar() {
    if (!isZip) return;
    file = _convertToTar(file);
  }
}

class ArtifactManager {
  final String base =
      'https://storage.googleapis.com/flutter_infra_release/flutter/intellij';

  final List<Artifact> artifacts = [];

  Artifact javac;

  ArtifactManager() {
    javac = add(Artifact(
      'intellij-javac2.zip',
      output: 'javac2',
      bareArchive: true,
    ));
  }

  Artifact add(Artifact artifact) {
    artifacts.add(artifact);
    return artifact;
  }

  Future<int> provision({bool rebuildCache = false}) async {
    separator('Getting artifacts');
    createDir('artifacts');

    var result = 0;
    for (var artifact in artifacts.sublist(0)) {
      var doDownload = true;

      void alreadyDownloaded(String path) {
        log('$path exists in cache');
        doDownload = false;
      }

      var path = 'artifacts/${artifact.file}';
      if (FileSystemEntity.isFileSync(path)) {
        alreadyDownloaded(path);
      } else {
        if (artifact.isZip) {
          var tarPath = _convertToTar(path);
          if (FileSystemEntity.isFileSync(tarPath)) {
            artifact.convertToTar();
            alreadyDownloaded(tarPath);
          }
        }
        if (doDownload) {
          log('downloading $path...');
          result = await curl('$base/${artifact.file}', to: path);
          if (result != 0) {
            log('download failed');
          }
          var archiveFile = File(path);
          if (!_isValidDownloadArtifact(archiveFile)) {
            // If the file is missing the server returns a small file containing
            // an error message. Delete it and try again. The smallest file we
            // store in the cloud is over 700K.
            log('archive file not found: $base/${artifact.file}');
            archiveFile.deleteSync();
            if (artifact.isZip) {
              artifact.convertToTar();
              path = 'artifacts/${artifact.file}';
              result = await curl('$base/${artifact.file}', to: path);
              if (result != 0) {
                log('download failed');
                artifacts.remove(artifact);
                continue;
              }
              var archiveFile = File(path);
              if (!_isValidDownloadArtifact(archiveFile)) {
                log('archive file not found: $base/${artifact.file}');
                archiveFile.deleteSync();
                artifacts.remove(artifact);
                continue;
              }
            } else {
              artifacts.remove(artifact);
              continue;
            }
          }
        }
      }

      // clear unpacked cache
      if (rebuildCache || !FileSystemEntity.isDirectorySync(artifact.outPath)) {
        await removeAll(artifact.outPath);
      }
      if (isCacheDirectoryValid(artifact)) {
        continue;
      }

      // expand
      if (Directory(artifact.outPath).existsSync()) {
        await removeAll(artifact.outPath);
      }
      createDir(artifact.outPath);

      if (artifact.isZip) {
        if (artifact.bareArchive) {
          result = await exec(
              'unzip', ['-q', '-d', artifact.output, artifact.file],
              cwd: 'artifacts');
          var files = Directory(artifact.outPath).listSync();
          if (files.length < 3) /* Might have .DS_Store */ {
            // This is the Mac zip case.
            var entity = files.first;
            if (entity.statSync().type == FileSystemEntityType.file) {
              entity = files.last;
            }
            Directory("${entity.path}/Contents")
                .renameSync("${artifact.outPath}Temp");
            Directory(artifact.outPath).deleteSync(recursive: true);
            Directory("${artifact.outPath}Temp").renameSync(artifact.outPath);
          }
        } else {
          result = await exec('unzip', ['-q', artifact.file], cwd: 'artifacts');
        }
      } else {
        result = await exec(
          'tar',
          [
            '--strip-components=1',
            '-zxf',
            artifact.file,
            '-C',
            artifact.output
          ],
          cwd: p.join(rootPath, 'artifacts'),
        );
      }
      if (result != 0) {
        log('unpacking failed');
        await removeAll(artifact.output);
        break;
      }

      log('');
    }
    return result;
  }
}

String _convertToTar(String path) =>
    path.replaceFirst('.zip', '.tar.gz', path.length - 5);

bool _isValidDownloadArtifact(File archiveFile) =>
    archiveFile.existsSync() &&
    archiveFile.lengthSync() > cloudErrorFileMaxSize;
