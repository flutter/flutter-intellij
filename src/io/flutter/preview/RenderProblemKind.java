/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

public enum RenderProblemKind {

  /**
   * There was an exception during preparing for rendering, or rendering the widget.
   */
  EXCEPTION,

  /**
   * The temporary directory was not created.
   */
  NO_TEMPORARY_DIRECTORY,

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
}
