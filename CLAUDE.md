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
  See **The custom coin** below. `revision` is the cache key for anything drawn from these files:
  the prefix is `"custom"` before and after a replacement, and a face can be *cleared* without the
  revision moving at all, so a `remember` over a thumbnail has to key on both it and whether the
  face is set
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
  the prefix and both have to force a redraw. The cache is what makes it safe for `MainScreen` to
  report the theme colors on every composition — but `RANDOM` deliberately opts out of it and
  rerolls on every load, so a caller that regenerates unconditionally draws one coin and immediately
  replaces it with a different one. `MainViewModel.onSetRimColors` therefore regenerates only when
  the coin on screen is actually drawn in those colors (`needsRimColors()`); everything else already
  loaded in `onResume`, moments earlier
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
gone at once while the files stay put, so undo costs nothing and no renamed leftovers accumulate.
Three things then settle the pending delete, and it needs all three because the snackbar can vanish
without ever reporting either way — `repeatOnLifecycle(STARTED)` cancels the coroutine awaiting it,
so merely rotating the device strands a delete with no undo left on screen. The snackbar lapsing
untouched commits it; Settings closing commits it (`onCleared`, which runs *after* `viewModelScope` is cancelled, so
the unlink goes to a scope on the store rather than blocking the main thread during teardown); and
writing a new face commits it **before** the write. That last one is the subtle one and it used to
be wrong: it called the delete *off* instead, which left the face that was not being replaced on
disk, where `storedFaces` found it and rebuilt the coin the user had just deleted. The order is
load-bearing in both directions — called off, the stale face survives; run after the write, it
unlinks the file just written. `validateSchema()` wipes prefs but not `filesDir/coins`, so a
schema bump orphans the images rather than deleting somebody's photo — re-selecting brings the coin
straight back.

Five things about it are invisible in the code and easy to undo by accident:

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
  Mirroring also has to negate the rotation alongside the toggle. `Orientation` is mirror-*then*-
  rotate, and R(θ)·M·R(−θ) is a flip about the *other* axis, so without that the Mirror button flips
  the picture left-to-right at 0° and top-to-bottom at 90° — one button doing two different visible
  things depending on hidden state. Negating the turn is the identity M·R(θ) = R(−θ)·M, and all
  eight orientations stay reachable either way, which is what makes the bug easy to miss.

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
- **The crop screen's turned copy is guarded and released, and the original is neither.** Rotating
  or mirroring re-derives the displayed bitmap through `CoinImage.oriented`, which allocates a
  second full-size copy beside the original — up to 16MB apiece at the 2048px decode bound, so a few
  taps churn a lot of heap. Two rules keep that safe. It is wrapped in an `OutOfMemoryError` guard,
  because `decodeBounded` bounds the *decode* and this allocation happens after it; unguarded, a
  turn crashes the process where the identical failure one step earlier reaches the user as
  `coin_crop_failed`. And the superseded copy is released from `DisposableEffect`'s `onDispose`,
  **not** at the moment it is replaced — until the composition lets go of it, it is still what the
  last frame drew, and recycling it there trades a memory problem for a use-after-recycle crash.
  The identity check against `CropSource.Ready.bitmap` is load-bearing too: `oriented` returns the
  *source itself* when the orientation is upright, so an unguarded recycle would take the original
  with it and leave every later turn drawing from a dead bitmap.
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
meaningless `×1` off every single flip. The run is drawn on the result's own line as `HEADS ×7`
rather than taking a line of its own, because landscape has no vertical room to spare. It is two
`Text`s in a center-aligned `Row`, not one styled span: a span shares the result's baseline, which
leaves the smaller digits sitting low against tall capitals. It survives a cold start (that is the
point — you show somebody your run hours later), on the back of the restore described below.
Beating your own record plays the `STREAK`
fanfare, but only from `FANFARE_THRESHOLD` (ten) up; below that a new record lands within the first
few flips and the five-second sound would fire constantly. Ten also takes about 1,000 flips to reach,
so the fanfare stays a rare event. The record itself lives in Settings, not on
the main screen — a personal best is a trophy to look up, the run in progress is the number you hold
up to somebody.

**Resuming the main screen** puts back what the user left: the last result named, the face it landed
on drawn, and the run it belongs to counted. It used to revert to the `?` glyph on every resume and
hide the result unless a drawn streak was standing — a holdover from the first version of the app,
fifteen years ago. Two things still reset it to `?` with the result line cleared: the user picking a
different coin in Settings, and `RANDOM`, which draws a different coin every time it loads and would
otherwise spoil its own surprise. Both are decided in `restoreDisplay` by comparing
`AnimationHelper.identity()` against `MainState.drawnCoin`, the coin the face on screen was actually
drawn from — which is why replacing the custom coin's artwork counts as a change, a rim redrawn for
a new theme does not, and a coin picked in Settings and changed back before returning does not
either. Only the *display* is cleared: `stats` keeps the run, so the flip after a coin change
continues it rather than starting over.

Three things about it are less obvious than the rule:

