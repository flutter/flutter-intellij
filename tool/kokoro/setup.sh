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

# Send a signal to a process and all of its descendants.
# Usage: kill_tree <signal> <pid>
kill_tree() {
  local sig="$1"
  local pid="$2"
  local child
  for child in $(pgrep -P "$pid" 2>/dev/null); do
    kill_tree "$sig" "$child"
  done
  kill "-$sig" "$pid" 2>/dev/null || true
}

# Run a command, killing it (and any subprocesses) if it runs longer than the
# given number of seconds. macOS doesn't ship coreutils `timeout`, so this is a
# portable bash version. Returns the command's exit code, or 124 on timeout.
# Usage: run_with_timeout <seconds> <command...>
run_with_timeout() {
  local secs="$1"
  shift

  "$@" &
  local pid=$!

  local marker
  marker=$(mktemp)
  rm -f "$marker"
  (
    sleep "$secs"
    if kill -0 "$pid" 2>/dev/null; then
      touch "$marker"
      echo "Timed out after ${secs}s: $*" >&2
      kill_tree TERM "$pid"
      sleep 10
      kill_tree KILL "$pid"
    fi
  ) &
  local watchdog=$!

  local exit_code=0
  wait "$pid" || exit_code=$?

  kill_tree TERM "$watchdog"
  wait "$watchdog" 2>/dev/null || true

  if [[ -f "$marker" ]]; then
    rm -f "$marker"
    return 124
  fi
  return $exit_code
}

# Initialize everything required by the plugin tool.
setup() {
  # Fail on any error.
  set -e
  # Prevent pipeline failures from being masked (e.g. printVersion | tail).
  set -o pipefail

  # Display commands being run. Only do this while debugging and be careful
  # that no confidential information is displayed.
  # set -x

  # Set to 0 to use the system java, 1 to download/install a different version of java
  export USE_CUSTOM_JAVA=0

  echo "System Java version:"
  java --version
  # JAVA_HOME_OLD is used by runner.dart
  export JAVA_HOME_OLD=$JAVA_HOME
  echo "export JAVA_HOME_OLD=$JAVA_HOME"

  if [ "$USE_CUSTOM_JAVA" = 1 ] ; then
    echo "curl https://download.oracle.com/java/17/archive/jdk-17.0.4.1_macos-x64_bin.tar.gz > ../java.tar.gz"
    curl https://download.oracle.com/java/17/archive/jdk-17.0.4.1_macos-x64_bin.tar.gz > ../java.tar.gz
    (cd ..; tar fx java.tar.gz)
    echo "Custom java version:"
    java --version

    export JAVA_HOME=`pwd`/../jdk-17.0.4.1.jdk/Contents/Home
    echo "JAVA_HOME=$JAVA_HOME"
    export PATH=$PATH:$JAVA_HOME/bin
  fi

  export JAVA_OPTS=" -Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true"

  # Download and configure Flutter to the pinned stable release if not present
  source ./tool/provision_flutter.sh
  export PATH="$PATH":`pwd`/../flutter/bin:`pwd`/../flutter/bin/cache/dart-sdk/bin

  # These steps are informational and can hang indefinitely when the VM can't
  # reach github.com (the Flutter tool runs `git fetch`), which previously
  # consumed the full 3h Kokoro VM timeout. Bound them and keep going.
  run_with_timeout 300 flutter config --no-analytics \
    || echo "WARNING: 'flutter config' failed or timed out; continuing." >&2
  run_with_timeout 300 flutter doctor \
    || echo "WARNING: 'flutter doctor' failed or timed out; continuing." >&2

  export FLUTTER_SDK=`pwd`/../flutter
  export FLUTTER_KEYSTORE_ID=74840
  export FLUTTER_KEYSTORE_NAME=jetbrains-plugin-upload-auth-token
  export FLUTTER_KEYSTORE_JXBROWSER_KEY_NAME=flutter-intellij-plugin-jxbrowser-license-key
  export NO_FS_ROOTS_ACCESS_CHECK=true

  (cd tool/plugin; echo "dart pub get `pwd`"; dart pub get --no-precompile)
  run_gradle_with_retry --version
}
