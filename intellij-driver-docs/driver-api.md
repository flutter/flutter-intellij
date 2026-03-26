# Driver API (`driver-client` + `driver-sdk`)

## `Driver` (`com.intellij.driver.client.Driver`)

The interface is implemented by the remote driver connected to the IDE. Notable members (from `driver-client`):

| API | Purpose |
|-----|---------|
| `isConnected()` | Whether the session is live |
| `isRemDevMode()` | Remote dev / split process mode |
| `getProductVersion()` | IDE product version |
| `exitApplication()` | Ask the IDE to exit |
| `takeScreenshot(String)` | Capture screenshot (returns path or identifier; failures possible if UI not ready) |
| `service(KClass, RdTarget)` / `service(KClass, ProjectRef, RdTarget)` | Obtain IDE-side service facades |
| `utility(KClass, RdTarget)` | Utility services |
| `new(KClass, Array<Any>, RdTarget)` | Construct remote types where supported |
| `cast(Any, KClass)` | Cast remote handles |
| `withContext(OnDispatcher, LockSemantics, (Driver) -> T)` | Run block with dispatcher + locks |
| `withReadAction(OnDispatcher, (Driver) -> T)` | Read action |
| `withWriteAction((Driver) -> T)` | Write action |

Prefer **`withContext` / `withReadAction` / `withWriteAction`** when touching PSI, project model, or Swing rules require it.

## `invokeAction` (`com.intellij.driver.sdk.invokeAction`)

The SDK extends **`Driver`** with:

```kotlin
fun Driver.invokeAction(
  actionId: String,
  now: Boolean = true,
  component: Component? = null,
  place: String? = null,
  rdTarget: RdTarget? = null,
)
```

Implementation delegates to the platform **`ActionManager.tryToExecute(..., now)`** (see IntelliJ Platform sources for exact semantics).

### Parameters

| Parameter | Meaning |
|-----------|---------|
| `actionId` | Action ID as registered in the platform / plugin XML (e.g. `"editRunConfigurations"`, `"RunContext"`) |
| **`now`** | Passed through to **`tryToExecute`**: whether the action is executed **immediately** vs **queued** on the EDT. **Not** “async” in the coroutine sense. |
| `component` | Context component for `AnActionEvent`, if needed |
| `place` | Action place string (toolbar, popup, etc.) |
| `rdTarget` | Which Rd side to target when applicable |

### `now` and modal dialogs (hang scenario)

Many actions **open modal dialogs**. While a modal dialog is showing, **the EDT is blocked** inside the dialog’s event loop. From the test’s point of view, a call that **invokes such an action and then waits for the same thread to do something else** can appear to **never return** or **deadlock** until the dialog is closed.

**Mitigations:**

1. Prefer **`now = false`** only when you **intentionally** want the action queued with the next EDT chunk — verify against platform `ActionManager.tryToExecute` for your IDE version; behavior may differ by action.
2. Prefer the **driver SDK UI DSL** for known dialogs: e.g. **`IdeaFrameUI.editRunConfigurationsDialog { }`** (see `EditRunConfigurationsDialogUiComponent` in `driver-sdk`) which locates the **`JDialog`** with accessible name **`"Run/Debug Configurations"`** instead of ad-hoc XPath only.
3. Structure tests so **opening** and **closing** dialogs happen in coherent steps (same `withContext` / `ideFrame` block), and avoid stacking ambiguous `invokeAction` calls on the same dialog without closing.
4. Use **`waitForIndicators`** / `wait` after navigation so the IDE is idle before driving the next UI.

**Verify** modal-related behavior against **IntelliJ Platform `ActionManager.tryToExecute`** if you need precise queue vs immediate semantics.

## Related helpers (SDK)

Search `driver-sdk` sources / JAR for:

- **`invokeGlobalBackendAction`** — backend-targeted actions when using remote setups.
- **`invokeActionWithRetries`** — resilience when actions are flaky under load.

Exact signatures evolve with the driver version; use **`jar tf`** on `driver-sdk-*-sources.jar` or decompile in the IDE.

## `RdTarget`

`com.intellij.driver.model.RdTarget`: **`DEFAULT`**, **`FRONTEND`**, **`BACKEND`**.

Pass explicitly when the API requires a non-default target (remote dev, multi-host). Otherwise **`null`** / default usage is typical for local UI tests.