- **A cold start has no drawable to restore, so it rebuilds one.** `MainState` dies with the process
  and `animation` is `@IgnoredOnParcel` besides, so `seededState()` reads the face the last flip
  landed on out of `Setting.STREAK_VALUE` and `Permutation.landingOn()` names the permutation whose
  last frame is that face. `Coin.restoreFace()` is the other half: the flip animation begins on the
  coin's `currentValue`, so a screen showing tails and a `Coin` freshly constructed on heads would
  make the coin jump as the flip started. The screen reports the face on every resume, which also
  resyncs it after `DiagnosticsViewModel` has flipped the singleton several million times.
- **The artwork loads on `Dispatchers.IO`, so the face goes up in two places.** `restoreDisplay`
  publishes the animation the helper already holds — an in-session resume is a cache hit, and
  drawing `?` first would flash on every return from Settings — and `showFace()` publishes it again
  once a load finishes, which is what a cold start and a theme change need.
- **A flip in the air owns the screen until it lands.** `onResume` restores nothing while
  `isFlipping`: `state.result` already holds the result the animation has not revealed yet, so
  putting the "previous" state back mid-flip shows what the coin is about to land on. The counts
  moved into `restoreDisplay` for the same reason, alongside `streakCount`, which was already
  deferred.

**The keyboard flickers when the Custom Text dialog's two fields hand focus over**, and it is
Compose's doing, not this app's. A text field that loses focus ends its IME session, and Compose
turns that into a hide — a deliberate divergence from how Views behave, argued out in the [design
doc](https://docs.google.com/document/d/1o-y3NkfFPCBhfDekdVEEl41tqtjjqs8jOss6txNgqaw/edit?resourcekey=0-o728aLn51uXXnA4Pkpe88Q#heading=h.ieacosb5rizm)
its source comment cites. Compose coalesces the stop and the next field's start when both land in
one batch, but that start is asynchronous — it waits on the session mutex — so the hide escapes
first, and Gboard runs the whole hide animation before the show that follows 16 ms later. Measured
on a Pixel 9a with `ImeTracker`: ~370 ms of keyboard leaving and coming back, on a focus change
whose two halves were 1 ms apart.

Ruled out on device, so a future report about this dialog does not restart the search: it is not the
dialog (two bare `OutlinedTextField`s on the Settings list do it too), not the legacy text field API
(`TextFieldState` with `showKeyboardOnFocus = true` does it), not `TextInputDialog`'s shell, its
`selectAll` selection or a cursor popup (empty fields do it), and not the tap path (the IME's Next
key does it). `keyboardController.show()` on focus gain cannot help: the coalescer ignores a show
queued after a stop (`if (startInput != false)`), and `KeyboardOptions` exposes no hide-on-blur
opt-out. Two platform `EditText`s in the same app produce *no* IME traffic at all on the same
transfer, which is the whole difference.

The one fix that works is backing the fields with `AndroidView` and `TextInputEditText`. That trades
the flicker for the M3 outlined styling, dynamic-color parity (a View theme beside a Compose one is
the drift `ColorHelper` is already called out for) and the dialog's `@PreviewLightDark` preview, so
it was considered and declined. Recheck after a Compose bump — the behavior is in
`TextInputServiceAndroid.processInputCommands`; the tracker has plenty of neighbouring text-field
keyboard bugs but none, as of August 2026, that names this one.

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

## Comments

These rules are enforced in review. They govern **production code**; test files are exempt, and
commenting the intent of a test is encouraged there.

- **An inline comment warns about a constraint at that line — it does not explain why an approach was
  chosen.** Write one only if changing that line without reading it would introduce a bug. "settled
  before the write, not called off by it: called off, the face that is not being replaced survives on
  disk and comes back" earns its place; "the run rides on the result's own line rather than taking a
  line of its own" does not, because the code already shows that.
- **Design reasoning goes in the commit message or in this file**, not inline, where it does not age
  against the code. The Architecture section above is where it lands — the custom coin's five
  invariants are there because they were too long to live at the lines they describe.
- **Say it once, in the place with the most context.** A comment can flag a real constraint and still
  be redundant. `buildCoinList` explains why the custom coin's star is permanent; the two comments
  that restated it at `CoinRow`'s call site and on its `favorite` parameter were deleted. Grep before
  adding one.
- **KDoc is mandatory for a public API, and for any signature whose parameters or return value are
  non-obvious** — `CustomCoinStore.thumbnail` documents that `targetPx` is approximate, and
  `SettingsViewModel.thumbnail` repeats it because a caller reads the wrapper. A KDoc on anything
  else has to earn its keep by preventing a footgun or documenting a genuinely complex function.
  Everything else is `//`.
- **"Public API" means the surface another part of the app calls, not every `public` declaration.**
  A feature's MVI triad is not one: `*State`, `*Action` and `*Effect` are that screen's own
  vocabulary, bundled with the ViewModel and the composable that read them, so a `data object` in a
  sealed action hierarchy is finished the moment it is named. The stores, helpers and managers other
  classes reach for are the surface this rule is about. `main` is the reference — it ships
  `MainState`'s fields and every `SettingsAction` subclass undocumented, deliberately.
- **Match the surrounding format.** In this codebase that means lowercase, no trailing period, and no
  `/** */` for a one-line remark on a property or a constant. Read the neighbouring comments before
  writing one.
- Length is a judgment call, not a limit — a long comment is fine when the constraint is genuinely
  intricate. It is the *content* rules above that decide whether it belongs.

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
