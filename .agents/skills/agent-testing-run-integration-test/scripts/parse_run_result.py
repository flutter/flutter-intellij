#!/usr/bin/env python3
"""Classify a run from build/agent-testing/run-result.json.

Usage:
  parse_run_result.py <run-result.json> --expect red  [--expect-oracle O3,O4]
  parse_run_result.py <run-result.json> --expect green
  parse_run_result.py <run-result.json> --expect plugin-off

Prints a one-line verdict and a JSON summary; exit 0 when the run matches
the expectation, 1 otherwise. This script, not the agent, decides whether a
red run is valid, so the rules live here:

  Valid red  := pluginLoaded && testsAborted == 0
                && pluginVersion contains "-broken-"   (the stamp build-broken-plugin.sh
                                                puts on every broken build; proves the
                                                IDE ran the broken bytes, not the real ones)
                && every step before the trigger completed
                && O1 passed
                && failurePhase == "ORACLE"
                && failedKind == "assertion"   (an exception inside an oracle block,
                                                e.g. a Driver error, is a test defect)
                && failedId in expected oracle parts (default O2,O3,O4)
             or failurePhase == "FREEZE" && freeze.confirmed

  Valid green := failurePhase == "NONE" && testsAborted == 0
                 && pluginVersion does NOT contain "-broken-"
                 && ideaLogSevere == []  && leakedProcesses == []

  Valid plugin-off (the FLUTTER_UI_TEST_WITHOUT_PLUGIN control) :=
                 pluginLoaded == false && failurePhase != "NONE" && testsAborted == 0
                 (any failure counts: a test that passes without the plugin isn't
                 testing the plugin)

Expected run-result.json shape (written by the harness):
{
  "test": "io.flutter.integrationTest.HotReloadUITest",
  "ide": "IU-262.10968.63",
  "bundleSha256": "...",
  "pluginLoaded": true,
  "pluginVersion": "SNAPSHOT-broken-20261113-030500", "pluginZip": "/tmp/.../Flutter-broken-....zip", "pluginZipSha256": "...",
  "testsStarted": 1, "testsSucceeded": 0, "testsAborted": 0,
  "steps":   [{"id": "1", "title": "...", "trigger": false, "status": "ok"}, ...],
  "oracles": [{"id": "O1", "status": "ok"}, {"id": "O3", "status": "failed", "message": "..."}],
  "failurePhase": "NONE" | "PREFLIGHT" | "SETUP" | "STEP" | "ORACLE" | "FREEZE" | "TEARDOWN",
  "failedId": "O3",
  "failureMessage": "...",
  "ideaLogSevere": ["..."],
  "leakedProcesses": [],
  "freeze": {"confirmed": true, "thread": "AWT-EventQueue-0", "frame": "io.flutter...", "dumps": 2},
  "wallClockSeconds": 212.4
}
"""
import json
import sys

BUCKETS = {
    "PREFLIGHT": "environment",
    "SETUP": "test-defect",
    "STEP": "test-defect",
    "TEARDOWN": "test-defect",
    "ORACLE": "product-behavior",
    "FREEZE": "product-behavior",
    "NONE": "pass",
}


