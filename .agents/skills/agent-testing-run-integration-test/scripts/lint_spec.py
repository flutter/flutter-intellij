#!/usr/bin/env python3
"""Lint a workflow spec, and optionally the test written from it.

Usage:
  lint_spec.py <spec.md> [--test <TestFile.kt|.java>]

Exit code 0 when clean, 1 with one finding per line otherwise. Runs in well
under a second; it's meant to gate every IDE run.

Spec checks:
  - required sections present (Goal, Source, Target seam, Fixture,
    Preconditions, Steps, Expected outcome, Oracle, Test level,
    Certification plan)
  - Steps is a table with columns #, Task, User interaction, Drives, and at
    least one row marked (trigger)
  - Oracle has O1..O4, each with at least one bullet
  - Test level is `ui` or `platform`
  - Certification plan names an expected failing oracle part

Test checks (with --test):
  - one assertOracle("O1".."O4") call per oracle part, in order, O1 before
    the trigger step
  - no Thread.sleep, delay(, allEditorText(), Assumptions.assume, @Disabled,
    @EnabledIf, @Ignore
  - no contains(@class, 'Browser') style locators
  - ui tests carry @Tag("agent-generated"); platform tests carry
    @Category(AgentGeneratedTest
  - a KDoc/Javadoc link to the spec file
  - platform tests don't `new` plugin.xml-registered classes (heuristic: a
    `new Flutter...Action(` or `Flutter...Producer(` constructor call)
"""
import re
import sys
from pathlib import Path

REQUIRED_SECTIONS = [
    "Target seam",
    "Fixture",
    "Preconditions",
    "Steps",
    "Expected outcome",
    "Oracle",
    "Test level",
    "Certification plan",
]
REQUIRED_HEADER_FIELDS = ["**Goal:**", "**Source:**"]
ORACLE_PARTS = ["O1", "O2", "O3", "O4"]
STEP_COLUMNS = ["#", "Task", "User interaction", "Drives"]

FORBIDDEN_IN_TEST = [
    (r"Thread\.sleep\s*\(", "fixed sleep; use waitUntil"),
    (r"\bdelay\s*\(", "fixed delay; use waitUntil"),
    (r"allEditorText\s*\(", "reads every editor; use consoleText(toolWindow, sinceOffset)"),
    (r"Assumptions\.assume", "skips exit Gradle with code 0; declare capability in the spec Fixture instead"),
    (r"@Disabled\b", "disabled tests can't be certified"),
    (r"@EnabledIf\b", "conditional tests can't be certified"),
    (r"@Ignore\b", "ignored tests can't be certified"),
    (r"contains\(\s*@class\s*,\s*'Browser'\s*\)", "generic browser locator; query the DOM via JBCefJSQuery"),
]


