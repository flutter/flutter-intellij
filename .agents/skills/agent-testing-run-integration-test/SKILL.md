---
name: agent-testing-run-integration-test
description: Certify an agent-authored integration test for the Flutter or Dart IntelliJ plugins by proving it fails on a deliberately broken plugin build and passes on the real one, classifying failures from run-result.json, repairing test defects within a budget without weakening the oracle, and producing an evidence report with logs, screenshots, and a walkthrough. Modes certify (red then green, for every new or changed test), repro (red only, for open bugs), check (green only, for already-certified tests), and witness (the user runs the broken and the fixed build by hand in a verified sandbox). Use after agent-testing-author-integration-test. Don't use to write specs or tests.
---

# Skill: Run and Certify an Integration Test

Prove that a test means something. A test is **certified** when the same test
code fails on a plugin build that has the behavior broken and passes, three
times in a row, on the real build. You run the builds and the tests, classify
what happened from `run-result.json`, repair test defects inside a budget, and
write the evidence report. You never change what the test checks.

Design: [`agent-integration-testing.md`](../../design-docs/agent-integration-testing.md)
sections 5.6–5.9. Scripts: [`scripts/`](scripts/). Harness and platform
details: [`ui-test-reference`](../ui-test-reference/SKILL.md).

All modes run on Linux, macOS, and Windows. A workflow that applies to one
platform only says so in the spec's **Fixture**; otherwise the test is expected
to certify on whichever platform you're on, and the evidence report records
which. The only Linux-only pieces are headless Xvfb and the walkthrough
recorder.

## Modes

| Mode | For | Proof required |
|---|---|---|
| `certify` | Every new or modified test, from any `agent-testing-*-to-spec` path | **Valid red** on the broken build, then **green** `N_green` times on the real build, same test bytes |
| `repro` | Open bugs with no fix yet | **Valid red** on `HEAD`. Test is tagged `unvalidated-repro` until a fix passes `certify` |
| `check` | Tests already certified (new IDE build, new OS, periodic run) | Green `N_green` times |
| `witness` | The user wants to **see** the before and after with their own hands | Same two builds the automated runs used, each launched by `launch-ide.sh` and verified from its own log; a report per build |

`N_red = 1` for deterministic faults; `3` when the spec's Requirements say
intermittent or freeze. `N_green = 3` for `ui`, `5` for `platform`. A mixed
green result is **flaky** and fails; report it, don't rerun until it passes.

## Build identity: how anyone can tell which build ran

Every claim of "red" or "green" rests on knowing which plugin bytes the IDE
loaded. Three things make that checkable instead of assumed:

- `build-broken-plugin.sh` stamps every broken build's version with
  `-broken-<stamp>` (`SNAPSHOT-broken-20261113-030500`). The IDE prints it in
  `idea.log` (`Loaded custom plugins: … Flutter (SNAPSHOT-broken-…)`), in
  Settings › Plugins, and the harness writes it to `run-result.json` as
  `pluginVersion`, next to `pluginZip` and `pluginZipSha256`.
- `parse_run_result.py` refuses a red whose `pluginVersion` lacks the stamp
  and a green that has it. A stale sandbox or an ignored `-Pflutter.plugin.zip`
  therefore shows up as `environment`, never as a result.
- `tool/agent-testing/launch-ide.sh` (witness mode, below) only hands the user
  an IDE after proving from that IDE's log that the sandbox paths, the plugin
  version, and the jar's sha256 are the ones asked for. It kills the IDE
  otherwise.

Quote the version and sha from these sources in the evidence report; never
from what you intended to install.

## Workflow

### 1. Preflight

Fail fast, before any 4-minute run, on:

- `FLUTTER_SDK` unset or `flutter --version` failing; `flutter devices` not
  listing the spec's device.
- No display for `ui` tests. Linux: `DISPLAY` set, or Xvfb available for
  headless. macOS and Windows: a logged-in desktop session; headless isn't
  supported there.
- Less than 10 GB free, or the IDE cache missing when offline.
- Lint failure:
  ```bash
  python3 .agents/skills/agent-testing-run-integration-test/scripts/lint_spec.py testSrc/integration/specs/<name>.md --test <test file>
  ```
- A dirty `src/` or `resources/`: `git status --porcelain src resources` must
  be empty, except a component-naming commit the author reported.

Resolve `ide.version` to an exact build now and pass it to every run in this
certification, so red and green use the same IDE.

Record the **bundle hash**: `sha256` over the spec, the test file, and the
overlay directory. Red and green must report the same hash.

### 2. Build the broken plugin (certify and repro)

Read the spec's **Certification plan** and build accordingly, always in a
detached worktree so the working tree is never touched:

