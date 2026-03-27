# Discovering and locating UI elements in IDE integration tests

This document explains **how the IntelliJ Driver sees the UI**, **what to prefer when writing locators**, and **how to discover** selectors when you’re debugging a test. It complements [ui-testing.md](ui-testing.md) (API overview) and [agent-playbook.md](agent-playbook.md) (short checklist).

---

## 1. Mental model: Swing → XML DOM → XPath

The driver does **not** use browser-style HTML. It uses a **remote Swing hierarchy** exposed as an **XML-like DOM** string. **`SearchService`** (in `driver-sdk`) calls **`SwingHierarchyService.getSwingHierarchyAsDOM(component, onlyFrontend)`**, parses that XML, and runs **XPath** queries against it.

Implications:

- **“XPath” in tests** means XPath over this **Swing DOM**, not HTML.
- Attributes you match on (`@accessiblename`, `@visible_text`, `@class`, `@javaclass`, `@title`, `@tooltiptext`, etc.) come from how that DOM is serialized — see **`QueryBuilder`** in **`driver-sdk-*-sources.jar`** for the exact attribute names.
- **Scoped searches**: If you pass a **root `Component`** (e.g. a dialog), the hierarchy is smaller and queries are faster and less ambiguous. `Finder`/`UiComponent` implicitly search from a **search context** (often the focused window or a parent xpath).

