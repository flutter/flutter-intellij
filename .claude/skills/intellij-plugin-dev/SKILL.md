---
name: intellij-plugin-dev
description: >
  Expert knowledge of IntelliJ Platform plugin architecture, APIs, and development
  patterns. Use this skill whenever working on IntelliJ IDEA plugins, Android Studio
  plugins, or any JetBrains IDE plugin — including reading, writing, debugging, or
  explaining plugin code. Triggers on questions about plugin.xml structure, extension
  points, IntelliJ Platform SDK APIs (PSI, VFS, ProcessHandler, ConsoleView,
  RunConfiguration, XDebugProcess, etc.), Gradle plugin builds, IntelliJ test
  framework, threading rules (EDT), or anything related to the flutter-intellij
  plugin codebase. Also use for diagnosing why plugin behaviour differs across
  platforms (Windows vs macOS vs Linux), understanding run/debug process pipelines,
  or navigating the relationship between IntelliJ's run system and external debuggers.
---

# IntelliJ Platform Plugin Development

## Plugin structure

```
my-plugin/
├── src/main/java/           # Plugin source
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml       # Plugin descriptor — single source of truth
├── src/test/java/           # Unit tests
└── build.gradle.kts         # Gradle IntelliJ Plugin build
```

### plugin.xml

```xml
<idea-plugin>
  <id>com.example.myplugin</id>
  <name>My Plugin</name>
  <depends>com.intellij.modules.platform</depends>
  <depends>Dart</depends>                           <!-- optional plugin dep -->

  <extensions defaultExtensionNs="com.intellij">
    <projectService serviceImplementation="com.example.MyProjectService"/>
    <runConfigurationProducer implementation="com.example.MyRunConfigProducer"/>
    <programRunner implementation="com.example.MyRunner"/>
    <toolWindow id="My Tool" anchor="bottom"
                factoryClass="com.example.MyToolWindowFactory"/>
  </extensions>

  <actions>
    <action id="MyAction" class="com.example.MyAction" text="Do Something">
      <add-to-group group-id="ToolsMenu" anchor="last"/>
    </action>
  </actions>
</idea-plugin>
```

- `<depends>` — hard dependency; missing = plugin disabled
- `optional="true" config-file="..."` — soft dependency; plugin works without it
- `<extensionPoints>` — declare your own extension points for other plugins to implement

---

## Services

Singletons scoped to Application, Project, or Module. The standard way to hold shared state.

```java
// Declaration in plugin.xml
<projectService serviceImplementation="com.example.MyProjectService"/>
<applicationService serviceImplementation="com.example.MyAppService"/>

// Retrieval
MyProjectService svc = project.getService(MyProjectService.class);
MyAppService app = ApplicationManager.getApplication().getService(MyAppService.class);

// Modern annotation form (IJ 2023+)
@Service(Service.Level.PROJECT)
public final class MyProjectService implements Disposable { ... }
```

---

## Disposables

IntelliJ uses a tree of `Disposable` objects for lifecycle. Always register resources
against a parent so they are cleaned up automatically.

```java
// BAD — listener lives forever, leaks memory
messageBus.connect().subscribe(TOPIC, listener);

// GOOD — auto-unsubscribes when project is disposed
messageBus.connect(project).subscribe(TOPIC, listener);

// Or explicitly
Disposer.register(parentDisposable, childDisposable);
```

Services that implement `Disposable` have `dispose()` called automatically.
Tool windows get a `toolWindow.getDisposable()` parent to register against.

---

## Threading rules

**EDT (Event Dispatch Thread)** — all UI work must happen here. Background work must not block it.

```java
// Queue work on EDT (non-blocking)
ApplicationManager.getApplication().invokeLater(() -> { /* UI */ });

// Run on EDT and wait
ApplicationManager.getApplication().invokeAndWait(() -> { /* UI */ });

// Background thread pool
ApplicationManager.getApplication().executeOnPooledThread(() -> { /* bg work */ });

// Read PSI/VFS (can be on any thread, but needs a read action)
ApplicationManager.getApplication().runReadAction(() -> { /* read PSI */ });

// Write PSI/VFS (must be on EDT)
WriteCommandAction.runWriteCommandAction(project, () -> { /* modify PSI */ });
```

