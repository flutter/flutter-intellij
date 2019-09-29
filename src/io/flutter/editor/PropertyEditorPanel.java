/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.google.common.base.Joiner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.PositionTracker;
import com.jetbrains.lang.dart.assists.AssistUtils;
import com.jetbrains.lang.dart.assists.DartSourceEditException;
import io.flutter.FlutterMessages;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.hotui.StableWidgetTracker;
import io.flutter.inspector.DiagnosticsNode;
import io.flutter.inspector.InspectorGroupManagerService;
import io.flutter.inspector.InspectorObjectGroupManager;
import io.flutter.inspector.InspectorService;
import io.flutter.preview.OutlineOffsetConverter;
import io.flutter.preview.WidgetEditToolbar;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncRateLimiter;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.EventStream;
import net.miginfocom.swing.MigLayout;
import org.dartlang.analysis.server.protocol.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

/**
 * Panel that supports editing properties of a specified widget.
 * <p>
 * The property panel will update
 */
public class PropertyEditorPanel extends SimpleToolWindowPanel {
  protected final AsyncRateLimiter retryLookupPropertiesRateLimiter;
  /**
   * Whether the property panel is being rendered in a fixed width context such
   * as inside a baloon popup window or is within a resizeable window.
   */
  private final boolean fixedWidth;
  private final InspectorGroupManagerService.Client inspectorStateServiceClient;
  private final FlutterDartAnalysisServer flutterDartAnalysisService;
  private final Project project;
  private final boolean showWidgetEditToolbar;
  private final Map<String, JComponent> fields = new HashMap<>();
  private final Map<String, FlutterWidgetProperty> propertyMap = new HashMap<>();
  private final Map<String, String> currentExpressionMap = new HashMap<>();
  private final ArrayList<FlutterWidgetProperty> properties = new ArrayList<>();
  private final Disposable parentDisposable;
  // TODO(jacobr): figure out why this is needed.
  int numFailures;
  String previouslyFocusedProperty = null;
  private DiagnosticsNode node;
  /**
   * Outline node
   */
  private FlutterOutline outline;

  private EventStream<VirtualFile> activeFile;

  /**
   * Whether the property panel has already triggered a pending hot reload.
   */
  private boolean pendingHotReload;

  /**
   * Whether the property panel needs another hot reload to occur after the
   * current pending hot reload is complete.
   */
  private boolean needHotReload;
  private CompletableFuture<List<FlutterWidgetProperty>> propertyFuture;

  public PropertyEditorPanel(
    @Nullable InspectorGroupManagerService inspectorGroupManagerService,
    Project project,
    FlutterDartAnalysisServer flutterDartAnalysisService,
    boolean showWidgetEditToolbar,
    boolean fixedWidth,
    Disposable parentDisposable
  ) {
    super(true, true);
    setFocusable(true);
    this.fixedWidth = fixedWidth;
    this.parentDisposable = parentDisposable;

    inspectorStateServiceClient = new InspectorGroupManagerService.Client(parentDisposable) {
      @Override
      public void onInspectorAvailabilityChanged() {
        // The app has terminated or restarted. No way we are still waiting
        // for a pending hot reload.
        pendingHotReload = false;
      }

      @Override
      public void notifyAppReloaded() {
        pendingHotReload = false;
      }

      @Override
      public void notifyAppRestarted() {
        pendingHotReload = false;
      }
    };

    retryLookupPropertiesRateLimiter = new AsyncRateLimiter(10, () -> {
      // TODO(jacobr): is this still needed now that we have dealt with timeout
      //  issues by making the analysis server api async?
      maybeLoadProperties();
      return CompletableFuture.completedFuture(null);
    }, parentDisposable);

    inspectorGroupManagerService.addListener(inspectorStateServiceClient, parentDisposable);
    this.project = project;
    this.flutterDartAnalysisService = flutterDartAnalysisService;
    this.showWidgetEditToolbar = showWidgetEditToolbar;
  }

