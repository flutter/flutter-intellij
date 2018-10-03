/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.server.vmService.FlutterFramesMonitor;
import io.flutter.run.daemon.FlutterApp;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class FPSDisplay {
  static final DecimalFormat df = new DecimalFormat();

  static {
    df.setMaximumFractionDigits(1);
  }

  public static JPanel createJPanelView(Disposable parentDisposable, FlutterApp app) {
    final JPanel panel = new JPanel(new StackLayout());
    panel.setDoubleBuffered(true);
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
    panel.setPreferredSize(new Dimension(-1, HeapDisplay.PANEL_HEIGHT));
    panel.setMaximumSize(new Dimension(Short.MAX_VALUE, HeapDisplay.PANEL_HEIGHT));

    assert app.getVMServiceManager() != null;
    final FlutterFramesMonitor flutterFramesMonitor = app.getVMServiceManager().getFlutterFramesMonitor();

    final FPSPanel fpsPanel = new FPSPanel(flutterFramesMonitor);

    final JPanel labelsPanel = new JPanel();
    labelsPanel.setOpaque(false);
    labelsPanel.setLayout(new BoxLayout(labelsPanel, BoxLayout.X_AXIS));
    panel.add(fpsPanel);
    panel.add(labelsPanel);

    final JBLabel fpsLabel = new JBLabel();
    fpsLabel.setAlignmentY(Component.TOP_ALIGNMENT);
    fpsLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    fpsLabel.setForeground(UIUtil.getLabelDisabledForeground());
    fpsLabel.setOpaque(false);
    fpsLabel.setBorder(JBUI.Borders.empty(4));

    final JBLabel elapsedLabel = new JBLabel("", SwingConstants.RIGHT);
    elapsedLabel.setAlignmentY(Component.TOP_ALIGNMENT);
    elapsedLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    elapsedLabel.setForeground(UIUtil.getLabelDisabledForeground());
    elapsedLabel.setOpaque(false);
    elapsedLabel.setBorder(JBUI.Borders.empty(4));

    labelsPanel.add(fpsLabel);
    labelsPanel.add(Box.createHorizontalGlue());
    labelsPanel.add(elapsedLabel);

    final FlutterFramesMonitor.Listener listener = event -> {
      fpsPanel.update();

      final int ms = Math.round(event.elapsedMicros / 1000.0f);
      elapsedLabel.setText(ms + "ms");
      SwingUtilities.invokeLater(elapsedLabel::repaint);

      fpsLabel.setText(df.format(flutterFramesMonitor.getFPS()) + " FPS");
      SwingUtilities.invokeLater(fpsLabel::repaint);
    };

    flutterFramesMonitor.addListener(listener);
    Disposer.register(parentDisposable, () -> flutterFramesMonitor.removeListener(listener));

    return panel;
  }
}

class FPSPanel extends JPanel {
  private final FlutterFramesMonitor framesMonitor;

  private final Map<FlutterFramesMonitor.FlutterFrameEvent, JComponent> frameWidgets = new HashMap<>();

  private Rectangle lastSavedBounds;

