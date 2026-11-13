/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ActionManager
import com.intellij.driver.sdk.getPlugin
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.ide.IDETestContext
import io.flutter.integrationTest.utils.IdeLog
import io.flutter.integrationTest.utils.Platform
import io.flutter.integrationTest.utils.RunResult
import io.flutter.integrationTest.utils.RunResult.Phase
import io.flutter.integrationTest.utils.WalkthroughSteps
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for every Flutter plugin UI test, whatever it opens: an existing project ([ExistingProjectUITest]), the
 * New Project wizard, or just the welcome screen.
 *
 * It owns the parts of a test that the certification protocol depends on (see
 * `.agents/design-docs/agent-integration-testing.md`, sections 5.6 and 5.7):
 *
 * - **Plugin-loaded gate.** [assertPluginsLoaded] runs as soon as the IDE is up and fails the test if `io.flutter` or
 *   `Dart` isn't loaded and enabled. A test that runs without the plugin can only pass by accident.
 * - **`step` / `assertOracle` / `probe`.** Tests wrap their actions in [step] blocks and their checks in
 *   [assertOracle] blocks named after the spec's oracle parts (`O1`..`O4`). Each block records its outcome in
 *   [RunResult], which is written to `build/agent-testing/run-result.json` when the test ends, so the runner can tell a
 *   failure at the oracle (a real red) from a failure getting there (a test defect).
 * - **Log and process gates.** After the IDE closes, `idea.log` is scanned for `SEVERE`/`ERROR` entries that mention
 *   our plugins ([IdeLog]), and any `flutter`/`dart` processes the test left behind are killed and reported. Both fail
 *   the test unless `AGENT_TESTING_MODE=red`, in which case they are recorded only, so a red run is judged on the
 *   oracle alone.
 *
 * Subclasses start the IDE through [startIde], which sets [run] and [context] and performs the plugin check.
 */
@Tag("ui")
@ExtendWith(FlutterIdeUITest.ResultWatcher::class)
abstract class FlutterIdeUITest {

  protected lateinit var run: BackgroundRun
    private set

  /** The IDE Starter context of the running IDE (paths, VM options). */
  protected lateinit var context: IDETestContext
    private set

  protected val driver: Driver
    get() = run.driver

  /** This run's IDE log. IDE Starter starts each test with a fresh test home, so it only covers this run. */
  protected val ideLogFile: Path
    get() = context.paths.testHome.resolve("log").resolve("idea.log")

  /** What this test recorded; written to `run-result.json` by [ResultWatcher]. */
  val result: RunResult by lazy { RunResult(javaClass.name) }

  private var ideLogOffset = 0L
  private var stepCounter = 0

  /** `red` when the runner is certifying against a broken build; log and process gates then record instead of fail. */
  private val redMode: Boolean
    get() = System.getenv("AGENT_TESTING_MODE").equals("red", ignoreCase = true)

  // ---------------------------------------------------------------------------------------------------------------
  // Lifecycle

  /**
   * Starts the IDE with [start] (which must return the Starter [BackgroundRun] for [context]) and runs the
   * plugin-loaded gate. Everything up to the first [step] is recorded as the `SETUP` phase.
   */
  protected fun startIde(context: IDETestContext, start: (IDETestContext) -> BackgroundRun) {
    result.currentPhase = Phase.SETUP
    println(Platform.describe())
    this.context = context
    result.ide = context.ide.build
    run = start(context)
    ideLogOffset = IdeLog.offset(ideLogFile)
    assertPluginsLoaded()
  }

