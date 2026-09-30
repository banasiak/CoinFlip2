// Simple Coin Flip -- Google Play promo video, drawn one frame at a time.
//
// render.mjs loads this page, calls setup() once and renderFrame(t) for every frame. Nothing here
// animates on its own: every frame is a pure function of t, so a frame can be re-rendered alone.
'use strict';

const W = 1920;
const H = 1080;
// the app advances its flip animation every 20 ms, so at 50 fps one video frame is one app frame
const FPS = 50;

const RES = '../coinflip/src/main/res/';

const stage = document.getElementById('stage');
const ctx = stage.getContext('2d');

// ---------------------------------------------------------------------------------------------
// palette: the app's own Material schemes (ui/theme/Color.kt), plus three dynamic-color examples

const DARK = { bg: '#1C1B1F', on: '#E5E1E6', primary: '#C1C1FF', heads: '#FFB2BC', tails: '#FFB68B', nav: '#211F26', navOn: '#C8C5D0' };
const LIGHT = { bg: '#FFFBFF', on: '#1C1B1F', primary: '#5455A9', heads: '#B0294B', tails: '#96490C', nav: '#F3EDF7', navOn: '#47464F' };
const MOSS = { bg: '#F8FAF0', on: '#191D16', primary: '#3B6939', heads: '#52634F', tails: '#38656A', nav: '#ECEFE4', navOn: '#43483F' };
const OCEAN = { bg: '#0F1417', on: '#DEE3E6', primary: '#8ECFF2', heads: '#B4CAD6', tails: '#C7C2EA', nav: '#1B2023', navOn: '#C0C8CD' };
const CORAL = { bg: '#FFF8F6', on: '#231918', primary: '#904A42', heads: '#775653', tails: '#715B2E', nav: '#FCEAE7', navOn: '#534341' };

const INK = '#F4F2FF';
const SUB = '#BDBBE8';
const GOLD = '#FFD567';
const BRAND = '#6667AB';

// ---------------------------------------------------------------------------------------------
// math

const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const seg = (t, a, b) => clamp((t - a) / (b - a));
const lerp = (a, b, p) => a + (b - a) * p;
const outCubic = (p) => 1 - (1 - p) ** 3;
const inCubic = (p) => p ** 3;
const inOutCubic = (p) => (p < 0.5 ? 4 * p ** 3 : 1 - (-2 * p + 2) ** 3 / 2);
const outBack = (p) => {
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * (p - 1) ** 3 + c1 * (p - 1) ** 2;
};

function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

// ---------------------------------------------------------------------------------------------
// the flip itself, frame for frame as AnimationHelper builds it: a4..a1 are the heads face squashed
// to 4/4..1/4 of its width, b4..b1 the tails face, e the edge

const TURN_A = [['a', 4], ['a', 3], ['a', 2], ['a', 1], ['e', 0], ['b', 1], ['b', 2], ['b', 3], ['b', 4], ['b', 3], ['b', 2], ['b', 1], ['e', 0], ['a', 1], ['a', 2], ['a', 3]];
const TURN_B = TURN_A.map(([s, k]) => [s === 'a' ? 'b' : s === 'b' ? 'a' : 'e', k]);
const SEQ = {
  HH: [...TURN_A, ...TURN_A, ...TURN_A, ['a', 4]],
  HT: [...TURN_A, ...TURN_A, ...TURN_A.slice(0, 9)],
  TH: [...TURN_B, ...TURN_B, ...TURN_B.slice(0, 9)],
  TT: [...TURN_B, ...TURN_B, ...TURN_B, ['b', 4]],
};

// a flip is scheduled by the moment it lands: the app plays the landing sound and reveals the
// result four frames before the animation ends (MainViewModel.flipCoin)
function mkFlip(perm, land, speed = 1) {
  const n = SEQ[perm].length;
  return { perm, land, speed, t0: land - (n - 4) / (FPS * speed), value: perm[1] };
}

function frameOf(flip, t) {
  const seq = SEQ[flip.perm];
  const i = Math.floor((t - flip.t0) * FPS * flip.speed + 1e-6);
  return seq[clamp(i, 0, seq.length - 1)];
}

function flipAt(flips, t) {
  let cur = null;
  const landed = [];
  for (const f of flips) {
    if (t >= f.t0) cur = f;
    if (t >= f.land) landed.push(f);
  }
  return { cur, landed, flying: cur !== null && t < cur.land, frame: cur ? frameOf(cur, t) : null };
}

// at double speed a turn takes eight video frames, and stepping through the app's frames strobes
// between the same two widths, so this turns the coin continuously and blurs each video frame
// across its whole 20 ms, the way a camera would
const BLUR_SAMPLES = 10;
let spinAcc = null;
let spinOne = null;

function drawSpin(coin, flip, t, cx, cy, d) {
  const last = SEQ[flip.perm].length - 1;
  const stepAt = (u) => clamp((u - flip.t0) * FPS * flip.speed, 0, last);
  const from = stepAt(t - 0.5 / FPS);
  const to = stepAt(t + 0.5 / FPS);
  if (from === to) {
    drawFrame(coin, frameOf(flip, t), cx, cy, d);
    return;
  }
  const size = Math.ceil(d);
  spinAcc ??= canvas(size, size);
  spinOne ??= canvas(size, size);
  const acc = spinAcc.getContext('2d');
  const one = spinOne.getContext('2d');
  acc.clearRect(0, 0, size, size);
  acc.globalCompositeOperation = 'lighter';
  acc.globalAlpha = 1 / BLUR_SAMPLES;
  for (let j = 0; j < BLUR_SAMPLES; j++) {
    // a turn is 16 of the app's frames
    const c = Math.cos((lerp(from, to, (j + 0.5) / BLUR_SAMPLES) * Math.PI) / 8);
    const w = Math.max(1, size * Math.abs(c));
    one.clearRect(0, 0, size, size);
    if (Math.abs(c) < 0.25) one.drawImage(coin.edge, 0, 0, size, size);
    one.drawImage(c >= 0 === (flip.perm[0] === 'H') ? coin.heads : coin.tails, (size - w) / 2, 0, w, size);
    acc.drawImage(spinOne, 0, 0);
  }
  acc.globalCompositeOperation = 'source-over';
  acc.globalAlpha = 1;
  ctx.drawImage(spinAcc, cx - size / 2, cy - size / 2);
}

function drawFrame(coin, frame, cx, cy, d, alpha = 1) {
  const [side, k] = frame;
  ctx.save();
  ctx.globalAlpha *= alpha;
  if (side === 'e') {
    ctx.drawImage(coin.edge, cx - d / 2, cy - d / 2, d, d);
  } else {
    const img = side === 'a' ? coin.heads : coin.tails;
    const w = Math.max(1, d * k / 4);
    ctx.drawImage(img, cx - w / 2, cy - d / 2, w, d);
  }
  ctx.restore();
}

// ---------------------------------------------------------------------------------------------
// assets

const IMG = {};
let COINS = [];
const coinBy = {};
let CUSTOM = null;

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`failed to load ${src}`));
    img.src = src;
  });
}

function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
}

async function setup() {
  const catalog = await (await fetch('coins.json')).json();
  const cache = {};
  const load = (src) => (cache[src] ??= loadImage(src));
  IMG.edge = await load(`${RES}drawable/edge.webp`);
  IMG.logo = await load(`${RES}mipmap-xxxhdpi/ic_launcher_foreground.webp`);
  COINS = await Promise.all(
    catalog.map(async (c) => ({ ...c, heads: await load(c.headsUrl), tails: await load(c.tailsUrl), edge: IMG.edge })),
  );
  for (const c of COINS) coinBy[c.prefix] = c;

  await Promise.all(['400', '500', '700', '900'].map((w) => document.fonts.load(`${w} 40px Roboto`)));
  // resolves empty rather than rejecting when no @font-face declares the family, and the canvas then
  // falls back to a system emoji font if there is one and draws an empty box if there is not
  if (!(await document.fonts.load('40px "Noto Color Emoji"', '\u{1F355}\u{1F32E}')).length) {
    throw new Error('Noto Color Emoji did not load; the pizza and taco would draw as empty boxes');
  }

  IMG.pizza = makePhoto('pizza');
  IMG.taco = makePhoto('taco');
  CUSTOM = makeCustomCoin();
  buildWall();
  return { fps: FPS, duration: DURATION, cues: CUES, marks: MARKS };
}

