/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.ide.starter.utils.Git
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.walk

/**
 * Records what a UI test did and writes it as `build/agent-testing/run-result.json` when the test ends.
 *
 * The file is the contract between a test and `agent-testing-run-integration-test`: the runner's
 * `parse_run_result.py` reads it to decide whether a failure was a valid red (the oracle failed after every step and
 * the pre-state passed), a test defect (a step or setup failed), or an environment problem. Tests don't write to this
 * directly; they call `step`, `assertOracle`, and `probe` on `FlutterIdeUITest`, which record here.
 *
 * Phases, in the order a test passes through them:
 * - `PREFLIGHT`: before the IDE is started (SDK, display, plugin zip).
 * - `SETUP`: IDE starting, project opening, plugin-loaded check.
 * - `STEP`: inside a `step { }` block.
 * - `ORACLE`: inside an `assertOracle { }` block.
 * - `TEARDOWN`: closing the IDE, log and process checks.
 * - `FREEZE`: the test timed out and a thread dump showed the named frame (set by the freeze oracle).
 * - `NONE`: no failure.
 */
class RunResult(val testName: String) {

  enum class Phase { PREFLIGHT, SETUP, STEP, ORACLE, TEARDOWN, FREEZE, NONE }

  data class Entry(val id: String, val title: String, val trigger: Boolean = false) {
    var status: String = "pending"
    /** `assertion` when the block failed on an AssertionError (a real check), `error` for any other exception. */
    var kind: String? = null
    var message: String? = null
    var millis: Long = 0
  }

  data class Freeze(val confirmed: Boolean, val thread: String, val frame: String, val dumps: Int)

  var ide: String? = null
  var platform: String = System.getProperty("os.name") ?: "unknown"
  var bundleSha256: String? = null
  var pluginLoaded: Boolean = false
  var pluginVersion: String? = null
  var pluginZip: String? = null
  var pluginZipSha256: String? = null
  var testsAborted: Int = 0
  var failurePhase: Phase = Phase.NONE
  var failedId: String? = null
  var failedKind: String? = null
  var failureMessage: String? = null
  var freeze: Freeze? = null
  val ideaLogSevere = mutableListOf<String>()
  val leakedProcesses = mutableListOf<String>()
  val steps = mutableListOf<Entry>()
  val oracles = mutableListOf<Entry>()
  val probes = mutableListOf<String>()

  /** The phase the test is in right now; `fail` uses it when a block doesn't report its own phase. */
  @Volatile
  var currentPhase: Phase = Phase.PREFLIGHT

  private val startNanos = System.nanoTime()
  private var written = false

  /** Records a failure in [phase] unless an earlier failure was already recorded (the first failure is the one that counts). */
  fun fail(phase: Phase, id: String?, message: String?, kind: String = "assertion") {
    if (failurePhase != Phase.NONE) return
    failurePhase = phase
    failedId = id
    failedKind = kind
    failureMessage = message?.let(::firstLine)
  }

  /** Runs [block] as step or oracle [entry] in [phase], recording status, timing, and the first failure. */
  fun <T> record(phase: Phase, entry: Entry, list: MutableList<Entry>, block: () -> T): T {
    list += entry
    val previous = currentPhase
    currentPhase = phase
    val started = System.nanoTime()
    try {
      val result = block()
      entry.status = "ok"
      return result
    }
    catch (t: Throwable) {
      entry.status = "failed"
      entry.kind = if (isAssertion(t)) "assertion" else "error"
      entry.message = summarize(t)
      fail(phase, entry.id, "${entry.title}: ${summarize(t)}", entry.kind!!)
      throw t
    }
    finally {
      entry.millis = (System.nanoTime() - started) / 1_000_000
      currentPhase = previous
    }
  }

  /** Writes the JSON file. Safe to call more than once; only the first call writes. */
  fun write(path: Path = defaultPath()) {
    if (written) return
    written = true
    Files.createDirectories(path.parent)
    Files.writeString(path, toJson())
    println("run-result.json written to $path (phase=$failurePhase id=$failedId)")
  }

