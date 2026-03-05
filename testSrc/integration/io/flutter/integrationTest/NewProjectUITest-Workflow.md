# Workflow for `MyProjectUITest.newProjectIC`

This document outlines the expected workflow for the `newProjectIC` test in `MyProjectUITest.kt`. This test is an end-to-end integration test that verifies the creation of a new Flutter project in IntelliJ Community Edition.

## 1. Test Setup (`@BeforeEach`)

-   A unique project name is generated for each test run to avoid conflicts (e.g., `my_test_project_1678886400000`).

## 2. IDE and Test Driver Initialization

-   The test context is set up for IntelliJ Community (`IC`) edition.
-   A headless instance of the IDE is launched with a test driver attached. The driver allows the test to automate the IDE's UI.

## 3. Project Creation from Welcome Screen (`newProjectWelcomeScreen`)

This is the main "Act" part of the test.

-   **Open "New Project" Wizard:** The test waits for the IDE's welcome screen to appear and then clicks the "New Project" button.
-   **Select Project Type:** In the "New Project" dialog, the test waits for the list of project types to appear and then selects "Flutter".
-   **Navigate to Project Settings:** The test waits for the "Next" button to become enabled (which confirms that the Flutter SDK has been detected) and then clicks it.
-   **Enter Project Details and Create:**
    -   The test waits for the "Create" button to appear on the second screen of the wizard.
    -   It types the unique project name into the appropriate text field.
    -   It clicks the "Create" button to start the project creation process.

## 4. Project Verification (`newProjectInProjectView`)

This is the main "Assert" part of the test.

-   **Wait for IDE to be Ready:** The test waits for the main IDE window to appear. It then waits for up to 5 minutes for the IDE to finish all background tasks, such as indexing and downloading dependencies.
-   **Verify Project Structure:**
    -   The test opens the "Project" view tool window.
    -   It asserts that all the expected files and directories (`README.md`, `pubspec.yaml`, `lib/main.dart`, etc.) are present in the project tree.
-   **Open `main.dart`:** The test double-clicks the `lib/main.dart` file to open it in the editor, confirming that file navigation is working.

## 5. Test Teardown (`@AfterEach` and `@AfterAll`)

-   **Close IDE:** After each test, the IDE instance is closed.
-   **Clean Up Project Files:** After all tests in the class have run, the test project directory is deleted from the user's `IdeaProjects` folder to ensure a clean state for subsequent test runs.
