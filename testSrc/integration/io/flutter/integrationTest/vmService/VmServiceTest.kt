/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.vmService

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.BaseOutputReader
import org.dartlang.vm.service.VmService
import org.dartlang.vm.service.consumer.SuccessConsumer
import org.dartlang.vm.service.consumer.VMConsumer
import org.dartlang.vm.service.consumer.VersionConsumer
import org.dartlang.vm.service.element.RPCError
import org.dartlang.vm.service.element.Success
import org.dartlang.vm.service.element.VM
import org.dartlang.vm.service.element.Version
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class VmServiceTest {

  private companion object {
    const val CONNECT_TIMEOUT_SECONDS = 5
    const val RESPONSE_TIMEOUT_SECONDS = 5
  }

  private var processHandler: KillableProcessHandler? = null
  private var vmService: VmService? = null

  @AfterEach
  fun tearDown() {
    try {
      vmService?.disconnect()
    } finally {
      processHandler?.killProcess()
    }
  }

  @Tag("integration")
  @Test
  fun testVmServiceExchangesMessagesOverWebSocket() {
    val wsUri = launchFlutterTestAndGetWsUri()

    val service = VmService.connect(wsUri)
    vmService = service

    val runtimeVersion = service.runtimeVersion
    assertNotNull(runtimeVersion, "VmService.connect() should complete the version handshake")

    val version = awaitVersion(service)
    assertEquals(runtimeVersion.major, version.major, "Major version should match the handshake")
    assertEquals(runtimeVersion.minor, version.minor, "Minor version should match the handshake")

    val vm = awaitVM(service)
    assertNotNull(vm.name, "VM name should be present")

    val streamResponse = CountDownLatch(1)
    val streamError = AtomicReference<RPCError?>()
    service.streamListen(VmService.ISOLATE_STREAM_ID, object : SuccessConsumer {
      override fun received(response: Success?) {
        streamResponse.countDown()
      }

      override fun onError(error: RPCError?) {
        streamError.set(error)
        streamResponse.countDown()
      }
    })
    assertTrue(
      streamResponse.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS),
      "streamListen(Isolate) should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
    )
    assertNull(streamError.get(), "streamListen(Isolate) returned an error")
  }

  /**
   * Launches `flutter test --machine --start-paused test/vm_service_test.dart` and parses the
   * `ws://` URI from its `test.startedProcess` machine event. `--start-paused` keeps the test
   * isolate paused so this test has time to connect before the test runs. See `TestLaunchState.java`
   * and `FlutterSdk.flutterTest()`.
   *
   * Production launches Flutter commands with `MostlySilentColoredProcessHandler`. This test uses
   * its simpler `KillableProcessHandler` base because it parses raw machine output and does not
   * need ANSI color handling. This avoids initializing an IntelliJ test application while
   * retaining mostly-silent output polling.
   */
  private fun launchFlutterTestAndGetWsUri(): String {
    val flutterSdkHome = findFlutterSdkHome()
    val fixtureRoot = Path.of("testData", "vmService", "flutter_test").toAbsolutePath().normalize()
    val testFile = fixtureRoot.resolve("test").resolve("vm_service_test.dart")
    assertTrue(Files.isRegularFile(testFile), "Missing Flutter test fixture: $testFile")

    val isWindows = SystemInfo.isWindows
    val flutterExecutable = Path.of(flutterSdkHome, "bin", if (isWindows) "flutter.bat" else "flutter")
    assertTrue(Files.isRegularFile(flutterExecutable), "Missing Flutter executable: $flutterExecutable")

    val flutterArguments = listOf("test", "--machine", "--start-paused", "test/vm_service_test.dart")
    val command = if (isWindows) {
      listOf("cmd.exe", "/d", "/c", flutterExecutable.toString()) + flutterArguments
    } else {
      listOf(flutterExecutable.toString()) + flutterArguments
    }

    val wsUri = AtomicReference<String?>()
    val uriLatch = CountDownLatch(1)

    val commandLine = GeneralCommandLine(command)
      .withWorkDirectory(fixtureRoot.toFile())
      .withCharset(StandardCharsets.UTF_8)
      .withRedirectErrorStream(true)
    val handler = object : KillableProcessHandler(commandLine) {
      override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.forMostlySilentProcess()
    }
    processHandler = handler

    // Matches: {"event":"test.startedProcess",...,"vmServiceUri":"http://127.0.0.1:1234/abc=/"}
    val vmServiceLaunchUriPattern: Pattern = Pattern.compile(
      "\"event\"\\s*:\\s*\"test\\.startedProcess\".*?\"vmServiceUri\"\\s*:\\s*\"([^\"]+)\""
    )

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

    assertTrue(
      uriLatch.await(CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS),
      "Did not receive a VM service URI from flutter test within ${CONNECT_TIMEOUT_SECONDS}s",
    )

    return requireNotNull(wsUri.get()) { "Failed to parse VM service URI" }
  }

  private fun findFlutterSdkHome(): String {
    val sdkHome = System.getProperty("flutter.sdk")?.takeIf { it.isNotBlank() }
      ?: System.getenv("FLUTTER_ROOT")?.takeIf { it.isNotBlank() }
      ?: System.getenv("FLUTTER_SDK")?.takeIf { it.isNotBlank() }

    assumeTrue(
      sdkHome != null,
      "A real Flutter SDK is required; set -Dflutter.sdk=<path>, FLUTTER_ROOT, or FLUTTER_SDK",
    )
    return requireNotNull(sdkHome)
  }

  private fun awaitVersion(service: VmService): Version {
    val responseReceived = CountDownLatch(1)
    val result = AtomicReference<Version?>()
    val error = AtomicReference<RPCError?>()
    service.getVersion(object : VersionConsumer {
      override fun received(response: Version) {
        result.set(response)
        responseReceived.countDown()
      }

      override fun onError(responseError: RPCError?) {
        error.set(responseError)
        responseReceived.countDown()
      }
    })
    assertTrue(
      responseReceived.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS),
      "getVersion should respond within ${RESPONSE_TIMEOUT_SECONDS}s",
    )
    assertNull(error.get(), "getVersion returned an error")
    return requireNotNull(result.get()) { "getVersion returned no response" }
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
      latch.await(RESPONSE_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS),
      "getVM should respond within ${RESPONSE_TIMEOUT_SECONDS}s"
    )
    return requireNotNull(result.get()) { "getVM returned an error" }
  }

  //"http://127.0.0.1:1234/abc=/" -> "ws://127.0.0.1:1234/abc=/ws"
  private fun httpToWs(httpUri: String): String {
    val trimmed = httpUri.removeSuffix("/")
    return "ws" + trimmed.removePrefix("http") + "/ws"
  }
}

