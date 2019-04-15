/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
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
  public static final String NO_WIDGET_MESSAGE = "No widget selected";
  public static final String NOT_RENDERABLE = "Selection is not a renderable widget";

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
  private RenderObject rootRenderObject;
  private final Map<Integer, RenderObject> idToRenderObject = new HashMap<>();

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
        final Dimension renderSize = getRenderSize();
        listener.resized(renderSize.width, renderSize.height);
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
    final JPanel panel = new JPanel();
    panel.setLayout(new MigLayout("", "[grow, center]", "[grow][][grow 200]"));

    panel.add(new JBLabel(message, SwingConstants.CENTER), "cell 0 1");
    clear(panel);
  }

  public void clear(JComponent component) {
    setToolbarTitle(null);

    rootWidgetId = 0;
    rootRenderObject = null;
    idToRenderObject.clear();

    idToOutline.clear();
    outlineToComponent.clear();

    primaryLayer.removeAll();
    primaryLayer.setLayout(new BorderLayout());
    primaryLayer.add(component, BorderLayout.CENTER);

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

    fillIdToRenderObject(renderObject);

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
      final String title = widgetClassName + (stateClassName != null ? " > " + stateClassName : "");
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

  private void fillIdToRenderObject(@NotNull JsonObject renderJson) {
    rootRenderObject = null;
    idToRenderObject.clear();
    for (Map.Entry<String, JsonElement> entry : renderJson.entrySet()) {
      try {
        final int id = Integer.parseInt(entry.getKey());

        final JsonObject widgetObject = (JsonObject)entry.getValue();

        final JsonObject boundsObject = widgetObject.getAsJsonObject("globalBounds");
        final int left = boundsObject.getAsJsonPrimitive("left").getAsInt();
        final int top = boundsObject.getAsJsonPrimitive("top").getAsInt();
        final int width = boundsObject.getAsJsonPrimitive("width").getAsInt();
        final int height = boundsObject.getAsJsonPrimitive("height").getAsInt();
        final Rectangle rect = new Rectangle(left, top, width, height);

        final RenderObject renderObject = new RenderObject(rect);

        if (rootRenderObject == null) {
          rootWidgetId = id;
          rootRenderObject = renderObject;
        }

        idToRenderObject.put(id, renderObject);
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

    final RenderObject renderObject = idToRenderObject.get(id);
    if (renderObject == null) {
      return;
    }

    final Rectangle rect = renderObject.globalBounds;
    final int x = BORDER_WIDTH + rect.x - rootRenderObject.globalBounds.x;
    final int y = BORDER_WIDTH + rect.y - rootRenderObject.globalBounds.y;

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

    final List<FlutterOutline> children = outline.getChildren();
    if (children != null) {
      // In Swing siblings added first are rendered on top of siblings added later. And,
      // Flutter children generally paint back to front, and Swing front to back; ¯\_(ツ)_/¯.
      for (FlutterOutline child : Lists.reverse(children)) {
        renderWidgetOutline(child);
      }
    }

    primaryLayer.add(widget);
  }

  private void setToolbarTitle(String text) {
    toolbarGroup.removeAll();
    toolbarGroup.add(new TitleAction(text == null ? "Preview" : ("Preview: " + text)));
    windowToolbar.updateActionsImmediately();
  }

  public Dimension getRenderSize() {
    final int width = layeredPanel.getWidth();
    final int height = layeredPanel.getHeight();
    for (Component child : layeredPanel.getComponents()) {
      child.setBounds(0, 0, width, height);
    }

    final int renderWidth = width - 2 * BORDER_WIDTH;
    final int renderHeight = height - 2 * BORDER_WIDTH;
    return new Dimension(renderWidth, renderHeight);
  }

  interface Listener {
    void clicked(FlutterOutline outline);

    void doubleClicked(FlutterOutline outline);

    void resized(int width, int height);
  }
}

class RenderObject {
  final Rectangle globalBounds;

  RenderObject(Rectangle globalBounds) {
    this.globalBounds = globalBounds;
  }
}

class TitleAction extends AnAction implements CustomComponentAction {
  TitleAction(String text) {
    super(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    final JPanel panel = new JPanel(new BorderLayout());

    // Add left border to make the title look similar to the tool window title.
    panel.setBorder(BorderFactory.createEmptyBorder(0, JBUI.scale(3), 0, 0));

    final String text = getTemplatePresentation().getText();
    panel.add(new JBLabel(text != null ? text : "", UIUtil.ComponentStyle.SMALL));

    return panel;
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
