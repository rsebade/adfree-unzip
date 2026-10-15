#!/usr/bin/env bash
# The single local quality gate. Agents run this before every push; CI runs the same tasks.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew --no-daemon ktlintCheck lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug "$@"

echo "verify: OK — debug APK at app/build/outputs/apk/debug/"