  fun toJson(): String {
    val wall = (System.nanoTime() - startNanos) / 1_000_000_000.0
    val sb = StringBuilder("{\n")
    fun field(name: String, value: Any?, last: Boolean = false) {
      sb.append("  ").append(q(name)).append(": ").append(json(value)).append(if (last) "\n" else ",\n")
    }
    field("test", testName)
    field("ide", ide)
    field("platform", platform)
    field("bundleSha256", bundleSha256)
    field("pluginLoaded", pluginLoaded)
    field("pluginVersion", pluginVersion)
    field("pluginZip", pluginZip)
    field("pluginZipSha256", pluginZipSha256)
    field("testsStarted", 1)
    field("testsSucceeded", if (failurePhase == Phase.NONE) 1 else 0)
    field("testsAborted", testsAborted)
    field("steps", steps.map(::entryJson))
    field("oracles", oracles.map(::entryJson))
    field("probes", probes)
    field("failurePhase", failurePhase.name)
    field("failedId", failedId)
    field("failedKind", failedKind)
    field("failureMessage", failureMessage)
    field("ideaLogSevere", ideaLogSevere)
    field("leakedProcesses", leakedProcesses)
    field("freeze", freeze?.let {
      linkedMapOf("confirmed" to it.confirmed, "thread" to it.thread, "frame" to it.frame, "dumps" to it.dumps)
    })
    field("wallClockSeconds", String.format(java.util.Locale.ROOT, "%.1f", wall).toDouble(), last = true)
    return sb.append("}\n").toString()
  }

  private fun entryJson(e: Entry): Map<String, Any?> = linkedMapOf(
    "id" to e.id, "title" to e.title, "trigger" to e.trigger, "status" to e.status, "kind" to e.kind, "message" to e.message, "millis" to e.millis,
  )

  companion object {
    private val ansi = Regex("\u001B\\[[;\\d]*m")

    /** First non-blank line of [text], without ANSI colour codes, at most 500 characters. */
    fun firstLine(text: String): String =
      text.replace(ansi, "").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("----") }?.take(500) ?: ""

    /**
     * True when [t], or any cause under it, is an AssertionError: the block failed on one of its own checks, not on
     * an exception from the harness or Driver. Starter wraps failures in `DriverWithContextError`, so the cause chain
     * is what counts.
     */
    fun isAssertion(t: Throwable): Boolean {
      var cause: Throwable? = t
      while (cause != null) {
        if (cause is AssertionError) return true
        cause = cause.cause?.takeIf { it !== cause }
      }
      return false
    }

    /**
     * The most useful one-line description of [t]: the innermost assertion error's message if there is one (Starter
     * wraps failures in `DriverWithContextError`), otherwise the outermost message, otherwise the class name.
     */
    fun summarize(t: Throwable): String {
      var cause: Throwable? = t
      var assertion: Throwable? = null
      while (cause != null) {
        if (cause is AssertionError) assertion = cause
        cause = cause.cause?.takeIf { it !== cause }
      }
      val chosen = assertion ?: t
      return chosen.message?.let(::firstLine)?.ifEmpty { null } ?: chosen.javaClass.simpleName
    }

    fun defaultPath(): Path = Git.getRepoRoot().resolve("build").resolve("agent-testing").resolve("run-result.json")

    /**
     * SHA-256 over the given files and directories (sorted, path-relative), so that red and green runs can prove
     * they ran the same spec, test, and overlay. Missing paths are skipped.
     */
    fun bundleHash(vararg paths: Path): String {
      val digest = MessageDigest.getInstance("SHA-256")
      val files = paths.filter { it.exists() }.flatMap { p ->
        if (p.isDirectory()) p.walk().filter { !it.isDirectory() }.sortedBy { it.toString() }.toList() else listOf(p)
      }
      for (f in files) {
        digest.update(f.fileName.toString().toByteArray())
        digest.update(f.readBytes())
      }
      return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    private fun json(v: Any?): String = when (v) {
      null -> "null"
      is String -> q(v)
      is Boolean, is Number -> v.toString()
      is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { q(it.key.toString()) + ": " + json(it.value) }
      is Iterable<*> -> v.joinToString(", ", "[", "]") { json(it) }
      else -> q(v.toString())
    }
  }
}
