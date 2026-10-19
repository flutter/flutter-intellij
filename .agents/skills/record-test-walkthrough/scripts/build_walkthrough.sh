#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Selects key frames from a capture, writes default captions, and renders the walkthrough
# (walkthrough.md and a self-contained walkthrough.html) via render_walkthrough.py.
#
# Usage: build_walkthrough.sh <frames-dir> <output-dir> [min-changed-percent]
#
# A frame is kept when more than <min-changed-percent> (default 0.5) of its pixels differ from the
# last kept frame, which drops cursor blinks and progress-bar ticks. Blank frames are skipped and
# the first non-blank frame is always kept. Kept frames are trimmed of the black Xvfb background
# around the IDE window.

set -eu

FRAMES="${1:?usage: build_walkthrough.sh <frames-dir> <output-dir> [min-changed-percent]}"
OUT="${2:?usage: build_walkthrough.sh <frames-dir> <output-dir> [min-changed-percent]}"
MIN_PERCENT="${3:-0.5}"

mapfile -t frames < <(ls "$FRAMES"/[0-9]*.png 2> /dev/null | sort)
if [ "${#frames[@]}" -eq 0 ]; then
  echo "build_walkthrough: no frames in $FRAMES" >&2
  exit 1
fi

mkdir -p "$OUT"
rm -f "$OUT"/step_*.png

small() { convert "$1" -scale 25% -depth 8 "$2"; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

keep=()
nonblank=()
last_small=""
total_pixels=0
for i in "${!frames[@]}"; do
  f="${frames[$i]}"
  small "$f" "$work/cur.png"
  # Skip blank frames: the Xvfb root window before the IDE draws or after it closes, and empty
  # windows that are still being painted (uniform once the black background is trimmed).
  if [ "$(convert "$work/cur.png" -format '%[fx:maxima>0.02]' info:)" = "0" ] ||
     [ "$(convert "$work/cur.png" -bordercolor black -fuzz 2% -trim +repage -format '%[fx:standard_deviation>0.02]' info:)" = "0" ]; then
    continue
  fi
  nonblank+=("$f")
  if [ -z "$last_small" ]; then
    keep+=("$f")
    total_pixels=$(identify -format '%[fx:w*h]' "$work/cur.png")
  else
    # compare may print the count in scientific notation, so compare in awk.
    changed=$(compare -metric AE -fuzz 5% "$last_small" "$work/cur.png" null: 2>&1 | cut -d' ' -f1)
    if awk -v c="$changed" -v t="$total_pixels" -v p="$MIN_PERCENT" 'BEGIN { exit !(c * 100 > t * p) }' ||
       [ "$i" -eq $((${#frames[@]} - 1)) ]; then
      keep+=("$f")
    else
      continue
    fi
  fi
  cp "$work/cur.png" "$work/last.png"
  last_small="$work/last.png"
done

# Match frames to the steps the test announced (steps.tsv: HHMMSS<TAB>title<TAB>what to expect), make sure every
# step has at least one frame, and write "frame<TAB>caption<TAB>expect" lines in display order.
printf '%s\n' "${keep[@]}" > "$work/keep.txt"
printf '%s\n' "${nonblank[@]}" > "$work/nonblank.txt"
python3 - "$work/keep.txt" "$work/nonblank.txt" "$OUT/steps.tsv" > "$work/plan.tsv" <<'PY'
import os, sys
keep = [l for l in open(sys.argv[1]).read().splitlines() if l]
nonblank = [l for l in open(sys.argv[2]).read().splitlines() if l]
stamp = lambda f: os.path.basename(f)[:-4].split("_")[1]
steps = []
if os.path.exists(sys.argv[3]):
  for line in open(sys.argv[3]).read().splitlines():
    parts = line.split("\t")
    if len(parts) >= 2:
      steps.append((parts[0], parts[1], parts[2] if len(parts) > 2 else ""))
def step_of(frame):
  current = None
  for i, (t, _, _) in enumerate(steps):
    if t <= stamp(frame):
      current = i
  return current
chosen = set(keep)
for i in range(len(steps)):
  if not any(step_of(f) == i for f in chosen):
    # Show where the step ended up: the last frame captured during it.
    during = [f for f in nonblank if step_of(f) == i]
    if during:
      chosen.add(during[-1])
frames = sorted(chosen, key=lambda f: os.path.basename(f))
counts = {}
for f in frames:
  counts[step_of(f)] = counts.get(step_of(f), 0) + 1
seen = {}
for f in frames:
  i = step_of(f)
  t = stamp(f)
  if i is None:
    caption, expect = f"Captured at {t[:2]}:{t[2:4]}:{t[4:]}", ""
  else:
    seen[i] = seen.get(i, 0) + 1
    caption, expect = steps[i][1], steps[i][2]
    if counts[i] > 1:
      caption += f" ({seen[i]}/{counts[i]})"
  print(f"{f}\t{caption}\t{expect}")
PY

captions="$OUT/captions.txt"
{
  echo "# One line per step, in order: caption, optionally followed by ' | ' and what to look for in that step."
  echo "# Lines starting with # are ignored. Edit, then run: render_walkthrough.py $OUT"
} > "$captions"

step=0
while IFS=$'\t' read -r f caption expect; do
  step=$((step + 1))
  stamp=$(basename "$f" .png | cut -d_ -f2)
  name=$(printf 'step_%02d_%s.png' "$step" "$stamp")
  convert "$f" -bordercolor black -fuzz 2% -trim +repage "$OUT/$name"
  if [ -n "$expect" ]; then echo "$caption | $expect" >> "$captions"; else echo "$caption" >> "$captions"; fi
done < "$work/plan.tsv"

echo "build_walkthrough: $step of ${#frames[@]} frames kept"
python3 "$(cd "${BASH_SOURCE[0]%/*}" && pwd)/render_walkthrough.py" "$OUT" --frames-total "${#frames[@]}"
