/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import java.util.ArrayList;

public class ServiceExtensionDescription<T> {
  private final String extension;
  private final String description;
  private final ArrayList<T> values;
  private final ArrayList<String> tooltips;

  public ServiceExtensionDescription(
    String extension, String description, ArrayList<T> values, ArrayList<String> tooltips) {
    this.extension = extension;
    this.description = description;
    this.values = values;
    this.tooltips = tooltips;
  }

  public String getExtension() {
    return extension;
  }

  public String getDescription() {
    return description;
  }

  public ArrayList<T> getValues() {
    return values;
  }

  public ArrayList<String> getTooltips() {
    return tooltips;
  }
}
