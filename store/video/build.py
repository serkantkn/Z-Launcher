#!/usr/bin/env python3
"""The Z Launcher promo, second cut: each interface plays inside its device's frame on a plain,
slowly moving ground in that design's colours. No scenes."""
import subprocess, sys, os

S = os.environ.get('PROMO_DIR', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'work'))
P = f'{S}/promo'; W = f'{P}/work'; FR = f'{S}/frames'  # fetch_stock.sh copies Android Studio's device art here; os.makedirs(W, exist_ok=True)
F = '/Users/serkan/AndroidStudioProjects/ZuneLauncher/app/src/main/res/font'
LIGHT = f'{F}/selawik_light.ttf'; REG = f'{F}/selawik_semilight.ttf'; BOLD = f'{F}/selawik_semibold.ttf'
MUSIC = f'{S}/stock/music/stylish.mp3'
FPS = 30
ONLY = sys.argv[1:]

# The device frames (Android Studio's device art): frame size, where the screen sits, the screen size.
DEV = {
    'phone': dict(back=f'{FR}/pixel_8_back.png', mask=f'{FR}/pixel_8_mask.png', fw=1187, fh=2513, sx=49, sy=55, sw=1080, sh=2400),
    'tablet': dict(back=f'{FR}/pixel_tablet_back.png', mask=f'{FR}/pixel_tablet_mask.png', fw=2798, fh=1837, sx=119, sy=117, sw=2560, sh=1600),
    'tv': dict(back=f'{FR}/tv_back.png', fore=f'{FR}/tv_fore.png', fw=2090, fh=1255, sx=85, sy=59, sw=1920, sh=1080),
}


def ff(args, name):
    if ONLY and not any(o in name for o in ONLY):
        return
    print('==', name, flush=True)
    r = subprocess.run(['ffmpeg', '-v', 'error', '-y'] + args)
    if r.returncode: sys.exit(f'ffmpeg failed: {name}')


def esc(t):
    return t.replace('\\', '\\\\').replace(':', '\\:').replace("'", "’").replace('%', '\\%')


def ease(t0, dur):
    return f'pow(1-min(1\\,max(0\\,(t-{t0})/{dur}))\\,3)'


def text(txt, font, size, x, y, t0, color='white', dx=240, dur=0.7, t_out=None, center=False):
    a = f'min(1\\,max(0\\,(t-{t0})/{dur * 0.8}))'
    if t_out is not None:
        a = f'{a}*(1-min(1\\,max(0\\,(t-{t_out})/0.5)))'
    xx = '(w-text_w)/2' if center else str(x)
    return (f"drawtext=fontfile={font}:text='{esc(txt)}':fontsize={size}:fontcolor={color}"
            f":shadowcolor=black@0.35:shadowx=2:shadowy=3:x={xx}+{dx}*{ease(t0, dur)}:y={y}:alpha={a}")


def ground(c0, c1, c2, dur, speed=0.012):
    """A plain ground that turns slowly: three colours, no picture."""
    return (f'gradients=s=1920x1080:r={FPS}:d={dur}:n=3:c0={c0}:c1={c1}:c2={c2}:'
            f'x0=0:y0=0:x1=1920:y1=1080:speed={speed},format=yuv420p')


def device_chain(kind, body_label, out_label, width, i0):
    """Filter lines putting [body_label] into its frame, scaled to [width]; uses inputs i0 (back) and i0+1 (mask/fore)."""
    d = DEV[kind]
    h = round(width * d['fh'] / d['fw'] / 2) * 2
    lines = [f"color=c=black@0:s={d['fw']}x{d['fh']}:r={FPS},format=rgba[cv{out_label}]"]
    if kind == 'tv':
        lines += [
            f"[{i0}:v]format=rgba[bk{out_label}]",
            f"[cv{out_label}][bk{out_label}]overlay=0:0:shortest=1[c1{out_label}]",
            f"[{body_label}]scale={d['sw']}:{d['sh']},format=rgba[sc{out_label}]",
            f"[c1{out_label}][sc{out_label}]overlay={d['sx']}:{d['sy']}:shortest=1[c2{out_label}]",
            f"[{i0 + 1}:v]format=rgba[fo{out_label}]",
            f"[c2{out_label}][fo{out_label}]overlay=0:0:shortest=1,scale={width}:{h}[{out_label}]",
        ]
    else:
        lines += [
            f"[{body_label}]scale={d['sw']}:{d['sh']},format=rgba[sc{out_label}]",
            f"[cv{out_label}][sc{out_label}]overlay={d['sx']}:{d['sy']}:shortest=1[c1{out_label}]",
            f"[{i0}:v]format=rgba[bk{out_label}]",
            f"[c1{out_label}][bk{out_label}]overlay=0:0:shortest=1[c2{out_label}]",
            f"[{i0 + 1}:v]format=rgba[mk{out_label}]",
            f"[c2{out_label}][mk{out_label}]overlay={d['sx']}:{d['sy']}:shortest=1,scale={width}:{h}[{out_label}]",
        ]
    return lines, h


