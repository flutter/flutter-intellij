# UI testing: `Finder`, `UiComponent`, and built-in components

For a **deeper guide** on choosing locators (XPath vs `QueryBuilder`, how to discover attributes, hierarchy dumps, and avoiding ambiguous matches), see **[ui-discovery-and-locators.md](ui-discovery-and-locators.md)**.

## `Finder` and `UiComponent`

`com.intellij.driver.sdk.ui.Finder` is the entry point for **locating UI** inside the IDE. Typical patterns:

- **`x(...)`** — find a **single** component (throws / times out if not found — see `waitFound`).
- **`xx(...)`** — find **multiple** matches.

Overloads include:

- XPath-style **`String`**
- **`QueryBuilder` lambda** (DSL for building queries)
- **`x(Class<T>, init)`** — typed `UiComponent` subclass

**Default find timeout** in `Finder` sources: **`DEFAULT_FIND_TIMEOUT` = 15 seconds** (confirm in `driver-sdk-*-sources.jar` if upgrading).

`com.intellij.driver.sdk.ui.components.UiComponent` wraps located component **data** and exposes:

- Child finds via **`x` / `xx`**
- **`waitFound()`** (from companion) to block until visible
- **`click()`**, **keyboard { }** blocks, integration with **robot** where exposed

## Query builders

Rich queries live under **`com.intellij.driver.sdk.ui`** (e.g. **`QueryBuilder`**, **`xQuery`**, **`byAccessibleName`**, **`byType`**, etc.). Prefer these over fragile raw XPath when the SDK provides them.

**Source of truth:** `driver-sdk-*-sources.jar` (`Finder.kt`, `QueryBuilder` and related files).

## Keyboard and robot

`Finder` / `UiComponent` expose **keyboard** DSLs for **`typeText`**, shortcuts, etc., and **robot**-level low-level input where needed. Use the same **threading** rules as the rest of the driver (`withContext` / EDT when required).

## Built-in high-level components (`driver-sdk`)

Packages under **`com.intellij.driver.sdk.ui.components`** (especially **`common`**) provide first-class wrappers:

| Area | Package / examples |
|------|------------------|
| **IDE shell** | `components.common` — **`ideFrame`**, **`welcomeScreen`**, etc. |
| **Tool windows** | `components.common.toolwindows` — e.g. **`projectView`** |
| **Dialogs** | `components.common.dialogs` — e.g. **`EditRunConfigurationsDialogUiComponent`**; extension **`IdeaFrameUI.editRunConfigurationsDialog { }`** (dialog accessible name **`"Run/Debug Configurations"`**) |
| **Popups** | `components.common.popups` — e.g. Search Everywhere / similar |
| **Editor** | `components.common.editor` |

IDE-specific and language-specific extras live under sibling packages (`idea`, `kotlin`, `python`, `clion`, …).

### Prefer SDK dialog components over one-off XPath

For **Run/Debug Configurations**, the SDK’s **`editRunConfigurationsDialog`** / **`EditRunConfigurationsDialogUiComponent`** is preferred over ad-hoc XPath. This repo also defines **`runConfigurationsDialog { }`** in `testSrc/integration/.../RunConfigurationsDialog.kt` as a **project-specific** helper that finds the dialog by title/title fragment; keep one strategy per test flow to avoid flakiness.

## Waiting for background work

Use **`waitForIndicators`** (and **`wait`** from Kotlin-time helpers in `driver-sdk`) so indexing, Gradle, analysis, or Flutter tooling finish before assertions.

Example pattern in this repo: `driver.waitForIndicators(1.minutes)` inside `ideFrame { }` before driving UI.

## Official doc link

- [Integration tests (UI)](https://plugins.jetbrains.com/docs/intellij/integration-tests-ui.html)
