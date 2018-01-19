/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.google.gson.JsonObject;

public interface DartAnalysisServerServiceExResponseListener {
  void onResponse(JsonObject json);
}
