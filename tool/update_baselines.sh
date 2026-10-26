#!/bin/bash

# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Run the IntelliJ plugin verifier and refresh the committed baselines from the
# resulting reports. Run this from the repository root.
#
# The comparison logic lives in check_verifier_baselines.sh so that the "check"
# and "update" paths can never drift apart.

set -uo pipefail

if [ ! -f "build.gradle.kts" ]; then
  echo "Error: This script must be run from the repository root directory."
  exit 1
fi

YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # None (Reset)

echo -e "${BOLD}Running plugin verification...${NC}"

rm -rf build/reports/pluginVerifier

VERSIONS=$(ls tool/baseline)

if [ -z "$VERSIONS" ]; then
  echo -e "${YELLOW}Warning: No baseline directories found in tool/baseline.${NC}"
  exit 0
fi

echo -e "${BOLD}Found versions to update: $VERSIONS${NC}"

# One IDE at a time: `verifyPlugin` deletes each IDE after use when
# `singleIdeVersion` is set (see build.gradle.kts), which is what keeps CI bots
# from running out of disk. The reports accumulate, so the baseline refresh
# below sees all of them.
#
# `verifyPlugin` exits non-zero when it finds problems, which is exactly the
# case we want to re-baseline, so its status is intentionally ignored here.
# check_verifier_baselines.sh fails if no reports were produced at all.
for version in $VERSIONS; do
  echo -e "${BOLD}Verifying version $version...${NC}"
  ./gradlew verifyPlugin -PsingleIdeVersion=$version --no-configuration-cache --no-daemon || true
done

exec ./tool/check_verifier_baselines.sh update
