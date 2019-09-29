/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.ColorPickerListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.PositionTracker;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import io.flutter.FlutterMessages;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorService;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.ColorIconMaker;
import net.miginfocom.swing.MigLayout;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

class EnumValueWrapper {
  final FlutterWidgetPropertyValueEnumItem item;
  final String expression;

  public EnumValueWrapper(FlutterWidgetPropertyValueEnumItem item) {
    this.item = item;
    this.expression = item.getName();
    assert (this.expression != null);
  }

  public EnumValueWrapper(String expression) {
    this.expression = expression;
    item = null;
  }

  @Override
  public String toString() {
    if (expression != null) {
      return expression;
    }
    return item != null ? item.getName() : "[null]";
  }
}

class PropertyEnumComboBoxModel extends AbstractListModel<EnumValueWrapper>
  implements ComboBoxModel<EnumValueWrapper> {
  private final List<EnumValueWrapper> myList;
  private EnumValueWrapper mySelected;
  private String expression;

  public PropertyEnumComboBoxModel(FlutterWidgetProperty property) {
    final FlutterWidgetPropertyEditor editor = property.getEditor();
    assert (editor != null);
    myList = new ArrayList<>();
    for (FlutterWidgetPropertyValueEnumItem item : editor.getEnumItems()) {
      myList.add(new EnumValueWrapper(item));
    }
    String expression = property.getExpression();
    if (expression == null) {
      mySelected = null;
      expression = "";
      return;
    }
    if (property.getValue() != null) {
      FlutterWidgetPropertyValue value = property.getValue();
      FlutterWidgetPropertyValueEnumItem enumValue = value.getEnumValue();
      if (enumValue != null) {
        for (EnumValueWrapper e : myList) {
          if (e != null && e.item != null && Objects.equals(e.item.getName(), enumValue.getName())) {
            mySelected = e;
          }
        }
      }
    }
    else {
      final EnumValueWrapper newItem = new EnumValueWrapper(expression);
      myList.add(newItem);
      mySelected = newItem;
    }
    final String kind = editor.getKind();
  }

  @Override
  public int getSize() {
    return myList.size();
  }

  @Override
  public EnumValueWrapper getElementAt(int index) {
    return myList.get(index);
  }

  @Override
  public EnumValueWrapper getSelectedItem() {
    return mySelected;
  }

  @Override
  public void setSelectedItem(Object item) {
    if (item instanceof String) {
      String expression = (String)item;
      for (EnumValueWrapper e : myList) {
        if (Objects.equals(e.expression, expression)) {
          mySelected = e;
          return;
        }
      }
      EnumValueWrapper wrapper = new EnumValueWrapper(expression);
      myList.add(wrapper);
      this.fireIntervalAdded(this, myList.size() - 1, myList.size());
      setSelectedItem(wrapper);
      return;
    }
    setSelectedItem((EnumValueWrapper)item);
  }

  public void setSelectedItem(EnumValueWrapper item) {
    mySelected = item;
    fireContentsChanged(this, 0, getSize());
  }
}

public class PropertyEditorPanel extends JBPanel {
  final DiagnosticsNode node;
  private final XSourcePosition position;
  private final FlutterDartAnalysisServer flutterDartAnalysisService;
  private final Project project;
  private JBPopup popup;
  private Map<String, JComponent> fields = new HashMap<>();
  private Color widgetColor;

