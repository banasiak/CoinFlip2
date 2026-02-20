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
- `SettingsFragment` — preferences (XML `PreferenceFragmentCompat` with `root_settings.xml`)
- `AboutFragment` / `DiagnosticsFragment` — `BottomSheetDialogFragment` dialogs

**UI is migrating from XML to Jetpack Compose.** Fragments create a `ComposeView` in `onCreateView` and render a `*Screen` composable (e.g., `MainFragment` → `MainScreen`). Each Compose screen follows a two-layer pattern: `*Screen(viewModel)` collects state, `*View(state, postAction)` is the pure composable (used by `@PreviewLightDark`). The Compose theme is in `ui/theme/` (`AppTheme`). The coin animation still uses `AndroidView` wrapping an `ImageView` with `DurationAnimationDrawable`.

**State management pattern (MVI-style):**
- Each feature has a `*State` (Parcelable data class), `*Action` (sealed class for user intents), and `*Effect` (sealed class for one-shot side effects like navigation or toasts).
- ViewModels expose `stateFlow: StateFlow<State>` and `effectFlow: SharedFlow<Effect>`. UI calls `viewModel.postAction(action)`.
- State is saved/restored via `SavedStateHandle` using `save()`/`restore()` extensions in `extensions/SavedState.kt` (stores under the key `"state"`).
- Effects are consumed by Fragments (navigation, URLs via Chrome Custom Tabs, toasts, rate dialog).

**Dependency injection:** Hilt (`@HiltAndroidApp` on `App`, `@AndroidEntryPoint` on Activity/Fragments, `@HiltViewModel` on ViewModels). `AppModule` provides system services (`SensorManager`, `Vibrator`, `SoundPool`, `SharedPreferences`) and platform types (`Clock`, `Random`, `SecureRandom`, `BuildInfo`) as a `SingletonComponent`. `ColorHelper` is `@ActivityScoped`.

**Key domain classes:**
- `Coin` — core flip logic; tracks `currentValue` to determine animation permutation (heads→heads, heads→tails, etc.)
- `RNG` — wraps `kotlin.random.Random` / `SecureRandom`, listens for preference changes to hot-swap
- `SettingsManager` — typed accessors over `SharedPreferences`; all preference keys defined in the `Settings` enum
- `AnimationHelper` — generates frame-by-frame `DurationAnimationDrawable` for each of the 4 flip permutations; coin images are loaded by resource name prefix (e.g., `"gw"` → `gw_heads` / `gw_tails` drawables)
- `SoundHelper`, `VibrationHelper` — play sounds / haptics only when the corresponding setting is enabled

**Testing:** JUnit 5 with MockK for mocking, Kluent for assertions, Turbine for Flow testing. ViewModel tests use `@ExtendWith(MainDispatcherRule::class)` to swap `Dispatchers.Main` with `UnconfinedTestDispatcher`. Tests are in `coinflip/src/test/`.

## Code Style

Kotlin formatting is enforced by **ktlint**. Run `./gradlew :coinflip:format` before committing. The project uses Kotlin DSL for Gradle build files and a version catalog (`gradle/libs.versions.toml`). Package is `com.banasiak.coinflip`; code targets JVM 17.
