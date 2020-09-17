/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.tree;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tree.BaseTreeModel;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.DiagnosticsTreeStyle;
import io.flutter.logging.FlutterLogEntry;
import io.flutter.logging.FlutterLogEntryParser.LineHandler;
import io.flutter.logging.text.StyledText;
import io.flutter.utils.JsonUtils;
import io.flutter.view.InspectorColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
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
    private final List<Filter> filters;

    public NodeRenderer(List<Filter> filters) {
      super();
      this.filters = filters;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof String) {
        appendStyledText(value.toString());
      }
      else if (value instanceof DiagnosticsNode) {
        final DiagnosticsNode node = (DiagnosticsNode)value;
        if (!node.isProperty()) {
          final Icon icon = node.getIcon();
          if (icon != null) {
            setIcon(icon);
          }
        }

        appendStyledText(value.toString());
      }
    }

    void appendStyledText(@NotNull String line) {
      final LineHandler lineHandler = new LineHandler(filters, null);
      final List<StyledText> styledTexts = lineHandler.parseLineStyle(line);
      for (StyledText styledText : styledTexts) {
        append(styledText.getText(), styledText.getStyle() != null ? styledText.getStyle() : REGULAR_ATTRIBUTES, styledText.getTag());
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

  static class MouseHandler extends MouseAdapter implements MouseMotionListener {
    @NotNull
    private final JTree tree;
    @NotNull
    private final Project project;

    MouseHandler(@NotNull JTree tree, @NotNull Project project) {
      this.tree = tree;
      this.project = project;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      final Cursor cursor = getTagForPosition(e) instanceof HyperlinkInfo
                            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            : Cursor.getDefaultCursor();
      tree.setCursor(cursor);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      final Object tag = getTagForPosition(e);
      if (tag instanceof HyperlinkInfo) {
        ((HyperlinkInfo)tag).navigate(project);
      }
    }

    private Object getTagForPosition(MouseEvent e) {
      final JTree tree = (JTree)e.getSource();
      final TreeCellRenderer cellRenderer = tree.getCellRenderer();
      if (cellRenderer instanceof InspectorColoredTreeCellRenderer) {
        final InspectorColoredTreeCellRenderer renderer = (InspectorColoredTreeCellRenderer)cellRenderer;
        final TreePath treePath = tree.getPathForLocation(e.getX(), e.getY());
        final Rectangle pathBounds = tree.getPathBounds(treePath);
        if (pathBounds != null) {
          final int x = e.getX() - pathBounds.x;
          return renderer.getFragmentTagAt(x);
        }
      }
      return null;
    }
  }

  private final Gson gsonHelper = new GsonBuilder().setPrettyPrinting().create();
  private final HTMLEditorKit editorKit;

  private final EventDispatcher<ContentListener>
    dispatcher = EventDispatcher.create(ContentListener.class);
  private final Project project;
  private FlutterLogEntry entry;

  private DataPanel(Project project) {
    this.project = project;
    setBorder(JBUI.Borders.empty());
    editorKit = new HTMLEditorKit();
  }

  public static DataPanel create(@NotNull Project project) {
    final DataPanel panel = new DataPanel(project);
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    return panel;
  }

  public void onUpdate(@NotNull ContentListener listener) {
    dispatcher.addListener(listener);
  }

  private void linkSelected(URL url) {
    // TODO(pq): handle html links
  }

  public void update(@NotNull FlutterLogEntry entry) {
    // Avoid unneeded updates.
    if (entry == this.entry) {
      return;
    }

    this.entry = entry;
    final Object data = entry.getData();

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
    @SuppressWarnings("ConstantConditions") final JsonElement jsonElement = JsonUtils.parseString((String)data);
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
    tree.setCellRenderer(new NodeRenderer(entry.getFilters()));
    tree.setShowsRootHandles(true);
    tree.collapseRow(0);

    tree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        tree.clearSelection();
      }
    });
    tree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        selectionChanged(tree);
      }
    });

    final MouseHandler mouseHandler = new MouseHandler(tree, project);
    tree.addMouseListener(mouseHandler);
    tree.addMouseMotionListener(mouseHandler);

    final NonOpaquePanel panel = new NonOpaquePanel();
    panel.add(tree);
    add(panel);
  }

  private void selectionChanged(@NotNull JTree tree) {
    final Object pathComponent = tree.getLastSelectedPathComponent();
    if (pathComponent instanceof DiagnosticsNode) {
      final DiagnosticsNode diagnostics = (DiagnosticsNode)pathComponent;
      diagnostics.setSelection(diagnostics.getValueRef(), false);
    }
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
