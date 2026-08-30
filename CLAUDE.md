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
- `CustomCoin` / `CustomCoinStore` / `CoinImage` — the one coin whose artwork the user supplies.
  See **The custom coin** below
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
- `AnimationHelper` — generates frame-by-frame `DurationAnimationDrawable` for each of the 4 flip
  permutations. Resolution stops at *drawables*, not resource ids: a shipped coin comes from
  `<prefix>_heads`/`<prefix>_tails` by name, the custom coin comes off disk, and either failing
  reads as null and falls back to `Setting.COIN.default`. It used to dereference the ids with `!!`,
  which turned a prefix the build no longer ships into a crash. A permutation with no frames gets
  *no map entry*, not an empty drawable — `getLastFrame()` reads index -1 on an empty one, which
  would defeat the caller's null check. The cache key is the prefix alone for a shipped coin; for
  the custom coin it also carries the store's revision and the rim colors, because neither changes
  the prefix and both have to force a redraw
- `SoundHelper`, `VibrationHelper` — play sounds / haptics only when the corresponding setting is enabled. `STREAK` runs ~5.5s where the others run ~1s, so it is still sounding over the flips that follow it; `AppModule` sizes the `SoundPool` stream budget for that

**The custom coin** is a single user-supplied heads/tails pair, mirroring the Custom Text row it
sits beside. It is deliberately **not** a `CoinType`: catalog entries name drawables that ship in
the APK, `CoinType.flippable` is the pool `RANDOM` draws from, and `CoinResourcesTests` asserts both
about every entry. It rides the existing plumbing instead as a reserved prefix, `"custom"`, since
`Setting.COIN` was always just a string. Settings is the *only* entry point — the coin picker
selects and favorites, and launching a system picker out of a list of eighty coins would be a second
job for one screen — so a half-configured coin exists in the Settings dialog and nowhere else. Once
both faces are set it appears in the picker in `OTHER`, immediately before `RANDOM` (which keeps the
sentinel last on screen as well as in the enum), and is permanently starred. That permanence lives
in `buildCoinList`, not in `Setting.FAVORITES`: a stored star could be toggled off from its own row,
and would outlive the artwork it names. Deleting it resets `Setting.COIN` when the custom coin was
selected, since the entry leaves the picker with it. Nothing is unlinked up front: the coin reads as
gone at once while the files stay put, and they go only when the snackbar's undo lapses — or when
Settings closes, which settles a delete the departing snackbar would otherwise strand. So undo costs
nothing, and no renamed leftovers accumulate. `validateSchema()` wipes prefs but not `filesDir/coins`, so a
schema bump orphans the images rather than deleting somebody's photo — re-selecting brings the coin
straight back.

Four things about it are invisible in the code and easy to undo by accident:

- **Faces are decoded raw and tagged mdpi — never density-scaled.** `res/drawable` carries no
  density qualifier, so a shipped face is the mdpi baseline, but `ResourcesCompat.getDrawable`
  leaves the *bitmap* at its stored 390px with `density = 160` and lets the drawable do the scaling
  at draw time. `resizeBitmapDrawable` composites **raw pixels** against that 390px backdrop, so a
  custom face pre-scaled to the display density overflows the canvas and every squashed frame is
  clipped. `CustomCoinStore.decode` therefore decodes with `inScaled = false` and stamps
  `density = DENSITY_MEDIUM`, reproducing exactly what the resource loader hands back. The trap is
  a quiet one, and it was shipped once before being caught on a device: a pre-scaled face still
  renders correctly at full size, because both routes report the same drawable intrinsic size, so
  the coin looks right until it is halfway through a flip. `inMutable = true` belongs here too —
  `Canvas` refuses an immutable bitmap and the rim is stroked straight onto this face.
- **EXIF orientation needs the mirror, not just the rotation.** `ExifInterface.rotationDegrees`
  reports only the rotation half of the tag, so the four mirrored orientations come back flipped —
  and for `TRANSPOSE` and `TRANSVERSE` that rotation belongs to a decomposition whose mirror has
  been dropped, leaving the picture a further 180° out. A Pixel camera writes `TRANSVERSE` for an
  ordinary portrait photo, so this shipped with real photos landing on the coin upside down.
  `CoinImage.orientationFor` maps all eight tags to a mirror-then-rotate pair instead, and is a pure
  function so the table is unit-tested — the `Matrix` it feeds is not testable, but the decision is.
  The crop screen's rotate and mirror buttons reuse that same `Orientation`, applied to the decoded
  bitmap rather than to the drawing, which is what keeps `coverScale`/`clampOffset`/`cropRect` free
  of any case for a turned image. The consequence is that the crop rect is in the *adjusted* image's
  coordinates, so `CustomCoinStore.save` has to replay the adjustment before the rect means anything.

- **The rim is optional, stroked at generation time, and neither baked in nor overlaid.** It is
  what makes an arbitrary photo read as a coin, so it is on by default — but somebody who has
  photographed a *real* coin wants neither the ring nor the tinted edge, and the switch in the
  Custom Coin dialog turns both off together (`Setting.CUSTOM_COIN_RIM`, carried to `AnimationHelper`
  as a null `RimColors`, which is also what a shipped coin passes). Not baked into the
  stored file because the color follows the theme (`secondary` is a crimson in light and a pink in
  dark, and either can come from Material You). Not drawn over the finished animation because
  `resizeBitmapDrawable` squashes frames to a quarter width mid-flip, so a ring added afterwards
  would stay round while the coin turned inside it. `MainScreen` reads the colors off the Material
  scheme rather than through `ColorHelper`, which resolves the *View* theme's `colorPrimary` — a
  parallel mechanism free to drift from the result text the rim is meant to match. Width is 5% of
  the diameter, rounded from the Claude coin's measured 5.1%. The cache key carries the colors *and*
  their absence, so switching the border off redraws the ring away rather than leaving it up.
- **The edge is tinted as a copy.** Drawables resolved from resources share their `ConstantState`,
  so a `ColorFilter` on `R.drawable.edge` would follow the shipped coins around any process that had
  also drawn a custom one. `SRC_IN` loses nothing: the asset is a single flat `#696969` whose
  thirty-odd distinct values differ only in alpha. One blended color rather than one per transition,
  because the edge frame sits between the faces in every permutation and belongs to neither.

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
lines count as covered the moment a test touches the enum, so it flatters a little. The biggest remaining gap is the bitmap pipeline — `AnimationHelper`'s frame generation and
`CoinImage`'s crop, mask, rim and tint — which needs Robolectric or instrumentation rather than
plain unit tests. That gap is why the crop's pan and zoom arithmetic is carved out of
`CoinCropDialog` as `coverScale`/`clampOffset`/`cropRect`: Compose's geometry types are pure Kotlin,
so the part that is easy to get wrong is testable even though the gestures around it are not. Generated (Hilt/Dagger) code, `@Composable`
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
means adding it to every locale in the same commit or `./gradlew :coinflip:check` fails — including
when that would otherwise split a feature's strings out into a commit of their own. `values-es-rMX`
is overrides only and inherits the rest from `values-es`, so it is not one of the twelve.

Read **[I18N.md](I18N.md)** before touching `res/values-*/strings.xml`. It records the coin-face
terminology per locale and the rule behind it (portrait side = heads), the two idioms that run
backwards against that rule, which strings are still unreviewed machine output, and the regional
variant's partial-override design.
