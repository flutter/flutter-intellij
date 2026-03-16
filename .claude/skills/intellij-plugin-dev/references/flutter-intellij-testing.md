# flutter-intellij Testing Patterns — Deep Reference

## Test directory layout

```
flutter-intellij/
├── testSrc/
│   ├── unit/io/flutter/          ← JUnit 4 unit tests (~33 files)
│   │   └── testing/              ← project-specific test utilities (see below)
│   └── integration/io/flutter/integrationTest/  ← IDE Starter UI tests (JUnit 5)
└── testData/                     ← static test data, sample projects, SDK artifacts
```

Unit and integration tests use **different frameworks**: unit tests use JUnit 4 + IntelliJ
platform test framework; integration tests use JUnit 5 + IntelliJ IDE Starter.

---

## Unit test utilities — the `testing` package

These live in `testSrc/unit/io/flutter/testing/` and are the idiomatic way to write tests in
this codebase. Do not use bare `LightPlatformTestCase` / `BasePlatformTestCase` — use these
instead.

### `Testing` — the primary entry point

```java
import io.flutter.testing.Testing;

// Create a light project fixture (no module) — use as @Rule
@Rule public ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyProject();

// Create a heavy fixture with project + module — use as @Rule
@Rule public ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyModule();

// Create a fixture with CodeInsight/Dart language support — use as @Rule
@Rule public ProjectFixture<CodeInsightTestFixture> fixture = Testing.makeCodeInsightModule();
```

**Thread helpers:**

```java
// Run code on the EDT and wait for it to complete
Testing.runOnDispatchThread(() -> {
    // IDE code here — safe to call getProject(), create PsiFiles, etc.
});

// Compute a value on the EDT
String result = Testing.computeOnDispatchThread(() -> someIdeThing());

// Run in a write action (for VirtualFile / document modifications)
Testing.runInWriteAction(() -> { file.delete(this); });
Testing.computeInWriteAction(() -> file.contentsToByteArray());
```

Most IntelliJ APIs — including `ConsoleViewImpl`, `DaemonConsoleView`, fixture setup/teardown,
and PSI operations — **must** run on the EDT. Wrap everything that touches the IDE in
`Testing.runOnDispatchThread()`.

### `ProjectFixture<T>` — the base fixture rule

```java
// Implements JUnit 4 @Rule — handles setUp/tearDown automatically
@Rule
public final ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyModule();

public void test() throws Exception {
    Project project = fixture.getProject();
    Module  module  = fixture.getModule();  // only available from makeEmptyModule()
}
```

### `TestDir` — temporary virtual filesystem

```java
@Rule public TestDir tmp = new TestDir();

VirtualFile dir  = tmp.ensureDir("src/lib");
VirtualFile file = tmp.writeFile("src/lib/main.dart", "void main() {}");
tmp.deleteFile("src/lib/main.dart");
String path = tmp.pathAt("src/lib/main.dart");  // absolute path
```

Files are created inside IntelliJ's in-memory VFS. `writeFile` and `deleteFile` run in
write actions automatically.

### `JsonTesting` — readable JSON construction

```java
import static io.flutter.testing.JsonTesting.curly;

// curly(key:value pairs) → {"key": value} JSON
String json = curly("appId:\"foo\"", "fullRestart:true", "code:42");
// → {"appId":"foo","fullRestart":true,"code":42}
// Keys are always quoted; values are used as-is (include quotes yourself for strings)
```

---

## JUnit 4 test structure (the standard pattern)

```java
package io.flutter.run.daemon;

import io.flutter.testing.Testing;
import io.flutter.testing.ProjectFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class MyFeatureTest {

  @Rule
  public final ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyModule();

  @Before
  public void setUp() throws Exception {
    // Initialization that doesn't need the EDT
  }

  @Test
  public void testSomething() throws Exception {
    Testing.runOnDispatchThread(() -> {
      Project project = fixture.getProject();
      // ... test body
    });
  }
}
```

Key rules:
- Tests are JUnit 4 (`org.junit.Test`, not `org.junit.jupiter.api.Test`)
- Fixtures are `@Rule` fields, not extended base classes
- Code touching the IDE must be wrapped in `Testing.runOnDispatchThread()`
- Prefer `assertEquals` / `assertNotNull` from `org.junit.Assert` (statically imported)

---

## Testing DaemonApi — the checkSent / replyWithResult pattern

`DaemonApi` is tested by capturing outgoing JSON commands and manually dispatching replies.

