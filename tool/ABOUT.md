<!--* freshness: { reviewed: '2026-10-25' } *-->
# tool

## Overview
Root tooling directory for flutter-intellij. Contains grind tasks and other shell utilities for CI, deployment, and testing.

## Interface
- [`check_agent_skills.sh`](check_agent_skills.sh): Checks that the skills in `.agents/skills` are listed in the root `README.md` (presubmit).
- [`check_ai_config.sh`](check_ai_config.sh): Validates `.aiexclude` against `.gemini/config.yaml` (presubmit).
- [`check_verifier_baselines.sh`](check_verifier_baselines.sh): Compares plugin-verifier reports against the per-IDE baselines in `baseline/` (`check` mode, the default) or rewrites the baselines (`update` mode).
- [`github.bat`](github.bat): Windows CI script that installs Dart and runs `dart pub get` in `tool/plugin`.
- [`github.sh`](github.sh): GitHub Actions presubmit driver. The `BOT` env var selects the job (`DART_BOT`, `CHECK_BOT`, `UNIT_TEST_BOT`, `VERIFY_BOT`, `INTEGRATION_BOT`).
- [`grind.dart`](grind.dart): Defines the `main` entrypoint and two grinder tasks: `checkUrls` (`check-urls`) and `lintSkills` (`lint-skills`, runs `skills_lint` with `skills_lint.yaml`).
- [`provision_flutter.sh`](provision_flutter.sh): Downloads the pinned Flutter SDK to `../flutter` if it is missing. `FLUTTER_VERSION` is bumped by `.github/workflows/update_flutter.yaml`.
- [`skills_lint.yaml`](skills_lint.yaml): `skills_lint` config. Paths resolve relative to this file, so `../.agents/skills` needs `skills_lint` >=0.5.2.
- [`update_baselines.sh`](update_baselines.sh): Runs the plugin verifier and refreshes `baseline/` through `check_verifier_baselines.sh update`.
- [`validate_skills.sh`](validate_skills.sh): Runs `dart pub get` and then `skills_lint` from the repo root (presubmit).

## Subdirectories & Boundaries
- `baseline`: Per-IDE-branch plugin-verifier baselines (e.g. [`baseline/261/verifier-baseline.txt`](baseline/261/verifier-baseline.txt)), read by `check_verifier_baselines.sh` and written by `update_baselines.sh`.
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Continuous integration and deployment scripts for Kokoro environments.
- `plugin`: [./plugin/ABOUT.md](./plugin/ABOUT.md) - The `plugin` Dart CLI for linting, live-template generation, and dev-channel deployment.

## Invariants
- Scripts and grind tasks in this directory are executed from either the repository root or the tool directory.
- Tool packages use standalone Dart CLI packages (such as `args` and `grinder`) rather than Flutter framework dependencies.

## Side Effects
- Makes network requests (e.g., `checkUrls` makes HTTP GET requests)
- Executes external processes and commands (bash scripts, git grep, gradle scripts, jar, curl)
- Mutates the filesystem (creates directories, renames files, copies resources)
- Uploads the plugin ZIP to JetBrains Marketplace via curl

## Verification
- The root package has no tests. Run the plugin tool tests with `(cd tool/plugin; dart test)`.
- Skills: `dart tool/grind.dart lint-skills` (or `./tool/validate_skills.sh`).
