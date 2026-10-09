// The synthesizer behind both the promo video's soundtrack and the app's own sound effects. Every
// sound is built from oscillators and noise, nothing sampled, so all of it is original.

export const SR = 48000;

// ---------------------------------------------------------------------------------------------
// primitives

export function noiseGen(seed) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return (s / 4294967296) * 2 - 1;
  };
}

export const hz = (midi) => 440 * 2 ** ((midi - 69) / 12);

export function buffer(seconds) {
  return new Float32Array(Math.max(1, Math.ceil(seconds * SR)));
}

// Chamberlin state-variable filter; returns the chosen output per sample
export function svf(input, cutoff, q = 0.7, mode = 'band') {
  const out = new Float32Array(input.length);
  let low = 0;
  let band = 0;
  for (let i = 0; i < input.length; i++) {
    const fc = typeof cutoff === 'function' ? cutoff(i / SR) : cutoff;
    const f = 2 * Math.sin((Math.PI * Math.min(fc, SR / 6)) / SR);
    low += f * band;
    const high = input[i] - low - band / q;
    band += f * high;
    out[i] = mode === 'low' ? low : mode === 'high' ? high : band;
  }
  return out;
}

export function envelope(sig, attack, release) {
  const a = Math.max(1, attack * SR);
  const r = Math.max(1, release * SR);
  for (let i = 0; i < sig.length; i++) {
    let g = 1;
    if (i < a) g = i / a;
    const left = sig.length - i;
    if (left < r) g *= left / r;
    sig[i] *= g;
  }
  return sig;
}

// ---------------------------------------------------------------------------------------------
// instruments

export function kick() {
  const s = buffer(0.45);
  let ph = 0;
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    ph += (2 * Math.PI * (44 + 120 * Math.exp(-t / 0.028))) / SR;
    s[i] = Math.sin(ph) * Math.exp(-t / 0.22) + (i < 90 ? 0.4 * Math.exp(-i / 20) : 0);
  }
  return s;
}

export function clap(seed) {
  const n = noiseGen(seed);
  const raw = buffer(0.25);
  for (let i = 0; i < raw.length; i++) {
    const t = i / SR;
    const bursts = [0, 0.011, 0.022].reduce((acc, b) => acc + (t >= b ? Math.exp(-(t - b) / 0.006) : 0), 0);
    raw[i] = n() * (0.6 * bursts + Math.exp(-t / 0.11));
  }
  return svf(raw, 1300, 1.1, 'band');
}

export function hat(seed, open = false) {
  const n = noiseGen(seed);
  const raw = buffer(open ? 0.25 : 0.06);
  for (let i = 0; i < raw.length; i++) raw[i] = n() * Math.exp(-i / SR / (open ? 0.08 : 0.018));
  return svf(raw, 9000, 0.8, 'high');
}

export function snare(seed) {
  const n = noiseGen(seed);
  const s = buffer(0.2);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = 0.7 * n() * Math.exp(-t / 0.07) + 0.5 * Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t / 0.05);
  }
  return svf(s, 3500, 0.6, 'low');
}

export function crash(seed) {
  const n = noiseGen(seed);
  const raw = buffer(2.2);
  for (let i = 0; i < raw.length; i++) raw[i] = n() * Math.exp(-i / SR / 0.7);
  return svf(raw, 6500, 0.7, 'high');
}

export function whoosh(len, f0, f1, seed, shape = 1.5) {
  const n = noiseGen(seed);
  const raw = buffer(len);
  for (let i = 0; i < raw.length; i++) raw[i] = n();
  const out = svf(raw, (t) => f0 * (f1 / f0) ** (t / len), 1.4, 'band');
  for (let i = 0; i < out.length; i++) out[i] *= Math.sin((Math.PI * i) / out.length) ** shape;
  return out;
}

// struck metal: a bright transient and four inharmonic partials ringing out at different rates
export function ting(f, seed) {
  const n = noiseGen(seed);
  const s = buffer(0.9);
  const partials = [[1, 1, 0.5], [2.32, 0.42, 0.28], [4.25, 0.22, 0.16], [6.63, 0.12, 0.09]];
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    let v = 0;
    for (const [r, a, d] of partials) v += a * Math.sin(2 * Math.PI * f * r * t) * Math.exp(-t / d);
    s[i] = v + (i < 200 ? 0.5 * n() * Math.exp(-i / 40) : 0);
  }
  return envelope(s, 0.001, 0.05);
}

