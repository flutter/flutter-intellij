#!/usr/bin/env bash
# Launch the IDE the UI tests use, by hand, with a chosen Flutter plugin build, in a private sandbox, and PROVE
# from the running IDE's own log which build it loaded. For the "let me see the bug and the fix myself" step of
# agent-testing-run-integration-test (witness mode) and for debugging a test interactively.
#
# Usage:
#   launch-ide.sh --label <broken|fixed|any-word> --plugin-zip <Flutter.zip> [options]
#   launch-ide.sh report <sandbox-dir> [--grep <regex>]
#
# Options:
#   --ide-version <build>   exact IDE build, e.g. 252.28238.7 (default: ide.version from gradle.properties' IU line
#                           is NOT guessed; pass the build the certification is pinned to). Must already be in
#                           Starter's cache: build/out/ide-tests/cache/builds/IU-<build>/ (any UI test run with
#                           -Pide.version=<build> puts it there).
#   --project <dir>         project to open by hand; printed in the banner, not opened automatically
#   --delete-idea-dir       remove <project>/.idea before launching (for specs about projects without one)
#   --dart-version <v>      Dart plugin version from Starter's cache (default: $DART_PLUGIN_VERSION, else the newest
#                           cached one; on 25x IDEs use 506.1.0)
#   --lsp4ij-version <v>    LSP4IJ version from Starter's cache (default: newest cached)
#   --sandbox-root <dir>    where sandboxes live (default: ${TMPDIR:-/tmp}/agent-testing/manual)
#   --fresh                 wipe the sandbox for this label first (default: keep config/system, always rebuild plugins)
#   --wait                  block until the IDE exits, then print the report
#
# What it guarantees (or exits non-zero and kills the IDE):
#   1. The IDE's config/system/plugins/log paths are the sandbox's: Starter leaves <ide-home>.vmoptions next to the
#      IDE with -Didea.*.path pointing at the LAST TEST's sandbox, and -D beats idea.properties, so we pass our own
#      vmoptions through IDEA_VM_OPTIONS (which idea.sh prefers) and verify the paths in idea.log.
#   2. The plugins dir holds exactly: the given Flutter zip (sha256 of its main jar checked against the installed
#      one), the chosen Dart and LSP4IJ versions, nothing else.
#   3. idea.log's "Loaded custom plugins" line names the Flutter version the zip carries (broken builds are
#      stamped "-broken-<stamp>" by build-broken-plugin.sh, so a broken build can't pass as the real one).
#   4. No other instance of this IDE is running (two IDEs sharing a system dir would corrupt it and the log).
# The verified facts are written to <sandbox>/manifest.json for the evidence report.

set -euo pipefail

here="$(cd "$(dirname "$(realpath "$0")")" && pwd)"
repo="$(git -C "$here" rev-parse --show-toplevel)"
cache="$repo/build/out/ide-tests/cache"

