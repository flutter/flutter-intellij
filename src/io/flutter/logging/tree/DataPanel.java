/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.DiagnosticsTreeStyle;
import io.flutter.utils.JsonUtils;
import io.flutter.view.InspectorColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.flutter.utils.HtmlBuilder.*;

@SuppressWarnings("ALL")
public class DataPanel extends JPanel {

  public static interface ContentListener extends EventListener {
    void hasContent(boolean hasContent);
  }

  /*
   * Swing's support for CSS is limited and doesn't allow for multiple classes so we cons
   * together css styling as a single style attribute.
   *
   * These values are loosely derived from devtools/inspector.css.
   *
   * TODO(pq): add theme-awareness.
   */
  private static class CssStyle {
    static String forNode(DiagnosticsNode node) {
      return attr("style", forLevel(node.getLevel()) + forStyle(node.getStyle()));
    }

    static String forLevel(DiagnosticLevel level) {
      switch (level) {
        case info:
          return "padding-left: 16px; ";
        case error:
          return "color: rgb(244, 67, 54); padding-left: 16px; ";
        case hint:
          return "background-color: #fafad2; padding: 8px 8px 8px 24px; margin: 5px 0; ";
        default:
          return "";
      }
    }

    static String forStyle(DiagnosticsTreeStyle style) {
      switch (style) {
        case error:
          return "background-color: #f97c7c; padding: 12px; margin:5px 0; ";
        default:
          return "";
      }
    }
  }

  class NodeRenderer extends InspectorColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof String) {
        append(value.toString());
      }
      else if (value instanceof DiagnosticsNode) {
        final DiagnosticsNode node = (DiagnosticsNode)value;
        append(value.toString());
        if (!node.isProperty()) {
          final Icon icon = node.getIcon();
          if (icon != null) {
            setIcon(icon);
          }
        }
      }
    }
  }

  // TODO(pq): consider a model shared w/ the inspector tree.
  class NodeModel extends BaseTreeModel<Object> {
    private final DiagnosticsNode node;
    private final String rootContent;

    public NodeModel(DiagnosticsNode node, String rootContent) {
      this.node = node;
      this.rootContent = rootContent;
    }

    @Override
    public List<? extends Object> getChildren(Object parent) {
      if (parent instanceof DiagnosticsNode) {
        return DataPanel.getChildren((DiagnosticsNode)parent);
      }
      if (parent == rootContent) {
        return DataPanel.getChildren(node);
      }
      return null;
    }

    @Override
    public Object getRoot() {
      return rootContent;
    }
  }

  private final Gson gsonHelper = new GsonBuilder().setPrettyPrinting().create();
  private final HTMLEditorKit editorKit;

  private final EventDispatcher<ContentListener>
    dispatcher = EventDispatcher.create(ContentListener.class);

  private DataPanel() {
    setBorder(JBUI.Borders.empty());
    editorKit = new HTMLEditorKit();
  }

  public static DataPanel create() {
    final DataPanel panel = new DataPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    return panel;
  }

  public void onUpdate(@NotNull ContentListener listener) {
    dispatcher.addListener(listener);
  }

  private void linkSelected(URL url) {
    // TODO(pq): handle links
  }

  public void update(Object data) {
    // Remove stale content.
    for (Component c : getComponents()) {
      remove(c);
    }

    boolean showDataPane = false;
    if (data instanceof DiagnosticsNode) {
      showDataPane = updateNodeData((DiagnosticsNode)data);
    }
    else if (data instanceof String && JsonUtils.hasJsonData((String)data)) {
      showDataPane = updateJsonTextData((String)data);
    }

    showPane(showDataPane);
  }

  private void showPane(boolean show) {
    setVisible(show);
    if (show) {
      repaint();
      revalidate();
    }
    dispatcher.getMulticaster().hasContent(show);

    // TODO(pq): set caret position
    // Scroll to start.
    // setCaretPosition(0);
  }

  private boolean updateJsonTextData(String data) {
    @SuppressWarnings("ConstantConditions") final JsonElement jsonElement = new JsonParser().parse((String)data);
    final String text = gsonHelper.toJson(jsonElement);
    if (text.isEmpty()) {
      return false;
    }

    final JEditorPane editorPane = createEditorPane();
    editorPane.setText(html(pre(text)));
    add(editorPane);

    return true;
  }

  private boolean updateNodeData(DiagnosticsNode data) {
    // Header.
    addText(data.toString(), data);

    // Body.
    final List<DiagnosticsNode> properties = data.getInlineProperties();
    for (DiagnosticsNode node : properties) {
      // TODO(pq): refactor to handle edge cases and consider parsing into a list of StyledTexts.
      String contents = "";
      if (node.getName() != null) {
        contents += node.getName();
      }

      final List<DiagnosticsNode> children = getChildren(node);
      if (!children.isEmpty() || node.getDescription() != null) {
        if (!contents.isEmpty()) {
          contents += ": ";
        }
        if (node.getDescription() != null) {
          contents += node.getDescription();
        }
      }

      if (children.isEmpty()) {
        addText(contents, node);
      }
      else {
        addTree(contents, node);
      }
    }
    return true;
  }

  private void addText(String contents, DiagnosticsNode node) {
    final JEditorPane editorPane = createEditorPane();
    editorPane.setText(html(
      div(
        CssStyle.forNode(node),
        span(contents)
      )));
    add(editorPane);
  }

  private void addTree(String rootLabel, DiagnosticsNode node) {
    final JTree tree = new JTree();
    tree.setModel(new NodeModel(node, rootLabel));
    tree.setCellRenderer(new NodeRenderer());
    tree.setShowsRootHandles(true);
    tree.collapseRow(0);

    tree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        tree.clearSelection();
      }
    });

    final NonOpaquePanel panel = new NonOpaquePanel();
    panel.add(tree);
    add(panel);
  }

  private JEditorPane createEditorPane() {
    final JEditorPane editorPane = new JEditorPane();
    editorPane.setEditable(false);
    editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    editorPane.setEditorKit(editorKit);
    editorPane.addHyperlinkListener(e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        linkSelected(e.getURL());
      }
    });

    return editorPane;
  }

  private static List<DiagnosticsNode> getChildren(DiagnosticsNode node) {
    final ArrayList<DiagnosticsNode> children = node.getChildren().getNow(new ArrayList<>());
    return Stream.of(children, node.getInlineProperties()).flatMap(Collection::stream).collect(Collectors.toList());
  }
}
