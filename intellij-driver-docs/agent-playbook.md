# Agent playbook (short rules)

Use this as a **checklist** when adding or fixing UI integration tests in this repo.

## Actions and modals

1. **`invokeAction("…")` always passes `now`** (default **`true`**) through to **`ActionManager.tryToExecute`**. If a test “hangs” after an action, suspect a **modal dialog** blocking the EDT.
2. For **Run/Debug Configurations**, prefer **`editRunConfigurationsDialog { }`** / **`EditRunConfigurationsDialogUiComponent`** from **`driver-sdk`**, or this repo’s **`runConfigurationsDialog { }`** helper — **not** a mix of blind `invokeAction` and XPath without a close path.
3. After heavy IDE work, call **`waitForIndicators`** (with a generous timeout) before fragile UI steps.

## Discovery workflow

1. **Resolved versions:** `./gradlew dependencyInsight --dependency driver-sdk --configuration integrationRuntimeClasspath`
2. **API surface:** `jar tf ~/.gradle/caches/.../driver-sdk-<ver>.jar | findstr /i something` (or use `tools/list-driver-sdk-classes.ps1`)
3. **Kotlin sources:** download **`driver-sdk-<ver>-sources.jar`** from the same Maven coordinates and read **`Finder.kt`**, **`ActionManager.kt`** (or equivalent `invokeAction` file), dialog components under **`com.intellij.driver.sdk.ui.components.common`**
4. **Bytecode quick check:** `javap -classpath driver-client-…jar com.intellij.driver.client.Driver`

## Prefer SDK over invention

- Use **`ideFrame`**, **`projectView`**, **`welcomeScreen`**, and packaged **dialog** / **tool window** components when they exist.
- Add **project helpers** under `testSrc/integration/.../utils/` only when the SDK does not cover a stable selector — document the **why** in the helper’s KDoc.

## Platform specifics

- **Windows:** use **`flutter.bat`** in **`ProcessBuilder`** for CLI; **`FLUTTER_SDK`** must be set for flows that need the SDK path in UI.
- **Paths:** avoid mixing raw strings and **`Path`** in ways that produce **`InvalidPathException`** in teardown logs; prefer `Paths.get` / normalized paths consistently.

## Common noise vs real failures

- **`TakeScreenshotCommand` NPE** or similar during shutdown — often **non-fatal** noise; confirm whether the test assertion failed independently.
- **InvalidPathException** in JVM shutdown hooks — log noise; fix only if it masks real failures.

## Tags and Gradle

- UI tests must be tagged **`@Tag("ui")`** to run under **`./gradlew integration`**.
