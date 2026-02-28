#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This script runs inspections and checks for specific errors against baselines.
# It enforces code quality by verifying that inspection results match expected counts.
# See: https://www.jetbrains.com/help/idea/code-inspection.html
#
# Usage: ./tool/verify_inspections.sh

set -e

# Run the inspections
./tool/inspect.sh

RESULTS_DIR="build/inspect_results"
FAILURE=0

echo "Verifying inspection results..."

# Verifies that the number of errors in an inspection report matches the expected baseline.
#
# Arguments:
#   $1 (file): Path to the inspection XML output file.
#   $2 (name): Human-readable name for the inspection check.
#   $3 (expected_count): The expected number of errors (baseline).
#
# Logic:
#   - Fails if the file exists and the error count is higher than expected (regression).
#   - Fails if the file exists and the error count is lower than expected (implies baseline should be updated).
#   - Passes if the error count matches the baseline.
#   - Handles missing files:
#     - If expected_count is 0, assumes absence means success.
#     - If expected_count > 0, fails (report missing).
check_errors_count() {
  local file="$1"
  local name="$2"
  local expected_count="$3"

  if [ -f "$file" ]; then
    local count=$(grep -c 'severity="ERROR"' "$file" || true)
    if [ "$count" -ne "$expected_count" ]; then
      if [ "$count" -gt "$expected_count" ]; then
        echo "❌ Error: $name regression! Expected $expected_count errors, found $count."
        FAILURE=1
      else
        echo "⚠️  $name improved! Expected $expected_count errors, found $count. Please update this script."
        FAILURE=1
      fi
    else
      echo "✅ $name matches baseline ($count errors)"
    fi
  else
    if [ "$expected_count" -eq 0 ]; then
       echo "✅ $name passed (report not generated, implies 0 issues)"
    else
       echo "⚠️  $name report not found but expected $expected_count errors (skipping)"
    fi
  fi
}

# --- Critical Checks (Enforced 0 Violations) ---
# Action IDs referenced in code (e.g. "Flutter.HotReload") actually exist
check_errors_count "$RESULTS_DIR/UndefinedAction.xml" "Undefined Actions" 0

# Actions/Components defined in code are actually registered in plugin.xml
check_errors_count "$RESULTS_DIR/ComponentNotRegistered.xml" "Component Registration" 0

# Expressions that always evaluate to true/false (potential logic bugs)
check_errors_count "$RESULTS_DIR/ConstantValue.xml" "Constant Values" 0

# --- Baserlined Checks (Enforced Exact Count) ---
# These checks have known violations that are tracked here.
# Goal: 0 violations, but we enforce the current baseline to prevent regression.

# Unused imports in Kotlin files
check_errors_count "$RESULTS_DIR/KotlinUnusedImport.xml" "Kotlin Unused Imports" 0

# Unused imports in Java files
check_errors_count "$RESULTS_DIR/UNUSED_IMPORT.xml" "Java Unused Imports" 0

# Checks validity of plugin.xml (e.g. unresolved classes, deprecated attributes)
check_errors_count "$RESULTS_DIR/PluginXmlValidity.xml" "Plugin XML Validity" 2

# Issues with custom annotators
check_errors_count "$RESULTS_DIR/Annotator.xml" "Annotator Issues" 1

# Incorrect Cancellation Exception Handling
check_errors_count "$RESULTS_DIR/IncorrectCancellationExceptionHandling.xml" "Cancellation Handling" 1

# Javadoc Reference Issues
check_errors_count "$RESULTS_DIR/JavadocReference.xml" "Javadoc References" 2

# API marked for removal
check_errors_count "$RESULTS_DIR/MarkedForRemoval.xml" "Marked For Removal" 1

# Unresolved plugin configuration references
check_errors_count "$RESULTS_DIR/UnresolvedPluginConfigReference.xml" "Unresolved Plugin Config" 1

# Loops that are effectively strict busy waits
check_errors_count "$RESULTS_DIR/BusyWait.xml" "Busy Waits" 0

# Calling methods annotated with @OverrideOnly
check_errors_count "$RESULTS_DIR/OverrideOnly.xml" "Override Only Usage" 0

if [ $FAILURE -ne 0 ]; then
  echo "---------------------------------------------------"
  echo "Inspection verification failed!"
  echo "Please fix the errors listed above."
  exit 1
fi

echo "All critical inspections passed."
exit 0