def findings_for_spec(text: str, path: Path):
    out = []
    for field in REQUIRED_HEADER_FIELDS:
        if field not in text:
            out.append(f"{path}: missing header field {field}")
    sections = {m.group(1).strip(): m.start() for m in re.finditer(r"^##\s+(.+?)\s*$", text, re.M)}
    for sec in REQUIRED_SECTIONS:
        if not any(name.startswith(sec) for name in sections):
            out.append(f"{path}: missing section '## {sec}'")

    def section_body(name_prefix):
        names = sorted(sections.items(), key=lambda kv: kv[1])
        for i, (name, start) in enumerate(names):
            if name.startswith(name_prefix):
                end = names[i + 1][1] if i + 1 < len(names) else len(text)
                return text[start:end]
        return ""

    steps = section_body("Steps")
    header = re.search(r"^\|(.+)\|\s*$", steps, re.M)
    if not header:
        out.append(f"{path}: Steps must be a table")
    else:
        cols = [c.strip() for c in header.group(1).split("|")]
        if cols != STEP_COLUMNS:
            out.append(f"{path}: Steps columns must be {STEP_COLUMNS}, got {cols}")
        rows = [l for l in steps.splitlines() if l.startswith("|")][2:]
        if not rows:
            out.append(f"{path}: Steps table has no rows")
        if not any("(trigger)" in r for r in rows):
            out.append(f"{path}: no Steps row is marked (trigger)")
        for r in rows:
            cells = [c.strip() for c in r.strip("|").split("|")]
            if len(cells) == 4 and not cells[3]:
                out.append(f"{path}: Steps row {cells[0]} has an empty Drives cell (use `probe` if unknown)")

    oracle = section_body("Oracle")
    for part in ORACLE_PARTS:
        m = re.search(rf"^\s*-\s+\*\*{part}\b.*?$((?:\n\s+-\s+.+$)+)", oracle, re.M)
        if not m:
            out.append(f"{path}: Oracle part {part} missing or has no bullets")

    level = section_body("Test level")
    if not re.search(r"`(ui|platform)`", level):
        out.append(f"{path}: Test level must name `ui` or `platform`")

    plan = section_body("Certification plan")
    expected = re.search(r"Expected failure:\s*([^,.\n]*)", plan)
    if not expected or not re.search(r"\bO[234]\b", expected.group(1)):
        out.append(f"{path}: Certification plan must name an expected failing oracle part (O2, O3, or O4)")
    elif re.search(r"\bO1\b", expected.group(1)):
        out.append(f"{path}: Certification plan expects O1 to fail; O1 is the pre-state and must pass on red")
    return out


def findings_for_test(text: str, path: Path, spec_path: Path, level: str):
    out = []
    for pattern, why in FORBIDDEN_IN_TEST:
        for m in re.finditer(pattern, text):
            line = text.count("\n", 0, m.start()) + 1
            out.append(f"{path}:{line}: {why}")

    calls = [(m.group(1), m.start()) for m in re.finditer(r'assertOracle\(\s*"(O[1-4])"', text)]
    seen = [c for c, _ in calls]
    for part in ORACLE_PARTS:
        if part not in seen:
            out.append(f"{path}: no assertOracle(\"{part}\") block")
    if seen and seen != sorted(seen):
        out.append(f"{path}: assertOracle blocks out of order: {seen}")
    trigger = re.search(r'step\(\s*"[^"]*\(trigger\)', text)
    o1 = next((pos for c, pos in calls if c == "O1"), None)
    if trigger and o1 is not None and o1 > trigger.start():
        out.append(f"{path}: assertOracle(\"O1\") must run before the (trigger) step")

    if level == "ui" and '@Tag("agent-generated")' not in text:
        out.append(f"{path}: ui test must carry @Tag(\"agent-generated\")")
    if level == "platform" and "@Category(AgentGeneratedTest" not in text:
        out.append(f"{path}: platform test must carry @Category(AgentGeneratedTest.class)")
    if spec_path.name not in text:
        out.append(f"{path}: no link to the spec ({spec_path.name}) in the class doc comment")
    if level == "platform":
        for m in re.finditer(r"\bnew\s+(Flutter\w*(Action|Producer|ConfigurationType|Provider))\s*\(", text):
            line = text.count("\n", 0, m.start()) + 1
            out.append(f"{path}:{line}: constructs {m.group(1)} directly; resolve it through ActionManager / the extension point")
    return out


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    spec_path = Path(argv[1])
    test_path = None
    if "--test" in argv:
        test_path = Path(argv[argv.index("--test") + 1])
    spec = spec_path.read_text(encoding="utf-8")
    findings = findings_for_spec(spec, spec_path)
    level_m = re.search(r"##\s+Test level.*?`(ui|platform)`", spec, re.S)
    level = level_m.group(1) if level_m else "ui"
    if test_path:
        findings += findings_for_test(test_path.read_text(encoding="utf-8"), test_path, spec_path, level)
    for f in findings:
        print(f)
    if not findings:
        print(f"OK: {spec_path}" + (f" + {test_path}" if test_path else ""))
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
