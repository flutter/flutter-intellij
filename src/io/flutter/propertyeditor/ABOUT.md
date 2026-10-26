<!--* freshness: { reviewed: '2026-10-25' } *-->
# Property Editor

## Overview
Integrates the DevTools Flutter Property Editor as a dedicated tool window within the IDE.

[PropertyEditorViewFactory](PropertyEditorViewFactory.java) is registered as the `Flutter Property Editor` tool window factory in [plugin.xml](../../../../resources/META-INF/plugin.xml). It extends [AbstractDevToolsViewFactory](../devtools/AbstractDevToolsViewFactory.java). The base class handles the Flutter SDK version gating (through `versionSupportsThisTool`) and builds the embedded DevTools content.

## Interface
- `PropertyEditorViewFactory`
- `PropertyEditorViewFactory.TOOL_WINDOW_ID`
- `PropertyEditorViewFactory.DEVTOOLS_PAGE_ID`
- `PropertyEditorViewFactory.versionSupportsThisTool`
- `PropertyEditorViewFactory.getToolWindowId`
- `PropertyEditorViewFactory.getToolWindowTitle`
- `PropertyEditorViewFactory.getDevToolsUrl`
- `PropertyEditorViewFactory.createToolWindowContent`

## Invariants
- The tool window ID is 'Flutter Property Editor'
- The tool window title is 'Property Editor'
- The DevTools page ID is 'propertyEditor'
- The tool requires a Flutter SDK version that supports the property editor
- The tool requires a Dart plugin version that supports the property editor
- If the Dart plugin is incompatible, error labels are shown instead of the tool content
- The DevTools URL is configured to be embedded and uses the `TOOL_WINDOW` IDE feature
- A warning is shown when the tool window is in 'Docked Unpinned' mode to prevent it from disappearing during use
- Content is built on first load and afterwards rebuilt only when the docked-unpinned state changes. The check runs on `toolWindowShown` for this tool window and on `SetToolWindowAutoHide` / `SetToolWindowType` state changes.

## Side Effects
- Mutates the static variable `previousDockedUnpinned`. Because it is static, the state is shared across all open projects.
- Subscribes a `ToolWindowManagerListener` to the project's MessageBus
- Registers the `MessageBusConnection` with the tool window's Disposer
- Modifies the contents of the ToolWindow to present DevTools or an error message
