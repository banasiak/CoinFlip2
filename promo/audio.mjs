// The soundtrack, synthesized from nothing so that every sound in the video is original.
//
// The app's own sound effects are deliberately not used here: coin.ogg, powerup.ogg, oneup.ogg and
// streak.ogg are Super Mario Bros. sounds, which a public promo video cannot carry.
//
// A 120 bpm bed in C major (C G Am F) runs under the cues sizzle.js schedules: every landing gets a
// struck-metal ting, every tap a click, every cut a whoosh, and the record streak an original fanfare.

const SR = 48000;
const BPM = 120;
const BEAT = 60 / BPM;

// ---------------------------------------------------------------------------------------------
// primitives

function noiseGen(seed) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return (s / 4294967296) * 2 - 1;
  };
}

const hz = (midi) => 440 * 2 ** ((midi - 69) / 12);

function buffer(seconds) {
  return new Float32Array(Math.max(1, Math.ceil(seconds * SR)));
}

// Chamberlin state-variable filter; returns the chosen output per sample
function svf(input, cutoff, q = 0.7, mode = 'band') {
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

function envelope(sig, attack, release) {
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

function kick() {
  const s = buffer(0.45);
  let ph = 0;
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    ph += (2 * Math.PI * (44 + 120 * Math.exp(-t / 0.028))) / SR;
    s[i] = Math.sin(ph) * Math.exp(-t / 0.22) + (i < 90 ? 0.4 * Math.exp(-i / 20) : 0);
  }
  return s;
}

function clap(seed) {
  const n = noiseGen(seed);
  const raw = buffer(0.25);
  for (let i = 0; i < raw.length; i++) {
    const t = i / SR;
    const bursts = [0, 0.011, 0.022].reduce((acc, b) => acc + (t >= b ? Math.exp(-(t - b) / 0.006) : 0), 0);
    raw[i] = n() * (0.6 * bursts + Math.exp(-t / 0.11));
  }
  return svf(raw, 1300, 1.1, 'band');
}

function hat(seed, open = false) {
  const n = noiseGen(seed);
  const raw = buffer(open ? 0.25 : 0.06);
  for (let i = 0; i < raw.length; i++) raw[i] = n() * Math.exp(-i / SR / (open ? 0.08 : 0.018));
  return svf(raw, 9000, 0.8, 'high');
}

function snare(seed) {
  const n = noiseGen(seed);
  const s = buffer(0.2);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = 0.7 * n() * Math.exp(-t / 0.07) + 0.5 * Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t / 0.05);
  }
  return svf(s, 3500, 0.6, 'low');
}

function crash(seed) {
  const n = noiseGen(seed);
  const raw = buffer(2.2);
  for (let i = 0; i < raw.length; i++) raw[i] = n() * Math.exp(-i / SR / 0.7);
  return svf(raw, 6500, 0.7, 'high');
}

function whoosh(len, f0, f1, seed, shape = 1.5) {
  const n = noiseGen(seed);
  const raw = buffer(len);
  for (let i = 0; i < raw.length; i++) raw[i] = n();
  const out = svf(raw, (t) => f0 * (f1 / f0) ** (t / len), 1.4, 'band');
  for (let i = 0; i < out.length; i++) out[i] *= Math.sin((Math.PI * i) / out.length) ** shape;
  return out;
}

// struck metal: a bright transient and four inharmonic partials ringing out at different rates
function ting(f, seed) {
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

function ping(f, len = 0.35) {
  const s = buffer(len);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = (Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(4 * Math.PI * f * t)) * Math.exp(-t / (len / 4));
  }
  return envelope(s, 0.002, 0.02);
}

function click(seed) {
  const n = noiseGen(seed);
  const s = buffer(0.03);
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    s[i] = 0.6 * n() * Math.exp(-t / 0.002) + 0.5 * Math.sin(2 * Math.PI * 2400 * t) * Math.exp(-t / 0.006);
  }
  return s;
}

function bubble() {
  const s = buffer(0.09);
  let ph = 0;
  for (let i = 0; i < s.length; i++) {
    const t = i / SR;
    ph += (2 * Math.PI * (420 + 900 * (t / 0.09))) / SR;
    s[i] = Math.sin(ph) * Math.sin((Math.PI * i) / s.length);
  }
  return s;
}

