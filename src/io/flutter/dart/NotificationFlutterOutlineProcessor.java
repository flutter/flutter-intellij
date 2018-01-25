/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.dart.server.AnalysisServerListener;
import com.google.dart.server.internal.remote.processor.NotificationProcessor;
import com.google.gson.JsonObject;

import org.dartlang.analysis.server.protocol.Outline;

/**
 * Processor for "flutter.outline" notification.
 */
public class NotificationFlutterOutlineProcessor extends NotificationProcessor {

  public NotificationFlutterOutlineProcessor(AnalysisServerListener listener) {
    super(listener);
  }

  /**
   * Process the given {@link JsonObject} notification and notify {@link #listener}.
   */
  @Override
  public void process(JsonObject response) throws Exception {
    final JsonObject paramsObject = response.get("params").getAsJsonObject();
    final String file = paramsObject.get("file").getAsString();
    final JsonObject outlineObject = paramsObject.get("outline").getAsJsonObject();
    getListener().computedOutline(file, Outline.fromJson(null, outlineObject));
  }
}
