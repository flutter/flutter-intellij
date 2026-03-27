# Unit Test for DaemonConsoleView JSON Filtering

## The Key Constraint: `hasFlutterModule` Gate

`DaemonConsoleView.print()` (in `src/io/flutter/run/daemon/DaemonConsoleView.java`) starts with:

```java
if (!FlutterModuleUtils.hasFlutterModule(getProject())) {
  return;  // swallows EVERYTHING if no Flutter module
}
```

A plain light test project has no Flutter module, so without handling this gate the test passes vacuously — nothing is printed and assertions about filtered content trivially pass. The approach below uses a recording subclass that overrides `print()` to sidestep the guard while keeping the filtering logic (`writeAvailableLines` / `StdoutJsonParser`) intact.

---

## Full Test Class

File: `testSrc/unit/io/flutter/run/daemon/DaemonConsoleViewTest.java`

```java
package io.flutter.run.daemon;

import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DaemonConsoleViewTest {

  @Rule
  public final ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyProject();

  @Test
  public void daemonJsonEventsAreFilteredFromConsoleOutput() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final Project project = fixture.getProject();
      final List<String> printed = new ArrayList<>();

      final RecordingDaemonConsoleView console =
        new RecordingDaemonConsoleView(project, GlobalSearchScope.allScope(project), printed);

      // --- Set up the fake process handler ---
      final ProcessHandler handler = new NopProcessHandler();
      // attachToProcess before startNotify so the listener is registered in time
      console.attachToProcess(handler);
      handler.startNotify();  // idempotent; safe to call again after attachToProcess

      // --- Simulate stdout events ---

      // 1. Well-formed daemon JSON event — must be SWALLOWED
      handler.notifyTextAvailable(
        "[{\"event\":\"app.log\",\"params\":{\"appId\":\"test-app\",\"log\":\"hello\",\"error\":false}}]\n",
        ProcessOutputTypes.STDOUT
      );

      // 2. Regular text — must be DISPLAYED
      handler.notifyTextAvailable("Launching lib/main.dart on iPhone 15\n", ProcessOutputTypes.STDOUT);

      // 3. Another daemon event — must be SWALLOWED
      handler.notifyTextAvailable(
        "[{\"event\":\"app.started\",\"params\":{\"appId\":\"test-app\"}}]\n",
        ProcessOutputTypes.STDOUT
      );

      // 4. Another regular line — must be DISPLAYED
      handler.notifyTextAvailable("Flutter run key commands.\n", ProcessOutputTypes.STDOUT);

      // 5. Stderr — bypasses the JSON filter entirely, must be DISPLAYED
      handler.notifyTextAvailable("Error: file not found\n", ProcessOutputTypes.STDERR);

      // --- Assert what ended up in the console ---
      for (String line : printed) {
        assertFalse("JSON daemon event must not appear: " + line,
                    line.trim().startsWith("[{"));
      }

      assertTrue(printed.stream().anyMatch(l -> l.contains("Launching lib/main.dart")));
      assertTrue(printed.stream().anyMatch(l -> l.contains("Flutter run key commands")));
      assertTrue(printed.stream().anyMatch(l -> l.contains("Error: file not found")));

      long normalCount = printed.stream()
        .filter(l -> l.contains("Launching") || l.contains("Flutter run key"))
        .count();
      assertEquals(2, normalCount);
    });
  }

  @Test
  public void regularStdoutIsDisplayedVerbatim() throws Exception {
    Testing.runOnDispatchThread(() -> {
      final List<String> printed = new ArrayList<>();
      final RecordingDaemonConsoleView console = new RecordingDaemonConsoleView(
        fixture.getProject(), GlobalSearchScope.allScope(fixture.getProject()), printed);

      final ProcessHandler handler = new NopProcessHandler();
      console.attachToProcess(handler);
      handler.startNotify();

      handler.notifyTextAvailable("Syncing files to device...\n", ProcessOutputTypes.STDOUT);
      handler.notifyTextAvailable("Debug service on ws://127.0.0.1:54321\n", ProcessOutputTypes.STDOUT);

      assertEquals(2, printed.size());
      assertEquals("Syncing files to device...\n", printed.get(0));
      assertEquals("Debug service on ws://127.0.0.1:54321\n", printed.get(1));
    });
  }

  // ---- Recording subclass ----

  /**
   * Overrides print() to record what DaemonConsoleView's filtering decided to
   * display, without writing to a real editor widget.
   *
   * DaemonConsoleView.writeAvailableLines() calls super.print(line, NORMAL_OUTPUT).
   * Dynamic dispatch resolves that super.print() call to ConsoleViewImpl.print(),
   * which in turn calls THIS override because we are the most-derived class.
   * So this override captures exactly the lines that survived the JSON filter.
   */
  private static class RecordingDaemonConsoleView extends DaemonConsoleView {
    private final List<String> log;

    RecordingDaemonConsoleView(Project project, GlobalSearchScope scope, List<String> log) {
      super(project, scope);
      this.log = log;
    }

    @Override
    public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
      log.add(text);
      // Do NOT call super.print() — avoids writing to an uninitialized headless editor.
    }
  }
}
```

