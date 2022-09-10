// Copyright 2017 The Chromium Authors. All rights reserved. Use of this source
// code is governed by a BSD-style license that can be found in the LICENSE file.

// @dart = 2.10

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart';
import 'package:cli_util/cli_logging.dart';
import 'package:git/git.dart';
import 'package:path/path.dart' as p;

import 'artifact.dart';
import 'build_spec.dart';
import 'globals.dart';

Future<int> exec(String cmd, List<String> args, {String cwd}) async {
  if (cwd != null) {
    log(_shorten('$cmd ${args.join(' ')} {cwd=$cwd}'));
  } else {
    log(_shorten('$cmd ${args.join(' ')}'));
  }

  var codec = Platform.isWindows ? latin1 : utf8;
  final process = await Process.start(cmd, args, workingDirectory: cwd);
  _toLineStream(process.stderr, codec).listen(log);
  _toLineStream(process.stdout, codec).listen(log);

  return await process.exitCode;
}

Future<String> makeDevLog(BuildSpec spec) async {
  if (lastReleaseName == null) {
    return '';
  } // The shallow on travis causes problems.
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var since = lastReleaseName;
  var processResult =
      await gitDir.runCommand(['log', '--oneline', '$since..HEAD']);
  String out = processResult.stdout;
  var messages = out.trim().split('\n');
  var devLog = StringBuffer();
  devLog.writeln('## Changes since ${since.replaceAll('_', ' ')}');
  for (var m in messages) {
    devLog.writeln(m.replaceFirst(RegExp(r'^[A-Fa-f\d]+\s+'), '- '));
  }
  devLog.writeln();
  return devLog.toString();
}

Future<DateTime> dateOfLastRelease() async {
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var processResult = await gitDir
      .runCommand(['branch', '--list', '-v', '--no-abbrev', lastReleaseName]);
  String out = processResult.stdout;
  var logLine = out.trim().split('\n').first.trim();
  var match =
      RegExp(r'release_\d+\s+([A-Fa-f\d]{40})\s').matchAsPrefix(logLine);
  var commitHash = match.group(1);
  processResult =
      await gitDir.runCommand(['show', '--pretty=tformat:"%cI"', commitHash]);
  out = processResult.stdout;
  var date = out.trim().split('\n').first.trim();
  return DateTime.parse(date.replaceAll('"', ''));
}

Future<String> lastRelease() async {
  _checkGitDir();
  var gitDir = await GitDir.fromExisting(rootPath);
  var processResult =
      await gitDir.runCommand(['branch', '--list', 'release_*']);
  String out = processResult.stdout;
  var release = out.trim().split('\n').last.trim();
  if (release.isNotEmpty) return release;
  processResult =
      await gitDir.runCommand(['branch', '--list', '-a', '*release_*']);
  out = processResult.stdout;
  var remote =
      out.trim().split('\n').last.trim(); // "remotes/origin/release_43"
  release = remote.substring(remote.lastIndexOf('/') + 1);
  await gitDir.runCommand(['branch', '--track', release, remote]);
  return release;
}

final Ansi ansi = Ansi(true);

void separator(String name) {
  log('');
  log('${ansi.red}${ansi.bold}$name${ansi.none}', indent: false);
}

void log(String s, {bool indent = true}) {
  indent ? print('  $s') : print(s);
}

void createDir(String name) {
  final dir = Directory(name);
  if (!dir.existsSync()) {
    log('creating $name');
    dir.createSync(recursive: true);
  }
}

Future<int> curl(String url, {String to}) async {
  return await exec('curl', ['-o', to, url]);
}

Future<int> removeAll(String dir) async {
  var targetDirectory = Directory(dir);
  return targetDirectory.exists().then((exists) async {
    if (exists) {
      await targetDirectory.delete(recursive: true);
      return 0;
    } else {
      return -1;
    }
  }).onError((error, stackTrace) {
    log("An error occurred while deleting $targetDirectory");
    return -1;
  });
}

bool isCacheDirectoryValid(Artifact artifact) {
  var dirPath = artifact.outPath;
  var dir = Directory(dirPath);
  if (!dir.existsSync()) {
    return false;
  }
  var filePath = artifact.file;
  var file = File(p.join(rootPath, 'artifacts', filePath));
  if (!file.existsSync()) {
    throw 'Artifact file missing: $filePath';
  }
  return isNewer(dir, file);
}

bool isNewer(FileSystemEntity newer, FileSystemEntity older) {
  return newer.statSync().modified.isAfter(older.statSync().modified);
}

void _checkGitDir() async {
  var isGitDir = await GitDir.isGitDir(rootPath);
  if (!isGitDir) {
    throw 'the current working directory is not managed by git: $rootPath';
  }
}

String _shorten(String str) {
  return str.length < 200
      ? str
      : str.substring(0, 170) + ' ... ' + str.substring(str.length - 30);
}