  /** Fails unless the Flutter and Dart plugins are loaded and enabled in the running IDE, and records their versions. */
  protected fun assertPluginsLoaded() {
    if (System.getenv("FLUTTER_UI_TEST_WITHOUT_PLUGIN").toBoolean()) {
      println("FLUTTER_UI_TEST_WITHOUT_PLUGIN is set: skipping the plugin-loaded gate; the test's own checks must fail")
      result.pluginLoaded = false
      return
    }
    waitFor("plugins to initialize", 2.minutes, 2.seconds) {
      runCatching { driver.isPluginLoaded("io.flutter") || driver.isPluginLoaded("Dart") }.getOrDefault(false)
    }
    val missing = listOf("io.flutter", "Dart").filterNot { id ->
      runCatching { driver.isPluginLoaded(id) && driver.getPlugin(id)?.isEnabled() == true }.getOrDefault(false)
    }
    if (missing.isNotEmpty()) {
      result.pluginLoaded = false
      result.fail(Phase.SETUP, "plugin-loaded", "plugins not loaded or disabled: $missing")
      error("Plugins not loaded or disabled in the IDE under test: $missing. Check path.to.build.plugin / flutter.plugin.zip / DART_PLUGIN_ZIP.")
    }
    result.pluginLoaded = true
    result.pluginVersion = runCatching { driver.getPlugin("io.flutter")?.getVersion() }.getOrNull()
    // Build identity gate: the IDE must report the version carried by the zip we asked Starter to install. Broken
    // builds are stamped "-broken-<stamp>" by build-broken-plugin.sh, so this catches a stale sandbox, an ignored
    // -Pflutter.plugin.zip, or a Marketplace copy standing in for ours, before any step runs.
    val zip = System.getProperty("flutter.plugin.zip")?.takeIf { it.isNotBlank() } ?: System.getProperty("path.to.build.plugin")
    if (zip != null) {
      result.pluginZip = zip
      result.pluginZipSha256 = runCatching { sha256(File(zip)) }.getOrNull()
      val expected = runCatching { pluginVersionInZip(File(zip)) }.getOrNull()
      if (expected != null && expected != result.pluginVersion) {
        result.fail(Phase.SETUP, "plugin-identity", "IDE loaded Flutter ${result.pluginVersion}, but $zip carries $expected")
        error("Build identity mismatch: the IDE loaded Flutter ${result.pluginVersion}, but the zip to install ($zip) carries $expected. " +
              "The IDE under test is not running the build this run is about.")
      }
    }
    println("Plugins loaded: io.flutter ${result.pluginVersion} (from $zip, sha256 ${result.pluginZipSha256}), " +
            "Dart ${runCatching { driver.getPlugin("Dart")?.getVersion() }.getOrNull()}")
  }

  /** The `<version>` in META-INF/plugin.xml of the plugin's main jar inside [zip]; what the IDE should report. */
  private fun pluginVersionInZip(zip: File): String? {
    ZipFile(zip).use { outer ->
      val jarEntry = outer.entries().asSequence().firstOrNull {
        Regex("""^[^/]+/lib/flutter-intellij[^/]*\.jar$""").matches(it.name) && !it.name.contains("searchableOptions")
      } ?: return null
      ZipInputStream(outer.getInputStream(jarEntry)).use { jar ->
        generateSequence { jar.nextEntry }.forEach { e ->
          if (e.name == "META-INF/plugin.xml") {
            val xml = jar.readBytes().toString(Charsets.UTF_8)
            return Regex("<version>([^<]*)</version>").find(xml)?.groupValues?.get(1)
          }
        }
      }
    }
    return null
  }

