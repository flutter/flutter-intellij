/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FlutterSampleManager {

  private static final Logger LOG = Logger.getInstance(FlutterSampleManager.class);

  private static List<FlutterSample> SAMPLES;

  public static List<FlutterSample> getSamples() {
    if (SAMPLES == null) {
      // When we're reading from the repo and the file may be changing, consider a fresh read on each access.
      SAMPLES = loadSamples();
    }
    return SAMPLES;
  }

  private static List<FlutterSample> loadSamples() {
    final List<FlutterSample> samples = new ArrayList<>();
    try {
      // TODO(pq): replace w/ index read from repo (https://github.com/flutter/flutter/pull/25515).

      //https://docs.flutter.io/snippets/index.json
      //https://master-docs-flutter-io.firebaseapp.com/snippets/index.json

      final URL url = FlutterSampleManager.class.getResource("index.json");
      final String contents = IOUtils.toString(url.toURI(), "UTF-8");
      final JsonArray jsonArray = new JsonParser().parse(contents).getAsJsonArray();
      for (JsonElement element : jsonArray) {
        final JsonObject sample = element.getAsJsonObject();
        samples.add(new FlutterSample(sample.getAsJsonPrimitive("element").getAsString(),
                                      sample.getAsJsonPrimitive("library").getAsString(),
                                      sample.getAsJsonPrimitive("id").getAsString(),
                                      sample.getAsJsonPrimitive("file").getAsString(),
                                      sample.getAsJsonPrimitive("sourcePath").getAsString(),
                                      sample.getAsJsonPrimitive("description").getAsString()));
      }
    }
    catch (URISyntaxException | IOException e) {
      LOG.warn(e);
    }

    // Sort by display label.
    samples.sort(Comparator.comparing(FlutterSample::getDisplayLabel));
    return samples;
  }
}
