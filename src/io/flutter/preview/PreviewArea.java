/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.editor.*;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.inspector.InspectorStateService;
import net.miginfocom.swing.MigLayout;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Set;

public class PreviewArea implements WidgetViewController.Listener, Disposable, InspectorStateService.Listener {
  public static int BORDER_WIDTH = 0;
  public static final String NOTHING_TO_SHOW = "Run the application.";

  private static final Color labelColor = new JBColor(new Color(0x333333), new Color(0xcccccc));
  public static String NOT_RENDERABLE = "Unable to take a screenshot of the running application.";

  private final Listener myListener;

  private final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
  private final ActionToolbar windowToolbar;

  private final SimpleToolWindowPanel window;

  private final JLayeredPane layeredPanel = new JLayeredPane();
  private final JPanel primaryLayer;

  private final JPanel handleLayer = new JPanel(null);
  private final PreviewViewController preview;
  private final WidgetEditingContext context;
  private final Set<FlutterOutline> outlinesWithWidgets;

  private InspectorService inspectorService;
  private InspectorService.ObjectGroup objectGroup;

  private boolean isDisposed;

  public PreviewArea(Project project, Set<FlutterOutline> outlinesWithWidgets, Listener listener) {
    this.outlinesWithWidgets = outlinesWithWidgets;

    context = new WidgetEditingContext(
      project,
      FlutterDartAnalysisServer.getInstance(project),
      InspectorStateService.getInstance(project),
      EditorPositionService.getInstance(project),
      layeredPanel
    );

    context.inspectorStateService.addListener(this);
    preview = new PreviewViewController(new WidgetViewModelData(context));
    preview.setListener(this);

    primaryLayer = new PreviewViewModelPanel(preview);
    this.myListener = listener;

    windowToolbar = ActionManager.getInstance().createActionToolbar("PreviewArea", toolbarGroup, true);

    window = new SimpleToolWindowPanel(true, true);
    window.setToolbar(windowToolbar.getComponent());

    primaryLayer.setLayout(new BorderLayout());
    clear(NOTHING_TO_SHOW);

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
        preview.setScreenshotBounds(new Rectangle(0, 0, renderSize.width, renderSize.height));
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

    // XXX dispose the model.
    primaryLayer.removeAll();
    primaryLayer.setLayout(new BorderLayout());
    primaryLayer.add(component, BorderLayout.CENTER);

    handleLayer.removeAll();

    window.revalidate();
    window.repaint();
  }

  /**
   * Rendering finished, the new outline and rendering information is available.
   * Show the rendered outlines.
   */
  public void show(DiagnosticsNode node) {

    primaryLayer.removeAll();
    primaryLayer.setLayout(null);

    /* NOT LOADED Yet.
    if (rootOutline == null) {
      clear(NO_WIDGET_MESSAGE);
      return;
    }

     */

    if (node != null) {
      final String description = node.getDescription();
      setToolbarTitle(description);
    }
    else {
      setToolbarTitle(null);
    }

    window.revalidate();
    window.repaint();
  }

  public void select(@NotNull List<FlutterOutline> outlines, Editor editor) {
    if (editor.isDisposed()) return;

    final InspectorService.ObjectGroup group = getObjectGroup();
    if (group != null && outlines.size() > 0) {
      FlutterOutline outline = findWidgetToHighlight(outlines.get(0));
      if (outline == null) return;
      final InspectorService.Location location = InspectorService.Location.outlineToLocation(editor, outline);
      if (location == null) return;
      group.setSelection(location, false , true);
      // TODO(jacobr): update highlighting on preview area immediately?
    }
  }

  /**
   * Find the first descendant of the outline that is a widget as long as there
   * is only one path down the tree that leeds to a widget.
   */
  private FlutterOutline findWidgetToHighlight(FlutterOutline outline) {
    if (outline == null || outline.getClassName() != null) return outline;
    if (!outlinesWithWidgets.contains(outline)) return null;
    FlutterOutline candidate = null;
    for (FlutterOutline child : outline.getChildren()) {
      if (outlinesWithWidgets.contains(child)) {
        if (candidate != null) {
          // It is not unambiguous candidate to show so don't show anything.
          // TODO(jacobr): consider showing multiple locations instead if the
          // inspector on device protocol is enhanced to support that.
          return null;
        }
        candidate = findWidgetToHighlight(child);
      }
    }
    return candidate;
  }


  private void setToolbarTitle(String text) {
    toolbarGroup.removeAll();
    toolbarGroup.add(new TitleAction(text == null ? "Device Mirror" : ("Device Mirror: " + text)));
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

  @Override
  public void forceRender() {
    // XXX clear message?
    window.revalidate();
    window.repaint();
  }

  @Override
  public void dispose() {
    isDisposed = true;
    if (objectGroup != null) {
      objectGroup.dispose();
    }
    context.inspectorStateService.removeListener(this);

    // XXX actually disposer register.??
    Disposer.dispose(this);
  }

  @Override
  public boolean isValid() {
    return !isDisposed;
  }

  @Override
  public void onSelectionChanged(DiagnosticsNode selection) {

  }

  InspectorService.ObjectGroup getObjectGroup() {
    if (objectGroup != null || inspectorService == null) {
      return objectGroup;
    }
    objectGroup = inspectorService.createObjectGroup("preview-area");
    return objectGroup;
  }

  @Override
  public void onInspectorAvailable(InspectorService service) {
    if (service == inspectorService) {
      return;
    }
    inspectorService = service;
    if (objectGroup != null) {
      objectGroup.dispose();
      ;
    }
    objectGroup = null;
  }

  @Override
  public void requestRepaint(boolean force) {

  }

  @Override
  public void onFlutterFrame() {

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
    return new Insets(1, 0, 1, 1);
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