log() { echo "[launch-ide] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

# ---------------------------------------------------------------- report
report() {
  local sb="$1" extra="${2:-}"
  local idea_log="$sb/log/idea.log"
  [[ -f "$idea_log" ]] || die "no idea.log in $sb/log"
  echo "== manifest"; cat "$sb/manifest.json" 2>/dev/null || echo "(none)"
  echo; echo "== identity lines from idea.log"
  grep -n 'IDE: IntelliJ\|idea.plugins.path=\|Loaded custom plugins' "$idea_log" | cut -c1-220
  echo; echo "== project open lines"
  grep -n 'Using processor\|ProjectUtil\|exit dumb mode\|UnindexedFilesIndexer - Finished\|Saving Project\|Last Action: WelcomeScreen.OpenProject' "$idea_log" | cut -c1-220 | tail -40 || true
  echo; echo "== SEVERE entries: $(grep -c ' SEVERE - ' "$idea_log" || true); stack frames in io.flutter: $(grep -c '^\s*at io\.flutter' "$idea_log" || true)"
  if [[ -n "$extra" ]]; then
    echo; echo "== lines matching /$extra/"
    grep -n -E "$extra" "$idea_log" | cut -c1-220 | tail -40 || true
  fi
}

if [[ "${1:-}" == report ]]; then
  shift; sb="${1:?sandbox dir}"; shift || true
  extra=""; [[ "${1:-}" == --grep ]] && extra="$2"
  report "$sb" "$extra"; exit 0
fi

# ---------------------------------------------------------------- args
label="" zip="" build="" project="" delete_idea=0 dart_version="${DART_PLUGIN_VERSION:-}" lsp4ij_version="${LSP4IJ_PLUGIN_VERSION:-}"
root="${TMPDIR:-/tmp}/agent-testing/manual" fresh=0 wait_exit=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --label) label="$2"; shift 2 ;;
    --plugin-zip) zip="$(realpath "$2")"; shift 2 ;;
    --ide-version) build="$2"; shift 2 ;;
    --project) project="$(realpath "$2")"; shift 2 ;;
    --delete-idea-dir) delete_idea=1; shift ;;
    --dart-version) dart_version="$2"; shift 2 ;;
    --lsp4ij-version) lsp4ij_version="$2"; shift 2 ;;
    --sandbox-root) root="$2"; shift 2 ;;
    --fresh) fresh=1; shift ;;
    --wait) wait_exit=1; shift ;;
    -h|--help) sed -n '2,36p' "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done
[[ -n "$label" && -n "$zip" && -n "$build" ]] || die "--label, --plugin-zip and --ide-version are required (see --help)"
[[ -f "$zip" ]] || die "no plugin zip at $zip"
[[ "$label" =~ ^[A-Za-z0-9._-]+$ ]] || die "--label must be a single word"

ide="$cache/builds/IU-$build/idea-IU-$build"
[[ -x "$ide/bin/idea.sh" ]] || die "IDE build $build is not in Starter's cache ($ide). Run any UI test once with -Pide.version=$build to download it."

newest_cached() {
  local v
  for v in $(ls -1 "$cache/plugins" 2>/dev/null | sort -V); do
    if [[ -f "$cache/plugins/$v/$1" ]]; then echo "$v"; fi
  done | tail -1
}
[[ -n "$dart_version" ]] || dart_version="$(newest_cached Dart.zip || true)"
[[ -n "$lsp4ij_version" ]] || lsp4ij_version="$(newest_cached com-redhat-devtools-lsp4ij.zip || true)"
dart_zip="$cache/plugins/$dart_version/Dart.zip"
lsp4ij_zip="$cache/plugins/$lsp4ij_version/com-redhat-devtools-lsp4ij.zip"
[[ -f "$dart_zip" ]] || die "Dart $dart_version is not in Starter's cache ($cache/plugins). Run a UI test with DART_PLUGIN_VERSION=$dart_version -Pide.version=$build once."
[[ -f "$lsp4ij_zip" ]] || die "LSP4IJ $lsp4ij_version is not in Starter's cache ($cache/plugins)."

# ---------------------------------------------------------------- guard: one IDE at a time
if pgrep -f "$ide/jbr/bin/java" >/dev/null 2>&1; then
  die "an IDE from $ide is already running (pgrep -af '$ide/jbr/bin/java'). Close it first; two instances would share state and the log."
fi

