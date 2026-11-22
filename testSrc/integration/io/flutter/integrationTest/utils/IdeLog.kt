/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Reads the IDE's `idea.log` for errors that involve our plugins.
 *
 * `idea.log` lines look like `2026-11-11 10:00:00,123 [  12345] SEVERE - #io.flutter.FlutterInitializer - message`,
 * followed by stack-trace lines that start with whitespace. An *entry* is a log line plus its continuation lines.
 * [severeEntries] returns the `SEVERE` and `ERROR` entries whose text or stack trace mentions one of [markers],
 * minus anything matching the [allowlist].
 *
 * The allowlist is for noise we have looked at and decided is not a plugin bug; every pattern should have a comment
 * saying why. The runner skill treats the allowlist as read-only during repair.
 */
object IdeLog {

  private val entryStart = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3} \[\s*\d+]\s+(\w+)\s+-\s""")

  /** Substrings that identify our code in a message or stack trace. */
  val defaultMarkers = listOf("io.flutter", "com.jetbrains.lang.dart")

  /** Known noise. Keep this short, and justify every entry. */
  val allowlist: List<Regex> = listOf(
    // IDE Starter installs plugins into a fresh sandbox; the platform logs this once when the plugin cache is cold.
    Regex("Plugin descriptor .* is not found in cache"),
    // Dart plugin 506.1.0 on IntelliJ IDEA 2025.2 (IU 252.28238.7): the Dart plugin's coroutines (CoroutineName
    // com.jetbrains.lang.dart.analyzer.DartAnalysisServerService, ...DartToolingDaemonService) die in
    // kotlinx.coroutines' debug probes with "Debug metadata version mismatch. Expected: 1, got 2" because Dart is
    // compiled with a newer Kotlin than the platform's stdlib. No io.flutter frames; the same entries appear in a
    // hand-run IDE (witness session 2026-11-13) with no visible effect. Approved by Cory for T4 certification on 252.
    // Regression of flutter/dart-intellij-third-party#210 / flutter-intellij#8729; see testSrc/integration/specs/FINDINGS.md.
    Regex("Debug metadata version mismatch\\. Expected: 1, got 2"),
  )

  /** Size of [log] now, to pass as `sinceByte` later so only this test's lines are read. */
  fun offset(log: Path): Long = if (log.exists()) Files.size(log) else 0L

  /**
   * `SEVERE`/`ERROR` entries after [sinceByte] that mention any of [markers], not matching the allowlist. Each entry is
   * returned as its first line plus up to [contextLines] continuation lines, joined with newlines.
   */
  fun severeEntries(
    log: Path,
    sinceByte: Long = 0L,
    markers: List<String> = defaultMarkers,
    contextLines: Int = 8,
  ): List<String> {
    if (!log.exists()) return emptyList()
    val text = Files.newInputStream(log).use { input ->
      input.skip(sinceByte)
      input.readBytes().toString(Charsets.UTF_8)
    }
    val entries = mutableListOf<MutableList<String>>()
    for (line in text.lineSequence()) {
      if (entryStart.containsMatchIn(line)) entries += mutableListOf(line)
      else entries.lastOrNull()?.add(line)
    }
    return entries.asSequence()
      .filter { e -> entryStart.find(e.first())?.groupValues?.get(1) in setOf("SEVERE", "ERROR") }
      .filter { e -> e.any { l -> markers.any { l.contains(it) } } }
      .filterNot { e -> allowlist.any { re -> e.any { l -> re.containsMatchIn(l) } } }
      .map { e -> e.take(1 + contextLines).joinToString("\n") }
      .toList()
  }
}
