#!/usr/bin/env bash
# Installs a headless Android SDK (cmdline-tools, platform, build-tools) on Linux,
# plus a Gradle init script that mirrors Maven Central (see gradle/init/).
# Idempotent: safe to run on every session start. Requires access to dl.google.com.
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708" # commandlinetools-linux-<rev>_latest.zip
PLATFORM="platforms;android-37.0"
BUILD_TOOLS="build-tools;36.0.0"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Route Maven Central through Google's mirror to avoid HTTP 429s from shared cloud egress.
# Done first so Gradle benefits even if the SDK download below fails.
mkdir -p "$HOME/.gradle/init.d"
cp "$REPO_ROOT/gradle/init/maven-central-mirror.init.gradle.kts" "$HOME/.gradle/init.d/"

if [[ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  echo "Installing Android cmdline-tools into $SDK_ROOT"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --install "platform-tools" "$PLATFORM" "$BUILD_TOOLS" >/dev/null

echo "sdk.dir=$SDK_ROOT" > "$REPO_ROOT/local.properties"

# Persist env vars for the rest of a Claude Code session, if running under a hook.
if [[ -n "${CLAUDE_ENV_FILE:-}" ]]; then
  {
    echo "export ANDROID_HOME=\"$SDK_ROOT\""
    echo "export ANDROID_SDK_ROOT=\"$SDK_ROOT\""
    echo "export PATH=\"\$PATH:$SDK_ROOT/platform-tools:$SDK_ROOT/cmdline-tools/latest/bin\""
  } >> "$CLAUDE_ENV_FILE"
fi

echo "Android SDK ready at $SDK_ROOT"
