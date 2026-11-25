# Promo video

The Google Play promo video, rendered from the app's own artwork rather than edited by hand: the
coin faces come straight out of `coinflip/src/main/res/drawable`, the catalog is read out of
`CoinType.kt`, and the flip is the frame sequence `AnimationHelper` builds, one to each video frame
at 60 fps. That is the rate a phone shows them at, one 16.7 ms vsync apiece rather than the 20 ms
the drawable declares, so the video's flip runs at the app's speed. Change the catalog or the
artwork and a re-render picks it up. The one coin never shown is
Claude Code, an easter egg rather than a real coin (`OFF_WALL` in `sizzle.js`). The catalog count
still includes it, so the number on screen matches what the app ships.

The logo is `logo.png`, the app icon's foreground, which `store/scripts/icon.py` writes along with
the icon itself. Rerun that script, not anything here, to change it.

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
after `sounds.mjs`, commit only the files whose design changed and restore the rest. Playwright
drives a headless Chromium, `$CHROMIUM` if it is set, else the one Playwright installs for itself;
[The browser](#the-browser) says which to use.

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
  matched to the loudness of the sound it replaced, which the app still offers as its Classic set,
  so switching sets leaves the volume where it was; `spin` replaced nothing and takes the coin's
  gain. Its swells fall on the flip's edge-on frames: the app's copy is timed to the
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

## The browser

`npx playwright install chromium` downloads the Chromium build Playwright 1.56.1 expects,
141.0.7390.37, but Playwright checks neither a checksum nor a signature on it. A pinned and verified
browser comes from Debian instead, and `render.mjs` takes it from `$CHROMIUM`:

- **The packages** are `chromium-headless-shell` and `chromium-common` 141.0.7390.122-1, and
  `libjpeg62-turbo` 2.1.5-4, from `sid` in the `20251023T024602Z` snapshot on snapshot.debian.org,
  whose URLs never change.
- **They are verified through Debian's signatures.** `gpgv` checks that snapshot's
  `dists/sid/InRelease` against `/usr/share/keyrings/debian-archive-keyring.gpg`, from Ubuntu's
  `debian-archive-keyring` package. The SHA-256 of `main/binary-amd64/Packages.xz` comes from the
  signed text alone (`gpgv --output`), and each `.deb`'s SHA-256 from that index:

  | Package | SHA-256 |
  |---|---|
  | `chromium-headless-shell_141.0.7390.122-1_amd64.deb` | `242a50bb9d88e122677357785098f2b627238b83d1b7743b0ec887ab7217b348` |
  | `chromium-common_141.0.7390.122-1_amd64.deb` | `842ec5e0b64d6b42d5ea91d722da3105da33f22a5bb3e9f75c8626b411c9e8ff` |
  | `libjpeg62-turbo_2.1.5-4_amd64.deb` | `5504299ad0e3c0a9ca9f962a68a650a040db52f9f416a109e74df48d55601d3f` |
  | `chromium-sandbox_141.0.7390.122-1_amd64.deb` | `338d0fa0448e03dcae9fe1db1311ac5246ac4bfddf680a02e1343503729b5a2a` |

- **They are unpacked, not installed,** with `dpkg-deb -x` into a directory of their own. On Ubuntu,
  apt refuses the Chromium packages, because `libjpeg62-turbo` is a Debian name; Ubuntu's
  `libjpeg62` is the original libjpeg 6b and lacks the `jpeg_crop_scanline` that Chromium calls.
  Every other library they need is in Ubuntu's archive; `ldd` on
  `usr/lib/chromium/chromium-headless-shell` lists any still missing.
- **The sandbox helper is the one file installed as root.** `render.mjs` launches Chromium with its
  sandbox on, and Ubuntu 23.10 and later let AppArmor deny it the user namespaces it would otherwise
  use, so Chromium falls back to the setuid helper in `chromium-sandbox`. That one binary,
  `usr/lib/chromium/chrome-sandbox`, goes to a root-owned directory outside the home directory, with
  `sudo install -o root -g root -m 4755`, never into the user-writable unpacked tree. The package
  has no maintainer scripts and nothing else of consequence. The table's hash is the package's; the
  helper itself, as installed, has SHA-256
  `1ade996325c4be78e3946d6d5dc09ae953194e8211fce8c15a6d3bd02b0145ab`, which matches its MD5 in the
  package's own `DEBIAN/md5sums`.
- **`$CHROMIUM` names a short launcher** that sets `CHROME_DEVEL_SANDBOX` to the helper, puts the
  unpacked `usr/lib/x86_64-linux-gnu` on `LD_LIBRARY_PATH` and execs
  `usr/lib/chromium/chromium-headless-shell`. Without the helper, Chromium stops with "No usable
  sandbox!".

It is a patch release newer than Playwright's own build. A full render with it passed the checks
below.

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
