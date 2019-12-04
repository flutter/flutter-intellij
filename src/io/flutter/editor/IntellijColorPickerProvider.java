/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.colorpicker.ColorPickerBuilder;
import com.intellij.ui.colorpicker.LightCalloutPopup;
import com.intellij.ui.colorpicker.MaterialGraphicalColorPipetteProvider;
import kotlin.Unit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class IntellijColorPickerProvider implements ColorPickerProvider {
  private LightCalloutPopup popup;

  @Override
  public void show(Color initialColor, JComponent component, Point offset, Balloon.Position position, ColorListener colorListener, Runnable onCancel, Runnable onOk) {
    if (popup != null) {
      popup.close();
    }
    popup = new ColorPickerBuilder()
      .setOriginalColor(initialColor)
      // TODO(jacobr): we would like to add the saturation and brightness
      // component but for some reason it throws exceptions even though there
      // are examples of it being used identically without throwing exceptions
      // elsewhere in the IntelliJ code base.
      //.addSaturationBrightnessComponent()
      .addColorAdjustPanel(new MaterialGraphicalColorPipetteProvider())
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
        }
      )
      .addKeyAction(
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            onOk.run();
          }
        }
      )
      .addColorListener(colorListener::colorChanged, true)
      .build();
    popup.show(component, offset, position);
  }

  @Override
  public void dispose() {
    if (popup != null) {
      popup.close();
    }
    popup = null;
  }
}
