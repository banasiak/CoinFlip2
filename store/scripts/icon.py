#!/usr/bin/env python3
"""Draws the app icon from one set of shapes and writes every copy of it.

  python3 -I store/scripts/icon.py

The launcher icon is vector drawables, and the copies that have to be pictures (the Play Store icon,
the coin on the feature graphic and the promo video's logo) are rasterized here from the same
shapes, so they cannot drift from it. Standard library only. store/README.md says what each output
is for.
"""
import math
import os
import struct
import zlib

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
RES = os.path.join(ROOT, 'coinflip', 'src', 'main', 'res')
DEBUG_RES = os.path.join(ROOT, 'coinflip', 'src', 'debug', 'res')

# the adaptive icon's canvas is 108 units square, and a launcher's mask shows the middle 72
C = 54.0
R_EDGE, R_RIM, R_FACE = 29.125, 28.56, 25.4
INSET = 0.85  # the face is recessed: its shadow shows at the top left
LIFT = 0.9  # the dollar sign is raised: its shadow falls to the bottom right
# the dollar sign's S is two elliptical bowls joined by a diagonal spine through the center, and its
# bar shows only as a stub above and below it. Angles are in degrees round each bowl, clockwise from
# its right, and the bottom bowl's are the top's turned half round, which keeps the S symmetric: an
# S whose lower tail ran on further than its upper one looked as if it leaned to the right
S_RX, S_RY, S_STROKE = 9.2, 7.8, 5.6
S_END = -35  # where the S's upper end stops
S_SPINE = 145  # where the spine leaves the top bowl
BAR_HALF, BAR_STROKE = 19.2, 4.2  # BAR_HALF is from the center to the end of each stub

BACKGROUND = '#6667AB'  # also the theme's seed in ui/theme/Color.kt
EDGE, RIM, FACE, SHADOW = '#E8B240', '#FFD567', '#EBB646', '#DA9848'

BADGE_RED = '#D50000'
BADGE_TOP, BADGE_BOTTOM = 62, 76
BADGE_MONO_ALPHA = 0.45  # a themed icon is one color, so the band is told from its letters by alpha
LETTER_W, LETTER_GAP, LETTER_TOP, LETTER_BOTTOM, LETTER_STROKE = 6, 2, 65, 73, 1.6

# where the coin sits on the 1024x500 feature graphic, measured from the coin it replaced, and drawn
# wide enough to cover that coin's dark outline as well, which is darkest 151.5 to 152.5px out
FEATURE_CENTER = (254.76, 235.43)
FEATURE_EDGE_PX = 153.2


def dollar(dx=0.0, dy=0.0):
  """The dollar sign as round-ended pens, the S and its two stubs, moved by dx, dy."""
  def bowl(cy, start, end):
    steps = max(2, int(abs(end - start) / 4))
    return [(C + dx + S_RX * math.cos(math.radians(start + (end - start) * i / steps)),
             cy + dy + S_RY * math.sin(math.radians(start + (end - start) * i / steps))) for i in range(steps + 1)]

  # the top bowl runs back over the top and down the left, the spine crosses to the bottom bowl's upper
  # right, and that runs on round the right and under to the left
  s = bowl(C - S_RY, S_END, S_SPINE - 360) + bowl(C + S_RY, S_SPINE - 180, S_END + 180)
  # each stub runs into the S's outer curve, so its round end is hidden under the S
  top, bottom = C + dy - 2 * S_RY, C + dy + 2 * S_RY
  stubs = [[(C + dx, C + dy - BAR_HALF), (C + dx, top)], [(C + dx, bottom), (C + dx, C + dy + BAR_HALF)]]
  return [('pen', [s], S_STROKE), ('pen', stubs, BAR_STROKE)]


