# Unzip

An Android app set up for **agent-driven development** (e.g. Claude Code
on the web/mobile). You describe work from your phone; an agent implements it, proves
it with `./scripts/verify.sh` + CI, and opens a PR with an installable APK.


- **Agent rules:** [`agents/CLAUDE.md`](agents/CLAUDE.md) (auto-loaded via root `CLAUDE.md`)
- **Stack decision:** [`docs/adr/0001-tech-stack.md`](docs/adr/0001-tech-stack.md)

## Toolchain

| Layer | Tool | Version |
|---|---|---|
| Language | Kotlin | 2.4.21 |
| UI | Jetpack Compose (BOM) + Material 3 | 2026.09.00 |
| Build | Gradle (wrapper) / Android Gradle Plugin | 9.8.1 / 9.4.1 |
| JDK | Temurin / OpenJDK | 17 (21 also works to run Gradle) |
| Android SDK | compileSdk / targetSdk / minSdk | 37 / 35 / 26 |
| Lint & style | Android Lint, ktlint (via `org.jlleitschuh.gradle.ktlint`) | — |
| Tests | JUnit4, kotlinx-coroutines-test, Compose UI Test, Espresso | — |
| CI | GitHub Actions (+ `android-emulator-runner`) | — |
| Updates | Dependabot (Gradle + Actions) | — |

## Phone workflow

1. Start a Claude Code session on this repo from the Claude app and describe the change.
2. The agent follows `agents/CLAUDE.md`, runs `./scripts/verify.sh`, and pushes a PR.
3. Open the PR's CI run → download the **app-debug** artifact → install the APK
   (allow "install unknown apps" for your browser/files app once).
4. Review, comment, merge.

## Commands

```bash
./scripts/verify.sh            # ktlint + lint + unit tests + UI-test compile + debug APK
./gradlew ktlintFormat         # auto-fix style
./gradlew connectedDebugAndroidTest   # UI tests (needs emulator/device)
```

## Cloud environment requirements

The Claude Code cloud environment must allow `dl.google.com`, `maven.google.com`
(Android SDK + Google Maven) and `maven-central.storage-download.googleapis.com`
(Maven Central mirror; Maven Central itself returns HTTP 429 to cloud sessions). A SessionStart hook runs `scripts/setup-android-sdk.sh`
automatically in cloud sessions.
