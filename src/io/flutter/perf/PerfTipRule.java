/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.google.common.base.Objects;
import io.flutter.inspector.DiagnosticsNode;
import io.netty.util.collection.IntObjectHashMap;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Rule describing when to generate a performance tip.
 * <p>
 * In the future it would make sense to read the rule definitions in from a
 * JSON file instead of hard coding the rules in this file. The set of rules
 * defined in this file is hardly exaustive and the thresholds for when the
 * rules activate could easily be made looser.
 */
public class PerfTipRule {
  // Unique identifier used for analytics.
  final String analyticsId;
  final String hackFileName;
  final String message;
  final String url;
  final int minSinceNavigate;
  final int minPerSecond;
  final PerfReportKind kind;
  final int priority;
  final int minProblemLocationsInSubtree;
  final Icon icon;
  WidgetPattern pattern;

  PerfTipRule(
    PerfReportKind kind,
    int priority,
    String hackFileName,
    String message,
    String analyticsId,
    String url,
    WidgetPattern pattern,
    int minProblemLocationsInSubtree,
    int minSinceNavigate,
    int minPerSecond,
    Icon icon
  ) {
    this.kind = kind;
    this.priority = priority;
    this.hackFileName = hackFileName;
    this.message = message;
    this.analyticsId = analyticsId;
    this.url = url;
    this.pattern = pattern;
    this.minProblemLocationsInSubtree = minProblemLocationsInSubtree;
    this.minSinceNavigate = minSinceNavigate;
    this.minPerSecond = minPerSecond;
    this.icon = icon;
  }

  static public WidgetPattern matchParent(String name) {
    return new WidgetPattern(name, null);
  }

  static public WidgetPattern matchWidget(String name) {
    return new WidgetPattern(null, name);
  }

  public String getAnalyticsId() {
    return analyticsId;
  }

  public static boolean equalTipRule(PerfTip a, PerfTip b) {
    if (a == null || b == null) {
      return a == b;
    }
    return Objects.equal(a.getRule(), b.getRule());
  }

  public static boolean equivalentPerfTips(List<PerfTip> a, List<PerfTip> b) {
    if (a == null || b == null) {
      return a == b;
    }
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); ++i) {
      if (!equalTipRule(a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  public String getMessage() {
    return message;
  }

  public String getUrl() {
    return url;
  }

  public Icon getIcon() {
    return icon;
  }

  String getHtmlFragmentDescription() {
    return "<p><a href='" + url + "'>" + message + "</a></p>";
  }

  boolean maybeMatches(SummaryStats summary) {
    if (!matchesFrequency(summary)) {
      return false;
    }
    return pattern.widget == null || pattern.widget.equals(summary.getDescription());
  }

  boolean matchesFrequency(SummaryStats summary) {
    return (minSinceNavigate > 0 && summary.getValue(PerfMetric.totalSinceRouteChange) >= minSinceNavigate) ||
           (minPerSecond > 0 && summary.getValue(PerfMetric.pastSecond) >= minPerSecond);
  }


  boolean matches(SummaryStats summary, Collection<DiagnosticsNode> candidates, IntObjectHashMap<SummaryStats> statsInFile) {
    if (!maybeMatches(summary)) {
      return false;
    }
    if (pattern.parentWidget != null) {
      final boolean patternIsStateful = Objects.equal(pattern.parentWidget, "StatefulWidget");
      for (DiagnosticsNode candidate : candidates) {
        if (ancestorMatches(statsInFile, patternIsStateful, candidate, candidate.getParent())) {
          return true;
        }
      }
      return false;
    }
    if (pattern.widget != null) {
      for (DiagnosticsNode candidate : candidates) {
        if (pattern.widget.equals(candidate.getWidgetRuntimeType())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean ancestorMatches(IntObjectHashMap<SummaryStats> statsInFile,
                                  boolean patternIsStateful,
                                  DiagnosticsNode candidate,
                                  DiagnosticsNode parent) {
    if (parent == null) {
      return false;
    }
    if ((parent.isStateful() && patternIsStateful) || (pattern.parentWidget.equals(parent.getWidgetRuntimeType()))) {
      return minProblemLocationsInSubtree <= 1 || minProblemLocationsInSubtree <= countSubtreeMatches(candidate, statsInFile);
    }
    parent = parent.getParent();
    if (parent != null && Objects.equal(parent.getCreationLocation().getPath(), candidate.getCreationLocation().getPath())) {
      // Keep walking up the tree until we hit a different file.
      // TODO(jacobr): this is a bit of an ugly heuristic. Think of a cleaner
      // way of expressing this concept. In reality we could probably force the
      // ancestor to be in the same build method.
      return ancestorMatches(statsInFile, patternIsStateful, candidate, parent);
    }
    return false;
  }

  // TODO(jacobr): this method might be slow in degenerate cases if an extreme
  // number of locations in a source file match a rule. We could memoize match
  // counts to avoid a possible O(n^2) algorithm worst case.
  private int countSubtreeMatches(DiagnosticsNode candidate, IntObjectHashMap<SummaryStats> statsInFile) {
    final int id = candidate.getLocationId();
    int matches = 0;
    if (id >= 0) {
      final SummaryStats stats = statsInFile.get(id);
      if (stats != null && maybeMatches(stats)) {
        matches += 1;
      }
    }
    final ArrayList<DiagnosticsNode> children = candidate.getChildren().getNow(null);
    if (children != null) {
      for (DiagnosticsNode child : children) {
        matches += countSubtreeMatches(child, statsInFile);
      }
    }
    return matches;
  }

  /**
   * Pattern describing expectations for the names of a widget and the name of
   * its parent in the widget tree.
   */
  static public class WidgetPattern {
    final String parentWidget;
    final String widget;

    WidgetPattern(String parentWidget, String widget) {
      this.parentWidget = parentWidget;
      this.widget = widget;
    }
  }
}
