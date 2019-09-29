/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.colorpicker.ColorPickerBuilder;
import com.intellij.ui.colorpicker.LightCalloutPopup;
import com.intellij.ui.colorpicker.MaterialGraphicalColorPipetteProvider;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextField;
import io.flutter.inspector.InspectorObjectGroupManager;
import io.flutter.inspector.InspectorService;
import io.flutter.utils.ColorIconMaker;
import kotlin.Unit;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.FlutterWidgetProperty;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.concurrent.CompletableFuture;

/**
 * Field that displays a color property including a clickable color icon that
 * opens a color picker.
 */
class ColorField extends ExtendableTextField {
  private final FlutterWidgetProperty property;
  private final String originalExpression;
  private final String name;
  private final Color initialColor;
  private Color currentColor;
  private LightCalloutPopup popup;
  private PropertyEditorPanel panel;
  private Color colorAtPopupLaunch;
  private String expressionAtPopupLaunch;

  public ColorField(PropertyEditorPanel panel, String name, FlutterWidgetProperty property, Disposable parentDisposable) {
    super("", 1);
    this.name = name;
    this.property = property;

    final String expression = property.getExpression();
    if (expression != null) {
      setText(expression);
    }
    this.originalExpression = expression;
    initialColor = parseColorExpression(expression);
    currentColor = initialColor;

    final ColorIconMaker maker = new ColorIconMaker();

    final KeyStroke keyStroke = KeyStroke.getKeyStroke(10, 64);
    final Extension setColorExtension =
      new Extension() {
        @Override
        public boolean isIconBeforeText() {
          return true;
        }

        public Icon getIcon(boolean hovered) {
          if (currentColor == null) {
            return AllIcons.Actions.Colors;
          }
          return maker.getCustomIcon(currentColor);
        }

        public String getTooltip() {
          return "Edit color";
        }

        public Runnable getActionOnClick() {
          return () -> showColorFieldPopup();
        }
      };
    (new DumbAwareAction() {
      public void actionPerformed(@NotNull AnActionEvent e) {
        showColorFieldPopup();
      }
    }).registerCustomShortcutSet(new CustomShortcutSet(keyStroke), this, parentDisposable);
    addExtension(setColorExtension);
    panel.addTextFieldListeners(name, this);
    this.panel = panel;
  }

  private static Color parseColorExpression(String expression) {
    if (expression == null) return null;

    final String colorsPrefix = "Colors.";
    if (expression.startsWith(colorsPrefix)) {
      final FlutterColors.FlutterColor flutterColor = FlutterColors.getColor(expression.substring(colorsPrefix.length()));
      if (flutterColor != null) {
        return flutterColor.getAWTColor();
      }
    }
    return ExpressionParsingUtils.parseColor(expression);
  }

  private static String buildColorExpression(Color color) {
    final String flutterColorName = FlutterColors.getColorName(color);
    if (flutterColorName != null) {
      // TODO(jacobr): only apply this conversion if the material library is
      // already imported in the library being edited.
      // We also need to be able to handle cases where the material library
      // is imported with a prefix.
      return "Colors." + flutterColorName;
    }
    final int alpha = color.getAlpha();
    final int red = color.getRed();
    final int green = color.getGreen();
    final int blue = color.getBlue();
    return String.format("Color(0x%02x%02x%02x%02x)", alpha, red, green, blue);
  }

  public void addTextFieldListeners(String name, JBTextField field) {
    final FlutterOutline matchingOutline = panel.getCurrentOutline();
    field.addActionListener(e -> panel.setPropertyValue(name, field.getText()));
    field.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        // The popup is gone if we have received the focus again.
        popup = null;
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (panel.getCurrentOutline() != matchingOutline) {
          // Don't do anything. The user has moved on to a different outline node.
          return;
        }
        if (e.isTemporary()) {
          return;
        }

        if (popup != null) {
          // We lost focus due to the popup being opened so there is no need to
          // update the value now. The popup will update the value when it is
          // closed.
          return;
        }
        panel.setPropertyValue(name, field.getText());
      }
    });
  }

  void cancelPopup() {
    currentColor = colorAtPopupLaunch;
    setText(expressionAtPopupLaunch);
    panel.setPropertyValue(name, originalExpression, true);
    repaint();
    popup.cancel();
    popup = null;
  }

  void showColorFieldPopup() {
    if (popup != null) {
      popup.close();
    }
    colorAtPopupLaunch = currentColor;
    expressionAtPopupLaunch = getText();
    popup = new ColorPickerBuilder()
      .setOriginalColor(initialColor != null ? initialColor : new Color(255, 255, 255))
      // TODO(jacobr): we would like to add the saturation and brightness
      // component but for some reason it throws exceptions even though there
      // are examples of it being used identically without throwing exceptions
      // elsewhere in the IntelliJ code base.
      // .addSaturationBrightnessComponent()
      .addColorAdjustPanel(new MaterialGraphicalColorPipetteProvider())
      .addColorValuePanel().withFocus()
      .addOperationPanel(
        (okColor) -> {
          applyColor(okColor);
          return Unit.INSTANCE;
        },
        (cancelColor) -> {
          cancelPopup();
          return Unit.INSTANCE;
        }
      ).withFocus()
      .setFocusCycleRoot(true)
      .focusWhenDisplay(true)
      .addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    new AbstractAction() {
                      @Override
                      public void actionPerformed(ActionEvent e) {
                        cancelPopup();
                      }
                    })
      .addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                    new AbstractAction() {
                      @Override
                      public void actionPerformed(ActionEvent e) {
                        applyColor(currentColor);
                      }
                    })
      .addColorListener((color, o) -> {
        if (popup == null) {
          // This can happen after a call to cancel.
          return;
        }
        currentColor = color;
        final InspectorObjectGroupManager groupManager = panel.getGroupManager();
        final String colorExpression = buildColorExpression(color);

        // TODO(jacobr): colorField may no longer be the right field in the UI.
        setText(colorExpression);
        repaint();

        if (panel.getNode() != null && groupManager != null) {
          // TODO(jacobr): rate limit setting the color property if there is a performance issue.
          final InspectorService.ObjectGroup group = groupManager.getCurrent();
          CompletableFuture<Boolean> valueFuture = groupManager.getCurrent().setColorProperty(panel.getNode(), color);
          group.safeWhenComplete(valueFuture, (success, error) -> {
            if (success == null || error != null) {
              return;
            }
            // TODO(jacobr):
            // If setting the property immediately failed, we may have to set the property value fully to see a result.
            // This code is risky because it could cause the outline to change and too many hot reloads cause flaky app behavior.
              /*
              if (!success && color.equals(picker.getColor())) {
                setPropertyValue(colorPropertyName, colorExpression);
              }*/
          });
        }
      }, true)
      .build();
    popup.show(panel, new Point(-150, 150));
  }

  private void applyColor(Color color) {
    final String colorExpression = buildColorExpression(color);
    currentColor = color;
    setText(colorExpression);
    panel.setPropertyValue(name, colorExpression);
    repaint();
    popup.close();
    popup = null;
  }
}
