# Handoff: promo video, retimed cut

Written for the next session, which runs locally. Everything is on branch `ccr-8fbc6c4b-8f90x9`;
read `README.md` in this directory first for what the generator is and how its pieces fit.

## Where things stand

| Commit | What |
|---|---|
| `eb9c536` | The generator: `sizzle.js` (picture), `audio.mjs` (soundtrack), `render.mjs` (driver) |
| `4948601` | The owner's six tweaks: slower pacing, new copy, a timeline of section starts |
| next one | Parallel rendering with a faster capture, this document |

**The retimed 41 s cut has never been rendered in full.** The last complete render was the
original 33 s cut, which the owner reviewed. The owner does not want renders run in the cloud
session, which is why this one moved here. Everything since was checked this way:

- **Retiming:** stills at 0.4, 1.6, 3.6, 9.0, 12.5, 14.8, 17.3, 20.8, 24.2, 26.5, 29.6 and 39.5 s
  all showed the right content at the right time.
- **Soundtrack:** synthesized alone (`--audio`). It is 41.0 s long and every section sits between
  −10.8 and −13.6 dB RMS, so nothing jumps out or drops away.
- **Fast capture:** pixel-identical to Playwright's screenshot on two stills.
- **Not yet run:** the parallel path in `render.mjs`, with its interleaved workers, in-order
  feed to ffmpeg and backpressure window. It has only been syntax-checked.

## First job: render, check, deliver

```bash
git fetch origin && git checkout ccr-8fbc6c4b-8f90x9
cd promo
npm install
npx playwright install chromium     # only if Playwright has no Chromium yet
node render.mjs                     # needs ffmpeg with libx264 on PATH (or FFMPEG=/path/to/ffmpeg)
```

The measured costs were ~30 ms to draw a frame and ~115 ms to capture it, on one core of the
cloud container. At that rate one worker would take ~5 min for the 2,050 frames, and several
workers should take less. x264 at `-preset slow` may become the limit once capture is parallel.
If a run fails or looks wrong, try `--jobs 1` first, to tell a parallelism bug from a drawing one.

Then check it before handing it over:

```bash
ffprobe out/sizzle.mp4          # expect 41.00 s, 1920x1080, 50 fps, yuv420p bt709, AAC 48 kHz stereo
ffmpeg -i out/sizzle.mp4 -map 0:a -af ebur128=peak=true -f null -   # expect I ≈ -14 LUFS, true peak ≤ -1.5 dBFS
mkdir -p out/review && ffmpeg -i out/sizzle.mp4 -vf "fps=4,scale=480:270" out/review/f-%03d.png
```

Look at the review frames in order (a contact sheet of 36 per page works well). Things worth
confirming:

- The frame count is exactly 2,050, with no duplicated or dropped frames around the worker
  boundaries. Frame *n* came from worker *n* mod *jobs*, so an ordering bug shows up as periodic
  jitter.
- Each caption in the phone section (6–15.5 s) has settled for about 3 s before the next one.
- The Random coin (19.5–23 s) changes coin at 20.5, 21.25 and 22.0 s.
- The custom-coin result reads TAILS at 28 s, TACOS from 29.4 s, and PIZZA from 31 s.
- The end card reads "Free. No ads. No tracking. Open source."

Deliver `out/sizzle.mp4` to the owner. Nothing under `out/` is committed.

## The cut

Every section is timed from its own start, which is set near the top of the timeline in `sizzle.js`:

| Starts | Constant | Section |
|---|---|---|
| 0 | (none) | Hook: "Heads or Tails?", a coin tossed in lands TAILS, then "Let the coin decide!" |
| 4 | (none) | Brand card: icon and "Simple Coin Flip" |
| 6 | `TAP` | Phone main screen: tap to flip, shake to flip, keep score |
| 15.5 | `WALL_AT` | 84-tile wall, the count to 82, then four category highlights |
| 19.5 | `RANDOM_AT` | "Or go Random.": four coins in turn |
| 23 | `CUSTOM_AT` | Custom coin: crop a pizza, crop a taco, flip, rename the sides to PIZZA / TACOS |
| 32 | `STREAK_AT` | Streak ×7 → ×10, a burst of coins and the fanfare. **The owner called this "perfect"; leave it alone** |
| 35.5 | `THEMES_AT` | Theme cycle with five feature chips |
| 38 | `END` | End card, then a fade to black at 41 |

The soundtrack is arranged around the same marks: `sizzle.js` exports `MARKS` and `audio.mjs`
takes them. So retiming a section needs no audio edits. The grid is 120 bpm, so keep section
starts and flip landings on multiples of 0.5 s.

## Feedback so far

The owner liked the first cut and asked for: title case "Heads or Tails?"; "Let the coin
decide!"; a slower phone section that pauses for reading; a much slower Random; a somewhat
slower custom coin; the streak unchanged; and the new end-card line. All of that is in `4948601`.
Holding the "82 coins to flip" count about 0.5 s longer was added unasked, because it was on
screen for barely a third of a second.

## Things that will bite

- **Do not use the app's own sounds.** `res/raw/coin.ogg`, `powerup.ogg`, `oneup.ogg` and
  `streak.ogg` are the Super Mario Bros. coin, power-up, 1-UP and course-clear sounds, confirmed
  by pitch analysis. A public YouTube video would draw a copyright claim. Everything in
  `audio.mjs` is synthesized.
- **No session has listened to the soundtrack.** It has only been measured. It is a placeholder
  bed the owner may want to replace; swapping in a different track is a matter of muxing new
  audio over the video stream.
- **The phone screens are redrawn, not recorded.** They use the Compose layouts' dp values and
  the app's color schemes, so a UI change in the app will not show up here on its own.
- **The end-card claims were checked against the build.** There is no ad or analytics SDK, the
  only permission requested is `VIBRATE` (not even `INTERNET`), and the code is under the
  Unlicense. Re-check them if dependencies change.
- **Color emoji take the canvas fill's alpha.** The pizza came out translucent until `emoji()`
  set an opaque `fillStyle`.
- **Playwright's `page.screenshot` is ~5× slower than Chromium's fast PNG** for identical pixels,
  because it compresses the PNG much harder. Keep `capture()` on the CDP call.
- **ffmpeg converts to BT.709 explicitly.** Without that, it converts RGB with BT.601, and HD
  players show the colors shifted.
- **The video runs at 50 fps on purpose.** The app advances the flip every 20 ms, so each video
  frame is exactly one app frame.

## After delivery

Google Play takes a YouTube URL, not a file. The owner uploads the MP4 as public or unlisted,
with ads off and no age restriction, and pastes the link into the Video field of the main store
listing. Nothing about that step belongs in this repository.