// the "photos" the custom coin is made from: a slice on a gingham tablecloth, a taco on tiles
function makePhoto(kind) {
  const c = canvas(960, 720);
  const g = c.getContext('2d');
  if (kind === 'pizza') {
    g.fillStyle = '#FBF1E4';
    g.fillRect(0, 0, 960, 720);
    g.fillStyle = 'rgba(206, 45, 58, 0.5)';
    for (let x = 0; x < 960; x += 90) g.fillRect(x, 0, 45, 720);
    for (let y = 0; y < 720; y += 90) g.fillRect(0, y, 960, 45);
    emoji(g, '\u{1F355}', 470, 400, 400, -0.35);
  } else {
    g.fillStyle = '#1E8C8A';
    g.fillRect(0, 0, 960, 720);
    for (let y = 0; y < 720; y += 120) {
      for (let x = 0; x < 960; x += 120) {
        g.fillStyle = (x / 120 + y / 120) % 2 ? '#2BA3A0' : '#23979A';
        g.fillRect(x + 4, y + 4, 112, 112);
      }
    }
    emoji(g, '\u{1F32E}', 500, 380, 400, 0.15);
  }
  const v = g.createRadialGradient(480, 360, 120, 480, 360, 640);
  v.addColorStop(0, 'rgba(255, 255, 255, 0.10)');
  v.addColorStop(1, 'rgba(0, 0, 0, 0.40)');
  g.fillStyle = v;
  g.fillRect(0, 0, 960, 720);
  return c;
}

function emoji(g, ch, x, y, size, rot) {
  g.save();
  g.translate(x, y);
  g.rotate(rot);
  g.font = `${size}px "Noto Color Emoji"`;
  // a colour glyph still takes the fill's alpha
  g.fillStyle = '#000';
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.shadowColor = 'rgba(0, 0, 0, 0.35)';
  g.shadowBlur = 36;
  g.shadowOffsetY = 18;
  g.fillText(ch, 0, 0);
  g.restore();
}

// where the crop screen leaves each photo: the same numbers draw the crop and cut the face, so the
// coin that comes out is exactly what the circle showed
const CROP_HEADS = { from: { s: 1.0, ox: 0, oy: 0 }, to: { s: 1.75, ox: 0.02, oy: -0.08 } };
const CROP_TAILS = { from: { s: 1.75, ox: 0.16, oy: 0.1 }, to: { s: 1.75, ox: -0.03, oy: -0.02 } };

function makeCustomCoin() {
  const heads = cutFace(IMG.pizza, CROP_HEADS.to, DARK.heads);
  const tails = cutFace(IMG.taco, CROP_TAILS.to, DARK.tails);
  // the edge takes an even mix of the two rim colors, tinted as a copy (AnimationHelper.edge)
  const edge = canvas(390, 390);
  const g = edge.getContext('2d');
  g.drawImage(IMG.edge, 0, 0, 390, 390);
  g.globalCompositeOperation = 'source-in';
  g.fillStyle = mix(DARK.heads, DARK.tails, 0.5);
  g.fillRect(0, 0, 390, 390);
  return { prefix: 'custom', heads, tails, edge };
}

function cutFace(photo, crop, rim) {
  const F = 390;
  const c = canvas(F, F);
  const g = c.getContext('2d');
  const scale = (F / Math.min(photo.width, photo.height)) * crop.s;
  const w = photo.width * scale;
  const h = photo.height * scale;
  g.beginPath();
  g.arc(F / 2, F / 2, F / 2, 0, Math.PI * 2);
  g.clip();
  g.drawImage(photo, F / 2 + crop.ox * F - w / 2, F / 2 + crop.oy * F - h / 2, w, h);
  // CoinImage.drawRim: 5% of the diameter, centred half a stroke in
  const rw = F * 0.05;
  g.beginPath();
  g.arc(F / 2, F / 2, F / 2 - rw / 2, 0, Math.PI * 2);
  g.lineWidth = rw;
  g.strokeStyle = rim;
  g.stroke();
  return c;
}

function mix(a, b, p) {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
  return `rgb(${pa.map((v, i) => Math.round(lerp(v, pb[i], p))).join(',')})`;
}

// ---------------------------------------------------------------------------------------------
// drawing helpers

// Roboto's hhea metrics; Compose centres each line box, so a run of either size sits on the same
// centre line rather than the same baseline
const ASC = 0.928;
const DESC = 0.244;
const centred = (cy, size) => cy + (size * (ASC - DESC)) / 2;

function font(size, weight) {
  return `${weight} ${size}px Roboto`;
}

function txt(s, x, y, o = {}) {
  const { size = 64, weight = 700, color = INK, align = 'center', alpha = 1, ls = 0 } = o;
  if (alpha <= 0) return;
  ctx.save();
  ctx.globalAlpha *= alpha;
  ctx.font = font(size, weight);
  ctx.letterSpacing = `${ls}px`;
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = 'alphabetic';
  ctx.fillText(s, x, y);
  ctx.restore();
}

function measure(s, size, weight, ls = 0) {
  ctx.save();
  ctx.font = font(size, weight);
  ctx.letterSpacing = `${ls}px`;
  const w = ctx.measureText(s).width;
  ctx.restore();
  return w;
}

// a line of type that rises into view through a mask at tin, and leaves upward through it at tout
function kline(s, x, y, o, t, tin, tout = Infinity) {
  const size = o.size ?? 64;
  if (t < tin || t > tout + 0.3) return;
  const pin = outCubic(seg(t, tin, tin + 0.4));
  const pout = Number.isFinite(tout) ? inCubic(seg(t, tout, tout + 0.28)) : 0;
  const dy = (1 - pin) * size * 1.25 - pout * size * 1.25;
  ctx.save();
  ctx.beginPath();
  ctx.rect(0, y - size * 1.02, W, size * 1.4);
  ctx.clip();
  txt(s, x, y + dy, o);
  ctx.restore();
}

// one line made of differently coloured runs, each run optionally arriving a little later
function kparts(parts, x, y, o, t, tin, tout = Infinity) {
  const size = o.size ?? 64;
  const weight = o.weight ?? 900;
  const ls = o.ls ?? 0;
  const widths = parts.map((p) => measure(p.s, size, weight, ls));
  const total = widths.reduce((a, b) => a + b, 0);
  let px = o.align === 'center' ? x - total / 2 : o.align === 'right' ? x - total : x;
  parts.forEach((p, i) => {
    kline(p.s, px, y, { ...o, align: 'left', color: p.color ?? o.color }, t, tin + (p.delay ?? 0), tout);
    px += widths[i];
  });
}

// headline and caption stacked on the left, centred on the frame's vertical middle
const HEAD = { size: 104, weight: 900, color: INK, align: 'left', ls: -2 };
const CAP = { size: 40, weight: 400, color: SUB, align: 'left' };

function copyBlock(t, tin, tout, lines, caps, x = 150) {
  const lh = 112;
  const ch = 56;
  const height = lines.length * lh + 34 + caps.length * ch;
  let y = 540 - height / 2 + HEAD.size * 0.78;
  lines.forEach((l, i) => kline(l, x, y + i * lh, HEAD, t, tin + i * 0.08, tout));
  y += (lines.length - 1) * lh + 34 + ch;
  caps.forEach((c, i) => kline(c, x, y + i * ch, CAP, t, tin + 0.22 + i * 0.06, tout));
}

function glow(x, y, r, color) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, r);
  g.addColorStop(0, color);
  g.addColorStop(1, 'rgba(0, 0, 0, 0)');
  ctx.fillStyle = g;
  ctx.fillRect(x - r, y - r, 2 * r, 2 * r);
}

