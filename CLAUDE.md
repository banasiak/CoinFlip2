# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CoinFlip2 is a Modern Android Development (MAD) coin-flipping app published on the Google Play Store. It is a single-module Android app (`coinflip`) written in Kotlin.

## Build & Development Commands

```bash
# Build debug APK
./gradlew :coinflip:assembleDebug

# Run all unit tests (uses JUnit 5 / JUnit Platform)
./gradlew :coinflip:test

# Run a single test class
./gradlew :coinflip:test --tests "com.banasiak.coinflip.common.CoinTests"

# Lint Kotlin code style (ktlint, runs as part of `check`)
./gradlew :coinflip:ktlintCheck

# Auto-format Kotlin code
./gradlew :coinflip:format

# Full check (build + tests + ktlint)
./gradlew :coinflip:check
```

## Architecture

**Single Activity, Fragment-based navigation:** `AppActivity` hosts a Navigation Component graph (`nav_graph.xml`). Fragments are the top-level destinations:
- `MainFragment` — coin flip screen (start destination)
- `SettingsFragment` — preferences (XML PreferenceScreen)
- `AboutFragment` / `DiagnosticsFragment` — shown as dialogs

**UI is migrating from XML to Jetpack Compose.** Fragments create a `ComposeView` and render a `*Screen` composable (e.g., `MainFragment` → `MainScreen`). The Compose theme is in `ui/theme/`.

**State management pattern (MVI-style):**
- Each feature has a `*State` (Parcelable data class), `*Action` (sealed class for user intents), and `*Effect` (sealed class for one-shot side effects).
- ViewModels expose `stateFlow: StateFlow<State>` and `effectFlow: SharedFlow<Effect>`. Fragments/Composables call `viewModel.postAction(action)`.
- State is saved/restored via `SavedStateHandle` using extension functions in `extensions/SavedState.kt`.

**Dependency injection:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`). `AppModule` provides system services and platform dependencies as a `SingletonComponent`.

**Key domain classes:**
- `Coin` — core flip logic using `RNG` (wraps `kotlin.random.Random` / `SecureRandom`)
- `SettingsManager` — reads/writes `SharedPreferences`
- `AnimationHelper`, `SoundHelper`, `VibrationHelper` — media/haptic feedback utilities

**Testing:** JUnit 5 with MockK for mocking, Kluent for assertions, Turbine for Flow testing. Test coroutine dispatch uses a `MainDispatcherRule` JUnit extension. Tests are in `coinflip/src/test/`.

## Code Style

Kotlin formatting is enforced by **ktlint**. Run `./gradlew :coinflip:format` before committing. The project uses Kotlin DSL for Gradle build files and a version catalog (`gradle/libs.versions.toml`).
