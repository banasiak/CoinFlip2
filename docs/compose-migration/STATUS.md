# Task: Migrate Main + Settings screens to Jetpack Compose

## Status: implementation + static verification COMPLETE. Only on-device smoke test remains (blocked — no device available).

- [x] Add dynamic-color support to `AppTheme` + thread through all 4 screens (Main, Settings, About, Diagnostics)
- [x] Migrate Main screen (`MainState`/`MainViewModel`/`MainScreen`/`MainFragment`)
- [x] Migrate Settings screen (`SettingsState`/`SettingsViewModel`/`SettingsScreen`/`SettingsFragment` + `SettingsManager` setters)
- [x] Delete dead XML/classes; clean `nav_graph.xml`
- [x] Add `MainViewModelTests` + `SettingsViewModelTests`; update `AboutViewModelTests`
- [x] Verify: `format`, `ktlintCheck` pass, 44 unit tests pass, `assembleDebug` succeeds
- [x] Render Compose previews for `MainView` and `SettingsView` — both render correctly
- [ ] **BLOCKED / DEFERRED** Manual on-device smoke test (no emulator/device available)

## Remaining on-device smoke test checklist (for whoever resumes)
- [ ] Tap-to-flip + shake-to-flip trigger the coin animation
- [ ] Result text + stats counts update only AFTER the coin lands (not at flip start)
- [ ] Quick-reset button + undo snackbar work
- [ ] Bottom nav routes to Diagnostics / Settings / About
- [ ] Every settings control persists across an app restart
- [ ] Dynamic-color toggle recreates the Activity and re-themes
- [ ] Custom heads/tails text dialog + diagnostics validation snackbars behave correctly

## Key context to resume
- Approved decisions: keep AndroidX Navigation Component; fully support dynamic colors; preserve behavior/fix bugs.
- Pattern: `Fragment` hosts `ComposeView` rendering `*Screen(viewModel)`; pure `*View(state, postAction)` is `@PreviewLightDark`. MVI with `*State`/`*Action`/`*Effect`, `@HiltViewModel`.
- Coin animation stays an `ImageView` wrapped in Compose `AndroidView` (`DurationAnimationDrawable`).
- Build: `./gradlew :coinflip:format` (run before commit), `:coinflip:ktlintCheck`, `:coinflip:testDebugUnitTest`, `:coinflip:assembleDebug`.
- See [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) (approved) and [WALKTHROUGH.md](./WALKTHROUGH.md) for details.