---

## How Each Piece Works

### `NopProcessHandler` — the fake process handler

`NopProcessHandler` (IntelliJ platform, `com.intellij.execution.process`) holds no OS process. It provides the full `ProcessHandler` API:

| Call | Effect |
|------|--------|
| `addProcessListener(l)` | Registers listener (called internally by `attachToProcess`) |
| `startNotify()` | Marks handler as started; idempotent |
| `notifyTextAvailable(text, key)` | Fires `onTextAvailable` **synchronously** on all listeners |
| `notifyProcessTerminated(code)` | Fires `processTerminated` on all listeners |

Because `notifyTextAvailable` is synchronous in `NopProcessHandler`, there is no background thread and no need to call `UIUtil.dispatchAllInvocationEvents()` before asserting. (You would need it for real `OSProcessHandler`/`ColoredProcessHandler` where output arrives on a background reader thread.)

### Correct ordering: attach before notify

```java
console.attachToProcess(handler);  // registers ConsoleViewImpl's ProcessListener
handler.startNotify();             // safe second call — idempotent
handler.notifyTextAvailable(...);  // fires synchronously
```

### Simulating stdout vs stderr

- `ProcessOutputTypes.STDOUT` → console receives `NORMAL_OUTPUT` → JSON filter runs via `stdoutParser` + `writeAvailableLines()`
- `ProcessOutputTypes.STDERR` → console receives `ERROR_OUTPUT` → JSON filter skipped, text goes directly to `super.print()`

### Why the recording subclass works

`DaemonConsoleView.writeAvailableLines()` calls `super.print(line, NORMAL_OUTPUT)`. In Java, `super.print()` is a virtual call that dispatches to `RecordingDaemonConsoleView.print()` at runtime. So the recording subclass intercepts exactly what the filter decided to forward. Lines identified as daemon JSON events by `DaemonApi.parseAndValidateDaemonEvent()` are never passed to `super.print()` at all.

### What `DaemonApi.parseAndValidateDaemonEvent` treats as a JSON event

The method returns non-null only when the line:
1. Starts with `[{`
2. Is valid JSON
3. Contains at least one key with a non-null value

A line like `[{"event":"app.log","params":{...}}]\n` passes all three and is swallowed. A line like `Launching lib/main.dart` fails check 1 and is displayed.

---

## Running the Test

```bash
./gradlew :flutter-idea:test --tests "io.flutter.run.daemon.DaemonConsoleViewTest"
# Results: build/reports/tests/test/index.html
```

---

## Coverage Summary

| Input | Key | Expected in console |
|-------|-----|---------------------|
| `[{"event":"app.log",...}]\n` | STDOUT | Absent (filtered) |
| `[{"event":"app.started",...}]\n` | STDOUT | Absent (filtered) |
| `Launching lib/main.dart...\n` | STDOUT | Present |
| `Flutter run key commands.\n` | STDOUT | Present |
| `Error: file not found\n` | STDERR | Present (bypasses filter) |
