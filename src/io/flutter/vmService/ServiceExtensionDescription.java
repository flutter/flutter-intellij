/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.vmService;

import java.util.List;

public class ServiceExtensionDescription<T> {
  private final String extension;
  private final String description;
  private final List<T> values;
  private final List<String> tooltips;

  public ServiceExtensionDescription(
    String extension, String description, List<T> values, List<String> tooltips) {
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

  public List<T> getValues() {
    return values;
  }

  public List<String> getTooltips() {
    return tooltips;
  }

  public Class getValueClass() {
    return values.get(0).getClass();
  }
}
