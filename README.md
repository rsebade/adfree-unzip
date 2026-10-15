# claude-android-scaffold

An Android app set up for **agent-driven development** (e.g. Claude Code
on the web/mobile). You describe work from your phone; an agent implements it, proves
it with `./scripts/verify.sh` + CI, and opens a PR with an installable APK.

<!-- template-only:start -->
> **This repository is a template.** Don't build an app here. Create a new repo from it.

## Start a new app

1. On this repo's GitHub page, tap **Use this template → Create a new repository**.
2. In Claude Code, set up a cloud environment for the new repo whose network access allows
   the hosts listed under [Cloud environment requirements](#cloud-environment-requirements).
3. Start a session on the new repo and ask:
   *"Initialize this project as `com.yourname.yourapp` called "Your App", then open a PR."*
   The agent runs `./scripts/init-project.sh`, which renames the package and app and removes
   the template-only files and sections (like this one). You can also run it yourself.
4. Merge that PR, then start describing features.

## Changing the template

Only make changes here that every new app should start with: toolchain, CI, agent rules,
scripts, or the `ui/home/` reference feature. The `Template` workflow runs
`init-project.sh` on every push and builds the result, so the setup step can't silently break.
<!-- template-only:end -->

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
