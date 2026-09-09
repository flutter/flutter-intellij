#!/bin/bash
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Fast fail on errors.
set -e

REPO_DIR="$(cd "${BASH_SOURCE[0]%/*}/.." && pwd)"
cd "$REPO_DIR"

dart tool/grind.dart lint-skills "$@"
