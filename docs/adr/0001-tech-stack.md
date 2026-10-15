# ADR 0001: Initial tech stack

- **Status:** Accepted
- **Date:** 2026-10-15

## Context
The app is developed primarily by AI agents, steered from a phone. We need a stack
that is mainstream (so agents produce idiomatic code), verifiable headlessly
(so agents and CI can prove changes work), and reviewable in small diffs.

## Decision
Kotlin, Jetpack Compose + Material 3, single-activity MVVM/UDF with StateFlow,
Coroutines, Gradle Kotlin DSL + version catalog, ktlint + Android Lint
(warnings as errors), JUnit4 + Compose UI tests, GitHub Actions CI producing an
installable debug APK on every PR.

## Consequences
- No XML layouts or Fragments; all UI is Compose.
- DI is manual until complexity justifies Hilt (new ADR required).
- Toolchain versions are pinned in `gradle/libs.versions.toml` and bumped by Dependabot.
