#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Runs NewProjectIU262UITest headless under Xvfb while recording the virtual screen, then builds a
# screenshot walkthrough.
#
# Usage: record_walkthrough.sh [output-dir]
#   Default output dir: build/walkthrough/<timestamp>
#
# Environment:
#   FLUTTER_SDK  Flutter SDK root. Defaults to the SDK that `flutter` on PATH belongs to.
#   TEST_CLASS   Fully qualified test to run. Default: io.flutter.integrationTest.NewProjectIU262UITest
#   MIN_PERCENT  Key-frame change threshold passed to build_walkthrough.sh. Default: 0.5

set -u

SCRIPT_DIR="$(cd "${BASH_SOURCE[0]%/*}" && pwd)"
REPO_DIR="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
cd "$REPO_DIR"

OUT="${1:-build/walkthrough/$(date +%Y%m%d-%H%M%S)}"
TEST_CLASS="${TEST_CLASS:-io.flutter.integrationTest.NewProjectIU262UITest}"

# --- Preflight -------------------------------------------------------------------------------
missing=()
for tool in xvfb-run Xvfb xdpyinfo import convert compare identify; do
  command -v "$tool" > /dev/null || missing+=("$tool")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "Missing tools: ${missing[*]} (install xvfb, x11-utils, imagemagick)" >&2
  exit 1
fi

if [ -z "${FLUTTER_SDK:-}" ]; then
  flutter_bin=$(command -v flutter || true)
  [ -n "$flutter_bin" ] && FLUTTER_SDK="$(cd "$(dirname "$(readlink -f "$flutter_bin")")/.." && pwd)"
fi
if [ -z "${FLUTTER_SDK:-}" ] || [ ! -x "$FLUTTER_SDK/bin/flutter" ]; then
  echo "Set FLUTTER_SDK to a Flutter SDK root (no flutter found on PATH)." >&2
  exit 1
fi
export FLUTTER_SDK

if pgrep -f 'ide-tests/cache/builds/' > /dev/null; then
  echo "An IDE Starter test IDE is already running; stop it first so the capture records the right display." >&2
  exit 1
fi

mkdir -p "$OUT/frames"
echo "Output: $OUT"

# --- Record ----------------------------------------------------------------------------------
bash "$SCRIPT_DIR/capture_xvfb.sh" "$OUT/frames" > "$OUT/capture.log" 2>&1 &
capture_pid=$!

# IDE Starter only uses Xvfb when DISPLAY is unset; Wayland would also block synthetic input.
(
  unset DISPLAY WAYLAND_DISPLAY
  ./gradlew --no-daemon buildPlugin integration --tests "$TEST_CLASS"
) > "$OUT/gradle.log" 2>&1
test_status=$?

# The capture stops by itself when the IDE's display closes; don't wait forever if it never started.
for _ in $(seq 30); do
  kill -0 "$capture_pid" 2> /dev/null || break
  sleep 1
done
kill "$capture_pid" 2> /dev/null
wait "$capture_pid" 2> /dev/null
cat "$OUT/capture.log"

# --- Build walkthrough -----------------------------------------------------------------------
bash "$SCRIPT_DIR/build_walkthrough.sh" "$OUT/frames" "$OUT" "${MIN_PERCENT:-0.5}"

echo
# Test stdout is only in the JUnit XML (Gradle hides it without --info).
grep -h -o -E 'Project [a-z0-9_]+ created and indexed\.|Successfully deleted test folder: [^&<]*' \
  build/test-results/integration/TEST-*.xml 2> /dev/null
grep -E 'FAILED|BUILD (SUCCESSFUL|FAILED)' "$OUT/gradle.log"
echo "Test exit status: $test_status"
echo "Walkthrough: $OUT/walkthrough.md"
exit "$test_status"
