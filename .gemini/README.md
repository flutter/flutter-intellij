# Gemini & Agent Skills Note

Shared repository workflows and skills are stored in [`.agents/skills/`](../.agents/skills/) following the cross-agent open standard.
Refer to [`.agents/skills/`](../.agents/skills/) for all executable project skills and standard operating procedures.

Other files in this directory:

- [`config.yaml`](config.yaml): Gemini Code Assist configuration for GitHub pull request reviews (review settings and ignore patterns).
- [`styleguide.md`](styleguide.md): The style guide that Gemini Code Assist reviews pull requests against. The [`code-review`](../.agents/skills/code-review/SKILL.md) skill and [`CLAUDE.md`](../CLAUDE.md) use it too.
- [`skills/triage/SKILL.md`](skills/triage/SKILL.md): A legacy Gemini-specific skill (`triage-helper`) for triaging unprioritized issues. New skills belong in `.agents/skills/`.