def letter_strokes():
  """The badge's DEBUG, as polylines in a block face of straight strokes."""
  total = 5 * LETTER_W + 4 * LETTER_GAP
  x = C - total / 2
  h = LETTER_STROKE / 2
  c = 1.4  # the chamfer on the round letters' corners
  out = []
  for ch in 'DEBUG':
    a, b, t, bo = x + h, x + LETTER_W - h, LETTER_TOP + h, LETTER_BOTTOM - h
    m = (t + bo) / 2
    out.extend({
      'D': [[(a, t), (b - c, t), (b, t + c), (b, bo - c), (b - c, bo), (a, bo), (a, t)]],
      'E': [[(b, t), (a, t), (a, bo), (b, bo)], [(a, m), (b - 0.8, m)]],
      'B': [[(a, m), (b - c, m), (b, m + c), (b, bo - c), (b - c, bo), (a, bo), (a, t), (b - c, t), (b, t + c),
             (b, m - c), (b - c, m)]],
      'U': [[(a, t), (a, bo - c), (a + c, bo), (b - c, bo), (b, bo - c), (b, t)]],
      'G': [[(b, t + c), (b, t), (a, t), (a, bo), (b, bo), (b, m), (x + LETTER_W / 2, m)]],
    }[ch])
    x += LETTER_W + LETTER_GAP
  return out


# shapes: ('circle', cx, cy, r), ('ring', cx, cy, inner, outer), ('rect', x0, y0, x1, y1),
# ('poly', points, rounding), ('strokes', polylines, width) with mitered corners, ('pen', polylines,
# width) with round ends and corners, and, as a clip only, ('union', shapes). The rasterizer draws
# 'strokes' with round ends and corners too, which no picture this writes yet depends on.
# A layer is (color, alpha, shape, clip)
def coin_layers():
  return [
    (EDGE, 1, ('circle', C, C, R_EDGE), None),
    (RIM, 1, ('circle', C, C, R_RIM), None),
    (SHADOW, 1, ('circle', C, C, R_FACE), None),
    (FACE, 1, ('circle', C + INSET, C + INSET, R_FACE), ('circle', C, C, R_FACE)),
    *[(SHADOW, 1, pen, None) for pen in dollar(LIFT, LIFT)],
    *[(RIM, 1, pen, None) for pen in dollar()],
  ]


def monochrome_layers():
  return [
    ('#FFFFFF', 1, ('ring', C, C, R_FACE, R_EDGE), None),
    *[('#FFFFFF', 1, pen, None) for pen in dollar()],
  ]


def badge_layers():
  band = ('rect', 0, BADGE_TOP, 108, BADGE_BOTTOM)
  return [(BADGE_RED, 1, band, None), ('#FFFFFF', 1, ('strokes', letter_strokes(), LETTER_STROKE), None)]


def monochrome_debug_layers():
  # one tint can't cut the letters out of the dollar sign, so the coin is cut away under the band instead
  outside_band = ('union', [('rect', 0, 0, 108, BADGE_TOP), ('rect', 0, BADGE_BOTTOM, 108, 108)])
  coin = [(color, alpha, shape, outside_band) for color, alpha, shape, _ in monochrome_layers()]
  band = ('rect', 0, BADGE_TOP, 108, BADGE_BOTTOM)
  return coin + [
    ('#FFFFFF', BADGE_MONO_ALPHA, band, None),
    ('#FFFFFF', 1, ('strokes', letter_strokes(), LETTER_STROKE), None),
  ]


# --- vector drawables ---------------------------------------------------------------------------

def num(v):
  s = f'{round(v, 3):g}'
  return '0' if s == '-0' else s


def path_data(shape):
  kind = shape[0]
  if kind == 'circle':
    _, cx, cy, r = shape
    return f'M{num(cx - r)},{num(cy)}a{num(r)},{num(r)} 0,1 1,{num(2 * r)},0a{num(r)},{num(r)} 0,1 1,{num(-2 * r)},0Z'
  if kind == 'ring':
    _, cx, cy, inner, outer = shape
    return path_data(('circle', cx, cy, outer)) + path_data(('circle', cx, cy, inner))
  if kind == 'rect':
    _, x0, y0, x1, y1 = shape
    return f'M{num(x0)},{num(y0)}H{num(x1)}V{num(y1)}H{num(x0)}Z'
  if kind == 'poly':
    return 'M' + 'L'.join(f'{num(x)},{num(y)}' for x, y in shape[1]) + 'Z'
  if kind in ('strokes', 'pen'):
    def subpath(line):
      # closed rather than ended where it began, or the meeting point gets two caps and no join
      closed = len(line) > 2 and line[0] == line[-1]
      points = line[:-1] if closed else line
      return 'M' + 'L'.join(f'{num(x)},{num(y)}' for x, y in points) + ('Z' if closed else '')
    return ''.join(subpath(line) for line in shape[1])
  if kind == 'union':
    return ''.join(path_data(s) for s in shape[1])
  raise ValueError(kind)


