/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import java.util.Arrays;
import java.util.List;

public class ToggleableServiceExtensionDescription<T> extends ServiceExtensionDescription {
  public ToggleableServiceExtensionDescription(
    String extension,
    String description,
    T enabledValue,
    T disabledValue,
    String enabledText,
    String disabledText
  ) {
    super(extension,
          description,
          Arrays.asList(enabledValue, disabledValue),
          Arrays.asList(enabledText, disabledText));
  }

  static int enabledIndex = 0;
  static int disabledIndex = 1;

  public T getEnabledValue() {
    @SuppressWarnings("unchecked") final List<T> values = super.getValues();
    return values.get(enabledIndex);
  }

  public T getDisabledValue() {
    @SuppressWarnings("unchecked") final List<T> values = super.getValues();
    return values.get(disabledIndex);
  }

  public String getEnabledText() {
    @SuppressWarnings("unchecked") final List<String> tooltips = super.getTooltips();
    return tooltips.get(enabledIndex);
  }

  public String getDisabledText() {
    @SuppressWarnings("unchecked") final List<String> tooltips = super.getTooltips();
    return tooltips.get(disabledIndex);
  }
}
