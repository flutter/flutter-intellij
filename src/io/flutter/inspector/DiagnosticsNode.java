/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import io.flutter.server.vmService.frame.DartVmServiceValue;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.CustomIconMaker;
import io.flutter.utils.JsonUtils;
import org.apache.commons.lang.StringUtils;
import org.dartlang.analysis.server.protocol.HoverInformation;
import org.dartlang.vm.service.element.InstanceRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Defines diagnostics data for a [value].
 * <p>
 * [DiagnosticsNode] provides a high quality multi-line string dump via
 * [toStringDeep]. The core members are the [name], [toDescription],
 * [getProperties], [value], and [getChildren]. All other members exist
 * typically to provide hints for how [toStringDeep] and debugging tools should
 * format output.
 * <p>
 * See also:
 * <p>
 * * DiagnosticsNode class defined at https://github.com/flutter/flutter/blob/master/packages/flutter/lib/src/foundation/diagnostics.dart
 * The difference is the class hierarchy is collapsed on the Java side as
 * the class hierarchy on the Dart side exists more to simplify creation
 * of Diagnostics than because the class hierarchy of Diagnostics is
 * important. If you need to determine the exact Diagnostic class on the
 * Dart side you can use the value of type. The raw Dart object value is
 * also available via the getValue() method.
 */
public class DiagnosticsNode {
  private static final CustomIconMaker iconMaker = new CustomIconMaker();

  private InspectorSourceLocation location;
  private DiagnosticsNode parent;

  private CompletableFuture<String> propertyDocFuture;

  private ArrayList<DiagnosticsNode> cachedProperties;

