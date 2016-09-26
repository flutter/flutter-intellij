/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

public interface DaemonListener {

  /**
   * Process JSON input from the Flutter daemon running on the <code>controller.</code>
   *
   * @param json       The JSON string read from the Flutter daemon
   * @param controller The FlutterDaemonController that controls the daemon
   */
  void daemonInput(String json, FlutterDaemonController controller);

  /**
   * Instruct the listener that it should enable device polling for the <code>controller.</code>
   *
   * @param controller The FlutterDaemonController that controls the daemon
   */
  void enableDevicePolling(FlutterDaemonController controller);
}
