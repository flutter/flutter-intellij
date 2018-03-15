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
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import io.flutter.perf.HeapMonitor.HeapListener;
import io.flutter.perf.HeapMonitor.HeapSample;
import io.flutter.perf.HeapMonitor.HeapSpace;
import io.flutter.perf.HeapMonitor.IsolateObject;
import io.flutter.perf.PerfService;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

// TODO(pq): make capacity setting dynamic
// TODO(pq): add label displaying curent/total heap use
// TODO(pq): handle GCs
// TODO(pq): tweak scaling
// TODO(pq): fix duplicate value caching (remove int cache from graph)
public class HeapDisplay extends JPanel {

  public static class ToolbarComponentAction extends AnAction implements CustomComponentAction, HeapListener, Disposable {

    private final List<JPanel> panels = new ArrayList<>();
    private final List<HeapDisplay> graphs = new ArrayList<>();

    // TODO(pq): be smart about setting capacity based on available width.
    final HeapState heapState = new HeapState(30);

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
      final JPanel panel = new JPanel(new GridBagLayout());
      final HeapDisplay graph = new HeapDisplay();

      panel.add(graph,
                new GridBagConstraints(0, 0, 1, 1, 1, 1,
                                       GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                       JBUI.insets(0, 3, 0, 3), 0, 0));
      // Because there may be multiple toolbars, we need to store (and update) multiple panels.
      panels.add(panel);
      graphs.add(graph);
      return panel;
    }

    @Override
    public void dispose() {
      // TODO(pq): add any cleanup here.
    }

    @Override
    public void update(List<IsolateObject> isolates) {
      heapState.update(isolates);
      for (HeapDisplay graph : graphs) {
        // TODO(pq): stop storing points in the graph and just use the samples directly.
        heapState.getSamples().forEach(sample -> graph.addMeasure(scale(heapState, sample)));
      }
      panels.forEach(panel -> SwingUtilities.invokeLater(panel::repaint));
    }
  }

  /**
   * A fixed-length list of captured samples.
   */
  public static class HeapSamples {
    private final LinkedList<HeapSample> samples = new LinkedList<>();
    private final int capacity;

    HeapSamples(int size) {
      this.capacity = size;
    }

    void add(HeapSample sample) {
      if (samples.size() == capacity) {
        samples.removeFirst();
      }
      samples.add(sample);
    }

    int size() {
      return samples.size();
    }

    HeapSample get(int index) {
      return samples.get(index);
    }
  }

  public static class HeapState implements HeapListener {
    // Running count of the max heap (in bytes).
    private int heapMaxInBytes;

    private final HeapSamples samples;

    HeapState(int sampleCapacity) {
      samples = new HeapSamples(sampleCapacity);
    }

    public Iterable<HeapSample> getSamples() {
      return samples.samples;
    }

    public int getMaxHeapInBytes() {
      return heapMaxInBytes;
    }

    void addSample(HeapSample sample) {
      samples.add(sample);
    }

    @Override
    public void update(List<IsolateObject> isolates) {
      int current = 0;
      int total = 0;

      for (IsolateObject isolate : isolates) {
        for (HeapSpace heap : isolate.getHeaps()) {
          current += heap.getUsed() + heap.getExternal();
          total += heap.getCapacity() + heap.getExternal();
        }
      }

      heapMaxInBytes = total;
      addSample(new HeapSample(current, false));
    }
  }

  private static final int TEN_MB = 1024 * 1024 * 10;
  private static final double GRAPH_PIXEL_HIGHT = 16.0;

  private static int scale(HeapState state, HeapSample sample) {
    final double maxDataSize = (Math.round(state.getMaxHeapInBytes() / TEN_MB)) * TEN_MB + TEN_MB;
    return (int)(GRAPH_PIXEL_HIGHT * sample.getBytes() / maxDataSize);
  }

  // TODO(pq): remove in favor of just using the HeapSamples directly.
  static class IntCache {
    final LinkedList<Integer> list = new LinkedList<>();
    private final int capacity;

    IntCache(int size) {
      this.capacity = size;
    }

    void add(int v) {
      if (list.size() == capacity) {
        list.removeFirst();
      }
      list.add(v);
    }

    int size() {
      return list.size();
    }

    int get(int index) {
      return list.get(index);
    }
  }

  private static final int PREFFERED_WIDTH = 60;
  private static final int PREFFERED_HEIGHT = 16;

  private static final Color GRAPH_COLOR = JBColor.LIGHT_GRAY;
  private static final Stroke GRAPH_STROKE = new BasicStroke(2f);

  private final IntCache measures = new IntCache(30);

  public HeapDisplay() {
    setVisible(true);
  }

  public void addMeasure(int measure) {
    measures.add(measure);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    final Graphics2D graphics2D = (Graphics2D)g;
    graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    final List<Point> graphPoints = new ArrayList<>();
    for (int i = 0; i < measures.size(); i++) {
      graphPoints.add(new Point(i * 3, measures.get(i)));
    }

    graphics2D.setColor(GRAPH_COLOR);
    graphics2D.setStroke(GRAPH_STROKE);

    for (int i = 0; i < graphPoints.size() - 1; i++) {
      final Point p1 = graphPoints.get(i);
      final Point p2 = graphPoints.get(i + 1);
      graphics2D.drawLine(p1.x, p1.y, p2.x, p2.y);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(PREFFERED_WIDTH, PREFFERED_HEIGHT);
  }
}