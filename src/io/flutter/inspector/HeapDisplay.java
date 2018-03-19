/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.perf.HeapMonitor;
import io.flutter.perf.HeapMonitor.HeapListener;
import io.flutter.perf.HeapMonitor.HeapSample;
import io.flutter.perf.HeapMonitor.HeapSpace;
import io.flutter.perf.HeapMonitor.IsolateObject;
import io.flutter.perf.PerfService;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.element.IsolateRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

// TODO(pq): make capacity setting dynamic
public class HeapDisplay extends JPanel {

  // The width of the drawable heap area.
  private static final int HEAP_GRAPH_WIDTH = 60;
  // The height of the drawable heap area.
  private static final int HEAP_GRAPH_HEIGHT = 16;

  // TODO(pq): consider basing this on available space or user configuration.
  private static final boolean SHOW_HEAP_SUMMARY = false;

  @Nullable
  private final SummaryCallback summaryCallback;

  public static class ToolbarComponentAction extends AnAction implements CustomComponentAction, HeapListener, Disposable {
    private final List<JPanel> panels = new ArrayList<>();
    private final List<HeapDisplay> graphs = new ArrayList<>();

    final HeapState heapState = new HeapState(20 * 1000);

    public ToolbarComponentAction(@NotNull Disposable parent, @NotNull FlutterApp app) {
      final PerfService service = app.getPerfService();
      assert service != null;
      service.addListener(this);
      service.start();
      Disposer.register(parent, this);
    }

    @Override
    public void update(AnActionEvent e) {
      // No-op.
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      // No-op.
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {

      // Summary label.
      JBLabel label = null;
      if (SHOW_HEAP_SUMMARY) {
        label = new JBLabel();
        label.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        label.setForeground(getForegroundColor());
      }

      // Graph component.
      final JPanel panel = new JPanel(new GridBagLayout());
      final HeapDisplay graph = new HeapDisplay(SHOW_HEAP_SUMMARY ? label::setText : null);
      panel.add(graph,
                new GridBagConstraints(0, 0, 1, 1, 1, 1,
                                       GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                       JBUI.insets(0, 3, 0, 3), 0, 0));
      // Because there may be multiple toolbars, we need to store (and update) multiple panels.
      panels.add(panel);
      graphs.add(graph);

      final JPanel container = new JPanel(new BorderLayout(5, 0));
      //noinspection ConstantConditions
      if (label != null) {
        container.add(label, BorderLayout.WEST);
      }
      container.add(panel, BorderLayout.CENTER);
      return container;
    }

    @Override
    public void dispose() {
      // TODO(pq): add any cleanup here.
    }

    @Override
    public void handleIsolatesInfo(List<IsolateObject> isolates) {
      heapState.handleIsolatesInfo(isolates);

      for (HeapDisplay graph : graphs) {
        graph.updateFrom(heapState);
      }

      panels.forEach(panel -> SwingUtilities.invokeLater(panel::repaint));
    }

    @Override
    public void handleGCEvent(IsolateRef iIsolateRef, HeapSpace newHeapSpace, HeapSpace oldHeapSpace) {
      heapState.handleGCEvent(iIsolateRef, newHeapSpace, oldHeapSpace);

      for (HeapDisplay graph : graphs) {
        graph.updateFrom(heapState);
      }

      panels.forEach(panel -> SwingUtilities.invokeLater(panel::repaint));
    }
  }

  private static Color getForegroundColor() {
    return UIUtil.getLabelDisabledForeground();
  }

  private static final int TEN_MB = 1024 * 1024 * 10;

  private static final Stroke GRAPH_STROKE = new BasicStroke(2f);

  private static final DecimalFormat df = new DecimalFormat();

  static {
    df.setMaximumFractionDigits(1);
  }

  private interface SummaryCallback {
    void updatedSummary(String summary);
  }

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
        summaryCallback.updatedSummary(printMb(sample.getBytes()));
      }
      else {
        // TODO(pq): if the summary callback is defined, consider displaying a quick pointer or doc in the tooltip.
        final String summary = printMb(sample.getBytes()) + " of " + printMb(heapState.getMaxHeapInBytes());
        setToolTipText(summary);
      }
    }
  }

  private static String printMb(int bytes) {
    return df.format(bytes / (1024 * 1024.0)) + "MB";
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (heapState == null) {
      return;
    }

    final int height = getHeight();
    final int width = getWidth();
    final long now = System.currentTimeMillis();

    final double maxDataSize = (Math.round(heapState.getMaxHeapInBytes() / TEN_MB)) * TEN_MB + TEN_MB;

    final Graphics2D graphics2D = (Graphics2D)g;
    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    final List<Point> graphPoints = new ArrayList<>();
    for (HeapSample sample : heapState.getSamples()) {
      final int x = width - (int)(((double)(now - sample.getSampleTime())) / ((double)heapState.getMaxSampleSizeMs()) * width);
      // TODO(pq): consider a Y offset or scaling.
      final int y = (int)(height * sample.getBytes() / maxDataSize);
      graphPoints.add(new Point(x, y));
    }

    graphics2D.setColor(getForegroundColor());
    graphics2D.setStroke(GRAPH_STROKE);

    for (int i = 0; i < graphPoints.size() - 1; i++) {
      final Point p1 = graphPoints.get(i);
      final Point p2 = graphPoints.get(i + 1);
      // TODO(pq): consider UIUtil.drawLine(...);
      graphics2D.drawLine(p1.x, height - p1.y, p2.x, height - p2.y);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(HEAP_GRAPH_WIDTH, HEAP_GRAPH_HEIGHT);
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

  void addSample(HeapSample sample) {
    samples.add(sample);
  }

  @Override
  public void handleIsolatesInfo(List<IsolateObject> isolates) {
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
