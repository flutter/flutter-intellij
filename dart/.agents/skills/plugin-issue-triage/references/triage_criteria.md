# IntelliJ Plugin Issue Triage Criteria & Guidelines

This document defines the operational execution rules for classifying issues, assigning priorities, recommending owners, and drafting responses for the Dart and Flutter IntelliJ plugins.

---

## Canonical Sources of Truth

Refer to standard JetBrains and Flutter plugin guidelines when evaluating issues:
- Does it belong in the Dart plugin or Flutter plugin?
- Is it a core IntelliJ platform issue? (If so, recommend upstreaming to YouTrack).

---

## Issue Classification & Operational Action Flows

All triaged issues must be categorized and appropriately labeled.

### 1. Bug Reports

- **Analysis**:
  - **Reproduction**: Do not attempt local reproduction unless the steps are simple, clear, and the environment can be set up immediately. 
  - **Static Analysis**: For complex bugs, analyze the logs, stack traces, and relevant files to diagnose the issue. Look out for `java.lang.Throwable` or EDT-related exceptions.
- **Action**:
  - If reproduction steps or logs are missing, set action to `needs_info`, apply the `status: waiting-for-author-response` label.
  - If verified, suggest the appropriate priority (`P0` to `P3`) and the `bug` label. By default, do not propose an assignee (leave it blank). Only suggest an owner from the OWNERS file if the issue is highly specific to them.
  - If the issue is a core IDE bug, recommend `close_invalid` or `close_resolved` and suggest filing in JetBrains YouTrack.

### 2. Feature Requests

- **Analysis**:
  - Verify if the request aligns with the plugin's roadmap and design philosophy.
- **Action**:
  - If aligned, set action to `backlog` with priority `P2` or `P3` and suggest the `feature` label.
  - If it is more of an internal chore or maintenance, suggest the `task` label.
  - If out of scope, set priority to `P4` and action to `backlog` (keeping the issue open). 

### 3. Support Requests and Questions

- **Analysis**:
  - Identify if the issue is a question about usage or setup rather than a bug.
- **Action**:
  - Set action to `close_resolved` or `close_invalid`.
  - Answer the question directly or provide links to relevant guides or discussions, then close the issue.

---

## Response Guidelines

- **Be direct**: State the action being taken or what is needed immediately.
- **Eliminate fluff**: Do not use conversational filler (e.g., "I hope this helps").