def argb(color, alpha):
  return f'#{round(alpha * 255):02X}{color[1:].upper()}'


def vector_path(color, alpha, shape, indent):
  pad = ' ' * indent
  attrs = [f'android:pathData="{path_data(shape)}"']
  kind = shape[0]
  if kind in ('strokes', 'pen'):
    attrs += [
      f'android:strokeColor="{argb(color, alpha)}"',
      f'android:strokeWidth="{num(shape[2])}"',
    ]
    attrs += ['android:strokeLineCap="round"', 'android:strokeLineJoin="round"'] if kind == 'pen' else [
      'android:strokeLineJoin="miter"']
  else:
    attrs.append(f'android:fillColor="{argb(color, alpha)}"')
    if kind == 'ring':
      attrs.append('android:fillType="evenOdd"')
    if kind == 'poly' and shape[2]:
      # the stroke, in the fill's color, is what rounds the points
      attrs += [
        f'android:strokeColor="{argb(color, alpha)}"',
        f'android:strokeWidth="{num(2 * shape[2])}"',
        'android:strokeLineJoin="round"',
      ]
  body = ''.join(f'{pad}  {a}\n' for a in attrs)
  return f'{pad}<path\n{body}{pad}  />\n'


def vector(layers):
  out = [
    '<?xml version="1.0" encoding="utf-8"?>\n',
    '<!-- generated by store/scripts/icon.py: edit that and rerun it, not this -->\n',
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n',
    '  android:width="108dp"\n',
    '  android:height="108dp"\n',
    '  android:viewportWidth="108"\n',
    '  android:viewportHeight="108"\n',
    '  >\n',
  ]
  for color, alpha, shape, clip in layers:
    if clip:
      out.append('  <group>\n')
      out.append(f'    <clip-path android:pathData="{path_data(clip)}" />\n')
      out.append(vector_path(color, alpha, shape, 4))
      out.append('  </group>\n')
    else:
      out.append(vector_path(color, alpha, shape, 2))
  out.append('</vector>\n')
  return ''.join(out)


# --- rasterizing --------------------------------------------------------------------------------

def segment_distance(px, py, ax, ay, bx, by):
  dx, dy = bx - ax, by - ay
  t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
  return math.hypot(px - ax - t * dx, py - ay - t * dy)


def inside(shape, x, y):
  kind = shape[0]
  if kind == 'circle':
    return (x - shape[1]) ** 2 + (y - shape[2]) ** 2 <= shape[3] ** 2
  if kind == 'ring':
    d = (x - shape[1]) ** 2 + (y - shape[2]) ** 2
    return shape[3] ** 2 <= d <= shape[4] ** 2
  if kind == 'rect':
    return shape[1] <= x <= shape[3] and shape[2] <= y <= shape[4]
  if kind == 'poly':
    pts, rounding = shape[1], shape[2]
    hit = False
    for i in range(len(pts)):
      (ax, ay), (bx, by) = pts[i], pts[i - 1]
      if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
        hit = not hit
    return hit or any(segment_distance(x, y, *pts[i], *pts[i - 1]) <= rounding for i in range(len(pts)))
  if kind in ('strokes', 'pen'):
    lines, width = shape[1], shape[2]
    return any(segment_distance(x, y, *ln[i], *ln[i + 1]) <= width / 2 for ln in lines for i in range(len(ln) - 1))
  if kind == 'union':
    return any(inside(s, x, y) for s in shape[1])
  raise ValueError(kind)


