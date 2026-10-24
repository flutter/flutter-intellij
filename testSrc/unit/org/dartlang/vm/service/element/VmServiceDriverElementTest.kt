/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package org.dartlang.vm.service.element

import com.google.gson.JsonObject
import junit.framework.TestCase

class VmServiceDriverElementTest : TestCase() {

  fun `test UserTagKind`() {
    val json = JsonObject().apply {
      addProperty("kind", "UserTag")
    }

    val instanceRef = InstanceRef(json)
    assertEquals(InstanceKind.UserTag, instanceRef.kind)

    val instance = Instance(json)
    assertEquals(InstanceKind.UserTag, instance.kind)
  }

  fun `test label`() {
    val json = JsonObject().apply {
      addProperty("label", "manual-test-tag")
    }

    val instanceRef = InstanceRef(json)
    assertEquals("manual-test-tag", instanceRef.label)

    val instance = Instance(json)
    assertEquals("manual-test-tag", instance.label)
  }
}
