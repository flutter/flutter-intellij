/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.ui.Gray;
import com.intellij.ui.colorpicker.ColorPickerBuilder;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.colorpicker.LightCalloutPopup;
import kotlin.Unit;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import java.awt.*;
import javax.swing.*;

public class AndroidStudioColorPickerProvider implements ColorPickerProvider {
  private Balloon popup;

  @Override
  public void show(
    Color initialColor,
    JComponent component,
    Point offset,
    Balloon.Position position,
    ColorPickerProvider.ColorListener colorListener,
    Runnable onCancel,
    Runnable onOk
  ) {
    if (popup != null) {
      popup.dispose();
    }
    popup = null;

    final LightCalloutPopup colorPanel = new ColorPickerBuilder()
      .setOriginalColor(initialColor != null ? initialColor : Gray._255)
      .addSaturationBrightnessComponent()
      .addColorAdjustPanel()
      .addColorValuePanel().withFocus()
      .addOperationPanel(
        (okColor) -> {
          onOk.run();
          return Unit.INSTANCE;
        },
        (cancelColor) -> {
          onCancel.run();
          return Unit.INSTANCE;
        }
      ).withFocus()
      .setFocusCycleRoot(true)
      .focusWhenDisplay(true)
      .addKeyAction(
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            onCancel.run();
          }
        })

      .addKeyAction(
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            onOk.run();
          }
        }
      )

      .addColorListener((c, o) -> colorListener.colorChanged(c, null))
      .build();
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(colorPanel.getContent());
    balloonBuilder.setFadeoutTime(0);
    balloonBuilder.setAnimationCycle(0);
    balloonBuilder.setHideOnClickOutside(true);
    balloonBuilder.setHideOnKeyOutside(false);
    balloonBuilder.setHideOnAction(false);
    balloonBuilder.setCloseButtonEnabled(false);
    balloonBuilder.setBlockClicksThroughBalloon(true);
    balloonBuilder.setRequestFocus(true);
    balloonBuilder.setShadow(true);
    balloonBuilder.setFillColor(colorPanel.getContent().getBackground());
    popup = balloonBuilder.createBalloon();
    popup.show(new RelativePoint(component, offset), position);
  }

  @Override
  public void dispose() {
    if (popup != null) {
      popup.dispose();
    }
    popup = null;
  }
}
