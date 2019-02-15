/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;
import io.flutter.FlutterMessages;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class FlutterSampleActionsPanel extends JPanel {

  protected final JBLabel myLabel = new JBLabel();
  protected final JBLabel goLink;

  @NotNull private final List<FlutterSample> samples;
  @NotNull private final Project project;
  protected Color myBackgroundColor;
  protected ColorKey myBackgroundColorKey;

  final JEditorPane descriptionText;

  // Combo or label.
  JComponent sampleSelector;

  FlutterSampleActionsPanel(@NotNull List<FlutterSample> samples, @NotNull Project project) {
    super(new BorderLayout());
    this.samples = samples;
    this.project = project;

    myBackgroundColorKey = EditorColors.GUTTER_BACKGROUND;

    myLabel.setIcon(FlutterIcons.Flutter);
    myLabel.setText("Open sample project:");
    myLabel.setBorder(JBUI.Borders.emptyRight(5));

    goLink = createLinkLabel("Go...", this::doCreate);
    goLink.setBorder(JBUI.Borders.empty(1, 8, 0, 0));

    sampleSelector = setupSelectorComponent();

    descriptionText = new JEditorPane();
    descriptionText.setBackground(getBackground());
    descriptionText.setContentType("text/html");
    descriptionText.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    descriptionText.setBorder(JBUI.Borders.emptyLeft(3));

    final FlutterSample selectedSample = getSelectedSample();
    descriptionText.setText(selectedSample.getShortHtmlDescription());
    descriptionText.setEditable(false);

    descriptionText.setFont(getFont());
    descriptionText.setForeground(JBColor.gray);

    setupPanel();
  }

  private static JBLabel createLinkLabel(@NotNull String text, @NotNull Runnable onClick) {
    // Standard hyperlinks were rendering oddly on 2018.3, so we create our own.
    // See: https://github.com/flutter/flutter-intellij/issues/3197
    final JBLabel label  = new JBLabel(text);
    label.setForeground(JBColor.blue);
    label.setCursor(new Cursor(Cursor.HAND_CURSOR));
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onClick.run();
      }
    });
    return label;
  }

  JComponent setupSelectorComponent() {
    if (samples.size() == 1) {
      return new JBLabel(samples.get(0).getDisplayLabel());
    }

    final FlutterSampleComboBox sampleCombo = new FlutterSampleComboBox(samples);
    sampleCombo.addActionListener(e -> updateSelection(sampleCombo.getSelectedItem()));

    return sampleCombo;
  }

  private void updateSelection(@NotNull FlutterSample item) {
    descriptionText.setText(item.getShortHtmlDescription());
  }

  private void setupPanel() {
    // LABEL | SELECTOR | LINK...
    final JPanel subPanel = new NonOpaquePanel(new BorderLayout(0, 10));
    subPanel.add(BorderLayout.WEST, myLabel);
    subPanel.add(BorderLayout.CENTER, sampleSelector);
    subPanel.add(BorderLayout.EAST, goLink);
    final JPanel selectionPanel = new NonOpaquePanel(new BorderLayout(10, 0));
    selectionPanel.add(BorderLayout.NORTH, subPanel);
    add(BorderLayout.WEST, selectionPanel);

    // DESCRIPTION
    final JPanel descriptionPanel = new NonOpaquePanel(new BorderLayout());
    final int descriptionTopNudge = sampleSelector instanceof FlutterSampleComboBox ? -7 : -14;
    descriptionPanel.setBorder(JBUI.Borders.empty(descriptionTopNudge, 12, 5, 5));
    descriptionPanel.add(BorderLayout.NORTH, descriptionText);
    add(BorderLayout.CENTER, descriptionPanel);

    // PLACEHOLDER (to force reflow on resize)
    add(BorderLayout.EAST, new NonOpaquePanel(new BorderLayout()));

    setBorder(JBUI.Borders.empty(10, 10, 5,  10));
  }

  @Override
  public Color getBackground() {
    final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
    if (myBackgroundColor != null) return myBackgroundColor;
    if (myBackgroundColorKey != null) {
      final Color color = globalScheme.getColor(myBackgroundColorKey);
      if (color != null) return color;
    }
    final Color color = globalScheme.getColor(EditorColors.NOTIFICATION_BACKGROUND);
    return color != null ? color : UIUtil.getToolTipBackground();
  }

  @NotNull
  FlutterSample getSelectedSample() {
    return samples.size() == 1 ? samples.get(0) : ((FlutterSampleComboBox)sampleSelector).getSelectedItem();
  }

  private void doCreate() {
    final FlutterSample sample = getSelectedSample();
    final String status = FlutterSampleManager.createSampleProject(sample, project);
    if (status != null) {
      FlutterMessages.showError("Sample Project Creation", "Error: " + status);
    }
  }
}
