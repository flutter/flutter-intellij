// Copyright 2025 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:github/github.dart';

Future<List<Issue>> getFlutterPluginIssues({Authentication? auth}) async {
  var github = auth != null ? GitHub(auth: auth) : GitHub();
  var slug = RepositorySlug('flutter', 'flutter-intellij');
  try {
    return github.issues.listByRepo(slug).toList();
  } on Exception catch (e) {
    print(e);
    return Future.value(<Issue>[]);
  }
}

void printCreationTimeCounts(Iterable<Issue> issues) {
  var seven = 0;
  var fourteen = 0;
  var twentyEight = 0;
  var ninety = 0;
  var threeSixty = 0;
  var tenEighty = 0;
  var beyond = 0;

  var now = DateTime.timestamp();
  for (var issue in issues) {
    var updated = issue.createdAt!;
    var daysSinceUpdate = now.difference(updated).inDays;
    switch (daysSinceUpdate) {
      case <= 7:
        ++seven;
      case > 7 && <= 14:
        ++fourteen;
      case > 14 && <= 28:
        ++twentyEight;
      case > 28 && <= 90:
        ++ninety;
      case > 90 && <= 360:
        ++threeSixty;
      case > 360 && <= 1080:
        ++tenEighty;
      case _:
        ++beyond;
    }
  }

  print(
      '| 1 week | 2 weeks | 1 month | 3 months | 1 year | 3 years | longer |');
  print('| --- | --- | --- | --- | --- | --- | --- |');
  print(
      '| $seven | $fourteen | $twentyEight | $ninety | $threeSixty | $tenEighty | $beyond |');
}

extension IssueExtension on Issue {
  bool get prioritized =>
      labels.map((l) => l.name).any((n) => n.startsWith('P'));

  bool get isPR => pullRequest != null;

  bool get isIssue => !isPR;
}