  public PropertyEditorPanel(@Nullable InspectorService inspectorService,
                             Project project,
                             DiagnosticsNode node,
                             XSourcePosition p,
                             FlutterDartAnalysisServer flutterDartAnalysisService) {
    super();
    this.project = project;
    this.node = node;
    this.position = node != null ? node.getCreationLocation().getXSourcePosition() : p;
    this.flutterDartAnalysisService = flutterDartAnalysisService;
    assert(position != null);
    final java.util.List<FlutterWidgetProperty> properties =
      flutterDartAnalysisService.getWidgetDescription(position.getFile(), position.getOffset());

    MigLayout manager = new MigLayout("insets 0", // Layout Constraints
                                      "[::80]5[:150:300]", // Column constraints
                                      "[]0[]");
    setLayout(manager);
    add(new JBLabel(getDescription()), "span, growx");
    //    setBorder(JBUI.Borders.empty(5));
    int added = 0;
    Map<String, FlutterWidgetProperty> map = new HashMap<>();
    if (properties != null) {
      for (FlutterWidgetProperty property : properties) {
        String name = property.getName();
        map.put(name, property);
      }
    }
    if (node != null) {
      InspectorService.ObjectGroup group = node.getInspectorService().getNow(null);

      group.safeWhenComplete(node.getProperties(group), (diagnosticProperties, error) -> {
        if (error != null || diagnosticProperties == null) {
          return;
        }
        for (DiagnosticsNode prop : diagnosticProperties) {
          final String name = prop.getName();
          if (fields.containsKey(name)) {
            JComponent field = fields.get(name);
            field.setToolTipText("Runtime value:" + prop.getDescription());
            if (name.equals("color")) {
              JBLabel textField = (JBLabel)field;
              String value = "";
              if (prop.getDescription() != null) {
                value = prop.getDescription();
              }
              textField.setText(value);
            }
          }
        }
      });

      if (node.getWidgetRuntimeType().equals("Text")) {
        final String fieldName = "color";
        FlutterWidgetProperty property = map.get(fieldName);

        String documentation = "Set color with live updates";
        String expression = "";
        if (property != null) {
          documentation = property.getDocumentation();
          expression = property.getExpression();
        }
        JBLabel label = new JBLabel("color");
        JBLabel field;
        add(label, "right");
        field = new JBLabel(expression);
        if (documentation != null) {
          field.setToolTipText(documentation);
          label.setToolTipText(documentation);
        }
        fields.put(fieldName, field);
        add(field, "wrap, growx");
        field.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            ColorChooserService service = ColorChooserService.getInstance();

            List<ColorPickerListener> listeners = new ArrayList<>();
            listeners.add(new ColorPickerListener() {
              @Override
              public void colorChanged(Color color) {
                ColorIconMaker maker = new ColorIconMaker();

                widgetColor = color;
                long value =
                  ((long)color.getAlpha() << 24) | ((long)color.getRed() << 16) | (long)(color.getGreen() << 8) | (long)color.getBlue();
                group.getInspectorService().createObjectGroup("setprop").setProperty(node, "color", "" + value);
                field.setIcon(maker.getCustomIcon(color));
                String textV;
                int alpha = color.getAlpha();
                int red = color.getRed();
                int green = color.getGreen();
                int blue = color.getBlue();
                if (alpha == 255) {
                  textV = String.format("#%02x%02x%02x", red, green, blue);
                }
                else {
                  textV = String.format("#%02x%02x%02x%02x", alpha, red, green, blue);
                }

                field.setText(textV);
              }

              @Override
              public void closed(@Nullable Color color) {

              }
            });
            ColorPicker picker = new ColorPicker(new Disposable() {
              @Override
              public void dispose() {
                System.out.println("XXX disposed?");
              }
            }, Color.RED, true, true, listeners, false);
            ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(picker, PropertyEditorPanel.this);
            builder.setMovable(true);
            builder.setFocusable(true);
            builder.setTitle("Select color");
            builder.createPopup().show(new RelativePoint(new Point(0, 0)));
          }
        });
      }
    }
    if (properties != null && !properties.isEmpty()) {
      for (FlutterWidgetProperty property : properties) {
        String name = property.getName();
        if (name.startsWith("on") || name.endsWith("Callback")) {
          continue;
        }
        if (name.equals("key")) {
          continue;
        }
        if (name.equals("child") || name.equals("children")) {
          continue;
        }
        if (name.equals("Container")) {
          List<FlutterWidgetProperty> containerProperties = property.getChildren();
          continue;
        }
        // Text widget properties to demote.
        if (name.equals("strutStyle") || name.equals("overflow") || name.equals("locale") || name.equals("semanticsLabel")) {
          continue;
        }
        if (property.getEditor() == null) continue;
        final String documentation = property.getDocumentation();
        JComponent field;

        if (property.getEditor() == null) {
          field = new JBTextField(property.getExpression());
        }
        else {
          FlutterWidgetPropertyEditor editor = property.getEditor();
          if (editor.getEnumItems() != null) {
            ComboBox comboBox = new ComboBox();
            comboBox.setEditable(true);
            PropertyEnumComboBoxModel model = new PropertyEnumComboBoxModel(property);
            comboBox.setModel(model);
            field = comboBox;
            comboBox.addItemListener(e -> {
              if (e.getStateChange() == ItemEvent.SELECTED) {
                EnumValueWrapper wrapper = (EnumValueWrapper)e.getItem();
                if (wrapper.item != null) {
                  SourceChange change = flutterDartAnalysisService
                    .setWidgetPropertyValue(property.getId(), new FlutterWidgetPropertyValue(null, null, null, null, wrapper.item, null));
                  System.out.println("XXX set property result = " + change);
                  if (change != null) {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                      try {
                        AssistUtils.applySourceChange(project, change, false);
                        if (inspectorService != null) {
                          // XXX handle the app loading part way through better.
                          ArrayList<FlutterApp> l = new ArrayList<>();
                          l.add(inspectorService.getApp());
                          FlutterReloadManager.getInstance(project).saveAllAndReloadAll(l, "Property Editor");
                        }
                      }
                      catch (DartSourceEditException exception) {
                        FlutterMessages.showError("Error applying change", exception.getMessage());
                      }
                    });
                  }
                }
                else {
                  System.out.println("XXX sorry can't yet set expression: " + wrapper.expression);
                }
                System.out.println("XXX new item = " + e.getItem());
              }
            });
          }
          else {
            field = new JBTextField(property.getExpression());
          }
        }
        /*
        field.addActionListener((e) -> {
          System.out.println("XXX command = " + e.getActionCommand());
        });

         */
        if (name.equals("data")) {
          if (documentation != null) {
            field.setToolTipText(documentation);
            //            field.getEmptyText().setText(documentation);
          }
          else {
            field.setToolTipText("data");
          }
          add(field, "span, growx");
        }
        else {
          JBLabel label = new JBLabel(property.getName());
          add(label, "right");
          if (documentation != null) {
            field.setToolTipText(documentation);
            label.setToolTipText(documentation);
          }
          add(field, "wrap, growx");
        }
        fields.put(name, field);

        added++;
        if (added > 8) {
          //          add(new JBLabel("..."), "span, growx");
          break;
        }
      }
    }
    else {
      System.out.println("XXX no properties");
    }
