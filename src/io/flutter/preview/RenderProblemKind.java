/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

public enum RenderProblemKind {
  /**
   * The offset does not correspond to a widget.
   */
  NO_WIDGET,

  /**
   * The offset corresponds to a widget, but it cannot be rendered.
   */
  NOT_RENDERABLE_WIDGET,

  /**
   * There was a timeout during rendering the widget.
   */
  TIMEOUT,

  /**
   * There was an exception during the JSON response parsing.
   */
  INVALID_JSON,
}
