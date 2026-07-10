# Migrate Remaining Legacy XML Views to Jetpack Compose

The app is mid-migration to Compose. The `about` and `diagnostics` screens already follow
the documented pattern (a `Fragment` hosts a `ComposeView` rendering a `*Screen` composable,
backed by an MVI `*State`/`*Action`/`*Effect` + `*ViewModel`). The two remaining screens still
use legacy XML:

- **Main screen** — `MainFragment` inflates `fragment_main.xml` via ViewBinding and binds imperatively.
- **Settings screen** — `SettingsFragment` is a `PreferenceFragmentCompat` driven by `root_settings.xml`.

This plan migrates both to Compose using the **exact same pattern** as About/Diagnostics, deletes the
now-dead XML/classes, and preserves all current behavior (coin animation, stats timing, shake-to-flip,
dynamic colors, dynamic-color activity restart, preference persistence).

## User Review Required

1. **Navigation architecture (scope) — recommend "keep").**
   The project's documented and existing pattern is *ComposeView-inside-Fragment* with the
   AndroidX **Navigation Component** (`nav_graph.xml` + `NavHostFragment` in `activity_app.xml`).
   - **Recommended (this plan):** Keep Navigation Component. Migrate the *content* layouts
     (`fragment_main.xml`, `root_settings.xml`) and the bottom-nav menu (`nav_menu.xml`) to Compose.
     `activity_app.xml` + `nav_graph.xml` remain as navigation **infrastructure** (not rendered "views").
   - **Alternative (larger, not recommended now):** Full rewrite to `navigation-compose`, deleting all
     Fragments, `activity_app.xml`, `nav_graph.xml`, and re-migrating About/Diagnostics into
     `ModalBottomSheet`s. Bigger blast radius; contradicts the documented pattern.
   👉 Please confirm you're happy keeping the Navigation Component (Recommended).

2. **Dynamic colors.** Today, dynamic (wallpaper) colors apply to XML/Material screens via the Activity
   theme, but the Compose `AppTheme` uses static brand colors — so About/Diagnostics already ignore
   dynamic colors. To avoid a **visible regression** on Main/Settings after migration, I'll add dynamic
   color support to `AppTheme` (API 31+, gated on the existing `dynamic` setting) and thread the flag
   through all four screens for consistency. 👉 OK to include this?

3. **Stats-count timing (Main).** The current code intentionally updates the on-screen heads/tails
   counts only **after** the flip animation finishes (via the `UpdateStats` effect), not when the flip
   starts. I'll preserve this exact timing by moving the displayed counts into `MainState` (updated at
   the same moments) and removing the now-redundant `UpdateStats` effect. 👉 Confirm this is acceptable.

---

## Proposed Changes

### Main screen (`com.banasiak.coinflip.main`)

Convert the coin-flip screen to Compose, matching the About/Diagnostics pattern. The coin image keeps
using an `ImageView` (for `DurationAnimationDrawable` frame animation) wrapped in `AndroidView`, as
described in `CLAUDE.md`.

#### [NEW] [MainScreen.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/main/MainScreen.kt)

- `MainScreen(viewModel, onNavigate, onShowRate)` collects `stateFlow` + `effectFlow`:
  - `FlipCoin` → bump a flip token used to (re)start the coin animation (so unrelated recompositions
    never restart it mid-flip).
  - `ToAbout`/`ToSettings`/`ToDiagnostics` → `onNavigate(@IdRes)`; `ShowRateDialog` → `onShowRate()`.