  FPSPanel(FlutterFramesMonitor framesMonitor) {
    this.framesMonitor = framesMonitor;

    setLayout(null);
    final Color color = UIUtil.getLabelDisabledForeground();
    //noinspection UseJBColor
    setForeground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 0x7f));
  }

  public void update() {
    SwingUtilities.invokeLater(this::updateFromFramesMonitor);
  }

  public void doLayout() {
    if (lastSavedBounds != null && !lastSavedBounds.equals(getBounds())) {
      lastSavedBounds = null;

      SwingUtilities.invokeLater(this::updateFromFramesMonitor);
    }
  }

  private static final Stroke STROKE = new BasicStroke(
    0.5f, BasicStroke.CAP_BUTT,
    BasicStroke.JOIN_MITER, 10.0f, new float[]{2.0f, 2.0f}, 0.0f);

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    final Rectangle bounds = getBounds();
    final int height = bounds.height;

    if (height <= 20) {
      return;
    }

    final Graphics2D g2 = (Graphics2D)g;

    final float msPerPixel = (2.0f * 1000000.0f / 60.0f) / height;
    final float y = FlutterFramesMonitor.microsPerFrame / msPerPixel;
    final Stroke oldStroke = g2.getStroke();
    try {
      g2.setStroke(STROKE);
      final Path2D path = new Path2D.Float();
      path.moveTo(0, height - y);
      path.lineTo(bounds.width, height - y);
      g2.draw(path);
    }
    finally {
      g2.setStroke(oldStroke);
    }
  }

  void updateFromFramesMonitor() {
    final Set<FlutterFramesMonitor.FlutterFrameEvent> frames = new HashSet<>(frameWidgets.keySet());

    final Rectangle bounds = getBounds();
    lastSavedBounds = bounds;

    final int height = bounds.height;
    final int inc = height <= 20 ? 1 : 2;

    int x = bounds.width;

    final int widgetWidth = Math.min(Math.max(Math.round(height / 8.0f), 2), 5);

    synchronized (framesMonitor) {
      for (FlutterFramesMonitor.FlutterFrameEvent frame : framesMonitor.frames) {
        if (x + widgetWidth < 0) {
          break;
        }

        x -= (widgetWidth + inc);

        final float msPerPixel = (2.0f * 1000000.0f / 60.0f) / height;
        JComponent widget = frameWidgets.get(frame);
        if (widget != null) {
          frames.remove(frame);
        }
        else {
          widget = new JLabel();
          widget.setOpaque(true);
          widget.setBackground(frame.isSlowFrame() ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
          widget.setToolTipText(FPSDisplay.df.format(frame.elapsedMicros / 1000.0d) + "ms");
          frameWidgets.put(frame, widget);
          add(widget);
        }

        int pixelHeight = Math.round(frame.elapsedMicros / msPerPixel);
        if (pixelHeight > height) {
          pixelHeight = height;
        }
        pixelHeight = Math.max(1, pixelHeight);
        widget.setPreferredSize(new Dimension(widgetWidth, pixelHeight));
        widget.setBounds(x, height - pixelHeight, widgetWidth, pixelHeight);

        // Add a gap between sets of frames.
        if (frame.frameSetStart) {
          x -= widgetWidth;
        }
      }
    }

    if (!frames.isEmpty()) {
      for (FlutterFramesMonitor.FlutterFrameEvent frame : frames) {
        final JComponent widget = frameWidgets.remove(frame);
        remove(widget);
      }
    }
  }
}

class StackLayout implements LayoutManager2 {
  public static final String BOTTOM = "bottom";
  public static final String TOP = "top";
  private final List<Component> components = new LinkedList<>();

  public StackLayout() {
  }

  public void addLayoutComponent(Component comp, Object constraints) {
    synchronized (comp.getTreeLock()) {
      if ("bottom".equals(constraints)) {
        this.components.add(0, comp);
      }
      else if ("top".equals(constraints)) {
        this.components.add(comp);
      }
      else {
        this.components.add(comp);
      }
    }
  }

  public void addLayoutComponent(String name, Component comp) {
    this.addLayoutComponent(comp, "top");
  }

  public void removeLayoutComponent(Component comp) {
    synchronized (comp.getTreeLock()) {
      this.components.remove(comp);
    }
  }

  public float getLayoutAlignmentX(Container target) {
    return 0.5F;
  }

  public float getLayoutAlignmentY(Container target) {
    return 0.5F;
  }

  public void invalidateLayout(Container target) {
  }

  public Dimension preferredLayoutSize(Container parent) {
    synchronized (parent.getTreeLock()) {
      int width = 0;
      int height = 0;

      Dimension size;
      for (final Iterator i$ = this.components.iterator(); i$.hasNext(); height = Math.max(size.height, height)) {
        final Component comp = (Component)i$.next();
        size = comp.getPreferredSize();
        width = Math.max(size.width, width);
      }

      final Insets insets = parent.getInsets();
      width += insets.left + insets.right;
      height += insets.top + insets.bottom;
      return new Dimension(width, height);
    }
  }

  public Dimension minimumLayoutSize(Container parent) {
    synchronized (parent.getTreeLock()) {
      int width = 0;
      int height = 0;

      Dimension size;
      for (final Iterator i$ = this.components.iterator(); i$.hasNext(); height = Math.max(size.height, height)) {
        final Component comp = (Component)i$.next();
        size = comp.getMinimumSize();
        width = Math.max(size.width, width);
      }

      final Insets insets = parent.getInsets();
      width += insets.left + insets.right;
      height += insets.top + insets.bottom;
      return new Dimension(width, height);
    }
  }

  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(2147483647, 2147483647);
  }

  public void layoutContainer(Container parent) {
    synchronized (parent.getTreeLock()) {
      final Insets insets = parent.getInsets();

      final int width = parent.getWidth() - insets.left - insets.right;
      final int height = parent.getHeight() - insets.top - insets.bottom;
      final Rectangle bounds = new Rectangle(insets.left, insets.top, width, height);
      final int componentsCount = this.components.size();

      for (int i = 0; i < componentsCount; ++i) {
        final Component comp = this.components.get(i);
        comp.setBounds(bounds);
        parent.setComponentZOrder(comp, componentsCount - i - 1);
      }
    }
  }
}