def bounds(shape):
  kind = shape[0]
  if kind == 'circle':
    return shape[1] - shape[3], shape[2] - shape[3], shape[1] + shape[3], shape[2] + shape[3]
  if kind == 'ring':
    return shape[1] - shape[4], shape[2] - shape[4], shape[1] + shape[4], shape[2] + shape[4]
  if kind == 'rect':
    return shape[1:5]
  pts = shape[1] if kind == 'poly' else [p for ln in shape[1] for p in ln]
  pad = shape[2] if kind == 'poly' else shape[2] / 2
  xs, ys = [p[0] for p in pts], [p[1] for p in pts]
  return min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad


class Image:
  def __init__(self, width, height, pixels=None):
    self.width, self.height = width, height
    self.pixels = pixels or [[0.0, 0.0, 0.0, 0.0] for _ in range(width * height)]

  def draw(self, layers, origin_x, origin_y, scale, samples=4):
    """Draws layers whose canvas unit (0, 0) lands at origin, at scale pixels to the unit."""
    for color, alpha, shape, clip in layers:
      rgb = [int(color[i:i + 2], 16) for i in (1, 3, 5)]
      x0, y0, x1, y1 = bounds(shape)
      for py in range(max(0, int(origin_y + y0 * scale) - 1), min(self.height, int(origin_y + y1 * scale) + 2)):
        for px in range(max(0, int(origin_x + x0 * scale) - 1), min(self.width, int(origin_x + x1 * scale) + 2)):
          hits = 0
          for sy in range(samples):
            uy = (py + (sy + 0.5) / samples - origin_y) / scale
            for sx in range(samples):
              ux = (px + (sx + 0.5) / samples - origin_x) / scale
              if inside(shape, ux, uy) and (clip is None or inside(clip, ux, uy)):
                hits += 1
          if hits:
            self.blend(py * self.width + px, rgb, alpha * hits / (samples * samples))

  def blend(self, i, rgb, a):
    p = self.pixels[i]
    below = p[3] / 255
    out = a + below * (1 - a)
    for c in range(3):
      p[c] = (rgb[c] * a + p[c] * below * (1 - a)) / out
    p[3] = out * 255

  def fill(self, color):
    rgb = [int(color[i:i + 2], 16) for i in (1, 3, 5)]
    self.pixels = [[*rgb, 255.0] for _ in range(self.width * self.height)]

  def write_png(self, path, alpha=True):
    channels = 4 if alpha else 3
    raw, plain, previous = bytearray(), bytearray(), bytes(self.width * channels)
    for y in range(self.height):
      line = bytes(int(round(v)) for p in self.pixels[y * self.width:(y + 1) * self.width] for v in p[:channels])
      # each row takes whichever of PNG's filters leaves the smallest residuals, the usual heuristic
      candidates = [line]
      for kind in (1, 2, 4):
        out = bytearray(len(line))
        for x in range(len(line)):
          left = line[x - channels] if x >= channels else 0
          up = previous[x]
          if kind == 1:
            out[x] = (line[x] - left) & 255
          elif kind == 2:
            out[x] = (line[x] - up) & 255
          else:
            corner = previous[x - channels] if x >= channels else 0
            pa, pb, pc = abs(up - corner), abs(left - corner), abs(left + up - 2 * corner)
            out[x] = (line[x] - (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
        candidates.append(bytes(out))
      kinds = (0, 1, 2, 4)
      best = min(range(4), key=lambda i: sum(b if b < 128 else 256 - b for b in candidates[i]))
      raw.append(kinds[best])
      raw.extend(candidates[best])
      plain.append(0)
      plain.extend(line)
      previous = line
    # flat color compresses better unfiltered, a photograph better filtered, so keep the smaller
    data = min(zlib.compress(bytes(raw), 9), zlib.compress(bytes(plain), 9), key=len)

    def chunk(kind, data):
      return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack('>IIBBBBB', self.width, self.height, 8, 6 if alpha else 2, 0, 0, 0)
    with open(path, 'wb') as f:
      f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', data) +
              chunk(b'IEND', b''))


def read_png(path):
  """Reads an 8-bit RGB or RGBA PNG, which is all this script is ever handed."""
  with open(path, 'rb') as f:
    data = f.read()
  i, idat = 8, b''
  while i < len(data):
    (length,) = struct.unpack('>I', data[i:i + 4])
    kind, body = data[i + 4:i + 8], data[i + 8:i + 8 + length]
    i += 12 + length
    if kind == b'IHDR':
      width, height, depth, color_type, _, _, interlace = struct.unpack('>IIBBBBB', body)
      if depth != 8 or color_type not in (2, 6) or interlace:
        raise SystemExit(f'{path}: only 8-bit, non-interlaced RGB or RGBA is supported')
    elif kind == b'IDAT':
      idat += body
  bpp = 4 if color_type == 6 else 3
  stride = width * bpp
  raw = zlib.decompress(idat)
  pixels, previous, pos = [], bytearray(stride), 0
  for _ in range(height):
    kind, line = raw[pos], bytearray(raw[pos + 1:pos + 1 + stride])
    pos += 1 + stride
    for x in range(stride):
      left = line[x - bpp] if x >= bpp else 0
      up = previous[x]
      corner = previous[x - bpp] if x >= bpp else 0
      if kind == 1:
        line[x] = (line[x] + left) & 255
      elif kind == 2:
        line[x] = (line[x] + up) & 255
      elif kind == 3:
        line[x] = (line[x] + (left + up) // 2) & 255
      elif kind == 4:
        pa, pb, pc = abs(up - corner), abs(left - corner), abs(left + up - 2 * corner)
        line[x] = (line[x] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
    for x in range(width):
      p = line[x * bpp:x * bpp + bpp]
      pixels.append([float(p[0]), float(p[1]), float(p[2]), float(p[3]) if bpp == 4 else 255.0])
    previous = line
  return Image(width, height, pixels)


# --- outputs ------------------------------------------------------------------------------------

def write(path, text):
  with open(path, 'w') as f:
    f.write(text)
  print(os.path.relpath(path, ROOT))


def main():
  write(os.path.join(RES, 'drawable', 'ic_launcher_foreground.xml'), vector(coin_layers()))
  write(os.path.join(RES, 'drawable', 'ic_launcher_monochrome.xml'), vector(monochrome_layers()))
  write(os.path.join(RES, 'drawable', 'ic_launcher_debug_badge.xml'), vector(badge_layers()))
  write(os.path.join(DEBUG_RES, 'drawable', 'ic_launcher_monochrome_debug.xml'), vector(monochrome_debug_layers()))

  # Play shows the 512px icon under its own mask, so it is the visible 72 units, full bleed
  store = Image(512, 512)
  store.fill(BACKGROUND)
  scale = 512 / 72
  store.draw(coin_layers(), -18 * scale, -18 * scale, scale)
  path = os.path.join(ROOT, 'store', 'ic_launcher-playstore.png')
  store.write_png(path)  # Play asks for a 32-bit PNG, though nothing in it is transparent
  print(os.path.relpath(path, ROOT))

  # painted onto the banner as it was before this coin, never onto its own last output: the edge's
  # anti-aliased pixels would blend over themselves and darken a little on every run
  feature = read_png(os.path.join(ROOT, 'store', 'feature_graphic_background.png'))
  path = os.path.join(ROOT, 'store', 'feature_graphic.png')
  scale = FEATURE_EDGE_PX / R_EDGE
  feature.draw(coin_layers(), FEATURE_CENTER[0] - C * scale, FEATURE_CENTER[1] - C * scale, scale)
  feature.write_png(path, alpha=False)
  print(os.path.relpath(path, ROOT))

  # the whole 108-unit foreground at 432px, as the promo video's logo has always been drawn from
  logo = Image(432, 432)
  logo.draw(coin_layers(), 0, 0, 4)
  path = os.path.join(ROOT, 'promo', 'logo.png')
  logo.write_png(path)
  print(os.path.relpath(path, ROOT))


if __name__ == '__main__':
  main()
