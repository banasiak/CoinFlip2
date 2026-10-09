// Renders the app's five sound effects from SOUNDS in synth.mjs into coinflip/src/main/res/raw.
//
//   node sounds.mjs                  write spin, coin, streak, powerup and oneup .ogg into res/raw
//   node sounds.mjs --out some/dir   ...or somewhere else, with a .wav of each beside it
//
// ffmpeg is taken from $FFMPEG, else from the PATH, and needs libvorbis.

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { SOUNDS, render, wav } from './synth.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const ffmpeg = process.env.FFMPEG ?? 'ffmpeg';

// integrated loudness of the sounds these replaced, measured before they were removed, so the app
// plays exactly as loud as it did
const TARGET_LUFS = { coin: -19.8, powerup: -17.6, oneup: -17.0, streak: -18.8 };
// spin replaced nothing, and plays straight into the coin: it takes the coin's gain, so the two keep
// the balance they were mixed to
const SAME_GAIN_AS = { spin: 'coin' };
const CEILING_DBTP = -1.0;
// SoundPool decodes each sound into a 1 MiB buffer and silently drops what does not fit
// (kDefaultHeapSize in its Sound.cpp): 5.46 s of 48 kHz 16-bit stereo, reverb tail included
const SOUNDPOOL_BYTES = 1024 * 1024;

function run(args) {
  const r = spawnSync(ffmpeg, ['-hide_banner', '-nostdin', ...args], { encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`ffmpeg ${args.join(' ')}\n${r.stderr}`);
  return r.stderr;
}

// ebur128's summary is the last "I:" and the last "Peak:" it prints
function measure(file) {
  const log = run(['-i', file, '-af', 'ebur128=peak=true', '-f', 'null', '-']);
  const last = (re) => Number([...log.matchAll(re)].pop()[1]);
  return { lufs: last(/I:\s+(-?[\d.]+) LUFS/g), peak: last(/Peak:\s+(-?[\d.]+) dBFS/g) };
}

const argv = process.argv.slice(2);
const outArg = argv.indexOf('--out');
const out = outArg >= 0 ? path.resolve(argv[outArg + 1]) : path.resolve(here, '../coinflip/src/main/res/raw');
const scratch = outArg >= 0 ? out : fs.mkdtempSync(path.join(here, 'out-'));
fs.mkdirSync(out, { recursive: true });

try {
  const rendered = Object.entries(SOUNDS).map(([name, design]) => {
    const [L, R] = render(design());
    if (L.length * 4 > SOUNDPOOL_BYTES) {
      throw new Error(`${name} runs ${(L.length / 48000).toFixed(2)} s, and SoundPool keeps only ${(SOUNDPOOL_BYTES / 4 / 48000).toFixed(2)} s`);
    }
    const raw = path.join(scratch, `${name}.wav`);
    fs.writeFileSync(raw, wav(L, R));
    return { name, L, raw, before: measure(raw) };
  });
  const gains = {};
  // the sounds with targets first, so a sound that borrows another's gain finds it set
  rendered.sort((a, b) => (a.name in SAME_GAIN_AS) - (b.name in SAME_GAIN_AS));
  for (const { name, L, raw, before } of rendered) {
    // loudness first, then never closer than the ceiling to clipping
    const level = name in SAME_GAIN_AS ? gains[SAME_GAIN_AS[name]] : TARGET_LUFS[name] - before.lufs;
    const gain = Math.min(level, CEILING_DBTP - before.peak);
    gains[name] = gain;
    const ogg = path.join(out, `${name}.ogg`);
    run([
      '-y', '-i', raw, '-af', `volume=${gain.toFixed(2)}dB`,
      '-c:a', 'libvorbis', '-q:a', '6', '-ar', '48000',
      '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact',
      ogg,
    ]);
    const after = measure(ogg);
    const secs = (L.length / 48000).toFixed(2);
    const target = name in SAME_GAIN_AS ? `${SAME_GAIN_AS[name]}'s gain` : `target ${TARGET_LUFS[name]}`;
    console.log(`${name.padEnd(8)} ${secs} s  ${after.lufs} LUFS (${target})  peak ${after.peak} dBFS  ${fs.statSync(ogg).size} bytes`);
  }
} finally {
  if (scratch !== out) fs.rmSync(scratch, { recursive: true, force: true });
}
