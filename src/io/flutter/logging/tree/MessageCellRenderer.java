/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import com.intellij.icons.AllIcons;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.logging.FlutterLogEntry;
import io.flutter.logging.FlutterLogTree;
import io.flutter.logging.text.StyledText;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;

public class MessageCellRenderer extends AbstractEntryCellRender {
  // TODO(pq): use app for link resolution.
  @NotNull
  private final FlutterApp app;

  public MessageCellRenderer(@NotNull FlutterApp app, @NotNull FlutterLogTree.EntryModel entryModel) {
    super(entryModel);
    this.app = app;
    setIconTextGap(JBUI.scale(5));
    setIconOnTheRight(true);
    setIconOpaque(false);
    setTransparentIconBackground(true);
  }

  @Override
  void render(FlutterLogEntry entry) {
    final SimpleTextAttributes style = entryModel.style(entry, STYLE_PLAIN);
    if (style.getBgColor() != null) {
      setBackground(style.getBgColor());
    }

    // TODO(pq): SpeedSearchUtil.applySpeedSearchHighlighting
    // TODO(pq): setTooltipText

    for (StyledText styledText : entry.getStyledText()) {
      append(styledText.getText(), styledText.getStyle() != null ? styledText.getStyle() : style, styledText.getTag());
    }

    // Append data badge
    final Object data = entry.getData();
    // TODO(pq): should make this a simple null check.
    if (data instanceof DiagnosticsNode || (data instanceof String && JsonUtils.hasJsonData((String)data))) {
      // TODO(pq): change to ArrowRight when we're no longer supporting 3.3.
      setIcon(AllIcons.General.ComboArrowRight);
    }
  }
}
