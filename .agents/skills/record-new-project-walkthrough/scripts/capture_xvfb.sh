#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Captures the Xvfb display that IntelliJ IDE Starter launches for UI tests. Samples every 0.5s and
# saves a PNG whenever the screen changes, until the display goes away.
#
# Usage: capture_xvfb.sh <frames-dir> [timeout-seconds-to-wait-for-display]
#
# IDE Starter starts the IDE via `xvfb-run --auto-servernum --server-num=88` with a
# 1920x1080x24 screen when DISPLAY is unset, so the display number is discovered from the
# running Xvfb process rather than assumed.

set -u

OUT="${1:?usage: capture_xvfb.sh <frames-dir> [wait-seconds]}"
WAIT_SECONDS="${2:-900}"

mkdir -p "$OUT"
rm -f "$OUT"/*.png

find_display() {
  local pid
  pid=$(pgrep -n -f 'Xvfb :[0-9]+ .*-screen 0 1920x1080x24') || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" | grep -o -E 'Xvfb :[0-9]+' | cut -d' ' -f2
}

display=""
for _ in $(seq "$WAIT_SECONDS"); do
  display=$(find_display) && xdpyinfo -display "$display" > /dev/null 2>&1 && break
  display=""
  sleep 1
done
if [ -z "$display" ]; then
  echo "capture: no IDE Starter Xvfb display appeared within ${WAIT_SECONDS}s" >&2
  exit 1
fi
echo "capture: recording display $display from $(date +%T)"

last=""
n=0
misses=0
while true; do
  tmp="$OUT/.tmp.png"
  if ! import -display "$display" -window root "$tmp" 2> /dev/null; then
    misses=$((misses + 1))
    [ "$misses" -ge 5 ] && break
    sleep 1
    continue
  fi
  misses=0
  sig=$(convert "$tmp" -scale 25% -format '%#' info:)
  if [ "$sig" != "$last" ]; then
    n=$((n + 1))
    mv "$tmp" "$(printf '%s/%03d_%s.png' "$OUT" "$n" "$(date +%H%M%S)")"
    last="$sig"
  fi
  sleep 0.5
done
rm -f "$OUT/.tmp.png"
echo "capture: display $display closed at $(date +%T); $n frames saved to $OUT"
