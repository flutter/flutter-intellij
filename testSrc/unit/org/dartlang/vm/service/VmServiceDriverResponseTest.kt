/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package org.dartlang.vm.service

import com.google.gson.JsonObject
import junit.framework.TestCase
import org.dartlang.vm.service.consumer.GetPerfettoCpuSamplesConsumer
import org.dartlang.vm.service.element.PerfettoCpuSamples
import org.dartlang.vm.service.element.RPCError
import org.dartlang.vm.service.element.Sentinel
import org.dartlang.vm.service.element.SentinelKind

class VmServiceDriverResponseTest : TestCase() {

  fun `test PerfettoCpuSamples response dispatch`() {
    val consumer = RecordingPerfettoCpuSamplesConsumer()
    val json = JsonObject().apply {
      addProperty("type", "PerfettoCpuSamples")
      addProperty("samplePeriod", 1_000)
      addProperty("maxStackDepth", 128)
      addProperty("sampleCount", 42)
      addProperty("timeOriginMicros", 3_000_000_000L)
      addProperty("timeExtentMicros", 4_000_000_000L)
      addProperty("pid", 12_345)
      addProperty("samples", "AA==")
    }

    VmService().forwardResponse(consumer, "PerfettoCpuSamples", json)

    val response = requireNotNull(consumer.response) { "Expected a PerfettoCpuSamples response" }
    assertEquals(3_000_000_000L, response.timeOriginMicros)
    assertEquals(4_000_000_000L, response.timeExtentMicros)
    assertNull(consumer.sentinel)
    assertNull(consumer.error)
  }

  fun `test PerfettoCpuSamples sentinel dispatch`() {
    val consumer = RecordingPerfettoCpuSamplesConsumer()
    val json = JsonObject().apply {
      addProperty("type", "Sentinel")
      addProperty("kind", "Collected")
      addProperty("valueAsString", "<collected>")
    }

    VmService().forwardResponse(consumer, "Sentinel", json)

    assertEquals(SentinelKind.Collected, consumer.sentinel?.kind)
    assertNull(consumer.response)
    assertNull(consumer.error)
  }

  private class RecordingPerfettoCpuSamplesConsumer : GetPerfettoCpuSamplesConsumer {
    var response: PerfettoCpuSamples? = null
    var sentinel: Sentinel? = null
    var error: RPCError? = null

    override fun received(response: PerfettoCpuSamples) {
      this.response = response
    }

    override fun received(response: Sentinel) {
      sentinel = response
    }

    override fun onError(error: RPCError) {
      this.error = error
    }
  }
}
