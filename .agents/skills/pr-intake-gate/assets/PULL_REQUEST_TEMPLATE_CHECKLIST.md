### PR Intake Pre-Flight Checklist
All gates must pass before marking this pull request ready for codeowner review:

- [ ] Executed the `pr-intake-gate` skill prior to requesting review.
- [ ] `CHANGELOG.md` updated under `## Unreleased`, OR commit message includes `NO CHANGELOG: <reason>`.
- [ ] Chromium BSD copyright headers present on all newly added files.
- [ ] `./gradlew verifyPlugin` completed with zero errors and summarized in commit message.
- [ ] If diff > 500 lines: commit message includes `LARGER PR: XXXX lines of change` and justification.
- [ ] Test integrity verified (no deleted tests, disabled tests, or weakened assertions).
- [ ] All Gemini / bot review `[MUST-FIX]` and `[CONCERN]` comments addressed or answered.
- [ ] Commit message includes the required verification stamp:
      `<!-- flutter-pr-check: passed @ <timestamp_epoch> (commit <sha>) -->`