export function ping(f, len = 0.35) {
  const s = buffer(len);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = (Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(4 * Math.PI * f * t)) * Math.exp(-t / (len / 4));
  }
  return envelope(s, 0.002, 0.02);
}

export function click(seed) {
  const n = noiseGen(seed);
  const s = buffer(0.03);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = 0.6 * n() * Math.exp(-t / 0.002) + 0.5 * Math.sin(2 * Math.PI * 2400 * t) * Math.exp(-t / 0.006);
  }
  return s;
}

export function bubble() {
  const s = buffer(0.09);
  let ph = 0;
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    ph += (2 * Math.PI * (420 + 900 * (t / 0.09))) / SR;
    s[i] = Math.sin(ph) * Math.sin((Math.PI * i) / s.length);
  }
  return s;
}

export function pluck(f, len = 0.5) {
  const s = buffer(len);
  for (let k = 1; k <= 8; k++) {
    if (f * k > 14000) break;
    const a = 1 / k ** 1.3;
    const d = 5 + 4 * k;
    const w = (2 * Math.PI * f * k) / SR;
    for (let i = 0; i < s.length; i++) s[i] += a * Math.sin(w * i) * Math.exp((-d * i) / SR);
  }
  return envelope(s, 0.002, 0.03);
}

export function bass(f, len) {
  const s = buffer(len);
  const w = (2 * Math.PI * f) / SR;
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    const e = 0.6 + 0.4 * Math.exp(-t / 0.06);
    s[i] = e * (Math.sin(w * i) + 0.35 * Math.sin(2 * w * i) + 0.12 * Math.sin(3 * w * i));
  }
  return envelope(s, 0.004, 0.03);
}

// band-limited saws, two voices a few cents apart, with the upper harmonics rolled off
export function pad(freqs, len, attack = 0.25, release = 0.5) {
  const s = buffer(len);
  for (const f of freqs) {
    for (const det of [-0.004, 0.004]) {
      const fd = f * (1 + det);
      for (let k = 1; k <= 10; k++) {
        if (fd * k > 9000) break;
        const a = 1 / k ** 1.7;
        const w = (2 * Math.PI * fd * k) / SR;
        const ph = k * det * 1000;
        for (let i = 0; i < s.length; i++) s[i] += a * Math.sin(w * i + ph);
      }
    }
  }
  return envelope(s, attack, release);
}

// a brass-ish lead: a saw whose brightness swells in over the first 80 ms, vibrato on long notes
export function brass(f, len) {
  const s = buffer(len);
  for (let k = 1; k <= 14; k++) {
    if (f * k > 12000) break;
    let ph = 0;
    for (let i = 0; i < s.length; i++) {
      const t = i / SR;
      const bright = 0.15 + 0.5 * Math.exp(-t / 0.05);
      const vib = t > 0.25 ? 1 + 0.004 * Math.sin(2 * Math.PI * 5.5 * t) : 1;
      ph += (2 * Math.PI * f * k * vib) / SR;
      s[i] += (Math.exp(-k * bright) / k) * Math.sin(ph);
    }
  }
  return envelope(s, 0.02, Math.min(0.12, len / 3));
}

// a band-limited pulse wave. Harmonic n of a pulse with duty d has amplitude sin(pi n d) / n, so a
// duty of 0.5 is a square (odd harmonics only) and 0.25 loses every fourth harmonic: the two timbres
// 8-bit consoles were built on. The top is rolled off so it reads as a pulse, not a buzz
export function pulse(f, len, duty, decay = Infinity) {
  const s = buffer(len);
  for (let k = 1; k <= 40; k++) {
    const fk = f * k;
    if (fk > 12000) break;
    const a = Math.sin(Math.PI * k * duty) / k / (1 + (fk / 7000) ** 2);
    if (Math.abs(a) < 1e-4) continue;
    const w = (2 * Math.PI * fk) / SR;
    for (let i = 0; i < s.length; i++) s[i] += a * Math.sin(w * i);
  }
  if (Number.isFinite(decay)) for (let i = 0; i < s.length; i++) s[i] *= Math.exp(-i / SR / decay);
  return envelope(s, 0.002, 0.004);
}

// ---------------------------------------------------------------------------------------------
// Freeverb, abridged

