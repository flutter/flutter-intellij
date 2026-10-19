#!/usr/bin/env python3
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Writes <folder>/index.html listing every walkthrough in <folder> (newest first).

Each entry shows the walkthrough's title, capture date, step count, and the first line of its test summary, and
links to its walkthrough.html.

Usage: build_index.py <folder>
"""

import datetime
import html
import json
import pathlib
import re
import sys


def first_summary_line(directory: pathlib.Path) -> str:
  path = directory / "summary.md"
  if not path.exists():
    return ""
  for line in path.read_text().splitlines():
    if line.strip():
      return re.sub(r"^Shows:\s*", "", re.sub(r"\*\*|`", "", line.strip()))
  return ""


def main() -> int:
  if len(sys.argv) != 2:
    print(__doc__, file=sys.stderr)
    return 2
  folder = pathlib.Path(sys.argv[1]).expanduser()
  entries = []
  for page in folder.glob("*/walkthrough.html"):
    directory = page.parent
    meta_path = directory / ".walkthrough-meta.json"
    meta = json.loads(meta_path.read_text()) if meta_path.exists() else {}
    entries.append({
      "dir": directory.name,
      "title": meta.get("title", directory.name),
      "captured": meta.get("captured", ""),
      "steps": len(list(directory.glob("step_*.png"))),
      "summary": first_summary_line(directory),
      "mtime": page.stat().st_mtime,
    })
  entries.sort(key=lambda e: e["mtime"], reverse=True)

  rows = "\n".join(
    f'<tr><td><a href="{html.escape(e["dir"])}/walkthrough.html">{html.escape(e["title"])}</a>'
    f'<div class="dir">{html.escape(e["dir"])}</div></td>'
    f'<td>{html.escape(e["summary"])}</td><td>{html.escape(e["captured"])}</td><td>{e["steps"]}</td></tr>'
    for e in entries)
  page = f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>Walkthroughs</title>
<style>
  :root {{ color-scheme: dark; }}
  body {{ margin:24px; font:15px/1.5 system-ui, sans-serif; background:#1e1f22; color:#dfe1e5; }}
  table {{ border-collapse:collapse; width:100%; }}
  th, td {{ text-align:left; padding:8px 10px; border-bottom:1px solid #43454a; vertical-align:top; }}
  td:nth-child(3), td:nth-child(4) {{ white-space:nowrap; }}
  a {{ color:#6e9cf7; }} .dir, .meta {{ color:#8c8f94; font-size:13px; }}
</style></head><body>
<h1>Walkthroughs</h1>
<div class="meta">{len(entries)} walkthroughs in {html.escape(str(folder))}. Updated {datetime.datetime.now():%Y-%m-%d %H:%M}.</div>
<table><tr><th>Walkthrough</th><th>Shows</th><th>Captured</th><th>Steps</th></tr>
{rows}
</table></body></html>
"""
  (folder / "index.html").write_text(page)
  print(f"build_index: {len(entries)} walkthroughs -> {folder / 'index.html'}")
  return 0


if __name__ == "__main__":
  sys.exit(main())