```java
import static io.flutter.testing.JsonTesting.curly;

public class DaemonApiTest {
  private List<String> log;   // captures commands sent to the daemon
  private DaemonApi api;

  @Before
  public void setUp() {
    log = new ArrayList<>();
    api = new DaemonApi(log::add);   // the callback receives each outgoing JSON line
  }

  @Test
  public void canRestartApp() throws Exception {
    // Trigger the call — it's async so result is a Future
    Future<DaemonApi.RestartResult> result = api.restartApp("foo", true, false, "manual");

    // Assert what was sent to the daemon
    assertEquals(
        "{\"method\":\"app.restart\",\"params\":" +
        curly("appId:\"foo\"", "fullRestart:true", "pause:false", "reason:\"manual\"") +
        ",\"id\":0}",
        log.get(0)
    );
    assertFalse("Future should not be done yet", result.isDone());

    // Simulate the daemon replying
    api.dispatch(
        JsonUtils.parseString("{\"id\":\"0\",\"result\":" +
            curly("code:42", "message:\"sorry\"") + "}").getAsJsonObject(),
        null   // no listener needed for reply dispatch
    );

    assertTrue(result.isDone());
    assertFalse(result.get().ok());
    assertEquals(42, result.get().getCode());
  }

  @Test
  public void canStopApp() throws Exception {
    Future<Boolean> result = api.stopApp("foo");
    assertEquals("{\"method\":\"app.stop\",\"params\":{\"appId\":\"foo\"},\"id\":0}",
                 log.get(0));
    api.dispatch(
        JsonUtils.parseString("{\"id\":\"0\",\"result\":true}").getAsJsonObject(), null);
    assertTrue(result.get());
  }
}
```

Note: `DaemonApi` can be constructed and tested without any `ProcessHandler` or real process.

---

## Testing DaemonEvent — dispatching events to a listener

```java
public class DaemonEventTest {
  private final Gson GSON = new Gson();
  private List<String> log;
  private DaemonEvent.Listener listener;

  @Before
  public void setUp() {
    log = new ArrayList<>();
    listener = new DaemonEvent.Listener() {
      @Override
      public void onAppStarting(DaemonEvent.AppStarting event) {
        log.add("AppStarting: " + event.appId + ", " + event.deviceId);
      }
      @Override
      public void onAppLog(DaemonEvent.AppLog event) {
        log.add("AppLog: " + event.log + " error=" + event.error);
      }
      // override other handlers as needed
    };
  }

  @Test
  public void canReceiveAppStarting() {
    send("app.start", curly("appId:42", "deviceId:456", "directory:somedir", "launchMode:run"));
    assertEquals("AppStarting: 42, 456", log.get(0));
  }

  private void send(String eventName, String params) {
    DaemonEvent.dispatch(
        GSON.fromJson(curly("event:\"" + eventName + "\"", "params:" + params),
                      JsonObject.class),
        listener
    );
  }
}
```

---

## Testing DaemonConsoleView — the recording subclass pattern

`DaemonConsoleView.print()` has a `hasFlutterModule` guard that swallows all output if the
project has no Flutter module. To test the JSON-filtering logic without that guard, override
`print()` in a recording subclass:

```java
public class DaemonConsoleViewTest {

  @Rule
  public final ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyProject();

  @Test
  public void daemonJsonEventsAreFilteredFromOutput() throws Exception {
    Testing.runOnDispatchThread(() -> {
      Project project = fixture.getProject();
      List<String> printed = new ArrayList<>();

      // Recording subclass intercepts what the JSON filter decided to display
      DaemonConsoleView console = new DaemonConsoleView(project,
                                        GlobalSearchScope.allScope(project)) {
        @Override
        public void print(@NotNull String text, @NotNull ConsoleViewContentType ct) {
          printed.add(text);
          // Do NOT call super — avoids writing to an uninitialized headless editor
        }
      };

      ProcessHandler handler = new NopProcessHandler();
      console.attachToProcess(handler);
      handler.startNotify();

      // Daemon JSON — must be SWALLOWED
      handler.notifyTextAvailable(
          "[{\"event\":\"app.log\",\"params\":{\"appId\":\"x\",\"log\":\"hi\",\"error\":false}}]\n",
          ProcessOutputTypes.STDOUT
      );
      // Regular text — must be DISPLAYED
      handler.notifyTextAvailable("Launching lib/main.dart\n", ProcessOutputTypes.STDOUT);

      // NopProcessHandler fires notifyTextAvailable synchronously — no EDT flush needed
      for (String line : printed) {
        assertFalse("Daemon JSON must not appear: " + line, line.trim().startsWith("[{"));
      }
      assertTrue(printed.stream().anyMatch(l -> l.contains("Launching lib/main.dart")));
    });
  }
}
```

