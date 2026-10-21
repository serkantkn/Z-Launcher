"""Zeta's social media images: each design in its device's frame, on its own colours."""
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SC = os.path.dirname(os.path.abspath(__file__))
# The device art (store/video/frames.py) and the screenshots the images are made from.
FR = os.environ.get('FRAMES_DIR', os.path.join(SC, '..', 'video', 'work', 'frames'))
SH = os.environ.get('SHOTS_DIR', os.path.join(SC, 'work'))
OUT = os.environ.get('SOCIAL_OUT', SC)
os.makedirs(OUT, exist_ok=True)
ROOT = os.path.abspath(os.path.join(SC, '..', '..'))
FONT = os.path.join(ROOT, 'app/src/main/res/font/')
LIGHT, SEMI, BOLD = FONT + 'selawik_light.ttf', FONT + 'selawik_semilight.ttf', FONT + 'selawik_semibold.ttf'
MARK = Image.open(os.path.join(ROOT, 'store/play_store_icon.png')).convert('RGBA')
PINK = (255, 61, 154)

DEV = {
    'phone': dict(back='pixel_8_back.png', mask='pixel_8_mask.png', fw=1187, fh=2513, sx=49, sy=55, sw=1080, sh=2400),
    'tablet': dict(back='pixel_tablet_back.png', mask='pixel_tablet_mask.png', fw=2798, fh=1837, sx=119, sy=117, sw=2560, sh=1600),
    'tv': dict(back='tv_back.png', fore='tv_fore.png', fw=2090, fh=1255, sx=85, sy=59, sw=1920, sh=1080),
}


def hexc(h):
    h = h.replace('0x', '').replace('#', '')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ground(w, h, c0, c1, c2, glow=(0.75, 0.25)):
    """Three colours along the diagonal, a soft light in one corner: the video's grounds, still."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    t = (x / w * 0.55 + y / h * 0.45)
    a, b, c = (np.array(hexc(k), np.float32) for k in (c0, c1, c2))
    t3 = t[..., None]
    col = np.where(t3 < 0.5, a + (b - a) * (t3 / 0.5), b + (c - b) * ((t3 - 0.5) / 0.5))
    gx, gy = glow
    d = np.sqrt(((x / w - gx) * w / max(w, h)) ** 2 + ((y / h - gy) * h / max(w, h)) ** 2)
    col = col + 70 * np.clip(1 - d / 0.55, 0, 1)[..., None] ** 2
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), 'RGB').convert('RGBA')


def device(kind, shot, width):
    d = DEV[kind]
    canvas = Image.new('RGBA', (d['fw'], d['fh']), (0, 0, 0, 0))
    scr = Image.open(os.path.join(SH, shot)).convert('RGBA').resize((d['sw'], d['sh']), Image.LANCZOS)
    back = Image.open(os.path.join(FR, d['back'])).convert('RGBA')
    if kind == 'tv':
        canvas.alpha_composite(back)
        canvas.alpha_composite(scr, (d['sx'], d['sy']))
        canvas.alpha_composite(Image.open(os.path.join(FR, d['fore'])).convert('RGBA'))
    else:
        canvas.alpha_composite(scr, (d['sx'], d['sy']))
        canvas.alpha_composite(back)
        canvas.alpha_composite(Image.open(os.path.join(FR, d['mask'])).convert('RGBA'), (d['sx'], d['sy']))
    h = round(width * d['fh'] / d['fw'])
    return canvas.resize((width, h), Image.LANCZOS)


def place(img, dev, x, y, shadow=0.55):
    """The device with a soft shadow under it."""
    pad = max(dev.width, dev.height) // 10
    s = Image.new('RGBA', (dev.width + 2 * pad, dev.height + 2 * pad), (0, 0, 0, 0))
    a = dev.split()[3].point(lambda v: int(v * shadow))
    s.paste(Image.new('RGBA', dev.size, (0, 0, 0, 255)), (pad, pad), a)
    s = s.filter(ImageFilter.GaussianBlur(pad / 2.6))
    img.alpha_composite(s, (int(x - pad + pad * 0.12), int(y - pad + pad * 0.3)))
    img.alpha_composite(dev, (int(x), int(y)))


def text(img, xy, s, font, size, fill=(255, 255, 255, 255), anchor='la'):
    d = ImageDraw.Draw(img)
    d.text(xy, s, font=ImageFont.truetype(font, size), fill=fill, anchor=anchor)


def width_of(s, font, size):
    return ImageDraw.Draw(Image.new('RGB', (1, 1))).textlength(s, font=ImageFont.truetype(font, size))


def mark(img, x, y, size):
    img.alpha_composite(MARK.resize((size, size), Image.LANCZOS), (int(x), int(y)))


def brand(img, x, y, size, anchor='left', soon=True, soon_fill=PINK + (255,)):
    """Zeta's mark and name, "çok yakında" after them."""
    m = int(size * 1.15)
    label = 'Zeta'
    w = m + size * 0.35 + width_of(label, LIGHT, size)
    if soon:
        w += size * 0.6 + width_of('çok yakında', BOLD, int(size * 0.62))
    if anchor == 'center':
        x = x - w / 2
    elif anchor == 'right':
        x = x - w
    mark(img, x, y - m / 2, m)
    tx = x + m + size * 0.35
    text(img, (tx, y), label, LIGHT, size, anchor='lm')
    if soon:
        text(img, (tx + width_of(label, LIGHT, size) + size * 0.6, y + size * 0.04), 'çok yakında', BOLD, int(size * 0.62), fill=soon_fill, anchor='lm')


