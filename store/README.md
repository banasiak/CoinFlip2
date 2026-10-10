# Play Store assets

Everything the Google Play listing for Simple Coin Flip is made of, kept beside the code it
describes. Play Console is still where it is published: nothing here uploads anything, and anything
changed here reaches the store only when it is pasted or uploaded there.

| File | What it is | Where it goes in Play Console |
|---|---|---|
| `store_listing.xml` | App name, short description and full description, in every language | The main store listing, one language at a time |
| `release_notes.xml` | What's new in the release being prepared, in every language | The release's notes, all languages in one paste |
| `screenshots/<language>/1.png`–`7.png` | Seven framed phone screenshots per language | The main store listing's phone screenshots, per language |
| `feature_graphic.png` | The 1024×500 banner | The main store listing's feature graphic |
| `ic_launcher-playstore.png` | The 512×512 app icon | The main store listing's app icon |

The promo video is rendered by [`promo/`](../promo/README.md), and Play takes it as a YouTube URL.

## The listing text

Both XML files hold one block per language, tagged with **Play Console's language code**, not
Android's resource qualifier. Most codes are language and region, but Thai is plain `th`. A block
Play does not recognise is dropped without a warning: version 77's release notes tagged Thai as
`th-TH`, and Thai users were shown the English notes instead.

Long text sits flush against the left margin between indented tags, so that copying it gives
exactly what Play should show. Keep `&`, `<` and `>` out of the text: XML would need them escaped,
and the escape is what would get pasted.

Before pasting anything, check both files:

```bash
python3 store/scripts/check.py
```

It confirms they parse, that they cover the same languages under codes Play uses, and that every
field is inside Play's limit: 30 characters for the app name, 80 for the short description, 4000
for the full description and 500 for each language's release notes. It prints every field's length
against its limit, and exits non-zero on any problem.

- **The app name here is the store's**, "Simple Coin Flip" in every language. The launcher label
  under the icon is the app's `app_name` resource, deliberately the shorter "Coin Flip"; CLAUDE.md
  says why. The two are not meant to match.
- **The short description must not mention price or promotion.** Play flagged "No ads" in October
  2026 with a warning that the app may not be promoted, so no language's short description says it.
  The full description still does.
- **Release notes are pasted as one block.** Copy the language blocks, without the `<?xml …?>` line
  or the `<release_notes>` tags around them, into the release's notes field. Play sorts the text
  into languages by the tags.
- **Write English first, then the translations.** Use the app's own words for anything that is on
  screen (its `strings.xml` in each `values-*` directory) and [I18N.md](../I18N.md)'s coin-face
  terms. Chinese and Japanese use full-width punctuation.

## Screenshots

```bash
store/scripts/screenshots.sh
```

This retakes all seven screenshots in every language of `store_listing.xml` and writes them to
`screenshots/<language>/`, framed the way Android Studio frames a Pixel 9a. A full run takes about
twenty minutes. Leave the phone alone while it runs.

| Shot | Screen |
|---|---|
| 1 | The main screen on first launch: the question mark, no flips yet |
| 2 | A flip that landed on heads, with four heads and six tails so far |
| 3 | Settings |
| 4 | The coin picker, with favorites starred |
| 5 | A finished Diagnostic Test of 100,000 flips |
| 6 | A run of four tails, with the streak shown |
| 7 | The About sheet |

### What it needs

- A phone connected over adb whose screen is 1080×2424, the Pixel 9a's, at the default font size.
  Set `ANDROID_SERIAL` when more than one device is connected.
- Android Studio, for its Pixel 9a device frame. It is looked for under `/opt/android/studio`, or
  wherever `ANDROID_STUDIO` points.
- The Android SDK (`ANDROID_HOME`) with build-tools, for `zipalign` and `apksigner`, and the debug
  keystore that any debug build creates in `~/.android`.
- ImageMagick built with WebP support, and Python 3.

`LANGS="en-US de-DE"` limits a run to those languages. `OUT=some/dir` writes somewhere other than
`screenshots/`.

### What it does to the phone

It never touches an installed copy of the app. It builds its own, `com.banasiak.coinflip.screenshots`,
into `build/screenshots`, installs it beside the real one, and uninstalls it at the end. Each shot's
state is written straight into that copy's preferences, so the counts, the streak and the favorites
are the same on every run.

While it runs, it switches the phone to dark mode and turns on demo mode, which fixes the clock at
5:00 and draws full signal. It sets the copy's language through Android's per-app language setting,
and finds its way around the app by labels it reads from the app's own translations. When it exits,
including on a failure, dark mode and the demo mode settings go back to what they were.

The Diagnostics shot comes from a release build of the copy, installed over the debug build it was
set up with. A debuggable build can run interpreted, which shows the test taking about three times
as long as a real install does.

### When a run goes wrong

- **The status bar changed part-way through.** Every shot's status bar has to match a reference
  set: one already in `screenshots/` that the run is not retaking, English first, or else the
  first language the run takes. The run stops at the first language that does not match, keeping
  the languages already finished. A notification is the usual cause, such as GrapheneOS's notice
  to reboot after an update. Clear it and rerun the remaining languages with `LANGS`, which are
  then checked against the ones already done. `FORCE=1` keeps a language regardless.
- **A dump of the screen was killed.** Android allows one UI automation connection at a time, so
  running `uiautomator`, or anything that uses it, during a run kills the run's own dump. The run
  retries a few times, but leave the phone to it.
- **The battery is drawn green and charging.** Some builds, GrapheneOS's among them, draw a
  plugged-in phone as charging whatever demo mode says. Unplug the cable and run over wireless
  debugging for a plain battery. Either is fine, as long as a whole set is taken the same way.
- **Notification icons show.** GrapheneOS's demo mode does not hide them, so clear what can be
  cleared before a run.
- **The status bar still shows a full battery afterwards.** SystemUI keeps the demo battery until
  the next real battery change. Unplugging or plugging the cable in refreshes it.

### Play's rules on screenshots

Play's documentation asks for phone screenshots in JPEG or 24-bit PNG with no alpha channel, with
the long side no more than twice the short side. These framed screenshots break both rules: they
are 1224×2570, a ratio of 2.1, with a transparent background. Play accepted them anyway, and the
live listing served them with their transparency as of October 2026. If an upload is ever
rejected, flatten the frames onto a solid background and pad them to 2:1.

The screenshots are stored in Git LFS (see `.gitattributes`), so a clone needs `git-lfs` installed
to get the images rather than pointer files.

## The icon

`ic_launcher-playstore.png` comes from Android Studio's Image Asset wizard. That wizard also writes
a new copy into `coinflip/src/main/` every time it regenerates the launcher icons. Move that copy
here, replacing this one.