`ProcessListener` callbacks (`onTextAvailable` etc.) run on a **background thread**.
`ConsoleViewImpl.print()` is thread-safe and posts to EDT internally.

---

## Run/Debug pipeline

### Core chain

```
RunConfiguration → RunProfileState (LaunchState)
                        ↓
                   ProcessHandler ←→ OS process
                        ↓
                   ExecutionResult (ConsoleView + ProcessHandler)
                        ↓
              RunContentBuilder (run)  OR  XDebugSession (debug)
```

### RunConfiguration

```java
public class MyRunConfig extends RunConfigurationBase<RunConfigurationOptions> {
  @Override
  public RunProfileState getState(@NotNull Executor executor,
                                  @NotNull ExecutionEnvironment env) {
    return new MyLaunchState(env, this);
  }
}
```

### ProgramRunner

```java
public class MyRunner extends GenericProgramRunner<RunnerSettings> {
  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return DefaultRunExecutor.EXECUTOR_ID.equals(executorId)
        && profile instanceof MyRunConfig;
  }

  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state,
                                           @NotNull ExecutionEnvironment env) {
    return ((MyLaunchState) state).launch(env);
  }
}
```

### XDebugProcess (debug sessions)

```java
XDebugSession session = XDebuggerManager.getInstance(project)
    .startSession(env, new XDebugProcessStarter() {
      @Override public @NotNull XDebugProcess start(@NotNull XDebugSession session) {
        return new MyDebugProcess(session, executionResult, ...);
      }
    });
```

Key methods to override: `createConsole()`, `sessionInitialized()`, `stop()`.
`getSession().getConsoleView()` returns whatever `createConsole()` returned.

---

## Daemon protocol (flutter-specific)

Flutter runs `flutter run --machine`, communicating over stdin/stdout via JSON:

```
[{"event":"app.log","params":{"appId":"...","log":"some text","error":false}}]
```

Key daemon events: `app.start`, `app.debugPort`, `app.started`, `app.log`,
`app.progress`, `app.stop`, `daemon.log`.

**Structured errors**: when `ext.flutter.inspector.structuredErrors` is enabled,
Flutter stops sending `app.log` for errors and sends `Flutter.Error` VM extension
events instead (on the `Extension` stream). If the handler for these fails silently,
no errors appear — this is the root cause of issue #8839 (Windows desktop).

VM service streams that ARE subscribed: `Extension`, `Logging`, `Service` (in
`VMServiceManager`); `Debug`, `Isolate` (in `VmServiceWrapper`).
NOT subscribed: `Stdout`, `Stderr`.

See `docs/architecture/debug-console-pipeline.md` for the full launch sequence,
error display paths A/B/C, and the DaemonConsoleView filtering logic.

---

## Tool windows

```xml
<toolWindow id="My Tool" anchor="bottom" icon="/icons/tool.png"
            factoryClass="com.example.MyToolWindowFactory"/>
```

```java
public class MyToolWindowFactory implements ToolWindowFactory, DumbAware {
  @Override
  public void createToolWindowContent(@NotNull Project project,
                                      @NotNull ToolWindow toolWindow) {
    MyPanel panel = new MyPanel(project);
    Content content = ContentFactory.getInstance()
        .createContent(panel.getComponent(), "", false);
    toolWindow.getContentManager().addContent(content);
    Disposer.register(toolWindow.getDisposable(), panel);
  }
}
```

Implement `DumbAware` to allow the window during indexing. Use `isApplicableAsync()`
to hide it for non-Flutter projects.

---

## Gradle build

```kotlin
// build.gradle.kts
plugins {
  id("org.jetbrains.intellij.platform") version "2.x"
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.x"
}

intellijPlatform {
  androidStudio(ideaVersion)          // or intellijIdeaCommunity(version)
  testFramework(TestFrameworkType.Platform)
}

tasks.patchPluginXml {
  sinceBuild = "251"
  untilBuild = "261.*"
}
```

Key tasks: `runIde`, `test`, `buildPlugin`, `verifyPlugin`, `integration`.

