# Gradle Build & Testing — Deep Reference

## Gradle IntelliJ Plugin — build.gradle.kts

The `org.jetbrains.intellij` Gradle plugin handles:
- Downloading the target IDE and its dependencies
- Compiling plugin code against the IDE's classes
- Packaging into a distributable zip
- Launching a sandboxed IDE for manual testing
- Running tests in a headless IDE environment

### Minimal working build file

```kotlin
// build.gradle.kts
plugins {
  id("org.jetbrains.intellij") version "1.17.4"
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.9.23"   // if using Kotlin
}

group = "com.example"
version = "1.0.0"

repositories {
  mavenCentral()
}

intellij {
  version.set("2024.1")           // Target IDE version (IJ build number or release)
  type.set("IC")                  // IC = Community, IU = Ultimate, AI = Android Studio
  plugins.set(listOf(
    "Dart:241.18034.62",          // <plugin-id>:<compatible-version>
    "java",                       // bundled plugin — no version needed
  ))
  updateSinceUntilBuild.set(true) // auto-set sinceBuild/untilBuild from intellij.version
}

tasks {
  patchPluginXml {
    sinceBuild.set("241")         // 241 = 2024.1
    untilBuild.set("243.*")       // 243.* = any 2024.3.x
    changeNotes.set("""...""")
  }

  compileJava {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }

  test {
    // Headless test environment — no display needed
  }

  runIde {
    // Optional: set the IDE to launch (default = downloaded IDE)
    // ideDir.set(file("/path/to/installed/IDE"))
  }
}
```

### Key Gradle tasks

| Task | Effect |
|------|--------|
| `./gradlew runIde` | Launches sandboxed IDE with plugin loaded (for manual testing) |
| `./gradlew test` | Runs all tests in headless IDE environment |
| `./gradlew buildPlugin` | Produces `build/distributions/<name>-<version>.zip` |
| `./gradlew verifyPlugin` | Checks plugin.xml, deprecated APIs, binary compatibility |
| `./gradlew publishPlugin` | Publishes to JetBrains Marketplace (needs token) |
| `./gradlew dependencies` | Shows full dependency tree |
| `./gradlew listProductsReleases` | Shows available IDE versions for testing |

### Sandbox directory

`runIde` creates a sandbox at `build/idea-sandbox/` containing:
- `config/` — IDE configuration (persists between runs, useful for reproducible state)
- `system/` — caches, indices
- `plugins/` — your plugin's output + its dependencies

Delete `build/idea-sandbox/` to reset to a clean state.

### Plugin dependencies

```kotlin
intellij {
  plugins.set(listOf(
    "com.intellij.java",          // Java plugin (bundled in IJ, separate in other IDEs)
    "Dart:241.18034.62",          // Third-party plugin: id:version
    "android",                   // Android plugin
  ))
}
```

For optional plugin dependencies in `plugin.xml`:

```xml
<depends optional="true" config-file="my-plugin-dart-support.xml">Dart</depends>
```

The optional config file is loaded only when the Dart plugin is present.

---

## Testing infrastructure

### Test base classes (choose the right one)

| Class | When to use |
|-------|-------------|
| `BasePlatformTestCase` | Modern default for most unit/integration tests |
| `LightPlatformTestCase` | Older equivalent of BasePlatformTestCase |
| `HeavyPlatformTestCase` | Full project lifecycle — slow, use sparingly |
| `LightJavaCodeInsightFixtureTestCase` | Tests that need Java PSI support |
| `CodeInsightTestFixture` | Tests for code completion, inspections, intentions |

All test classes run in a headless IntelliJ environment with a real (light) project and module. The IDE services, extension points, and MessageBus are all available.

### BasePlatformTestCase usage

```java
public class MyFeatureTest extends BasePlatformTestCase {

  // Optional: specify a test data path
  @Override
  protected String getTestDataPath() {
    return "src/test/testData/myFeature";
  }

  public void testSomethingSimple() {
    // getProject()   — the light test project
    // getModule()    — the test module
    // myFixture      — CodeInsightTestFixture for file operations
    assertNotNull(getProject().getService(MyProjectService.class));
  }

  public void testWithFile() {
    // Load a .dart file from testData and open it in a virtual editor
    myFixture.configureByFile("sample.dart");
    // Access the PSI tree
    PsiFile file = myFixture.getFile();
    assertNotNull(file);
  }
}
```

### Testing with in-memory files

```java
public void testInMemoryFile() {
  // Create file content in memory — no file on disk needed
  PsiFile file = myFixture.configureByText("Test.java",
    "public class Test { void foo() { <caret> } }");

  // Run an inspection, completion, etc.
  List<IntentionAction> actions = myFixture.getAvailableIntentions();
  // ...
}
```

### Testing ProcessHandler / console output

Use `NopProcessHandler` (no real process) to simulate process events:

