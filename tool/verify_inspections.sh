#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This script runs inspections and checks for specific errors.
# Usage: ./tool/verify_inspections.sh

set -e

# Run the inspections
./tool/inspect.sh

RESULTS_DIR="build/inspect_results"
FAILURE=0

echo "Verifying inspection results..."

# Function to check for errors in an XML file
check_errors() {
  local file="$1"
  local name="$2"

  if [ -f "$file" ]; then
    # Look for severity="ERROR"
    if grep -q 'severity="ERROR"' "$file"; then
      echo "❌ Error: Found critical issues in $name"
      grep -B 1 -A 5 'severity="ERROR"' "$file"
      FAILURE=1
    else
      echo "✅ $name passed (no errors)"
    fi
  else
    echo "⚠️  $name report not found (skipping)"
  fi
}

# --- Critical Checks (Enforced) ---
# Checks validity of plugin.xml (e.g. unresolved classes, deprecated attributes)
check_errors "$RESULTS_DIR/PluginXmlValidity.xml" "Plugin XML Validity"

# Checks if Actions/Components defined in code are actually registered in plugin.xml
check_errors "$RESULTS_DIR/ComponentNotRegistered.xml" "Component Registration"

# Checks if Action IDs referenced in code (e.g. "Flutter.HotReload") actually exist
check_errors "$RESULTS_DIR/UndefinedAction.xml" "Undefined Actions"

# --- Candidates for Future Enforcement (Commented Out) ---

# [1 violation] Issues with custom annotators
# check_errors "$RESULTS_DIR/Annotator.xml" "Annotator Issues"

# [2 violations] Loops that are effectively strict busy waits
# check_errors "$RESULTS_DIR/BusyWait.xml" "Busy Waits"

# [2 violations] Expressions that always evaluate to true/false (potential logic bugs)
# check_errors "$RESULTS_DIR/ConstantValue.xml" "Constant Values"

# [4 violations] Unused imports in Kotlin files
# check_errors "$RESULTS_DIR/KotlinUnusedImport.xml" "Kotlin Unused Imports"

# [4 violations] Calling methods annotated with @OverrideOnly
# check_errors "$RESULTS_DIR/OverrideOnly.xml" "Override Only Usage"

# [9 violations] Unused imports in Java files
# check_errors "$RESULTS_DIR/UNUSED_IMPORT.xml" "Java Unused Imports"

if [ $FAILURE -ne 0 ]; then
  echo "---------------------------------------------------"
  echo "Inspection verification failed!"
  echo "Please fix the errors listed above."
  exit 1
fi

echo "All critical inspections passed."
exit 0
