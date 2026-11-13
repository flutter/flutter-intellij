# Workspace Agent Guidelines & Architecture Docs

This directory contains workspace customizations, rules, skills, and design documentation for AI agents working on `flutter-intellij`.

## Rules
- [`sdk-functionality.md`](rules/sdk-functionality.md): Guidelines and intentions for Flutter SDK management.

## Design Documents
- [`sdk-functionality.md`](design-docs/sdk-functionality.md): Full architectural design document for SDK handling in IntelliJ plugins.
- [`agent-integration-testing.md`](design-docs/agent-integration-testing.md): Proposal for agent skills that author, run, and validate IDE integration tests from workflows, bugs, and code changes.

## Skills
- [`ui-test-reference`](skills/ui-test-reference/SKILL.md): How Starter, Driver, and the Flutter UI test harness work; read before writing or repairing a test under `testSrc/integration`.
- [`record-test-walkthrough`](skills/record-test-walkthrough/SKILL.md): Write IntelliJ IDE Starter UI tests and record any automated UI test as a step-by-step screenshot walkthrough.
- Agent-driven integration testing (see the design doc above; specs live in [`testSrc/integration/specs/`](../testSrc/integration/specs/README.md)):
  - [`agent-testing-workflow-to-spec`](skills/agent-testing-workflow-to-spec/SKILL.md): Prose workflow or coverage gap → workflow spec, after the seam gate.
  - [`agent-testing-bug-to-spec`](skills/agent-testing-bug-to-spec/SKILL.md): Bug report → spec whose oracle encodes the correct behavior.
  - [`agent-testing-change-to-spec`](skills/agent-testing-change-to-spec/SKILL.md): Diff or PR → spec(s) for the changed behavior, with a red-build strategy.
  - [`agent-testing-author-integration-test`](skills/agent-testing-author-integration-test/SKILL.md): Spec → test class at the spec's level, one `assertOracle` block per oracle part.
  - [`agent-testing-run-integration-test`](skills/agent-testing-run-integration-test/SKILL.md): Certify a test: red on a broken plugin build, green on the real one, evidence report.
