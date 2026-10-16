#!/usr/bin/env python3
# Copyright 2026 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Renders a recorded walkthrough directory into browsable files.

Reads step_*.png and captions.txt (one caption per line, in step order; lines starting with #
are ignored) from the walkthrough directory and writes:

  walkthrough.md    Standard Markdown with relative image links (GitHub, IDEs, Markdown viewers).
  walkthrough.html  A single self-contained page (images embedded) with a slideshow, keyboard
                    navigation, a thumbnail strip, and a "show all" view. Safe to copy or archive.

Usage: render_walkthrough.py <walkthrough-dir> [--title TITLE] [--frames-total N]
"""

import argparse
import base64
import datetime
import html
import json
import pathlib
import sys

META_FILE = ".walkthrough-meta.json"


def read_captions(path: pathlib.Path, count: int) -> list[str]:
  lines = []
  if path.exists():
    lines = [l.strip() for l in path.read_text().splitlines()
             if l.strip() and not l.lstrip().startswith("#")]
  return [lines[i] if i < len(lines) else f"Step {i + 1}" for i in range(count)]


def load_meta(directory: pathlib.Path, args: argparse.Namespace) -> dict:
  meta_path = directory / META_FILE
  meta = json.loads(meta_path.read_text()) if meta_path.exists() else {}
  meta.setdefault("captured", datetime.date.today().isoformat())
  if args.frames_total is not None:
    meta["frames_total"] = args.frames_total
  if args.title:
    meta["title"] = args.title
  meta.setdefault("title", "Test walkthrough")
  meta_path.write_text(json.dumps(meta, indent=2) + "\n")
  return meta


def summary(meta: dict, steps: int) -> str:
  text = f"Captured {meta['captured']}. {steps} steps"
  if meta.get("frames_total"):
    text += f" selected from {meta['frames_total']} captured frames"
  return text + "."


def write_markdown(directory: pathlib.Path, meta: dict, images: list[pathlib.Path],
                   captions: list[str]) -> pathlib.Path:
  out = [f"# {meta['title']}", "", summary(meta, len(images)), ""]
  for i, (image, caption) in enumerate(zip(images, captions), start=1):
    out += [f"## {i}. {caption}", "", f"![{caption}]({image.name})", ""]
  path = directory / "walkthrough.md"
  path.write_text("\n".join(out))
  return path


HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<style>
  :root {{ color-scheme: dark; --bg:#1e1f22; --panel:#2b2d30; --fg:#dfe1e5; --muted:#8c8f94; --accent:#3574f0; }}
  * {{ box-sizing: border-box; }}
  body {{ margin:0; font:15px/1.5 system-ui, sans-serif; background:var(--bg); color:var(--fg); }}
  header {{ padding:16px 24px 8px; }}
  h1 {{ margin:0; font-size:22px; }}
  .meta {{ color:var(--muted); }}
  .bar {{ display:flex; gap:8px; align-items:center; padding:8px 24px; }}
  button {{ background:var(--panel); color:var(--fg); border:1px solid #43454a; border-radius:6px; padding:6px 12px; cursor:pointer; }}
  button:hover {{ border-color:var(--accent); }}
  #counter {{ color:var(--muted); min-width:70px; }}
  #stage {{ padding:0 24px; text-align:center; }}
  #stage figure {{ margin:0; }}
  #stage img {{ max-width:100%; max-height:calc(100vh - 260px); border:1px solid #43454a; border-radius:6px; }}
  figcaption {{ font-size:17px; margin:10px 0 4px; }}
  #thumbs {{ display:flex; gap:8px; overflow-x:auto; padding:12px 24px 20px; }}
  #thumbs img {{ height:72px; border:2px solid transparent; border-radius:4px; cursor:pointer; opacity:.7; }}
  #thumbs img.active {{ border-color:var(--accent); opacity:1; }}
  #all {{ display:none; padding:0 24px 24px; }}
  #all figure {{ margin:0 0 32px; }}
  #all img {{ max-width:100%; border:1px solid #43454a; border-radius:6px; }}
  body.show-all #stage, body.show-all #thumbs, body.show-all .nav {{ display:none; }}
  body.show-all #all {{ display:block; }}
</style>
</head>
<body>
<header>
  <h1>{title}</h1>
  <div class="meta">{summary}</div>
</header>
<div class="bar">
  <button class="nav" id="prev" title="Previous (Left arrow)">&#8592; Prev</button>
  <button class="nav" id="next" title="Next (Right arrow)">Next &#8594;</button>
  <span class="nav" id="counter"></span>
  <span style="flex:1"></span>
  <button id="toggle" title="Toggle slideshow / all steps (A)">Show all steps</button>
</div>
<div id="stage"><figure><img id="main" alt=""><figcaption id="caption"></figcaption></figure></div>
<div id="thumbs"></div>
<div id="all"></div>
<script>
const steps = {steps_json};
let index = 0;
const main = document.getElementById('main');
const caption = document.getElementById('caption');
const counter = document.getElementById('counter');
const thumbs = document.getElementById('thumbs');
const all = document.getElementById('all');
steps.forEach((s, i) => {{
  const t = document.createElement('img');
  t.src = s.src; t.alt = s.caption; t.title = (i + 1) + '. ' + s.caption;
  t.onclick = () => show(i);
  thumbs.appendChild(t);
  const f = document.createElement('figure');
  f.innerHTML = '<figcaption></figcaption><img alt="">';
  f.querySelector('figcaption').textContent = (i + 1) + '. ' + s.caption;
  f.querySelector('img').src = s.src;
  all.appendChild(f);
}});
function show(i) {{
  index = (i + steps.length) % steps.length;
  main.src = steps[index].src;
  main.alt = steps[index].caption;
  caption.textContent = (index + 1) + '. ' + steps[index].caption;
  counter.textContent = (index + 1) + ' / ' + steps.length;
  [...thumbs.children].forEach((t, j) => t.classList.toggle('active', j === index));
  thumbs.children[index].scrollIntoView({{block: 'nearest', inline: 'nearest'}});
}}
function toggle() {{
  const on = document.body.classList.toggle('show-all');
  document.getElementById('toggle').textContent = on ? 'Slideshow' : 'Show all steps';
}}
document.getElementById('prev').onclick = () => show(index - 1);
document.getElementById('next').onclick = () => show(index + 1);
document.getElementById('toggle').onclick = toggle;
document.addEventListener('keydown', e => {{
  if (e.key === 'ArrowLeft') show(index - 1);
  else if (e.key === 'ArrowRight') show(index + 1);
  else if (e.key === 'Home') show(0);
  else if (e.key === 'End') show(steps.length - 1);
  else if (e.key === 'a' || e.key === 'A') toggle();
}});
show(0);
</script>
</body>
</html>
"""


