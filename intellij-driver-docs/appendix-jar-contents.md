# Appendix: `driver-sdk` package index (trimmed)

This appendix lists **top-level directory prefixes** under **`com.intellij.driver.sdk`** inside the **`driver-sdk`** JAR. It is **not** a full class list — use `jar tf` or `tools/list-driver-sdk-classes.ps1` for exhaustive output.

**Generated for:** `com.jetbrains.intellij.driver:driver-sdk:252.28238.7` (example resolution; run `dependencyInsight` after upgrades — see [maintenance.md](maintenance.md)).

| Prefix | Notes |
|--------|--------|
| `com.intellij.driver.sdk` | Root |
| `com.intellij.driver.sdk.application` | Application-level helpers |
| `com.intellij.driver.sdk.jdk` | JDK-related UI/services |
| `com.intellij.driver.sdk.plugins.*` | Stubs/helpers for bundled plugins (database, git4idea, gradle, maven, qodana, …) |
| `com.intellij.driver.sdk.remoteDev` | Remote development |
| `com.intellij.driver.sdk.settings` | Settings UI |
| `com.intellij.driver.sdk.ui` | **`Finder`**, queries, components |
| `com.intellij.driver.sdk.ui.components` | **`common`** (dialogs, editor, popups, tool windows), plus language/IDE-specific (`idea`, `kotlin`, `clion`, …) |
| `com.intellij.driver.sdk.ui.components.common.dialogs` | **`EditRunConfigurationsDialogUiComponent`**, other dialogs |
| `com.intellij.driver.sdk.ui.components.common.toolwindows` | **`projectView`**, other tool windows |
| `com.intellij.driver.sdk.ui.components.common.popups` | Search Everywhere–style popups |
| `com.intellij.driver.sdk.ui.components.common.editor` | Editor chrome |
| `com.intellij.driver.sdk.ui.keyboard` | Keyboard helpers |
| `com.intellij.driver.sdk.ui.remote` | Remote UI helpers |

## Regeneration

See [maintenance.md](maintenance.md) and `tools/list-driver-sdk-classes.ps1`.
