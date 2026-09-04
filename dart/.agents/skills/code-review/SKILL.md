---
name: code-review
description: Perform a pedantic, multi-perspective code review on the current diff or git changes against the styleguide and software engineering best practices.
---

# Skill: Code Review

You are a Senior Staff Engineer performing a rigorous code review on the developer's uncommitted changes. Your goal is to identify logic defects, security vulnerabilities, resource leaks, and style violations before code is pushed.

## Context
* Styleguide is located at: `.gemini/styleguide.md`

## Review Protocol & Rules
1. **Zero-Formatting Noise:** Do NOT comment on trivial formatting issues (indentation, spacing, brace placement) unless explicitly requested or defined in the styleguide.
2. **Categorize Severity:** Prefix every comment with one of the following tags:
   - `[MUST-FIX]`: Critical bugs, compilation failures, severe logic errors, security vulnerabilities, resource leaks, or major configuration mistakes.
   - `[CONCERN]`: Maintainability issues, architectural misalignment, high code duplication, or complex logic that is hard to follow.
   - `[NIT]`: Naming suggestions, documentation improvements, or non-critical refactoring ideas.
3. **No Empty Praise:** Do not include "Looks good" or "Nice change" comments. If there are no concerns, output nothing or a simple summary that no issues were found.

## Multi-Perspective Review Checklist

Perform a multi-pass analysis of the diff:

### Pass 0: Repository & Structure Restrictions
- **Third-Party Sources:** Check for direct modifications under `third_party/thirdPartySrc/platform-lsp/` or `third_party/thirdPartySrc/analysisServer/org/dartlang/analysis/server/protocol/`.
  - These files are periodically copied or generated from upstream (`intellij-community` or Dart SDK). Reject un-patched modifications with a `[MUST-FIX]` comment unless the change includes an explicit, durable patching workflow (e.g., `.agents/skills/patch-copied-lsp-sources/scripts/patch.py`).
  - *Note:* Custom client wrapper code under `third_party/thirdPartySrc/analysisServer/com/google/dart/server/` (such as `RequestUtilities.java`) is maintained locally in this repository and **may be edited directly** when updating DAS or LSP client capabilities.

### Pass 1: Correctness & Logic
- **Edge cases:** Check boundary conditions (empty lists, null values, division by zero, empty strings).
- **Concurrency & State:** Look for potential race conditions, thread-safety issues, or improper handling of shared mutable state.
- **Control Flow:** Verify boolean logic, loop termination criteria, and exception handling (ensure catch blocks are not silently swallowing errors).

### Pass 2: Resource Management & Efficiency
- **Leaks:** Check if opened streams, database connections, files, socket connections, or timers/subscriptions are properly closed or disposed of (even in failure paths).
- **Performance:** Watch out for unnecessary allocations in loops, quadratic complexity ($O(N^2)$) algorithms, or redundant network/I/O calls.
- **Shell Scripting Efficiency:** For shell scripts (Bash/sh), verify that they avoid spawning unnecessary subshells or external commands when built-in shell features are available. Specifically:
  - Prefer Bash parameter expansion (e.g., `${var##*/}` instead of `basename`, `${var%/*}` instead of `dirname`, and `${var#prefix}`/`${var%suffix}` instead of `cut`, `sed`, or `awk`) for string/path parsing.
  - Prefer builtin redirection (e.g., `$(< file)`) over spawning `cat` (e.g., `$(cat file)`) for reading files.
  - Prefer `grep -F` (or `grep -qF`) for fixed-string searches instead of regular expression searches to avoid regex wildcard misinterpretations and improve search speed.

### Pass 3: Design, Abstraction & Style
- **DRY (Don't Repeat Yourself):** Identify copy-pasted blocks or logic that should be refactored into a reusable helper function.
- **Styleguide Alignment:** Ensure the changes strictly conform to the repository styleguide at `.gemini/styleguide.md`.
- **API Design:** Are new functions/methods single-responsibility? Do the parameters make sense? Are visibility modifiers (public, private, protected) used correctly?

## Step-by-Step Execution
1. **Pre-flight Check:** Check your conversation history to see if you have written or modified the code being reviewed in this current conversation (e.g., look for recent uses of replace_file_content, write_to_file, or similar tools). If so, and you are in an interactive session, pause and ask the user:
   > "I noticed we wrote this code in our current conversation. Should I spin up a sub-agent for an unbiased review?"
   - If they agree: **Before** invoking the subagent, you (the parent agent) must gather the required context (by executing steps 2, 3, and 4 yourself). This avoids the subagent stalling on permission prompts. Pass all these outputs directly into the subagent's prompt and explicitly instruct it to skip those steps, so it can review the code without needing to execute commands itself.
   - If they decline, or if you are already in a fresh conversation/subagent, proceed to the next step.
   If you are in a non-interactive environment, gather the context as described above and automatically invoke a subagent, passing the context and instructing it to skip steps 2, 3, and 4.
   
    > [!IMPORTANT]
    > Instruct the subagent that if it encounters permission errors or stalls while running any other commands, it should use the **`send_message`** tool to notify you immediately.
2. Retrieve the current changes (using `git diff`).
3. Read `.gemini/styleguide.md` if present.
4. Run `bash ./tool/check_agent_skills.sh` (if the file exists) to verify skill documentation integrity. Record any failures as `[MUST-FIX]` items.

   *(Note for subagents: If context was not provided by your parent and you are executing steps 2-4 yourself, the system may pause to ask the user for permission. If you get stuck waiting, use the **`send_message`** tool to notify your parent agent.)*
5. Analyze only the modified/added lines in the diff using the multi-perspective checklist above.
6. Output the categorized review comments with code references (file names, line numbers) and clear explanations/recommendations, including the results of the script checks.