`ideaVersion` comes from `gradle.properties` and must match a real released Android
Studio version. Check the AS releases list if `buildPlugin` fails at configuration time.

---

## Testing

### Choosing the right fixture

| Situation | Use |
|-----------|-----|
| Pure logic, no IDE APIs | Plain JUnit — no fixture |
| IDE services, PSI, VFS | `Testing.makeEmptyProject()` |
| Needs a Module | `Testing.makeEmptyModule()` |
| Dart/code insight | `Testing.makeCodeInsightModule()` |
| Console/process output | `NopProcessHandler` + recording subclass |
| Daemon API commands/replies | `DaemonApi(log::add)` + `api.dispatch(...)` |
| Daemon events | `DaemonEvent.dispatch(json, listener)` |
| Mock a service | `ServiceContainerUtil.registerServiceInstance()` |
| Files on disk | `TestDir` rule |
| Full IDE workflow | Integration tests (IDE Starter, JUnit 5) |

### Standard unit test structure (JUnit 4)

```java
public class MyTest {
  @Rule
  public final ProjectFixture<IdeaProjectTestFixture> fixture =
      Testing.makeEmptyModule();

  @Test
  public void testSomething() throws Exception {
    Testing.runOnDispatchThread(() -> {
      Project project = fixture.getProject();
      // ... test body using IDE APIs
    });
  }
}
```

Always wrap IDE API calls in `Testing.runOnDispatchThread()`. See
`references/flutter-intellij-testing.md` for the full testing API reference including
`DaemonApi`, `DaemonConsoleView`, service injection, `TestDir`, and integration tests.

---

## Common pitfalls

**EDT violations**: never do I/O or network calls on the EDT. Move to
`executeOnPooledThread()`, post results back with `invokeLater`.

**Missing read action**: accessing PSI/VFS content outside a read action causes
exceptions at runtime. Wrap in `runReadAction()`.

**Listener leak**: always scope MessageBus connections and listeners to a `Disposable`.
Unscoped connections outlive the project and cause memory leaks and stale callbacks.

**startNotify() ordering**: register all `ProcessListener`s *before* calling
`startNotify()` — or accept that early output may be missed.

**Deprecated API migration** (common in flutter-intellij):
- `ProcessAdapter` → use `ProcessAdapter` from `io.flutter.utils` (re-declared without deprecation)
- `URL(string)` constructor → `URI(string).toURL()`

---

## Navigating the flutter-intellij codebase

| Package | Key classes |
|---------|-------------|
| `io.flutter.run` | `LaunchState`, `FlutterRunner`, `FlutterDevice` |
| `io.flutter.run.daemon` | `FlutterApp`, `DaemonApi`, `DaemonEvent`, `DaemonConsoleView` |
| `io.flutter.vmService` | `DartVmServiceDebugProcess`, `VMServiceManager`, `VmServiceWrapper` |
| `io.flutter.logging` | `FlutterConsoleLogManager`, `DiagnosticsNode` |
| `io.flutter.utils` | `StdoutJsonParser`, `MostlySilentColoredProcessHandler`, `ProcessAdapter` |
| `io.flutter.settings` | `FlutterSettings` (`isShowStructuredErrors`, `isVerboseLogging`, etc.) |

---

## Reference files

- `references/process-and-console.md` — ProcessHandler hierarchy, lifecycle, ConsoleView API, threading, MessageBus
- `references/run-debug-pipeline.md` — RunConfiguration, ProgramRunner, CommandLineState, XDebugProcess concepts
- `references/gradle-and-testing.md` — Gradle build setup, generic IntelliJ test base classes and patterns
- `references/flutter-intellij-testing.md` — Project-specific test utilities: `Testing`, `ProjectFixture`, `TestDir`, `JsonTesting`, `DaemonApi`/`DaemonEvent`/`DaemonConsoleView` patterns, service injection, integration tests with IDE Starter
- `docs/architecture/debug-console-pipeline.md` *(in repo)* — Full launch sequence, VM connection, error display paths A/B/C, DaemonConsoleView filtering, threading hazards, FlutterApp state machine