function background(t) {
  const g = ctx.createRadialGradient(W / 2, H * 0.45, 0, W / 2, H / 2, W * 0.7);
  g.addColorStop(0, '#26244F');
  g.addColorStop(1, '#0D0C1C');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
  glow(W * 0.18 + 70 * Math.sin(t * 0.4), H * 0.22 + 50 * Math.cos(t * 0.3), 700, 'rgba(255, 178, 188, 0.10)');
  glow(W * 0.84 + 60 * Math.cos(t * 0.35), H * 0.82 + 50 * Math.sin(t * 0.45), 760, 'rgba(255, 182, 139, 0.09)');
}

function shock(x, y, r0, t, t0, color, grow = 150, dur = 0.55) {
  const p = seg(t, t0, t0 + dur);
  if (p <= 0 || p >= 1) return;
  ctx.save();
  ctx.globalAlpha = 0.65 * (1 - p);
  ctx.strokeStyle = color;
  ctx.lineWidth = 10 * (1 - p) + 1;
  ctx.beginPath();
  ctx.arc(x, y, r0 + grow * outCubic(p), 0, Math.PI * 2);
  ctx.stroke();
  ctx.restore();
}

function pop(t, t0, dur = 0.35) {
  return outBack(seg(t, t0, t0 + dur));
}

function scaled(x, y, s, draw) {
  if (s <= 0) return;
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(s, s);
  ctx.translate(-x, -y);
  draw();
  ctx.restore();
}

// ---------------------------------------------------------------------------------------------
// the phone and the app's screens, laid out in dp from MainScreen.kt and CoinCropDialog.kt

const SW = 411;
const SH = 891;
const STATUS = 40;
const NAV_TOP = SH - 104; // 80dp NavigationBar plus the 24dp gesture inset
const COIN_BOX = 283; // the width less Dimen.xlarge either side
const COIN_Y = STATUS + SW / 2;
const RESULT_Y = STATUS + SW + 45; // the 72sp line, 1.25 line height
const INSTR_Y = RESULT_Y + 45 + 16 + 12;
const STATS_Y = INSTR_Y + 12 + 32 + 14;

const ICON = {
  diagnostics: new Path2D('M280,680h80v-280h-80v280ZM440,680h80v-400h-80v400ZM600,680h80v-160h-80v160ZM200,840q-33,0 -56.5,-23.5T120,760v-560q0,-33 23.5,-56.5T200,120h560q33,0 56.5,23.5T840,200v560q0,33 -23.5,56.5T760,840L200,840ZM200,760h560v-560L200,200v560ZM200,200v560,-560Z'),
  settings: new Path2D('m370,880 l-16,-128q-13,-5 -24.5,-12T307,725l-119,50L78,585l103,-78q-1,-7 -1,-13.5v-27q0,-6.5 1,-13.5L78,375l110,-190 119,50q11,-8 23,-15t24,-12l16,-128h220l16,128q13,5 24.5,12t22.5,15l119,-50 110,190 -103,78q1,7 1,13.5v27q0,6.5 -2,13.5l103,78 -110,190 -118,-50q-11,8 -23,15t-24,12L590,880L370,880ZM440,800h79l14,-106q31,-8 57.5,-23.5T639,633l99,41 39,-68 -86,-65q5,-14 7,-29.5t2,-31.5q0,-16 -2,-31.5t-7,-29.5l86,-65 -39,-68 -99,42q-22,-23 -48.5,-38.5T533,266l-13,-106h-79l-14,106q-31,8 -57.5,23.5T321,327l-99,-41 -39,68 86,64q-5,15 -7,30t-2,32q0,16 2,31t7,30l-86,65 39,68 99,-42q22,23 48.5,38.5T427,694l13,106ZM482,620q58,0 99,-41t41,-99q0,-58 -41,-99t-99,-41q-59,0 -99.5,41T342,480q0,58 40.5,99t99.5,41ZM480,480Z'),
  about: new Path2D('M440,680h80v-240h-80v240ZM480,360q17,0 28.5,-11.5T520,320q0,-17 -11.5,-28.5T480,280q-17,0 -28.5,11.5T440,320q0,17 11.5,28.5T480,360ZM480,880q-83,0 -156,-31.5T197,763q-54,-54 -85.5,-127T80,480q0,-83 31.5,-156T197,197q54,-54 127,-85.5T480,80q83,0 156,31.5T763,197q54,54 85.5,127T880,480q0,83 -31.5,156T763,763q-54,54 -127,85.5T480,880ZM480,800q134,0 227,-93t93,-227q0,-134 -93,-227t-227,-93q-134,0 -227,93t-93,227q0,134 93,227t227,93ZM480,480Z'),
  rotate: new Path2D('M522 880v-82q34-5 66.5-18t61.5-34l56 58q-42 32-88 51.5T522 880Zm-80 0Q304 862 213 760.5T122 522q0-75 28.5-140.5t77-114q48.5-48.5 114-77T482 162h6l-62-62 56-58 160 160-160 160-56-56 64-64h-8q-117 0-198.5 81.5T202 522q0 104 68 182.5T442 798v82Zm322-134-58-56q21-29 34-61.5t18-66.5h82q-5 50-24.5 96T764 746Zm76-264h-82q-5-34-18-66.5T706 354l58-56q32 39 51 86t25 98Z'),
  mirror: new Path2D('M360 840H200q-33 0-56.5-23.5T120 760v-560q0-33 23.5-56.5T200 120h160v80H200v560h160v80Zm80 80v-880h80v880h-80Zm160-80v-80h80v80h-80Zm0-640v-80h80v80h-80Zm160 640v-80h80q0 33-23.5 56.5T760 840Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80h80v80h-80Zm0-160v-80q33 0 56.5 23.5T840 200h-80Z'),
  close: new Path2D('M256,760L200,704L424,480L200,256L256,200L480,424L704,200L760,256L536,480L760,704L704,760L480,536L256,760Z'),
};

function icon(path, x, y, size, color) {
  ctx.save();
  ctx.translate(x - size / 2, y - size / 2);
  ctx.scale(size / 960, size / 960);
  ctx.fillStyle = color;
  ctx.fill(path);
  ctx.restore();
}

function phone(cx, cy, scale, rot, drawScreen) {
  const w = SW * scale;
  const h = SH * scale;
  const bez = 13 * scale;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(rot);
  ctx.save();
  ctx.shadowColor = 'rgba(0, 0, 0, 0.6)';
  ctx.shadowBlur = 90;
  ctx.shadowOffsetY = 36;
  ctx.beginPath();
  ctx.roundRect(-w / 2 - bez, -h / 2 - bez, w + 2 * bez, h + 2 * bez, 60 * scale);
  ctx.fillStyle = '#0A0A0F';
  ctx.fill();
  ctx.restore();
  ctx.beginPath();
  ctx.roundRect(-w / 2 - bez + 1.5, -h / 2 - bez + 1.5, w + 2 * bez - 3, h + 2 * bez - 3, 59 * scale);
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.16)';
  ctx.lineWidth = 3;
  ctx.stroke();
  ctx.save();
  ctx.beginPath();
  ctx.roundRect(-w / 2, -h / 2, w, h, 47 * scale);
  ctx.clip();
  ctx.translate(-w / 2, -h / 2);
  ctx.scale(scale, scale);
  drawScreen();
  // the punch-hole camera sits over whatever the app draws
  ctx.beginPath();
  ctx.arc(SW / 2, 20, 6.5, 0, Math.PI * 2);
  ctx.fillStyle = '#050507';
  ctx.fill();
  ctx.restore();
  ctx.restore();
}

