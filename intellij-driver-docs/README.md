# IntelliJ Driver SDK — project reference

This folder holds **maintained, project-oriented** notes for writing and debugging UI integration tests that use the **IntelliJ Driver** (remote control of the IDE) together with **IDE Starter** (orchestration: download IDE, install plugins, launch process).

It complements the official guides:

- [Integration tests (UI)](https://plugins.jetbrains.com/docs/intellij/integration-tests-ui.html)
- [IntelliJ IDE Starter](https://github.com/JetBrains/intellij-ide-starter)

## What the driver is

The **driver** is a bridge from your **test JVM** (Gradle `integration` task) into the **running IDE**: you get a [`Driver`](driver-api.md) handle to run actions, query services, drive the UI via [`Finder`](ui-testing.md), and take screenshots.

Artifacts on the **`integration`** classpath (resolved transitively from IDE Starter):

| Artifact | Role |
|----------|------|
| `com.jetbrains.intellij.tools:ide-starter-driver` | Launches the IDE, wires the driver connection, `runIdeWithDriver()`, `BackgroundRun` |
| `com.jetbrains.intellij.driver:driver-sdk` | Kotlin DSL: `invokeAction`, `Finder`, `ideFrame`, dialog/tool-window UI components, `waitForIndicators`, etc. |
| `com.jetbrains.intellij.driver:driver-client` | `com.intellij.driver.client.Driver` interface (remote API surface) |
| `com.jetbrains.intellij.driver:driver-model` | Shared types (e.g. `RdTarget`, dispatchers) |

See [architecture.md](architecture.md) for how these pieces connect.

## Documentation map

| Doc | Contents |
|-----|----------|
| [architecture.md](architecture.md) | Test process vs IDE, threading, `RdTarget`, artifact diagram |
| [driver-api.md](driver-api.md) | `Driver`, `invokeAction` and **`now`**, modals, `RdTarget` |
| [ui-testing.md](ui-testing.md) | `Finder` / `UiComponent`, built-in frames and dialogs |
| [ui-discovery-and-locators.md](ui-discovery-and-locators.md) | **How to find UI**: XPath vs `QueryBuilder`, discovery workflow, scoping, text fields |
| [ide-starter-integration.md](ide-starter-integration.md) | How **this repo** wires Starter + Gradle (`Setup.kt`, `integration` task) |
| [agent-playbook.md](agent-playbook.md) | Short rules for humans and coding agents |
| [maintenance.md](maintenance.md) | Refreshing docs when upgrading IDE / driver |
| [appendix-jar-contents.md](appendix-jar-contents.md) | Trimmed package index of `driver-sdk` (for grep) |

## Version pin (current resolved driver)

Driver JARs are **not** declared directly in `build.gradle.kts`; they come in with **`intellijPlatform { testFramework(TestFrameworkType.Starter, ...) }`** via `ide-starter-driver`.

To see the **exact** versions Gradle resolves for your checkout:

```bash
./gradlew dependencyInsight --dependency driver-sdk --configuration integrationRuntimeClasspath
./gradlew dependencyInsight --dependency driver-client --configuration integrationRuntimeClasspath
```

Example (one machine, one Gradle lock state): **`com.jetbrains.intellij.driver:driver-sdk:252.28238.7`** pulled by **`com.jetbrains.intellij.tools:ide-starter-driver:252.28238.7`**. Your output may differ slightly after upgrades; always use `dependencyInsight` as the source of truth.
