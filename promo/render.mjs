// Renders sizzle.html to out/sizzle.mp4, one screenshot per frame, with a soundtrack synthesized
// from the cues the page schedules.
//
//   node render.mjs                  the whole video
//   node render.mjs --still 2.5,12.8 single frames, as out/still-<t>.png
//   node render.mjs --audio          the soundtrack alone, and its buses, as out/stem-*.wav
//
// ffmpeg is taken from $FFMPEG, else from the PATH, and needs libx264.

import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { synthesize } from './audio.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const drawable = path.join(root, 'coinflip/src/main/res/drawable');
const out = path.join(here, 'out');

// the coin catalog, read from the app's own enum so the video can never list a coin it no longer ships
function catalog() {
  const src = fs.readFileSync(path.join(root, 'coinflip/src/main/java/com/banasiak/coinflip/common/CoinType.kt'), 'utf8');
  const rows = [...src.matchAll(/^\s+([A-Z_]+)\("([a-z]+)", "([^"]+)", ([A-Z]+)\)/gm)];
  return rows
    .filter(([, id]) => id !== 'RANDOM')
    .map(([, , prefix, name, group]) => ({
      prefix,
      name,
      group,
      headsUrl: resolveDrawable(`${prefix}_heads`),
      tailsUrl: resolveDrawable(`${prefix}_tails`),
    }));
}

// the shared faces (every state quarter's heads, every euro's tails) are <bitmap> aliases
function resolveDrawable(name) {
  for (const ext of ['webp', 'png']) {
    if (fs.existsSync(path.join(drawable, `${name}.${ext}`))) return `../coinflip/src/main/res/drawable/${name}.${ext}`;
  }
  const alias = fs.readFileSync(path.join(drawable, `${name}.xml`), 'utf8').match(/@drawable\/(\w+)/);
  if (!alias) throw new Error(`${name} is neither a bitmap nor an alias of one`);
  return resolveDrawable(alias[1]);
}

const TYPES = {
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.webp': 'image/webp',
  '.png': 'image/png',
  '.woff2': 'font/woff2',
};

function serve() {
  const coins = JSON.stringify(catalog());
  const server = http.createServer((req, res) => {
    const url = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    if (url === '/promo/coins.json') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(coins);
      return;
    }
    const file = path.join(root, path.normalize(url));
    if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      res.writeHead(404);
      res.end();
      return;
    }
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] ?? 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

function encoder(file, fps, audio) {
  const args = [
    '-y', '-loglevel', 'error',
    '-f', 'image2pipe', '-framerate', String(fps), '-i', '-',
    '-i', audio,
    // screenshots are sRGB; without an explicit matrix the conversion is BT.601, which HD players do not assume
    '-vf', 'scale=out_color_matrix=bt709:out_range=tv',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-profile:v', 'high', '-pix_fmt', 'yuv420p',
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-af', 'loudnorm=I=-14:TP=-1.5:LRA=11', '-ar', '48000',
    '-c:a', 'aac', '-b:a', '256k',
    '-movflags', '+faststart', '-shortest',
    file,
  ];
  return spawn(process.env.FFMPEG ?? 'ffmpeg', args, { stdio: ['pipe', 'inherit', 'inherit'] });
}

async function main() {
  const argv = process.argv.slice(2);
  const stillArg = argv.indexOf('--still');
  const stills = stillArg >= 0 ? argv[stillArg + 1].split(',').map(Number) : null;

  fs.mkdirSync(out, { recursive: true });
  const server = await serve();
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
    const errors = [];
    page.on('pageerror', (e) => errors.push(e));
    page.on('console', (m) => console.log(`[page] ${m.text()}`));
    await page.goto(`http://127.0.0.1:${server.address().port}/promo/sizzle.html`);
    const meta = await page.evaluate(() => window.setup());
    const draw = async (t) => {
      await page.evaluate((time) => window.renderFrame(time), t);
      if (errors.length) throw errors[0];
    };

    if (stills) {
      for (const t of stills) {
        await draw(t);
        await page.screenshot({ path: path.join(out, `still-${t.toFixed(2)}.png`) });
      }
      return;
    }

    const wav = path.join(out, 'soundtrack.wav');
    if (argv.includes('--audio')) {
      // the soundtrack alone, with its buses beside it, for balancing the mix without re-rendering
      const parts = synthesize(meta.cues, meta.duration, { stems: true });
      for (const [name, data] of Object.entries(parts)) fs.writeFileSync(path.join(out, `stem-${name}.wav`), data);
      return;
    }
    fs.writeFileSync(wav, synthesize(meta.cues, meta.duration));
    const frames = Math.round(meta.duration * meta.fps);
    const enc = encoder(path.join(out, 'sizzle.mp4'), meta.fps, wav);
    const done = new Promise((resolve, reject) => {
      enc.on('close', (code) => (code ? reject(new Error(`ffmpeg exited with ${code}`)) : resolve()));
    });
    const started = Date.now();
    for (let f = 0; f < frames; f++) {
      await draw(f / meta.fps);
      const png = await page.screenshot({ type: 'png' });
      if (!enc.stdin.write(png)) await new Promise((r) => enc.stdin.once('drain', r));
      if (f % 50 === 0) process.stdout.write(`\rframe ${f}/${frames}  ${((Date.now() - started) / 1000).toFixed(0)}s`);
    }
    enc.stdin.end();
    await done;
    process.stdout.write(`\rwrote out/sizzle.mp4 (${frames} frames) in ${((Date.now() - started) / 1000).toFixed(0)}s\n`);
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
