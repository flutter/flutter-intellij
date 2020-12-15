/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

public class DevToolsInstance {
  final public String host;
  final public int port;

  DevToolsInstance(String host, int port) {
    this.host = host;
    this.port = port;
  }
}
