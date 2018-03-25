/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.dartlang.analysis.server.protocol.Element;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: have a way to tell the panel whether it's hidden or shown

// TODO: we'll need to know whether the preview element is stateless widget or a state of a stateful
//       widget; for the 2nd case, we'll need to find the corresponding stateful widget class

// TODO: we want to preview anything in a state, stateful, or stateless class (not
//       just things contained in a build method)

// TODO: we should be bolding stateful and stateless (and state) classes, not build() methods
//       or, show all elements of these classes with some additional emphasis (italic? background color?)

// TODO: we need to take the layer (or z-index?) of the widget into account (see Scaffold and its FAB)

public class PreviewArea {
  public static int BORDER_WIDTH = 0;
  public static final String NOTHING_TO_SHOW = "Nothing to show";
  public static final String NO_WIDGET_MESSAGE = "The selection does not correspond to a widget";

  private static final Color[] widgetColors = new Color[]{
    new JBColor(new Color(0xB8F1FF), new Color(0x546E7A)),
    new JBColor(new Color(0x80FFF2), new Color(0x008975)),
    new JBColor(new Color(0xE1E1E1), new Color(0x757575)),
    new JBColor(new Color(0x80DBFF), new Color(0x0288D1)),
    new JBColor(new Color(0xA0FFCA), new Color(0x607D8B)),
    new JBColor(new Color(0xFFD0B5), new Color(0x8D6E63)),
  };

  private static final Color labelColor = new JBColor(new Color(0x333333), new Color(0xcccccc));

  private final Listener myListener;

  private final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
  private final ActionToolbar windowToolbar;

  private final SimpleToolWindowPanel window;

  private final JLayeredPane layeredPanel = new JLayeredPane();
  private final JPanel primaryLayer = new JPanel();
  private final JPanel handleLayer = new JPanel(null);

  private boolean isBeingRendered = false;

  private final Map<Integer, FlutterOutline> idToOutline = new HashMap<>();

  private int rootWidgetId;
  private Rectangle rootWidgetBounds;
  private final Map<Integer, Rectangle> idToGlobalBounds = new HashMap<>();

  private final Map<FlutterOutline, JComponent> outlineToComponent = new HashMap<>();
  private final List<SelectionEditPolicy> selectionComponents = new ArrayList<>();

  private int widgetIndex = 0;

