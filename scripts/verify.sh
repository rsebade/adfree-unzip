#!/usr/bin/env bash
# The single local quality gate. Agents run this before every push; CI runs the same tasks.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew --no-daemon ktlintCheck lintDebug testDebugUnitTest compileDebugAndroidTestKotlin distDebugApk "$@"

echo "verify: OK — installable APK at app/build/dist/"