def frame_inputs(kind, dur):
    d = DEV[kind]
    second = d['fore'] if kind == 'tv' else d['mask']
    return ['-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', d['back'],
            '-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', second]


def place(base, dev, out, x, y, t0=0.0, from_dx=0, from_dy=260, dur=0.9, float_px=7, shadow=True):
    """Shadow and device over [base]: slides in from (from_dx, from_dy) easing out, then floats gently."""
    e = ease(t0, dur)
    fl = f'{float_px}*sin(2*PI*(t-{t0})/5.5)'
    X = f'{x}+{from_dx}*{e}'; Y = f'{y}+{from_dy}*{e}+{fl}*(1-{e})'
    lines = []
    if shadow:
        lines += [f'[{dev}]split[{dev}a][{dev}b]',
                  f'[{dev}b]colorchannelmixer=rr=0:gg=0:bb=0:aa=0.55,pad=iw+160:ih+160:80:80:color=black@0,gblur=sigma=34[{dev}s]',
                  f"[{base}][{dev}s]overlay=x={X}-80+20:y={Y}-80+38:format=auto[{base}{dev}]",
                  f"[{base}{dev}][{dev}a]overlay=x={X}:y={Y}:format=auto[{out}]"]
    else:
        lines += [f"[{base}][{dev}]overlay=x={X}:y={Y}:format=auto[{out}]"]
    return lines


def encode(ins, fl, out_label, dur, name):
    ff(ins + ['-filter_complex', ';'.join(fl), '-map', f'[{out_label}]', '-t', str(dur), '-r', str(FPS),
              '-c:v', 'libx264', '-crf', '16', '-preset', 'fast', '-pix_fmt', 'yuv420p', f'{W}/v2_{name}.mp4'], f'v2_{name}')


def body_input(name, dur):
    # The last frame is held should the cut be shorter than the section.
    return ['-i', f'{W}/body_{name}.mp4'], f'tpad=stop_mode=clone:stop_duration={dur},fps={FPS}'


# ── one design: its device on the left or in the middle, its name beside it ─────────────────────
def section(name, kind, bodyname, colours, title, sub, tag, accent, dur):
    ins = []
    bi, pad = body_input(bodyname, dur); ins += bi
    ins += frame_inputs(kind, dur)
    fl = [ground(*colours, dur) + '[g]', f'[0:v]{pad},trim=duration={dur}[b]']
    if kind == 'phone':
        width = 430; tx, ty = 900, 330; x, y = 330, 90
    elif kind == 'tablet':
        width = 1150; tx, ty = 1330, 360; x, y = 90, 160
    else:
        width = 1230; tx, ty = 1340, 360; x, y = 70, 190
    dl, h = device_chain(kind, 'b', 'dev', width, 1); fl += dl
    fl += place('g', 'dev', 'p', x, y, t0=0.0, from_dx=-140 if kind != 'phone' else 0, from_dy=0 if kind != 'phone' else 300)
    big = 150 if len(title) <= 6 else (124 if len(title) <= 9 else 104)
    if kind != 'phone':
        big = min(big, 120 if len(title) <= 9 else 96)
    fl.append(f"[p]{text(title, LIGHT, big, tx, ty, 0.35)},{text(sub, REG, 56, tx + 8, ty + big + 40, 0.65, color='white')},"
              f"{text(tag, REG, 40, tx + 8, ty + big + 120, 0.95, color=accent)},fade=t=in:d=0.25[out]")
    encode(ins, fl, 'out', dur, name)


# ── the three together: phone, tablet and TV, each playing ──────────────────────────────────────
def lineup(name, dur, lines, outro=False):
    ins = []
    fl = [ground('0x1b0f24', '0x5a1340', '0x0f1a2a', dur, 0.008) + '[g]']
    specs = [('tv', 'xbox', 980, 470, 150), ('tablet', 'zune_tablet', 760, 90, 360), ('phone', 'wp_phone', 250, 1430, 330)]
    idx = 0
    for k, (kind, bodyname, width, x, y) in enumerate(specs):
        ss = 2.0 if outro else 0.0
        ins += ['-ss', str(ss), '-i', f'{W}/body_{bodyname}.mp4']; bi = idx; idx += 1
        ins += frame_inputs(kind, dur); fi = idx; idx += 2
        fl.append(f'[{bi}:v]tpad=stop_mode=clone:stop_duration={dur},fps={FPS},trim=duration={dur}[b{k}]')
        dl, h = device_chain(kind, f'b{k}', f'd{k}', width, fi); fl += dl
    base = 'g'
    order = [(0, 0.2, 0, 220), (1, 0.6, -260, 0), (2, 1.0, 260, 0)]
    for n, (k, t0, dx, dy) in enumerate(order):
        x, y = specs[k][3], specs[k][4]
        if outro:
            y -= 120
        fl += place(base, f'd{k}', f'L{n}', x, y, t0=t0, from_dx=dx, from_dy=dy, float_px=5)
        base = f'L{n}'
    fl.append(f'[{base}]' + ','.join(lines) + ',fade=t=in:d=0.6' + (f',fade=t=out:st={dur - 1.4}:d=1.4' if outro else '') + '[out]')
    encode(ins, fl, 'out', dur, name)


