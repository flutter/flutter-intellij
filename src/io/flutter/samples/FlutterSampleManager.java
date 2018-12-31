/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

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
      final URL url = FlutterSampleManager.class.getResource("index.json");
      final String contents = IOUtils.toString(url.toURI(), "UTF-8");
      final JSONArray jsonArray = new JSONArray(contents);

      for (int i = 0; i < jsonArray.length(); i++) {
        final JSONObject sample = jsonArray.getJSONObject(i);
        samples.add(new FlutterSample(sample.getString("element"),
                                      sample.getString("library"),
                                      sample.getString("id"),
                                      sample.getString("file"),
                                      sample.getString("description")));
      }
    }
    catch (URISyntaxException | IOException | JSONException e) {
      LOG.warn(e);
    }

    // Sort by name and library.
    samples.sort(Comparator.comparing(s -> (s.getElement() + s.getLibrary())));
    return samples;
  }
}
