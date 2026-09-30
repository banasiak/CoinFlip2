# Promo video

The Google Play promo video, rendered from the app's own artwork rather than edited by hand: the
coin faces come straight out of `coinflip/src/main/res/drawable`, the catalog is read out of
`CoinType.kt`, and the flip is the frame sequence `AnimationHelper` builds, at the same 20 ms per
frame. Change the catalog or the artwork and a re-render picks it up.

```bash
cd promo
npm install
FFMPEG=/path/to/ffmpeg node render.mjs        # out/sizzle.mp4, 1920x1080 at 50 fps, ~33 s
node render.mjs --still 2.5,12.8              # single frames, as out/still-<t>.png
node render.mjs --audio                       # the soundtrack alone, and its buses, as out/stem-*.wav
```

`ffmpeg` is taken from `$FFMPEG`, else from the `PATH`, and needs `libx264`. Playwright drives a
headless Chromium; if its browser is not installed, `npx playwright install chromium`.

- `sizzle.js` draws every frame as a pure function of time, so any frame renders on its own. The
  timeline is pinned to a 120 bpm grid, and each flip is scheduled by the beat it lands on.
- `audio.mjs` synthesizes the soundtrack from the cues the page schedules. Nothing in it is
  sampled. The app's own `res/raw` sounds are Super Mario Bros. sound effects, which a public
  promo video cannot carry.
- The phone screens are redrawn from the Compose layouts' dp values and the app's color schemes,
  not screen-recorded, because nothing here runs an emulator. They are faithful to the layout
  but are a reconstruction, so re-check them after a UI change.

Google Play takes the video as a YouTube URL, not a file: upload `out/sizzle.mp4` to YouTube as a
public or unlisted video with ads off and no age restriction, then paste its URL into the Video
field of the app's main store listing in Play Console.
