#!/bin/bash
set -e

# Path to README.md and skills directory
README_FILE="README.md"
SKILLS_DIR=".agents/skills"

# Check if README file exists
if [[ ! -f "$README_FILE" ]]; then
  echo "Error: $README_FILE not found." >&2
  exit 1
fi

# Check if skills directory exists
if [[ ! -d "$SKILLS_DIR" ]]; then
  echo "No skills directory found at $SKILLS_DIR."
  exit 0
fi

exit_code=0

# 1. Check that every skill in .agents/skills/ is listed in README.md
for skill_path in "$SKILLS_DIR"/*; do
  if [[ -d "$skill_path" ]]; then
    skill_name="${skill_path##*/}"
    # Verify the skill is linked in README.md
    if ! grep -qF ".agents/skills/$skill_name/SKILL.md" "$README_FILE"; then
      echo "Error: Skill '$skill_name' is defined in $skill_path but not listed in $README_FILE." >&2
      exit_code=1
    fi
  fi
done

# 2. Check that every skill link in README.md actually exists in .agents/skills/
# Extract links matching '.agents/skills/<name>/SKILL.md' from README.md
while read -r link; do
  skill_name="${link#.agents/skills/}"
  skill_name="${skill_name%/SKILL.md}"
  if [[ ! -d "$SKILLS_DIR/$skill_name" ]]; then
    echo "Error: $README_FILE references skill '$skill_name' ($link) which does not exist in $SKILLS_DIR." >&2
    exit_code=1
  fi
done < <(grep -o '\.agents/skills/[a-zA-Z0-9_-]*/SKILL.md' "$README_FILE" || true)

if [[ $exit_code -eq 0 ]]; then
  echo "Success: All AI Agent Skills are correctly documented in $README_FILE."
else
  echo "Error: Documentation check failed. See errors above." >&2
fi

exit $exit_code
