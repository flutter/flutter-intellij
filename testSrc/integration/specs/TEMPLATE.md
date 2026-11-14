# <Goal, as a short title>

**Goal:** as a <kind of user>, I want <outcome>.
**Source:** <issue URL | PR or branch | manual>
**Seam gate result:** <`ui` | `platform`>. <One sentence: why the level below
can't prove this seam.> <Duplication check: "no existing spec shares this
fixture and boot state" or "extends `<spec>.md`">

## Target seam

- <`io.flutter.<package>.<Class>.<method>` | action ID | `plugin.xml` entry>
- <second seam if the workflow crosses two>

**Why not a unit test:** <what the integration adds that a unit test can't see>

## Fixture

- Base project: `flutter create <options> <name>`. Device: <Chrome | Linux desktop | emulator | none>.
- Overlay: <none | `testData/integration/overlays/<name>/`>
- Product: <`IU` and `AI` | `IU` only, because ...>. `ide.version`: <`latest` | pinned, because ...>

## Preconditions

- <SDK state>
- <open files, settings>
- <anything assumed from a bug report, marked "(assumed)">

## Steps

| # | Task | User interaction | Drives |
|---|---|---|---|
| 1 | <Task> | <what the user does, in the words of the UI> | <`helper(...)` \| `Action.Id` \| locator \| `probe`> |
| 2 | <Task> | **(trigger)** <...> | <...> |
| 3 | <Task> | <...> | <...> |

## Expected outcome

<The user-visible result, two or three sentences.>

**Requirements:** <optional: time or health bound, e.g. "completes within 30 s";
required for freeze and performance workflows>

## Oracle

- **O1 — Pre-state** (before the trigger step):
  - <what is true now that proves the outcome hasn't happened yet>
- **O2 — Attribution** (after the trigger):
  - <what proves our plugin's seam produced the outcome>
- **O3 — Positive postconditions:**
  - <the complete outcome, every surface>
- **O4 — Negative / analysis postconditions:**
  - <what must not be there: degraded outcomes, leftovers>
  - No `SEVERE` `idea.log` entries with `io.flutter` frames (harness, green runs).
  - <zero `ERROR` Dart analysis diagnostics in touched files, if files were edited>

## Test level

<`ui` — `ExistingProjectUITest` subclass, `@Tag("agent-generated")` |
`platform` — `BasePlatformTestCase`, `@Category(AgentGeneratedTest.class)`>

## Certification plan

- **Red build:** <fix reverted by the runner | `merge-base` | hollow seam in `<method>` | fault: `<one line describing the semantic fault>`>.
  Expected failure: <`O3` and/or `O4`>, after all steps and `O1` complete.
- **Green build:** `HEAD`, `N_green = <3 | 5>`.
- **N_red:** <1 | 3, because intermittent / freeze>
