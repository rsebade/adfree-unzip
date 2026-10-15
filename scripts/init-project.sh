#!/usr/bin/env bash
# One-time setup for a repo created from this template.
# Renames the package/applicationId and app name, then removes template-only files
# (this script, the template CI workflow, and <!-- template-only --> doc sections).
#
# Usage: ./scripts/init-project.sh com.yourname.yourapp "Your App"
set -euo pipefail
cd "$(dirname "$0")/.."

OLD_ID="com.example.app"
NEW_ID="${1:-}"
APP_NAME="${2:-}"

if [[ ! "$NEW_ID" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "error: first argument must be a lowercase package name like com.yourname.yourapp" >&2
  exit 1
fi
if [[ ! "$APP_NAME" =~ ^[A-Za-z0-9][A-Za-z0-9\ ._-]*$ ]]; then
  echo "error: second argument must be an app name (letters, digits, spaces, . _ -)" >&2
  exit 1
fi
if [[ "$NEW_ID" == "$OLD_ID" ]]; then
  echo "error: pick your own package name instead of $OLD_ID" >&2
  exit 1
fi

OLD_PATH="${OLD_ID//./\/}"
NEW_PATH="${NEW_ID//./\/}"
# Gradle project name: app name without spaces/punctuation, e.g. "My App" -> "MyApp".
PROJECT_NAME="$(echo "$APP_NAME" | tr -cd 'A-Za-z0-9')"

for set in main test androidTest; do
  src="app/src/$set/java/$OLD_PATH"
  [[ -d "$src" ]] || continue
  dest="app/src/$set/java/$NEW_PATH"
  # Move via a temp dir so a new ID nested in the old one (com.example.app.x) works.
  tmp="app/src/$set/java/.init-project-tmp"
  mv "$src" "$tmp"
  mkdir -p "$(dirname "$dest")"
  mv "$tmp" "$dest"
  # Remove now-empty old parent dirs (e.g. com/example).
  find "app/src/$set/java" -type d -empty -delete
done

grep -rlF --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle \
  -e "$OLD_ID" -e "$OLD_PATH" app agents settings.gradle.kts |
  while read -r f; do
    sed -i -e "s|$OLD_PATH|$NEW_PATH|g" -e "s|${OLD_ID//./\\.}|$NEW_ID|g" "$f"
  done

sed -i "s|<string name=\"app_name\">.*</string>|<string name=\"app_name\">$APP_NAME</string>|" \
  app/src/main/res/values/strings.xml
sed -i "s|^rootProject.name = .*|rootProject.name = \"$PROJECT_NAME\"|" settings.gradle.kts

# Drop template-only doc sections, CI workflow and this script.
for f in README.md agents/CLAUDE.md; do
  sed -i '/<!-- template-only:start -->/,/<!-- template-only:end -->/d' "$f"
done
sed -i "1s|.*|# $APP_NAME|" README.md
rm -f .github/workflows/template.yml scripts/init-project.sh

echo "init-project: $APP_NAME ($NEW_ID) is ready. Run ./scripts/verify.sh, then commit."
