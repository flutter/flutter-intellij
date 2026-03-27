# Run/Debug Pipeline — Platform Concepts

## The launch chain (generic)

```
RunConfiguration  →  RunProfile
                          ↓
                    RunProfileState (CommandLineState / LaunchState)
                          ↓
                    ProcessHandler  ←→  OS process
                          ↓
                    ExecutionResult (ConsoleView + ProcessHandler + actions)
                          ↓
               RunContentBuilder (run)   OR   XDebugSession (debug)
                          ↓
                    RunContentDescriptor  →  shown in Run/Debug tool window
```

---

## RunConfiguration / RunProfile

`RunConfiguration` describes what to run. It creates a `RunProfileState` when asked
to execute. The state object does the actual work.

```java
public class MyRunConfig extends RunConfigurationBase<RunConfigurationOptions> {
  @Override
  public RunProfileState getState(@NotNull Executor executor,
                                  @NotNull ExecutionEnvironment env)
      throws ExecutionException {
    return new MyLaunchState(env, this);
  }
}
```

Persist settings by overriding `writeExternal` / `readExternal` (uses JDom XML).

---

## ProgramRunner

`ProgramRunner` decides how to execute a `RunProfileState`. Declare it in `plugin.xml`:

```xml
<programRunner implementation="io.flutter.run.FlutterRunner"/>
```

```java
public class MyRunner extends GenericProgramRunner<RunnerSettings> {

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId)
        && profile instanceof MyRunConfig;
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state,
                                           @NotNull ExecutionEnvironment env)
      throws ExecutionException {
    // Build process, create console, return descriptor
    return ((MyLaunchState) state).launch(env);
  }
}
```

Flutter uses a single runner for both Run and Debug executors because it always
creates an `XDebugSession` (needed for hot reload / Observatory connection).

---

## CommandLineState / LaunchState

`CommandLineState` is the simplest `RunProfileState`. Override `startProcess()` to
return your `OSProcessHandler`, and IntelliJ handles the rest (console creation,
process attachment).

For more control (custom console, debug session), extend or bypass `CommandLineState`
and implement `execute()` directly — this is what flutter-intellij's `LaunchState` does.

```java
@Override
public ExecutionResult execute(@NotNull Executor executor,
                               @NotNull ProgramRunner<?> runner)
    throws ExecutionException {
  ProcessHandler handler = startProcess();
  ConsoleView console = createConsole(executor);
  console.attachToProcess(handler);
  return new DefaultExecutionResult(console, handler, createActions(console, handler));
}
```

---

## XDebugProcess and XDebugSession

For debug sessions, create an `XDebugProcessStarter` and pass it to
`XDebuggerManager.startSession()`:

```java
XDebugSession session = XDebuggerManager.getInstance(project)
    .startSession(env, new XDebugProcessStarter() {
      @Override
      public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
        return new MyDebugProcess(session, executionResult, ...);
      }
    });
return session.getRunContentDescriptor();
```

Key `XDebugProcess` methods to override:

| Method | When called | What to do |
|--------|-------------|------------|
| `createConsole()` | Once at session start | Return your console view |
| `registerAdditionalActions()` | Once at session start | Add toolbar buttons |
| `sessionInitialized()` | After session fully set up | Connect to debugger |
| `stop()` | When user stops debugging | Kill process, clean up |
| `startStepOver()` etc. | Breakpoint stepping | Delegate to debugger |

`getSession().getConsoleView()` returns whatever `createConsole()` returned. In
flutter-intellij this is the `DaemonConsoleView` passed in via `executionResult`.

---

## Executors

| Executor | ID constant | Use |
|----------|-------------|-----|
| `DefaultRunExecutor` | `"Run"` | Normal run |
| `DefaultDebugExecutor` | `"Debug"` | Debug run |
| `RunAnythingExecutor` | various | Run Anything panel |

Register your runner for specific executor IDs in `canRun()`.

---

## Tool window and content

The Run/Debug tool window is managed by `RunContentManager`. Each run produces a
`RunContentDescriptor` that becomes one tab. The descriptor holds:
- `getExecutionConsole()` — the primary console component
- `getProcessHandler()` — to check if the process is alive
- `getDisplayName()` — tab label

`ExecutionResult` (returned by `execute()`) is the source of truth for both the
console and the process handler. IntelliJ wraps it in a descriptor automatically
when you call `session.getRunContentDescriptor()`.

---

## For flutter-intellij-specific detail

See `docs/architecture/debug-console-pipeline.md` in the repo for:
- Full `LaunchState.launch()` sequence with class-level detail
- VM connection sequence (app.debugPort → VmServiceWrapper → VMServiceManager)
- Error display paths: app.log vs Flutter.Error vs WriteEvent
- FlutterApp state machine
- ObservatoryConnector pattern
