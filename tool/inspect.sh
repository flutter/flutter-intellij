#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This script runs IntelliJ code inspections.
# Inspections define a set of rules that detect and correct abnormal code structure,
# potential bugs, and dead code. They can be configured via profiles and scopes.
# See: https://www.jetbrains.com/help/idea/code-inspection.html
#
# Usage: ./tool/inspect.sh [profile_name]

set -e

# Check for IDEA_HOME or common locations
IDEA_INSPECT_SCRIPT=""

if [ -n "$IDEA_HOME" ]; then
  IDEA_INSPECT_SCRIPT="$IDEA_HOME/bin/inspect.sh"
elif [ -f "/Applications/IntelliJ 2025.3.app/Contents/bin/inspect.sh" ]; then
  IDEA_INSPECT_SCRIPT="/Applications/IntelliJ 2025.3.app/Contents/bin/inspect.sh"
fi

if [ -z "$IDEA_INSPECT_SCRIPT" ] || [ ! -f "$IDEA_INSPECT_SCRIPT" ]; then
  echo "IntelliJ inspect script not found. Set IDEA_HOME to your IntelliJ installation."
  echo "Example: export IDEA_HOME=/Applications/IntelliJ\ IDEA\ CE.app/Contents"
  exit 1
fi

PROJECT_PATH=$(pwd)
PROFILE_NAME="${1:-Project Default}"
OUTPUT_PATH="$PROJECT_PATH/build/inspect_results"

echo "Running inspections using profile '$PROFILE_NAME'..."
echo "Output directory: $OUTPUT_PATH"
rm -rf "$OUTPUT_PATH"
mkdir -p "$OUTPUT_PATH"

"$IDEA_INSPECT_SCRIPT" "$PROJECT_PATH" "$PROFILE_NAME" "$OUTPUT_PATH" -v2

echo "Inspection complete. Results are in $OUTPUT_PATH"
