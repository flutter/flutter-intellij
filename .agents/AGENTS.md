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

## Adding or changing a skill: what presubmit checks

Every PR runs these from the repository root (`.github/workflows/presubmit.yaml`, job `check-ai-config`, and the
`skills_lint` grinder task in every checker bot). Run them locally before pushing anything under `.agents/`:

```bash
./tool/check_agent_skills.sh   # README.md must list every skill
./tool/validate_skills.sh      # skills_lint: links, paths, whitespace, frontmatter (needs `dart` on PATH)
./tool/check_ai_config.sh      # .aiexclude and .gemini/config.yaml agree and have no stale paths
```

What breaks them:

- **A skill directory not listed in `README.md`.** Every directory under `.agents/skills/` must appear in the
  "Available Workspace Skills" list as a link of the exact form `.agents/skills/<name>/SKILL.md`, and every such
  link must point at an existing directory. A directory counts as a skill as soon as it exists, so a PR that adds
  `scripts/` under a skill before the `SKILL.md` already fails; land them together.
- **A link in a `SKILL.md` that does not resolve** (`check-relative-paths`): every relative link must point at an
  existing *file* on the PR's branch. Links to directories (`scripts/`, `utils/`) fail; link to a file inside.
  Links to files that arrive in another PR fail until that PR merges; refer to them by path in backticks instead.
- **Absolute paths** in a `SKILL.md` (`check-absolute-paths`): never link `/usr/...`, `/google/...`, or
  `~/...`; make it relative to the skill directory or describe it in prose.
- **Trailing whitespace** anywhere in a `SKILL.md` (`check-trailing-whitespace`).
- **Frontmatter fields** other than the allowed ones (`disallowed-field`): keep to `name` and `description`.
- **A stale path in `.aiexclude` or `.gemini/config.yaml`** (`check_ai_config.sh`): if a skill or tool moves or is
  deleted, update both files.

The checker bots also run the unit tests and the plugin verifier; a skill-only PR still needs them green.
