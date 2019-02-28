/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.server.vmService;

public final class ServiceExtensionState {
  private final boolean enabled;
  private final Object value;

  public ServiceExtensionState(boolean enabled, Object value) {
    this.enabled = enabled;
    this.value = value;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Object getValue() {
    return value;
  }
}
