# Plan for Creating a New Flutter App

1.  **Start IDE**
    *   This is handled by the test setup (`@BeforeEach` and `@ExtendWith`). No explicit action needed in the test body.

2.  **In the menu, go to New -> Project...**
    *   This is handled by the `welcomeScreen { createNewProjectButton.click() }` block.

3.  **In the New Project Dialog, select Flutter in the Generators list.**
    *   Use `newProjectDialog { chooseProjectType("Flutter") }`.

4.  **Select Next.**
    *   Click the "Next" button.
    *   **Assertion:** Assert that a new project dialog is open.
    *   **Assertion:** Assert that the "Project name" field is visible.
    *   **Assertion:** Assert that the "Project location" field is visible.

5.  **Name the project.**
    *   Enter a project name in the "Project name" field.
    *   Project name should be unique: use it_test_project_<current_timestamp>
    * 
6.  **Ensure that Android and iOS platforms are selected.**
    *   **Assertion:** Assert that the "Android" platform checkbox is selected.
    *   **Assertion:** Assert that the "iOS" platform checkbox is selected.

7.  **Add a description.**
    *   Enter a description in the "Description" field.

8.  **Ensure the application type is selected for the project type.**
    *   **Assertion:** Assert that the "Application" project type is selected.

9.  **Ensure that the Android language is Kotlin.**
    *   **Assertion:** Assert that the "Android language" is set to "Kotlin".

10. **Click create.**
    *   Click the "Create" button.

11. **Wait for the project to open.**
    *   Wait for the IDE to finish indexing and for the project to be fully loaded.

12. **Ensure a main.dart file is open in a tab, and the README.md file is open.**
    *   **Assertion:** Assert that a tab with the title "main.dart" is open.
    *   **Assertion:** Assert that a tab with the title "README.md" is open.
