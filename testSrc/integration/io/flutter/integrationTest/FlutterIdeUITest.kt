/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ActionManager
import com.intellij.driver.sdk.getPlugin
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.coroutine.CommonScope
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.ide.IDETestContext
import io.flutter.integrationTest.utils.DartPluginSelection
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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Optional
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
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

  init {
    disableStarterProcessScans
  }

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
    recordDartPluginChoice()
    timed("kill leftover IDEs from earlier test runs") { killLeftoverTestIdes() }
    run = timed("IDE launch until Driver connects") { start(context) }
    ideLogOffset = IdeLog.offset(ideLogFile)
    timed("plugin-loaded gate") { assertPluginsLoaded() }
  }

  /** Copies the Dart plugin choice made while building the IDE context into [result]. */
  private fun recordDartPluginChoice() {
    DartPluginSelection.last?.let {
      result.dartPluginVersion = it.version
      result.dartPluginSource = it.source
      result.dartPluginBuilds = it.range
    }
  }

  /**
   * Runs [block] as harness phase [label] and records its duration in `run-result.json` (`timings`) and as a
   * `[timing]` line in the test output. Use it for setup and teardown work that isn't a spec step or oracle.
   */
  protected fun <T> timed(label: String, block: () -> T): T = result.timed(label, block)

  /** Fails unless the Flutter and Dart plugins are loaded and enabled in the running IDE, and records their versions. */
  protected fun assertPluginsLoaded() {
    if (System.getenv("FLUTTER_UI_TEST_WITHOUT_PLUGIN").toBoolean()) {
      println("FLUTTER_UI_TEST_WITHOUT_PLUGIN is set: skipping the plugin-loaded gate; the test's own checks must fail")
      result.pluginLoaded = false
      return
    }
    // The plugin set is fixed when the IDE starts, so there is nothing to wait for once Driver answers: wait only until
    // a Driver call succeeds (whatever it returns), then check once. Polling until the plugins "load" made an
    // incompatible Dart plugin cost the full timeout before failing.
    waitFor("Driver to answer plugin queries", 1.minutes, 1.seconds) {
      runCatching { driver.isPluginLoaded("io.flutter") }.isSuccess
    }
    val missing = listOf("io.flutter", "Dart").filterNot { id ->
      runCatching { driver.isPluginLoaded(id) && driver.getPlugin(id)?.isEnabled() == true }.getOrDefault(false)
    }
    if (missing.isNotEmpty()) {
      result.pluginLoaded = false
      val problems = runCatching { IdeLog.pluginLoadProblems(ideLogFile) }.getOrDefault(emptyList())
      val why = if (problems.isEmpty()) "" else " The IDE reported: ${problems.joinToString("; ")}."
      result.fail(Phase.SETUP, "plugin-loaded", "plugins not loaded or disabled: $missing.$why")
      error("Plugins not loaded or disabled in the IDE under test: $missing.$why " +
            "Check DART_PLUGIN_VERSION / DART_PLUGIN_ZIP and path.to.build.plugin / flutter.plugin.zip.")
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
    WalkthroughSteps.finish()
    if (!::run.isInitialized) {
      // The IDE never started, typically because building its context failed (for example an incompatible Dart
      // plugin). Keep the phase (PREFLIGHT) so the failure isn't reported as a teardown problem.
      recordDartPluginChoice()
      return
    }
    result.currentPhase = Phase.TEARDOWN
    val keepOpenMinutes = System.getenv("KEEP_IDE_OPEN_MINUTES")?.toLongOrNull() ?: 0L
    if (keepOpenMinutes > 0) {
      println("Keeping IDE open for $keepOpenMinutes minute(s)")
      Thread.sleep(keepOpenMinutes * 60_000)
    }
    try {
      timed("close IDE and wait") { run.closeIdeAndWait() }
    }
    finally {
      timed("idea.log and leaked-process checks") {
        try {
          checkIdeLog()
        }
        finally {
          checkLeakedProcesses()
        }
      }
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
   * Brings the IDE's windows in front of every other window on the desktop, unless one of them already has focus.
   *
   * Driver clicks with the real mouse (`java.awt.Robot`), so a click lands on whatever window is on top at that spot.
   * On Windows the IDE often opens *behind* the window you were using: Windows won't let a process that wasn't started
   * by a user action take the foreground ("foreground lock"), so it only flashes the taskbar button. Clicks then hit
   * the other window (`idea.log`: "SmoothRobot - Click was unsuccessful") and screenshots show the wrong thing. A
   * window can still raise itself by being always-on-top for a moment, so this does that for each showing IDE window
   * on the IDE's UI thread, then restores the previous always-on-top state. Harmless on Linux and macOS.
   *
   * Called with [force] after the IDE's first window appears, and without it from [dismissInterruptions], so a window
   * that loses its place while a test is polling is raised again. Never fails the test; a failure is only logged.
   *
   * @param force raise even if an IDE window reports focus. Needed right after launch: under the foreground lock
   *   Windows has been seen to report the IDE frame as focused while it sat behind a maximized terminal. Without
   *   [force] the raise is skipped when a window is focused, so polling doesn't steal focus from a popup the test has
   *   just opened (a focus change closes IDE popups).
   */
  protected fun bringIdeToFront(force: Boolean = false) {
    runCatching {
      driver.withContext(OnDispatcher.EDT) {
        val windows = driver.utility(AwtWindow::class).getWindows().filter { it.isShowing() }
        if (windows.isEmpty() || (!force && windows.any { it.isFocused() })) return@withContext
        windows.forEach { window ->
          val wasOnTop = window.isAlwaysOnTop()
          window.setAlwaysOnTop(true)
          window.toFront()
          window.requestFocus()
          if (!wasOnTop) window.setAlwaysOnTop(false)
        }
        println("Brought the IDE to the front (${windows.size} window(s))")
      }
    }.onFailure { println("Could not bring the IDE to the front: ${it.message}") }
  }

  /**
   * The parts of `java.awt.Window` that [bringIdeToFront] needs, called inside the IDE through Driver. Driver's own
   * `Window` interface has no always-on-top methods. `getWindows` is static and is called through `driver.utility`.
   */
  @Remote("java.awt.Window")
  interface AwtWindow {
    fun getWindows(): List<AwtWindow>
    fun isShowing(): Boolean
    fun isFocused(): Boolean
    fun isAlwaysOnTop(): Boolean
    fun setAlwaysOnTop(alwaysOnTop: Boolean)
    fun toFront()
    fun requestFocus()
  }

  /**
   * Closes dialogs the IDE may pop up on its own during a test (for example the trial feedback survey "Tell us about
   * your experience"), which would otherwise block the UI, and brings the IDE back to the front if another window
   * covered it. Called while polling.
   */
  protected fun dismissInterruptions() {
    bringIdeToFront()
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

  companion object {
    /**
     * Turns off Starter's `KillOutdatedProcesses` extension, which before and after every test class looks for leftover
     * IDEs by reading the command line of every process on the machine (OSHI). On Windows each scan took 2-3 minutes,
     * more than the test itself. [killLeftoverTestIdes] does the same job with `ProcessHandle` in well under a second.
     * Evaluated from the instance initializer, which (for `PER_CLASS` tests) runs before the class's BeforeAll
     * callbacks. Side effect: Starter binds IDE processes to the suite scope instead of the class scope; every test
     * closes its IDE in [tearDownIde] anyway.
     */
    private val disableStarterProcessScans: Unit by lazy { CommonScope.perSuiteScopeForIdeActivities() }

    /** Kills IDE processes still running from an earlier, aborted test run (their executable is under `ide-tests`). */
    fun killLeftoverTestIdes() {
      val leftovers = ProcessHandle.allProcesses()
        .filter { it.pid() != ProcessHandle.current().pid() }
        .filter { p ->
          val cmd = p.info().command().orElse("")
          cmd.contains("/ide-tests/") || cmd.contains("\\ide-tests\\")
        }
        .toList()
      leftovers.forEach { p ->
        println("Killing leftover test IDE process ${p.pid()} ${p.info().command().orElse("?")}")
        p.destroyForcibly()
      }
    }
  }

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
