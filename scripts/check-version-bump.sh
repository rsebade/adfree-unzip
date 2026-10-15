#!/usr/bin/env bash
# Fails when this branch changes the app but doesn't raise versionCode and versionName above
# the base branch's. Every installable APK must have a new name (Unzip-<versionName>) and a
# higher versionCode, or Android won't install it as an update.
# Usage: scripts/check-version-bump.sh [base-ref]   (default: origin/main)
set -euo pipefail
cd "$(dirname "$0")/.."

base="${1:-origin/main}"
gradle_file="app/build.gradle.kts"

# Changes that end up in the APK. Tests and docs don't.
changed=$(git diff --name-only "$base"...HEAD -- app gradle/libs.versions.toml \
    ':!app/src/test' ':!app/src/androidTest')
if [ -z "$changed" ]; then
    echo "version: no app changes against $base; no bump needed"
    exit 0
fi

version_code() { sed -nE 's/^ *versionCode = ([0-9]+).*/\1/p' | head -n1; }
version_name() { sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' | head -n1; }

base_code=$(git show "$base:$gradle_file" | version_code)
base_name=$(git show "$base:$gradle_file" | version_name)
head_code=$(version_code < "$gradle_file")
head_name=$(version_name < "$gradle_file")

status=0
if [ "$head_code" -le "$base_code" ]; then
    echo "version: versionCode $head_code must be greater than $base_code on $base" >&2
    status=1
fi
newest=$(printf '%s\n%s\n' "$base_name" "$head_name" | sort -V | tail -n1)
if [ "$head_name" = "$base_name" ] || [ "$newest" != "$head_name" ]; then
    echo "version: versionName $head_name must be greater than $base_name on $base" >&2
    status=1
fi
if [ "$status" -ne 0 ]; then
    echo "version: bump both in $gradle_file (see agents/CLAUDE.md §6). App files changed:" >&2
    echo "$changed" | sed 's/^/  /' >&2
    exit 1
fi
echo "version: $base_name ($base_code) -> $head_name ($head_code) OK"