  /**
   * Display a popup containing the property editing panel for the specified widget.
   */
  public static Balloon showPopup(
    InspectorGroupManagerService inspectorGroupManagerService,
    EditorEx editor,
    DiagnosticsNode node,
    @NotNull InspectorService.Location location,
    FlutterDartAnalysisServer service,
    Point point
  ) {
    final Balloon balloon = showPopupHelper(inspectorGroupManagerService, editor.getProject(), node, location, service);
    if (point != null) {
      balloon.show(new PropertyBalloonPositionTrackerScreenshot(editor, point), Balloon.Position.below);
    }
    else {
      final int offset = location.getOffset();
      final TextRange textRange = new TextRange(offset, offset + 1);
      balloon.show(new PropertyBalloonPositionTracker(editor, textRange), Balloon.Position.below);
    }
    return balloon;
  }

  public static Balloon showPopup(
    InspectorGroupManagerService inspectorGroupManagerService,
                                  Project project,
                                  Component component,
                                  @Nullable DiagnosticsNode node,
                                  @NonNls InspectorService.Location location,
                                  FlutterDartAnalysisServer service,
                                  Point point
  ) {
    final Balloon balloon = showPopupHelper(inspectorGroupManagerService, project, node, location, service);
    balloon.show(new RelativePoint(component, point), Balloon.Position.above);
    return balloon;
  }

  public static Balloon showPopupHelper(
    InspectorGroupManagerService inspectorService,
                                        Project project,
                                        @Nullable DiagnosticsNode node,
                                        @NotNull InspectorService.Location location,
                                        FlutterDartAnalysisServer service
  ) {
    final Color GRAPHITE_COLOR = new JBColor(new Color(236, 236, 236, 215), new Color(60, 63, 65, 215));

    final Disposable panelDisposable = Disposer.newDisposable();
    final PropertyEditorPanel panel =
      new PropertyEditorPanel(inspectorService, project, service, true, true, panelDisposable);

    final StableWidgetTracker tracker = new StableWidgetTracker(location, service, project, panelDisposable);

    final EventStream<VirtualFile> activeFile = new EventStream<>(location.getFile());
    panel.initalize(node, tracker.getCurrentOutlines(), activeFile);

    panel.setBackground(GRAPHITE_COLOR);
    panel.setOpaque(false);
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createBalloonBuilder(panel);
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
    final Balloon balloon = balloonBuilder.createBalloon();
    Disposer.register(balloon, panelDisposable);

    return balloon;
  }

  InspectorObjectGroupManager getGroupManager() {
    return inspectorStateServiceClient.getGroupManager();
  }

  int getOffset() {
    if (outline == null) return -1;
    final VirtualFile file = activeFile.getValue();

    assert (activeFile.getValue() != null);
    final OutlineOffsetConverter converter = new OutlineOffsetConverter(project, activeFile.getValue());
    return converter.getConvertedOutlineOffset(outline);
  }

  InspectorService.Location getInspectorLocation() {
    final VirtualFile file = activeFile.getValue();
    if (file == null || outline == null) {
      return null;
    }

    final Document document = FileDocumentManager.getInstance().getDocument(file);
    return InspectorService.Location.outlineToLocation(project, activeFile.getValue(), outline, document);
  }

  void updateWidgetDescription() {
    final VirtualFile file = activeFile.getValue();
    final int offset = getOffset();

    if (file == null || offset < 0) {
      properties.clear();
      return;
    }

    final CompletableFuture<List<FlutterWidgetProperty>> future =
      flutterDartAnalysisService.getWidgetDescription(file, offset);
    propertyFuture = future;

    if (propertyFuture == null) return;

    AsyncUtils.whenCompleteUiThread(propertyFuture, (updatedProperties, throwable) -> {
      if (propertyFuture != future || updatedProperties == null || throwable != null) {
        // This request is obsolete.
        return;
      }
      if (offset != getOffset() || !file.equals(activeFile.getValue())) {
        return;
      }
      properties.clear();
      properties.addAll(updatedProperties);
      propertyMap.clear();
      currentExpressionMap.clear();
      for (FlutterWidgetProperty property : updatedProperties) {
        final String name = property.getName();
        propertyMap.put(name, property);
        currentExpressionMap.put(name, property.getExpression());
      }

      if (propertyMap.isEmpty()) {
        // TODO(jacobr): it is unclear why this initialy returns an invalid value.
        numFailures++;
        if (numFailures < 3) {
          retryLookupPropertiesRateLimiter.scheduleRequest();
        }
        return;
      }
      numFailures = 0;
      rebuildUi();
    });
  }

