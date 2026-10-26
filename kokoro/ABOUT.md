<!--* freshness: { reviewed: '2026-10-25' } *-->
# kokoro

## Overview
Configuration and scripts for the `flutter-intellij-kokoro` CI jobs on Google's internal Kokoro infrastructure. The job scripts here are thin wrappers around the shared scripts in [`../tool/kokoro/`](../tool/kokoro/ABOUT.md). Overall CI documentation is in [README.md](README.md).

## Subdirectories & Boundaries
- `gcp_windows`: [./gcp_windows/ABOUT.md](./gcp_windows/ABOUT.md) - Former Kokoro `gcp_windows` CI environment. Now holds only its `ABOUT.md`, with no configs or scripts.
- `macos_external`: [./macos_external/ABOUT.md](./macos_external/ABOUT.md) - Configuration and scripts for the active Kokoro `macos_external` CI environment.