  public PreviewArea(Listener listener) {
    this.myListener = listener;

    windowToolbar = ActionManager.getInstance().createActionToolbar("PreviewArea", toolbarGroup, true);

    window = new SimpleToolWindowPanel(true, true);
    window.setToolbar(windowToolbar.getComponent());

    primaryLayer.setLayout(new BorderLayout());
    clear(NO_WIDGET_MESSAGE);

    // Layers must be transparent.
    handleLayer.setOpaque(false);

    window.setContent(layeredPanel);
    layeredPanel.add(primaryLayer, Integer.valueOf(0));
    layeredPanel.add(handleLayer, Integer.valueOf(1));

    // Layers must cover the whole root panel.
    layeredPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        final int width = layeredPanel.getWidth();
        final int height = layeredPanel.getHeight();
        for (Component child : layeredPanel.getComponents()) {
          child.setBounds(0, 0, width, height);
        }

        final int renderWidth = width - 2 * BORDER_WIDTH;
        final int renderHeight = height - 2 * BORDER_WIDTH;
        listener.resized(renderWidth, renderHeight);
      }
    });
  }

  /**
   * Return the Swing component of the area.
   */
  public JComponent getComponent() {
    return window;
  }

  public void clear(String message) {
    setToolbarTitle(" ");

    primaryLayer.removeAll();

    if (message != null) {
      primaryLayer.setLayout(new BorderLayout());
      primaryLayer.add(new JBLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
    }

    handleLayer.removeAll();

    window.revalidate();
    window.repaint();
  }

  /**
   * A new outline was received, and we started rendering.
   * Until rendering is finished, the area is inconsistent with the new outline.
   * It should not ignore incoming events and should not send its events to the listener.
   */
  public void renderingStarted() {
    isBeingRendered = true;
  }

  /**
   * Rendering finished, the new outline and rendering information is available.
   * Show the rendered outlines.
   */
  public void show(@NotNull FlutterOutline unitOutline, @NotNull FlutterOutline widgetOutline, @NotNull JsonObject renderObject) {
    isBeingRendered = false;

    idToOutline.clear();
    fillIdToOutline(unitOutline);

    fillIdToGlobalBounds(renderObject);

    primaryLayer.removeAll();
    primaryLayer.setLayout(null);

    final FlutterOutline rootOutline = idToOutline.get(rootWidgetId);
    if (rootOutline == null) {
      clear(NO_WIDGET_MESSAGE);
      return;
    }

    final Element widgetClassElement = widgetOutline.getDartElement();
    if (widgetClassElement != null) {
      final String widgetClassName = widgetClassElement.getName();
      final String stateClassName = widgetOutline.getStateClassName();
      final String title = widgetClassName + (stateClassName != null ? " : " + stateClassName : "");
      setToolbarTitle(title);
    }
    else {
      setToolbarTitle(null);
    }

    outlineToComponent.clear();
    widgetIndex = 0;
    renderWidgetOutline(rootOutline);

    window.revalidate();
    window.repaint();
  }

  public void select(@NotNull List<FlutterOutline> outlines) {
    if (isBeingRendered) {
      return;
    }

    for (SelectionEditPolicy policy : selectionComponents) {
      policy.deactivate();
    }
    selectionComponents.clear();

    for (FlutterOutline outline : outlines) {
      final JComponent widget = outlineToComponent.get(outline);
      if (widget != null) {
        final SelectionEditPolicy selectionPolicy = new SelectionEditPolicy(handleLayer, widget);
        selectionComponents.add(selectionPolicy);
        selectionPolicy.activate();
      }
    }

    primaryLayer.repaint();
  }

  private void fillIdToOutline(@NotNull FlutterOutline outline) {
    if (outline.getId() != null) {
      idToOutline.put(outline.getId(), outline);
    }
    if (outline.getChildren() != null) {
      for (FlutterOutline child : outline.getChildren()) {
        fillIdToOutline(child);
      }
    }
  }

  private void fillIdToGlobalBounds(@NotNull JsonObject renderObject) {
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
      }
      catch (Throwable ignored) {
      }
    }
  }

  private void renderWidgetOutline(@NotNull FlutterOutline outline) {
    final Integer id = outline.getId();
    if (id == null) {
      return;
    }

    final Rectangle rect = idToGlobalBounds.get(id);
    if (rect == null) {
      return;
    }

    final int x = BORDER_WIDTH + rect.x - rootWidgetBounds.x;
    final int y = BORDER_WIDTH + rect.y - rootWidgetBounds.y;

    final JPanel widget = new JPanel(new BorderLayout());
    final DropShadowBorder shadowBorder = new DropShadowBorder();
    widget.setBorder(shadowBorder);
    widget.setOpaque(false);
    final Insets insets = shadowBorder.getBorderInsets(widget);
    widget.setBounds(new Rectangle(x, y, rect.width + insets.right, rect.height + insets.bottom));

    final JPanel inner = new JPanel(new BorderLayout());
    inner.setBackground(widgetColors[widgetIndex % widgetColors.length]);
    widgetIndex++;
    inner.setBorder(BorderFactory.createLineBorder(inner.getBackground().darker()));
    widget.add(inner, BorderLayout.CENTER);

    final JBLabel label = new JBLabel(outline.getClassName());
    label.setBorder(JBUI.Borders.empty(1, 4, 0, 0));
    label.setForeground(labelColor);
    inner.add(label, BorderLayout.NORTH);

    outlineToComponent.put(outline, widget);

    inner.addMouseListener(new MouseAdapter() {
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

    primaryLayer.add(widget);
  }

  private void setToolbarTitle(String text) {
    toolbarGroup.removeAll();
    if (text != null) {
      toolbarGroup.add(new TitleAction(text));
    }
    windowToolbar.updateActionsImmediately();
  }

  interface Listener {
    void clicked(FlutterOutline outline);

    void doubleClicked(FlutterOutline outline);

    void resized(int width, int height);
  }
}

class TitleAction extends AnAction implements CustomComponentAction {
  TitleAction(String text) {
    super(text);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    final String text = getTemplatePresentation().getText();
    return new JLabel(text);
  }
}

class DropShadowBorder extends AbstractBorder {
  @SuppressWarnings("UseJBColor") private static final Color borderColor = new Color(0x7F000000, true);

  public DropShadowBorder() {
  }

  public Insets getBorderInsets(Component component) {
    //noinspection UseDPIAwareInsets
    return new Insets(0, 0, 1, 1);
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(borderColor);
    final int x1 = x + 1;
    final int y1 = y + 1;
    final int x2 = x + width - 1;
    final int y2 = y + height - 1;
    g.drawLine(x1, y2, x2, y2);
    g.drawLine(x2, y1, x2, y2 - 1);
  }
}