  public DiagnosticsNode(JsonObject json,
                         InspectorService.ObjectGroup inspectorService,
                         boolean isProperty,
                         DiagnosticsNode parent) {
    this.json = json;
    this.inspectorService = inspectorService;
    this.isProperty = isProperty;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof DiagnosticsNode) {
      final DiagnosticsNode otherNode = (DiagnosticsNode)other;
      return getDartDiagnosticRef().equals(otherNode.getDartDiagnosticRef());
    }
    return false;
  }

  @Override
  public String toString() {
    final String name = getName();
    if (StringUtils.isEmpty(name) || !getShowName()) {
      return getDescription();
    }

    return name + getSeparator() + ' ' + getDescription();
  }

  /**
   * Set this node's parent.
   */
  public void setParent(DiagnosticsNode parent) {
    this.parent = parent;
  }

  /**
   * This node's parent (if it's been set).
   */
  @Nullable
  public DiagnosticsNode getParent() {
    return parent;
  }

  /**
   * Separator text to show between property names and values.
   */
  public String getSeparator() {
    return getShowSeparator() ? ":" : "";
  }

  /**
   * Label describing the [DiagnosticsNode], typically shown before a separator
   * (see [showSeparator]).
   * <p>
   * The name should be omitted if the [showName] property is false.
   */
  public String getName() {
    return getStringMember("name");
  }

  /**
   * Whether to show a separator between [name] and description.
   * <p>
   * If false, name and description should be shown with no separation.
   * `:` is typically used as a separator when displaying as text.
   */
  public boolean getShowSeparator() {
    return getBooleanMember("showSeparator", true);
  }

  /**
   * Returns a description with a short summary of the node itself not
   * including children or properties.
   * <p>
   * `parentConfiguration` specifies how the parent is rendered as text art.
   * For example, if the parent does not line break between properties, the
   * description of a property should also be a single line if possible.
   */
  public String getDescription() {
    return getStringMember("description");
  }

  /**
   * Priority level of the diagnostic used to control which diagnostics should
   * be shown and filtered.
   * <p>
   * Typically this only makes sense to set to a different value than
   * [DiagnosticLevel.info] for diagnostics representing properties. Some
   * subclasses have a `level` argument to their constructor which influences
   * the value returned here but other factors also influence it. For example,
   * whether an exception is thrown computing a property value
   * [DiagnosticLevel.error] is returned.
   */
  public DiagnosticLevel getLevel() {
    return getLevelMember("level", DiagnosticLevel.info);
  }

  /**
   * Whether the name of the property should be shown when showing the default
   * view of the tree.
   * <p>
   * This could be set to false (hiding the name) if the value's description
   * will make the name self-evident.
   */
  public boolean getShowName() {
    return getBooleanMember("showName", true);
  }

  /**
   * Description to show if the node has no displayed properties or children.
   */
  public String getEmptyBodyDescription() {
    return getStringMember("emptyBodyDescription");
  }

  /**
   * Hint for how the node should be displayed.
   */
  public DiagnosticsTreeStyle getStyle() {
    return getStyleMember("style", DiagnosticsTreeStyle.sparse);
  }

  /**
   * Dart class defining the diagnostic node.
   * For example, DiagnosticProperty<Color>, IntProperty, StringProperty, etc.
   * This should rarely be required except for cases where custom rendering is desired
   * of a specific Dart diagnostic class.
   */
  String getType() {
    return getStringMember("type");
  }

  /**
   * Whether the description is enclosed in double quotes.
   * <p>
   * Only relevant for String properties.
   */
  public boolean getIsQuoted() {
    return getBooleanMember("quoted", false);
  }

  public boolean hasIsQuoted() {
    return json.has("quoted");
  }

  /**
   * Optional unit the [value] is measured in.
   * <p>
   * Unit must be acceptable to display immediately after a number with no
   * spaces. For example: 'physical pixels per logical pixel' should be a
   * [tooltip] not a [unit].
   * <p>
   * Only specified for Number properties.
   */
  public String getUnit() {
    return getStringMember("unit");
  }

  public boolean hasUnit() {
    return json.has("unit");
  }

  /**
   * String describing just the numeric [value] without a unit suffix.
   * <p>
   * Only specified for Number properties.
   */
  public String getNumberToString() {
    return getStringMember("numberToString");
  }

  public boolean hasNumberToString() {
    return json.has("numberToString");
  }

  /**
   * Description to use if the property [value] is true.
   * <p>
   * If not specified and [value] equals true the property's priority [level]
   * will be [DiagnosticLevel.hidden].
   * <p>
   * Only applies to Flag properties.
   */
  public String getIfTrue() {
    return getStringMember("ifTrue");
  }

  public boolean hasIfTrue() {
    return json.has("ifTrue");
  }

  /**
   * Description to use if the property value is false.
   * <p>
   * If not specified and [value] equals false, the property's priority [level]
   * will be [DiagnosticLevel.hidden].
   * <p>
   * Only applies to Flag properties.
   */
  public String getIfFalse() {
    return getStringMember("ifFalse");
  }

  public boolean hasIfFalse() {
    return json.has("ifFalse");
  }

  /**
   * Value as a List of strings.
   * <p>
   * The raw value can always be extracted with the regular observatory protocol.
   * <p>
   * Only applies to IterableProperty.
   */
  public ArrayList<String> getValues() {
    if (!json.has("values")) {
      return null;
    }
    final JsonArray rawValues = json.getAsJsonArray("values");
    final ArrayList<String> values = new ArrayList<>(rawValues.size());
    for (int i = 0; i < rawValues.size(); ++i) {
      values.add(rawValues.get(i).getAsString());
    }
    return values;
  }

  public boolean hasValues() {
    return json.has("values");
  }

  /**
   * Description to use if the property [value] is not null.
   * <p>
   * If the property [value] is not null and [ifPresent] is null, the
   * [level] for the property is [DiagnosticsLevel.hidden] and the description
   * from superclass is used.
   * <p>
   * Only specified for ObjectFlagProperty.
   */
  public String getIfPresent() {
    return getStringMember("ifPresent");
  }

  public boolean hasIfPresent() {
    return json.has("ifPresent");
  }

  /**
   * If the [value] of the property equals [defaultValue] the priority [level]
   * of the property is downgraded to [DiagnosticLevel.fine] as the property
   * value is uninteresting.
   * <p>
   * This is the default value of the object represented as a String.
   * The actual Dart object representing the defaultValue can also be accessed via
   * the observatory protocol. We can add a convenience helper method to access it here
   * if there is a use case.
   * <p>
   * Typically you shouldn't need to worry about the default value as the underlying
   * machinery will generate appropriate description and priority level based on the
   * default value.
   */
  public String getDefaultValue() {
    return getStringMember("defaultValue");
  }

  /**
   * Whether a property has a default value.
   */
  public boolean hasDefaultValue() {
    return json.has("defaultValue");
  }

  /**
   * Description if the property description would otherwise be empty.
   * <p>
   * Consider showing the property value in gray in an IDE if the description matches
   * ifEmpty.
   */
  public String getIfEmpty() {
    return getStringMember("ifEmpty");
  }

  /**
   * Description if the property [value] is null.
   */
  public String getIfNull() {
    return getStringMember("ifNull");
  }

  /**
   * Optional tooltip typically describing the property.
   * <p>
   * Example tooltip: 'physical pixels per logical pixel'
   * <p>
   * If present, the tooltip is added in parenthesis after the raw value when
   * generating the string description.
   */
  public String getTooltip() {
    return getStringMember("tooltip");
  }

  public boolean hasTooltip() {
    return json.has("tooltip");
  }

  /**
   * Whether a [value] of null causes the property to have [level]
   * [DiagnosticLevel.warning] warning that the property is missing a [value].
   */
  public boolean getMissingIfNull() {
    return getBooleanMember("missingIfNull", false);
  }

  /**
   * String representation of exception thrown if accessing the property
   * [value] threw an exception.
   */
  public String exception() {
    return getStringMember("exception");
  }

  /**
   * Whether accessing the property throws an exception.
   */
  boolean hasException() {
    return json.has("exception");
  }

  public boolean hasCreationLocation() {
    return location != null || json.has("creationLocation");
  }

  public int getLocationId() {
    return JsonUtils.getIntMember(json, "locationId");
  }

  public InspectorSourceLocation getCreationLocation() {
    if (location != null) {
      return location;
    }
    if (!hasCreationLocation()) {
      return null;
    }
    location = new InspectorSourceLocation(json.getAsJsonObject("creationLocation"), null);
    return location;
  }

  /**
   * String representation of the type of the property [value].
   * <p>
   * This is determined from the type argument `T` used to instantiate the
   * [DiagnosticsProperty] class. This means that the type is available even if
   * [value] is null, but it also means that the [propertyType] is only as
   * accurate as the type provided when invoking the constructor.
   * <p>
   * Generally, this is only useful for diagnostic tools that should display
   * null values in a manner consistent with the property type. For example, a
   * tool might display a null [Color] value as an empty rectangle instead of
   * the word "null".
   */
  public String getPropertyType() {
    return getStringMember("propertyType");
  }

  /**
   * If the [value] of the property equals [defaultValue] the priority [level]
   * of the property is downgraded to [DiagnosticLevel.fine] as the property
   * value is uninteresting.
   * <p>
   * [defaultValue] has type [T] or is [kNoDefaultValue].
   */
  public DiagnosticLevel getDefaultLevel() {
    return getLevelMember("defaultLevel", DiagnosticLevel.info);
  }

  /**
   * Whether the value of the property is a Diagnosticable value itself.
   * Optionally, properties that are themselves Diagnosticable should be
   * displayed as trees of diagnosticable properties and children.
   * <p>
   * TODO(jacobr): add helpers to get the properties and children of
   * this diagnosticable value even if getChildren and getProperties
   * would return null. This will allow showing nested data for properties
   * that don't show children by default in other debugging output but
   * could.
   */
  public boolean getIsDiagnosticableValue() {
    return getBooleanMember("isDiagnosticableValue", false);
  }

  /**
   * Service used to retrieve more detailed information about the value of
   * the property and its children and properties.
   */
  private final InspectorService.ObjectGroup inspectorService;

  /**
   * JSON describing the diagnostic node.
   */
  private final JsonObject json;

  private CompletableFuture<ArrayList<DiagnosticsNode>> children;

  private CompletableFuture<Map<String, InstanceRef>> valueProperties;

  private final boolean isProperty;

  public boolean isProperty() {
    return isProperty;
  }

  public String getStringMember(@NotNull String memberName) {
    return JsonUtils.getStringMember(json, memberName);
  }

  private boolean getBooleanMember(String memberName, boolean defaultValue) {
    if (!json.has(memberName)) {
      return defaultValue;
    }
    final JsonElement value = json.get(memberName);
    if (value instanceof JsonNull) {
      return defaultValue;
    }
    return value.getAsBoolean();
  }

  private DiagnosticLevel getLevelMember(String memberName, DiagnosticLevel defaultValue) {
    if (!json.has(memberName)) {
      return defaultValue;
    }
    final JsonElement value = json.get(memberName);
    if (value instanceof JsonNull) {
      return defaultValue;
    }
    return DiagnosticLevel.valueOf(value.getAsString());
  }

  private DiagnosticsTreeStyle getStyleMember(String memberName, DiagnosticsTreeStyle defaultValue) {
    if (!json.has(memberName)) {
      return defaultValue;
    }
    final JsonElement value = json.get(memberName);
    if (value instanceof JsonNull) {
      return defaultValue;
    }
    return DiagnosticsTreeStyle.valueOf(value.getAsString());
  }

  /**
   * Returns a reference to the value the DiagnosticsNode object is describing.
   */
  public InspectorInstanceRef getValueRef() {
    final JsonElement valueId = json.get("valueId");
    return new InspectorInstanceRef(valueId.isJsonNull() ? null : valueId.getAsString());
  }

  public boolean isEnumProperty() {
    final String type = getType();
    return type != null && type.startsWith("EnumProperty<");
  }

  /**
   * Returns a list of raw Dart property values of the Dart value of this
   * property that are useful for custom display of the property value.
   * For example, get the red, green, and blue components of color.
   * <p>
   * Unfortunately we cannot just use the list of fields from the Observatory
   * Instance object for the Dart value because much of the relevant
   * information to display good visualizations of Flutter values is stored
   * in properties not in fields.
   */
  public CompletableFuture<Map<String, InstanceRef>> getValueProperties() {
    final InspectorInstanceRef valueRef = getValueRef();
    if (valueProperties == null) {
      if (getPropertyType() == null || valueRef == null || valueRef.getId() == null) {
        valueProperties = CompletableFuture.completedFuture(null);
        return valueProperties;
      }
      if (isEnumProperty()) {
        // Populate all the enum property values.
        valueProperties = inspectorService.getEnumPropertyValues(getValueRef());
        return valueProperties;
      }

      final String[] propertyNames;
      // Add more cases here as visual displays for additional Dart objects
      // are added.
      switch (getPropertyType()) {
        case "Color":
          propertyNames = new String[]{"red", "green", "blue", "alpha"};
          break;
        case "IconData":
          propertyNames = new String[]{"codePoint"};
          break;
        default:
          valueProperties = CompletableFuture.completedFuture(null);
          return valueProperties;
      }
      valueProperties = inspectorService.getDartObjectProperties(getValueRef(), propertyNames);
    }
    return valueProperties;
  }

  public JsonObject getValuePropertiesJson() {
    return json.getAsJsonObject("valueProperties");
  }

  public boolean hasChildren() {
    return getBooleanMember("hasChildren", false);
  }

  public boolean isCreatedByLocalProject() {
    return getBooleanMember("createdByLocalProject", false);
  }

  /**
   * Whether this node is being displayed as a full tree or a filtered tree.
   */
  public boolean isSummaryTree() {
    return getBooleanMember("summaryTree", false);
  }

  /**
   * Whether this node is being displayed as a full tree or a filtered tree.
   */
  public boolean isStateful() {
    return getBooleanMember("stateful", false);
  }

  public String getWidgetRuntimeType() {
    return getStringMember("widgetRuntimeType");
  }

  /**
   * Check whether children are already available.
   */
  public boolean childrenReady() {
    return json.has("children") || (children != null && children.isDone());
  }

  public CompletableFuture<ArrayList<DiagnosticsNode>> getChildren() {
    if (children == null) {
      if (json.has("children")) {
        final JsonArray jsonArray = json.get("children").getAsJsonArray();
        final ArrayList<DiagnosticsNode> nodes = new ArrayList<>();
        for (JsonElement element : jsonArray) {
          DiagnosticsNode child = new DiagnosticsNode(element.getAsJsonObject(), inspectorService, false, parent);
          child.setParent(this);
          nodes.add(child);
        }
        children = CompletableFuture.completedFuture(nodes);
      } else  if (hasChildren()) {
        children = inspectorService.getChildren(getDartDiagnosticRef(), isSummaryTree(), this);
      }
      else {
        // Known to have no children so we can provide the children immediately.
        children = CompletableFuture.completedFuture(new ArrayList<>());
      }
    }
    return children;
  }

  /**
   * Reference the actual Dart DiagnosticsNode object this object is referencing.
   */
  public InspectorInstanceRef getDartDiagnosticRef() {
    final JsonElement objectId = json.get("objectId");
    return new InspectorInstanceRef(objectId.isJsonNull() ? null : objectId.getAsString());
  }

  /**
   * Properties to show inline in the widget tree.
   */
  public ArrayList<DiagnosticsNode> getInlineProperties() {
    if (cachedProperties == null) {
      cachedProperties = new ArrayList<>();
      if (json.has("properties")) {
        final JsonArray jsonArray = json.get("properties").getAsJsonArray();
        for (JsonElement element : jsonArray) {
          cachedProperties.add(new DiagnosticsNode(element.getAsJsonObject(), inspectorService, true, parent));
        }
        trackPropertiesMatchingParameters(cachedProperties);
      }
    }
    return cachedProperties;
  }

  public CompletableFuture<ArrayList<DiagnosticsNode>> getProperties(InspectorService.ObjectGroup objectGroup) {
    final CompletableFuture<ArrayList<DiagnosticsNode>> properties = objectGroup.getProperties(getDartDiagnosticRef());
    return properties.thenApplyAsync(this::trackPropertiesMatchingParameters);
  }

  ArrayList<DiagnosticsNode> trackPropertiesMatchingParameters(ArrayList<DiagnosticsNode> nodes) {
    // Map locations to property nodes where available.
    final InspectorSourceLocation creationLocation = getCreationLocation();
    if (creationLocation != null) {
      final ArrayList<InspectorSourceLocation> parameterLocations = creationLocation.getParameterLocations();
      if (parameterLocations != null) {
        final Map<String, InspectorSourceLocation> names = new HashMap<>();
        for (InspectorSourceLocation location : parameterLocations) {
          final String name = location.getName();
          if (name != null) {
            names.put(name, location);
          }
        }
        for (DiagnosticsNode node : nodes) {
          node.setParent(this);
          final String name = node.getName();
          if (name != null) {
            final InspectorSourceLocation parameterLocation = names.get(name);
            if (parameterLocation != null) {
              node.setCreationLocation(parameterLocation);
            }
          }
        }
      }
    }
    return nodes;
  }

  @NotNull
  public CompletableFuture<String> getPropertyDoc() {
    if (propertyDocFuture == null) {
      propertyDocFuture = createPropertyDocFurure();
    }
    return propertyDocFuture;
  }

  private CompletableFuture<String> createPropertyDocFurure() {
    final DiagnosticsNode parent = getParent();
    if (parent != null) {
      return inspectorService.toDartVmServiceValueForSourceLocation(parent.getValueRef())
        .thenComposeAsync((DartVmServiceValue vmValue) -> {
          if (vmValue == null) {
            return CompletableFuture.completedFuture(null);
          }
          return inspectorService.getPropertyLocation(vmValue.getInstanceRef(), getName())
          .thenApplyAsync((XSourcePosition sourcePosition) -> {
            if (sourcePosition != null) {
              final VirtualFile file = sourcePosition.getFile();
              final int offset = sourcePosition.getOffset();

              final Project project = getProject(file);
              if (project != null) {
                final List<HoverInformation> hovers =
                  DartAnalysisServerService.getInstance(project).analysis_getHover(file, offset);
                if (!hovers.isEmpty()) {
                  return hovers.get(0).getDartdoc();
                }
              }
            }
            return "Unable to find property source";
          });
        });
    }

    return CompletableFuture.completedFuture("Unable to find property source");
  }

  @Nullable
  private Project getProject(@NotNull VirtualFile file) {
    final FlutterApp app = inspectorService.getApp();
    return app != null ? app.getProject() : ProjectUtil.guessProjectForFile(file);
  }

  private void setCreationLocation(InspectorSourceLocation location) {
    this.location = location;
  }

  public InspectorService.ObjectGroup getInspectorService() {
    return inspectorService;
  }

  @Nullable
  public FlutterWidget getWidget() {
    return FlutterWidget.getCatalog().getWidget(getDescription());
  }

  @Nullable
  public Icon getIcon() {
    Icon icon = null;
    final FlutterWidget widget = getWidget();
    if (widget != null) {
      icon = widget.getIcon();
    }
    if (icon == null) {
      icon = iconMaker.fromWidgetName(getDescription());
    }
    return icon;
  }

  /**
   * Returns true if two diagnostic nodes are indistinguishable from
   * the perspective of a user debugging.
   * <p>
   * In practice this means that all fields but the objectId and valueId
   * properties for the DiagnosticsNode objects are identical. The valueId
   * field may change even for properties that have not changed because in
   * some cases such as the 'created' property for an element, the property
   * value is created dynamically each time 'getProperties' is called.
   */
  public boolean identicalDisplay(DiagnosticsNode node) {
    if (node == null) {
      return false;
    }
    final Set<Map.Entry<String, JsonElement>> entries = json.entrySet();
    if (entries.size() != node.json.entrySet().size()) {
      return false;
    }
    for (Map.Entry<String, JsonElement> entry : entries) {
      final String key = entry.getKey();
      if (key.equals("objectId") || key.equals("valueId")) {
        continue;
      }
      if (entry.getValue().equals(node.json.get(key))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Await a Future invoking the callback on completion on the UI thread only if the
   * InspectorService group is still alive when the Future completes.
   */
  public <T> void safeWhenComplete(CompletableFuture<T> future, BiConsumer<? super T, ? super Throwable> action) {
    inspectorService.safeWhenComplete(future, action);
  }
}
