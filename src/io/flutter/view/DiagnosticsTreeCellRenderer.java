/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.google.gson.JsonObject;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.ui.UIUtil;
import io.flutter.editor.FlutterMaterialIcons;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.utils.ColorIconMaker;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.flutter.utils.JsonUtils.getIntMember;

class DiagnosticsTreeCellRenderer extends InspectorColoredTreeCellRenderer {
  // TODO(jacobr): enable this experiment once we make the link actually clickable.
  private static final boolean SHOW_RENDER_OBJECT_PROPERTIES_AS_LINKS = false;

  private final InspectorPanel panel;

  /**
   * Split text into two groups, word characters at the start of a string and all other
   * characters. Skip an <code>-</code> or <code>#</code> between the two groups.
   */
  private final Pattern primaryDescriptionPattern = Pattern.compile("([\\w ]+)[-#]?(.*)");

  private JTree tree;
  private boolean selected;

  final ColorIconMaker colorIconMaker = new ColorIconMaker();

  // Yellow color scheme for showing selecting and matching nodes.
  // TODO(jacobr): consider a scheme where the selected node is blue
  // to be more consistent with regular IntelliJ selected node display.
  // The main problem is in the regular scheme, selected but not focused
  // nodes are grey which makes the correlation between the selection in
  // the two views less obvious.
  private static final JBColor HIGHLIGHT_COLOR = new JBColor(new Color(202, 191, 69), new Color(99, 101, 103));
  private static final JBColor SHOW_MATCH_COLOR = new JBColor(new Color(225, 225, 0), new Color(90, 93, 96));
  private static final JBColor LINKED_COLOR = new JBColor(new Color(255, 255, 220), new Color(70, 73, 76));

  public DiagnosticsTreeCellRenderer(InspectorPanel panel) {
    this.panel = panel;
  }

