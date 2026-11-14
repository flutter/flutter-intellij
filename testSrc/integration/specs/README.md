# Workflow spec catalog

One row per user **Goal** for the Flutter and Dart IntelliJ plugins. A Goal with
no spec is a coverage gap and is the input to `agent-testing-workflow-to-spec`.
The spec format is [`TEMPLATE.md`](TEMPLATE.md); the design is
[`agent-integration-testing.md`](../../../.agents/design-docs/agent-integration-testing.md).

| Goal | Tasks | Spec | Level | Status | Priority | Owner |
|---|---|---|---|---|---|---|
| Run an app and see an edit after hot reload | Launch, Edit, Reload, Stop | `hot-reload-applies-edit.md` | `ui` | certified 2026-11-12 (red: reload→restart fault at O3; green 3/3 on IU 262.10968.63, Linux) | P0 | plugin team |
| Create a new Flutter project from the wizard after backing out of another project type ([#9106](https://github.com/flutter/flutter-intellij/issues/9106)) | Open wizard, Pick another type, Cancel, Pick Flutter, Pick SDK, Create | `new-project-after-cancel.md` | `ui` | planned (MVP T2) | P0 | plugin team |
| Run a widget test whose name is not ASCII ([#7985](https://github.com/flutter/flutter-intellij/issues/7985)) | Open test, Run from gutter, Read result | `run-test-non-ascii-name.md` | `platform` | planned (MVP T3) | P1 | plugin team |
| Open an existing Flutter project that has no `.idea/` ([#8845](https://github.com/flutter/flutter-intellij/issues/8845)) | Open folder, Wait for indexing, Confirm Flutter module | `open-project-without-idea-dir.md` | `ui` | certified on IU 252.28238.7 (MVP T4; passes on 262 with either build) | P1 | plugin team |
| See an error in the editor after typing invalid Dart | Open file, Type invalid code, Read error | `invalid-dart-shows-error.md` | `ui` | planned (MVP T5, Dart plugin seam) | P1 | plugin team |
| Change the Flutter SDK path in Settings without freezing the IDE ([#9013](https://github.com/flutter/flutter-intellij/issues/9013)) | Open settings, Change path, Apply | *none* (needs `two-sdks` overlay, freeze oracle) | `ui` | gap | P1 | plugin team |
| Open a project whose Flutter SDK path is invalid without freezing the IDE ([#9093](https://github.com/flutter/flutter-intellij/issues/9093)) | Open project, Wait | *none* (needs `invalid-sdk-path` overlay, freeze oracle; reported on Windows) | `ui` | gap | P2 | plugin team |

**Status values:** `planned` (spec being written), `authored` (test exists,
not certified), `certified` (red and green proven; see the evidence report),
`unvalidated-repro` (red proven on an open bug, no fix yet), `gap` (no spec),
`rejected: <reason>` (the seam gate said no; keep the row so the decision is
recorded).

Findings from certification runs that are not test defects (platform behaviour, third-party bugs, environment traps): [`FINDINGS.md`](FINDINGS.md).
