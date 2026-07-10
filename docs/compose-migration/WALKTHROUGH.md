# Walkthrough: Main + Settings → Jetpack Compose

Completed the app's Compose migration by converting the last two legacy XML
screens (coin-flip **Main** and **Settings**) to the project's existing
`ComposeView`-in-`Fragment` MVI pattern, and added full dynamic-color support.

> [!NOTE]
> Per the approved plan: AndroidX Navigation Component was kept as nav
> infrastructure, dynamic colors are fully supported, and existing behavior was
> preserved.

## What changed

### Theming
- `ui/theme/Theme.kt` — `AppTheme` gained `dynamicColor: Boolean = false`; uses
  `dynamicLight/DarkColorScheme(LocalContext.current)` on API ≥ 31. Threaded the
  setting through all four screens (Main, Settings, About, Diagnostics) via their
  state/ViewModels.

### Main screen
- **New** `main/MainScreen.kt` — `MainScreen(...)` (collects state/effects) +
  pure `MainView(state, postAction, flipToken)`. Coin rendered via Compose
  `AndroidView { ImageView }` driven by `LaunchedEffect(flipToken)` (keeps the
  frame-based `DurationAnimationDrawable` animation). Compose `NavigationBar`
  replaces the old `nav_menu.xml`.
- `main/MainState.kt` — added `dynamicColors`, `headsCount`, `tailsCount`;
  removed `MainEffect.UpdateStats`.
- `main/MainViewModel.kt` — stat counts now live in state and update only after
  the coin lands (`onResume`/`onFlipFinished`/`onResetStats`), preserving the
  "counts appear after landing" behavior.
- `main/MainFragment.kt` — now hosts a `ComposeView`; retains ShakeDetector and
  Play in-app review.

### Settings screen
- **New** `settings/SettingsViewModel.kt`, `SettingsState.kt`, `SettingsScreen.kt`
  — replaces `PreferenceFragmentCompat` with a `Scaffold` of category headers,
  switch rows, single-choice (coin/force) dialogs, custom-text input dialogs, and
  a reset-with-undo snackbar. Writes go through `SettingsManager` against the same
  `SharedPreferences` keys so `RNG`'s change listener keeps firing.
- `settings/SettingsManager.kt` — added `update(setting, value)` setter and
  `forceValue` getter.
- `settings/SettingsFragment.kt` — plain `Fragment` hosting `ComposeView`;
  recreates the Activity on back when the dynamic-color pref changed.

### Cleanup
- Deleted `fragment_main.xml`, `nav_menu.xml`, `root_settings.xml`,
  `SquareFrameLayout.kt`, `NumberPreference.kt`; cleaned `nav_graph.xml`.

## Verification summary

| Check | Command | Result |
|---|---|---|
| Lint | `:coinflip:ktlintCheck` | ✅ pass |
| Unit tests | `:coinflip:testDebugUnitTest` | ✅ 44 passed, 0 failed |
| Build | `:coinflip:assembleDebug` | ✅ success |
| Previews | `MainView` / `SettingsView` (light+dark) | ✅ render correctly |

New tests: `MainViewModelTests`, `SettingsViewModelTests`; updated
`AboutViewModelTests`.

## Not yet done (deferred — no device available)
On-device smoke test is still outstanding. The full checklist is tracked in
[STATUS.md](./STATUS.md) and should be run before shipping.
