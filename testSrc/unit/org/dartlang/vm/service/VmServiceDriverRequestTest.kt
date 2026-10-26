/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package org.dartlang.vm.service

import com.google.gson.JsonObject
import junit.framework.TestCase
import org.dartlang.vm.service.consumer.Consumer
import org.dartlang.vm.service.consumer.GetPerfettoCpuSamplesConsumer
import org.dartlang.vm.service.consumer.PerfettoTimelineConsumer
import org.dartlang.vm.service.element.PerfettoCpuSamples
import org.dartlang.vm.service.element.PerfettoTimeline
import org.dartlang.vm.service.element.RPCError
import org.dartlang.vm.service.element.Sentinel

class VmServiceDriverRequestTest : TestCase() {

  fun `test PerfettoTimeline request supports long timestamps`() {
    val service = RecordingVmService()
    val consumer = recordingConsumer()

    service.getPerfettoVMTimeline(3_000_000_000L, 4_000_000_000L, consumer)

    assertEquals("getPerfettoVMTimeline", service.method)
    assertEquals(3_000_000_000L, service.params.get("timeOriginMicros").asLong)
    assertEquals(4_000_000_000L, service.params.get("timeExtentMicros").asLong)
    assertSame(consumer, service.consumer)
  }

  fun `test PerfettoTimeline request omits optional timestamps`() {
    val service = RecordingVmService()
    val consumer = recordingConsumer()

    service.getPerfettoVMTimeline(consumer)

    assertEquals("getPerfettoVMTimeline", service.method)
    assertFalse(service.params.has("timeOriginMicros"))
    assertFalse(service.params.has("timeExtentMicros"))
    assertSame(consumer, service.consumer)
  }

  fun `test PerfettoTimeline response is dispatched to its consumer`() {
    val service = RecordingVmService()
    var received: PerfettoTimeline? = null
    val consumer = object : PerfettoTimelineConsumer {
      override fun received(response: PerfettoTimeline) {
        received = response
      }

      override fun onError(error: RPCError) = Unit
    }
    val json = JsonObject().apply {
      addProperty("trace", "AA==")
      addProperty("timeOriginMicros", 3_000_000_000L)
      addProperty("timeExtentMicros", 4_000_000_000L)
    }

    service.forwardResponse(consumer, "PerfettoTimeline", json)

    assertNotNull(received)
    assertEquals("AA==", received?.trace)
  }

  fun `test PerfettoCpuSamples request supports long timestamps`() {
    val service = RecordingVmService()
    val consumer = perfettoCpuSamplesConsumer()

    service.getPerfettoCpuSamples("isolates/1", 3_000_000_000L, 4_000_000_000L, consumer)

    assertEquals("getPerfettoCpuSamples", service.method)
    assertEquals("isolates/1", service.params.get("isolateId").asString)
    assertEquals(3_000_000_000L, service.params.get("timeOriginMicros").asLong)
    assertEquals(4_000_000_000L, service.params.get("timeExtentMicros").asLong)
    assertSame(consumer, service.consumer)
  }

  fun `test PerfettoCpuSamples request omits optional timestamps`() {
    val service = RecordingVmService()
    val consumer = perfettoCpuSamplesConsumer()

    service.getPerfettoCpuSamples("isolates/1", consumer)

    assertEquals("getPerfettoCpuSamples", service.method)
    assertEquals("isolates/1", service.params.get("isolateId").asString)
    assertFalse(service.params.has("timeOriginMicros"))
    assertFalse(service.params.has("timeExtentMicros"))
    assertSame(consumer, service.consumer)
  }

  fun `test PerfettoCpuSamples request supports only time origin`() {
    val service = RecordingVmService()

    service.getPerfettoCpuSamples("isolates/1", 3_000_000_000L, null, perfettoCpuSamplesConsumer())

    assertEquals(3_000_000_000L, service.params.get("timeOriginMicros").asLong)
    assertFalse(service.params.has("timeExtentMicros"))
  }

  fun `test PerfettoCpuSamples request supports only time extent`() {
    val service = RecordingVmService()

    service.getPerfettoCpuSamples("isolates/1", null, 4_000_000_000L, perfettoCpuSamplesConsumer())

    assertFalse(service.params.has("timeOriginMicros"))
    assertEquals(4_000_000_000L, service.params.get("timeExtentMicros").asLong)
  }

  private fun recordingConsumer() = object : PerfettoTimelineConsumer {
    override fun received(response: PerfettoTimeline) = Unit
    override fun onError(error: RPCError) = Unit
  }

  private fun perfettoCpuSamplesConsumer() = object : GetPerfettoCpuSamplesConsumer {
    override fun received(response: PerfettoCpuSamples) = Unit
    override fun received(response: Sentinel) = Unit
    override fun onError(error: RPCError) = Unit
  }

  private class RecordingVmService : VmService() {
    lateinit var method: String
    lateinit var params: JsonObject
    lateinit var consumer: Consumer

    override fun request(method: String, params: JsonObject, consumer: Consumer) {
      this.method = method
      this.params = params
      this.consumer = consumer
    }
  }
}