```java
public void testDaemonConsoleFiltersJson() {
  ProcessHandler handler = new NopProcessHandler();
  // Create and attach the console
  DaemonConsoleView console = new DaemonConsoleView(getProject(), searchScope);
  console.attachToProcess(handler);
  handler.startNotify();

  // Simulate stdout output arriving
  handler.notifyTextAvailable(
    "[{\"event\":\"app.log\",\"params\":{\"log\":\"hello\"}}]\n",
    ProcessOutputTypes.STDOUT
  );
  handler.notifyTextAvailable(
    "regular text\n",
    ProcessOutputTypes.STDOUT
  );

  // Give EDT a chance to flush
  UIUtil.dispatchAllInvocationEvents();

  // Now assert what appeared (or didn't) in the console
  // Use console.getText() or a ConsoleViewImpl helper
}
```

### Testing services and extension points

```java
// Register a mock service for the test
public void testWithMockService() {
  MyService mockService = mock(MyService.class);
  ServiceContainerUtil.replaceService(getProject(), MyService.class, mockService, getTestRootDisposable());

  // The mock is active only for this test
  MyService svc = getProject().getService(MyService.class);
  assertSame(mockService, svc);
}

// Register an extension point implementation for the test
public void testWithExtension() {
  ExtensionTestUtil.maskExtensions(
    MyExtensionPoint.EP_NAME,
    List.of(new MyTestExtension()),
    getTestRootDisposable()
  );
}
```

### Testing with real files on disk

Put test data in `src/test/testData/` (or wherever `getTestDataPath()` points):

```
src/test/
├── java/
│   └── com/example/MyTest.java
└── testData/
    └── myFeature/
        ├── input.dart
        └── expected.dart
```

```java
@Override
protected String getTestDataPath() {
  return "src/test/testData";
}

public void testTransformFile() {
  myFixture.configureByFile("myFeature/input.dart");
  // Run the action/intention/inspection
  myFixture.checkResultByFile("myFeature/expected.dart");
}
```

---

## flutter-intellij specific: running the test suite

The flutter-intellij codebase has tests in multiple modules. From the repo root:

```bash
# Run all tests (slow — launches headless IDE)
./gradlew test

# Run tests in a specific module
./gradlew :flutter-idea:test

# Run a specific test class
./gradlew :flutter-idea:test --tests "io.flutter.run.daemon.DaemonApiTest"

# Run with more output
./gradlew test --info
```

Test output lands in `build/reports/tests/test/index.html`.

### Gradle property: skipping slow tests

Some integration tests are marked with `@Ignore` or gated behind a system property. Check
`FlutterTestUtils` or the individual test classes for skip conditions.

### Build variants

flutter-intellij uses a `local.properties` file (or environment variables) to set the
target IDE version:

```properties
# local.properties
flutter.ideVersion=2024.1
flutter.androidPluginVersion=241.18034.62
```

The build script reads these to configure `intellij { version.set(...) }`.

---

## Common build and test issues

### "Cannot resolve symbol X" in tests

The target IDE version in `intellij { version.set(...) }` must include the plugin that
defines X. Check `intellij { plugins.set(...) }` and add the required plugin.

### Test hangs on first run

The Gradle IntelliJ plugin downloads the target IDE the first time. This can take
several minutes on a slow connection. Subsequent runs use the cached download in
`~/.gradle/caches/modules-*/files-*/com.jetbrains.intellij.idea/`.

### "Plugin 'X' not found" at test startup

The plugin ID in `intellij { plugins.set(...) }` is wrong or the version is
incompatible with the target IDE. Use `./gradlew listProductsReleases` to check
available IDE versions, and the JetBrains Marketplace to check plugin versions.

### EDT violations in tests

Tests run on the EDT by default. Code that must NOT run on the EDT (like spawning
processes) will trigger `com.intellij.testFramework.EdtTestUtil` assertions. Use
`ApplicationManager.getApplication().executeOnPooledThread()` and join back in the test.

### Flaky tests due to timing

Process output arrives asynchronously. After calling `handler.notifyTextAvailable(...)`,
call `UIUtil.dispatchAllInvocationEvents()` (or `PlatformTestUtil.dispatchAllEventsInIdeEventQueue()`)
to flush the EDT queue before asserting.

---

## Useful test utilities in flutter-intellij

| Utility | Location | Purpose |
|---------|----------|---------|
| `FlutterTestUtils` | `src/test/...` | Helper for creating Flutter modules in tests |
| `MockVmService` | `src/test/...` | Fake VM service for testing debug protocol |
| `NopProcessHandler` | IntelliJ platform | Process handler that does nothing (no real process) |
| `TestConsoleProperties` | IntelliJ platform | Utility for test consoles |
| `EdtTestUtil` | IntelliJ platform | Helpers for running code on/off EDT in tests |