function statusBar(th) {
  txt('12:30', 26, centred(22, 14), { size: 14, weight: 500, color: th.on, align: 'left' });
  ctx.save();
  ctx.fillStyle = th.on;
  ctx.beginPath();
  ctx.moveTo(346, 28);
  ctx.arc(346, 28, 11, -Math.PI * 0.75, -Math.PI * 0.25);
  ctx.closePath();
  ctx.fill();
  ctx.beginPath();
  ctx.roundRect(363, 14, 10, 17, 2);
  ctx.fill();
  ctx.fillRect(366, 12, 4, 3);
  ctx.restore();
}

function navBar(th) {
  ctx.fillStyle = th.nav;
  ctx.fillRect(0, NAV_TOP, SW, SH - NAV_TOP);
  [['diagnostics', 'Diagnostics'], ['settings', 'Settings'], ['about', 'About']].forEach(([k, label], i) => {
    const x = (SW * (2 * i + 1)) / 6;
    icon(ICON[k], x, NAV_TOP + 28, 24, th.navOn);
    txt(label, x, centred(NAV_TOP + 58, 12), { size: 12, weight: 500, color: th.navOn, ls: 0.5 });
  });
  gestureHandle(th);
}

function gestureHandle(th) {
  ctx.save();
  ctx.globalAlpha = 0.5;
  ctx.fillStyle = th.on;
  ctx.beginPath();
  ctx.roundRect(SW / 2 - 54, SH - 12, 108, 4, 2);
  ctx.fill();
  ctx.restore();
}

// the '?' the main screen shows before the first flip, fitted to its measured ink as MainScreen does
function placeholder(th, cx, cy, box) {
  ctx.save();
  ctx.font = font(box, 700);
  let m = ctx.measureText('?');
  const iw = m.actualBoundingBoxLeft + m.actualBoundingBoxRight;
  const ih = m.actualBoundingBoxAscent + m.actualBoundingBoxDescent;
  const size = box * Math.min(box / iw, box / ih) * 0.9;
  ctx.font = font(size, 700);
  m = ctx.measureText('?');
  ctx.fillStyle = th.primary;
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  ctx.fillText('?', cx - (m.actualBoundingBoxRight - m.actualBoundingBoxLeft) / 2, cy + (m.actualBoundingBoxAscent - m.actualBoundingBoxDescent) / 2);
  ctx.restore();
}

// 'HEADS x7': the run sits beside the result at 0.55 of its size, the pair centred as one
function resultLine(cx, cy, label, streak, color, size, streakScale = 1) {
  const ss = size * 0.55;
  const gap = streak ? (size * 8) / 72 : 0;
  const lw = measure(label, size, 700);
  const run = streak ? `×${streak}` : '';
  const sw = streak ? measure(run, ss, 700) : 0;
  const x0 = cx - (lw + gap + sw) / 2;
  txt(label, x0, centred(cy, size), { size, weight: 700, color, align: 'left' });
  if (streak) {
    const sx = x0 + lw + gap + sw / 2;
    scaled(sx, cy, streakScale, () => txt(run, sx, centred(cy, ss), { size: ss, weight: 700, color }));
  }
}

function statsRow(th, labels, heads, tails) {
  const y = centred(STATS_Y, 22);
  const mid = SW / 2;
  const hc = heads.toLocaleString('en-US');
  const tc = tails.toLocaleString('en-US');
  txt(hc, mid - 16, y, { size: 22, weight: 400, color: th.heads, align: 'right' });
  txt(labels[0], mid - 16 - measure(hc, 22, 400) - 16, y, { size: 22, weight: 400, color: th.heads, align: 'right' });
  txt(labels[1], mid + 16, y, { size: 22, weight: 400, color: th.tails, align: 'left' });
  txt(tc, mid + 16 + measure(labels[1], 22, 400) + 16, y, { size: 22, weight: 400, color: th.tails, align: 'left' });
}

function mainScreen(th, st) {
  ctx.fillStyle = th.bg;
  ctx.fillRect(0, 0, SW, SH);
  statusBar(th);
  if (st.frame) drawFrame(st.coin, st.frame, SW / 2, COIN_Y, COIN_BOX);
  else placeholder(th, SW / 2, COIN_Y, COIN_BOX);
  if (st.label) resultLine(SW / 2, RESULT_Y, st.label, st.streak, st.value === 'H' ? th.heads : th.tails, 72, st.streakScale ?? 1);
  txt('Tap/Shake to Flip Coin', SW / 2, centred(INSTR_Y, 16), { size: 16, weight: 500, color: th.on, ls: 0.15 });
  statsRow(th, st.labels ?? ['HEADS', 'TAILS'], st.heads, st.tails);
  navBar(th);
}

const CROP_TOP = STATUS + 64;
const CROP_BOTTOM = SH - 24 - 64;
const CROP_CY = (CROP_TOP + CROP_BOTTOM) / 2;
const CROP_V = 0.8 * Math.min(SW, CROP_BOTTOM - CROP_TOP);
const OK_X = 373;
const ACTIONS_Y = CROP_BOTTOM + 32;

function cropScreen(th, photo, crop, face) {
  ctx.fillStyle = th.bg;
  ctx.fillRect(0, 0, SW, SH);
  ctx.save();
  ctx.beginPath();
  ctx.rect(0, CROP_TOP, SW, CROP_BOTTOM - CROP_TOP);
  ctx.clip();
  const scale = (CROP_V / Math.min(photo.width, photo.height)) * crop.s;
  const w = photo.width * scale;
  const h = photo.height * scale;
  ctx.drawImage(photo, SW / 2 + crop.ox * CROP_V - w / 2, CROP_CY + crop.oy * CROP_V - h / 2, w, h);
  ctx.beginPath();
  ctx.rect(0, CROP_TOP, SW, CROP_BOTTOM - CROP_TOP);
  ctx.arc(SW / 2, CROP_CY, CROP_V / 2, 0, Math.PI * 2);
  ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
  ctx.fill('evenodd');
  const rw = CROP_V * 0.05;
  ctx.beginPath();
  ctx.arc(SW / 2, CROP_CY, CROP_V / 2 - rw / 2, 0, Math.PI * 2);
  ctx.lineWidth = rw;
  ctx.strokeStyle = face === 'heads' ? th.heads : th.tails;
  ctx.stroke();
  ctx.restore();
  statusBar(th);
  icon(ICON.close, 28, STATUS + 32, 24, th.on);
  txt('Position the Image', 64, centred(STATUS + 32, 22), { size: 22, weight: 400, color: th.on, align: 'left' });
  txt('Reset', 47, centred(ACTIONS_Y, 14), { size: 14, weight: 500, color: th.primary, ls: 0.1 });
  icon(ICON.rotate, 190.5, ACTIONS_Y, 24, th.navOn);
  icon(ICON.mirror, 238.5, ACTIONS_Y, 24, th.navOn);
  txt('OK', OK_X, centred(ACTIONS_Y, 14), { size: 14, weight: 500, color: th.primary, ls: 0.1 });
  gestureHandle(th);
}

function lerpCrop(a, b, p) {
  return { s: lerp(a.s, b.s, p), ox: lerp(a.ox, b.ox, p), oy: lerp(a.oy, b.oy, p) };
}

