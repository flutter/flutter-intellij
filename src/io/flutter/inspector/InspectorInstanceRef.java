/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

/**
 * Reference to a Dart object.
 *
 * This class is similar to the Observatory protocol InstanceRef with the
 * difference that InspectorInstanceRef objects do not expire and all
 * instances of the same Dart object are guaranteed to have the same
 * InspectorInstanceRef id. The tradeoff is the consumer of
 * InspectorInstanceRef objects is responsible for managing their lifecycles.
 */
public class InspectorInstanceRef {
  public InspectorInstanceRef(String id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object other) {
    //noinspection SimplifiableIfStatement
    if (other instanceof InspectorInstanceRef) {
      return ((InspectorInstanceRef)other).id.equals(id);
    }
    return false;
  }

  @Override
  public String toString() {
    return "instance-" + id;
  }

  public String getId() {
    return id;
  }

  private final String id;
}
