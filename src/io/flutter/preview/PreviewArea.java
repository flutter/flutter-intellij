/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.dartlang.analysis.server.protocol.FlutterOutline;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

// TODO: have a way to tell the panel whether it's hidden or shown

// TODO: we'll need to know whether the preview element is stateless widget or a state of a stateful
//       widget; for the 2nd case, we'll need to find the cooresponding stateful widget class

// TODO: we want to preview anything in a state, stateful, or stateless class (not
//       just things contained in a build method)

// TODO: we should be bolding stateful and stateless (and state) classes, not build() methods
//       or, show all elements of these classes with some additional emphasis (italic? background color?)

public class PreviewArea {
  public static int BORDER_WITH = 5;

  @SuppressWarnings("UseJBColor")
  private static final Color[] pastelColors = new Color[]{
    new Color(255,132,203),
    new Color(168,231,234),
    new Color(251,255,147),
    new Color(143,255,159),
    new Color(193,149,255),
  };

  private final Listener myListener;

  public final JPanel panel = new JPanel();

  private final Map<Integer, FlutterOutline> idToOutline = new HashMap<>();

  private int rootWidgetId;
  private Rectangle rootWidgetBounds;
  private final Map<Integer, Rectangle> idToGlobalBounds = new HashMap<>();

  private int colorIndex = 0;

  public PreviewArea(Listener listener) {
    this.myListener = listener;

    panel.setLayout(new BorderLayout());
    clear();
  }

  public void clear() {
    panel.removeAll();
    panel.setLayout(new BorderLayout());
    panel.add(new JLabel("Nothing to show", SwingConstants.CENTER), BorderLayout.CENTER);
    panel.repaint();
  }

  public void show(FlutterOutline unitOutline, JsonObject renderObject) {
    idToOutline.clear();
    fillIdToOutline(unitOutline);

    fillIdToGlobalBounds(renderObject);

    panel.removeAll();
    panel.setLayout(null);

    final FlutterOutline rootOutline = idToOutline.get(rootWidgetId);
    if (rootOutline == null) {
      clear();
      return;
    }

    colorIndex = 0;
    renderWidgetOutline(rootOutline);
    panel.repaint();
  }

  private void fillIdToOutline(FlutterOutline outline) {
    if (outline.getId() != null) {
      idToOutline.put(outline.getId(), outline);
    }
    if (outline.getChildren() != null) {
      for (FlutterOutline child : outline.getChildren()) {
        fillIdToOutline(child);
      }
    }
  }

  private void fillIdToGlobalBounds(JsonObject renderObject) {
    rootWidgetBounds = null;
    idToGlobalBounds.clear();
    for (Map.Entry<String, JsonElement> entry : renderObject.entrySet()) {
      try {
        final int id = Integer.parseInt(entry.getKey());

        final JsonObject widgetObject = (JsonObject)entry.getValue();

        final JsonObject boundsObject = widgetObject.getAsJsonObject("globalBounds");
        final int left = boundsObject.getAsJsonPrimitive("left").getAsInt();
        final int top = boundsObject.getAsJsonPrimitive("top").getAsInt();
        final int width = boundsObject.getAsJsonPrimitive("width").getAsInt();
        final int height = boundsObject.getAsJsonPrimitive("height").getAsInt();
        final Rectangle rect = new Rectangle(left, top, width, height);

        if (rootWidgetBounds == null) {
          rootWidgetId = id;
          rootWidgetBounds = rect;
        }

        idToGlobalBounds.put(id, rect);
      } catch (Throwable ignored) {}
    }
  }

  private void renderWidgetOutline(FlutterOutline outline) {
    final Integer id = outline.getId();
    if (id != null) {
      Rectangle rect = idToGlobalBounds.get(id);
      if (rect != null) {
        final int x = BORDER_WITH + rect.x - rootWidgetBounds.x;
        final int y = BORDER_WITH + rect.y - rootWidgetBounds.y;
        rect = new Rectangle(x, y, rect.width, rect.height);

        final JPanel widget = new JPanel();
        widget.setBackground(pastelColors[(colorIndex++) % pastelColors.length]);
        widget.setBounds(rect);

        widget.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() >= 2) {
              myListener.doubleClicked(outline);
            }
          }

          @Override
          public void mousePressed(MouseEvent e) {
            myListener.clicked(outline);
          }
        });

        if (outline.getChildren() != null) {
          for (FlutterOutline child : outline.getChildren()) {
            renderWidgetOutline(child);
          }
        }

        panel.add(widget);
      }
    }
  }

  interface Listener {
    void clicked(FlutterOutline outline);
    void doubleClicked(FlutterOutline outline);
  }
}