```bash
# Closed bug: HEAD with the fix reverted
tool/agent-testing/build-broken-plugin.sh --repo flutter --revert <fix-sha>...
# Change: the code before the change
tool/agent-testing/build-broken-plugin.sh --repo flutter --rev <merge-base>
# Workflow, refactor, or new behavior: a fault patch
tool/agent-testing/build-broken-plugin.sh --repo flutter --patch <fault.patch>
# Dart plugin seam
tool/agent-testing/build-broken-plugin.sh --repo dart --patch <fault.patch>
```

The script prints the zip path. Pass it to the test run as
`-Pflutter.plugin.zip=<zip>` or `DART_PLUGIN_ZIP=<zip>`. If it fails to
build, that's an **environment** result: report which strategy failed and
stop; don't switch strategies on your own, since a different fault proves a
different thing.

**Writing a fault patch** (workflow and refactor cases): 1–5 lines in the
seam the spec names. Make the outcome *wrong*, not absent: a reload that
restarts, an event that's dropped, a value off by one, a branch inverted.
Don't `throw`, and don't `return` at the top of the method when there's logic
below it; those trip O2 and leave O3/O4 untested. Write the patch from the
spec's Target seam and Steps **without reading the test's assertions**; if
you've already read them, say so in the report.

For the suite-wide control, also run once with
`FLUTTER_UI_TEST_WITHOUT_PLUGIN=true`: every `ui` test must fail.

### 3. Red run

```bash
FLUTTER_SDK=... ./gradlew --no-daemon integration --tests <fqcn> \
  -Pide.version=<pinned> -Pflutter.plugin.zip=<broken.zip>
```

Then classify:

```bash
python3 .agents/skills/agent-testing-run-integration-test/scripts/parse_run_result.py build/agent-testing/run-result.json --expect red --expect-oracle O3,O4
```

A red run is **valid** only when all of these hold, and the script is what
decides, not your reading of the log:

