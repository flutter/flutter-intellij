#!/bin/bash
source ./tool/kokoro/setup.sh
setup

echo "kokoro build start"

MAX_RETRIES=3
RETRY_DELAY_SECS=15
ATTEMPT=1

# Retry the build a few times to address transient failures.
# See: https://github.com/flutter/flutter-intellij/issues/9009
while [[ $ATTEMPT -le $MAX_RETRIES ]]; do
  echo "Running buildPlugin (Attempt $ATTEMPT of $MAX_RETRIES)..."
  
  exit_code=0\n  ./gradlew buildPlugin || exit_code=$?
  if [[ $exit_code -eq 0 ]]; then
    echo "Gradle build completed successfully."
    break
  fi

  echo "Build failed on attempt $ATTEMPT."
  
  if [[ $ATTEMPT -eq $MAX_RETRIES ]]; then
    echo "All $MAX_RETRIES attempts failed. Exiting."
    exit $exit_code
  fi

  echo "Waiting $RETRY_DELAY_SECS seconds before retrying..."
  sleep $RETRY_DELAY_SECS
  ATTEMPT=$((ATTEMPT + 1))
done

echo "kokoro build finished"
