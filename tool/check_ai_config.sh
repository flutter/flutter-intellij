#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

set -e

AIEXCLUDE_FILE=".aiexclude"
GEMINI_CONFIG=".gemini/config.yaml"

if [[ ! -f "$AIEXCLUDE_FILE" ]]; then
  echo "Error: $AIEXCLUDE_FILE not found." >&2
  exit 1
fi

if [[ ! -f "$GEMINI_CONFIG" ]]; then
  echo "Error: $GEMINI_CONFIG not found." >&2
  exit 1
fi

exit_code=0

aiexclude_norms=()
gemini_norms=()

# 1. Read and normalize entries from .aiexclude
while IFS= read -r line || [[ -n "$line" ]]; do
  # Strip trailing whitespace
  line="${line%"${line##*[![:space:]]}"}"
  # Skip empty lines and comments
  [[ -z "$line" || "$line" == \#* ]] && continue

  # Normalize path: strip leading '/' and trailing '/'
  norm="${line#/}"
  norm="${norm%/}"

  aiexclude_norms+=("$norm")

  # Check for stale references: if path does not exist on disk and is not gitignored, flag it
  if [[ ! -e "$norm" ]] && ! git check-ignore -q "$norm" 2>/dev/null && ! git check-ignore -q "$norm/" 2>/dev/null; then
    echo "Error: Stale reference '$line' in $AIEXCLUDE_FILE (path '$norm' does not exist and is not gitignored)." >&2
    exit_code=1
  fi
done < "$AIEXCLUDE_FILE"

# 2. Read and normalize entries scoped specifically under ignore_patterns in .gemini/config.yaml
in_ignore_patterns=0
while IFS= read -r line || [[ -n "$line" ]]; do
  # Strip trailing whitespace
  line="${line%"${line##*[![:space:]]}"}"
  [[ -z "$line" || "$line" == \#* ]] && continue

  if [[ "$line" == "ignore_patterns:"* ]]; then
    in_ignore_patterns=1
    continue
  fi

  # Exit ignore_patterns section when another top-level key starts
  if [[ $in_ignore_patterns -eq 1 && "$line" =~ ^[a-zA-Z0-9_-]+: ]]; then
    in_ignore_patterns=0
    continue
  fi

  if [[ $in_ignore_patterns -eq 1 ]]; then
    if [[ "$line" =~ ^[[:space:]]*-[[:space:]]+(.*)$ ]]; then
      item="${BASH_REMATCH[1]}"
      item="${item#\"}"
      item="${item%\"}"
      item="${item#\\'}"
      item="${item%\\'}"
      if [[ "$item" == *'/**' ]]; then
        norm="${item%/**}"
      else
        norm="$item"
      fi
      norm="${norm#/}"
      norm="${norm%/}"
      gemini_norms+=("$norm")
    fi
  fi
done < "$GEMINI_CONFIG"

# Helper to check array membership
contains_element() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    if [[ "$item" == "$needle" ]]; then
      return 0
    fi
  done
  return 1
}

# 3. Verify every .aiexclude entry is present in .gemini/config.yaml ignore_patterns
for norm in "${aiexclude_norms[@]}"; do
  if ! contains_element "$norm" "${gemini_norms[@]}"; then
    echo "Error: Pattern '$norm' is in $AIEXCLUDE_FILE but missing from $GEMINI_CONFIG ignore_patterns." >&2
    exit_code=1
  fi
done

# 4. Verify every .gemini/config.yaml ignore_patterns entry is present in .aiexclude
for norm in "${gemini_norms[@]}"; do
  if ! contains_element "$norm" "${aiexclude_norms[@]}"; then
    echo "Error: Pattern '$norm' in $GEMINI_CONFIG ignore_patterns is missing from $AIEXCLUDE_FILE." >&2
    exit_code=1
  fi
done

if [[ $exit_code -eq 0 ]]; then
  echo "Success: $AIEXCLUDE_FILE and $GEMINI_CONFIG are synchronized and contain no stale references."
else
  echo "Error: AI configuration check failed. See errors above." >&2
fi

exit $exit_code
