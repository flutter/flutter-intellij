# Unit Test for DaemonConsoleView: Filtering Daemon JSON Events

## Test Class

```java
package io.flutter.run.daemon;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.mockito.Mockito.*;

public class DaemonConsoleViewTest extends BasePlatformTestCase {

    private DaemonConsoleView daemonConsoleView;
    private ConsoleView mockConsoleView;
    private FakeProcessHandler fakeProcessHandler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockConsoleView = mock(ConsoleView.class);
        fakeProcessHandler = new FakeProcessHandler();
        daemonConsoleView = new DaemonConsoleView(mockConsoleView);
    }

    @Test
    public void testDaemonJsonEventIsFilteredFromOutput() {
        daemonConsoleView.attachToProcess(fakeProcessHandler);
        String daemonEvent = "[{\"event\":\"app.log\",\"params\":{\"log\":\"Observatory listening\"}}]\n";
        fakeProcessHandler.simulateStdout(daemonEvent);
        verify(mockConsoleView, never()).print(contains("app.log"), any(ConsoleViewContentType.class));
    }

    @Test
    public void testRegularTextLineIsDisplayedInConsole() {
        daemonConsoleView.attachToProcess(fakeProcessHandler);
        String regularText = "Launching lib/main.dart on Chrome in debug mode...\n";
        fakeProcessHandler.simulateStdout(regularText);
        verify(mockConsoleView, times(1)).print(eq(regularText), eq(ConsoleViewContentType.NORMAL_OUTPUT));
    }

    @Test
    public void testMixedOutputFiltersDaemonKeepsRegular() {
        daemonConsoleView.attachToProcess(fakeProcessHandler);
        String regular1 = "Flutter run key commands.\n";
        String daemon   = "[{\"event\":\"app.debugPort\",\"params\":{\"port\":9100}}]\n";
        String regular2 = "r Hot reload.\n";

        fakeProcessHandler.simulateStdout(regular1);
        fakeProcessHandler.simulateStdout(daemon);
        fakeProcessHandler.simulateStdout(regular2);

        verify(mockConsoleView).print(eq(regular1), eq(ConsoleViewContentType.NORMAL_OUTPUT));
        verify(mockConsoleView).print(eq(regular2), eq(ConsoleViewContentType.NORMAL_OUTPUT));
        verify(mockConsoleView, never()).print(eq(daemon), any(ConsoleViewContentType.class));
    }

    @Test
    public void testStderrLinesAreDisplayed() {
        daemonConsoleView.attachToProcess(fakeProcessHandler);
        String errorLine = "Error: Could not find pubspec.yaml\n";
        fakeProcessHandler.simulateStderr(errorLine);
        verify(mockConsoleView, times(1)).print(eq(errorLine), eq(ConsoleViewContentType.ERROR_OUTPUT));
    }
}
```

## FakeProcessHandler

```java
package io.flutter.run.daemon;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.OutputStream;

public class FakeProcessHandler extends ProcessHandler {

    public void simulateStdout(@NotNull String text) {
        notifyTextAvailable(text, ProcessOutputType.STDOUT);
    }

    public void simulateStderr(@NotNull String text) {
        notifyTextAvailable(text, ProcessOutputType.STDERR);
    }

    @Override protected void destroyProcessImpl() { notifyProcessTerminated(0); }
    @Override protected void detachProcessImpl()  { notifyProcessDetached(); }
    @Override public boolean detachIsDefault()     { return false; }
    @Override @Nullable public OutputStream getProcessInput() { return null; }
}
```

The key is `notifyTextAvailable(text, outputType)` — a protected method on `ProcessHandler` that fires `ProcessListener.onTextAvailable()` on all registered listeners synchronously. Since `DaemonConsoleView.attachToProcess()` registers a `ProcessListener`, calling `simulateStdout()` triggers the filter logic immediately without any threading.

## How to Simulate stdout Events

```java
// 1. Attach — this registers the filtering listener
daemonConsoleView.attachToProcess(fakeProcessHandler);

// 2. Push a daemon JSON event — should be swallowed
fakeProcessHandler.simulateStdout("[{\"event\":\"app.log\",\"params\":{}}]\n");

// 3. Push a regular line — should pass through
fakeProcessHandler.simulateStdout("Hot reload performed in 312ms.\n");
```

## Asserting Console Contents

**Mockito verify (simple):**
```java
verify(mockConsoleView, never()).print(contains("app.log"), any());
verify(mockConsoleView, times(1)).print(eq("Hot reload performed in 312ms.\n"), eq(ConsoleViewContentType.NORMAL_OUTPUT));
```

**ArgumentCaptor (richer):**
```java
ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
verify(mockConsoleView, atLeastOnce()).print(captor.capture(), any());

for (String line : captor.getAllValues()) {
    assertFalse("Daemon JSON leaked into console: " + line, line.startsWith("[{\"event\":"));
}
```

## Summary Table

| Component | Role |
|---|---|
| `FakeProcessHandler` | Extends `ProcessHandler`; no real process; injects text via `notifyTextAvailable()` |
| `mockConsoleView` | Mockito mock; captures all `print()` calls |
| `attachToProcess(fakeHandler)` | Registers the filtering `ProcessListener` |
| `simulateStdout(line)` | Triggers `onTextAvailable` synchronously |
| `verify(..., never())` | Asserts filtered lines never reached the console |
| `verify(..., times(1))` | Asserts regular lines did reach the console |
