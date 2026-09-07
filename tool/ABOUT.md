<!--* freshness: { reviewed: '2026-09-07' } *-->
# tool

## Overview
Root tooling directory for flutter-intellij. Contains grind tasks and other shell utilities for CI, deployment, and testing.

## Interface
- `check_agent_skills.sh`
- `github.bat`
- `github.sh`
- `grind.dart` (defines `main` entrypoint and `checkUrls` task)
- `provision_flutter.sh`
- `update_baselines.sh`

## Subdirectories & Boundaries
- `baseline`: IDE compatibility baseline definitions.
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Continuous integration and deployment scripts for Kokoro environments.
- `plugin`: [./plugin/ABOUT.md](./plugin/ABOUT.md) - Tooling to build, test, and deploy the flutter-intellij plugin.

## Invariants
- Scripts and grind tasks in this directory are executed from either the repository root or the tool directory.
- Tool packages use standalone Dart CLI packages (such as `args` and `grinder`) rather than Flutter framework dependencies.

## Side Effects
- Makes network requests (e.g., `checkUrls` makes HTTP GET requests)
- Executes external processes and commands (bash scripts, git grep, gradle scripts, jar, curl)
- Mutates the filesystem (creates directories, renames files, copies resources)
- Uploads the plugin ZIP to JetBrains Marketplace via curl

## Verification
- Test: `dart test`
