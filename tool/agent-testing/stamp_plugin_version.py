#!/usr/bin/env python3
"""Stamp a Flutter plugin zip with a version suffix, so a deliberately broken build can never be mistaken for the
real one.

    stamp_plugin_version.py <plugin.zip> <suffix> [--out <stamped.zip>]
    stamp_plugin_version.py <plugin.zip> --show

The version in META-INF/plugin.xml inside the plugin's main jar becomes `<version>-<suffix>`, e.g.
`SNAPSHOT-broken-20261113-030500`. That string then appears everywhere the build identifies itself:

  - idea.log: `Loaded custom plugins: ..., Flutter (SNAPSHOT-broken-20261113-030500)`
  - the IDE's Settings > Plugins page
  - run-result.json `pluginVersion`, which parse_run_result.py checks (red must be stamped, green must not be)
  - the `--show` output of this script, for the evidence report

Nothing else in the zip changes: the jar is rewritten entry by entry with the same names and compression, only
plugin.xml differs. Works on any revision of the plugin, so build-broken-plugin.sh can stamp builds of old
commits (--rev) whose Gradle script has no version override.
"""
import io
import re
import sys
import zipfile

MAIN_JAR = re.compile(r"^[^/]+/lib/flutter-intellij[^/]*\.jar$")
VERSION = re.compile(r"<version>([^<]*)</version>")


def find_main_jar(outer):
    names = [n for n in outer.namelist() if MAIN_JAR.match(n) and "searchableOptions" not in n]
    if len(names) != 1:
        sys.exit(f"expected exactly one main jar matching {MAIN_JAR.pattern}, found {names}")
    return names[0]


def read_version(outer):
    jar_name = find_main_jar(outer)
    with zipfile.ZipFile(io.BytesIO(outer.read(jar_name))) as jar:
        xml = jar.read("META-INF/plugin.xml").decode("utf-8")
    m = VERSION.search(xml)
    if not m:
        sys.exit("no <version> element in META-INF/plugin.xml")
    return jar_name, m.group(1)


def rewrite(src, dst, suffix):
    with zipfile.ZipFile(src) as outer:
        jar_name, old = read_version(outer)
        new = f"{old}-{suffix}"
        jar_bytes = outer.read(jar_name)
        new_jar = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(jar_bytes)) as jar, zipfile.ZipFile(new_jar, "w") as out_jar:
            for info in jar.infolist():
                data = jar.read(info)
                if info.filename == "META-INF/plugin.xml":
                    data = VERSION.sub(f"<version>{new}</version>", data.decode("utf-8"), count=1).encode("utf-8")
                out_jar.writestr(info, data)
        with zipfile.ZipFile(dst, "w") as out:
            for info in outer.infolist():
                data = new_jar.getvalue() if info.filename == jar_name else outer.read(info)
                out.writestr(info, data)
    return old, new


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    src = argv[1]
    if "--show" in argv:
        with zipfile.ZipFile(src) as outer:
            _, version = read_version(outer)
        print(version)
        return
    if len(argv) < 3:
        sys.exit(__doc__)
    suffix = argv[2]
    dst = argv[argv.index("--out") + 1] if "--out" in argv else src
    tmp = dst + ".tmp"
    old, new = rewrite(src, tmp, suffix)
    import os
    os.replace(tmp, dst)
    print(f"{src} -> {dst}: version {old} -> {new}", file=sys.stderr)
    print(new)


if __name__ == "__main__":
    main(sys.argv)
