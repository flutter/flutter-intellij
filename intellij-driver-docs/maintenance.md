# Maintaining `intellij-driver-docs/`

## When to refresh

Update this folder when you:

- Bump **IDE Starter** / **IntelliJ Platform Gradle Plugin** versions
- Change **`integration`** test dependencies
- Hit **API or behavior changes** in `invokeAction`, `Finder`, or dialog components

## Version line in README

1. Run:

   ```bash
   ./gradlew dependencyInsight --dependency driver-sdk --configuration integrationRuntimeClasspath
   ./gradlew dependencyInsight --dependency ide-starter-driver --configuration integrationRuntimeClasspath
   ```

2. Copy the resolved **`com.jetbrains.intellij.driver:driver-sdk:<version>`** (and note **`ide-starter-driver`** if useful) into [README.md](README.md) as the **example** pin — keep the disclaimer that **`dependencyInsight` is authoritative**.

## Re-verify behavioral claims

After upgrades, re-check in **`driver-sdk-*-sources.jar`**:

- **`invokeAction`** implementation (delegation to **`ActionManager.tryToExecute`**)
- **`DEFAULT_FIND_TIMEOUT`** in **`Finder`**
- **`QueryBuilder.kt`** (attribute names and helpers — affects locators; see [ui-discovery-and-locators.md](ui-discovery-and-locators.md))
- Dialog components (**`EditRunConfigurationsDialogUiComponent`**, accessible names)

If Kotlin sources are not attached in the IDE, download **`-sources.jar`** from the same Maven coordinates as the resolved **`driver-sdk`** JAR.

## Regenerating the JAR appendix

[appendix-jar-contents.md](appendix-jar-contents.md) can be regenerated from the **`driver-sdk`** JAR:

**Windows (PowerShell):**

```powershell
.\tools\list-driver-sdk-classes.ps1
```

The script prints unique **`com.intellij.driver.sdk.*`** path prefixes. Paste or merge into the appendix (trim if too large).

**Manual one-liner** (any OS with `jar`):

```bash
jar tf path/to/driver-sdk-<version>.jar | grep '^com/intellij/driver/sdk/'
```

## Optional: full class listing

For a grep-friendly dump of every class file:

```bash
jar tf driver-sdk-<version>.jar | grep '\.class$'
```

Keep **appendix** trimmed to **packages** or **high-value class names** so the doc stays reviewable.

## Links to upstream

- [Integration tests (UI)](https://plugins.jetbrains.com/docs/intellij/integration-tests-ui.html)
- [intellij-ide-starter](https://github.com/JetBrains/intellij-ide-starter)