  private fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buf = ByteArray(1 shl 16)
      while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
      }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  @AfterEach
  fun tearDownIde() {
    result.currentPhase = Phase.TEARDOWN
    WalkthroughSteps.finish()
    if (!::run.isInitialized) return
    val keepOpenMinutes = System.getenv("KEEP_IDE_OPEN_MINUTES")?.toLongOrNull() ?: 0L
    if (keepOpenMinutes > 0) {
      println("Keeping IDE open for $keepOpenMinutes minute(s)")
      Thread.sleep(keepOpenMinutes * 60_000)
    }
    try {
      run.closeIdeAndWait()
    }
    finally {
      checkIdeLog()
      checkLeakedProcesses()
    }
  }

  private fun checkIdeLog() {
    val entries = runCatching { IdeLog.severeEntries(ideLogFile, ideLogOffset) }.getOrDefault(emptyList())
    result.ideaLogSevere += entries.map { it.lineSequence().first().take(300) }
    if (entries.isEmpty()) return
    val report = entries.joinToString("\n\n")
    println("idea.log has ${entries.size} SEVERE/ERROR entr${if (entries.size == 1) "y" else "ies"} mentioning our plugins:\n$report")
    if (!redMode) {
      result.fail(Phase.TEARDOWN, "idea.log", entries.first().lineSequence().first())
      throw AssertionError("idea.log has ${entries.size} SEVERE/ERROR entries mentioning io.flutter or the Dart plugin (see output above)")
    }
  }

  private fun checkLeakedProcesses() {
    val leaked = ProcessHandle.current().descendants()
      .filter { it.isAlive }
      .filter { p ->
        val cmd = p.info().command().map { it.lowercase() }.orElse("")
        cmd.endsWith("/flutter") || cmd.endsWith("/dart") || cmd.endsWith("\\flutter.bat") || cmd.endsWith("\\dart.exe") ||
        cmd.contains("/bin/cache/dart-sdk/bin/dart")
      }
      .toList()
    if (leaked.isEmpty()) return
    leaked.forEach { p ->
      val desc = "${p.pid()} ${p.info().commandLine().orElse(p.info().command().orElse("?"))}".take(300)
      result.leakedProcesses += desc
      println("Leaked process: $desc")
      p.destroyForcibly()
    }
    if (!redMode) {
      result.fail(Phase.TEARDOWN, "processes", "${leaked.size} flutter/dart process(es) still running after the IDE closed")
      throw AssertionError("${leaked.size} flutter/dart process(es) were still running after the IDE closed; stop the app with stopAppAndVerifyTerminated()")
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Steps, oracles, probes

  /**
   * Runs [block] as one user-level step. [title] is the spec's User interaction text; it becomes the walkthrough
   * caption and the step's name in `run-result.json`. Mark the spec's trigger step with [trigger] so the runner knows
   * which steps must complete before an oracle failure counts as red. [expect] is what a walkthrough viewer should
   * see; it defaults to the title.
   */
  protected fun <T> step(title: String, trigger: Boolean = false, expect: String = "", block: () -> T): T {
    val id = (++stepCounter).toString()
    WalkthroughSteps.step(title, expect.ifEmpty { title })
    return result.record(Phase.STEP, RunResult.Entry(id, title, trigger), result.steps, block)
  }

  /** Marks the start of a user-visible step without a block (walkthrough caption only). Prefer the block form. */
  protected fun step(title: String, expect: String) = WalkthroughSteps.step(title, expect)

  /**
   * Runs [block] as the check for oracle part [id] (`O1`..`O4`) from the spec. Assertions inside the block are the
   * oracle; a failure here, after every step completed, is what the runner accepts as a valid red. Keep each spec
   * predicate inside the block for its part, and don't put oracle checks anywhere else.
   */
  protected fun <T> assertOracle(id: String, description: String, block: () -> T): T {
    require(Regex("O[1-9]").matches(id)) { "oracle id must be O1..O9, got '$id'" }
    println("Oracle $id: $description")
    return result.record(Phase.ORACLE, RunResult.Entry(id, description), result.oracles, block)
  }

  /**
   * Dumps the Swing component hierarchy and a screenshot into `build/agent-testing/probes/`, named after [title], so
   * an agent that can't see the IDE can write locators from them. Works on every platform the IDE runs on. Delete the
   * probe calls once the locators are written; they don't belong in a certified test.
   */
  protected fun probe(title: String): Path {
    val safe = title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80)
    val dir = RunResult.defaultPath().parent.resolve("probes").resolve("${javaClass.simpleName}-${System.currentTimeMillis()}-$safe")
    Files.createDirectories(dir)
    runCatching { driver.ui.robotProvider.saveHierarchy(dir.toString(), "hierarchy.html") }
      .onFailure { println("probe: could not save hierarchy: ${it.message}") }
    runCatching {
      val shot = driver.takeScreenshot(safe)
      Files.writeString(dir.resolve("screenshot.txt"), shot ?: "(no path returned)")
    }.onFailure { println("probe: could not take screenshot: ${it.message}") }
    result.probes += dir.toString()
    println("Probe '$title' saved to $dir")
    return dir
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Actions and waits

  /**
   * Invokes action [actionId] the way a user's click would: through `ActionManager.tryToExecute`, which runs the
   * action's `update()` in the context of [component] (or the focused component) and refuses actions that are
   * disabled or hidden there. An unregistered action or a refusal fails the test with the reason, so a broken
   * `update()` or a missing `plugin.xml` entry is caught instead of silently skipped. Use this for every action a spec
   * drives by ID; pass the editor or tool window as [component] when enablement depends on context.
   */
  protected fun invokeFlutterAction(actionId: String, component: UiComponent? = null, place: String = "FlutterUITest") {
    val registered = driver.withContext(OnDispatcher.EDT) { driver.service(ActionManager::class).getAction(actionId) != null }
    if (!registered) error("Action '$actionId' is not registered. Check plugin.xml, and that the plugin loaded.")
    try {
      // The SDK's invokeAction calls tryToExecute(action, null, component, place, now) and throws if the callback was
      // rejected (disabled or invisible in this context).
      driver.invokeAction(actionId, now = true, component = component?.component, place = place)
    }
    catch (e: IllegalStateException) {
      throw AssertionError("Action '$actionId' was rejected (disabled or not visible in this context): ${e.message}", e)
    }
    println("Invoked action $actionId")
  }

  /** Waits until [condition] holds, polling every 2 seconds and dismissing IDE interruptions between polls. */
  protected fun waitUntil(message: String, timeout: Duration = 3.minutes, condition: () -> Boolean) {
    waitFor(message, timeout, 2.seconds) {
      dismissInterruptions()
      condition()
    }
  }

  /**
   * Closes dialogs the IDE may pop up on its own during a test (for example the trial feedback survey "Tell us about
   * your experience"), which would otherwise block the UI. Called while polling.
   */
  protected fun dismissInterruptions() {
    runCatching {
      driver.ui.xx("//div[@class='JButton' and (@visible_text='No, Thanks' or @visible_text='No, thanks')]").list()
        .firstOrNull()?.let {
          println("Dismissing an IDE interruption dialog")
          it.click()
        }
    }
    closeTrialTab()
  }

  /**
   * Closes the "Trial" editor tab that IntelliJ IDEA Ultimate opens on its own after it starts a free trial (logged as
   * "Automatically opened an editor tab for 'active' trial state"). It covers the editor and shows up in walkthroughs.
   * Returns true if a tab was closed.
   */
  protected fun closeTrialTab(): Boolean = runCatching {
    val close = driver.ui.xx("//div[@class='EditorTabLabel'][.//div[@visible_text='Trial']]//div[@myicon='closeSmall.svg']")
      .list().firstOrNull() ?: return false
    println("Closing IntelliJ IDEA's Trial tab")
    close.click()
    true
  }.getOrDefault(false)

  // ---------------------------------------------------------------------------------------------------------------

  /** Writes `run-result.json` after each test, recording failures that happened outside `step`/`assertOracle` blocks. */
  class ResultWatcher : TestWatcher {
    private fun result(context: ExtensionContext): RunResult? =
      (context.requiredTestInstance as? FlutterIdeUITest)?.result

    override fun testSuccessful(context: ExtensionContext) {
      result(context)?.write()
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable?) {
      result(context)?.let {
        it.fail(it.currentPhase, null, cause?.message ?: cause?.javaClass?.simpleName ?: "unknown failure")
        it.write()
      }
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
      result(context)?.let {
        it.testsAborted = 1
        it.fail(it.currentPhase, "aborted", cause?.message ?: cause?.javaClass?.simpleName ?: "aborted")
        it.write()
      }
    }

    override fun testDisabled(context: ExtensionContext, reason: Optional<String>) {
      // A disabled test never had an instance; nothing to write.
    }
  }
}
