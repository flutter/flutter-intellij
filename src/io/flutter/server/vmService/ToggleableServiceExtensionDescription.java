/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.server.vmService;

public class ToggleableServiceExtensionDescription<T> {
  private final String extension;
  private final T enabledValue;
  private final T disabledValue;
  private final String enabledText;
  private final String disabledText;

  public ToggleableServiceExtensionDescription(
    String extension, T enabledValue, T disabledValue, String enabledText, String disabledText) {
    this.extension = extension;
    this.enabledValue = enabledValue;
    this.disabledValue = disabledValue;
    this.enabledText = enabledText;
    this.disabledText = disabledText;
  }

  public String getExtension() {
    return extension;
  }

  public T getEnabledValue() {
    return enabledValue;
  }

  public T getDisabledValue() {
    return disabledValue;
  }

  public String getEnabledText() {
    return enabledText;
  }

  public String getDisabledText() {
    return disabledText;
  }
}