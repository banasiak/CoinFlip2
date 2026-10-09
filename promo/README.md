# Promo video

The Google Play promo video, rendered from the app's own artwork rather than edited by hand: the
coin faces come straight out of `coinflip/src/main/res/drawable`, the catalog is read out of
`CoinType.kt`, and the flip is the frame sequence `AnimationHelper` builds, one to each video frame
at 60 fps. That is the rate a phone shows them at, one 16.7 ms vsync apiece rather than the 20 ms
the drawable declares, so the video's flip runs at the app's speed. Change the catalog or the
artwork and a re-render picks it up. The one coin never shown is
Claude Code, an easter egg rather than a real coin (`OFF_WALL` in `sizzle.js`). The catalog count
still includes it, so the number on screen matches what the app ships.

```bash
cd promo
npm ci --ignore-scripts
FFMPEG=/path/to/ffmpeg node render.mjs        # out/sizzle.mp4, 1920x1080 at 60 fps
node render.mjs --jobs 2                      # the same with 2 workers (default: one per core)
node render.mjs --still 2.5,12.8              # single frames, as out/still-<t>.png
node render.mjs --audio                       # the soundtrack alone, and its buses, as out/stem-*.wav
FFMPEG=/path/to/ffmpeg node sounds.mjs         # the app's sound effects, into coinflip/src/main/res/raw
```

`ffmpeg` is taken from `$FFMPEG`, else from the `PATH`, and needs `libx264` for the video and
`libvorbis` for the sounds. Different ffmpeg builds encode the same sound to different bytes, so
after `sounds.mjs`, commit only the files whose design changed and restore the rest. Playwright drives a headless Chromium; if its browser is not installed,
`npx playwright install chromium`.

- `sizzle.js` draws every frame as a pure function of time, so any frame renders on its own. The
  timeline is pinned to a 120 bpm grid, and each flip is scheduled by the beat it lands on.
  Sections are timed from their own start (`BRAND_AT`, `TAP`, ... `END` near the top of the
  timeline), so lengthening one moves everything after it, soundtrack included. Keep section
  starts and flip landings on multiples of 0.5 s.
- That independence is what `render.mjs` parallelizes: workers take interleaved frames and one
  encoder takes them in order. Drawing a frame costs ~30 ms; the capture is most of the rest,
  which is why it uses Chromium's fast PNG rather than Playwright's screenshot (~115 ms against
  ~625 ms, identical pixels). Renders with different `--jobs` are not byte-identical, and that is
  not a frame-order bug: a handful of frames in the streak burst differ by a few hundred pixels on
  one small particle, because Chromium's cache of downscaled images depends on what the page drew
  before. It reproduces with no parallelism at all, and every other frame matches exactly.
- The streak's flips are the one exception to the app's frame sequence. They run faster than the
  app's, back to back, where stepping through the app's frames strobes between a couple of widths,
  so that coin turns continuously instead, with each video frame blurred across its whole length
  (`drawSpin`).
- Fonts are bundled, never taken from the system. Roboto and Noto Color Emoji both come from
  pinned Fontsource packages, and `setup()` refuses to render if the emoji font did not load. The
  pizza and taco "photos" are emoji, and on a machine with no emoji font of its own they drew as
  empty boxes.
- `audio.mjs` synthesizes the soundtrack from the cues the page schedules, with the instruments in
  `synth.mjs`. Nothing in it is sampled.
- `sounds.mjs` renders the app's five sound effects into `res/raw` from `SOUNDS` in `synth.mjs`. The
  video's flip whir and landing ting are the app's `spin` and `coin`, so the two can never drift
  apart. Its record fanfare is only the opening of `streak` (`shortFanfare`), because the app's ~5 s
  fanfare would overrun the streak section. `sounds.mjs` refuses to write a sound longer than
  SoundPool holds, 5.46 s, since anything past that is cut off without a word. Each landing sound is
  matched to the loudness of the original sound it stands in for, which the app still offers as its
  Classic set, so switching sets leaves the volume where it was; `spin` replaced nothing and takes
  the coin's gain. Its swells fall on the flip's edge-on frames: the app's copy is timed to the
  frames a phone actually shows and set ahead of them for the delay before a sound starts. The
  video's copy is the same design on the same frames, with no lead, since the video has no start
  latency. The video cuts it at each landing, where the app stops it.
- The phone screens are redrawn from the Compose layouts' dp values and the app's color schemes,
  not screen-recorded, because nothing here runs an emulator. They are faithful to the layout
  but are a reconstruction, so re-check them after a UI change.
- The catalog is parsed from `CoinType.kt` by a regex that expects each entry on one line as
  `NAME("prefix", "Name", GROUP)`. Change that constructor's shape and the wall silently loses
  coins, with no build failure to say so.
- The end card claims "Free. No ads. No tracking. Open source." Each claim was checked against the
  build: there is no ad or analytics SDK, and the only system permission is `VIBRATE` (not even
  `INTERNET`). The merged manifest's one other entry is androidx's app-private
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. The source is under the Unlicense. Re-check the
  claims when the app's dependencies or permissions change.

## Checking a render

```bash
ffprobe out/sizzle.mp4                                            # 1920x1080, 60 fps, yuv420p bt709, AAC 48 kHz stereo
ffmpeg -i out/sizzle.mp4 -map 0:a -af ebur128=peak=true -f null -  # I ≈ -14 LUFS, true peak ≤ -1.5 dBFS
mkdir -p out/review && ffmpeg -i out/sizzle.mp4 -vf "fps=4,scale=480:270,tile=4x4" out/review/sheet-%02d.png
```

The contact sheets are the quickest way to review a cut: every caption should settle before the
next one arrives, and every result should read the face the coin landed on. If a render looks
wrong, re-run it with `--jobs 1` to tell a parallelism bug from a drawing one. Nothing under `out/`
is committed.

## Publishing

Google Play takes the video as a YouTube URL, not a file: upload `out/sizzle.mp4` to YouTube as a
public or unlisted video with ads off and no age restriction, then paste its URL into the Video
field of the app's main store listing in Play Console.
