## Adding a Flutter Device List Tool Window

### 1. plugin.xml Declaration

Add this inside the `<extensions defaultExtensionNs="com.intellij">` block in `resources/META-INF/plugin.xml`, following the pattern of the existing tool windows at lines 325–333:

```xml
<toolWindow id="Flutter Devices"
            anchor="bottom"
            icon="FlutterIcons.Mobile"
            factoryClass="io.flutter.devicelist.FlutterDeviceListWindowFactory"
            canCloseContents="false" />
```

Key attributes: `id` is the display name and lookup key; `anchor` sets which IDE edge it docks to; `factoryClass` is the fully qualified `ToolWindowFactory` implementation.

---

### 2. ToolWindowFactory Skeleton

```java
package io.flutter.devicelist;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.flutter.utils.FlutterModuleUtils;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public class FlutterDeviceListWindowFactory implements ToolWindowFactory, DumbAware {

  public static final String TOOL_WINDOW_ID = "Flutter Devices";

  @Override
  public Object isApplicableAsync(@NotNull Project project,
                                  @NotNull Continuation<? super Boolean> $completion) {
    // Only show in projects that have a Flutter module
    return FlutterModuleUtils.hasFlutterModule(project);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    FlutterDeviceListPanel panel = new FlutterDeviceListPanel(project, toolWindow);
    Content content = ContentFactory.getInstance()
        .createContent(panel.getComponent(), "", false);
    toolWindow.getContentManager().addContent(content);
  }
}
```

Implementing `DumbAware` allows the window to be available during indexing (safe here because we only query `DeviceService`, not PSI).

---

### 3. Reacting to Device Changes — DeviceService Listener API

The flutter-intellij plugin does **not** use a MessageBus topic for device changes. `DeviceService` (`io.flutter.run.daemon.DeviceService`) is a project-scoped service (already registered in plugin.xml at line 273) that exposes its own listener mechanism:

- `DeviceService.getInstance(project).addListener(Runnable)` — subscribe
- `DeviceService.getInstance(project).removeListener(Runnable)` — unsubscribe
- Callbacks are **always delivered on the EDT** (via `SwingUtilities.invokeLater` inside `DeviceService.fireChangeEvent()`)

The panel implementation:

```java
public class FlutterDeviceListPanel implements Disposable {
  private final Project project;
  private final JPanel rootPanel;
  private final DefaultListModel<String> listModel;
  private final Runnable deviceListener;

  public FlutterDeviceListPanel(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    this.project = project;

    // Build UI (status label + scrollable device list)
    rootPanel = new JPanel(new BorderLayout());
    listModel = new DefaultListModel<>();
    rootPanel.add(new JBScrollPane(new JBList<>(listModel)), BorderLayout.CENTER);

    // Subscribe — callback arrives on EDT, no invokeLater needed
    deviceListener = this::refreshDeviceList;
    DeviceService.getInstance(project).addListener(deviceListener);

    refreshDeviceList(); // seed with current state

    // Clean up when the tool window is disposed
    Disposer.register(toolWindow.getDisposable(), this);
  }

  private void refreshDeviceList() {
    if (project.isDisposed()) return;
    DeviceService svc = DeviceService.getInstance(project);
    Collection<FlutterDevice> devices = svc.getConnectedDevices();
    FlutterDevice selected = svc.getSelectedDevice();

    listModel.clear();
    for (FlutterDevice device : devices) {
      String label = device.presentationName();
      if (device.equals(selected)) label = "\u2713 " + label;
      listModel.addElement(label);
    }
  }

  public @NotNull JComponent getComponent() { return rootPanel; }

  @Override
  public void dispose() {
    // Critical: remove listener so DeviceService doesn't hold a stale reference
    DeviceService.getInstance(project).removeListener(deviceListener);
  }
}
```

---

### 4. MessageBus Pattern (for reference / custom topics)

If you need to fire your own cross-component events via the MessageBus, here is the idiomatic IntelliJ pattern:

```java
// Declare topic + listener interface
public static final Topic<DeviceChangeListener> DEVICE_CHANGED_TOPIC =
    Topic.create("flutter.deviceChanged", DeviceChangeListener.class);

public interface DeviceChangeListener {
    void onDevicesChanged(@NotNull Collection<FlutterDevice> devices);
}

// Subscribe (scoped to tool window lifetime)
MessageBusConnection connection = project.getMessageBus().connect();
connection.subscribe(DEVICE_CHANGED_TOPIC,
    devices -> SwingUtilities.invokeLater(() -> rebuildList(devices)));
Disposer.register(toolWindow.getDisposable(), connection);

// Publish
project.getMessageBus().syncPublisher(DEVICE_CHANGED_TOPIC).onDevicesChanged(devices);
```

The key disposable rule from the skill applies here too: `messageBus.connect(project)` or `Disposer.register(parent, connection)` — never leave a connection unscoped.

---

### 5. Key Files Referenced

- `resources/META-INF/plugin.xml` — lines 325–333 show the existing tool window declarations; line 273 shows the `DeviceService` project service registration
- `src/io/flutter/run/daemon/DeviceService.java` — `addListener`/`removeListener`/`getConnectedDevices`/`getSelectedDevice`/`getStatus` API
- `src/io/flutter/run/FlutterDevice.java` — `presentationName()`, `deviceId()`, `deviceName()`, `platform()`, `emulator()`, `getIcon()`
- `src/io/flutter/view/InspectorViewFactory.java` — reference implementation of a `ToolWindowFactory` with MessageBus subscription
- `src/io/flutter/widgetpreview/WidgetPreviewToolWindowFactory.java` — simplest `ToolWindowFactory` pattern using `ContentFactory`