  public void customizeCellRenderer(
    @NotNull final JTree tree,
    final Object value,
    final boolean selected,
    final boolean expanded,
    final boolean leaf,
    final int row,
    final boolean hasFocus
  ) {
    this.tree = tree;
    this.selected = selected;

    setOpaque(false);
    setIconOpaque(false);
    setTransparentIconBackground(true);

    final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
    if (userObject instanceof String) {
      appendText((String)userObject, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      return;
    }
    if (!(userObject instanceof DiagnosticsNode)) return;
    final DiagnosticsNode node = (DiagnosticsNode)userObject;

    boolean highlight = selected;
    boolean isLinkedChild = false;
    // Highlight nodes that exist in both the details and summary tree to
    // show how the trees are linked together.
    if (!highlight && panel.isHighlightNodesShownInBothTrees()) {
      if (panel.detailsSubtree && panel.isCreatedByLocalProject(node)) {
        isLinkedChild = panel.parentTree.hasDiagnosticsValue(node.getValueRef());
      }
      else {
        if (panel.subtreePanel != null) {
          isLinkedChild = panel.subtreePanel.hasDiagnosticsValue(node.getValueRef());
        }
      }
    }
    if (highlight) {
      setOpaque(true);
      setIconOpaque(false);
      setTransparentIconBackground(true);
      setBackground(HIGHLIGHT_COLOR);
      // TODO(jacobr): consider using UIUtil.getTreeSelectionBackground() instead.
    }
    else if (isLinkedChild || panel.currentShowNode == value) {
      setOpaque(true);
      setIconOpaque(false);
      setTransparentIconBackground(true);
      setBackground(panel.currentShowNode == value ? SHOW_MATCH_COLOR : LINKED_COLOR);
    }

    final String name = node.getName();
    SimpleTextAttributes textAttributes = InspectorPanel.textAttributesForLevel(node.getLevel());
    if (node.isProperty()) {
      // Display of inline properties.
      final String propertyType = node.getPropertyType();
      final JsonObject properties = node.getValuePropertiesJson();
      if (panel.isCreatedByLocalProject(node)) {
        textAttributes = textAttributes
          .derive(SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES.getStyle(), null, null, null);
      }

      if (StringUtils.isNotEmpty(name) && node.getShowName()) {
        appendText(name + node.getSeparator() + " ", textAttributes);
      }

      String description = node.getDescription();
      if (propertyType != null && properties != null) {
        switch (propertyType) {
          case "Color": {
            final int alpha = getIntMember(properties, "alpha");
            final int red = getIntMember(properties, "red");
            final int green = getIntMember(properties, "green");
            final int blue = getIntMember(properties, "blue");

            if (alpha == 255) {
              description = String.format("#%02x%02x%02x", red, green, blue);
            }
            else {
              description = String.format("#%02x%02x%02x%02x", alpha, red, green, blue);
            }

            //noinspection UseJBColor
            final Color color = new Color(red, green, blue, alpha);
            this.addIcon(colorIconMaker.getCustomIcon(color));
            this.setIconOpaque(false);
            this.setTransparentIconBackground(true);
            break;
          }

          case "IconData": {
            final int codePoint = getIntMember(properties, "codePoint");
            if (codePoint > 0) {
              final Icon icon = FlutterMaterialIcons.getIconForHex(String.format("%1$04x", codePoint));
              if (icon != null) {
                this.addIcon(icon);
                this.setIconOpaque(false);
                this.setTransparentIconBackground(true);
              }
            }
            break;
          }
        }
      }

      if (SHOW_RENDER_OBJECT_PROPERTIES_AS_LINKS && propertyType.equals("RenderObject")) {
        textAttributes = textAttributes
          .derive(SimpleTextAttributes.LINK_ATTRIBUTES.getStyle(), JBColor.blue, null, null);
      }

      // TODO(jacobr): custom display for units, iterables, and padding.
      appendText(description, textAttributes);
      if (node.getLevel().equals(DiagnosticLevel.fine) && node.hasDefaultValue()) {
        appendText(" ", textAttributes);
        this.addIcon(panel.defaultIcon);
      }
    }
    else {
      // Non property, regular node case.
      if (StringUtils.isNotEmpty(name) && node.getShowName() && !name.equals("child")) {
        if (name.startsWith("child ")) {
          appendText(name, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          appendText(name, textAttributes);
        }

        if (node.getShowSeparator()) {
          appendText(node.getSeparator(), SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        else {
          appendText(" ", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }

      if (panel.detailsSubtree && panel.isCreatedByLocalProject(node) && !panel.isHighlightNodesShownInBothTrees()) {
        textAttributes = textAttributes.derive(
          SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.getStyle(), null, null, null);
      }

      final String description = node.getDescription();
      final Matcher match = primaryDescriptionPattern.matcher(description);
      if (match.matches()) {
        appendText(" ", SimpleTextAttributes.GRAY_ATTRIBUTES);
        appendText(match.group(1), textAttributes);
        appendText(" ", textAttributes);
        appendText(match.group(2), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (!node.getDescription().isEmpty()) {
        appendText(" ", SimpleTextAttributes.GRAY_ATTRIBUTES);
        appendText(node.getDescription(), textAttributes);
      }

      // TODO(devoncarew): For widgets that are definied in the current project, we could consider
      // appending the relative path to the defining library ('lib/src/foo_page.dart').

      final Icon icon = node.getIcon();
      if (icon != null) {
        setIcon(icon);
      }
    }
  }

  private void appendText(@NotNull String text, @NotNull SimpleTextAttributes attributes) {
    appendFragmentsForSpeedSearch(tree, text, attributes, selected, this);
  }

  // Generally duplicated from SpeedSearchUtil.appendFragmentsForSpeedSearch
  public void appendFragmentsForSpeedSearch(@NotNull JComponent speedSearchEnabledComponent,
                                            @NotNull String text,
                                            @NotNull SimpleTextAttributes attributes,
                                            boolean selected,
                                            @NotNull MultiIconSimpleColoredComponent simpleColoredComponent) {
    final SpeedSearchSupply speedSearch = SpeedSearchSupply.getSupply(speedSearchEnabledComponent);
    if (speedSearch != null) {
      final Iterable<TextRange> fragments = speedSearch.matchingFragments(text);
      if (fragments != null) {
        final Color fg = attributes.getFgColor();
        final Color bg = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        final int style = attributes.getStyle();
        final SimpleTextAttributes plain = new SimpleTextAttributes(style, fg);
        final SimpleTextAttributes highlighted = new SimpleTextAttributes(bg, fg, null, style | SimpleTextAttributes.STYLE_SEARCH_MATCH);
        appendColoredFragments(simpleColoredComponent, text, fragments, plain, highlighted);
        return;
      }
    }
    simpleColoredComponent.append(text, attributes);
  }

  public void appendColoredFragments(final MultiIconSimpleColoredComponent simpleColoredComponent,
                                     final String text,
                                     Iterable<TextRange> colored,
                                     final SimpleTextAttributes plain, final SimpleTextAttributes highlighted) {
    final List<Pair<String, Integer>> searchTerms = new ArrayList<>();
    for (TextRange fragment : colored) {
      searchTerms.add(Pair.create(fragment.substring(text), fragment.getStartOffset()));
    }

    int lastOffset = 0;
    for (Pair<String, Integer> pair : searchTerms) {
      if (pair.second > lastOffset) {
        simpleColoredComponent.append(text.substring(lastOffset, pair.second), plain);
      }

      simpleColoredComponent.append(text.substring(pair.second, pair.second + pair.first.length()), highlighted);
      lastOffset = pair.second + pair.first.length();
    }

    if (lastOffset < text.length()) {
      simpleColoredComponent.append(text.substring(lastOffset), plain);
    }
  }
}