function pluck(f, len = 0.5) {
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

function bass(f, len) {
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
function pad(freqs, len, attack = 0.25, release = 0.5) {
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
function brass(f, len) {
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

// ---------------------------------------------------------------------------------------------
// Freeverb, abridged

function reverb(inL, inR, room = 0.84, damp = 0.25) {
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

// ---------------------------------------------------------------------------------------------
// harmony: C G Am F from the drop, with the hook, the fanfare and the ending voiced to lead into
// what follows them

const CHORDS = { C: [0, 4, 7], G: [7, 11, 14], Am: [9, 12, 16], F: [5, 9, 12] };
const LOOP = ['C', 'G', 'Am', 'F'];

function harmony(marks) {
  return (t) => {
    if (t < 1.5) return 'Am';
    if (t < 2.5) return 'G';
    if (t < 3.0) return 'C';
    if (t < marks.drop) return 'G';
    if (t >= marks.end) return 'C';
    if (t >= marks.end - 2) return 'G';
    if (t >= marks.outro) return 'F';
    if (t >= marks.record) return 'C';
    return LOOP[Math.floor((t - marks.drop) / 2) % 4];
  };
}

function voiced(chord, lo) {
  return CHORDS[chord].map((pc) => {
    let m = 12 * Math.floor(lo / 12) + (pc % 12);
    while (m < lo) m += 12;
    return m;
  }).sort((a, b) => a - b);
}

// ---------------------------------------------------------------------------------------------

/**
 * The finished soundtrack as a 48 kHz stereo WAV. `marks` are the section starts the picture is cut
 * to (see MARKS in sizzle.js). With `stems`, also the four buses before the mix, as WAVs keyed by
 * name, for balancing them.
 */
export function synthesize(cues, duration, marks, { stems = false } = {}) {
  const N = Math.ceil(duration * SR);
  const { drop, arp, wall, streak, record, outro, end } = marks;
  const chordAt = harmony(marks);
  const bus = () => [new Float32Array(N), new Float32Array(N)];
  const drums = bus();
  const music = bus(); // ducked under the kick
  const sfx = bus();
  const send = bus(); // into the reverb

  const place = (target, t, sig, gain = 1, pan = 0, wet = 0) => {
    const start = Math.round(t * SR);
    const gl = Math.cos(((pan + 1) * Math.PI) / 4) * Math.SQRT2 * gain;
    const gr = Math.sin(((pan + 1) * Math.PI) / 4) * Math.SQRT2 * gain;
    for (let i = 0; i < sig.length; i++) {
      const j = start + i;
      if (j < 0 || j >= N) continue;
      target[0][j] += sig[i] * gl;
      target[1][j] += sig[i] * gr;
      if (wet) {
        send[0][j] += sig[i] * gl * wet;
        send[1][j] += sig[i] * gr * wet;
      }
    }
  };

  const at = (type) => cues.filter((c) => c.type === type);
  const during = (t, a, b) => t >= a - 1e-6 && t < b - 1e-6;
  const grooving = (t) => during(t, drop, record) || during(t, outro, end);

  // drums
  const kicks = [0, 0.5, 1.0, 2.5, end];
  for (let t = 3.0; t < drop - 1e-6; t += BEAT) kicks.push(t);
  for (let t = drop; t < end; t += BEAT) if (grooving(t)) kicks.push(t);
  const kickSig = kick();
  for (const t of kicks) place(drums, t, kickSig, 0.85);
  for (let t = drop + BEAT; t < end; t += 2 * BEAT) if (grooving(t)) place(drums, t, clap(Math.round(t * 100)), 0.32, 0, 0.25);
  for (let t = drop + BEAT / 2; t < end; t += BEAT) {
    if (grooving(t)) place(drums, t, hat(Math.round(t * 1000)), 0.16, 0.25);
  }
  for (let t = streak - 4; t < streak; t += BEAT / 2) place(drums, t, hat(Math.round(t * 777)), 0.07, -0.3);
  // snare rolls into the drop and into the end card
  for (const [a, b] of [[drop - 1, drop], [end - 1, end]]) {
    for (let t = a; t < b - 1e-6; t += BEAT / 4) {
      const p = (t - a) / (b - a);
      place(drums, t, snare(Math.round(t * 1000)), 0.08 + 0.3 * p, 0, 0.2);
    }
  }
  for (const t of [drop, wall, streak, end]) place(drums, t, crash(Math.round(t)), 0.22, 0, 0.3);

  // bass, pad, arpeggio
  for (let t = drop; t < end; t += BEAT / 2) {
    if (!grooving(t)) continue;
    const root = 36 + (CHORDS[chordAt(t)][0] % 12);
    place(music, t, bass(hz(root), BEAT / 2 - 0.01), 0.3);
  }
  let segStart = drop;
  for (let t = drop; t <= end + 0.001; t += BEAT) {
    if (t >= end || chordAt(t) !== chordAt(segStart)) {
      place(music, segStart, pad(voiced(chordAt(segStart), 55).map(hz), t - segStart + 0.3), 0.045, 0, 0.35);
      segStart = t;
    }
  }
  const pattern = [0, 1, 2, 1, 0, 1, 2, 3];
  for (let t = arp; t < end; t += BEAT / 4) {
    if (!grooving(t)) continue;
    const notes = voiced(chordAt(t), 67);
    notes.push(notes[0] + 12);
    const step = Math.round((t - arp) / (BEAT / 4));
    const vel = step % 4 === 0 ? 1 : 0.7;
    place(music, t, pluck(hz(notes[pattern[step % 8]]), 0.35), 0.06 * vel, step % 2 ? 0.35 : -0.35, 0.45);
  }

  // the hook: three stabs, a toss and a landing
  for (const c of at('hit')) {
    place(sfx, c.t, pad(voiced('Am', 57).map(hz), 0.4, 0.004, 0.25), 0.16, 0, 0.5);
    place(sfx, c.t, whoosh(0.18, 300, 120, 5), 0.3);
  }
  place(sfx, 2.5, pad(voiced('C', 55).map(hz), 0.9, 0.004, 0.6), 0.1, 0, 0.5);
  place(music, 2.5, bass(hz(36), 0.5), 0.3);

  // the end: a held C major chord with a brass voicing on top
  place(sfx, end, pad([36, 43, 48, 52, 55, 60].map(hz), 3.0, 0.01, 2.2), 0.09, 0, 0.5);
  for (const m of [60, 64, 67, 72]) place(sfx, end, brass(hz(m), 2.6), 0.08, 0, 0.45);

  // sound effects from the picture's cues
  const n = noiseGen(99);
  for (const c of cues) {
    switch (c.type) {
      case 'land':
        place(sfx, c.t, ting(hz(91), Math.round(c.t * 10)), 0.34, 0, 0.35);
        break;
      case 'spin': {
        const len = 0.9 / (c.speed ?? 1);
        const w = whoosh(len, 1400, 900, Math.round(c.t * 10), 0.8);
        // the coin passes edge-on twice a turn, and a turn takes eight 20 ms frames
        for (let i = 0; i < w.length; i++) w[i] *= 0.55 + 0.45 * Math.sin((2 * Math.PI * 12.5 * (c.speed ?? 1) * i) / SR);
        place(sfx, c.t, w, 0.1);
        break;
      }
      case 'toss':
        place(sfx, c.t, whoosh(0.75, 250, 3200, 7), 0.8, 0, 0.2);
        break;
      case 'tap':
        place(sfx, c.t, click(Math.round(c.t * 10)), 0.28);
        break;
      case 'key':
        place(sfx, c.t, click(Math.round(c.t * 100)), 0.14, 0.2);
        break;
      case 'shake': {
        for (let t = c.t; t < c.until; t += 1 / 14) {
          place(sfx, t, whoosh(0.05, 2500, 1800, Math.round(t * 1000), 1), 0.32, (Math.round(t * 14) % 2) * 0.6 - 0.3);
        }
        break;
      }
      case 'pinch':
        place(sfx, c.t, whoosh(c.until - c.t, 700, 1400, 3, 1), 0.08);
        break;
      case 'swish':
      case 'theme':
        place(sfx, c.t - 0.05, whoosh(0.3, 600, 2600, Math.round(c.t * 10)), 0.16, 0, 0.15);
        break;
      case 'rise':
        place(sfx, c.t, whoosh(0.55, 200, 2000, 11), 0.28, 0, 0.2);
        break;
      case 'wipe':
        place(sfx, c.t - 0.1, whoosh(0.5, 3000, 300, 13), 0.3, 0, 0.25);
        break;
      case 'cut':
        place(sfx, c.t - 0.12, whoosh(0.22, 500, 3000, Math.round(c.t)), 0.18);
        break;
      case 'gather':
        place(sfx, c.t, whoosh(0.4, 4000, 500, 17, 2.5), 0.3, 0, 0.2);
        break;
      case 'flick':
        place(sfx, c.t, click(Math.round(c.t * 10)), 0.14);
        place(sfx, c.t + 0.08, ping(hz(84 + [0, 2, 4, 7, 9][Math.round(c.t * 4) % 5]), 0.2), 0.08, 0, 0.3);
        break;
      case 'tick': {
        const penta = [0, 2, 4, 7, 9];
        const m = 96 + penta[Math.floor((n() + 1) * 2.5) % 5] + (n() > 0 ? 12 : 0);
        place(sfx, c.t, ping(hz(m), 0.25), 0.035, n() * 0.8, 0.5);
        break;
      }
      case 'pop':
        place(sfx, c.t, bubble(), 0.18, 0, 0.2);
        break;
      case 'logo':
        for (let i = 0; i < 8; i++) place(sfx, c.t + i * 0.05, ping(hz(84 + [0, 4, 7, 12, 16, 19, 24, 28][i]), 0.4), 0.06, (i / 7) * 1.2 - 0.6, 0.6);
        break;
      case 'burst':
        for (let i = 0; i < 26; i++) {
          const m = 96 + [0, 4, 7, 12, 16][i % 5] + (i % 3 === 0 ? 12 : 0);
          place(sfx, c.t + 0.3 * Math.abs(n()) + i * 0.02, ping(hz(m), 0.3), 0.03, n(), 0.6);
        }
        break;
      case 'fanfare': {
        // G C E G, E G, then the top C, over a C major hold
        const line = [[0, 67, 0.1], [0.1, 72, 0.1], [0.2, 76, 0.1], [0.3, 79, 0.45], [0.8, 76, 0.13], [0.95, 79, 0.13], [1.1, 84, 0.95]];
        for (const [dt, m, len] of line) place(sfx, c.t + dt, brass(hz(m), len), 0.19, 0, 0.35);
        for (const m of [48, 55, 60, 64]) place(sfx, c.t + 1.1, brass(hz(m), 0.95), 0.07, 0, 0.35);
        place(sfx, c.t, ting(hz(91), 5), 0.3, 0, 0.35);
        break;
      }
      default:
        break;
    }
  }

  // sidechain: the music bus breathes with the kick
  const duck = new Float32Array(N).fill(1);
  for (const t of kicks) {
    const s = Math.round(t * SR);
    for (let i = 0; i < 0.3 * SR && s + i < N; i++) duck[s + i] = Math.min(duck[s + i], 1 - 0.55 * Math.exp(-i / SR / 0.08));
  }

  const [wl, wr] = reverb(send[0], send[1]);
  const L = new Float32Array(N);
  const R = new Float32Array(N);
  let peak = 0;
  for (let i = 0; i < N; i++) {
    const t = i / SR;
    const fade = 1 - Math.max(0, Math.min(1, (t - (duration - 0.7)) / 0.65));
    L[i] = (0.5 * drums[0][i] + 0.8 * music[0][i] * duck[i] + sfx[0][i] + 1.6 * wl[i]) * fade;
    R[i] = (0.5 * drums[1][i] + 0.8 * music[1][i] * duck[i] + sfx[1][i] + 1.6 * wr[i]) * fade;
    peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
  }
  // gentle saturation in place of a limiter, then normalize; loudness is set later by ffmpeg
  const drive = 1.2;
  const norm = Math.tanh(drive * peak);
  for (let i = 0; i < N; i++) {
    L[i] = (0.95 * Math.tanh(drive * L[i])) / norm;
    R[i] = (0.95 * Math.tanh(drive * R[i])) / norm;
  }
  if (!stems) return wav(L, R);
  const ducked = music.map((ch) => ch.map((v, i) => v * duck[i]));
  return {
    mix: wav(L, R),
    drums: wav(...drums),
    music: wav(...ducked),
    sfx: wav(...sfx),
    reverb: wav(wl, wr),
  };
}

function wav(L, R) {
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
