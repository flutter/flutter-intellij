## Adding a Flutter Device List Tool Window

### 1. plugin.xml Declaration

```xml
<extensions defaultExtensionNs="com.intellij">
  <toolWindow
      id="Flutter Devices"
      anchor="bottom"
      secondary="false"
      icon="/icons/flutter_13.png"
      factoryClass="io.flutter.view.FlutterDeviceToolWindowFactory"
      conditionClass="io.flutter.view.FlutterDeviceToolWindowCondition"/>
</extensions>
```

### 2. ToolWindowFactory Skeleton

```java
public class FlutterDeviceToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FlutterDevicePanel panel = new FlutterDevicePanel(project, toolWindow);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
```

### 3. MessageBus for Live Updates

Define a topic in `DeviceService`:
```java
public static final Topic<DeviceListListener> DEVICE_LIST_CHANGED =
    Topic.create("Flutter Device List Changed", DeviceListListener.class);
```

Subscribe in the panel:
```java
project.getMessageBus()
       .connect(this)  // `this` is Disposable — auto-disconnects on dispose
       .subscribe(DeviceService.DEVICE_LIST_CHANGED,
                  (DeviceListListener) this::updateList);
```

Publish when devices change:
```java
project.getMessageBus().syncPublisher(DEVICE_LIST_CHANGED).devicesChanged(snapshot);
```

Always marshal Swing updates to the EDT via `SwingUtilities.invokeLater`.
