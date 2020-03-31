/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.survey;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class FlutterSurvey {
  final String uniqueId;
  final LocalDate startDate;
  final LocalDate endDate;

  final String title;
  final String urlPrefix;

  FlutterSurvey(String uniqueId, String startDate, String endDate, String title, String urlPrefix) {
    this.uniqueId = uniqueId;
    this.startDate = parseDate(startDate);
    this.endDate = parseDate(endDate);
    this.title = title;
    this.urlPrefix = urlPrefix;
  }

  private static LocalDate parseDate(String date) {
    return LocalDate.from(DateTimeFormatter.ISO_DATE_TIME.parse(date));
  }

  public static FlutterSurvey fromJson(@NotNull JsonObject json) {
    return new FlutterSurvey(json.getAsJsonPrimitive("uniqueId").getAsString(),
                             json.getAsJsonPrimitive("startDate").getAsString(),
                             json.getAsJsonPrimitive("endDate").getAsString(),
                             json.getAsJsonPrimitive("title").getAsString(),
                             json.getAsJsonPrimitive("url").getAsString()
    );
  }

  boolean isSurveyOpen() {
    final LocalDate now = LocalDate.now();
    return (now.isEqual(startDate) || now.isAfter(startDate)) && now.isBefore(endDate);
  }
}
