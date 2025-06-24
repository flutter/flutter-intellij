// Copyright 2025 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:github/github.dart';
import 'package:intl/intl.dart';
import 'package:triage/github.dart';

/// Print a simple triage report.
Future<void> main(List<String> args) async {
  var auth = args.isNotEmpty ? Authentication.withToken(args[0]) : null;
  var issues = await getFlutterPluginIssues(auth: auth);
  var filtered = issues.where((issue) => issue.isIssue);
  var prCount = issues.length - filtered.length;
  var issueCount = filtered.length;
  var unprioritized = filtered.where((issue) => !issue.prioritized);
  var unpriortizedCount = unprioritized.length;
  printCreationTimeCounts(unprioritized, issueCount: issueCount, prCount: prCount, unpriortizedCount: unpriortizedCount);
}


void printCreationTimeCounts(Iterable<Issue> issues, {required int issueCount, required int prCount, required int unpriortizedCount} ) {
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

  print('issues: $issueCount (+ $prCount PRs)');
  var unprioritizedPercentage = '${(unpriortizedCount / issueCount).toStringAsFixed(2).substring(2)}%';
  print(
      'unprioritized: $unpriortizedCount ($unprioritizedPercentage)');
  print('created within ...');

  var timeData = [seven, fourteen, twentyEight, ninety, threeSixty, tenEighty, beyond];

  print(
      '| 1 week | 2 weeks | 1 month | 3 months | 1 year | 3 years | longer |');
  print('| --- | --- | --- | --- | --- | --- | --- |');
  print('| ${timeData.join(' | ')} |');
  print('\n');

  var today = DateFormat('MM/dd/yyyy').format(now);
  print([today, ...timeData, '' /** empty column */,issueCount, unpriortizedCount].join(', '));
}
