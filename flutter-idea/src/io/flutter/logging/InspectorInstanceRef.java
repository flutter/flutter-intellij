/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import java.util.Objects;

/**
 * Reference to a Dart object.
 * <p>
 * This class is similar to the Observatory protocol InstanceRef with the
 * difference that InspectorInstanceRef objects do not expire and all
 * instances of the same Dart object are guaranteed to have the same
 * InspectorInstanceRef id. The tradeoff is the consumer of
 * InspectorInstanceRef objects is responsible for managing their lifecycles.
 */
public record InspectorInstanceRef(String id) {

  @Override
  public boolean equals(Object other) {
    if (other instanceof InspectorInstanceRef otherRef) {
      return Objects.equals(id, otherRef.id);
    }
    return false;
  }

  @Override
  public String toString() {
    return "instance-" + id;
  }
}