  public void outlinesChanged(List<FlutterOutline> outlines) {
    final FlutterOutline nextOutline = outlines.isEmpty() ? null : outlines.get(0);
    if (nextOutline == outline) return;
    this.outline = nextOutline;
    maybeLoadProperties();
    lookupMatchingElements();
  }

  public void lookupMatchingElements() {
    final InspectorObjectGroupManager groupManager = getGroupManager();
    if (groupManager == null || outline == null) return;
    groupManager.cancelNext();
    ;
    node = null;
    final InspectorService.ObjectGroup group = groupManager.getNext();
    final InspectorService.Location location = getInspectorLocation();
    group.safeWhenComplete(group.getElementsAtLocation(location, 10), (elements, error) -> {
      if (elements == null || error != null) {
        return;
      }
      node = elements.isEmpty() ? null : elements.get(0);
      groupManager.promoteNext();
    });
  }

  public DiagnosticsNode getNode() {
    return node;
  }

  void maybeLoadProperties() {
    updateWidgetDescription();
  }

  public void initalize(
    DiagnosticsNode node,
    EventStream<List<FlutterOutline>> currentOutlines,
    EventStream<VirtualFile> activeFile
  ) {
    this.node = node;
    this.activeFile = activeFile;
    currentOutlines.listen(this::outlinesChanged, true);
    if (showWidgetEditToolbar) {
      final WidgetEditToolbar widgetEditToolbar =
        new WidgetEditToolbar(true, currentOutlines, activeFile, project, flutterDartAnalysisService);
      final ActionToolbar toolbar = widgetEditToolbar.getToolbar();
      toolbar.setShowSeparatorTitles(true);
      setToolbar(toolbar.getComponent());
    }

    rebuildUi();
  }

  protected void rebuildUi() {
    // TODO(jacobr): be lazier about only rebuilding what changed.
    final Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner != null) {
      if (isAncestorOf(focusOwner)) {
        for (Map.Entry<String, JComponent> entry : fields.entrySet()) {
          if (entry.getValue().isAncestorOf(focusOwner) || entry.getValue() == focusOwner) {
            previouslyFocusedProperty = entry.getKey();
            break;
          }
        }
      }
      else {
        previouslyFocusedProperty = null;
      }
    }
    removeAll();
    // Layout Constraints
    // Column constraints
    final MigLayout manager = new MigLayout(
      "insets 3", // Layout Constraints
      fixedWidth ? "[::120]5[:20:400]" : "[::120]5[grow]", // Column constraints
      "[23]4[23]"
    );
    setLayout(manager);
    int added = 0;
    for (FlutterWidgetProperty property : properties) {
      final String name = property.getName();
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
        final List<FlutterWidgetProperty> containerProperties = property.getChildren();
        // TODO(jacobr): add support for container properties.
        continue;
      }
      // Text widget properties to demote.
      if (name.equals("strutStyle") || name.equals("locale") || name.equals("semanticsLabel")) {
        continue;
      }
      final String documentation = property.getDocumentation();
      JComponent field = null;