- `MainView(state, postAction, flipToken)` — pure composable used by `@PreviewLightDark`:
  - Root `Column` with `systemBarsPadding()`, tap-to-flip (`clickable` → `MainAction.TapCoin`).
  - Square coin `Box` (`fillMaxWidth().aspectRatio(1f)`, replaces `SquareFrameLayout`) containing:
    - `PLACEHOLDER`: large bold "?" in `colorPrimary`.
    - `IMAGE`/`ANIMATION`: `AndroidView { ImageView }`; `update` sets drawable/background by
      `coinImageType`; a `LaunchedEffect(flipToken)` calls `stop()/start()` on the `DurationAnimationDrawable`.
  - Result text (color by `result.value`: HEADS→secondary, TAILS→tertiary, else→primary), shown via
    `alpha` so layout space is preserved when hidden (matches old `isInvisible`).
  - Instructions text; stats row (heads=secondary, tails=tertiary) gated on `statsVisible`;
    `reset` `Button` gated on `resetVisible`.
  - Bottom `NavigationBar` (replaces `BottomNavigationView`/`nav_menu.xml`) with Diagnostics/Settings/About
    items (icons `@drawable/diagnostics|settings|about`) posting `TapDiagnostics`/`TapSettings`/`TapAbout`.
- `@PreviewLightDark MainViewPreview()`.

#### [MainState.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/main/MainState.kt)

- Add `headsCount: String = "0"`, `tailsCount: String = "0"`, `dynamicColors: Boolean = false` to `MainState`.
- Remove `MainEffect.UpdateStats` (replaced by the new state fields).

#### [MainViewModel.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/main/MainViewModel.kt)

- Set `dynamicColors = settings.dynamicColorsEnabled` and displayed counts in `onResume()`.
- Update displayed counts in `onFlipFinished()` and `onResetStats()` (NOT in `flipCoin()` — preserves timing).
- Delete `updateStatsEffect()` + its emissions.

#### [MainFragment.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/main/MainFragment.kt)

- Replace ViewBinding with `ComposeView { MainScreen(viewModel, onNavigate = ::navigate, onShowRate = ::showRateAppDialog) }`.
- Keep `onPause`/`onResume` → `postAction`, the Play review flow, and the `ShakeDetector`
  (slim `stateFlow` collector to start/stop based on `shakeEnabled`/`shakeSensitivity`).
- Remove `bind()`/`onEffect()` and all view references.

#### [DELETE] [fragment_main.xml](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/res/layout/fragment_main.xml) · [nav_menu.xml](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/res/menu/nav_menu.xml) · [SquareFrameLayout.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/ui/SquareFrameLayout.kt)

- Also remove the `tools:layout="@layout/fragment_main"` attribute from `nav_graph.xml`.

---

### Settings screen (`com.banasiak.coinflip.settings`)

