/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.utils.LabelInput;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ViewUtils {
  public @NotNull JBLabel warningLabel(String warning) {
    final JBLabel descriptionLabel = new JBLabel(wrapWithHtml(warning));
    descriptionLabel.setBorder(JBUI.Borders.empty(5));
    descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
    descriptionLabel.setForeground(Color.RED);
    return descriptionLabel;
  }

  public void presentLabel(ToolWindow toolWindow, String text) {
    final JBLabel label = new JBLabel(wrapWithHtml(text), SwingConstants.CENTER);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    replacePanelLabel(toolWindow, label);
  }

  /**
   * Displays multiple labels vertically centered in the tool window.
   *
   * @param toolWindow The target tool window.
   * @param labels     A list of strings to display as labels.
   */
  public void presentLabels(@NotNull ToolWindow toolWindow, @NotNull List<String> labels) {
    final JPanel labelsPanel = new JPanel(new GridLayout(0, 1));
    labelsPanel.setBorder(JBUI.Borders.empty()); // Use padding on individual labels if needed

    for (String text : labels) {
      final JBLabel label = new JBLabel(wrapWithHtml(text), SwingConstants.CENTER);
      label.setForeground(UIUtil.getLabelDisabledForeground());
      // Add padding to each label for spacing
      label.setBorder(JBUI.Borders.empty(2, 0));
      labelsPanel.add(label);
    }

    // Use VerticalFlowLayout to center the block of labels vertically
    final JPanel centerPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    centerPanel.add(labelsPanel);

    replacePanelLabel(toolWindow, centerPanel);
  }


  public void presentClickableLabel(ToolWindow toolWindow, List<LabelInput> labels) {
    final JPanel panel = new JPanel(new GridLayout(0, 1));

    for (LabelInput input : labels) {
      if (input.listener == null) {
        final JLabel descriptionLabel = new JLabel(wrapWithHtml(input.text));
        descriptionLabel.setBorder(JBUI.Borders.empty(5));
        descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(descriptionLabel, BorderLayout.NORTH);
      }
      else {
        final LinkLabel<String> linkLabel = new LinkLabel<>(wrapWithHtml(input.text), null);
        linkLabel.setBorder(JBUI.Borders.empty(5));
        linkLabel.setListener(input.listener, null);
        linkLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(linkLabel, BorderLayout.SOUTH);
      }
    }

    final JPanel center = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE));
    center.add(panel);
    replacePanelLabel(toolWindow, center);
  }

  public void replacePanelLabel(ToolWindow toolWindow, JComponent label) {
    replacePanelLabel(toolWindow.getContentManager(), label);
  }

  public void replacePanelLabel(ContentManager contentManager, JComponent label) {
    OpenApiUtils.safeInvokeLater(() -> {
      if (contentManager.isDisposed()) {
        return;
      }

      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(label, BorderLayout.CENTER);
      final Content content = contentManager.getFactory().createContent(panel, null, false);
      contentManager.removeAllContents(true);
      contentManager.addContent(content);
    });
  }

  private String wrapWithHtml(String text) {
    // Wrapping with HTML tags ensures the text wraps and is not cut off.
    return "<html>" + text + "</html>";
  }
}
