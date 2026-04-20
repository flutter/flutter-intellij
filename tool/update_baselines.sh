#!/bin/bash
# Script to create or update baselines based on current verification reports.
# Run this from the repository root.

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # None (Reset)

echo -e "${BOLD}Running plugin verification...${NC}"

# Stale reports cleanup
rm -rf build/reports/pluginVerifier

# Discover versions from tool/baseline
VERSIONS=$(ls tool/baseline)

if [ -z "$VERSIONS" ]; then
  echo -e "${YELLOW}Warning: No baseline directories found in tool/baseline.${NC}"
  exit 0
fi

echo -e "${BOLD}Found versions to update: $VERSIONS${NC}"

for version in $VERSIONS; do
  echo -e "${BOLD}Verifying version $version...${NC}"
  ./gradlew verifyPlugin -PsingleIdeVersion=$version || true

  echo -e "${BOLD}Processing baseline for $version...${NC}"
  BASELINE="tool/baseline/$version/verifier-baseline.txt"
  REPORT=$(find build/reports/pluginVerifier -path "*-$version.*/report.md" | head -n 1)

  if [ -f "$REPORT" ]; then
    echo "Extracting issues from $REPORT"    
    mkdir -p "$(dirname "$BASELINE")"
    grep "^*" "$REPORT" | sort > "$BASELINE"
    echo -e "${GREEN}Updated baseline at $BASELINE${NC}"
  else
    echo -e "${YELLOW}Warning: Report does not exist for version $version. Skipping.${NC}"
  fi
done

echo -e "${BOLD}Done updating baselines.${NC}"
