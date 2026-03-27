# IDE Starter integration in this repo

This document describes **how flutter-intellij wires** [IntelliJ IDE Starter](https://github.com/JetBrains/intellij-ide-starter) and the Driver for **`integration`** UI tests.

## Source set and dependencies

- **Sources:** `testSrc/integration/` (Kotlin/Java/resources).
- **Gradle:** `build.gradle.kts` defines a **`integration`** source set and adds **`TestFrameworkType.Starter`** to **`integrationImplementation`** via `intellijPlatform { … }`.

That pulls **`ide-starter-driver`** and transitively **`driver-sdk`**, **`driver-client`**, **`driver-model`**, etc.

## `Setup.kt`

`testSrc/integration/io/flutter/integrationTest/Setup.kt`:

- Overrides **`GlobalPaths`** with **`TemplatePaths`** so Starter caches artifacts under **`build/`** in the repo root (`Git.getRepoRoot().resolve("build")`).
- **`setupTestContextIC` / `setupTestContextUE` / `setupTestContextWS`** — build **`IDETestContext`** for different IDE products (`IdeProductProvider`) with pinned **build numbers** (e.g. IC/UE `252.23892.409`, WS `252.23892.411`).
- **`Starter.newContext(...)`** with **`TestCase`** and **`PluginConfigurator`**:
  - Installs the **built plugin** from **`System.getProperty("path.to.build.plugin")`**
  - Installs **Dart** from the plugin manager (`installPluginFromPluginManager("Dart", ...)`)
  - Registers **`GradleBuildTool`**
- **`applyVMOptionsPatch`** — trust projects, disable consent, enable UI hierarchy URL, new UI, JUnit autodetection, OS-specific flags (macOS file chooser, etc.).

## `integration` Gradle task

`tasks.register<Test>("integration")` in `build.gradle.kts`:

| Setting | Purpose |
|---------|---------|
| `classpath` | `sourceSets["integration"].runtimeClasspath` |
| `useJUnitPlatform { includeTags("ui") }` | Only tests tagged **`@Tag("ui")`** |
| `maxParallelForks = 1` | Sequential runs — one IDE at a time |
| `heap` | `1g`–`4g` for UI tests |
| **`path.to.build.plugin`** | Set to **`buildPlugin` output** `.zip` path |
| **`idea.home.path`** | Sandbox / IDE home from **prepare test sandbox** |
| **`allure.results.directory`** | Allure results under `build/` |
| `dependsOn(buildPlugin)` | Ensures plugin is built before tests |

## Typical test shape

```kotlin
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver

private lateinit var run: BackgroundRun

run = Setup.setupTestContextIC(javaClass.simpleName, LocalProjectInfo(projectPath)).runIdeWithDriver()

run.driver.withContext {
  ideFrame {
    waitFound()
    driver.waitForIndicators(1.minutes)
    // … Finder / invokeAction …
  }
}
```

- **`runIdeWithDriver()`** — IDE Starter API: launches IDE and exposes **`run.driver`**.
- **`BackgroundRun`** — lifecycle (`closeIdeAndWait()` in `@AfterEach`).

## Environment: Flutter SDK

Tests that create or open Flutter projects **require** **`FLUTTER_SDK`** (see `NewProject.kt` and similar helpers). On **Windows**, CLI helpers often use **`flutter.bat`**.

## System properties reference (from `build.gradle.kts`)

- **`path.to.build.plugin`** — Gradle sets automatically for `integration`.
- **`idea.home.path`** — Gradle sets for IDE resolution.
- Optional: **`idea.test.cyclic.buffer.size=0`** to avoid IntelliJ test listener conflicts.

## Further reading

- [README.md](README.md) — artifact map and version pin commands
- [architecture.md](architecture.md) — process diagram
- [agent-playbook.md](agent-playbook.md) — practical pitfalls
