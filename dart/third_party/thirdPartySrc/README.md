# 🛑 DO NOT DIRECTLY EDIT UPSTREAM-SYNCED FILES 🛑

This directory contains both upstream-synced sources and locally maintained client libraries:

* `platform-lsp/`: Copied from JetBrains `intellij-community`. Any direct modifications must be codified in `.agents/skills/patch-copied-lsp-sources/scripts/patch.py` so they persist during the next sync.
* `analysisServer/org/dartlang/analysis/server/protocol/`: Protocol spec classes generated from upstream Dart SDK specs.
* `analysisServer/com/google/dart/server/`: Custom Java client wrapper utilities (including `RequestUtilities.java`) maintained locally in this repository. **Direct modifications here are permitted and expected** when updating client capability handshakes.