**Why NopProcessHandler is synchronous**: `NopProcessHandler.notifyTextAvailable()` fires
`ProcessListener.onTextAvailable()` on the calling thread immediately. There is no background
reader thread, so no `UIUtil.dispatchAllInvocationEvents()` is needed before asserting.
This contrasts with real `OSProcessHandler` / `ColoredProcessHandler` where output arrives
on a background reader thread and you would need to flush the EDT.

---

## Service injection in tests

Replace a project or application service with a mock for the duration of a test:

```java
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import static org.mockito.Mockito.*;

@Before
public void setUp() throws Exception {
    Project project = fixture.getProject();

    MyService mockService = mock(MyService.class);
    ServiceContainerUtil.registerServiceInstance(project, MyService.class, mockService);
    // Mock is active for this test only; original is restored automatically at project disposal
}
```

For Mockito annotations:

```java
@Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

@Mock private FlutterSdk mockSdk;
@Spy  private RealService spiedService;
```

---

## Testing with files on disk (TestDir + VFS)

```java
@Rule public TestDir tmp = new TestDir();
@Rule public ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyModule();

@Before
public void setUp() throws Exception {
    // Add the temp dir as a content root so PSI can resolve files
    VirtualFile root = tmp.ensureDir("root");
    Testing.runInWriteAction(() ->
        ModuleRootModificationUtil.addContentRoot(fixture.getModule(), root.getPath())
    );
}

@Test
public void testFileResolution() throws Exception {
    tmp.writeFile("root/pubspec.yaml", "name: myapp\n");
    VirtualFile main = tmp.writeFile("root/lib/main.dart", "void main() {}");

    Testing.runOnDispatchThread(() -> {
        PsiFile psi = PsiManager.getInstance(fixture.getProject())
                                .findFile(main);
        assertNotNull(psi);
    });
}
```

---

## Integration tests (IDE Starter)

Integration tests live in `testSrc/integration/io/flutter/integrationTest/` and use
the IntelliJ IDE Starter framework (JUnit 5). They launch a full sandboxed IDE with
the built plugin installed and drive the UI via the Driver SDK.

### Prerequisites

- **`FLUTTER_SDK` env var** must be set to your Flutter SDK root (e.g. `C:\Users\you\flutter`).
  Tests that create Flutter projects check this at runtime and fail with a clear message if absent.
- **`buildPlugin` must succeed first** — the integration Gradle task reads
  `System.getProperty("path.to.build.plugin")` (set automatically by the `integration` task
  in `build.gradle.kts`) to install the freshly-built plugin into the test IDE.

### The Setup class

All integration tests use `Setup.setupTestContextIC/UE/WS()` to create the IDE context.
The context pins a specific IC/IU/WS build number and installs the plugin + Dart plugin.

```kotlin
// Setup.kt companion object
fun setupTestContextIC(hyphenateWithClass: String,
                       projectInfoSpec: ProjectInfoSpec = NoProject): IDETestContext {
  return setupTestContext(
    "", IdeProductProvider.IC.copy(
      buildNumber = "252.23892.409",   // IC build — update when targeting a new IDE version
      buildType   = BuildType.RELEASE.type
    ), projectInfoSpec
  )
}

private fun setupTestContext(...): IDETestContext {
  val testCase = TestCase(ideInfo, projectInfoSpec)
  return Starter.newContext(testName = hyphenateWithClass, testCase = testCase).apply {
    val pluginPath = System.getProperty("path.to.build.plugin")  // set by Gradle
    PluginConfigurator(this)
      .installPluginFromPath(Paths.get(pluginPath))
      .installPluginFromPluginManager("Dart", "252.24322.5")     // Dart plugin version
  }.applyVMOptionsPatch {
    addSystemProperty("idea.trust.all.projects", true)
    addSystemProperty("jb.consents.confirmation.enabled", false)
    // ... other IDE config flags
    when {
      SystemInfo.isMac     -> { /* mac-specific flags */ }
      SystemInfo.isWindows -> { /* windows-specific flags */ }
      SystemInfo.isLinux   -> { /* linux-specific flags */ }
    }
  }.addProjectToTrustedLocations()
}
```

### Standard test class structure