/*      add(new JColorChooser(), "span");

    JBLabel label = new JBLabel("COLOR");
    add(label, "right");
    add(new JBTextField("#0FFFFF"), "wrap, growx");

     label = new JBLabel("Very long label");
    add(label, "right");
    add(new JBTextField("#099"), "wrap, growx");
    */

  }

  public static Balloon showPopup(InspectorService inspectorService,
                                  EditorEx editor,
                                  DiagnosticsNode node,
                                  XSourcePositionImpl position,
                                  FlutterDartAnalysisServer service,
                                  Point point) {
    final Balloon balloon = showPopupHelper(inspectorService, editor.getProject(), node, position, service);
    if (position != null) {
      final TextRange textRange = new TextRange(position.getOffset(), position.getOffset() + 1);
      balloon.show(new PropertyBalloonPositionTracker(editor, textRange), Balloon.Position.below);
    }
    else {
      //      balloon.show(new RelativePoint(editor.getComponent(), point), Balloon.Position.below);

      balloon.show(new PropertyBalloonPositionTrackerScreenshot(editor, point), Balloon.Position.below);
    }
    return balloon;
  }

  public static Balloon showPopup(InspectorService inspectorService,
                                  Project project,
                                  Component component,
                                  @Nullable DiagnosticsNode node,
                                  @Nullable XSourcePosition position,
                                  FlutterDartAnalysisServer service,
                                  Point point) {
    final Balloon balloon = showPopupHelper(inspectorService, project, node, position, service);
    point = new Point(point);
    point.y -= 20;
    balloon.show(new RelativePoint(component, point), Balloon.Position.above);
    return balloon;
  }

  public static Balloon showPopupHelper(InspectorService inspectorService,
                                        Project project,
                                        @Nullable DiagnosticsNode node,
                                        @Nullable XSourcePosition position,
                                        FlutterDartAnalysisServer service) {
 //   assert (node != null || position != null);
    final Color GRAPHITE_COLOR = new JBColor(new Color(200, 200, 200, 230), new Color(100, 100, 100, 230));

    PropertyEditorPanel panel =
      new PropertyEditorPanel(inspectorService, project, node, position, service);
    panel.setBackground(GRAPHITE_COLOR);
    panel.setOpaque(false);
/*
    JBScrollPane scrollPane = (JBScrollPane)ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    scrollPane.setAutoscrolls(true);
    scrollPane.setSize(new Dimension(300, 200));
    scrollPane.setMaximumSize(new Dimension(300, 200));*/

    /*
    JBPopup popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(panel, panel)
      .setMovable(true)
      .setAlpha(0.0f)
      .setMinSize(new Dimension(200, 5))
      .setTitle(panel.getDescription())
      .setCancelOnWindowDeactivation(true)
      .setRequestFocus(true)
      .createPopup();

     */
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(panel);
    balloonBuilder.setFadeoutTime(0);
    balloonBuilder.setFillColor(GRAPHITE_COLOR);
    balloonBuilder.setAnimationCycle(0);
    balloonBuilder.setHideOnClickOutside(true);
    balloonBuilder.setHideOnKeyOutside(false);
    balloonBuilder.setHideOnAction(false);
    balloonBuilder.setCloseButtonEnabled(false);
    balloonBuilder.setBlockClicksThroughBalloon(true);
    balloonBuilder.setRequestFocus(true);
    balloonBuilder.setShadow(true);
    Balloon balloon = balloonBuilder.createBalloon();
    return balloon;
  }

  public String getDescription() {
    if (node != null) {
      return node.getDescription() + " Properties";
    }
    else {
      // XXX extract the name by guessing.
      return position.toString() + " Properties";
    }
  }
}

