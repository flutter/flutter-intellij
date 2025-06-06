// Copyright 2025 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'package:github/github.dart';
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

  print('issues: $issueCount (+ $prCount PRs)');
  print(
      'unprioritized: $unpriortizedCount (${(unpriortizedCount / issueCount).toStringAsFixed(2).substring(2)}%)');
  print('created within ...');
  printCreationTimeCounts(unprioritized);
}
