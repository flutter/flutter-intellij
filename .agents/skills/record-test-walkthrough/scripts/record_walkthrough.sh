#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Records a screenshot walkthrough of any automated UI test.
#
# The recorder starts its own X display, runs the test with DISPLAY pointing at it, captures the
# display while the test runs, and renders the frames into walkthrough.html / walkthrough.md.
#
# Usage:
#   record_walkthrough.sh [options] --test <fqcn>       # Gradle test (default task: integration)
#   record_walkthrough.sh [options] -- <command> [args]  # any command that opens UI on $DISPLAY
#
# Options:
#   --mode headless|visible  headless (default): Xvfb, nothing shown on screen.
#                            visible: Xephyr window on your desktop, so you can watch the test live.
#   --test <fqcn>            Test class (or class.method) for `./gradlew --no-daemon <task> --tests`.
#   --task <name>            Gradle test task for --test. Default: integration.
#   --dest <folder>          Parent folder for walkthroughs; each run gets <folder>/<test>-<timestamp>.
#                            Default: $WALKTHROUGH_DIR, else ~/walkthroughs.
#   --out <dir>              Exact output directory (overrides --dest).
#   --title <text>           Walkthrough title. Default: the test name.
#   --summary <text|@file>   What the test shows (Markdown), shown at the top of the walkthrough. Default for
#                            --test: the test class's doc comment, up to "Implementation notes:".
#   --size <WxH>             Screen size. Default: 1920x1080.
#   --min-percent <n>        Key-frame threshold for build_walkthrough.sh. Default: 0.5.
#
# Both modes use a real X server, so tests that drive the UI with java.awt.Robot work even on a
# Wayland desktop (where Robot input to normal windows is blocked).

set -u

