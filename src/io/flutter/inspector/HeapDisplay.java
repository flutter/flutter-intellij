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
import io.flutter.perf.HeapMonitor;
import io.flutter.perf.HeapMonitor.HeapListener;
import io.flutter.perf.HeapMonitor.HeapSample;
import io.flutter.perf.HeapMonitor.HeapSpace;
import io.flutter.perf.HeapMonitor.IsolateObject;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

public class HeapDisplay extends JPanel {
  static final int PANEL_HEIGHT = 100;

  public static JPanel createJPanelView(Disposable parentDisposable, FlutterApp app) {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP | SideBorder.BOTTOM));
    panel.setPreferredSize(new Dimension(-1, PANEL_HEIGHT));
    panel.setMaximumSize(new Dimension(Short.MAX_VALUE, HeapDisplay.PANEL_HEIGHT));

    final JBLabel rssLabel = new JBLabel();
    rssLabel.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    rssLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    rssLabel.setForeground(UIUtil.getLabelDisabledForeground());
    rssLabel.setBorder(JBUI.Borders.empty(4));
    final JBLabel heapLabel = new JBLabel("", SwingConstants.RIGHT);
    heapLabel.setAlignmentY(Component.BOTTOM_ALIGNMENT);
    heapLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    heapLabel.setForeground(UIUtil.getLabelDisabledForeground());
    heapLabel.setBorder(JBUI.Borders.empty(4));

    final HeapState heapState = new HeapState(60 * 1000);
    final HeapDisplay graph = new HeapDisplay(state -> {
      rssLabel.setText(heapState.getRSSSummary());
      heapLabel.setText(heapState.getHeapSummary());

      SwingUtilities.invokeLater(rssLabel::repaint);
      SwingUtilities.invokeLater(heapLabel::repaint);
    });

    graph.setLayout(new BoxLayout(graph, BoxLayout.X_AXIS));
    graph.add(rssLabel);
    graph.add(Box.createHorizontalGlue());
    graph.add(heapLabel);

    panel.add(graph, BorderLayout.CENTER);

    final HeapListener listener = new HeapListener() {
      @Override
      public void handleIsolatesInfo(VM vm, List<IsolateObject> isolates) {
        SwingUtilities.invokeLater(() -> {
          heapState.handleIsolatesInfo(vm, isolates);
          graph.updateFrom(heapState);
          panel.repaint();
        });
      }

      @Override
      public void handleGCEvent(IsolateRef iIsolateRef, HeapSpace newHeapSpace, HeapSpace oldHeapSpace) {
        SwingUtilities.invokeLater(() -> {
          heapState.handleGCEvent(iIsolateRef, newHeapSpace, oldHeapSpace);
          graph.updateFrom(heapState);
          panel.repaint();
        });
      }
    };

    assert app.getPerfService() != null;
    app.getPerfService().addHeapListener(listener);
    Disposer.register(parentDisposable, () -> app.getPerfService().removeHeapListener(listener));

    return panel;
  }

  private static Color getForegroundColor() {
    return UIUtil.getLabelDisabledForeground();
  }

  private static final int TEN_MB = 1024 * 1024 * 10;

  private static final Stroke GRAPH_STROKE = new BasicStroke(2f);

  private interface SummaryCallback {
    void updatedSummary(HeapState state);
  }

  @Nullable
  private final SummaryCallback summaryCallback;

  private @Nullable HeapState heapState;

  public HeapDisplay(@Nullable SummaryCallback summaryCallback) {
    this.summaryCallback = summaryCallback;

    setVisible(true);
  }

  private void updateFrom(HeapState state) {
    this.heapState = state;

    if (!heapState.getSamples().isEmpty()) {
      final HeapSample sample = heapState.getSamples().get(0);
      if (summaryCallback != null) {
        summaryCallback.updatedSummary(state);
      }
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (heapState == null) {
      return;
    }

    final int height = getHeight() - 1;
    final int width = getWidth();
    final long now = System.currentTimeMillis();

    final int maxDataSize = Math.round(heapState.getMaxHeapInBytes() / TEN_MB) * TEN_MB + TEN_MB;

    final Graphics2D graphics2D = (Graphics2D)g;
    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    graphics2D.setColor(getForegroundColor());
    graphics2D.setStroke(GRAPH_STROKE);

    Path2D path = null;

    for (HeapSample sample : heapState.getSamples()) {
      final double x = width - (((double)(now - sample.getSampleTime())) / ((double)heapState.getMaxSampleSizeMs()) * width);
      final double y = (double)height * sample.getBytes() / maxDataSize;

      if (path == null) {
        path = new Path2D.Double();
        path.moveTo(x, height - y + 1);
      }
      else {
        path.lineTo(x, height - y + 1);
      }
    }

    graphics2D.draw(path);
  }
}

