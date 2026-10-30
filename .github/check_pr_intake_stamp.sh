#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# PR Intake Gate Presubmit Verifier
# Checks that the PR commit message contains the required verification stamp
# and that automated review bot (Gemini Code Assist) comments have been triaged.

set -e

STAMP_REGEX="<!-- flutter-pr-check: passed @ [0-9]+ \(commit [a-f0-9]+\) -->"

# 1. Determine commit message(s) to check
if [[ -n "$GITHUB_BASE_REF" ]]; then
  # Running in GitHub Actions PR context: check all commits in the PR
  COMMIT_MESSAGES=$(git log "origin/$GITHUB_BASE_REF..HEAD" --pretty=%B 2>/dev/null || git log -1 --pretty=%B)
else
  # Local or non-PR context: check the latest HEAD commit
  COMMIT_MESSAGES=$(git log -1 --pretty=%B)
fi

HAS_STAMP=false
if echo "$COMMIT_MESSAGES" | grep -Eq "$STAMP_REGEX"; then
  HAS_STAMP=true
fi

# 2. Check for Gemini bot review status and unresolved findings if in GitHub Actions PR context
BOTS_RUN=true
BOT_COMMENTS_UNANSWERED=false
UNANSWERED_DETAILS=""

if [[ -n "$GITHUB_REPOSITORY" && -n "$PR_NUMBER" && -n "$GITHUB_TOKEN" ]] && command -v gh >/dev/null 2>&1; then
  # Fetch review comments using gh CLI if available
  COMMENTS_JSON=$(gh api "repos/$GITHUB_REPOSITORY/pulls/$PR_NUMBER/comments" 2>/dev/null || true)
  if [[ -n "$COMMENTS_JSON" ]]; then
    # Check if gemini bot has commented
    if echo "$COMMENTS_JSON" | grep -q "gemini-code-assist"; then
      BOTS_RUN=true
      # Check for unresolved MUST-FIX or CONCERN comments that lack replies
      # Note: Authors must reply with [INTENTIONAL] or [FALSE-POSITIVE] or push a commit fix
      UNANSWERED=$(echo "$COMMENTS_JSON" | grep -E "\[MUST-FIX\]|\[CONCERN\]" | wc -l || true)
      REPLIES=$(echo "$COMMENTS_JSON" | grep -E "\[INTENTIONAL\]|\[FALSE-POSITIVE\]|\[FIXED\]" | wc -l || true)
      if [[ "$UNANSWERED" -gt 0 && "$REPLIES" -lt "$UNANSWERED" ]]; then
        BOT_COMMENTS_UNANSWERED=true
        UNANSWERED_DETAILS="($UNANSWERED bot finding(s) flagged, but only $REPLIES explanatory reply/replies found)"
      fi
    else
      # If PR is not draft, bot might still need to run
      IS_DRAFT=$(gh pr view "$PR_NUMBER" --json isDraft --jq '.isDraft' 2>/dev/null || echo "false")
      if [[ "$IS_DRAFT" == "false" ]]; then
        BOTS_RUN=false
      fi
    fi
  fi
fi

# 3. Evaluate Pass / Fail
if [[ "$HAS_STAMP" == "true" && "$BOTS_RUN" == "true" && "$BOT_COMMENTS_UNANSWERED" == "false" ]]; then
  echo "========================================================================"
  echo "✅ [SUCCESS] PR Intake Gate Presubmit Passed!"
  echo "Found valid verification stamp in commit message."
  echo "========================================================================"
  exit 0
fi

# 4. Print detailed Failure Banner and Agent Instructions
echo "========================================================================"
echo "❌ [X] PR Intake Gate Presubmit Failure!"
echo "========================================================================"
echo "Your pull request cannot be reviewed by repository codeowners until all"
echo "intake gates have been verified and stamped."
echo ""

if [[ "$HAS_STAMP" != "true" ]]; then
  echo "  [X] Missing Verification Stamp:"
  echo "      No matching '<!-- flutter-pr-check: passed @ <timestamp> (commit <sha>) -->' pattern found in commit message."
  echo ""
fi

if [[ "$BOTS_RUN" != "true" ]]; then
  echo "  [X] AI / Gemini Code Assist Bots Have Not Run:"
  echo "      Keep the PR in Draft status until automated bot review completes."
  echo ""
fi

if [[ "$BOT_COMMENTS_UNANSWERED" == "true" ]]; then
  echo "  [X] Unaddressed Bot Review Findings: $UNANSWERED_DETAILS"
  echo "      All [MUST-FIX] and [CONCERN] findings must either be fixed in code"
  echo "      or answered with an inline [INTENTIONAL] or [FALSE-POSITIVE] reply."
  echo ""
fi

echo "------------------------------------------------------------------------"
echo "HOW TO RESOLVE USING AN AI AGENT (Local or Subscription):"
echo "------------------------------------------------------------------------"
echo "Address any bot comments, then run the 'pr-intake-gate' skill to"
echo "re-verify your changes and stamp your commit message:"
echo ""
echo "  1. Local Model / Local Agent (e.g. Ollama, llama.cpp, Aider, Goose):"
echo "       ollama run llama3.3 \"Read .agents/skills/pr-intake-gate/SKILL.md, verify my changes, and stamp my commit message.\""
echo "       aider --message \"Run the pr-intake-gate skill to audit diff and update commit message\""
echo ""
echo "  2. Antigravity:"
echo "       Prompt: \"Run the pr-intake-gate skill on my changes, address bot comments, and update my commit message.\""
echo ""
echo "  3. Claude Code:"
echo "       Prompt: \"/pr-intake-gate\" or \"Run the pr-intake-gate skill on current branch\""
echo ""
echo "  4. Cursor (Agent Mode / Composer):"
echo "       Prompt: \"Execute the pr-intake-gate skill to verify gates and stamp the commit.\""
echo ""
echo "The agent will execute all pre-flight gates (CHANGELOG, copyright headers,"
echo "./gradlew verifyPlugin, test integrity), format your commit message, and"
echo "generate the required verification stamp."
echo "========================================================================"

exit 1
