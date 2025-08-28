/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import org.jetbrains.annotations.NotNull;

/**
 * These are requests to DTD. More are listed here: <a href="https://github.com/dart-lang/sdk/blob/main/pkg/dtd/lib/src/constants.dart">...</a>
 *
 * Also see the protocol documentation: <a href="https://github.com/dart-lang/sdk/blob/main/pkg/dtd_impl/dtd_protocol.md">...</a>
 */
public enum DtdRequest {
  REGISTER_VM_SERVICE("ConnectedApp.registerVmService"),
  UNREGISTER_VM_SERVICE("ConnectedApp.unregisterVmService");

  final public @NotNull String type;

  DtdRequest(@NotNull String type) {
    this.type = type;
  }
}