```kotlin
@Tag("ui")                                    // required — Gradle task filters by this tag
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class MyFeatureUITest {

  companion object {
    @JvmStatic
    @AfterAll
    fun cleanup() {
      // delete any test projects created on disk
    }
  }

  private lateinit var run: BackgroundRun     // holds the running IDE instance

  @BeforeEach
  fun initContext() {
    // per-test setup (e.g. unique project name)
  }

  @AfterEach
  fun closeIde() {
    if (::run.isInitialized) run.closeIdeAndWait()
  }

  @Test
  fun myTest() {
    run = Setup.setupTestContextIC("MyFeatureUITest").runIdeWithDriver()

    // All UI interactions go through run.driver.withContext { }
    run.driver.withContext {
      welcomeScreen {
        assert(isVisible())
        createNewProjectButton.click()
      }
      ideFrame {
        waitFound()                           // wait for main window
        driver.waitForIndicators(5.minutes)   // wait for indexing to finish
        projectView {
          projectViewTree.pathExists("myproject", "lib", "main.dart")
        }
      }
    }
  }
}
```

### Key Driver SDK APIs

```kotlin
// Top-level entry points inside run.driver.withContext { }
welcomeScreen { ... }        // com.intellij.driver.sdk.ui.components.common.welcomeScreen
ideFrame { ... }             // com.intellij.driver.sdk.ui.components.common.ideFrame
projectView { ... }          // com.intellij.driver.sdk.ui.components.common.toolwindows.projectView

// Inside ideFrame { }
waitFound()                  // UiComponent.waitFound() — asserts element is visible
driver.waitForIndicators(5.minutes)  // waits for all progress indicators to finish
projectViewTree.pathExists("proj", "lib", "main.dart")  // asserts tree path exists
projectViewTree.doubleClickPath("proj", "lib", "main.dart", fullMatch = false)

// XPath-style component lookup (used in NewProject.kt utils)
x("//div[@title='New Project']", NewProjectDialogUI::class.java).action()
x("//div[@class='JBList']")       // find by class name
x("//div[@text='Next']")          // find by visible text
```

### Custom UI component helpers

Reusable dialog/screen helpers live in `testSrc/integration/.../utils/`. Follow the
`NewProject.kt` pattern:

```kotlin
// Extension function on Finder — adds a named entry point
fun Finder.newProjectDialog(action: NewProjectDialogUI.() -> Unit) {
  x("//div[@title='New Project']", NewProjectDialogUI::class.java).action()
}

// Typed component with named element accessors
open class NewProjectDialogUI(data: ComponentData) : UiComponent(data) {
  val nextButton   = x("//div[@text='Next']")
  val createButton = x("//div[@text='Create']")
  fun chooseProjectType(type: String) = projectTypeList.waitOneText(type).click()
  private val projectTypeList = x("//div[@class='JBList']")
}
```

### Running integration tests

```bash
# Requires FLUTTER_SDK env var and a successful buildPlugin

# Run all integration (UI-tagged) tests
FLUTTER_SDK=C:\Users\you\flutter ./gradlew buildPlugin integration

# Run a specific test method
FLUTTER_SDK=... ./gradlew buildPlugin integration \
  --tests "io.flutter.integrationTest.MyProjectUITest.newProjectIC"
```

**Important**: `gradle.properties` contains `ideaVersion` which must match a real
released Android Studio version (e.g. `2025.2.3.9`). The `buildPlugin` task fails at
configuration time if the version is not found in the JetBrains release catalogue.
Check `https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html`
for valid versions.

Integration tests are **much slower** than unit tests (full IDE download + launch)
and run sequentially (`maxParallelForks = 1`). Reserve them for end-to-end workflow
validation; use unit tests for logic.

---

## Quick reference: which fixture to use

| Situation | Use |
|-----------|-----|
| Testing pure logic (no IDE APIs) | Plain JUnit — no fixture needed |
| Testing IDE services, PSI, VFS | `Testing.makeEmptyProject()` |
| Testing code that needs a Module (content roots, etc.) | `Testing.makeEmptyModule()` |
| Testing Dart language / code insight features | `Testing.makeCodeInsightModule()` |
| Testing console/process output without a real process | `NopProcessHandler` + recording subclass |
| Testing daemon API commands/replies | `DaemonApi(log::add)` + `api.dispatch(...)` |
| Testing daemon events received | `DaemonEvent.dispatch(json, listener)` |
| Replacing a service with a mock | `ServiceContainerUtil.registerServiceInstance()` |
| Working with files on disk | `TestDir` rule |
| Full IDE workflow testing | Integration tests (IDE Starter, JUnit 5) |