def write_html(directory: pathlib.Path, meta: dict, images: list[pathlib.Path],
               captions: list[str]) -> pathlib.Path:
  steps = [{"caption": caption,
            "src": "data:image/png;base64," + base64.b64encode(image.read_bytes()).decode("ascii")}
           for image, caption in zip(images, captions)]
  page = HTML_TEMPLATE.format(
    title=html.escape(meta["title"]),
    summary=html.escape(summary(meta, len(images))),
    # Escape "</" so captions cannot terminate the script element.
    steps_json=json.dumps(steps).replace("</", "<\\/"),
  )
  path = directory / "walkthrough.html"
  path.write_text(page)
  return path


def main() -> int:
  parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
  parser.add_argument("directory", type=pathlib.Path)
  parser.add_argument("--title")
  parser.add_argument("--frames-total", type=int)
  args = parser.parse_args()

  directory = args.directory
  images = sorted(directory.glob("step_*.png"))
  if not images:
    print(f"render_walkthrough: no step_*.png in {directory}", file=sys.stderr)
    return 1

  meta = load_meta(directory, args)
  captions = read_captions(directory / "captions.txt", len(images))
  md = write_markdown(directory, meta, images, captions)
  page = write_html(directory, meta, images, captions)
  print(f"render_walkthrough: {len(images)} steps -> {md} and {page}")
  return 0


if __name__ == "__main__":
  sys.exit(main())
