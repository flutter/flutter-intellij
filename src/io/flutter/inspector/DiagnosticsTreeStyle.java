/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

/**
 * Styles for displaying a node in a [DiagnosticsNode] tree.
 * <p>
 * Generally these styles are more important for ASCII art rendering than IDE
 * rendering with the exception of DiagnosticsTreeStyle.offstage which should
 * be used to trigger custom rendering for offstage children perhaps using dashed
 * lines or by graying out offstage children.
 * <p>
 * See also: [DiagnosticsNode.toStringDeep] from https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/foundation/diagnostics.dart
 * which dumps text art trees for these  styles.
 */
public enum DiagnosticsTreeStyle {
  /**
   * Render the tree on a single line without showing children acting like the
   * line is a header.
   */
  headerLine,

  /**
   * Render the tree without indenting children at all.
   */
  flat,

  /**
   * Style for displaying content describing an error.
   */
  error,

  /**
   * Sparse style for displaying trees.
   */
  sparse,

  /**
   * Connects a node to its parent typically with a dashed line.
   */
  offstage,

  /**
   * Slightly more compact version of the [sparse] style.
   * <p>
   * Differences between dense and spare are typically only relevant for ASCII
   * art display of trees and not for IDE display of trees.
   */
  dense,

  /**
   * Style that enables transitioning from nodes of one style to children of
   * another.
   * <p>
   * Typically doesn't matter for IDE support as all styles are typically
   * all styles are compatible as far as IDE display is concerned.
   */
  transition,

  /**
   * Suggestion to render the tree just using whitespace without connecting
   * parents to children using lines.
   */
  whitespace,

  /**
   * Render the tree on a single line without showing children.
   */
  singleLine,

  /**
   * Render the tree on a single line with the name and value on separate
   * lines.
   */
  indentedSingleLine,

  /**
   *
   */
  shallow,

  /**
   * Render only the immediate properties of a node instead of the full tree.
   */
  truncateChildren,
}
