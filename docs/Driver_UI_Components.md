# Finding UI Widgets with the IntelliJ Driver SDK

While raw XPath can be used to find any component on the screen, it is often brittle and can make tests difficult to read and maintain. The IntelliJ Driver SDK is designed to be used with the **Page Object Model (POM)** pattern, which provides a much cleaner, more robust, and more readable way to write UI tests.

## The Page Object Pattern

The core idea is to create special classes that represent a piece of your application's UI, such as a dialog or a tool window. This class is responsible for finding the widgets within that UI area and providing methods to interact with them.

### Key Components:

1.  **Page Object Class:** A class that extends `UiComponent`. It contains finders for all the interactive elements within that part of the UI.
    *   **Example:** `NewProjectDialogUI` in our project.

2.  **Finder Extension Function:** A helper function that makes it easy to get an instance of your Page Object. It extends the `Finder` interface.
    *   **Example:** `fun Finder.newProjectDialog(...)` allows you to write `newProjectDialog { ... }` in your test.

### How it Works: Scoped Searches

The power of this pattern comes from **scoped searches**. When you have a `UiComponent` object (like an instance of `NewProjectDialogUI`), any finder method you call on it will *only* search within that component's children, not the entire screen.

This is handled automatically by the `searchContext` property within every `UiComponent`. It makes the finders inside your Page Object short, simple, and stable.

```kotlin
// The Page Object for the "New Project" dialog
open class NewProjectDialogUI(data: ComponentData) : UiComponent(data) {
  // This XPath is NOT run against the whole screen.
  // It's automatically scoped to search ONLY within the dialog.
  val nextButton = x("//div[@text='Next']")
  val projectNameInput = x("//div[@class='LabeledComponent' and ./div[text()='Project name:']]//div[@class='ExtendableTextField']")
}
```

## Using Specific UI Element Classes

Instead of using the generic `UiComponent` for everything, the SDK provides specific classes for common Swing widgets and high-level IDE components. You should always prefer these, as they provide component-specific methods (e.g., `click()` on a button, `getText()` on a text field).

You use them by passing the class type to the `x(...)` finder method:

```kotlin
// In a Page Object
val createButton = x("//div[@text='Create']", JButtonUi::class.java)
val nameField = x("...", JTextFieldUI::class.java)
```

### Available UI Element Classes

Here is a more complete list of the most common and useful UI component classes provided by the SDK.

#### General IDE Components

Found in `com.intellij.driver.sdk.ui.components.common`:

*   `WelcomeScreenUI.kt`: The initial screen when no project is open.
*   `IdeaFrameUI.kt`: The main IDE window frame.
*   `FileChooserDialogUi.kt`: The native file chooser dialog.
*   `MainToolbarUI.kt`: The main toolbar at the top of the IDE.
*   `NavigationBarUi.kt`: The navigation bar showing the file path.
*   `LookupUI.kt`: The code completion and search lookup popup.
*   `JEditorUi.kt` / `JEditorTabs.kt`: The code editor component and its tabs.

#### Swing-Specific Components

Found in `com.intellij.driver.sdk.ui.components.elements`:

*   `Dialog.kt`
*   `PopupUI.kt`
*   `JButtonUi.kt`
*   `JCheckBoxUi.kt`
*   `JComboBoxUi.kt`
*   `JLabelUi.kt`
*   `JListUI.kt`
*   `JRadioButtonUi.kt`
*   `JScrollBarUi.kt`
*   `JTableUi.kt`
*   `JTextFieldUI.kt`
*   `JTreeUI.kt`
*   `ActionButtonUi.kt`
*   `JCheckboxTreeUI.kt`

By using these classes and the Page Object pattern, your tests will be significantly more readable, stable, and easier to maintain.