def save(img, name):
    img.convert('RGB').save(os.path.join(OUT, name), quality=95)
    print(name, img.size)


# ── the five designs, square ────────────────────────────────────────────────────────────────
DESIGNS = [
    ('aura', 'phone', 'aura1.png', ('0xe0157a', '0xff7a2a', '0x7a0d55'), 'telefon ve tablet', "hub'lar, dev yazılarla"),
    ('mozaik', 'phone', 'moz1.png', ('0x050505', '0x5c0b35', '0x111111'), 'telefon', 'kendi kendine dönen canlı karolar'),
    ('panorama', 'tablet', 'pan1.png', ('0x1f0838', '0x4b1d84', '0x2d0b52'), 'tablet', 'tam ekran, gruplu karolar'),
    ('konsol', 'tv', 'k_home.png', ('0x2b2f31', '0x1f7a1f', '0x4a4f52'), 'televizyon', 'kumandayla, karolarla'),
    ('salon', 'tv', 's_home.png', ('0x061631', '0x0b4aa2', '0x04101f'), 'televizyon', 'oturma odası için'),
]


def square(i, name, kind, shot, cols, sub, tag):
    W = H = 1080
    img = ground(W, H, *cols)
    if name == 'aura':
        # Two phones: home in front on the right; the music hub behind it on the left, a little dimmer,
        # its menu - set against the left edge - showing past the front phone.
        front = device('phone', 'aura_home.png', 380)
        fx, fy = W - 70 - front.width, 205
        back = device('phone', 'aura_music.png', 340)
        shade = Image.new('RGBA', back.size, (0, 0, 0, 0))
        shade.putalpha(back.split()[3].point(lambda v: int(v * 0.22)))
        back.alpha_composite(shade)
        place(img, back, fx - back.width * 0.62, 70)
        place(img, front, fx, fy, shadow=0.7)
        text(img, (90, 300), name, LIGHT, 130)
        y = 470
        for line in wrap(sub, SEMI, 40, 300):
            text(img, (96, y), line, SEMI, 40); y += 52
        y += 10
        for line in wrap(tag, SEMI, 34, 330):
            text(img, (96, y), line, SEMI, 34, fill=(255, 255, 255, 215)); y += 46
        brand(img, 96, H - 75, 40, soon_fill=(255, 255, 255, 255))
    elif kind == 'phone':
        dev = device(kind, shot, 410)
        place(img, dev, W - 110 - dev.width, (H - dev.height) / 2 - 10)
        text(img, (90, 330), name, LIGHT, 150)
        text(img, (96, 520), sub, SEMI, 44)
        y = 590
        for line in wrap(tag, SEMI, 40, 440):
            text(img, (96, y), line, SEMI, 40, fill=(255, 255, 255, 215)); y += 52
        # On Aura's pink and orange the pink word would vanish: it goes white there.
        brand(img, 96, H - 110, 46, soon_fill=(255, 255, 255, 255) if name == 'aura' else PINK + (255,))
    else:
        dev = device(kind, shot, 940)
        place(img, dev, (W - dev.width) / 2, 120)
        text(img, (W / 2, 720), name, LIGHT, 130, anchor='ma')
        text(img, (W / 2, 880), f'{sub}  ·  {tag}', SEMI, 38, fill=(255, 255, 255, 225), anchor='ma')
        brand(img, W / 2, H - 70, 40, anchor='center')
    save(img, f'{i}-{name}-kare.png')


