#!/usr/bin/env python3
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Prints the walkthrough summary for a test class, taken from the class's KDoc/Javadoc.

The summary is the doc comment directly above `class <Name>`, converted to Markdown, up to (not including) a line
that starts with "Implementation notes:". Write test doc comments for a viewer: what the test shows, the steps,
and the expected result; put setup and environment details under "Implementation notes:".

Usage: extract_test_summary.py <TestClass or fully.qualified.TestClass> [--repo <dir>]
Exits with status 1 (printing nothing) if the class or its doc comment is not found.
"""

import argparse
import pathlib
import re
import subprocess
import sys

STOP_MARKER = re.compile(r"^\s*Implementation notes:", re.IGNORECASE)


def find_source(repo: pathlib.Path, fqcn: str) -> pathlib.Path | None:
  simple = fqcn.split(".")[-1].split("#")[0]
  package_path = "/".join(fqcn.split(".")[:-1])
  # Include untracked files, so a new test that isn't committed yet still gets its summary.
  files = subprocess.run(["git", "-C", str(repo), "ls-files", "--cached", "--others", "--exclude-standard",
                          "*.kt", "*.java"],
                         capture_output=True, text=True, check=False).stdout.split()
  candidates = [f for f in files if pathlib.Path(f).stem == simple]
  if package_path:
    preferred = [f for f in candidates if package_path in f]
    candidates = preferred or candidates
  return repo / candidates[0] if candidates else None


def extract(source: str, simple_name: str) -> str | None:
  match = re.search(r"/\*\*(.*?)\*/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:\w+\s+)*class\s+" + re.escape(simple_name) + r"\b",
                    source, re.DOTALL)
  if not match:
    return None
  # Use only the last doc comment before the class (the regex may span earlier comments).
  body = match.group(1).split("/**")[-1]
  lines = []
  for raw in body.splitlines():
    line = re.sub(r"^\s*\* ?", "", raw).rstrip()
    if STOP_MARKER.match(line):
      break
    lines.append(line)
  text = "\n".join(lines).strip()
  text = re.sub(r"\[(\w[\w.#]*)\](?!\()", r"`\1`", text)  # KDoc [Symbol] links -> code
  return text or None


def main() -> int:
  parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
  parser.add_argument("test")
  parser.add_argument("--repo", type=pathlib.Path, default=pathlib.Path.cwd())
  args = parser.parse_args()

  path = find_source(args.repo, args.test)
  if path is None:
    return 1
  summary = extract(path.read_text(), args.test.split(".")[-1].split("#")[0])
  if summary is None:
    return 1
  print(summary)
  return 0


if __name__ == "__main__":
  sys.exit(main())