SCRIPT_DIR="$(cd "${BASH_SOURCE[0]%/*}" && pwd)"
REPO_DIR="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
cd "$REPO_DIR"

usage() {
  # Print the header comment block (everything after the license, up to the first code line).
  awk 'NR > 5 && /^#/ { sub(/^# ?/, ""); print; next } NR > 5 { exit }' "${BASH_SOURCE[0]}"
  exit "${1:-1}"
}

MODE=headless
TEST=""
TASK=integration
OUT=""
DEST="${WALKTHROUGH_DIR:-$HOME/walkthroughs}"
TITLE=""
SUMMARY=""
SIZE=1920x1080
MIN_PERCENT=0.5
CMD=()

while [ $# -gt 0 ]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --test) TEST="$2"; shift 2 ;;
    --task) TASK="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --dest) DEST="$2"; shift 2 ;;
    --title) TITLE="$2"; shift 2 ;;
    --summary) SUMMARY="$2"; shift 2 ;;
    --size) SIZE="$2"; shift 2 ;;
    --min-percent) MIN_PERCENT="$2"; shift 2 ;;
    -h|--help) usage 0 ;;
    --) shift; CMD=("$@"); break ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
done

if [ -n "$TEST" ]; then
  [ "${#CMD[@]}" -gt 0 ] && { echo "Use either --test or -- <command>, not both." >&2; exit 1; }
  CMD=(./gradlew --no-daemon "$TASK" --tests "$TEST")
  name="${TEST##*.}"
elif [ "${#CMD[@]}" -gt 0 ]; then
  name="$(basename "${CMD[0]}")"
else
  echo "Nothing to record: pass --test <fqcn> or -- <command>." >&2
  usage
fi
TITLE="${TITLE:-$name walkthrough}"
DEST="${DEST/#\~/$HOME}"
OUT="${OUT:-$DEST/${name}-$(date +%Y%m%d-%H%M%S)}"

case "$MODE" in
  headless) SERVER=Xvfb ;;
  visible) SERVER=Xephyr ;;
  *) echo "--mode must be headless or visible" >&2; exit 1 ;;
esac

# --- Preflight -------------------------------------------------------------------------------
missing=()
for tool in "$SERVER" xdpyinfo import convert compare identify python3; do
  command -v "$tool" > /dev/null || missing+=("$tool")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "Missing tools: ${missing[*]} (packages: xvfb, xserver-xephyr, x11-utils, imagemagick, python3)" >&2
  exit 1
fi
if [ "$MODE" = visible ] && [ -z "${DISPLAY:-}" ] && [ -z "${WAYLAND_DISPLAY:-}" ]; then
  echo "--mode visible needs a desktop session (DISPLAY or WAYLAND_DISPLAY) to show the Xephyr window." >&2
  exit 1
fi

# Flutter tests need an SDK; default to the one on PATH.
if [ -z "${FLUTTER_SDK:-}" ] && command -v flutter > /dev/null; then
  FLUTTER_SDK="$(cd "$(dirname "$(readlink -f "$(command -v flutter)")")/.." && pwd)"
fi
[ -n "${FLUTTER_SDK:-}" ] && export FLUTTER_SDK

mkdir -p "$OUT/frames"
OUT="$(cd "$OUT" && pwd)"
rm -f "$OUT/steps.tsv"
echo "Recording '$TITLE' ($MODE) -> $OUT"
echo "Command: ${CMD[*]}"

# --- Start the X display ---------------------------------------------------------------------
num=90
while [ -e "/tmp/.X${num}-lock" ] || [ -e "/tmp/.X11-unix/X${num}" ]; do num=$((num + 1)); done
TEST_DISPLAY=":$num"

if [ "$MODE" = headless ]; then
  Xvfb "$TEST_DISPLAY" -screen 0 "${SIZE}x24" -ac -nolisten tcp > "$OUT/xserver.log" 2>&1 &
else
  Xephyr "$TEST_DISPLAY" -screen "$SIZE" -ac -nolisten tcp -title "Walkthrough: $TITLE ($TEST_DISPLAY)" \
    > "$OUT/xserver.log" 2>&1 &
fi
server_pid=$!

cleanup() {
  [ -n "${capture_pid:-}" ] && kill "$capture_pid" 2> /dev/null
  kill "$server_pid" 2> /dev/null
}
trap cleanup EXIT

for _ in $(seq 50); do
  xdpyinfo -display "$TEST_DISPLAY" > /dev/null 2>&1 && break
  sleep 0.2
done
if ! xdpyinfo -display "$TEST_DISPLAY" > /dev/null 2>&1; then
  echo "$SERVER failed to start on $TEST_DISPLAY; see $OUT/xserver.log" >&2
  exit 1
fi

# --- Record ----------------------------------------------------------------------------------
bash "$SCRIPT_DIR/capture_display.sh" "$TEST_DISPLAY" "$OUT/frames" > "$OUT/capture.log" 2>&1 &
capture_pid=$!

(
  # Run the test against our display only; never the desktop's Wayland compositor.
  unset WAYLAND_DISPLAY
  export DISPLAY="$TEST_DISPLAY"
  # Tests that announce their steps (WalkthroughSteps) append "HHMMSS<TAB>title<TAB>what to expect" here.
  export WALKTHROUGH_STEPS_FILE="$OUT/steps.tsv"
  "${CMD[@]}"
) 2>&1 | tee "$OUT/test.log"
test_status=${PIPESTATUS[0]}

sleep 2 # Catch the final frame.
kill "$capture_pid" 2> /dev/null
wait "$capture_pid" 2> /dev/null
capture_pid=""
cat "$OUT/capture.log"

# --- Build walkthrough -----------------------------------------------------------------------
if [ -n "$SUMMARY" ]; then
  if [ "${SUMMARY:0:1}" = "@" ]; then cp "${SUMMARY:1}" "$OUT/summary.md"; else printf '%s\n' "$SUMMARY" > "$OUT/summary.md"; fi
elif [ -n "$TEST" ]; then
  python3 "$SCRIPT_DIR/extract_test_summary.py" "$TEST" --repo "$REPO_DIR" > "$OUT/summary.md" || rm -f "$OUT/summary.md"
fi
[ -f "$OUT/summary.md" ] || echo "No test summary found; add a doc comment to the test class or pass --summary."
bash "$SCRIPT_DIR/build_walkthrough.sh" "$OUT/frames" "$OUT" "$MIN_PERCENT"
python3 "$SCRIPT_DIR/render_walkthrough.py" "$OUT" --title "$TITLE" > /dev/null
# Refresh the index of all walkthroughs in the parent folder.
python3 "$SCRIPT_DIR/build_index.py" "$(dirname "$OUT")" || true

echo
echo "Test exit status: $test_status"
echo "Walkthrough: $OUT/walkthrough.html (self-contained) and $OUT/walkthrough.md"
echo "Edit $OUT/captions.txt, then re-render with: python3 $SCRIPT_DIR/render_walkthrough.py $OUT"
exit "$test_status"
