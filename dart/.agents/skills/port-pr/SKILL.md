---
name: port-pr
description: Fetches a Pull Request from either the dart-intellij-third-party or flutter-intellij repository and conceptually ports its changes to the other repository.
---

# Port PR Skill

This skill guides the agent to port architectural and conceptual changes from a Pull Request in one repository to another (specifically between the Dart and Flutter IntelliJ plugins).

## Step-by-Step Instructions

1. **Obtain the PR Link:**
   - If the user did not provide a Pull Request link or number in their request, ask them for the PR link before proceeding.

2. **Determine Origin and Target Repositories:**
   - Examine the PR link to determine the origin repository.
   - If the PR is from the Dart plugin repository (e.g., `JetBrains/intellij-plugins` or the `dart-intellij-third-party` fork), the target repository is `flutter-intellij`.
   - If the PR is from the `flutter/flutter-intellij` repository, the target repository is `dart-intellij-third-party`.

3. **Fetch PR Details using GitHub MCP:**
   - Use the `github` MCP server tools (like `get_pull_request`, `get_commit`, or `get_file_contents`) to fetch the PR's description, commits, and diffs. 
   - Note: If `get_pull_request` requires repository owner and name, extract them from the PR URL.

4. **Analyze the Diff:**
   - Read the changes carefully to understand what is being modified.
   - Identify the core conceptual and architectural changes being made (e.g., updating the build process, updating a specific IntelliJ Platform API usage, migrating to a new UI component).

5. **Map the File Structure:**
   - The file structures between `dart-intellij-third-party` and `flutter-intellij` are different, although they share some common IntelliJ plugin patterns.
   - Map the origin files to their equivalent or corresponding files in the target repository. 
   - Use `grep_search` or codebase exploration tools in the target repository to find where the comparable logic lives.

6. **Apply Changes Methodically:**
   - Make the necessary code changes in the target repository using your file editing tools (`write_file`, `edit_file`, etc.).
   - Adapt the code as necessary to fit the target repository's context, patterns, and language (Java vs Kotlin, etc.).
   - Do not just blindly copy-paste code; apply the *conceptual* changes.

7. **Verify Changes:**
   - Ensure the new code follows the style guidelines of the target repository.
   - If tests are affected or needed, update or add them.
   - Compile or lint the code if possible to verify correctness before presenting the changes to the user.
