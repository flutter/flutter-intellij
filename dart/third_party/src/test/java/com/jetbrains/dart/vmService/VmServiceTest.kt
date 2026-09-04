/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.jetbrains.dart.vmService

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.BaseOutputReader
import com.jetbrains.lang.dart.sdk.DartSdkUtil
import com.jetbrains.lang.dart.util.DartTestUtils
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Success
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.VmService
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.SuccessConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.VMConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.consumer.VersionConsumer
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.RPCError
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.VM
import com.jetbrains.lang.dart.ide.runner.server.vmService.vmServiceDrivers.service.element.Version
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class VmServiceTest : BasePlatformTestCase() {

  private companion object {
    const val CONNECT_TIMEOUT_SECONDS = 5
    const val RESPONSE_TIMEOUT_SECONDS = 5
  }

  private var processHandler: KillableProcessHandler? = null
  private var vmService: VmService? = null

  override fun setUp() {
    super.setUp()
    DartTestUtils.configureDartSdk(module, myFixture.projectDisposable, true)
  }

  override fun tearDown() {
    try {
      try {
        vmService?.disconnect()
      } finally {
        processHandler?.killProcess()
      }
    } finally {
      super.tearDown()
    }
  }

  fun testVmServiceExchangesMessagesOverWebSocket() {
    val wsUri = launchDartProcessAndGetWsUri()

    val service = VmService.connect(wsUri)
    vmService = service

    // connect() already performed a getVersion handshake, so the connection is open.
    assertNotNull("VmService connection should be open", service.runtimeVersion)

    // Version should be the same as the initial one
    val version = awaitVersion(service)
    assertEquals("Major version should match the handshake", service.runtimeVersion.major, version.major)
    assertEquals("Minor version should match the handshake", service.runtimeVersion.minor, version.minor)

    // Request/response: getVM
    val vm = awaitVM(service)
    assertNotNull("VM name should be present", vm.name)

    // Subscribe to the Isolate stream and expect a Success response.
    val streamSuccess = CountDownLatch(1)
    service.streamListen(VmService.ISOLATE_STREAM_ID, object : SuccessConsumer {
      override fun received(response: Success?) = streamSuccess.countDown()
      override fun onError(error: RPCError?) {}
    })
    assertTrue(
      "streamListen(Isolate) should be acknowledged within ${RESPONSE_TIMEOUT_SECONDS}s",
      streamSuccess.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
    )
  }

  private fun awaitVersion(service: VmService): Version {
    val latch = CountDownLatch(1)
    val result = AtomicReference<Version>()
    service.getVersion(object : VersionConsumer {
      override fun received(response: Version) {
        result.set(response)
        latch.countDown()
      }
      override fun onError(error: RPCError?) = latch.countDown()
    })
    assertTrue(
      "getVersion should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
    )
    return requireNotNull(result.get()) { "getVersion returned an error" }
  }

  private fun awaitVM(service: VmService): VM {
    val latch = CountDownLatch(1)
    val result = AtomicReference<VM>()
    service.getVM(object : VMConsumer {
      override fun received(response: VM) {
        result.set(response)
        latch.countDown()
      }
      override fun onError(error: RPCError?) = latch.countDown()
    })
    assertTrue(
      "getVM should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
      latch.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
    )
    return requireNotNull(result.get()) { "getVM returned an error" }
  }

  /**
   * Launches `dart run --enable-vm-service:0 --pause-isolates-on-start <script>` and parses the
   * `ws://` URI from its stdout. `--pause-isolates-on-start` keeps the isolate alive so the test
   * has time to connect before the program exits. See DartCommandLineRunningState.java
   */
  private fun launchDartProcessAndGetWsUri(): String {
    val sdkHome = requireNotNull(System.getProperty("dart.sdk") ?: System.getenv("dart.sdk")) {
      "dart.sdk system property must be set"
    }
    val script = "${DartTestUtils.BASE_TEST_DATA_PATH}/vmService/hello.dart"

    val commandLine = GeneralCommandLine().withWorkDirectory(sdkHome)
    commandLine.exePath = DartSdkUtil.getDartExePath(sdkHome)
    commandLine.charset = StandardCharsets.UTF_8
    commandLine.addParameter("run")
    commandLine.addParameter("--enable-vm-service:0")
    commandLine.addParameter("--pause-isolates-on-start")
    commandLine.addParameter(script)

    val wsUri = AtomicReference<String>()
    val uriLatch = CountDownLatch(1)

    val handler = object : KillableProcessHandler(commandLine) {
      override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.forMostlySilentProcess()
    }
    processHandler = handler


    // Matches: "The Dart VM service is listening on http://127.0.0.1:1234/abc=/"
    val vmServiceLaunchUriPattern: Pattern =
      Pattern.compile("listening on (http://[\\d.]+:\\d+/[\\w=/-]*)")

    handler.addProcessListener(object : ProcessListener {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (uriLatch.count == 0L) return
        val matcher = vmServiceLaunchUriPattern.matcher(event.text)
        if (matcher.find()) {
          wsUri.set(httpToWs(matcher.group(1)))
          uriLatch.countDown()
        }
      }
    })
    handler.startNotify()

    PlatformTestUtil.waitWithEventsDispatching(
      "Did not receive a VM service URI within ${CONNECT_TIMEOUT_SECONDS}s",
      { uriLatch.count == 0L },
      CONNECT_TIMEOUT_SECONDS
    )
    return requireNotNull(wsUri.get()) { "Failed to parse VM service URI" }
  }

  //"http://127.0.0.1:1234/abc=/" -> "ws://127.0.0.1:1234/abc=/ws"
  private fun httpToWs(httpUri: String): String {
    val trimmed = httpUri.removeSuffix("/")
    return "ws" + trimmed.removePrefix("http") + "/ws"
  }
}