# ---------------------------------------------------------------- sandbox
sb="$root/IU-$build-$label"
[[ $fresh -eq 1 ]] && rm -rf "$sb"
mkdir -p "$sb/config" "$sb/system" "$sb/log" "$sb/reports"
rm -rf "$sb/plugins"; mkdir -p "$sb/plugins"
unzip -q "$zip" -d "$sb/plugins"
unzip -q "$dart_zip" -d "$sb/plugins"
unzip -q "$lsp4ij_zip" -d "$sb/plugins"
# A stale plugin from an earlier Marketplace install can't sneak in: the dir was just created from three zips.
installed_jar="$(ls "$sb"/plugins/flutter-intellij/lib/flutter-intellij*.jar | grep -v searchableOptions | head -1)"
zip_jar_entry="$(unzip -Z1 "$zip" | grep -E '^[^/]+/lib/flutter-intellij[^/]*\.jar$' | grep -v searchableOptions | head -1)"
zip_jar_sha="$(unzip -p "$zip" "$zip_jar_entry" | sha256sum | cut -d' ' -f1)"
installed_jar_sha="$(sha256sum "$installed_jar" | cut -d' ' -f1)"
[[ "$zip_jar_sha" == "$installed_jar_sha" ]] || die "installed jar sha256 differs from the zip's (unzip problem?)"
expected_version="$(python3 "$here/stamp_plugin_version.py" "$zip" --show)"
zip_sha="$(sha256sum "$zip" | cut -d' ' -f1)"

if [[ -n "$project" && $delete_idea -eq 1 ]]; then rm -rf "$project/.idea"; fi

# ---------------------------------------------------------------- vmoptions: the same switches Starter gives the
# IDE under test (trust, no consents/tips/surveys, new UI, X11), minus its RMI/JMX/test-script plumbing, plus OUR paths.
vm="$sb/idea64.vmoptions"
cat > "$vm" <<EOF
-Didea.config.path=$sb/config
-Didea.system.path=$sb/system
-Didea.plugins.path=$sb/plugins
-Didea.log.path=$sb/log
-XX:ErrorFile=$sb/log/java_error_%p.log
-XX:HeapDumpPath=$sb/log/heap-dump.hprof
-Dij.load.shell.env=false
-Dide.instant.shutdown=false
-Dfeature.usage.event.log.send.on.ide.close=false
-Dlinux.native.menu.force.disable=true
-Djdk.gtk.version=2
-Dvcs.log.index.enable=false
-Dide.slow.operations.assertion=false
-Didea.local.statistics.without.report=true
-Didea.updates.url=http://127.0.0.1
-Dide.enable.notification.trace.data.sharing=false
-Didea.trust.all.projects=true
-Djb.consents.confirmation.enabled=false
-Djb.privacy.policy.text=<!--999.999-->
-Djb.privacy.policy.ai.assistant.text=<!--999.999-->
-Dide.show.tips.on.startup.default.value=false
-Dplatform.feedback=false
-Dcsat.survey.enabled=false
-Deditor.ux.survey.enabled=false
-Dshared.indexes.download.auto.consent=true
-Dide.experimental.ui=true
-Dide.open.project.at.startup=false
-Dide.browser.jcef.enabled=true
-Dide.native.launcher=false
-Dawt.toolkit.name=XToolkit
-Dsun.java2d.uiScale.enabled=false
-Dsun.java2d.xrender=false
-Dmarketplace.eula.reviewed.and.accepted=true
-Dwriterside.eula.reviewed.and.accepted=true
-Dide.newUsersOnboarding=false
-Dfreeze.reporter.profiling=false
-Didea.fatal.error.notification=true
-Didea.is.integration.test=true
-Dagent.testing.label=$label
EOF
# A sentinel property the log echoes back, so we can tell our vmoptions were the ones read.

# ---------------------------------------------------------------- launch
: > "$sb/log/idea.log"
export IDEA_VM_OPTIONS="$vm"
unset IDEA_PROPERTIES
log "launching IDE $build, label '$label', plugin $(basename "$zip") (version $expected_version)"
setsid nohup "$ide/bin/idea.sh" > "$sb/launch.out" 2>&1 &
ide_pid=$!

# ---------------------------------------------------------------- verify from the IDE's own log
deadline=$((SECONDS + 120))
while (( SECONDS < deadline )); do
  if grep -q 'Loaded custom plugins' "$sb/log/idea.log" 2>/dev/null; then break; fi
  kill -0 "$ide_pid" 2>/dev/null || break
  sleep 2
