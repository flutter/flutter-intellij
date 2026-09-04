#!/bin/bash
# Script to create or update baselines based on current verification reports.
# Run this from the repository root.

if [ ! -d "third_party" ]; then
  echo "Error: This script must be run from the repository root directory."
  exit 1
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m' # None (Reset)

echo -e "${BOLD}Running plugin verification...${NC}"
rm -rf third_party/build/reports/pluginVerifier
(cd third_party && ./gradlew verifyPlugin)

for version in 253 261 262; do
  echo -e "${BOLD}Processing baseline for $version...${NC}"
  BASELINE="third_party/tool/baseline/$version/verifier-baseline.txt"
  REPORT=$(find third_party/build/reports/pluginVerifier -path "*-$version.*/report.md" | head -n 1)

  if [ -f "$REPORT" ]; then
    echo "Extracting issues from $REPORT"    
    mkdir -p "$(dirname "$BASELINE")"
    grep "^*" "$REPORT" | grep -v "com\.intellij\.platform\.dartlsp" | sort > "$BASELINE"
    echo -e "${GREEN}Updated baseline at $BASELINE${NC}"
  else
    echo -e "${YELLOW}Warning: Report does not exist for version $version. Skipping.${NC}"
  fi
done

echo -e "${BOLD}Done updating baselines.${NC}"
