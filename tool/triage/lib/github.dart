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

extension IssueExtension on Issue {
  bool get prioritized =>
      labels.map((l) => l.name).any((n) => n.startsWith('P'));

  bool get isPR => pullRequest != null;

  bool get isIssue => !isPR;
}
