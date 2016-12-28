/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.process.ProcessHandler;

public interface DaemonListener {

  /**
   * Process JSON input from the Flutter daemon running on the <code>controller.</code>
   *
   * @param json       The JSON string read from the Flutter daemon
   * @param controller The FlutterDaemonController that controls the daemon
   */
  void daemonInput(String json, FlutterDaemonController controller);

  void aboutToTerminate(ProcessHandler handler, FlutterDaemonController controller);

  void processTerminated(ProcessHandler handler, FlutterDaemonController controller);
}