def classify(r, expect, expected_oracles):
    phase = r.get("failurePhase", "NONE")
    bucket = BUCKETS.get(phase, "unknown")
    reasons = []

    if expect == "plugin-off":
        if r.get("pluginLoaded", False):
            reasons.append("plugin WAS loaded; this is not a plugin-off run")
        if phase == "NONE":
            reasons.append("test PASSED without the plugin: it is not testing the plugin")
        if r.get("testsAborted", 0):
            reasons.append(f"testsAborted={r['testsAborted']} (a skip is not a result)")
        verdict = "valid-plugin-off" if not reasons else "invalid-plugin-off"
        return verdict, ("control" if not reasons else "test-defect"), reasons

    if not r.get("pluginLoaded", False):
        reasons.append("plugin not loaded (environment)")
        bucket = "environment"
    if r.get("testsAborted", 0):
        reasons.append(f"testsAborted={r['testsAborted']} (a skip is not a result)")
        bucket = "test-defect"

    # Build identity: the IDE reports the plugin version it actually loaded. A broken build is always stamped
    # "-broken-<stamp>" by build-broken-plugin.sh, so a red run without the stamp ran the wrong plugin (the usual
    # cause: a stale sandbox or a zip that wasn't picked up), and a green run with it did too.
    version = r.get("pluginVersion") or ""
    stamped = "-broken-" in version
    if expect == "red" and not stamped:
        reasons.append(f"pluginVersion {version!r} has no '-broken-' stamp: the IDE did not load a broken build")
        bucket = "environment"
    if expect == "green" and stamped:
        reasons.append(f"pluginVersion {version!r} is a broken build; a green run must use the real plugin")
        bucket = "environment"

    steps = r.get("steps", [])
    oracles = {o["id"]: o for o in r.get("oracles", [])}
    trigger_idx = next((i for i, s in enumerate(steps) if s.get("trigger")), len(steps))
    pre_trigger_ok = all(s.get("status") == "ok" for s in steps[:trigger_idx])
    o1_ok = oracles.get("O1", {}).get("status") == "ok"

    if expect == "red":
        if phase == "FREEZE":
            ok = bool(r.get("freeze", {}).get("confirmed"))
            if not ok:
                reasons.append("freeze not confirmed in >= 2 dumps >= 5s apart")
            verdict = "valid-red" if ok and not reasons else "invalid-red"
        elif phase == "ORACLE":
            failed = r.get("failedId")
            if r.get("failedKind") not in (None, "assertion"):
                reasons.append(f"oracle {failed} threw {r.get('failureMessage', '')[:120]!r} instead of failing an assertion: a test or harness defect, not a red")
                bucket = "test-defect"
            if not pre_trigger_ok:
                reasons.append("a step before the trigger failed; the oracle was never reached honestly")
            if not o1_ok:
                reasons.append("O1 (pre-state) did not pass; the spec's pre-state is wrong")
            if failed not in expected_oracles:
                reasons.append(f"failed at {failed}, expected one of {sorted(expected_oracles)}")
            verdict = "valid-red" if not reasons else "invalid-red"
        elif phase == "NONE":
            reasons.append("test PASSED on the broken build: the test does not detect the fault (mutant survived)")
            verdict = "mutant-survived"
            bucket = "test-defect"
        else:
            reasons.append(f"failed in {phase}, not at the oracle")
            verdict = "invalid-red"
    else:  # green
        if phase != "NONE":
            reasons.append(f"failed in {phase} at {r.get('failedId')}: {r.get('failureMessage', '')[:200]}")
        if r.get("ideaLogSevere"):
            reasons.append(f"{len(r['ideaLogSevere'])} SEVERE idea.log entries with io.flutter frames")
            bucket = "product-behavior" if bucket == "pass" else bucket
        if r.get("leakedProcesses"):
            reasons.append(f"leaked processes: {r['leakedProcesses']}")
            bucket = "test-defect" if bucket == "pass" else bucket
        verdict = "valid-green" if not reasons else "not-green"

    return verdict, bucket, reasons


def main(argv):
    if len(argv) < 2 or "--expect" not in argv:
        print(__doc__)
        return 2
    path = argv[1]
    expect = argv[argv.index("--expect") + 1]
    expected_oracles = {"O2", "O3", "O4"}
    if "--expect-oracle" in argv:
        expected_oracles = set(argv[argv.index("--expect-oracle") + 1].split(","))
    with open(path, encoding="utf-8") as f:
        r = json.load(f)
    verdict, bucket, reasons = classify(r, expect, expected_oracles)
    summary = {
        "verdict": verdict,
        "bucket": bucket,
        "test": r.get("test"),
        "ide": r.get("ide"),
        "bundleSha256": r.get("bundleSha256"),
        "failurePhase": r.get("failurePhase"),
        "failedId": r.get("failedId"),
        "wallClockSeconds": r.get("wallClockSeconds"),
        "reasons": reasons,
    }
    print(f"{verdict.upper()} [{bucket}] {r.get('test')} phase={r.get('failurePhase')} id={r.get('failedId')}")
    for reason in reasons:
        print(f"  - {reason}")
    print(json.dumps(summary, indent=2))
    return 0 if verdict in ("valid-red", "valid-green", "valid-plugin-off") else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
