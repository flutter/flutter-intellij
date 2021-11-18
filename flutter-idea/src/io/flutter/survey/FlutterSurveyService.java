/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.survey;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import io.flutter.utils.JsonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class FlutterSurveyService {
  private static final String FLUTTER_LAST_SURVEY_CONTENT_CHECK_KEY = "FLUTTER_LAST_SURVEY_CONTENT_CHECK_KEY";
  private static final long CHECK_INTERVAL_IN_MS = TimeUnit.HOURS.toMillis(40);
  private static final String CONTENT_URL = "https://docs.flutter.dev/f/flutter-survey-metadata.json";
  private static final PropertiesComponent properties = PropertiesComponent.getInstance();
  private static final Logger LOG = Logger.getInstance(FlutterSurveyService.class);
  private static FlutterSurvey cachedSurvey;

  private static boolean timeToUpdateCachedContent() {
    // Don't check more often than daily.
    final long currentTimeMillis = System.currentTimeMillis();
    final long lastCheckedMillis = properties.getLong(FLUTTER_LAST_SURVEY_CONTENT_CHECK_KEY, 0);
    final boolean timeToUpdateCache = currentTimeMillis - lastCheckedMillis >= CHECK_INTERVAL_IN_MS;
    if (timeToUpdateCache) {
      properties.setValue(FLUTTER_LAST_SURVEY_CONTENT_CHECK_KEY, String.valueOf(currentTimeMillis));
      return true;
    }
    return false;
  }

  @Nullable
  static FlutterSurvey getLatestSurveyContent() {
    if (timeToUpdateCachedContent()) {
      // This async call will set the survey cache when content is fetched.  (It's important that we not block the UI thread.)
      // The fetched content will get picked up in a subsequent call (on editor open or tab change).
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        cachedSurvey = fetchSurveyContent();
      });
    }
    return cachedSurvey;
  }

  @Nullable
  private static FlutterSurvey fetchSurveyContent() {
    try {
      final String contents = HttpRequests.request(CONTENT_URL).readString();
      final JsonObject json = JsonUtils.parseString(contents).getAsJsonObject();
      return FlutterSurvey.fromJson(json);
    }
    catch (IOException | JsonSyntaxException e) {
      // Null content is OK in case of a transient exception.
    }
    return null;
  }
}