**Official background:** [Integration tests (UI)](https://plugins.jetbrains.com/docs/intellij/integration-tests-ui.html).

---

## 2. What to prefer (decision order)

Use this order when choosing how to find a control:

| Priority | What to use | When / notes |
|----------|-------------|----------------|
| **1** | **Built-in `driver-sdk` UI types** (`ideFrame`, `welcomeScreen`, `projectView`, `editRunConfigurationsDialog`, …) | Default for standard IDE surfaces; maintained with platform. |
| **2** | **Element APIs** (`textField { }`, `popup { }`, … in `components.elements`) | Typed controls; use **`.text`** on text fields, not `toString()`. |
| **3** | **`QueryBuilder` / `xQuery { }`** (`byAccessibleName`, `byType`, `byTooltip`, `componentWithChild`, …) | Prefer over long hand-written XPath; composable and readable. |
| **4** | **Raw XPath string** `x("//div[…]")` | OK for spikes or when builders don’t cover an edge case; **fragile** — scope tightly or migrate to `QueryBuilder`. |
| **5** | **Project helpers** (e.g. `runConfigurationsDialog { }`) | One place for awkward dialogs; document rationale in KDoc. |

**XPath is not “wrong”** — it is the **underlying query language**. The recommendation is to avoid **long, hand-maintained** XPath strings when **`xQuery { }` + `QueryBuilder`** express the same thing more clearly and reuse platform attribute helpers.

---

## 3. `QueryBuilder` and `xQuery` (preferred structured locators)

In **`driver-sdk`**, `QueryBuilder` builds predicate fragments that become **`//div[ … ]`**-style paths via **`xQuery { … }`**.

Common builders (names may vary slightly by driver version — verify in **`QueryBuilder.kt`**):

| Builder | Typical meaning / DOM attribute |
|---------|-----------------------------------|
| **`byAccessibleName(text)`** | Accessibility name (screen readers, many buttons/labels). |
| **`byVisibleText(text)`** | **`visible_text`** — text shown on screen (good for buttons, static labels). |
| **`byTitle(title)`** | Window/dialog title. |
| **`byText(text)`** | **`text`** attribute where applicable. |
| **`byClass(shortName)`** | Short class name (e.g. `JBList`). |
| **`byJavaClass` / `byType(fullName)`** | Full JVM class name or hierarchy-aware match — e.g. **`ExpandableTextField`**, **`JDialog`**. |
| **`byTooltip(text)`** | **`tooltiptext`** — hover text when accessible name is unclear. |
| **`componentWithChild(parentPred, childPred)`** | Parent that **contains** a child matching another predicate (e.g. label next to field). |
| **`and` / `or` / `not` / `contains`** | Boolean combination of conditions. |

**Example (conceptual):**

```kotlin
x {
  or(
    byTooltip("Add new configuration"),
    byAccessibleName("Add New Configuration"),
  )
}
```

**Example: field next to a label** (pattern for plugin forms):

```kotlin
textField {
  and(
    xQuery {
      componentWithChild(
        byClass("javax.swing.JLabel"),
        byVisibleText("Additional args:"),
      )
    },
    xQuery { byType("com.intellij.ui.components.fields.ExpandableTextField") },
  )
}
```

Always confirm **exact** label strings in your plugin’s **`.form`** / UI code — casing and punctuation matter.

---

## 4. Raw XPath strings (`x("//div[…]")`)

**When they’re used:**

- Quick spikes, or matching attributes **`QueryBuilder` doesn’t wrap** yet.
- Very specific structural paths (rare).

**Risks:**

- DOM shape can change between IDE versions or LaF.
- Easy to match the **wrong** node (e.g. first `JTextField` in a dialog instead of “Additional args”).
- Harder to read than `byAccessibleName` / `byType`.

**Mitigation:** Scope under a known parent (`ideFrame`, dialog wrapper, or `runConfigurationsDialog` helper), and prefer migrating to **`xQuery`** once you know which attributes are stable.

---

## 5. Typed components and `x(Class, init)`

Use **`x(MyUiComponent::class.java) { … }`** when **`driver-sdk`** ships a subclass of **`UiComponent`** for that screen (e.g. **`EditRunConfigurationsDialogUiComponent`**). You get typed fields (`addNewConfigurationButton`, etc.) instead of ad-hoc queries.

Pattern:

```kotlin
ideFrame {
  editRunConfigurationsDialog {
    addNewConfigurationButton.click()
  }
}
```

If the SDK has no type for your dialog, fall back to **`RunConfigurationsDialogUI`**-style wrappers in **`testSrc/integration/.../utils/`**.

---

## 6. Text fields, buttons, lists

| Control | Typical approach |
|---------|-------------------|
| **JTextField / ExpandableTextField** | **`textField { … }`** → **`JTextFieldUI`** with **`.text`** getter/setter — **do not** assert on **`UiComponent.toString()`**. |
| **JButton / link** | **`x { byVisibleText("…") }`**, **`byAccessibleName`**, or XPath on `@text`. |
| **JBList / tree** | **`waitOneText("…")`** on list helpers where SDK provides them; or **`byClass("JBList")`** scoped under a dialog. |
| **Tool windows** | Prefer **`toolWindow("…")`**, **`projectView`**, etc., from **`IdeaFrameUI`** extensions. |

---

## 7. How to *discover* what to match (workflow)

### A. **`driver-sdk` sources JAR**

1. Resolve version: `./gradlew dependencyInsight --dependency driver-sdk --configuration integrationRuntimeClasspath`
2. Open **`driver-sdk-<ver>-sources.jar`** (same Maven coordinates as the binary JAR).
3. Read **`QueryBuilder.kt`**, **`Finder.kt`**, **`JTextFieldUI.kt`**, and **`ui/components/common/...`** for patterns used by JetBrains.

### B. **Swing hierarchy dump (same XML as XPath)**

The IDE exposes **`SwingHierarchyService.getSwingHierarchyAsDOM`**. In this repo you can use helpers such as **`dumpSwingHierarchyToConsole`** / **`dumpXPathTreeToConsole`** (see `testSrc/integration/.../utils/SwingHierarchyDebug.kt` and **`RunConfigurationsDialogUI`**) to **print the DOM** while the UI you care about is visible. Use a **non-null root `Component`** (e.g. dialog) to avoid huge logs.

**Setup:** `Setup.kt` sets **`expose.ui.hierarchy.url`** — useful for hierarchy inspection in some workflows.

### C. **Plugin UI source (this repo)**

For Flutter/plugin-specific labels and bindings:

- **`*.form`** (GUI Designer) — exact **`text`** on **`JLabel`**s, **`binding=`** on fields.
- **`TestForm.java`** / Kotlin UI — field types (**`ExpandableTextField`** vs **`JTextField`**).

Match those strings in **`byVisibleText`** / **`componentWithChild`** / **`byType`**.

### D. **Accessibility / hover in a running IDE**

If **`byAccessibleName`** is unknown, use **`byTooltip`** with the string from **hover** (watch **casing**). Combine with **`or`** for multiple spellings.

### E. **Screenshots**

**`driver.takeScreenshot(...)`** — visual confirmation of what the driver session sees (see [driver-api.md](driver-api.md)).

---

## 8. Scoping and ambiguity

- **Prefer one strategy per dialog** — don’t mix unrelated XPaths and SDK extensions in the same flow without a clear root scope.
- **“First match” bugs:** A query like “any `TextField` in the dialog” may hit **Name** before **Additional args**. Fix with **`byType`** for the concrete class, **`componentWithChild`** with a label, or a **single** `ExpandableTextField` if the form has only one.
- **Template vs non-template** — Run/Debug UI differs; lists may be empty until **Add (+)** adds a type.

---

## 9. Timing and visibility

- **`waitFound()`** — element exists in hierarchy / visible per driver rules.
- **`waitForIndicators`** — IDE not indexing / no status-bar background tasks (see [driver-api.md](driver-api.md) and SDK **`indicators.kt`**).
- **`wait(duration)`** — coarse delays; use sparingly; prefer **`waitFound`** + **`waitForIndicators`**.

---

## 10. Checklist before merging a new locator

1. Prefer **SDK** or **`QueryBuilder`** over long raw XPath.
2. Confirm strings **against real UI** or **`.form`** / hierarchy dump.
3. Scope under **dialog / frame** when possible.
4. Read **`.text`** (or the right property) for assertions on text fields.
5. Document **project helpers** in **`utils/`** with **why** the locator exists.

---

## See also

- [ui-testing.md](ui-testing.md) — `Finder`, `UiComponent`, packages overview  
- [driver-api.md](driver-api.md) — `invokeAction`, modals, screenshots  
- [agent-playbook.md](agent-playbook.md) — short agent rules  
- [maintenance.md](maintenance.md) — refreshing docs and JAR listings  
- [Integration tests (UI)](https://plugins.jetbrains.com/docs/intellij/integration-tests-ui.html) — JetBrains documentation  
