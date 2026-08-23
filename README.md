# Coin Flip

**15 years later...** an AI-assisted refactor of the modern rewrite of the app that started it all.

The original [Simple Coin Flip](https://github.com/banasiak/CoinFlip) landed on Android in 2011. In
2023 it was thrown away and rebuilt from scratch as a [MAD](https://developer.android.com/modern-android-development)
(Modern Android Development) app. In 2026 that rewrite got taken apart and put back together again —
this time with a coding assistant doing the typing.

It is still, fundamentally, an app that shows you a picture of a coin.

Available on the [Google Play Store](https://play.google.com/store/apps/details?id=com.banasiak.coinflip).

## What it does

Tap the coin or shake the phone. It flips, lands, and tells you which side came up. That's the whole
pitch, and everything else exists to make that one interaction pleasant:

- **81 coins to flip** — all 51 state quarters (District of Columbia included), the George Washington
  dollar, JFK half-dollar and Sacagawea dollar, 24 national euro designs, the Canadian loonie and
  toonie, plus a two-headed dollar for when you'd rather not leave things to chance. Or pick
  **Random** and let the app choose a different one every flip.
- **Favorites** — star the coins you actually use and they collect in their own section at the top of
  the picker, so you're not scrolling past every state quarter to find the loonie.
- **Custom labels** — call the sides whatever you like. Yes / No, Beer / Tacos, Ship / Crown.
- **Heads and tails tallies**, with a quick-reset button if you want it.
- **Material You** dynamic color on Android 12+, light and dark, portrait and landscape.
- **Secure random** — swap `kotlin.random.Random` for `java.security.SecureRandom` if you have strong
  feelings about entropy sources in a coin-flipping app. (You might!)
- **A diagnostics screen** that flips the coin up to ten million times and reports the distribution,
  for exactly those people.
- **13 languages**, and sound, haptics and animation you can each turn off.

## Under the hood

Single-module Kotlin app, Jetpack Compose throughout, MVI-ish state management.

| | |
|---|---|
| **UI** | Jetpack Compose (Material 3), one `Activity` hosting Navigation Component fragments that each render a Compose screen |
| **State** | Per-feature `State` / `Action` / `Effect` triads; ViewModels expose `StateFlow` and a `SharedFlow` of one-shot effects |
| **DI** | Hilt |
| **Persistence** | `SharedPreferences` behind a typed `SettingsManager` |
| **Testing** | JUnit 5, MockK, Kluent, Turbine — 142 unit tests |
| **Coverage** | Kover, reported into every CI run's summary |
| **Toolchain** | AGP 9 / Gradle 9 / Kotlin 2.3, `compileSdk` 37, `minSdk` 26 |

The coin animation is the one deliberate holdout: it's still an `ImageView` driven by a frame-by-frame
`AnimationDrawable`, wrapped in an `AndroidView`. It was written that way in 2011, it works, and
nothing about Compose makes it better.

Two supporting documents are worth reading before touching related code:

- **[CLAUDE.md](CLAUDE.md)** — architecture notes and the non-obvious constraints, aimed at coding agents
- **[I18N.md](I18N.md)** — which coin face is "heads" in each language, and the two idioms that run backwards

## Building

```bash
./gradlew :coinflip:assembleDebug
```

```bash
./gradlew :coinflip:check
```

`check` runs the build, unit tests, Android lint and ktlint. CI runs the same thing on every pull
request and reports coverage into the run summary.

## Credits

The 2026 refactor was done by **[Claude](https://claude.com/claude-code)** — landscape support, the
rebuilt Settings screen, the searchable coin picker and favorites, corrections to all 13 translations,
the unit test suite, the AGP 9 / Gradle 9 / SDK 37 migration, and most of the code in between — working
from direction, design decisions, and on-device testing by [@banasiak](https://github.com/banasiak).
The commit messages in that stretch were written by Claude as well, which goes some way toward
explaining their length.

Coin images are from the [United States Mint](https://www.usmint.gov/) and the
[European Central Bank](https://www.ecb.europa.eu/).

## License

Public domain, via [the Unlicense](LICENSE). Do whatever you want with it.
