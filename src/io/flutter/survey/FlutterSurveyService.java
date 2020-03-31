/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.survey;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FlutterSurveyService {
  private static final String FLUTTER_LAST_SURVEY_CONTENT_CHECK_KEY = "FLUTTER_LAST_SURVEY_CONTENT_CHECK_KEY";
  private static final long CHECK_INTERVAL_IN_MS = TimeUnit.HOURS.toMillis(40);
  private static final PropertiesComponent properties = PropertiesComponent.getInstance();
  private static final Logger LOG = Logger.getInstance(FlutterSurveyService.class);
  private static URL CONTENT_URL;
  private static FlutterSurvey cachedSurvey;

  static {
    try {
      CONTENT_URL = new URL("https://flutter.dev/f/flutter-survey-metadata.json");
    }
    catch (MalformedURLException e) {
      // Shouldn't happen.
    }
  }

  private static boolean timeToUpdateCachedContent() {
    // Don't check more often than once a day.
    final long lastCheckedMillis = properties.getOrInitLong(FLUTTER_LAST_SURVEY_CONTENT_CHECK_KEY, 0);
    return System.currentTimeMillis() - lastCheckedMillis >= CHECK_INTERVAL_IN_MS;
  }

  static FlutterSurvey getLatestSurveyContent() {
    if (timeToUpdateCachedContent() || cachedSurvey == null) {
      cachedSurvey = fetchSurveyContent();
    }
    return cachedSurvey;
  }

  @Nullable
  private static FlutterSurvey fetchSurveyContent() {
    if (CONTENT_URL != null) {
      try {
        final String contents = readString(CONTENT_URL);
        final JsonObject json = new JsonParser().parse(contents).getAsJsonObject();
        return FlutterSurvey.fromJson(json);
      }
      catch (IOException | JsonSyntaxException e) {
        // Null content is OK in case of a transient exception.
      }
    }
    return null;
  }

  private static String readString(URL url) throws IOException {
    try (final InputStream in = url.openStream()) {
      final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      return reader.lines().collect(Collectors.joining(System.lineSeparator()));
    }
  }
}
