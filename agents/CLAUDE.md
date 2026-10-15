# Agent Operating Rules — Android App

These rules govern every AI agent (Claude Code or otherwise) working in this repo.
Development here is **agent-driven and often initiated from a phone**: the human
reviews PRs and installs APKs; agents write, verify, and ship the code. Optimize
for changes a human can review on a small screen and trust without running locally.

## 1. Golden rules

1. **Never claim it works without proof.** "Done" means `./scripts/verify.sh` passed
   locally *or* CI is green on your pushed commit. If you couldn't run it (e.g. no
   Android SDK / network), say so explicitly — never fake or assume results.
2. **Small, reviewable PRs.** One concern per PR. Aim for < 400 changed lines
   excluding generated files. Split larger work into a stack of PRs or issues.
3. **Follow existing patterns before inventing new ones.** Read neighboring code first.
4. **No new dependency without justification.** Add it to `gradle/libs.versions.toml`
   only, and explain in the PR why the platform/existing deps don't suffice.
5. **Never weaken the safety net.** Do not disable/skip tests, lower lint severity,
   add `@Suppress`/baseline entries, or loosen `allWarningsAsErrors` to get green.
   Fix the cause. If truly unavoidable, call it out in the PR description.
6. **Secrets never enter the repo.** No API keys, keystores, `local.properties`,
   or `google-services.json` with real values. Use GitHub Actions secrets.
7. **Ask when intent is ambiguous**, especially for UX decisions, data
   deletion/migrations, permissions, and anything user-facing that is hard to undo.

## 2. Tech stack (do not change without an ADR in `docs/adr/`)

| Concern | Choice |
|---|---|
| Language | Kotlin only (no new Java files) |
| UI | Jetpack Compose + Material 3 (no XML layouts/Fragments) |
| Architecture | Single-activity, MVVM / Unidirectional Data Flow |
| State | `ViewModel` exposing `StateFlow<UiState>`; collected with `collectAsStateWithLifecycle()` |
| Async | Kotlin Coroutines + Flow (no RxJava, no `GlobalScope`) |
| DI | Constructor injection, manual wiring. Introduce Hilt via ADR when > ~5 injected types |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| Style | ktlint (official Kotlin style), Android Lint with warnings-as-errors |
| Tests | JUnit4 + kotlinx-coroutines-test (unit); Compose UI test (instrumented) |
| Min / target SDK | 26 / 35 |

## 3. Project layout

```
app/src/main/java/com/rsebade/adfreeunzip/
  MainActivity.kt          # only entry point; hosts Compose
  ui/theme/                # AppTheme, colors, typography
  ui/<feature>/            # <Feature>Route, <Feature>Screen, <Feature>ViewModel, <Feature>UiState
  data/                    # repositories, data sources (add when needed)
  domain/                  # pure-Kotlin use cases/models (add when needed)
app/src/test/              # JVM unit tests — mirror main package structure
app/src/androidTest/       # instrumented / Compose UI tests
```

Feature pattern (see `ui/home/` as the reference implementation):
- `XRoute` — stateful; obtains the ViewModel and collects state.
- `XScreen` — **stateless**; takes `uiState` + event lambdas + `modifier`. Has a `@Preview`.
- `XViewModel` — holds `MutableStateFlow<XUiState>`, exposes read-only `StateFlow`; public
  functions are named for events (`onRefresh`, `onItemClicked`).
- `XUiState` — immutable `data class`.

## 4. Coding rules

- All user-visible strings go in `res/values/strings.xml`. No hardcoded UI text.
- Composables: `modifier: Modifier = Modifier` is the first optional parameter.
  Add `testTag`s (as constants) for anything a UI test interacts with.
- No `!!`, no `lateinit` in ViewModels, no blocking calls on the main thread.
- Prefer `sealed interface` for finite states/results over nullable flags.
- Keep Android framework types out of `domain/` so it stays JVM-unit-testable.
- Public functions get KDoc only when the name doesn't say it all. No commented-out code.

## 5. Testing rules

- Every ViewModel/business-logic change ships with unit tests in `app/src/test`.
- Bug fixes start with a failing test that reproduces the bug.
- UI behavior changes get or update a Compose UI test in `app/src/androidTest`
  (these run on an emulator in CI; you may not be able to run them locally — say so).
- Tests use descriptive backtick names: `` `onIncrement increases count by one` ``.

## 6. Workflow for every task

1. Restate the goal and acceptance criteria in 1–3 bullets (in the PR description).
2. Explore relevant code; make a short plan for non-trivial tasks.
3. Implement in small steps; run `./scripts/verify.sh` (or the narrowest Gradle task) often.
4. Run `./gradlew ktlintFormat` before committing.
5. Commit with Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`,
   `build:`, `ci:`, `chore:`. Subject ≤ 72 chars, imperative mood.
6. Push to your working branch; open a PR using `.github/pull_request_template.md`.
7. Watch CI. If red, root-cause and fix — "flaky" is not a root cause.

## 7. Commands

| Purpose | Command |
|---|---|
| Full local gate (run before every push) | `./scripts/verify.sh` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Lint (Android) | `./gradlew lintDebug` |
| Style check / auto-fix | `./gradlew ktlintCheck` / `./gradlew ktlintFormat` |
| Build debug APK | `./gradlew assembleDebug` → `app/build/outputs/apk/debug/` |
| Instrumented tests (needs device/emulator) | `./gradlew connectedDebugAndroidTest` |
| One-time SDK setup (cloud/Linux) | `./scripts/setup-android-sdk.sh` |

## 8. Environment notes for cloud / phone-driven sessions

- The Android SDK is installed by the SessionStart hook (`.claude/settings.json` →
  `scripts/setup-android-sdk.sh`). It needs network access to `dl.google.com` and
  `maven.google.com`. If those hosts are blocked, Gradle cannot resolve AGP/AndroidX:
  report it, rely on CI, and do not fabricate build output.
- Maven Central rate-limits cloud sessions (HTTP 429). The same hook installs
  `gradle/init/maven-central-mirror.init.gradle.kts` into `~/.gradle/init.d/`, which makes
  Gradle use Google's Maven Central mirror first (needs `maven-central.storage-download.googleapis.com`).
  If you still see 429s, check that file is installed before blaming the build.
- The human tests on a real phone by downloading the `Unzip-debug` APK artifact from
  the CI run on the PR. Mention in the PR what they should tap through to verify.
- The `Compose UI tests (emulator)` CI job runs on every push after `verify` passes. Check
  its result on your commit before claiming UI tests passed; you can't run them locally.
- Never commit `local.properties`, `build/`, `.gradle/`, or IDE files.

## 9. PR description must include

- **What / Why** — 1–3 sentences.
- **How verified** — exact commands run and their result; what you could NOT run.
- **Manual test steps** — what to tap in the APK, expected result.
- **Screenshots** — for UI changes when possible (Compose previews or emulator).
- **Risks / follow-ups.**
