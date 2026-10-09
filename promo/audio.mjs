// The promo video's soundtrack, built from the voices in synth.mjs.
//
// A 120 bpm bed in C major (C G Am F) runs under the cues sizzle.js schedules: every landing gets a
// struck-metal ting, every tap a click, every cut a whoosh, and the record streak an original fanfare.

import { SOUNDS, SR, bass, brass, bubble, clap, click, crash, hat, hz, kick, noiseGen, pad, ping, pluck, reverb, shortFanfare, snare, wav, whoosh } from './synth.mjs';

const BPM = 120;
const BEAT = 60 / BPM;

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
        for (const v of SOUNDS.coin(Math.round(c.t * 10))) place(sfx, c.t + v.t, v.sig, v.gain, 0, v.wet);
        break;
      case 'spin':
        // cut at the landing, where the app stops it, with a fade so the cut does not click
        for (const v of SOUNDS.spin(c.speed ?? 1, Math.round(c.t * 10), { frame: c.frame, lead: 0 })) {
          const end = Math.min(v.sig.length, Math.round((c.until - c.t) * SR));
          const sig = v.sig.slice(0, end);
          const fade = Math.min(end, Math.round(0.01 * SR));
          for (let i = 0; i < fade; i++) sig[end - fade + i] *= 1 - i / fade;
          place(sfx, c.t + v.t, sig, v.gain, 0, v.wet);
        }
        break;
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
      case 'fanfare':
        for (const v of shortFanfare()) place(sfx, c.t + v.t, v.sig, v.gain, 0, v.wet);
        break;
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