// a fingertip: a soft disc while pressed, and the ripple it leaves on release
function finger(x, y, t, down, up) {
  if (t >= down - 0.06 && t <= up) {
    const a = 0.55 * seg(t, down - 0.06, down) * (1 - seg(t, up - 0.05, up));
    ctx.save();
    ctx.globalAlpha = a;
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(x, y, 19, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }
  const p = seg(t, up - 0.05, up + 0.4);
  if (p > 0 && p < 1) {
    ctx.save();
    ctx.globalAlpha = 0.35 * (1 - p);
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(x, y, 20 + 70 * outCubic(p), 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }
}

function tap(x, y, t, at) {
  finger(x, y, t, at - 0.1, at);
}

// ---------------------------------------------------------------------------------------------
// the timeline. Each flip is pinned to the beat it lands on (120 bpm, so every 0.5 s)

const CUES = [];
const cue = (t, type, extra = {}) => CUES.push({ t, type, ...extra });

// where each section starts; the times inside a section are offsets from its start, so a section
// can be lengthened without re-timing everything after it
const BRAND_AT = 5.0;
const TAP = BRAND_AT + 2.0;
const WALL_AT = TAP + 10.0;
const RANDOM_AT = WALL_AT + 4.0;
const CUSTOM_AT = RANDOM_AT + 3.5;
const STREAK_AT = CUSTOM_AT + 9.0;
const THEMES_AT = STREAK_AT + 3.5;
const END = THEMES_AT + 2.5;
const DURATION = END + 3.0;

function flipCues(flips, final = 'land') {
  flips.forEach((f, i) => {
    cue(f.t0, 'spin', { speed: f.speed });
    cue(f.land, i === flips.length - 1 ? final : 'land');
  });
}

// 1. hook: "Heads or Tails?" and a coin tossed in from below
const HOOK_FLIP = mkFlip('HT', 2.5);
cue(0.0, 'hit');
cue(0.5, 'hit');
cue(1.0, 'hit');
cue(HOOK_FLIP.t0, 'toss');
cue(HOOK_FLIP.land, 'land');
cue(3.0, 'swish');

function sceneHook(t) {
  background(t);
  const o = { size: 150, weight: 900, color: INK, align: 'center', ls: -3 };
  kparts(
    [
      { s: 'Heads ', color: DARK.heads },
      { s: 'or ', color: INK, delay: 0.5 },
      { s: 'Tails?', color: DARK.tails, delay: 1.0 },
    ],
    W / 2, 250, o, t, 0.0, 2.95,
  );
  const o2 = { ...o, size: 130 };
  kline('No Coin?', W / 2, 190, o2, t, 3.1);
  kline('Let Your Phone Decide!', W / 2, 330, o2, t, 3.18);

  const f = HOOK_FLIP;
  const restY = 610;
  if (t >= f.t0) {
    const p = seg(t, f.t0, f.land);
    const y = 1400 - (1400 - restY) * p - 1600 * p * (1 - p);
    const d = 420 * (1 + 0.1 * Math.sin(Math.PI * p));
    drawFrame(coinBy.gw, frameOf(f, t), W / 2, y, d);
  }
  shock(W / 2, restY, 215, t, f.land, DARK.tails, 90, 0.45);
  if (t >= f.land) {
    const s = pop(t, f.land);
    scaled(W / 2, 910, s, () => txt('TAILS', W / 2, centred(910, 120), { size: 120, weight: 700, color: DARK.tails }));
  }
}

// 2. brand
cue(BRAND_AT, 'wipe');
cue(BRAND_AT + 0.1, 'logo');

function logo(cx, cy, d, t, tin) {
  if (t < tin) return;
  const p = seg(t, tin, tin + 0.7);
  const s = outBack(seg(t, tin, tin + 0.45));
  const spin = Math.cos(outCubic(p) * Math.PI * 4);
  const k = d / 233; // the coin spans 233 of the foreground's 432px
  ctx.save();
  ctx.translate(cx, cy);
  ctx.scale(s * Math.max(0.03, Math.abs(spin)), s);
  ctx.shadowColor = 'rgba(20, 18, 60, 0.35)';
  ctx.shadowBlur = 50;
  ctx.shadowOffsetY = 24;
  ctx.drawImage(IMG.logo, (-432 * k) / 2, (-432 * k) / 2, 432 * k, 432 * k);
  ctx.restore();
}

function brandWipe(t, t0, x, y) {
  const r = 2300 * inOutCubic(seg(t, t0, t0 + 0.4));
  ctx.beginPath();
  ctx.arc(x, y, Math.max(0.1, r), 0, Math.PI * 2);
  ctx.clip();
  ctx.fillStyle = BRAND;
  ctx.fillRect(0, 0, W, H);
  glow(W / 2, 420, 900, 'rgba(255, 255, 255, 0.12)');
}

function sceneBrand(t) {
  ctx.save();
  brandWipe(t, BRAND_AT, W / 2, 610);
  logo(W / 2, 410, 400, t, BRAND_AT + 0.1);
  kline('Simple Coin Flip', W / 2, 830, { size: 140, weight: 900, color: '#FFFFFF', align: 'center', ls: -3 }, t, BRAND_AT + 0.45);
  ctx.restore();
}

// 3. the main screen: tap, shake, tally
const COPY_TAP = [TAP + 0.2, TAP + 3.35];
const COPY_SHAKE = [TAP + 3.6, TAP + 6.85];
const COPY_SCORE = TAP + 7.1;
const TAP_FLIPS = [mkFlip('HH', TAP + 2.5), mkFlip('HT', TAP + 6.0), mkFlip('TH', TAP + 8.5)];
const TAP_AT = TAP + 1.55;
const SHAKE = [TAP + 4.55, TAP + 5.25];
cue(TAP, 'rise');
cue(TAP_AT, 'tap');
cue(SHAKE[0], 'shake', { until: SHAKE[1] });
flipCues(TAP_FLIPS);
cue(COPY_SHAKE[0], 'swish');
cue(COPY_SCORE, 'swish');

function tally(flips, t, heads, tails) {
  const s = flipAt(flips, t);
  const last = s.landed[s.landed.length - 1];
  return {
    frame: s.frame,
    label: last && !s.flying ? last : null,
    heads: heads + s.landed.filter((f) => f.value === 'H').length,
    tails: tails + s.landed.filter((f) => f.value === 'T').length,
  };
}

function sceneTap(t) {
  background(t);
  copyBlock(t, ...COPY_TAP, ['Tap to Flip'], ['Tap the coin. It spins, lands,', 'and calls it.']);
  copyBlock(t, ...COPY_SHAKE, ['Or Shake It'], ['Give your phone a shake.', 'Adjust the sensitivity,', 'or disable it completely.']);
  copyBlock(t, COPY_SCORE, Infinity, ['Keep Score'], ['Heads and tails, tallied', 'as you go.']);

  const rise = outCubic(seg(t, TAP, TAP + 0.5));
  const zoom = 1 + 0.3 * inOutCubic(seg(t, COPY_SCORE, COPY_SCORE + 0.8));
  const cy = lerp(1700, 540, rise) - (STATS_Y - SH / 2) * (zoom - 1);
  let rot = 0;
  let dx = 0;
  if (t >= SHAKE[0] && t <= SHAKE[1]) {
    const q = t - SHAKE[0];
    const damp = 1 - seg(t, SHAKE[0], SHAKE[1]);
    rot = 0.085 * Math.sin(2 * Math.PI * 7 * q) * damp;
    dx = 16 * Math.sin(2 * Math.PI * 7 * q + 0.8) * damp;
    shakeMarks(1300 + dx, cy, t, damp);
  }
  const s = tally(TAP_FLIPS, t, 25, 22);
  phone(1300 + dx, cy, zoom, rot, () => {
    mainScreen(DARK, {
      coin: coinBy.gw,
      frame: s.frame,
      label: s.label ? (s.label.value === 'H' ? 'HEADS' : 'TAILS') : null,
      value: s.label?.value,
      heads: s.heads,
      tails: s.tails,
    });
    tap(SW / 2, COIN_Y + 20, t, TAP_AT);
  });
}

function shakeMarks(cx, cy, t, damp) {
  ctx.save();
  ctx.strokeStyle = SUB;
  ctx.lineCap = 'round';
  ctx.lineWidth = 7;
  for (const side of [-1, 1]) {
    for (let i = 0; i < 3; i++) {
      ctx.globalAlpha = damp * (0.35 + 0.5 * Math.abs(Math.sin(t * 40 + i)));
      const r = 70 + i * 34;
      ctx.beginPath();
      ctx.arc(cx + side * 250, cy - 330, r, side < 0 ? Math.PI * 0.95 : -Math.PI * 0.2, side < 0 ? Math.PI * 1.2 : Math.PI * 0.05);
      ctx.stroke();
    }
  }
  ctx.restore();
}

// 4. the catalog
const WALL = [];
const CATS = [
  { t: WALL_AT + 2.0, cat: 'state', parts: [{ s: '51', color: GOLD }, { s: ' state quarters' }] },
  { t: WALL_AT + 2.5, cat: 'euro', parts: [{ s: '24', color: GOLD }, { s: ' euro designs' }] },
  { t: WALL_AT + 3.0, cat: 'canada', parts: [{ s: 'Loonie & Toonie' }] },
  { t: WALL_AT + 3.5, cat: 'twoface', parts: [{ s: 'A ' }, { s: 'two-headed', color: GOLD }, { s: ' dollar' }] },
];
const RANDOM_RUN = ['gw', 'loonie', 'spain', 'hi'];
const RANDOM_SWAPS = [RANDOM_AT + 1.0, RANDOM_AT + 1.75, RANDOM_AT + 2.5];
cue(WALL_AT, 'cut');
CATS.forEach((c) => cue(c.t, 'pop'));
cue(RANDOM_AT, 'gather');
RANDOM_SWAPS.forEach((s) => cue(s, 'flick'));

// an easter egg rather than a coin anyone could hold: counted, but never shown
const OFF_WALL = ['claude'];

function buildWall() {
  const tiles = COINS.filter((c) => !OFF_WALL.includes(c.prefix)).map((c) => {
    const quarter = c.group === 'US' && !['gw', 'jfk', 'sacagawea'].includes(c.prefix);
    const cat = quarter ? 'state' : c.group === 'CANADA' ? 'canada' : c.group === 'EURO' ? 'euro' : c.prefix === 'twoface' ? 'twoface' : 'other';
    // show whichever face tells the coins apart: the states differ on the reverse, the euros on the obverse
    const face = quarter || c.group === 'CANADA' ? 'tails' : 'heads';
    return { kind: 'coin', coin: c, face, cat };
  });
  tiles.push({ kind: 'custom', coin: CUSTOM, face: 'heads', cat: 'other' });
  tiles.push({ kind: 'random', cat: 'other' });
  const r = rng(7);
  const dmax = Math.hypot(5.5, 3);
  tiles.forEach((tile, i) => {
    const row = Math.floor(i / 12);
    const inRow = Math.min(12, tiles.length - row * 12);
    const c = (i % 12) + (12 - inRow) / 2;
    tile.x = W / 2 + (c - 5.5) * 140;
    tile.y = H / 2 + (row - 3) * 140;
    const d = Math.hypot(c - 5.5, row - 3) / dmax;
    tile.d = d;
    tile.tIn = WALL_AT + 0.05 + 0.9 * d + (r() - 0.5) * 0.1;
    WALL.push(tile);
  });
  WALL.filter((w) => w.kind === 'coin').forEach((w) => cue(w.tIn, 'tick'));
}

function faceOf(tile) {
  return tile.face === 'tails' ? tile.coin.tails : tile.coin.heads;
}

function wallTile(tile, t, x, y, d, alpha) {
  const k = Math.floor((t - tile.tIn) * FPS / 2);
  if (k < 0 || alpha <= 0) return;
  ctx.save();
  ctx.globalAlpha = alpha;
  if (tile.kind === 'random') {
    ctx.translate(x, y);
    ctx.scale(k === 0 ? 0.03 : Math.min(k, 4) / 4, 1);
    ctx.strokeStyle = DARK.primary;
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.arc(0, 0, d / 2 - 2, 0, Math.PI * 2);
    ctx.stroke();
    txt('?', 0, centred(0, d * 0.5), { size: d * 0.5, weight: 700, color: DARK.primary });
  } else if (k === 0) {
    ctx.drawImage(tile.coin.edge, x - d / 2, y - d / 2, d, d);
  } else {
    const w = (d * Math.min(k, 4)) / 4;
    ctx.drawImage(faceOf(tile), x - w / 2, y - d / 2, w, d);
  }
  ctx.restore();
}

function sceneWall(t) {
  background(t);
  const zoom = 1 + 0.05 * seg(t, WALL_AT, RANDOM_AT);
  const active = [...CATS].reverse().find((c) => t >= c.t);
  const idx = active ? CATS.indexOf(active) : -1;
  const dim = 0.16;

  ctx.save();
  ctx.translate(W / 2, H / 2);
  ctx.scale(zoom, zoom);
  ctx.translate(-W / 2, -H / 2);
  for (const tile of WALL) {
    let alpha = 1;
    let s = 1;
    if (idx >= 0) {
      const now = tile.cat === active.cat ? 1 : dim;
      const before = idx === 0 ? 1 : tile.cat === CATS[idx - 1].cat ? 1 : dim;
      const p = seg(t, active.t, active.t + 0.12);
      alpha = lerp(before, now, p);
      if (tile.cat === active.cat) s = 1 + 0.08 * p;
    }
    let x = tile.x;
    let y = tile.y;
    if (t >= RANDOM_AT) {
      alpha = lerp(alpha, 1, seg(t, RANDOM_AT, RANDOM_AT + 0.1));
      const p = inCubic(seg(t, RANDOM_AT + 0.12 * tile.d, RANDOM_AT + 0.12 * tile.d + 0.3));
      x = lerp(x, W / 2, p);
      y = lerp(y, 540, p);
      s *= 1 - 0.7 * p;
      alpha = lerp(alpha, 0, p);
    }
    wallTile(tile, t, x, y, 124 * s, alpha);
  }
  ctx.restore();

  // the count, over a pool of shade so it reads against the coins
  const cIn = seg(t, WALL_AT + 0.1, WALL_AT + 0.35);
  const cOut = 1 - seg(t, WALL_AT + 1.75, WALL_AT + 1.9);
  const ca = Math.min(cIn, cOut);
  if (ca > 0) {
    ctx.save();
    ctx.globalAlpha = ca;
    const g = ctx.createRadialGradient(W / 2, 540, 0, W / 2, 540, 560);
    g.addColorStop(0, 'rgba(10, 9, 22, 0.92)');
    g.addColorStop(0.55, 'rgba(10, 9, 22, 0.78)');
    g.addColorStop(1, 'rgba(10, 9, 22, 0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
    // scaled to the whole catalog, so the count ends on what the app ships and not on the tiles shown
    const coins = WALL.filter((w) => w.kind === 'coin');
    const landed = coins.filter((w) => t >= w.tIn + 8 / FPS).length;
    const n = Math.round((landed * COINS.length) / coins.length);
    txt(String(n), W / 2, 590, { size: 300, weight: 900, color: GOLD, ls: -6 });
    txt('Coins to Flip', W / 2, 690, { size: 70, weight: 700, color: INK });
    ctx.restore();
  }

  if (active) {
    const s = pop(t, active.t, 0.28) * (1 - inCubic(seg(t, RANDOM_AT, RANDOM_AT + 0.15)));
    const size = 76;
    const widths = active.parts.map((p) => measure(p.s, size, 900, -1));
    const total = widths.reduce((a, b) => a + b, 0);
    scaled(W / 2, 540, s, () => {
      ctx.save();
      ctx.shadowColor = 'rgba(0, 0, 0, 0.5)';
      ctx.shadowBlur = 40;
      ctx.beginPath();
      ctx.roundRect(W / 2 - total / 2 - 56, 540 - 66, total + 112, 132, 66);
      ctx.fillStyle = 'rgba(14, 13, 30, 0.9)';
      ctx.fill();
      ctx.restore();
      ctx.beginPath();
      ctx.roundRect(W / 2 - total / 2 - 56, 540 - 66, total + 112, 132, 66);
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.14)';
      ctx.lineWidth = 2;
      ctx.stroke();
      let x = W / 2 - total / 2;
      active.parts.forEach((p, i) => {
        txt(p.s, x, centred(540, size), { size, weight: 900, color: p.color ?? INK, align: 'left', ls: -1 });
        x += widths[i];
      });
    });
  }

  if (t >= RANDOM_AT) randomCoin(t);
}

function wallFace(prefix) {
  const tile = WALL.find((w) => w.coin?.prefix === prefix);
  return faceOf(tile);
}

function randomCoin(t) {
  const o = { size: 110, weight: 900, color: INK, align: 'center', ls: -2 };
  kparts([{ s: 'Or Go ' }, { s: 'Random', color: GOLD }], W / 2, 200, o, t, RANDOM_AT + 0.1);
  kline('Let the app pick a surprise coin.', W / 2, 930, { ...CAP, align: 'center', size: 46 }, t, RANDOM_AT + 0.35);

  const s = pop(t, RANDOM_AT + 0.25, 0.4);
  if (s <= 0) return;
  // each swap is a half turn: the old coin narrows to its edge and a new one opens out of it
  let i = 0;
  while (i < RANDOM_SWAPS.length && t >= RANDOM_SWAPS[i]) i++;
  const cur = wallFace(RANDOM_RUN[i]);
  const prev = i > 0 ? wallFace(RANDOM_RUN[i - 1]) : cur;
  const since = i > 0 ? Math.floor((t - RANDOM_SWAPS[i - 1]) * FPS) : 99;
  const d = 440 * s;
  const cx = W / 2;
  const cy = 560;
  glow(cx, cy, 420, 'rgba(255, 213, 103, 0.10)');
  let img = cur;
  let k = 4;
  if (since < 4) {
    img = prev;
    k = 4 - since;
  } else if (since === 4) {
    ctx.drawImage(IMG.edge, cx - d / 2, cy - d / 2, d, d);
    return;
  } else if (since < 9) {
    k = since - 4;
  }
  const w = (d * k) / 4;
  ctx.drawImage(img, cx - w / 2, cy - d / 2, w, d);
}

// 5 + 6. the custom coin, cropped and then named
const C = CUSTOM_AT;
const COPY_MAKE = [C + 0.1, C + 5.5];
const COPY_NAME = C + 5.7;
const CROP_H = [C + 0.5, C + 1.5];
const OK_1 = C + 1.75;
const SLIDE_1 = [C + 1.85, C + 2.2];
const DRAG = [C + 2.4, C + 3.0];
const OK_2 = C + 3.2;
const BACK = [C + 3.3, C + 3.75];
const TAP_2 = C + 4.1;
const CUSTOM_FLIPS = [mkFlip('HT', C + 5.0), mkFlip('TH', C + 8.0)];
const RETYPE = [C + 5.9, C + 6.4];
cue(C, 'cut');
cue(CROP_H[0], 'pinch', { until: CROP_H[1] });
cue(OK_1, 'tap');
cue(SLIDE_1[0], 'swish');
cue(OK_2, 'tap');
cue(BACK[0], 'swish');
cue(TAP_2, 'tap');
flipCues(CUSTOM_FLIPS);
cue(COPY_NAME, 'swish');
for (let i = 0; i < 10; i++) cue(RETYPE[0] + (i * (RETYPE[1] - RETYPE[0])) / 10, 'key');

function retyped(from, to, t) {
  const p = seg(t, RETYPE[0], RETYPE[1]);
  const steps = from.length + to.length;
  const k = Math.floor(p * steps);
  return k <= from.length ? from.slice(0, from.length - k) : to.slice(0, k - from.length);
}

function sceneCustom(t) {
  background(t);
  copyBlock(t, ...COPY_MAKE, ['Make Your', 'Own Coin'], ['Pick a photo for each side,', 'then pinch and drag to frame it.']);
  copyBlock(t, COPY_NAME, Infinity, ['Name the', 'Sides'], ['Yes or no. Pizza or tacos.', 'Call them whatever you like.']);

  phone(1300, 540, 1, 0, () => {
    if (t < SLIDE_1[1]) {
      const crop = lerpCrop(CROP_HEADS.from, CROP_HEADS.to, inOutCubic(seg(t, CROP_H[0], CROP_H[1])));
      const slide = inOutCubic(seg(t, SLIDE_1[0], SLIDE_1[1]));
      ctx.save();
      ctx.translate(-SW * 0.3 * slide, 0);
      cropScreen(DARK, IMG.pizza, crop, 'heads');
      ctx.restore();
      // the pinch: two fingertips spreading from the middle of the circle
      if (t >= CROP_H[0] - 0.1 && t <= CROP_H[1] + 0.1) {
        const p = inOutCubic(seg(t, CROP_H[0], CROP_H[1]));
        const spread = lerp(38, 96, p);
        const a = seg(t, CROP_H[0] - 0.1, CROP_H[0]) * (1 - seg(t, CROP_H[1], CROP_H[1] + 0.1));
        for (const sgn of [-1, 1]) {
          ctx.save();
          ctx.globalAlpha = 0.55 * a;
          ctx.fillStyle = '#FFFFFF';
          ctx.beginPath();
          ctx.arc(SW / 2 + sgn * spread * 0.7, CROP_CY + sgn * spread * 0.7, 19, 0, Math.PI * 2);
          ctx.fill();
          ctx.restore();
        }
      }
      tap(OK_X, ACTIONS_Y, t, OK_1);
      if (slide > 0) {
        ctx.save();
        ctx.translate(SW * (1 - slide), 0);
        cropScreen(DARK, IMG.taco, CROP_TAILS.from, 'tails');
        ctx.restore();
      }
    } else if (t < BACK[1]) {
      const crop = lerpCrop(CROP_TAILS.from, CROP_TAILS.to, inOutCubic(seg(t, DRAG[0], DRAG[1])));
      // popping back to the main screen: it grows back from 0.75 and fades in while the crop
      // screen leaves by the end edge, the same pair of animators the app runs
      const p = inOutCubic(seg(t, BACK[0], BACK[1]));
      if (p > 0) {
        ctx.save();
        ctx.globalAlpha = p;
        const s = lerp(0.75, 1, p);
        ctx.translate(SW / 2, SH / 2);
        ctx.scale(s, s);
        ctx.translate(-SW / 2, -SH / 2);
        customMain(t);
        ctx.restore();
      }
      ctx.save();
      ctx.translate(SW * p, 0);
      cropScreen(DARK, IMG.taco, crop, 'tails');
      const fx = SW / 2 + lerp(0.16, -0.03, inOutCubic(seg(t, DRAG[0], DRAG[1]))) * CROP_V * 0.6;
      const fy = CROP_CY + 40 + lerp(0.1, -0.02, inOutCubic(seg(t, DRAG[0], DRAG[1]))) * CROP_V * 0.6;
      finger(fx, fy, t, DRAG[0], DRAG[1] + 0.02);
      tap(OK_X, ACTIONS_Y, t, OK_2);
      ctx.restore();
    } else {
      customMain(t);
      tap(SW / 2, COIN_Y + 20, t, TAP_2);
    }
  });
}

function customMain(t) {
  const s = tally(CUSTOM_FLIPS, t, 27, 23);
  const named = t >= RETYPE[1];
  let label = null;
  if (s.label) {
    const plain = s.label.value === 'H' ? 'HEADS' : 'TAILS';
    const custom = s.label.value === 'H' ? 'PIZZA' : 'TACOS';
    label = t < RETYPE[0] ? plain : t < RETYPE[1] ? retyped('TAILS', 'TACOS', t) : custom;
  }
  mainScreen(DARK, {
    coin: CUSTOM,
    frame: s.frame,
    label,
    value: s.label?.value,
    heads: s.heads,
    tails: s.tails,
    labels: named ? ['PIZZA', 'TACOS'] : ['HEADS', 'TAILS'],
  });
  // the caret that does the retyping
  if (t >= RETYPE[0] - 0.1 && t < RETYPE[1] + 0.25 && label) {
    const w = measure(label, 72, 700);
    ctx.save();
    ctx.fillStyle = DARK.tails;
    ctx.globalAlpha = Math.floor(t * 8) % 2 ? 0.9 : 0.3;
    ctx.fillRect(SW / 2 + w / 2 + 4, RESULT_Y - 34, 4, 68);
    ctx.restore();
  }
}

// 7. streaks
const STREAK_FLIPS = [mkFlip('HH', STREAK_AT + 0.5, 2), mkFlip('HH', STREAK_AT + 1.0, 2), mkFlip('HH', STREAK_AT + 1.5, 2)];
const RECORD = STREAK_FLIPS[2].land;
cue(STREAK_AT, 'cut');
flipCues(STREAK_FLIPS, 'fanfare');
cue(RECORD, 'burst');

function sceneStreak(t) {
  background(t);
  const s = flipAt(STREAK_FLIPS, t);
  const cx = W / 2;
  const cy = 500;
  const n = 7 + s.landed.length;
  const last = s.landed[s.landed.length - 1];
  const bump = last ? 1 + 0.35 * (1 - outCubic(seg(t, last.land, last.land + 0.3))) : 1;
  if (t >= RECORD) {
    glow(cx, cy, 700 * outCubic(seg(t, RECORD, RECORD + 0.5)), 'rgba(255, 213, 103, 0.18)');
    particles(t, cx, cy);
  }
  if (s.cur) drawSpin(coinBy.loonie, s.cur, t, cx, cy, 420);
  else drawFrame(coinBy.loonie, ['a', 4], cx, cy, 420);
  shock(cx, cy, 215, t, RECORD, GOLD, 260, 0.7);
  shock(cx, cy, 215, t, RECORD + 0.12, DARK.heads, 220, 0.7);
  resultLine(cx, 850, 'HEADS', n, DARK.heads, 150, bump);
  kline('Chase a Streak', W / 2, 180, { size: 104, weight: 900, color: INK, align: 'center', ls: -2 }, t, STREAK_AT + 0.05);
  kline('Beat your best run of 10 or more for a fanfare.', W / 2, 990, { ...CAP, align: 'center', size: 42 }, t, RECORD + 0.3);
}

// a spray of little coins thrown up out of the record-setting landing
function particles(t, cx, cy) {
  const r = rng(11);
  const pool = ['gw', 'loonie', 'toonie', 'sacagawea', 'jfk', 'germany', 'hi'];
  const dt = t - RECORD;
  for (let i = 0; i < 44; i++) {
    // thrown upward and outward, and gone before gravity brings them down across the type
    const a = -Math.PI * (0.08 + 0.84 * r());
    const v = 700 + r() * 900;
    const d = 38 + r() * 34;
    const spin = 6 + r() * 10;
    const c = coinBy[pool[i % pool.length]];
    const x = cx + Math.cos(a) * v * dt;
    const y = cy + Math.sin(a) * v * dt - 500 * dt + 0.5 * 1800 * dt * dt;
    const alpha = 1 - seg(dt, 0.6, 1.05);
    if (alpha <= 0 || y > H + 60) continue;
    const sx = Math.abs(Math.cos(spin * dt + i));
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.translate(x, y);
    ctx.scale(Math.max(0.05, sx), 1);
    ctx.drawImage(i % 2 ? c.heads : c.tails, -d / 2, -d / 2, d, d);
    ctx.restore();
  }
}

// 8. themes and the rest of the feature list
const THEMES = [
  { t: THEMES_AT, th: DARK },
  { t: THEMES_AT + 0.5, th: LIGHT },
  { t: THEMES_AT + 1.0, th: MOSS },
  { t: THEMES_AT + 1.5, th: OCEAN },
  { t: THEMES_AT + 2.0, th: CORAL },
];
const CHIPS = [
  { t: THEMES_AT + 0.1, s: 'Light & dark', x: 690, y: 330, align: 'right', dot: DARK.heads },
  { t: THEMES_AT + 0.55, s: 'Material You', x: 1230, y: 430, align: 'left', dot: '#8ECFF2' },
  { t: THEMES_AT + 1.05, s: 'Sound & haptics', x: 690, y: 560, align: 'right', dot: DARK.tails },
  { t: THEMES_AT + 1.55, s: '13 languages', x: 1230, y: 680, align: 'left', dot: GOLD },
  { t: THEMES_AT + 2.05, s: 'Secure random', x: 690, y: 790, align: 'right', dot: DARK.primary },
];
cue(THEMES_AT, 'rise');
THEMES.slice(1).forEach((th) => cue(th.t, 'theme'));
CHIPS.forEach((c) => cue(c.t, 'pop'));

function sceneThemes(t) {
  background(t);
  const rise = outCubic(seg(t, THEMES_AT, THEMES_AT + 0.35));
  const cy = lerp(1700, 540, rise);
  let i = 0;
  while (i + 1 < THEMES.length && t >= THEMES[i + 1].t) i++;
  const state = {
    coin: coinBy.loonie,
    frame: ['a', 4],
    label: 'HEADS',
    value: 'H',
    streak: 10,
    heads: 37,
    tails: 24,
  };
  phone(W / 2, cy, 1, 0, () => {
    const cur = THEMES[i];
    const prev = THEMES[Math.max(0, i - 1)];
    const p = inOutCubic(seg(t, cur.t, cur.t + 0.4));
    if (i > 0 && p < 1) {
      mainScreen(prev.th, state);
      ctx.save();
      ctx.beginPath();
      const fromRight = i % 2 === 1;
      ctx.arc(fromRight ? SW : 0, fromRight ? 0 : SH, Math.max(0.1, 1000 * p), 0, Math.PI * 2);
      ctx.clip();
      mainScreen(cur.th, state);
      ctx.restore();
    } else {
      mainScreen(cur.th, state);
    }
  });
  for (const c of CHIPS) chip(c, t);
}

function chip(c, t) {
  const s = pop(t, c.t, 0.35);
  if (s <= 0) return;
  const size = 44;
  const w = measure(c.s, size, 700) + 110;
  const h = 92;
  const x0 = c.align === 'right' ? c.x - w : c.x;
  scaled(c.align === 'right' ? c.x - w / 2 : c.x + w / 2, c.y, s, () => {
    ctx.save();
    ctx.globalAlpha = clamp(s);
    ctx.beginPath();
    ctx.roundRect(x0, c.y - h / 2, w, h, h / 2);
    ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.18)';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(x0 + 44, c.y, 11, 0, Math.PI * 2);
    ctx.fillStyle = c.dot;
    ctx.fill();
    ctx.restore();
    txt(c.s, x0 + 72, centred(c.y, size), { size, weight: 700, color: INK, align: 'left' });
  });
}

// 9. end card
cue(END, 'wipe');
cue(END, 'final');

function sceneEnd(t) {
  ctx.save();
  brandWipe(t, END, W / 2, 540);
  logo(W / 2, 380, 380, t, END + 0.12);
  kline('Simple Coin Flip', W / 2, 790, { size: 140, weight: 900, color: '#FFFFFF', align: 'center', ls: -3 }, t, END + 0.45);
  kline('Free. No ads. No tracking. Open source.', W / 2, 880, { size: 48, weight: 500, color: '#E8E6FF', align: 'center' }, t, END + 0.75);
  ctx.restore();
}

// ---------------------------------------------------------------------------------------------

const SCENES = [
  { from: 0, to: BRAND_AT + 0.45, draw: sceneHook },
  { from: BRAND_AT, to: TAP, draw: sceneBrand },
  { from: TAP, to: WALL_AT, draw: sceneTap },
  { from: WALL_AT, to: CUSTOM_AT, draw: sceneWall },
  { from: CUSTOM_AT, to: STREAK_AT, draw: sceneCustom },
  { from: STREAK_AT, to: THEMES_AT, draw: sceneStreak },
  { from: THEMES_AT, to: END + 0.45, draw: sceneThemes },
  { from: END, to: DURATION + 1, draw: sceneEnd },
];

// the section starts the soundtrack is arranged around
const MARKS = { drop: BRAND_AT, arp: TAP, wall: WALL_AT, streak: STREAK_AT, record: RECORD, outro: THEMES_AT, end: END };

function renderFrame(t) {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalAlpha = 1;
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, W, H);
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  for (const s of SCENES) if (t >= s.from && t < s.to) s.draw(t);
}

window.setup = setup;
window.renderFrame = renderFrame;