export function reverb(inL, inR, room = 0.84, damp = 0.25) {
  const scale = SR / 44100;
  const combs = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617];
  const aps = [556, 441, 341, 225];
  const run = (input, spread) => {
    const out = new Float32Array(input.length);
    for (const c of combs) {
      const buf = new Float32Array(Math.round((c + spread) * scale));
      let idx = 0;
      let store = 0;
      for (let i = 0; i < input.length; i++) {
        const y = buf[idx];
        store = y * (1 - damp) + store * damp;
        buf[idx] = input[i] * 0.015 + store * room;
        out[i] += y;
        if (++idx >= buf.length) idx = 0;
      }
    }
    for (const a of aps) {
      const buf = new Float32Array(Math.round((a + spread) * scale));
      let idx = 0;
      for (let i = 0; i < out.length; i++) {
        const b = buf[idx];
        const y = -out[i] + b;
        buf[idx] = out[i] + b * 0.5;
        out[i] = y;
        if (++idx >= buf.length) idx = 0;
      }
    }
    return out;
  };
  return [run(inL, 0), run(inR, 23)];
}

export function wav(L, R) {
  const n = L.length;
  const buf = Buffer.alloc(44 + n * 4);
  buf.write('RIFF', 0);
  buf.writeUInt32LE(36 + n * 4, 4);
  buf.write('WAVEfmt ', 8);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(2, 22);
  buf.writeUInt32LE(SR, 24);
  buf.writeUInt32LE(SR * 4, 28);
  buf.writeUInt16LE(4, 32);
  buf.writeUInt16LE(16, 34);
  buf.write('data', 36);
  buf.writeUInt32LE(n * 4, 40);
  for (let i = 0; i < n; i++) {
    buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, L[i])) * 32767), 44 + i * 4);
    buf.writeInt16LE(Math.round(Math.max(-1, Math.min(1, R[i])) * 32767), 46 + i * 4);
  }
  return buf;
}

// ---------------------------------------------------------------------------------------------
// the app's five sound effects (coinflip/src/main/res/raw), each a list of voices: when it starts,
// the signal, its level, and how much of it goes to the reverb. The video plays the same spin and
// coin, and the opening of streak, so those are designed once, here

const PULSE_RUN = 1 / 30; // the power-up's note length: two 60 Hz frames, as on the console
const PULSE_STEP = 2 / 15; // the 1-up's: eight frames

// the flip shows the coin edge-on on its fifth frame and every eighth after. A phone shows each frame
// for 16.7 ms, not the 20 ms AnimationHelper declares
const APP_FRAME = 1 / 60;
// how far the app's swells sit ahead of the picture, for SoundPool's start latency. Both values are
// measurements; CLAUDE.md has them and how to take them again
const APP_LEAD = 0.222;
// the longest flip is 49 frames; the app stops the whir at the landing, before this runs out
const SPIN_LENGTH = 1.0;

// the record fanfare opens with the landing ting and a call, G C E G and E G. The app's fanfare
// answers the call; the video's streak section has no room for the whole fanfare, so it plays the
// call alone (shortFanfare)
const fanfareTing = (gain) => ({ t: 0, sig: ting(hz(91), 5), gain, wet: 0.35 });
const FANFARE_CALL = [[0, 67, 0.1], [0.1, 72, 0.1], [0.2, 76, 0.1], [0.3, 79, 0.45], [0.8, 76, 0.13], [0.95, 79, 0.13]];
const fanfareLine = (notes) => notes.map(([t, m, len]) => ({ t, sig: brass(hz(m), len), gain: 0.19, wet: 0.35 }));
const fanfareChord = (t, notes, len) => notes.map((m) => ({ t, sig: brass(hz(m), len), gain: 0.07, wet: 0.35 }));

/** The video's record fanfare: the ting, the call and the top C, over a C major chord. */
export function shortFanfare() {
  return [fanfareTing(0.3), ...fanfareLine([...FANFARE_CALL, [1.1, 84, 0.95]]), ...fanfareChord(1.1, [48, 55, 60, 64], 0.95)];
}

