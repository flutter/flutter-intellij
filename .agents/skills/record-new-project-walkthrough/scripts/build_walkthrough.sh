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

captions="$OUT/captions.txt"
{
  echo "# One caption per line, in step order. Lines starting with # are ignored."
  echo "# Edit, then run: render_walkthrough.py $OUT"
} > "$captions"

step=0
for f in "${keep[@]}"; do
  step=$((step + 1))
  stamp=$(basename "$f" .png | cut -d_ -f2)
  time="${stamp:0:2}:${stamp:2:2}:${stamp:4:2}"
  name=$(printf 'step_%02d_%s.png' "$step" "$stamp")
  convert "$f" -bordercolor black -fuzz 2% -trim +repage "$OUT/$name"
  echo "Captured at ${time}" >> "$captions"
done

echo "build_walkthrough: ${#keep[@]} of ${#frames[@]} frames kept"
python3 "$(cd "${BASH_SOURCE[0]%/*}" && pwd)/render_walkthrough.py" "$OUT" --frames-total "${#frames[@]}"
