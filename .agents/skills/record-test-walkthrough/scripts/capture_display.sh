#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Records an X display: samples it every 0.5s and saves a PNG whenever the screen changes. Runs
# until it receives SIGTERM/SIGINT or the display goes away.
#
# Usage: capture_display.sh <display> <frames-dir>

set -u

DISPLAY_TO_CAPTURE="${1:?usage: capture_display.sh <display> <frames-dir>}"
OUT="${2:?usage: capture_display.sh <display> <frames-dir>}"

mkdir -p "$OUT"
rm -f "$OUT"/*.png

running=true
trap 'running=false' TERM INT

last=""
n=0
misses=0
echo "capture: recording display $DISPLAY_TO_CAPTURE from $(date +%T)"
while $running; do
  tmp="$OUT/.tmp.png"
  # Stamp the frame (HHMMSSmmm) when the capture starts, so it can be matched to test steps precisely.
  stamp=$(date +%H%M%S%3N)
  if ! import -display "$DISPLAY_TO_CAPTURE" -window root "$tmp" 2> /dev/null; then
    misses=$((misses + 1))
    [ "$misses" -ge 5 ] && break
    sleep 1
    continue
  fi
  misses=0
  sig=$(convert "$tmp" -scale 25% -format '%#' info:)
  if [ "$sig" != "$last" ]; then
    n=$((n + 1))
    mv "$tmp" "$(printf '%s/%03d_%s.png' "$OUT" "$n" "$stamp")"
    last="$sig"
  fi
  sleep 0.5
done
rm -f "$OUT/.tmp.png"
echo "capture: stopped at $(date +%T); $n frames saved to $OUT"