Stream<String> _toLineStream(Stream<List<int>> s, Encoding encoding) =>
    s.transform(encoding.decoder).transform(const LineSplitter());

String readTokenFromKeystore(String keyName) {
  var env = Platform.environment;
  var base = env['KOKORO_KEYSTORE_DIR'];
  var id = env['FLUTTER_KEYSTORE_ID'];
  var name = env[keyName];

  var file = File('$base/${id}_$name');
  return file.existsSync() ? file.readAsStringSync() : '';
}

int get devBuildNumber {
  // The dev channel is automatically refreshed weekly, so the build number
  // is just the number of weeks since the last stable release.
  var today = DateTime.now();
  var daysSinceRelease = today.difference(lastReleaseDate).inDays;
  var weekNumber = daysSinceRelease ~/ 7 + 1;
  return weekNumber;
}

String buildVersionNumber(BuildSpec spec) {
  var releaseNo = spec.isDevChannel ? _nextRelease() : spec.release;
  if (releaseNo == null) {
    releaseNo = 'SNAPSHOT';
  } else {
    releaseNo = '$releaseNo.$pluginCount';
    if (spec.isDevChannel) {
      releaseNo += '-dev.$devBuildNumber';
    }
  }
  return releaseNo;
}

String _nextRelease() {
  var current =
      RegExp(r'release_(\d+)').matchAsPrefix(lastReleaseName).group(1);
  var val = int.parse(current) + 1;
  return '$val.0';
}

/// Replicates `tar` --strip-components=N behaviour
///
/// Returns [filePath] with the first [i] components removed.
/// e.g. ('a/b/c/', 1) returns 'b/c'
String stripComponents(String filePath, int i) {
  List<String> components = p.split(filePath);

  if (i < components.length - 1) {
    components.removeRange(0, i);
  } else {
    components.removeRange(0, components.length - 1);
  }

  return p.joinAll(components);
}

/// Returns a File from  [filePath], throw an error if it doesn't exist
File getFileOrThrow(String filePath) {
  var file = File(filePath);

  if (!file.existsSync()) {
    throw FileSystemException("Unable to locate file '${file.path}'");
  }

  return file;
}

/// Extract files from a tar'd [artifact.file] to [artifact.output]
///
/// The [artifact] file location can be specified using [cwd] and
/// [targetDirectory] can be used to set a directory for the extracted files.
///
/// An int is returned to match existing patterns of checking for an error ala
/// the CLI
Future<int> extractTar(Artifact artifact,
    {targetDirectory = '', cwd = ''}) async {
  var artifactPath = p.join(cwd, artifact.file);
  var outputDir = p.join(cwd, targetDirectory, artifact.output);

  log('Extracting $artifactPath to $outputDir');

  try {
    var file = getFileOrThrow(artifactPath);
    var decodedGZipContent = GZipCodec().decode(file.readAsBytesSync());
    var iStream = InputStream(decodedGZipContent);
    var decodedArchive = TarDecoder().decodeBuffer(iStream);

    for (var tarFile in decodedArchive.files) {
      if (!tarFile.isFile) continue; // Don't need to create empty directories

      File(p.join(outputDir, stripComponents(tarFile.name, 1)))
        ..createSync(recursive: true)
        ..writeAsBytesSync(tarFile.content);
    }
  } on FileSystemException catch (e) {
    log(e.osError?.message ?? e.message);
    return -1;
  } catch (e) {
    log('An unknown error occurred: $e');
    return -1;
  }

  return 0;
}

/// Extract files from a zipped [artifactPath]
///
/// The [artifact] file location can be specified using [cwd] and
/// [targetDirectory] can be used to set a directory for the extracted files.
///
/// An int is returned to match existing patterns of checking for an error ala
/// the CLI
Future<int> extractZip(String artifactPath,
    {targetDirectory = '', cwd = '', quite = true}) async {
  var filePath = p.join(cwd, artifactPath);
  var outputPath = p.join(cwd, targetDirectory);

  try {
    File file = getFileOrThrow(filePath);

    Archive zipArchive = ZipDecoder().decodeBytes(file.readAsBytesSync());

    for (ArchiveFile archiveItem in zipArchive.files) {
      // Don't need to create empty directories
      if (!archiveItem.isFile) continue;

      File(p.join(outputPath, archiveItem.name))
        ..createSync(recursive: true)
        ..writeAsBytesSync(archiveItem.content);
    }
  } on FileSystemException catch (e) {
    log(e.osError?.message ?? e.message);
    return -1;
  } catch (e) {
    log('An unknown error occurred while opening $targetDirectory: $e');
    return -1;
  }

  return 0;
}

/// Calls the platform specific gradle wrapper with the provided arguments
Future<int> execLocalGradleCommand(List<String> args) async {
  if (Platform.isWindows) {
    return await exec('.\\gradlew.bat', args);
  } else {
    return await exec('./gradlew', args);
  }
}
