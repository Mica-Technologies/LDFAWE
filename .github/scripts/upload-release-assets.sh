#!/usr/bin/env bash
#
# Uploads files to an existing GitHub release one at a time, then reads the release back and fails
# unless every one of them is there.
#
# Canonical copy from dev-configurations (minecraft-mod-workflows/template/), folded back from
# MCMCP, where it first replaced the action's uploads.
#
# Why not softprops/action-gh-release's `files:`: its uploader runs in parallel and loses assets.
# City Super Mod lost 2-4 of its 11 files on each of three runs on 2026-09-17 (on v2 and v3
# alike), with `Error creating asset temp dir` in the log and a release left short. A release
# missing a jar is worse than no release: launcher manifests point straight at these file names.
#
# Each file is uploaded with `<file>#<basename>` so its label is the original file name. GitHub
# rewrites some characters in asset names (a space becomes `.`, so `MCMCP Orchestrator_...exe` is
# stored as `MCMCP.Orchestrator_...exe`) and shows the label instead, which is what the action set
# too. For the same reason the check below matches a file against the asset's name OR its label.
#
# The check is by name, not by count: it says which file is missing, and it stays correct when
# several jobs upload to the same release at the same time (MCMCP's orchestrator matrix does), where
# a count would depend on how far the others had got.
#
# Usage:  upload-release-assets.sh <tag> <file>...
# Needs:  GH_TOKEN, GITHUB_REPOSITORY (both set in a workflow step)

set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <tag> <file>..." >&2
  exit 2
fi

tag="$1"
shift
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"

for file in "$@"; do
  if [ ! -f "$file" ]; then
    echo "::error::$file was listed for upload but is not there"
    exit 1
  fi
  name="$(basename "$file")"
  for attempt in 1 2 3; do
    echo "==> uploading $name to $tag (attempt $attempt)"
    if gh release upload "$tag" "$file#$name" --repo "$repo" --clobber; then
      break
    fi
    if [ "$attempt" -eq 3 ]; then
      echo "::error::giving up on $name after 3 attempts"
      exit 1
    fi
    sleep $(( attempt * 5 ))
  done
done

# Only assets whose state is `uploaded` count. A failed or in-flight upload is listed with a
# different state, so listing every asset regardless would pass a broken release.
present="$(gh release view "$tag" --repo "$repo" --json assets \
  --jq '.assets[] | select(.state == "uploaded") | .name, .label')"

missing=0
for file in "$@"; do
  name="$(basename "$file")"
  if grep -qxF -- "$name" <<<"$present"; then
    echo "    ok  $name"
  else
    echo "::error::$name is not on $tag as an uploaded asset"
    missing=1
  fi
done

if [ "$missing" -ne 0 ]; then
  echo "==> assets now on $tag:"
  gh release view "$tag" --repo "$repo" --json assets \
    --jq '.assets[] | "    \(.name) [\(.label)] \(.size) \(.state)"'
  exit 1
fi
echo "==> all $# file(s) are on $tag"