/**
 * A fixed-length list of captured samples.
 */
class HeapSamples {
  final LinkedList<HeapSample> samples = new LinkedList<>();
  final int maxSampleSizeMs;

  HeapSamples(int maxSampleSizeMs) {
    this.maxSampleSizeMs = maxSampleSizeMs;
  }

  void add(HeapMonitor.HeapSample sample) {
    samples.add(sample);

    // Leave a little bit extra in the samples we trim off.
    final long oldestTime = System.currentTimeMillis() - maxSampleSizeMs - 2000;
    while (!samples.isEmpty() && samples.get(0).getSampleTime() < oldestTime) {
      samples.removeFirst();
    }
  }
}

class HeapState implements HeapListener {
  private static final DecimalFormat df = new DecimalFormat();
  private static final DecimalFormat df1 = new DecimalFormat();

  static {
    df.setMaximumFractionDigits(0);
    df1.setMaximumFractionDigits(1);
  }

  private int rssBytes;

  // Running count of the max heap (in bytes).
  private int heapMaxInBytes;

  private final HeapSamples samples;
  private final Map<String, List<HeapSpace>> isolateHeaps = new HashMap<>();

  HeapState(int maxSampleSizeMs) {
    samples = new HeapSamples(maxSampleSizeMs);
  }

  public int getMaxSampleSizeMs() {
    return samples.maxSampleSizeMs;
  }

  public List<HeapSample> getSamples() {
    return samples.samples;
  }

  public int getMaxHeapInBytes() {
    int max = heapMaxInBytes;

    for (HeapSample sample : samples.samples) {
      max = Math.max(max, sample.getBytes());
    }

    return max;
  }

  private static String printMb(int bytes) {
    return df.format(bytes / (1024 * 1024.0)) + "MB";
  }

  private static String printMb1(int bytes) {
    return df1.format(bytes / (1024 * 1024.0)) + "MB";
  }

  public String getRSSSummary() {
    return printMb(rssBytes) + " RSS";
  }

  public String getHeapSummary() {
    return printMb1(samples.samples.getLast().getBytes()) + " of " + printMb1(heapMaxInBytes);
  }

  public String getSimpleHeapSummary() {
    return printMb(samples.samples.getLast().getBytes());
  }

  void addSample(HeapSample sample) {
    samples.add(sample);
  }

  @Override
  public void handleIsolatesInfo(VM vm, List<IsolateObject> isolates) {
    int current = 0;
    int total = 0;

    isolateHeaps.clear();

    for (IsolateObject isolate : isolates) {
      isolateHeaps.put(isolate.getId(), isolate.getHeaps());

      for (HeapSpace heap : isolate.getHeaps()) {
        current += heap.getUsed() + heap.getExternal();
        total += heap.getCapacity() + heap.getExternal();
      }
    }

    rssBytes = vm.getJson().get("_currentRSS").getAsInt();
    heapMaxInBytes = total;

    addSample(new HeapSample(current, false));
  }

  @Override
  public void handleGCEvent(IsolateRef isolateRef, HeapSpace newHeapSpace, HeapSpace oldHeapSpace) {
    int current = 0;

    isolateHeaps.put(isolateRef.getId(), new ArrayList<>(Arrays.asList(newHeapSpace, oldHeapSpace)));

    for (List<HeapSpace> heaps : isolateHeaps.values()) {
      for (HeapSpace heap : heaps) {
        current += heap.getUsed() + heap.getExternal();
      }
    }

    addSample(new HeapSample(current, true));
  }
}