export const SOUNDS = {
  // the coin in the air: a soft whir that swells each time it turns edge-on, timed from the flip's
  // first frame. speed, frame and lead are the video's when it passes them: its streak flips faster
  // than the app's, its frame is its own, and it has no latency to lead
  spin: (speed = 1, seed = 11, { frame = APP_FRAME, lead = APP_LEAD } = {}) => {
    const w = whoosh(SPIN_LENGTH / speed, 1500, 900, seed, 0.6);
    // the middle of the edge-on frame
    const first = 4.5 * frame - lead;
    for (let i = 0; i < w.length; i++) {
      const edge = 0.5 + 0.5 * Math.cos((2 * Math.PI * ((i / SR) * speed - first)) / (8 * frame));
      w[i] *= 0.25 + 0.75 * edge ** 2;
    }
    return [{ t: 0, sig: w, gain: 0.24, wet: 0.2 }];
  },

  // struck metal, tuned to G6
  coin: (seed = 1) => [{ t: 0, sig: ting(hz(91), seed), gain: 0.34, wet: 0.35 }],

  // the ting, the call and the top C, answered over IV and V, up to a high E held over C major, with
  // a snare roll into the last chord and a timpani and a cymbal on it
  streak: () => [
    // as loud as a plain landing's ting: the rest of the fanfare pulls its loudness match down
    fanfareTing(0.42),
    ...fanfareLine([...FANFARE_CALL, [1.1, 84, 0.5], [1.7, 81, 0.1], [1.8, 77, 0.1], [1.9, 81, 0.1], [2.0, 84, 0.45], [2.5, 83, 0.13], [2.65, 86, 0.13], [2.8, 88, 1.3]]),
    ...fanfareChord(1.1, [48, 55, 60, 64], 0.55),
    ...fanfareChord(1.7, [53, 57, 60, 65], 0.8),
    ...fanfareChord(2.5, [55, 59, 62, 67], 0.3),
    ...fanfareChord(2.8, [48, 55, 60, 64, 67], 1.3),
    ...Array.from({ length: 6 }, (_, i) => ({ t: 2.5 + i * 0.05, sig: snare(40 + i), gain: 0.05 + 0.02 * i, wet: 0.2 })),
    { t: 2.8, sig: kick(), gain: 0.35, wet: 0.3 },
    { t: 2.8, sig: bass(hz(36), 1.2), gain: 0.18, wet: 0.2 },
    { t: 2.8, sig: crash(9), gain: 0.12, wet: 0.3 },
  ],

  // a 25% pulse running up I, IV, V and I in G, two frames a note, landing on a held G6 that the
  // coin's ting rings over
  powerup: () => {
    const runs = [[67, 71, 74, 79, 83], [72, 76, 79, 84, 88], [74, 78, 81, 86, 90], [79, 83, 86]];
    const notes = runs.flat();
    const voices = notes.map((m, i) => ({
      t: i * PULSE_RUN,
      sig: pulse(hz(m), PULSE_RUN, 0.25),
      gain: 0.16 * (0.7 + (0.3 * i) / notes.length),
      wet: 0.3,
    }));
    const end = notes.length * PULSE_RUN;
    voices.push({ t: end, sig: pulse(hz(91), 0.45, 0.25, 0.16), gain: 0.16, wet: 0.3 });
    voices.push({ t: end, sig: ting(hz(91), 3), gain: 0.08, wet: 0.35 });
    return voices;
  },

  // a square climbing the G major triad to G7, eight frames a note, each decaying by half before the
  // next. The top note is lifted: it is mostly fundamental, so it reads quieter than the notes below
  // it at the same level
  oneup: () => {
    const notes = [86, 91, 95, 98, 95, 103];
    return notes.map((m, i) => {
      const last = i === notes.length - 1;
      return { t: i * PULSE_STEP, sig: pulse(hz(m), last ? 0.5 : PULSE_STEP, 0.5, 0.19), gain: last ? 0.24 : 0.15, wet: 0.3 };
    });
  },
};

/** Mixes one sound's voices through the reverb, as the video's mix does, and trims the silence after. */
export function render(voices) {
  const n = Math.ceil((Math.max(...voices.map((v) => v.t + v.sig.length / SR)) + 2) * SR);
  const dry = new Float32Array(n);
  const send = new Float32Array(n);
  for (const v of voices) {
    const start = Math.round(v.t * SR);
    for (let i = 0; i < v.sig.length && start + i < n; i++) {
      dry[start + i] += v.sig[i] * v.gain;
      send[start + i] += v.sig[i] * v.gain * v.wet;
    }
  }
  const [wl, wr] = reverb(send, send);
  const L = dry.map((d, i) => d + 1.6 * wl[i]);
  const R = dry.map((d, i) => d + 1.6 * wr[i]);
  // the reverb tail ends where it falls 60 dB below the peak, with a short fade so it never clicks
  let peak = 0;
  for (let i = 0; i < n; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
  let end = n;
  while (end > 0 && Math.abs(L[end - 1]) < peak / 1000 && Math.abs(R[end - 1]) < peak / 1000) end--;
  const fade = Math.round(0.03 * SR);
  for (let i = 0; i < fade; i++) {
    L[end - fade + i] *= 1 - i / fade;
    R[end - fade + i] *= 1 - i / fade;
  }
  return [L.slice(0, end), R.slice(0, end)];
}
