/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

public interface DaemonListener {

  void daemonInput(String json, FlutterDaemonController controller);
}