Replace `PreferenceFragmentCompat` + `root_settings.xml` with a Compose settings screen that reads/writes
the **same** `SharedPreferences` keys (so `SettingsManager` and `RNG`'s preference-change listener keep working).

#### [NEW] [SettingsState.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/settings/SettingsState.kt)

- `@Parcelize data class SettingsState` mirroring every preference: `coin`, `animate`, `shake`, `sound`,
  `text`, `vibrate`, `stats`, `quickReset`, `customHeadsText`, `customTailsText`, `diagnostics`,
  `dynamic`, `secureRandom`, `force`.
- `SettingsAction` (one per control, e.g. `SetCoin(value)`, `ToggleAnimate(on)`, `ResetStats`,
  `UndoReset`, `SetCustomHeads(text)`, `SetDiagnostics(value)`, `SetForce(value)`, …).
- `SettingsEffect`: `StatsReset` (triggers undo snackbar) and `InvalidIterations` (error snackbar).

#### [NEW] [SettingsViewModel.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/settings/SettingsViewModel.kt)

- `@HiltViewModel`, injects `SettingsManager`. Builds initial `SettingsState` from current values;
  each action writes through `SettingsManager` and re-emits state.
- Replicates the existing validation rules:
  - empty custom heads/tails → revert to `HEADS`/`TAILS` default;
  - non-positive `diagnostics` value → reject + `InvalidIterations` effect;
  - `ResetStats` snapshots stats then resets; `UndoReset` restores them.

#### [NEW] [SettingsScreen.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/settings/SettingsScreen.kt)

- `SettingsScreen(viewModel, onDynamicChanged)` collects state/effects; shows snackbars
  (reset+Undo, invalid iterations) via a `Scaffold` + `SnackbarHostState`.
- `SettingsView(state, postAction)` — pure, `@PreviewLightDark`, `AppTheme(dynamicColor = state.dynamic)`,
  scrollable `Column` with `systemBarsPadding()`, organized into the three `PreferenceCategory` headers.
- Reusable preference composables (Material3): `CategoryHeader`, `SwitchPreference`,
  `ListPreference` (radio `AlertDialog`), `TextPreference` (text `AlertDialog`),
  `NumberPreference` (numeric `AlertDialog`), `ClickPreference`. `enabled` flags reproduce the XML
  `dependency` rules (`quickReset`⇐`stats`, `force`⇐`shake`).

#### [SettingsManager.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/settings/SettingsManager.kt)

- Add typed setters that `prefs.edit { … }` for each writable setting (string/boolean), e.g.
  `setCoin`, `setAnimate`, `setCustomHeadsText`, `setDiagnostics`, `setDynamic`, `setForce`, …
  Writing through this same `SharedPreferences` instance automatically fires `RNG`'s change listener.

#### [SettingsFragment.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/settings/SettingsFragment.kt)

- Becomes a plain `Fragment`: `ComposeView { SettingsScreen(viewModel, onDynamicChanged = { enableRestartOnBack() }) }`.
- Keep the existing **restart-the-activity-on-back** behavior when `dynamic` changes (the only reliable
  way to re-apply Material dynamic colors to the Activity window).

#### [DELETE] [root_settings.xml](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/res/xml/root_settings.xml) · [NumberPreference.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/ui/NumberPreference.kt)

---

### Theme (`com.banasiak.coinflip.ui.theme`)

#### [Theme.kt](file:///home/banasiak/workspace/CoinFlip2/coinflip/src/main/java/com/banasiak/coinflip/ui/theme/Theme.kt)

- Add `dynamicColor: Boolean = false` to `AppTheme`; when `true` and API ≥ 31, use
  `dynamicLightColorScheme`/`dynamicDarkColorScheme(LocalContext.current)`. Default `false` keeps previews
  and unchanged callers on the static brand palette.
- Thread the flag from each `*Screen` (Main/Settings via state; About/Diagnostics via a small
  `dynamicColors` field added to their states/VMs) for consistent behavior. *(Pending Review item #2.)*

> [!NOTE]
> `Coin.Value` keeps its now-unused `@StyleRes style` field and `themes.xml`'s `TextAppearance` styles
> are left intact (harmless) to keep the diff focused. `App.kt`'s `DynamicColors.applyToActivitiesIfAvailable`
> stays (it themes the Activity window behind the Compose content).

---

## Verification Plan

### Automated Tests
- **New** `coinflip/src/test/.../main/MainViewModelTests.kt` (MockK + Turbine + `MainDispatcherRule`,
  mirroring `AboutViewModelTests`): asserts initial state; `onResume` populates labels/counts/flags;
  a flip emits `FlipCoin` and counts update only after `onFlipFinished`; `ResetStats` zeroes counts;
  tap actions emit the correct navigation effects.
- **New** `coinflip/src/test/.../settings/SettingsViewModelTests.kt`: each setter calls the matching
  `SettingsManager` writer and updates state; empty custom heads/tails revert to default; invalid
  `diagnostics` emits `InvalidIterations`; `ResetStats`→`UndoReset` restores stats.
- Commands:
  - `./gradlew :coinflip:test`
  - `./gradlew :coinflip:ktlintCheck`
  - `./gradlew :coinflip:assembleDebug`

### Manual / Tooling Verification
- `render_compose_preview` for `MainView` and `SettingsView` (light + dark) to confirm layout.
- Deploy to the device and verify: tap & shake flip + animation, stats increment after the coin lands,
  reset button, bottom-nav navigation to About/Diagnostics/Settings, every settings control persists
  (re-open app), dynamic-color toggle restarts and re-themes, custom heads/tails + diagnostics validation
  snackbars. Capture before/after screenshots for the walkthrough.