- `pluginLoaded` is true (the broken plugin loaded; it's broken, not missing).
- `testsAborted` is 0 (no `assumeTrue` skips).
- Every `step` before the trigger completed, and `O1` passed.
- `failurePhase` is `ORACLE` and the failed part is one the spec's
  certification plan named (or any of O2–O4 if it didn't).

Anything else is not red; it's one of the buckets in step 5. In particular, a
failure in `SETUP` or `STEP` on the broken build means the test can't reach
the oracle, and a failure at `O1` means the spec's pre-state is wrong.

For freeze specs, the harness samples thread dumps on timeout; valid red needs
the named thread in the named frame in two dumps at least 5 s apart.

### 4. Green runs

Same command, without the broken zip, `N_green` times. All must pass with
`failurePhase: NONE`, zero `idea.log` `SEVERE` entries with `io.flutter`
frames (the harness fails the run on those during green), and no leftover
`flutter`/`dart` processes. On Linux, attach the walkthrough recorder to the
last green run; it costs no extra boot. On macOS and Windows the per-step
screenshots the harness takes are the evidence instead:

```bash
.agents/skills/record-test-walkthrough/scripts/record_walkthrough.sh --test <fqcn> -- \
  ./gradlew --no-daemon integration --tests <fqcn> -Pide.version=<pinned>
```

### 5. Classify and repair

Every non-passing run goes in exactly one bucket, from `run-result.json`:

| Bucket | Signs | Action |
|---|---|---|
| **Environment** | `PREFLIGHT` phase; download, SDK, display, or build failure | Report and stop. No repair |
| **Test defect** | `SETUP` or `STEP` failure; `testsAborted > 0`; a locator not found; flaky green; the broken build *passing* | Repair, within budget |
| **Product behavior** | `ORACLE` or `FREEZE` after all steps passed | On the broken build: valid red. On the real build: a real finding; report it and stop |

Before you call anything a product finding, read
[`testSrc/integration/specs/FINDINGS.md`](../../../testSrc/integration/specs/FINDINGS.md): it lists the
platform and third-party behaviours already seen (Dart-plugin SEVEREs on 25x,
out-of-process JCEF freezing the EDT under Xvfb, IDE-build-dependent bugs).
When you find a new one, append an entry in the same format, with a link to the
evidence directory, in the same commit as the test.

Repair budget: **3 iterations or about 45 minutes**, whichever first. Repair
runs are `N = 1`. What a repair may touch: locators, waits (condition-based
only), setup steps, overlay files, the harness. What it may not touch: the
spec, any predicate inside an `assertOracle` block, the `idea.log` allowlist.

**Any edit to the test or overlay changes the bundle hash and invalidates the
red run.** After a repair, go back to step 3. If the repaired test passes on
the broken build, stop: the repair weakened the test, even if you don't see
how, and the user decides what happens next.

When the budget runs out, stop and report with the evidence so far.

### 6. Evidence report

Write to the directory the user names (default `~/agent-testing-evidence/<test>-<timestamp>/`), outside the repo:

```
report.md            summary table (below) + what was repaired and why
spec.md              copy of the spec
bundle.sha256
broken-build.diff    the revert, merge-base range, or fault patch
red/run-result.json  + idea.log slice + screenshots
green-1..N/          run-result.json each
witness/<label>/     manifest.json, report output, idea.log slice (witness mode)
walkthrough/         from the last green run (Linux); per-step screenshots elsewhere
git-status.txt       `git status --porcelain src resources` after everything: must be empty
```

Summary table in `report.md`:

| Field | Value |
|---|---|
| Test / spec | |
| Mode | certify / repro / check |
| IDE build / platform | pinned version; Linux, macOS, or Windows |
| Bundle hash | |
| Red | strategy; failed at `O3`/`O4`; wall-clock |
| Green | 3/3; wall-clock each |
| Repairs | count, what changed, why |
| Plugin-off control | failed as expected / not run |
| Build identity | `pluginVersion` + `pluginZipSha256` from red and green `run-result.json` |
| Witness | builds the user ran by hand, what they saw, agrees with red/green? / not run |
| Verdict | **certified** / unvalidated-repro / flaky / product finding / blocked |
| Suggested commit message | |

Report to the user with the verdict and the path, and list every finding
that is not a defect of the test under certification (new `FINDINGS.md`
entries, issues to file). Commit to the local branch only if asked. Never push.

### 7. Witness mode: the user sees it themselves

Offer this after every `certify` or `repro`, and run it whenever the user
asks to "see the bug", "try the broken build", "run before and after", or
doubts a result. It is the human check on the automated proof, and it uses
the **same** builds, IDE version, Dart version, and project the test used, so
what the user sees is what the test saw.

Preconditions: the IDE build is pinned and in Starter's cache (any run of the
test with `-Pide.version` put it there), the broken zip from step 2 still
exists, and the real plugin is built (`./gradlew buildPlugin` →
`build/distributions/Flutter.zip`). On 25x IDEs pass `--dart-version 506.1.0`
(Dart 510+ needs 253+); the launcher refuses an incompatible Dart.

For each build, in this order (broken first, so the fix is seen against the bug):

1. Close any IDE from the same build; the launcher refuses to start a second.
2. Launch and let the launcher verify:
   ```bash
   tool/agent-testing/launch-ide.sh --label broken --ide-version <pinned> \
     --plugin-zip <broken.zip> --project <fixture project> [--delete-idea-dir] [--dart-version 506.1.0]
   ```
   It prints a `VERIFIED` banner naming the sandbox, the version the IDE
   reported, and the jar sha256, or exits 1 with the mismatch and the IDE
   killed. Don't hand the user an unverified IDE and don't "fix" a mismatch by
   editing the sandbox; find out why the wrong bytes loaded.
3. Tell the user exactly what to do (the spec's **Steps**, in user terms), what
   the spec's oracle says they should see on this build, and how to confirm
   the build in the IDE (Settings › Plugins shows the `-broken-` version).
   Then wait; don't drive the IDE for them.
4. When they close it, collect the log evidence:
   ```bash
   tool/agent-testing/launch-ide.sh report <sandbox> --grep '<spec-specific marker>'
   ```
   and save `manifest.json`, the report output, and the `idea.log` slice to
   `witness/<label>/` in the evidence directory.
5. Repeat with `--label fixed --plugin-zip build/distributions/Flutter.zip`.

Add a **Witness** row to the report's summary table: which builds the user
saw, what they observed, and whether it matched the automated red and green.
A disagreement between the user and the test is a finding; record it and stop.

Pitfalls the launcher exists for, so you recognise them if something else is
used: Starter leaves `<ide-home>.vmoptions` beside the IDE pointing
`-Didea.*.path` at the **last test's** sandbox, and `-D` beats
`idea.properties` and `IDEA_PROPERTIES`; a sandbox reused across builds keeps
the old plugin; a project opened once grows an `.idea/` that changes the next
open; two IDEs on one system dir corrupt both. Every one of these makes
"broken" and "fixed" silently identical.

## Hard rules

- Never edit the spec or any `assertOracle` predicate. Repairing is not
  re-specifying.
- Never accept a red run that the parser rejects, and never classify by
  reading the log instead of `run-result.json`.
- Never run green before red in `certify`.
- Never leave a worktree, a broken zip in the working tree, or a change in
  `src/` or `resources/` behind. The last thing you do is check `git status`.
- Never push, open PRs, or comment on issues.