class PropertyBalloonPositionTracker extends PositionTracker<Balloon> {
  private final Editor myEditor;
  private final TextRange myRange;

  PropertyBalloonPositionTracker(Editor editor, TextRange range) {
    super(editor.getContentComponent());
    myEditor = editor;
    myRange = range;
  }

  static boolean insideVisibleArea(Editor e, TextRange r) {
    int textLength = e.getDocument().getTextLength();
    if (r.getStartOffset() > textLength) return false;
    if (r.getEndOffset() > textLength) return false;
    Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.getStartOffset()));

    return visibleArea.contains(point);
  }

  @Override
  public RelativePoint recalculateLocation(final Balloon balloon) {
    int startOffset = myRange.getStartOffset();
    int endOffset = myRange.getEndOffset();

    //This might be interesting or might be an unrelated use case.
    /*
    if (!insideVisibleArea(myEditor, myRange)) {
      if (!balloon.isDisposed()) {
        Disposer.dispose(balloon);
      }

      VisibleAreaListener visibleAreaListener = new VisibleAreaListener() {
        @Override
        public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
          if (insideVisibleArea(myEditor, myRange)) {
//            showBalloon(myProject, myEditor, myRange);
            final VisibleAreaListener visibleAreaListener = this;
            myEditor.getScrollingModel().removeVisibleAreaListener(visibleAreaListener);
          }
        }
      };
      myEditor.getScrollingModel().addVisibleAreaListener(visibleAreaListener);
    }
*/
    Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset));
    Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset));
    Point point = new Point((startPoint.x + endPoint.x) / 2, startPoint.y + myEditor.getLineHeight());

    return new RelativePoint(myEditor.getContentComponent(), point);
  }
}

class PropertyBalloonPositionTrackerScreenshot extends PositionTracker<Balloon> {
  private final Editor myEditor;
  private final Point point;

  PropertyBalloonPositionTrackerScreenshot(Editor editor, Point point) {
    super(editor.getComponent());
    myEditor = editor;
    this.point = point;
  }

  @Override
  public RelativePoint recalculateLocation(final Balloon balloon) {
    return new RelativePoint(myEditor.getComponent(), point);
  }
}

