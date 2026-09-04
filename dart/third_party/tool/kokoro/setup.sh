#!/bin/bash

# Run a gradle command with retries to handle transient errors.
# See:
# - https://github.com/flutter/flutter-intellij/issues/9009
# - https://github.com/flutter/flutter-intellij/issues/9021
# Usage: run_gradle_with_retry [--max-retries N] [--delay-secs N] <gradle_args...>
run_gradle_with_retry() {
  local max_retries=2
  local delay_secs=15

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --max-retries)
        if [[ $# -lt 2 ]]; then
          echo "Error: --max-retries requires an argument." >&2
          return 1
        fi
        if [[ ! "$2" =~ ^[0-9]+$ ]]; then
          echo "Error: --max-retries requires a non-negative integer." >&2
          return 1
        fi
        max_retries="$2"
        shift 2
        ;;
      --delay-secs)
        if [[ $# -lt 2 ]]; then
          echo "Error: --delay-secs requires an argument." >&2
          return 1
        fi
        if [[ ! "$2" =~ ^[0-9]+$ ]]; then
          echo "Error: --delay-secs requires a non-negative integer." >&2
          return 1
        fi
        delay_secs="$2"
        shift 2
        ;;
      *)
        break
        ;;
    esac
  done

  local gradle_args=("$@")
  local total_attempts=$((1 + ${max_retries:-0}))
  local ATTEMPT=1
  local exit_code=0

  local gradlew_cmd="./gradlew"
  if [[ ! -x "$gradlew_cmd" ]]; then
    gradlew_cmd="./third_party/gradlew"
  fi

  echo "Gradle retry config: max_retries=$max_retries, total_attempts=$total_attempts, delay_secs=$delay_secs" >&2

  while [[ $ATTEMPT -le $total_attempts ]]; do
    echo "Running $gradlew_cmd ${gradle_args[*]} (Attempt $ATTEMPT of $total_attempts)..." >&2
    
    if "$gradlew_cmd" "${gradle_args[@]}"; then
      echo "Gradle command completed successfully." >&2
      return 0
    else
      exit_code=$?
      if [[ $exit_code -ge 128 ]]; then
        echo "Gradle command interrupted. Exiting." >&2
        return $exit_code
      fi
    fi

    echo "Gradle command failed on attempt $ATTEMPT." >&2
    
    if [[ $ATTEMPT -eq $total_attempts ]]; then
      echo "All $total_attempts attempts failed." >&2
      return $exit_code
    fi

    echo "Waiting $delay_secs seconds before retrying..." >&2
    sleep $delay_secs
    ATTEMPT=$((ATTEMPT + 1))
  done
}

setup() {
  # Fail on any error.
  set -e
  # Prevent pipeline failures from being masked (e.g. printVersion | tail).
  set -o pipefail

  java --version

  # Enable verbose output for Gradle
  export JAVA_OPTS=" -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true"

  run_gradle_with_retry --version
}