      if (property.getEditor() == null) {
        // TODO(jacobr): detect color properties more robustly.
        final boolean colorProperty = name.equals("color");
        final String colorPropertyName = name;
        if (colorProperty) {
          field = buildColorProperty(name, property);
        }
        else {
          String expression = property.getExpression();
          /*if (expression == null || expression.isEmpty()) {
            continue;
          }*/
          if (expression == null) {
            expression = "";
          }
          final JBTextField textField = new JBTextField(expression);
          addTextFieldListeners(name, textField);
          field = textField;
        }
      }
      else {
        final FlutterWidgetPropertyEditor editor = property.getEditor();
        if (editor.getEnumItems() != null) {
          final ComboBox<EnumValueWrapper> comboBox = new ComboBox<>();
          comboBox.setEditable(true);
          comboBox.setModel(new PropertyEnumComboBoxModel(property));

          // TODO(jacobr): need a bit more padding around comboBox to make it match the JBTextField.
          field = comboBox;
          comboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              final EnumValueWrapper wrapper = (EnumValueWrapper)e.getItem();
              if (wrapper.item != null) {
                setParsedPropertyValue(name, new FlutterWidgetPropertyValue(null, null, null, null, wrapper.item, null), false);
              }
              else {
                setPropertyValue(name, wrapper.expression);
              }
            }
          });
        }
        else {
          // TODO(jacobr): use IntegerField and friends when appropriate.
          // TODO(jacobr): we should probably use if (property.isSafeToUpdate())
          // but that currently it seems to have a bunch of false positives.
          final String kind = property.getEditor().getKind();
          if (Objects.equals(kind, FlutterWidgetPropertyEditorKind.BOOL)) {
            // TODO(jacobr): show as boolean.
          }
          final JBTextField textField = new JBTextField(property.getExpression());
          field = textField;
          addTextFieldListeners(name, textField);
        }
      }

      if (name.equals("data")) {
        if (documentation != null) {
          field.setToolTipText(documentation);
        }
        else {
          field.setToolTipText("data");
        }
        add(field, "span, growx");
      }
      else {
        final JBLabel label = new JBLabel(property.getName());
        add(label, "right");
        if (documentation != null) {
          label.setToolTipText(documentation);
        }
        add(field, "wrap, growx");
      }
      if (documentation != null) {
        field.setToolTipText(documentation);
      }
      // Hack: set the preferred width of the ui elements to a small value
      // so it doesn't cause the overall layout to be wider than it should
      // be.
      if (!fixedWidth) {
        setPreferredFieldSize(field);
      }

      fields.put(name, field);
      added++;
    }
    if (previouslyFocusedProperty != null && fields.containsKey(previouslyFocusedProperty)) {
      fields.get(previouslyFocusedProperty).requestFocus();
    }

    if (added == 0) {
      add(new JBLabel("No editable properties"));
    }
    // TODO(jacobr): why is this needed?
    revalidate();
    repaint();
  }

  private JTextField buildColorProperty(String name, FlutterWidgetProperty property) {

    return new ColorField(this, name, property, parentDisposable);
  }

  public void addTextFieldListeners(String name, JBTextField field) {
    final FlutterOutline matchingOutline = outline;
    field.addActionListener(e -> setPropertyValue(name, field.getText()));
    field.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (outline != matchingOutline) {
          // Don't do anything. The user has moved on to a different outline node.
          return;
        }
        setPropertyValue(name, field.getText());
      }
    });
  }

  private void setPreferredFieldSize(JComponent field) {
    field.setPreferredSize(new Dimension(20, (int)field.getPreferredSize().getHeight()));
  }

  public String getDescription() {
    final List<String> parts = new ArrayList<>();
    if (outline != null && outline.getClassName() != null) {
      parts.add(outline.getClassName());
    }
    parts.add("Properties");
    return Joiner.on(" ").join(parts);
  }

  void setPropertyValue(String propertyName, String expression) {
    setPropertyValue(propertyName, expression, false);
  }

  void setPropertyValue(String propertyName, String expression, boolean force) {
    setParsedPropertyValue(propertyName, new FlutterWidgetPropertyValue(null, null, null, null, null, expression), force);
  }

  private void setParsedPropertyValue(String propertyName, FlutterWidgetPropertyValue value, boolean force) {
    final boolean updated = setParsedPropertyValueHelper(propertyName, value);
    if (!updated && force) {
      hotReload();
    }
  }


  private boolean setParsedPropertyValueHelper(String propertyName, FlutterWidgetPropertyValue value) {
    // TODO(jacobr): also do simple tracking of how the previous expression maps to the current expression to avoid spurious edits.

    // Treat an empty expression and empty value objects as omitted values
    // indicating the property should be removed.
    final FlutterWidgetPropertyValue emptyValue = new FlutterWidgetPropertyValue(null, null, null, null, null, null);

    final FlutterWidgetProperty property = propertyMap.get(propertyName);
    if (property == null) {
      // UI is in the process of updating. Skip this action.
      return false;
    }

    if (property.getExpression() != null && property.getExpression().equals(value.getExpression())) {
      return false;
    }

    if (value != null && Objects.equals(value.getExpression(), "") || emptyValue.equals(value)) {
      // Normalize empty expressions to simplify calling this api.
      value = null;
    }

    final String lastExpression = currentExpressionMap.get(propertyName);
    if (lastExpression != null && value != null && lastExpression.equals(value.getExpression())) {
      return false;
    }
    currentExpressionMap.put(propertyName, value != null ? value.getExpression() : null);

    final FlutterWidgetPropertyEditor editor = property.getEditor();
    if (editor != null && value != null && value.getExpression() != null) {
      final String expression = value.getExpression();
      // Normalize expressions as primitive values.
      final String kind = editor.getKind();
      switch (kind) {
        case FlutterWidgetPropertyEditorKind.BOOL: {
          if (expression.equals("true")) {
            value = new FlutterWidgetPropertyValue(true, null, null, null, null, null);
          }
          else if (expression.equals("false")) {
            value = new FlutterWidgetPropertyValue(false, null, null, null, null, null);
          }
        }
        break;
        case FlutterWidgetPropertyEditorKind.STRING: {
          // TODO(jacobr): there might be non-string literal cases that match this patterned.
          if (expression.length() >= 2 && (
            (expression.startsWith("'") && expression.endsWith("'")) ||
            (expression.startsWith("\"") && expression.endsWith("\"")))) {
            value = new FlutterWidgetPropertyValue(null, null, null, expression.substring(1, expression.length() - 1), null, null);
          }
        }
        break;
        case FlutterWidgetPropertyEditorKind.DOUBLE: {
          try {
            double doubleValue = Double.parseDouble(expression);
            if (((double)((int)doubleValue)) == doubleValue) {
              // Express doubles that can be expressed as ints as ints.
              value = new FlutterWidgetPropertyValue(null, null, (int)doubleValue, null, null, null);
            }
            else {
              value = new FlutterWidgetPropertyValue(null, doubleValue, null, null, null, null);
            }
          }
          catch (NumberFormatException e) {
            // Don't convert value.
          }
        }
        break;
        case FlutterWidgetPropertyEditorKind.INT: {
          try {
            int intValue = Integer.parseInt(expression);
            value = new FlutterWidgetPropertyValue(null, null, intValue, null, null, null);
          }
          catch (NumberFormatException e) {
            // Don't convert value.
          }
        }
        break;
      }
    }
    if (Objects.equals(property.getValue(), value)) {
      // Short circuit as nothing changed.
      return false;
    }


    final SourceChange change;
    try {
      change = flutterDartAnalysisService.setWidgetPropertyValue(property.getId(), value);
    }
    catch (Exception e) {
      if (value != null && value.getExpression() != null) {
        FlutterMessages.showInfo("Invalid property value", value.getExpression());
      }
      else {
        FlutterMessages.showError("Unable to set propery value", e.getMessage());
      }
      return false;
    }

    if (change != null && change.getEdits() != null && !change.getEdits().isEmpty()) {
      // XXX does running a write action make any sense? Aren't we already on the ui thread?
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          System.out.println("XXX applying source change: " + change);
          AssistUtils.applySourceChange(project, change, false);
          hotReload();
        }
        catch (DartSourceEditException exception) {
          FlutterMessages.showInfo("Failed to apply code change", exception.getMessage());
        }
      });
      return true;
    }
    return false;
  }

  private void hotReload() {
    // TODO(jacobr): handle multiple simultaneously running Flutter applications.
    final FlutterApp app = inspectorStateServiceClient.getApp();
    if (app != null) {
      final ArrayList<FlutterApp> apps = new ArrayList<>();
      apps.add(app);
      if (pendingHotReload) {
        // It is important we don't try to trigger multiple hot reloads
        // as that will result in annoying user visible error messages.
        needHotReload = true;
      }
      else {
        pendingHotReload = true;
        needHotReload = false;
        FlutterReloadManager.getInstance(project).saveAllAndReloadAll(apps, "Property Editor");
      }
    }
  }

  public FlutterOutline getCurrentOutline() {
    return outline;
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
    final int textLength = e.getDocument().getTextLength();
    if (r.getStartOffset() > textLength) return false;
    if (r.getEndOffset() > textLength) return false;
    final Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    final  Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.getStartOffset()));

    return visibleArea.contains(point);
  }

  @Override
  public RelativePoint recalculateLocation(final Balloon balloon) {
    final int startOffset = myRange.getStartOffset();
    final  int endOffset = myRange.getEndOffset();
    final Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset));
    final Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset));
    final Point point = new Point((startPoint.x + endPoint.x) / 2, startPoint.y + myEditor.getLineHeight());

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