def wrap(s, font, size, width):
    words, lines, cur = s.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if width_of(t, font, size) > width and cur:
            lines.append(cur); cur = w
        else:
            cur = t
    lines.append(cur)
    return lines


# ── all three together ───────────────────────────────────────────────────────────────────────
DARK = ('0x1b0f24', '0x5a1340', '0x0f1a2a')


def trio(img, cx, top, scale):
    tv = device('tv', 'k_home.png', int(820 * scale))
    tab = device('tablet', 'taura1.png', int(560 * scale))
    ph = device('phone', 'moz1.png', int(205 * scale))
    place(img, tv, cx - tv.width / 2 + 60 * scale, top)
    place(img, tab, cx - tv.width / 2 - 70 * scale, top + tv.height * 0.55)
    place(img, ph, cx + tv.width / 2 - ph.width * 0.55 + 40 * scale, top + tv.height * 0.32)


def launch_square():
    W = H = 1080
    img = ground(W, H, *DARK, glow=(0.5, 0.15))
    trio(img, W / 2, 80, 1.0)
    text(img, (W / 2, 735), 'Zeta', LIGHT, 130, anchor='ma')
    text(img, (W / 2, 900), 'çok yakında', BOLD, 64, fill=PINK + (255,), anchor='ma')
    text(img, (W / 2, 990), 'telefon · tablet · televizyon için bir arayüz', SEMI, 34, fill=(255, 255, 255, 215), anchor='ma')
    save(img, '0-lansman-kare.png')


def launch_story():
    W, H = 1080, 1920
    img = ground(W, H, *DARK, glow=(0.5, 0.45))
    mark(img, W / 2 - 90, 190, 180)
    text(img, (W / 2, 400), 'Zeta', LIGHT, 190, anchor='ma')
    text(img, (W / 2, 620), 'çok yakında', BOLD, 84, fill=PINK + (255,), anchor='ma')
    trio(img, W / 2 - 10, 850, 1.0)
    y = 1560
    for line in ['telefon · tablet · televizyon', 'için bir arayüz paketi']:
        text(img, (W / 2, y), line, SEMI, 50, anchor='ma'); y += 66
    text(img, (W / 2, H - 170), 'aura · mozaik · panorama · konsol · salon', SEMI, 36, fill=(255, 255, 255, 190), anchor='ma')
    text(img, (W / 2, H - 110), 'Google Play  ·  GitHub', SEMI, 36, fill=(255, 255, 255, 190), anchor='ma')
    save(img, '0-lansman-hikaye.png')


def wide(W, H, name):
    img = ground(W, H, *DARK, glow=(0.3, 0.3))
    s = H / 900
    trio(img, W * 0.33, 120 * s, 0.95 * s)
    x = W * 0.68
    mark(img, x, H * 0.24, int(120 * s))
    text(img, (x, H * 0.24 + 150 * s), 'Zeta', LIGHT, int(150 * s))
    text(img, (x + 6 * s, H * 0.24 + 340 * s), 'çok yakında', BOLD, int(64 * s), fill=PINK + (255,))
    y = H * 0.24 + 440 * s
    for line in ['telefon · tablet · televizyon', 'için bir arayüz']:
        text(img, (x + 6 * s, y), line, SEMI, int(36 * s), fill=(255, 255, 255, 215)); y += 48 * s
    save(img, name)


if __name__ == '__main__':
    launch_square()
    launch_story()
    for i, d in enumerate(DESIGNS, 1):
        square(i, *d)
    wide(1600, 900, '6-genis-1600x900.png')
    wide(1200, 630, '7-baglanti-onizleme-1200x630.png')
