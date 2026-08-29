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

# Run a single test class (the `test` lifecycle task does not accept --tests)
./gradlew :coinflip:testDebugUnitTest --tests "com.banasiak.coinflip.common.CoinTests"

# Lint Kotlin code style (ktlint, runs as part of `check`)
./gradlew :coinflip:ktlintCheck

# Auto-format Kotlin code
./gradlew :coinflip:format

# Full check (build + tests + ktlint)
./gradlew :coinflip:check

# Coverage report -> coinflip/build/reports/kover/htmlDebug/index.html
./gradlew :coinflip:koverHtmlReportDebug
```

## Architecture

**Single Activity, Fragment-based navigation:** `AppActivity` hosts a Navigation Component graph (`nav_graph.xml`). Fragments are the top-level destinations:
- `MainFragment` — coin flip screen (start destination)
- `SettingsFragment` — preferences screen
- `AboutFragment` / `DiagnosticsFragment` — `BottomSheetDialogFragment` dialogs

**UI is fully migrated to Jetpack Compose.** Fragments create a `ComposeView` in `onCreateView` and render a `*Screen` composable (e.g., `MainFragment` → `MainScreen`, `SettingsFragment` → `SettingsScreen`). Each Compose screen follows a two-layer pattern: `*Screen(viewModel)` collects state, `*View(state, postAction)` is the pure composable (used by `@PreviewLightDark`). The Compose theme is in `ui/theme/` (`AppTheme`), which honors the dynamic-color preference on API 31+. The coin animation still uses `AndroidView` wrapping an `ImageView` with `DurationAnimationDrawable`.

**State management pattern (MVI-style):**
- Each feature has a `*State` (Parcelable data class), `*Action` (sealed class for user intents), and `*Effect` (sealed class for one-shot side effects like navigation or toasts).
- ViewModels expose `stateFlow: StateFlow<State>` and `effectFlow: SharedFlow<Effect>`. UI calls `viewModel.postAction(action)`.
- State is saved/restored via `SavedStateHandle` using `save()`/`restore()` extensions in `extensions/SavedState.kt` (stores under the key `"state"`).
- Effects are consumed by Fragments (navigation, URLs via Chrome Custom Tabs, toasts, rate dialog).

**Dependency injection:** Hilt (`@HiltAndroidApp` on `App`, `@AndroidEntryPoint` on Activity/Fragments, `@HiltViewModel` on ViewModels). `AppModule` provides system services (`SensorManager`, `Vibrator`, `SoundPool`, `SharedPreferences`) and platform types (`Clock`, `Random`, `SecureRandom`, `BuildInfo`) as a `SingletonComponent`. `ColorHelper` is `@ActivityScoped`.

**Key domain classes:**
- `Coin` — core flip logic; tracks `currentValue` to determine animation permutation (heads→heads, heads→tails, etc.). Deliberately holds *no* streak state: `DiagnosticsViewModel` runs `coin.flip()` in a loop up to 10,000,000 times, which would obliterate the user's run and records
- `Stats` — the counts, the run of identical results in progress, and each face's longest run, as one value. `afterFlip()` folds a landed flip into all three. They travel together because reset and undo have to move all of them at once
- `RNG` — wraps `kotlin.random.Random` / `SecureRandom`, listens for preference changes to hot-swap
- `CoinType` / `CoinGroup` — the catalog of every coin the app ships. This used to be three parallel
  `string-array`s in `res/values/arrays.xml` that only worked because they lined up index for index; one
  enum makes the three columns a single row and the drift impossible. `prefix` is the identity — the string
  SharedPreferences stores *and* the drawable name prefix — so `Setting.COIN.default` and `AnimationHelper`'s
  `"random"` sentinel read it off the enum instead of repeating a literal. Declaration order is load-bearing:
  the picker opens a new header only on a change of `group`, so the entries have to stay in unbroken runs.
  `coinName` is a plain string rather than a `@StringRes` — the coin names were never translated, and holding
  them in Kotlin says so outright where a `string-array` left every locale a permanent hole
- `ShakeForce` — the three shake sensitivities as one enum: the string on disk, the label resource, and
  the seismic threshold together. It is what `Setting.FORCE` decodes to, so the choice stays typed all the
  way to the segmented control and the string exists only inside the setting. `SettingsManager` exposes both
  halves — `force` for the control, `shakeSensitivity` for the detector — off one read, so a value this build
  no longer offers falls back to medium in both. It used to shake at medium while the control showed nothing
  selected at all. `stored` is spelled out rather than derived from the constant name it happens to match:
  these strings are already on disk, so deriving them would let a rename invalidate them silently
- `SettingsManager` — typed accessors over `SharedPreferences`, one per preference. Every key, default and encoding lives in `Setting`, a sealed `Setting<T>` whose subclasses each know how to read and write themselves, so `update(setting, value)` is checked at compile time and `prefs[Setting.X]` needs no cast. It replaced an enum with an `Any?` default, which could not carry a type — enums take no type parameter — and so cast at every use site, checked `update`'s argument at runtime, and left the Long-valued settings a persistence path of their own. Adding a key needs no schema bump: `validateSchema()` wipes everything only on a version *mismatch*
- `AnimationHelper` — generates frame-by-frame `DurationAnimationDrawable` for each of the 4 flip permutations; coin images are loaded by resource name prefix (`CoinType.prefix`, e.g. `"gw"` → `gw_heads` / `gw_tails` drawables). A permutation with no frames gets *no map entry*, not an empty drawable — `getLastFrame()` reads index -1 on an empty one, which would defeat the caller's null check
- `SoundHelper`, `VibrationHelper` — play sounds / haptics only when the corresponding setting is enabled. `STREAK` runs ~5.5s where the others run ~1s, so it is still sounding over the flips that follow it; `AppModule` sizes the `SoundPool` stream budget for that

**Streaks** count *any* face repeating, not heads specifically — the app ships 80-odd coins and custom
labels and takes no side, so a run is "the same result again". The number is therefore never 0 and
moves on every flip. `MainState.streakCount` is the *deferred* copy, held at 0 while a flip is
mid-air exactly as `headsCount`/`tailsCount` are, so nothing reveals the result early. It always
holds the true run, including 1 — `MIN_DRAWN_STREAK` is the separate *display* rule that keeps a
meaningless `×1` off every single flip, and it gates the cold-start seeding too so the screen still
opens blank when the standing run is 1. The run is drawn on the result's own line as `HEADS ×7`
rather than taking a line of its own, because landscape has no vertical room to spare. It is two
`Text`s in a center-aligned `Row`, not one styled span: a span shares the result's baseline, which
leaves the smaller digits sitting low against tall capitals. It survives a cold start (that is the point — you show somebody your
run hours later), which is why `onResume` seeds `result` from the persisted run so the number is
named rather than floating alone under an unflipped coin. Beating your own record plays the `STREAK`
fanfare, but only from `FANFARE_THRESHOLD` (ten) up; below that a new record lands within the first
few flips and the five-second sound would fire constantly. Ten also takes about 1,000 flips to reach,
so the fanfare stays a rare event. The record itself lives in Settings, not on
the main screen — a personal best is a trophy to look up, the run in progress is the number you hold
up to somebody.

**Testing:** JUnit 5 with MockK for mocking, Kluent for assertions, Turbine for Flow testing. ViewModel tests use `@ExtendWith(MainDispatcherRule::class)` to swap `Dispatchers.Main` with `UnconfinedTestDispatcher`. Tests are in `coinflip/src/test/`; there is no `androidTest` source set, so nothing in Compose is covered.

`FakeSharedPreferences` is an in-memory `SharedPreferences` used instead of mocking the interface, so
tests can assert what the store ends up holding. `CoinResourcesTests` reads `res/drawable` off disk to assert every
`CoinType` still ships the two faces it names by string concatenation, and that the catalog stays grouped —
`build.gradle.kts` declares that directory as a test input so the guard is not skipped as up-to-date.

**Coverage:** Kover, reported on the debug variant. Run `./gradlew :coinflip:koverHtmlReportDebug` for the
figure rather than trusting one written down here, and read it knowing that `CoinType`'s 80-odd declaration
lines count as covered the moment a test touches the enum, so it flatters a little. The biggest remaining gap is `AnimationHelper`'s bitmap pipeline, which needs Robolectric or instrumentation rather than plain unit tests. Generated (Hilt/Dagger) code, `@Composable`
functions, and the theme declarations are filtered out in the `kover` block of `coinflip/build.gradle.kts`,
so the number reflects testable logic only — remove the Compose exclusions if UI tests are ever added.
Nothing gates the build: `koverVerify` runs as part of `check` but has no rules. CI is the single
**Build & Test** workflow, which reports coverage into its run summary as a step that cannot fail the
job. It lives on the same job as the tests on purpose: `check` already runs the instrumented tests, so
generating the reports costs about a second, where a separate workflow would repeat the whole build.

## Code Style

Kotlin formatting is enforced by **ktlint**. Run `./gradlew :coinflip:format` before committing. The project uses Kotlin DSL for Gradle build files and a version catalog (`gradle/libs.versions.toml`). Package is `com.banasiak.coinflip`; code targets JVM 17.

**Toolchain:** AGP 9 compiles Kotlin itself, so there is deliberately no `org.jetbrains.kotlin.android`
plugin — re-adding it fails the build. The compose and parcelize compiler plugins are still applied
separately, and `kotlin { compilerOptions { } }` still configures the compiler. `compileSdkMinor`
selects the minor revision of the platform. KSP's version trails Kotlin's, and that is fine under
AGP 9: Hilt's codegen runs and the build is clean, so do not assume the two have to match. Verify
before holding a Kotlin bump back on KSP's account.

Dependabot is deliberately **not** configured for version updates; there is no `dependabot.yml`.
Security alerts are a repository setting instead. Dependency bumps are done deliberately, and the
SHA-pinned actions in `build.yml` need their pin and version comment updated together — note that
`gradle/actions` uses annotated tags, so verifying a pin means dereferencing the tag object.

ktlint runs from the CLI and takes its rules from the `ktlint_*` block at the end of `.editorconfig`.
Each release adds standard rules that reflow working code rather than catch a defect, so a bump
normally arrives as a wall of violations; the ones this project rejects are disabled there, and the
next bump will likely need a few more added. Check what a new rule actually changes before adopting it.

## Localization

The app ships English plus 12 translations (and an `es-MX` regional variant); none were done by a
human translator. Lint treats `MissingTranslation` as an **error**, so adding a translatable string
means adding it to every locale in the same commit or `./gradlew :coinflip:check` fails.

Read **[I18N.md](I18N.md)** before touching `res/values-*/strings.xml`. It records the coin-face
terminology per locale and the rule behind it (portrait side = heads), the two idioms that run
backwards against that rule, which strings are still unreviewed machine output, and the regional
variant's partial-override design.
