/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.TextEditor;
import io.flutter.FlutterBundle;
import io.flutter.inspector.DiagnosticsNode;
import io.netty.util.collection.IntObjectHashMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static io.flutter.perf.PerfTipRule.matchParent;

/**
 * Linter that determines what performance tips to show.
 * <p>
 * Performance tips are generally derived from a FlutterWidgetPerf object to
 * provide rebuild counts for widgets in the app and the Widget tree expressed
 * as a tree of DiagnositcsNode to give information about the types of
 * ancestors of widgets in the tree.
 */
public class WidgetPerfLinter {
  private static List<PerfTipRule> tips;
  final FlutterWidgetPerf widgetPerf;
  private final WidgetPerfProvider perfProvider;
  private ArrayList<PerfTip> lastTips;
  private Set<Location> lastCandidateLocations;
  private Multimap<Integer, DiagnosticsNode> nodesForLocation;

  WidgetPerfLinter(FlutterWidgetPerf widgetPerf, WidgetPerfProvider perfProvider) {
    this.widgetPerf = widgetPerf;
    this.perfProvider = perfProvider;
  }

  static List<PerfTipRule> getAllTips() {
    if (tips != null) {
      return tips;
    }
    tips = new ArrayList<>();

    tips.add(new PerfTipRule(
      PerfReportKind.rebuild,
      3,
      "perf_diagnosis_demo/lib/clock_demo.dart",
      "Performance considerations of StatefulWidget",
      "statefulWidget",
      FlutterBundle.message("flutter.perf.linter.statefulWidget.url"),
      matchParent("StatefulWidget"),
      4, // Only relevant if the build method is somewhat large.
      50,
      20,
      AllIcons.Actions.IntentionBulb
    ));
    tips.add(new PerfTipRule(
      PerfReportKind.rebuild,
      1,
      "perf_diagnosis_demo/lib/list_demo.dart",
      "Using ListView to load items efficiently",
      "listViewLoad",
      FlutterBundle.message("flutter.perf.linter.listViewLoad.url"),
      matchParent("ListView"),
      1,
      40,
      -1,
      AllIcons.Actions.IntentionBulb
    ));

    tips.add(new PerfTipRule(
      PerfReportKind.rebuild,
      1,
      "perf_diagnosis_demo/lib/spinning_box_demo.dart",
      "Performance optimizations when using AnimatedBuilder",
      "animatedBuilder",
      FlutterBundle.message("flutter.perf.linter.animatedBuilder.url"),
      matchParent("AnimatedBuilder"),
      1,
      50,
      20,
      AllIcons.Actions.IntentionBulb
    ));

    tips.add(new PerfTipRule(
      PerfReportKind.rebuild,
      2,
      "perf_diagnosis_demo/lib/scorecard_demo.dart",
      "Performance considerations of Opacity animations",
      "opacityAnimations",
      FlutterBundle.message("flutter.perf.linter.opacityAnimations.url"),
      matchParent("Opacity"),
      1,
      20,
      8,
      AllIcons.Actions.IntentionBulb
    ));

    return tips;
  }

  public CompletableFuture<ArrayList<PerfTip>> getTipsFor(Set<TextEditor> textEditors) {
    final ArrayList<PerfTipRule> candidateRules = new ArrayList<>();
    final Set<Location> candidateLocations = new HashSet<>();
    final ArrayList<FilePerfInfo> allFileStats = widgetPerf.buildAllSummaryStats(textEditors);
    for (PerfTipRule rule : getAllTips()) {
      for (FilePerfInfo fileStats : allFileStats) {
        for (SummaryStats stats : fileStats.getStats()) {
          if (rule.maybeMatches(stats)) {
            candidateRules.add(rule);
            candidateLocations.add(stats.getLocation());
            break;
          }
        }
      }
    }
    if (candidateRules.isEmpty()) {
      return CompletableFuture.completedFuture(new ArrayList<>());
    }

    if (candidateLocations.equals(lastCandidateLocations)) {
      // No need to load the widget tree again if the list of locations matching
      // rules has not changed.
      return CompletableFuture.completedFuture(computeMatches(candidateRules, allFileStats));
    }

    lastCandidateLocations = candidateLocations;
    return perfProvider.getWidgetTree().thenApplyAsync((treeRoot) -> {
      if (treeRoot != null) {
        nodesForLocation = LinkedListMultimap.create();
        addNodesToMap(treeRoot);
        return computeMatches(candidateRules, allFileStats);
      }
      else {
        return new ArrayList<>();
      }
    });
  }

  private ArrayList<PerfTip> computeMatches(ArrayList<PerfTipRule> candidateRules, ArrayList<FilePerfInfo> allFileStats) {
    final ArrayList<PerfTip> matches = new ArrayList<>();
    final Map<PerfReportKind, IntObjectHashMap<SummaryStats>> maps = new HashMap<>();
    for (FilePerfInfo fileStats : allFileStats) {
      for (SummaryStats stats : fileStats.getStats()) {
        IntObjectHashMap<SummaryStats> map = maps.get(stats.getKind());
        if (map == null) {
          map = new IntObjectHashMap<>();
          maps.put(stats.getKind(), map);
        }
        map.put(stats.getLocation().id, stats);
      }
    }

    for (PerfTipRule rule : candidateRules) {
      final IntObjectHashMap<SummaryStats> map = maps.get(rule.kind);
      if (map != null) {
        final ArrayList<Location> matchingLocations = new ArrayList<>();
        for (FilePerfInfo fileStats : allFileStats) {
          for (SummaryStats stats : fileStats.getStats()) {
            if (nodesForLocation == null) {
              // TODO(jacobr): warn that we need a new widget tree.
              continue;
            }
            assert (stats.getLocation() != null);
            final Collection<DiagnosticsNode> nodes = nodesForLocation.get(stats.getLocation().id);
            if (nodes == null || nodes.isEmpty()) {
              // This indicates a mismatch between the current inspector tree
              // and the stats we are using.
              continue;
            }
            if (rule.matches(stats, nodes, map)) {
              matchingLocations.add(stats.getLocation());
            }
          }
          if (matchingLocations.size() > 0) {
            matches.add(new PerfTip(rule, matchingLocations, 1.0 / rule.priority));
          }
        }
      }
    }
    matches.sort(Comparator.comparingDouble(a -> -a.getConfidence()));
    final Set<PerfTipRule> matchingRules = new HashSet<>();
    final ArrayList<PerfTip> uniqueMatches = new ArrayList<>();
    for (PerfTip match : matches) {
      if (matchingRules.add(match.rule)) {
        uniqueMatches.add(match);
      }
    }
    return uniqueMatches;
  }

  private void addNodesToMap(DiagnosticsNode node) {
    final int id = node.getLocationId();
    if (id >= 0) {
      nodesForLocation.put(id, node);
    }
    final ArrayList<DiagnosticsNode> children = node.getChildren().getNow(null);
    if (children != null) {
      for (DiagnosticsNode child : children) {
        addNodesToMap(child);
      }
    }
  }
}
