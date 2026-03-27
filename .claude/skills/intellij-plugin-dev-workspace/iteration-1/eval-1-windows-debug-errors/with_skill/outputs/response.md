# Flutter Windows Desktop: Missing Debug Console Errors

## The error display code path (structured errors enabled)

When `isShowStructuredErrors()` is true, the plugin enables `ext.flutter.inspector.structuredErrors`
via `FlutterApp.getFlutterConsoleLogManager()`. Once enabled, Flutter **suppresses** `app.log`
error events and instead sends `Flutter.Error` VM service extension events on the `Extension` stream.

The full chain:

```
Flutter framework error → FlutterError.presentError()
      ↓
Flutter.Error VM service extension event (Extension stream)
      ↓
VMServiceManager.onVmServiceReceived("Flutter.Error")   [VMServiceManager.java:207]
      ↓
app.getFlutterConsoleLogManager().handleFlutterErrorEvent(event)
      ↓
queue.add(() -> processFlutterErrorEvent(diagnosticsNode))  [FlutterConsoleLogManager.java:133]
      ↓
processFlutterErrorEvent() → console.print(header/properties/footer)
```

## Why Chrome works but Windows does not

On Chrome/web, `ext.flutter.inspector.structuredErrors` is **not available**, so the callback
in `hasServiceExtension(...)` never fires, structured errors stay disabled, and Flutter errors
come through the normal `app.log` daemon event path which works reliably.

On Windows desktop, the extension IS available, so:
1. Structured errors get enabled
2. `app.log` error events are suppressed by Flutter
3. Errors must flow through `processFlutterErrorEvent()` — and that's where they die silently

## The specific failure point

**`DiagnosticsNode.getStyleMember()` at line 504 of `DiagnosticsNode.java`:**

```java
private DiagnosticsTreeStyle getStyleMember(String memberName, DiagnosticsTreeStyle defaultValue) {
    if (!json.has(memberName)) {
        return defaultValue;
    }
    final JsonElement value = json.get(memberName);
    if (value instanceof JsonNull) {
        return defaultValue;
    }
    return DiagnosticsTreeStyle.valueOf(value.getAsString());  // ← NO try/catch
}
```

Compare with the safe `getLevelMember()` just above it, which has:

```java
catch (IllegalArgumentException ignore) {
    return defaultValue;
}
```

If the Windows Flutter SDK emits a `style` value not present in the plugin's `DiagnosticsTreeStyle`
enum, `valueOf()` throws `IllegalArgumentException`. This propagates out of `processFlutterErrorEvent()`
and is caught by the queue lambda's `catch (Throwable t)` at line 137:

```java
catch (Throwable t) {
    if (FlutterSettings.getInstance().isVerboseLogging()) {
        LOG.warn(t);
    } else {
        LOG.warn("Error processing FlutterErrorEvent: " + t.getMessage());
    }
}
```

The error goes to `idea.log` only. Nothing appears in the console.

## The fix — two parts

### Part 1: Fix `getStyleMember()` to match `getLevelMember()`

In `src/io/flutter/logging/DiagnosticsNode.java`, line 496–505:

```java
private DiagnosticsTreeStyle getStyleMember(String memberName, DiagnosticsTreeStyle defaultValue) {
    if (!json.has(memberName)) {
        return defaultValue;
    }
    final JsonElement value = json.get(memberName);
    if (value instanceof JsonNull) {
        return defaultValue;
    }
    try {
        return DiagnosticsTreeStyle.valueOf(value.getAsString());
    }
    catch (IllegalArgumentException ignore) {
        return defaultValue;
    }
}
```

### Part 2: Add fallback console output in the queue catch block

In `FlutterConsoleLogManager.java`, line 133–151, change the catch to also emit a raw error:

```java
queue.add(() -> {
    try {
        processFlutterErrorEvent(diagnosticsNode);
    }
    catch (Throwable t) {
        // Fallback: show the raw error text so it's never silently swallowed
        final String rawError = diagnosticsNode.getDescription();
        if (rawError != null && !rawError.isEmpty()) {
            console.print(rawError + "\n", ConsoleViewContentType.ERROR_OUTPUT);
        }
        LOG.warn("Error processing FlutterErrorEvent: " + t.getMessage());
    }
    finally { ... }
});
```

## Key files

| File | Location | Role |
|------|----------|------|
| `DiagnosticsNode.java` | `src/io/flutter/logging/` | **Fix site** — `getStyleMember()` line 504 |
| `FlutterConsoleLogManager.java` | `src/io/flutter/logging/` | Silent swallow site — line 137 |
| `VMServiceManager.java` | `src/io/flutter/vmService/` | Routes `Flutter.Error` events |
| `FlutterApp.java` | `src/io/flutter/run/daemon/` | Enables `structuredErrors` — lines 633–649 |
