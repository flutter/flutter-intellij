#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This script formats the codebase.
# - Dart: Uses 'dart format'
# - Java/Kotlin: Uses IntelliJ's format.sh (if configured)

set -e

echo "Formatting Dart files..."
dart format .

# Check for IDEA_HOME or common locations
IDEA_FORMAT_SCRIPT=""

if [ -n "$IDEA_HOME" ]; then
  IDEA_FORMAT_SCRIPT="$IDEA_HOME/bin/format.sh"
elif [ -f "/Applications/IntelliJ IDEA CE.app/Contents/bin/format.sh" ]; then
  IDEA_FORMAT_SCRIPT="/Applications/IntelliJ IDEA CE.app/Contents/bin/format.sh"
elif [ -f "/Applications/IntelliJ IDEA.app/Contents/bin/format.sh" ]; then
  IDEA_FORMAT_SCRIPT="/Applications/IntelliJ IDEA.app/Contents/bin/format.sh"
fi

if [ -n "$IDEA_FORMAT_SCRIPT" ] && [ -f "$IDEA_FORMAT_SCRIPT" ]; then
  echo "Formatting Java/Kotlin files using $IDEA_FORMAT_SCRIPT..."
  "$IDEA_FORMAT_SCRIPT" -r -m *.java,*.kt,*.xml,*.form,*.properties,*.json \
      src testSrc resources tool/plugin/lib tool/plugin/test

else
  echo "IntelliJ formatter not found. To format Java/Kotlin files, set IDEA_HOME to your IntelliJ installation."
  echo "Example: export IDEA_HOME=/Applications/IntelliJ\ IDEA\ CE.app/Contents"
fi

echo "Formatting complete."
