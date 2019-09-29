/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;

public interface WidgetViewModeInterface extends CustomHighlighterRenderer,
                                                 EditorMouseEventService.Listener
{

}
