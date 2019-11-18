/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.dartlang.analysis.server.protocol.FlutterWidgetPropertyValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A utilities class for generating analysis server json requests.
 */
public class FlutterRequestUtilities {
  private static final String FILE = "file";
  private static final String ID = "id";
  private static final String VALUE = "value";
  private static final String METHOD = "method";
  private static final String PARAMS = "params";
  private static final String OFFSET = "offset";
  private static final String SUBSCRIPTIONS = "subscriptions";

  private static final String METHOD_FLUTTER_GET_CHANGE_ADD_FOR_DESIGN_TIME_CONSTRUCTOR = "flutter.getChangeAddForDesignTimeConstructor";
  private static final String METHOD_FLUTTER_SET_SUBSCRIPTIONS = "flutter.setSubscriptions";
  private static final String METHOD_FLUTTER_GET_WIDGET_DESCRIPTION = "flutter.getWidgetDescription";
  private static final String METHOD_FLUTTER_SET_WIDGET_PROPERTY_VALUE = "flutter.setWidgetPropertyValue";

  private FlutterRequestUtilities() {
  }

  public static JsonObject generateFlutterGetWidgetDescription(String id, String file, int offset) {
    final JsonObject params = new JsonObject();
    params.addProperty(FILE, file);
    params.addProperty(OFFSET, offset);
    return buildJsonObjectRequest(id, METHOD_FLUTTER_GET_WIDGET_DESCRIPTION, params);
  }

  public static JsonObject generateFlutterSetWidgetPropertyValue(String id, int propertyId, FlutterWidgetPropertyValue value) {
    final JsonObject params = new JsonObject();
    params.addProperty(ID, propertyId);
    // An omitted value indicates that the property should be removed.
    if (value != null) {
      params.add(VALUE, value.toJson());
    }
    return buildJsonObjectRequest(id, METHOD_FLUTTER_SET_WIDGET_PROPERTY_VALUE, params);
  }

  /**
   * Generate and return a {@value #METHOD_FLUTTER_SET_SUBSCRIPTIONS} request.
   * <p>
   * <pre>
   * request: {
   *   "id": String
   *   "method": "flutter.setSubscriptions"
   *   "params": {
   *     "subscriptions": Map&gt;FlutterService, List&lt;FilePath&gt;&gt;
   *   }
   * }
   * </pre>
   */
  public static JsonObject generateAnalysisSetSubscriptions(String id,
                                                            Map<String, List<String>> subscriptions) {
    final JsonObject params = new JsonObject();
    params.add(SUBSCRIPTIONS, buildJsonElement(subscriptions));
    return buildJsonObjectRequest(id, METHOD_FLUTTER_SET_SUBSCRIPTIONS, params);
  }

  @NotNull
  private static JsonElement buildJsonElement(Object object) {
    if (object instanceof Boolean) {
      return new JsonPrimitive((Boolean)object);
    }
    else if (object instanceof Number) {
      return new JsonPrimitive((Number)object);
    }
    else if (object instanceof String) {
      return new JsonPrimitive((String)object);
    }
    else if (object instanceof List<?>) {
      final List<?> list = (List<?>)object;
      final JsonArray jsonArray = new JsonArray();
      for (Object item : list) {
        final JsonElement jsonItem = buildJsonElement(item);
        jsonArray.add(jsonItem);
      }
      return jsonArray;
    }
    else if (object instanceof Map<?, ?>) {
      final Map<?, ?> map = (Map<?, ?>)object;
      final JsonObject jsonObject = new JsonObject();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        final Object key = entry.getKey();
        // Prepare the string key.
        final String keyString;
        if (key instanceof String) {
          keyString = (String)key;
        }
        else {
          throw new IllegalArgumentException("Unable to convert to string: " + getClassName(key));
        }
        // Put the value.
        final Object value = entry.getValue();
        final JsonElement valueJson = buildJsonElement(value);
        jsonObject.add(keyString, valueJson);
      }
      return jsonObject;
    }
    throw new IllegalArgumentException("Unable to convert to JSON: " + object);
  }

  private static JsonObject buildJsonObjectRequest(String idValue, String methodValue) {
    return buildJsonObjectRequest(idValue, methodValue, null);
  }

  private static JsonObject buildJsonObjectRequest(String idValue, String methodValue,
                                                   JsonObject params) {
    final JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty(ID, idValue);
    jsonObject.addProperty(METHOD, methodValue);
    if (params != null) {
      jsonObject.add(PARAMS, params);
    }
    return jsonObject;
  }

  /**
   * Return the name of the given object, may be {@code "null"} string.
   */
  private static String getClassName(Object object) {
    return object != null ? object.getClass().getName() : "null";
  }
}
