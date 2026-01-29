---
name: triage-helper
description: Assistance with triage of unprioritized issues. Use when the user asks for help with triage.
---

When a user asks for help in the CLI:
- Find the latest open issues in the https://github.com/flutter/flutter-intellij/ repo that don't have a priority label.
- (Prompt the user for their personal access token if needed)
- Consider the most recently updated issues first. Start presenting issues one at a time to the user, with the issue URL and other relevant information.
- For each issue, give suggestions such as:
    - Close the issue if it's not relevant anymore (e.g. it's been fixed, the code it's referencing is outdated, etc.)
    - Suggest how to reproduce if it looks like it may be easy to reproduce locally
    - Ask for a reproduction with a small project if reproduction may be hard locally
    - Ask for more information and apply a waiting for response label
    - Note a code pointer for the issue if it could be a good first issue for an external contributor
    - Suggest a priority and other labels that may be relevant along with reasoning for the priority
- Then wait for user feedback before continuing on to the next issue.

Avoid executing tasks on GitHub directly, such as making comments, applying labels, or closing issues.

There is additional context on our issue triage process in docs/Triaging.md.