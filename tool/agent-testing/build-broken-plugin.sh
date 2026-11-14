#!/usr/bin/env bash
# Build a deliberately broken plugin zip in a detached git worktree, so the
# working tree is never modified, and print the zip path on stdout.
#
# Usage:
#   build-broken-plugin.sh --repo flutter|dart [--repo-path <dir>] \
#       ( --rev <commit> | --revert <sha> [--revert <sha>...] | --patch <file> [--patch <file>...] ) \
#       [--base <commit>] [--out <dir>] [--keep]
#
#   --repo flutter   this repository; output goes to -Pflutter.plugin.zip
#   --repo dart      the Dart plugin repository, a checkout of
#                    dart-intellij-third-party (set DART_PLUGIN_REPO or
#                    --repo-path); output goes to DART_PLUGIN_ZIP
#   --rev            build exactly this commit (change-to-spec: merge-base)
#   --revert         start from --base (default HEAD) and reverse-apply these
#                    commits (bug-to-spec: closed issue with a known fix)
#   --patch          start from --base (default HEAD) and apply these patches
#                    (workflow / refactor / hollow-seam faults)
#   --out            where to copy the zip (default $TMPDIR/agent-testing)
#   --keep           leave the worktree in place for inspection
#
# Everything is logged to stderr; only the final zip path goes to stdout, so
# callers can do: ZIP=$(build-broken-plugin.sh ...)
#
# Status: first version, written in MVP step 0; validated in step 1.

set -euo pipefail

repo=""
repo_path=""
rev=""
base="HEAD"
reverts=()
patches=()
out="${TMPDIR:-/tmp}/agent-testing"
keep=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) repo="$2"; shift 2 ;;
    --repo-path) repo_path="$2"; shift 2 ;;
    --rev) rev="$2"; shift 2 ;;
    --base) base="$2"; shift 2 ;;
    --revert) reverts+=("$2"); shift 2 ;;
    --patch) patches+=("$(realpath "$2")"); shift 2 ;;
    --out) out="$2"; shift 2 ;;
    --keep) keep=1; shift ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$repo" ]] || { echo "--repo flutter|dart is required" >&2; exit 2; }
if [[ -z "$rev" && ${#reverts[@]} -eq 0 && ${#patches[@]} -eq 0 ]]; then
  echo "one of --rev, --revert, --patch is required" >&2; exit 2
fi

case "$repo" in
  flutter)
    repo_path="${repo_path:-$(git rev-parse --show-toplevel)}"
    build_cmd=(./gradlew --no-daemon buildPlugin -q)
    zip_glob="build/distributions/*.zip"
    ;;
  dart)
    repo_path="${repo_path:-${DART_PLUGIN_REPO:-}}"
    [[ -n "$repo_path" ]] || { echo "set DART_PLUGIN_REPO or pass --repo-path for --repo dart" >&2; exit 2; }
    # TODO(step 3): confirm dart-intellij-third-party's build task and distribution path.
    build_cmd=(./gradlew --no-daemon buildPlugin -q)
    zip_glob="build/distributions/*.zip"
    ;;
  *) echo "--repo must be flutter or dart" >&2; exit 2 ;;
esac

repo_path="$(realpath "$repo_path")"
mkdir -p "$out"
stamp="$(date +%Y%m%d-%H%M%S)"
wt="$(mktemp -d "${TMPDIR:-/tmp}/agent-testing-wt.XXXXXX")"
log() { echo "[build-broken-plugin] $*" >&2; }

cleanup() {
  if [[ $keep -eq 1 ]]; then
    log "keeping worktree at $wt"
    return
  fi
  git -C "$repo_path" worktree remove --force "$wt" >/dev/null 2>&1 || rm -rf "$wt"
  git -C "$repo_path" worktree prune >/dev/null 2>&1 || true
}
trap cleanup EXIT

start_rev="${rev:-$base}"
start_sha="$(git -C "$repo_path" rev-parse --verify "$start_rev^{commit}")"
log "repo=$repo path=$repo_path start=$start_rev ($start_sha)"
git -C "$repo_path" worktree add --detach "$wt" "$start_sha" >&2

describe=("start=$start_rev")
for sha in "${reverts[@]:-}"; do
  [[ -n "$sha" ]] || continue
  log "reverting $sha"
  if ! git -C "$wt" revert --no-commit --no-edit "$sha" >&2; then
    # A conflict only in documentation (CHANGELOG.md and the like) doesn't affect the plugin; keep HEAD's copy and
    # carry on. Any conflict in code is a real one: stop and leave the worktree for inspection.
    conflicted="$(git -C "$wt" diff --name-only --diff-filter=U)"
    if [[ -n "$conflicted" ]] && ! grep -v -E '\.md$|^CHANGELOG' <<<"$conflicted" >/dev/null; then
      log "revert of $sha conflicts only in docs ($(tr '\n' ' ' <<<"$conflicted")); keeping HEAD's copy"
      while IFS= read -r f; do git -C "$wt" checkout HEAD -- "$f" && git -C "$wt" add -- "$f"; done <<<"$conflicted"
    else
      log "revert of $sha conflicts in code ($(tr '\n' ' ' <<<"$conflicted")); see $wt (use --keep to inspect)"; keep=1; exit 3
    fi
  fi
  describe+=("revert=$sha")
done
for p in "${patches[@]:-}"; do
  [[ -n "$p" ]] || continue
  log "applying $p"
  git -C "$wt" apply --index "$p" >&2 || { log "patch $p does not apply"; keep=1; exit 3; }
  describe+=("patch=$(basename "$p")")
done

# Record exactly what differs from the start commit, for the evidence report.
diff_file="$out/broken-build-$repo-$stamp.diff"
{
  echo "# ${describe[*]}"
  git -C "$wt" diff HEAD
} > "$diff_file"
log "diff recorded at $diff_file ($(grep -c '^[-+]' "$diff_file" || true) changed lines)"

# Share the Gradle user home and the main checkout's local.properties so the
# worktree build doesn't re-download everything.
if [[ -f "$repo_path/local.properties" && ! -f "$wt/local.properties" ]]; then
  cp "$repo_path/local.properties" "$wt/local.properties"
fi

log "building: ${build_cmd[*]} (in $wt)"
( cd "$wt" && "${build_cmd[@]}" ) >&2

zip_src="$(ls -1 "$wt"/$zip_glob 2>/dev/null | head -n1 || true)"
[[ -n "$zip_src" ]] || { log "no zip produced under $zip_glob"; keep=1; exit 4; }
zip_dst="$out/$(basename "${zip_src%.zip}")-broken-$stamp.zip"
cp "$zip_src" "$zip_dst"

# Stamp the plugin version with "-broken-<stamp>" so this build can never be mistaken for the real one: the suffix
# shows in idea.log ("Loaded custom plugins: ... Flutter (SNAPSHOT-broken-...)"), in Settings > Plugins, and in
# run-result.json, where parse_run_result.py requires it for red and rejects it for green.
if [[ "$repo" == flutter ]]; then
  stamped="$(python3 "$(dirname "$(realpath "$0")")/stamp_plugin_version.py" "$zip_dst" "broken-$stamp")"
  log "plugin version stamped: $stamped"
fi
log "zip: $zip_dst"
echo "$zip_dst"
