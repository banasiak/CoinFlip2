# Task: Migrate Main + Settings screens to Jetpack Compose

## Status: COMPLETE — implementation, static verification, and on-device smoke test all pass.

- [x] Add dynamic-color support to `AppTheme` + thread through all 4 screens (Main, Settings, About, Diagnostics)
- [x] Migrate Main screen (`MainState`/`MainViewModel`/`MainScreen`/`MainFragment`)
- [x] Migrate Settings screen (`SettingsState`/`SettingsViewModel`/`SettingsScreen`/`SettingsFragment` + `SettingsManager` setters)
- [x] Delete dead XML/classes; clean `nav_graph.xml`
- [x] Add `MainViewModelTests` + `SettingsViewModelTests`; update `AboutViewModelTests`
- [x] Verify: `format`, `ktlintCheck` pass, 44 unit tests pass, `assembleDebug` succeeds
- [x] Render Compose previews for `MainView` and `SettingsView` — both render correctly
- [x] On-device smoke test (Pixel 9a, 2026-07-09, driven via adb)

## Smoke test results (Pixel 9a)
- [x] Tap-to-flip triggers the coin animation
- [x] Shake-to-flip — confirmed manually by a human shaking the phone
- [x] Result text + stats counts update only AFTER the coin lands (verified mid-animation vs. landed screenshots)
- [x] Quick-reset button (appears when pref enabled, zeroes stats) + undo snackbar restores prior counts
- [x] Bottom nav routes to Diagnostics / Settings / About (both bottom sheets render and run)
- [x] Settings persist across a force-stop restart (custom text, quick reset, dynamic color — confirmed in UI and SharedPreferences XML)
- [x] Dynamic-color toggle recreates the Activity on back and re-themes
- [x] Custom HEADS text dialog persists ("WINNER" shown in stats row); diagnostics iterations dialog rejects `0` with "Invalid Number of Iterations" snackbar and does not persist

## Fixes made during the smoke test
- **Diagnostics summary regression:** the Compose `PreferenceRow` showed the static string
  "iterations" where the old `NumberPreference.SummaryProvider` showed the formatted value +
  units ("100,000 iterations"). Fixed in `SettingsScreen.kt` using `String.formatNumber()`.
- **ktlint:** 3 pre-existing `value-argument-comment` violations (trailing comments in argument
  lists in `MainScreen.kt` / `SettingsScreen.kt`) failed `ktlintCheck`; comments moved to their
  own lines. `format`, `ktlintCheck`, unit tests, and `assembleDebug` all pass again.

## Key context
- Approved decisions: keep AndroidX Navigation Component; fully support dynamic colors; preserve behavior/fix bugs.
- Pattern: `Fragment` hosts `ComposeView` rendering `*Screen(viewModel)`; pure `*View(state, postAction)` is `@PreviewLightDark`. MVI with `*State`/`*Action`/`*Effect`, `@HiltViewModel`.
- Coin animation stays an `ImageView` wrapped in Compose `AndroidView` (`DurationAnimationDrawable`).
- Build: `./gradlew :coinflip:format` (run before commit), `:coinflip:ktlintCheck`, `:coinflip:testDebugUnitTest`, `:coinflip:assembleDebug`.
- See [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) (approved) and [WALKTHROUGH.md](./WALKTHROUGH.md) for details.