done
idea_log="$sb/log/idea.log"
fail=()
grep -q "idea.plugins.path=$sb/plugins\$" "$idea_log" || fail+=("idea.plugins.path in the log is not $sb/plugins")
grep -q "idea.config.path=$sb/config\$" "$idea_log" || fail+=("idea.config.path in the log is not $sb/config")
grep -q -- "-Dagent.testing.label=$label" "$idea_log" || fail+=("sentinel -Dagent.testing.label=$label missing from the JVM options the IDE reports: our vmoptions were not read")
loaded="$(grep -o 'Loaded custom plugins: .*' "$idea_log" | tail -1 || true)"
[[ -n "$loaded" ]] || fail+=("no 'Loaded custom plugins' line within 120 s (IDE died? see $sb/launch.out)")
[[ "$loaded" == *"Flutter ($expected_version)"* ]] || fail+=("IDE loaded '$loaded', expected 'Flutter ($expected_version)'")
[[ "$loaded" == *"Dart ($dart_version)"* ]] || fail+=("Dart ($dart_version) not in '$loaded' (incompatible with $build? on 25x use 506.1.0)")
if grep -A30 'Problems found loading plugins' "$idea_log" | grep -q -i 'flutter\|Dart\|lsp4ij'; then
  fail+=("plugin loading problems for our plugins in idea.log: $(grep -A30 'Problems found loading plugins' "$idea_log" | grep -i -m2 'flutter\|Dart\|lsp4ij' | cut -c1-200 | tr '\n' ' ')")
fi

cat > "$sb/manifest.json" <<EOF
{
  "label": "$label",
  "ide": "IU-$build",
  "ideHome": "$ide",
  "pluginZip": "$zip",
  "pluginZipSha256": "$zip_sha",
  "pluginJarSha256": "$installed_jar_sha",
  "pluginVersion": "$expected_version",
  "dartVersion": "$dart_version",
  "lsp4ijVersion": "$lsp4ij_version",
  "project": "$project",
  "ideaDirDeleted": $([[ $delete_idea -eq 1 ]] && echo true || echo false),
  "sandbox": "$sb",
  "ideaLog": "$idea_log",
  "launchedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "pid": $ide_pid,
  "verified": $([[ ${#fail[@]} -eq 0 ]] && echo true || echo false),
  "failures": [$([[ ${#fail[@]} -gt 0 ]] && printf '"%s",' "${fail[@]}" | sed 's/,$//')]
}
EOF

if [[ ${#fail[@]} -gt 0 ]]; then
  log "VERIFICATION FAILED; killing the IDE (pid $ide_pid)"
  for f in "${fail[@]}"; do log "  - $f"; done
  kill "$ide_pid" 2>/dev/null || true
  pkill -f "$ide/jbr/bin/java" 2>/dev/null || true
  exit 1
fi

cat >&2 <<EOF

============================== VERIFIED ==============================
 Label:    $label
 IDE:      IU-$build
 Flutter:  $expected_version   (jar sha256 $installed_jar_sha)
           from $zip
 Dart:     $dart_version   LSP4IJ: $lsp4ij_version
 Sandbox:  $sb
 Log:      $idea_log
 Project:  ${project:-(none given)}$([[ $delete_idea -eq 1 ]] && echo '   (.idea/ removed)')
 Manifest: $sb/manifest.json
 In the IDE, Settings > Plugins > Installed shows Flutter $expected_version.
 Afterwards: $0 report $sb [--grep '<regex>']
======================================================================
EOF

if [[ $wait_exit -eq 1 ]]; then
  log "waiting for the IDE to exit (pid $ide_pid)"
  while kill -0 "$ide_pid" 2>/dev/null; do sleep 3; done
  report "$sb"
fi