D = dict(intro=9.9, zune_phone=11.5, wp_phone=11.5, zune_tablet=11.0, win8_tablet=13.0, xbox=19.0, mc=13.0, outro=12.0)
TR = [('intro', None, 0), ('zune_phone', 'circleopen', 0.7), ('wp_phone', 'slideleft', 0.7), ('zune_tablet', 'radial', 0.7),
      ('win8_tablet', 'pixelize', 0.7), ('xbox', 'fadewhite', 0.8), ('mc', 'wipeleft', 0.7), ('outro', 'circleclose', 0.9)]

if not ONLY or any(o.startswith('v2') or o == 'segs' for o in ONLY):
    lineup('intro', D['intro'], [
        text('Z Launcher', LIGHT, 150, 0, 820 - 120 * 0, 1.6, dx=0, center=True).replace('y=700', 'y=700'),
    ] if False else [
        text('Z Launcher', LIGHT, 130, 0, 860, 1.8, dx=0, center=True),
        text('telefon · tablet · televizyon', REG, 44, 0, 1010, 2.6, color='0xff8cc0', dx=0, center=True),
    ])
    section('zune_phone', 'phone', 'zune_phone', ('0xe0157a', '0xff7a2a', '0x7a0d55'), 'zune', 'telefon', 'kayar hub listesi', '0xffe0c2', D['zune_phone'])
    section('wp_phone', 'phone', 'wp_phone', ('0x050505', '0x5c0b35', '0x111111'), 'windows phone', 'telefon', 'canlı karolar', '0xff5fa8', D['wp_phone'])
    section('zune_tablet', 'tablet', 'zune_tablet', ('0x2a0b3d', '0xb0177a', '0x4a1260'), 'zune', 'tablet', 'quickplay', '0xffc6e6', D['zune_tablet'])
    section('win8_tablet', 'tablet', 'win8_tablet', ('0x1f0838', '0x4b1d84', '0x2d0b52'), 'windows 8', 'tablet', 'tam ekran başlangıç', '0xd9b8ff', D['win8_tablet'])
    section('xbox', 'tv', 'xbox', ('0x2b2f31', '0x1f7a1f', '0x4a4f52'), 'xbox 360', 'televizyon', 'rehber · görselleştirici', '0x9cf09c', D['xbox'])
    section('mc', 'tv', 'mc', ('0x061631', '0x0b4aa2', '0x04101f'), 'media center', 'televizyon', 'klasik, yeniden', '0x9fd2ff', D['mc'])
    small = 'Zune, Windows Phone, Windows 8, Xbox 360 ve Media Center Microsoft’un markalarıdır. Z Launcher Microsoft ile ilişkili değildir.'
    lineup('outro', D['outro'], [
        text('çok yakında', BOLD, 96, 0, 820, 1.6, color='0xff3d9a', dx=0, center=True),
        text('Z Launcher  ·  Google Play  ·  GitHub', REG, 46, 0, 950, 2.4, color='white@0.92', dx=0, center=True),
        f"drawtext=fontfile={REG}:text='{esc(small)}':fontsize=21:fontcolor=white@0.5:x=(w-text_w)/2:y=1038:alpha=min(1\\,max(0\\,(t-3.2)/0.8))",
    ], outro=True)

if not ONLY or 'final' in ONLY:
    ins, fl = [], []
    for n, _, _ in TR:
        ins += ['-i', f'{W}/v2_{n}.mp4']
    prev = '[0:v]'; t = D['intro']
    for i in range(1, len(TR)):
        n, tr, td = TR[i]
        off = t - td
        fl.append(f'{prev}[{i}:v]xfade=transition={tr}:duration={td}:offset={off:.3f}[x{i}]')
        prev = f'[x{i}]'; t = off + D[n]
    print('total', round(t, 2))
    ins += ['-i', MUSIC]
    fl.append(f'[{len(TR)}:a]atrim=0:{t},afade=t=in:d=0.5,afade=t=out:st={t - 2.0}:d=2.0[a]')
    ff(ins + ['-filter_complex', ';'.join(fl), '-map', prev, '-map', '[a]', '-c:v', 'libx264', '-crf', '17', '-preset', 'slow',
              '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', f'{P}/z_launcher_tanitim_v2.mp4'], 'final')
