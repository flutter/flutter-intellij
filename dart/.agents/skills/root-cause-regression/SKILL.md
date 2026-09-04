---
name: root-cause-regression
description: >-
  Investigates a GitHub issue reporting a regression, analyzes recent commits, and identifies possible root cause culprits. Use this when the user asks to find the cause of a regression or bug from an issue.
---

# Root Cause Regression Analysis

This skill guides you through investigating a GitHub issue to find the likely commit that caused a regression.

## Steps

1.  **Read the Issue**:
    *   **Primary**: Use the `github` MCP server (`issue_read` or equivalent) to fetch the details of the issue.
    *   **Fallback**: If the `github` MCP server is not available, try using the `gh` CLI (e.g., `gh issue view <number>`) or ask the user to paste the issue description into the chat.
    *   Understand the symptoms of the regression and when it started happening.
2.  **Identify the Timeframe**: Determine the approximate timeframe when the regression was introduced based on the issue description, comments, and reported version. If the timeframe isn't clear, ask the user or look at the repository's recent releases.
3.  **Fetch Recent Commits**: Use `search_commits` or `list_commits` from the `github` MCP server. If unavailable, fall back to local git commands (e.g., `git log --since="<date>" --until="<date>"`) to fetch commits within the suspected timeframe.
4.  **Analyze Commits**: Review the commit messages, changed files, and diffs for the suspected commits.
    *   Look for changes related to the systems, files, or components mentioned in the regression issue.
    *   Use `get_commit` (MCP) or `git show` (local) to view the details of specific commits.
5.  **Synthesize Findings**: Compile a list of the top suspect commits. For each suspect, explain *why* it is a potential culprit based on the code changes and how they relate to the regression symptoms.
6.  **Report Findings**: Generate a clean, well-formatted markdown Artifact containing your analysis. Present the findings clearly so the user can easily review the information and paste it into the GitHub issue UI if they choose to. Do not post the comment directly to GitHub.
