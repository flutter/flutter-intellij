# Process Handling & Console Output — Platform Patterns

## ProcessHandler hierarchy

```
ProcessHandler (abstract)
  └── OSProcessHandler
        └── BaseOSProcessHandler
              └── ColoredProcessHandler        ← parses ANSI escape codes
                    └── MostlySilentColoredProcessHandler  ← used in flutter-intellij
```

`MostlySilentColoredProcessHandler` is optimized for processes that produce infrequent
output: `readerOptions()` returns `forMostlySilentProcess()` (non-blocking polling,
lower CPU). On Unix `doDestroyProcess()` sends SIGINT to the process tree; on Windows
it falls back to the default destroy.

---

## ProcessHandler lifecycle

```
new ProcessHandler(command)   ← process created but NOT yet started
        ↓
addProcessListener(...)       ← register ALL listeners BEFORE startNotify
        ↓
startNotify()                 ← process starts; events begin flowing
        ↓
onTextAvailable(...)          ← fired for each output chunk
        ↓
processWillTerminate(...)     ← process is about to end
        ↓
processTerminated(...)        ← process has ended
```

`startNotify()` is idempotent — safe to call multiple times, but only the first call
takes effect. Events are queued internally until `startNotify()` is called.

**Critical ordering rule**: Any listener added *before* `startNotify()` receives all
events from the start. Listeners added *after* `startNotify()` may miss early output.

---

## ProcessListener / ProcessAdapter

```java
public interface ProcessListener extends EventListener {
  void startNotified(@NotNull ProcessEvent event);
  void processTerminated(@NotNull ProcessEvent event);
  void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed);
  void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType);
}
// ProcessAdapter provides default no-op implementations — extend this instead
```

`ProcessOutputTypes` keys: `STDOUT`, `STDERR`, `SYSTEM`

Callbacks run on a **background thread** (one reader thread per stream). Never access
PSI, modify documents, or update UI directly from these callbacks — post to EDT instead.

---

## ConsoleView API

```java
public interface ConsoleView extends JComponent, Disposable {
  void print(@NotNull String text, @NotNull ConsoleViewContentType contentType);
  void clear();
  void scrollTo(int offset);
  void attachToProcess(@NotNull ProcessHandler processHandler);
  void addMessageFilter(@NotNull Filter filter);
}
```

`ConsoleViewImpl.print()` is **thread-safe**. Calls from any thread are queued and
flushed to the editor on the EDT.

### ConsoleViewContentType

| Type | Color | Use for |
|------|-------|---------|
| `NORMAL_OUTPUT` | default | stdout, regular log lines |
| `ERROR_OUTPUT` | red | stderr, exceptions, errors |
| `SYSTEM_OUTPUT` | grey | IDE-generated messages |
| `LOG_INFO_OUTPUT` | default | log4j INFO |
| `LOG_WARNING_OUTPUT` | orange | log4j WARN |
| `LOG_ERROR_OUTPUT` | red | log4j ERROR |

---

## Attaching a console to a process

```java
ConsoleView console = createConsole(executor);   // via CommandLineState
console.attachToProcess(processHandler);          // registers listener + startNotify
```

`attachToProcess()` registers an internal `ProcessListener` that feeds output to the
console and also calls `startNotify()`. This means the process starts delivering events
at the point of attachment if it hasn't already.

For `DaemonConsoleView` specifically, `attachToProcess()` must be called after
`DaemonApi.listen()` (which also calls `startNotify()`). Since `startNotify()` is
idempotent this is safe, but the ordering must be correct so the console listener is
registered before meaningful output arrives.

---

## MessageBus

For plugin-internal pub/sub between components:

```java
// Define a topic + listener interface
Topic<MyListener> TOPIC = Topic.create("my.topic", MyListener.class);

// Subscribe — scoped to project lifetime (auto-unsubscribes on project disposal)
project.getMessageBus().connect(project).subscribe(TOPIC, myListenerImpl);

// Publish
project.getMessageBus().syncPublisher(TOPIC).onSomethingHappened(data);
```

Always scope connections to a `Disposable` (project, tool window, service) to prevent
memory leaks. Never use `messageBus.connect()` without a disposable parent.

In flutter-intellij, `FlutterViewMessages.FLUTTER_DEBUG_TOPIC` is used to notify the
Flutter tool window when a debug session becomes active.

---

## For flutter-intellij-specific detail

See `docs/architecture/debug-console-pipeline.md` in the repo for:
- The exact `DaemonConsoleView.print()` routing logic
- `writeAvailableLines()` and `StdoutJsonParser` implementation details
- The two-source threading hazard (process stdout vs. VM service WebSocket)
- Full launch sequence with class-level sequence diagrams
- Error display paths A (app.log), B (Flutter.Error), C (WriteEvent)
