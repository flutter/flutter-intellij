/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import java.util.ArrayList;
import java.util.Arrays;

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
          new ArrayList<>(Arrays.asList(enabledValue, disabledValue)),
          new ArrayList<>(Arrays.asList(enabledText, disabledText)));
  }

  static int enabledIndex = 0;
  static int disabledIndex = 1;

  public T getEnabledValue() {
    @SuppressWarnings("unchecked") final ArrayList<T> values = (ArrayList<T>)super.getValues();
    return values.get(enabledIndex);
  }

  public T getDisabledValue() {
    @SuppressWarnings("unchecked") final ArrayList<T> values = (ArrayList<T>)super.getValues();
    return values.get(disabledIndex);
  }

  public String getEnabledText() {
    @SuppressWarnings("unchecked") final ArrayList<String> tooltips = (ArrayList<String>)super.getTooltips();
    return tooltips.get(enabledIndex);
  }

  public String getDisabledText() {
    @SuppressWarnings("unchecked") final ArrayList<String> values = (ArrayList<String>)super.getTooltips();
    return values.get(disabledIndex);
  }
}
