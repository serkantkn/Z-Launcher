#!/usr/bin/env python3
"""Builds the Z Launcher promo from the emulator clips, Pexels stills and a Pixabay track."""
import subprocess, sys, os
from PIL import Image, ImageDraw, ImageFilter

# Working folder: stock/ (fetch_stock.sh) and promo/clips (rec.sh) live under it.
S = os.environ.get('PROMO_DIR', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'work'))
P = f'{S}/promo'; C = f'{P}/clips'; W = f'{P}/work'; os.makedirs(W, exist_ok=True)
F = '/Users/serkan/AndroidStudioProjects/ZuneLauncher/app/src/main/res/font'
LIGHT = f'{F}/selawik_light.ttf'; REG = f'{F}/selawik_semilight.ttf'; BOLD = f'{F}/selawik_semibold.ttf'
HI = f'{S}/stock/hi'; GAL = f'{S}/stock/gallery'
MUSIC = f'{S}/stock/music/stylish.mp3'
FPS = 30
ONLY = sys.argv[1:]  # build only these steps when given


def ff(args, name):
    if ONLY and not any(o in name for o in ONLY):
        return
    print('==', name, flush=True)
    r = subprocess.run(['ffmpeg', '-v', 'error', '-y'] + args)
    if r.returncode: sys.exit(f'ffmpeg failed: {name}')


def esc(t):
    return t.replace('\\', '\\\\').replace(':', '\\:').replace("'", "’").replace('%', '\\%')


# ── masks ─────────────────────────────────────────────────────────────────────────────────────
def rounded(w, h, r, path):
    m = Image.new('L', (w, h), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, w - 1, h - 1), r, fill=255); m.save(path)

rounded(430, 956, 44, f'{W}/mask_phone.png')
rounded(1200, 750, 22, f'{W}/mask_tablet.png')


# ── bodies: the good parts of each recording, cut and joined ────────────────────────────────────
def body(name, parts, size):
    """parts: (clip, start, end, speed)"""
    ins, fl = [], []
    for i, (clip, a, b, sp) in enumerate(parts):
        ins += ['-ss', str(a), '-t', str(b - a), '-i', f'{C}/cfr_{clip}.mp4']
        fl.append(f'[{i}:v]setpts=(PTS-STARTPTS)/{sp},fps={FPS},scale={size},setsar=1[v{i}]')
    fl.append(''.join(f'[v{i}]' for i in range(len(parts))) + f'concat=n={len(parts)}:v=1:a=0[out]')
    ff(ins + ['-filter_complex', ';'.join(fl), '-map', '[out]', '-c:v', 'libx264', '-crf', '14', '-preset', 'fast', '-pix_fmt', 'yuv420p', f'{W}/body_{name}.mp4'], f'body_{name}')

body('zune_phone', [('zune_phone', 1.0, 4.6, 1.3), ('zune_phone', 9.3, 14.3, 1), ('zune_phone', 20.8, 23.6, 1)], '1080:2400')
body('wp_phone', [('wp_phone', 0.0, 3.2, 1), ('wp_phone', 10.8, 16.2, 1), ('wp_phone', 4.4, 8.4, 1.3)], '1080:2400')
body('zune_tablet', [('zune_tablet', 0.0, 2.4, 1), ('zune_tablet', 8.8, 14.6, 1), ('zune_tablet', 26.6, 29.2, 1)], '2560:1600')
body('win8_tablet', [('win8_tablet', 0.0, 2.0, 1), ('win8_tablet', 3.6, 5.6, 1), ('win8_tablet', 9.8, 12.2, 1), ('win8_tablet', 16.6, 20.8, 1)], '2560:1600')
body('xbox', [('xbox_tv', 4.5, 13.5, 1.4), ('xbox_music', 9.6, 12.6, 1), ('xbox_music', 13.2, 17.8, 1), ('xbox_music', 37.6, 42.4, 1)], '1920:1080')
body('mc', [('mc_tv', 2.0, 4.6, 1), ('mc_tv', 5.0, 17.0, 1.6), ('mc_pics', 0.8, 4.2, 1)], '1920:1080')


# ── text helpers ───────────────────────────────────────────────────────────────────────────────
def slide_text(text, font, size, x, y, t0, color='white', dx=260, dur=0.7, t_out=None, shadow=True):
    """Slides in from the right with an ease-out, fading in; optionally fades out at t_out."""
    e = f'pow(1-min(1\\,max(0\\,(t-{t0})/{dur}))\\,3)'
    a = f'min(1\\,max(0\\,(t-{t0})/{dur * 0.8}))'
    if t_out is not None:
        a = f'{a}*(1-min(1\\,max(0\\,(t-{t_out})/0.4)))'
    sh = ':shadowcolor=black@0.45:shadowx=2:shadowy=3' if shadow else ''
    return (f"drawtext=fontfile={font}:text='{esc(text)}':fontsize={size}:fontcolor={color}{sh}"
            f":x={x}+{dx}*{e}:y={y}:alpha={a}")


def bg_still(img, tint, blur=28, dark=-0.22):
    return (f"scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,gblur=sigma={blur},"
            f"eq=brightness={dark}:saturation=1.25,drawbox=c={tint}:t=fill")


# ── device sections ────────────────────────────────────────────────────────────────────────────
def section_phone(name, bodyname, bgimg, tint, title, sub, tag, dur, accent):
    w, h, X, Y = 430, 956, 300, 62
    fl = [
        f'[0:v]{bg_still(bgimg, tint)},loop=-1:1,fps={FPS},trim=duration={dur}[bg]' if False else
        f'[0:v]{bg_still(bgimg, tint)},fps={FPS}[bg]',
        f'[1:v]scale={w}:{h},format=yuva420p[dv]',
        f'[2:v]format=gray,scale={w}:{h}[mk]',
        '[dv][mk]alphamerge,split[d1][d2]',
        f'[d2]colorchannelmixer=rr=0:gg=0:bb=0:aa=0.65,pad={w + 120}:{h + 120}:60:60:color=black@0,gblur=sigma=26[sh]',
        f"[bg][sh]overlay=x={X - 60 + 18}:y={Y - 60 + 26}+260*pow(1-min(1\\,t/0.8)\\,3):format=auto[b1]",
        f"[b1][d1]overlay=x={X}:y={Y}+260*pow(1-min(1\\,t/0.8)\\,3):format=auto[b2]",
        f"[b2]{slide_text(title, LIGHT, 150 if len(title) < 8 else 118, 880, 360, 0.25)},"
        f"{slide_text(sub, REG, 56, 888, 560, 0.55, color='white')},"
        f"{slide_text(tag, REG, 40, 888, 640, 0.85, color=accent)},"
        f"fade=t=in:st=0:d=0.3,format=yuv420p[out]",
    ]
    ff(['-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', bgimg, '-t', str(dur), '-i', f'{W}/body_{bodyname}.mp4',
        '-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', f'{W}/mask_phone.png', '-filter_complex', ';'.join(fl), '-map', '[out]',
        '-t', str(dur), '-r', str(FPS), '-c:v', 'libx264', '-crf', '16', '-preset', 'fast', f'{W}/seg_{name}.mp4'], f'seg_{name}')


def section_tablet(name, bodyname, bgimg, tint, title, sub, tag, dur, accent):
    w, h, X, Y = 1200, 750, 70, 165
    fl = [
        f'[0:v]{bg_still(bgimg, tint)},fps={FPS}[bg]',
        f'[1:v]scale={w}:{h},format=yuva420p[dv]',
        f'[2:v]format=gray,scale={w}:{h}[mk]',
        '[dv][mk]alphamerge,split[d1][d2]',
        f'[d2]colorchannelmixer=rr=0:gg=0:bb=0:aa=0.6,pad={w + 120}:{h + 120}:60:60:color=black@0,gblur=sigma=26[sh]',
        f"[bg][sh]overlay=x={X - 60 + 18}-300*pow(1-min(1\\,t/0.8)\\,3):y={Y - 60 + 26}:format=auto[b1]",
        f"[b1][d1]overlay=x={X}-300*pow(1-min(1\\,t/0.8)\\,3):y={Y}:format=auto[b2]",
        f"[b2]{slide_text(title, LIGHT, 120 if len(title) < 8 else 104, 1330, 360, 0.25)},"
        f"{slide_text(sub, REG, 54, 1338, 520, 0.55, color='white')},"
        f"{slide_text(tag, REG, 38, 1338, 596, 0.85, color=accent)},"
        f"fade=t=in:st=0:d=0.3,format=yuv420p[out]",
    ]
    ff(['-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', bgimg, '-t', str(dur), '-i', f'{W}/body_{bodyname}.mp4',
        '-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', f'{W}/mask_tablet.png', '-filter_complex', ';'.join(fl), '-map', '[out]',
        '-t', str(dur), '-r', str(FPS), '-c:v', 'libx264', '-crf', '16', '-preset', 'fast', f'{W}/seg_{name}.mp4'], f'seg_{name}')


def section_tv(name, bodyname, title, sub, tag, dur, accent):
    """The TV fills the frame; its name rides over a dark band at the start, then gives way."""
    fl = [
        f'[0:v]fps={FPS},format=yuv420p[v]',
        f"[v]drawbox=x=0:y=640:w=1920:h=440:c=black@0.55:t=fill:enable=lt(t\\,3.2),"
        f"{slide_text(title, LIGHT, 150, 110, 720, 0.2, t_out=2.9)},"
        f"{slide_text(sub, REG, 54, 118, 905, 0.5, color=accent, t_out=2.9)},"
        f"{slide_text(tag, REG, 40, 118 + 30 + len(sub) * 26, 917, 0.8, color='white@0.85', t_out=2.9)}[out]",
    ]
    ff(['-t', str(dur), '-i', f'{W}/body_{bodyname}.mp4', '-filter_complex', ';'.join(fl), '-map', '[out]',
        '-t', str(dur), '-r', str(FPS), '-c:v', 'libx264', '-crf', '16', '-preset', 'fast', f'{W}/seg_{name}.mp4'], f'seg_{name}')


# ── a still with the interface in its screen, the camera pushing in ────────────────────────────
def scene(name, photo, crop_y, quad, bodyname, body_ss, dur, zoom_to, label=None):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = quad
    cx = (x0 + x1 + x2 + x3) / 4; cy = (y0 + y1 + y2 + y3) / 4
    z = f'1+({zoom_to}-1)*pow(min(1\\,on/{dur * FPS})\\,1.6)'
    lab = (',' + slide_text(label, LIGHT, 64, 110, 940, 0.3, t_out=dur - 0.6)) if label else ''
    m = Image.new('L', (1920, 1080), 0); ImageDraw.Draw(m).polygon(quad, fill=255)
    m = m.filter(ImageFilter.GaussianBlur(0.8)); m.save(f'{W}/quad_{name}.png')
    fl = [
        f'[0:v]scale=1920:-1,crop=1920:1080:0:{crop_y},fps={FPS},format=yuva420p[ph]',
        f'[1:v]fps={FPS},scale=1920:1080,format=yuva420p,'
        f'perspective=x0={x0}:y0={y0}:x1={x1}:y1={y1}:x2={x3}:y2={y3}:x3={x2}:y3={y2}:sense=destination:eval=init[sp]',
        f'[2:v]format=gray,fps={FPS}[qm]',
        '[sp][qm]alphamerge[sc]',
        '[ph][sc]overlay=format=auto[c]',
        f"[c]scale=3840:2160,zoompan=z={z}:x={cx * 2}-iw/zoom/2:y={cy * 2}-ih/zoom/2:d=1:s=1920x1080:fps={FPS}{lab},"
        f"format=yuv420p[out]",
    ]
    ff(['-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', photo, '-ss', str(body_ss), '-t', str(dur), '-i', f'{W}/body_{bodyname}.mp4',
        '-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', f'{W}/quad_{name}.png', '-filter_complex', ';'.join(fl), '-map', '[out]', '-t', str(dur), '-r', str(FPS),
        '-c:v', 'libx264', '-crf', '16', '-preset', 'fast', f'{W}/seg_{name}.mp4'], f'seg_{name}')


# ── intro and outro ────────────────────────────────────────────────────────────────────────────
def intro(dur):
    bg = f'{HI}/6980724.jpg'
    fl = [
        f"[0:v]scale=2112:-1,crop=2112:1188:0:60,gblur=sigma=18,eq=brightness=-0.42:saturation=1.1,"
        f"zoompan=z=1+0.10*on/{dur * FPS}:x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=1:s=1920x1080:fps={FPS},"
        f"drawbox=c=0x5b1037@0.35:t=fill[bg]",
        f"[bg]{slide_text('Z Launcher', LIGHT, 190, 520, 380, 1.0, dx=0)},"
        f"{slide_text('telefon · tablet · televizyon', REG, 52, 540, 610, 2.2, color='0xff6fae')},"
        f"{slide_text('zune  ·  windows phone  ·  windows 8  ·  xbox 360  ·  media center', REG, 34, 542, 690, 3.6, color='white@0.75')},"
        f"fade=t=in:st=0:d=1.2,format=yuv420p[out]",
    ]
    ff(['-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', bg, '-filter_complex', ';'.join(fl), '-map', '[out]', '-t', str(dur),
        '-r', str(FPS), '-c:v', 'libx264', '-crf', '16', '-preset', 'fast', f'{W}/seg_intro.mp4'], 'seg_intro')


def outro(dur):
    bg = f'{GAL}/pexels_31048512.jpg'
    small = 'Zune, Windows Phone, Windows 8, Xbox 360 ve Media Center Microsoft’un markalarıdır. Z Launcher Microsoft ile ilişkili değildir.'
    fl = [
        f"[0:v]scale=2112:-1,crop=2112:1188,gblur=sigma=22,eq=brightness=-0.45,"
        f"zoompan=z=1.12-0.12*on/{dur * FPS}:x=iw/2-iw/zoom/2:y=ih/2-ih/zoom/2:d=1:s=1920x1080:fps={FPS},"
        f"drawbox=c=0x14202a@0.35:t=fill[bg]",
        f"[bg]{slide_text('Z Launcher', LIGHT, 170, 560, 300, 0.4, dx=0)},"
        f"{slide_text('çok yakında', BOLD, 92, 572, 520, 1.3, color='0xff3d9a')},"
        f"{slide_text('Google Play  ·  GitHub', REG, 50, 576, 660, 2.3, color='white@0.9')},"
        f"drawtext=fontfile={REG}:text='{esc(small)}':fontsize=22:fontcolor=white@0.55:x=(w-text_w)/2:y=1010:"
        f"alpha=min(1\\,max(0\\,(t-3)/0.8)),"
        f"fade=t=out:st={dur - 1.4}:d=1.4,format=yuv420p[out]",
    ]
    ff(['-loop', '1', '-framerate', str(FPS), '-t', str(dur), '-i', bg, '-filter_complex', ';'.join(fl), '-map', '[out]', '-t', str(dur),
        '-r', str(FPS), '-c:v', 'libx264', '-crf', '16', '-preset', 'fast', f'{W}/seg_outro.mp4'], 'seg_outro')


# ── the timeline ───────────────────────────────────────────────────────────────────────────────
PHONE_Q = [(890 + 5, 260 + 5), (1117 - 5, 267 + 5), (1105 - 5, 779 - 5), (865 + 5, 769 - 5)]
TV_Q = [(1302 + 6, 367 + 6), (1679 - 6, 343 + 6), (1677 - 6, 536 - 6), (1299 + 6, 532 - 6)]
TAB_Q = [(1 + 20, 640 + 14), (1262 - 18, 512 + 12), (1517 - 22, 797 - 10), (38 + 20, 1010 - 14)]

SEG = [  # name, duration, transition into it, its length
    ('intro', 9.9, None, 0),
    ('phone_scene', 2.6, 'circleopen', 0.7),
    ('zune_phone', 10.5, 'zoomin', 0.7),
    ('wp_phone', 10.5, 'slideleft', 0.7),
    ('tablet_scene', 2.6, 'radial', 0.7),
    ('zune_tablet', 9.5, 'smoothup', 0.7),
    ('win8_tablet', 9.5, 'pixelize', 0.7),
    ('tv_scene', 5.6, 'fadeblack', 0.9),
    ('xbox', 18.0, 'fadewhite', 0.8),
    ('mc', 12.5, 'wipeleft', 0.7),
    ('outro', 12.2, 'circleclose', 0.9),
]
D = {n: d for n, d, _, _ in SEG}

if not ONLY or 'segs' in ONLY or any(o.startswith('seg') for o in ONLY):
    intro(D['intro'])
    scene('phone_scene', f'{HI}/6373205.jpg', 100, PHONE_Q, 'zune_phone', 0.0, D['phone_scene'], 2.1)
    section_phone('zune_phone', 'zune_phone', f'{GAL}/pexels_40133180.jpg', '0xc2185b@0.30', 'zune', 'telefon', 'kayar hub listesi', D['zune_phone'], '0xffd1e6')
    section_phone('wp_phone', 'wp_phone', f'{GAL}/pexels_20208915.jpg', '0x9b1050@0.32', 'windows phone', 'telefon', 'canlı karolar', D['wp_phone'], '0xffc2dc')
    scene('tablet_scene', f'{HI}/286565.jpg', 100, TAB_Q, 'zune_tablet', 0.0, D['tablet_scene'], 1.7)
    section_tablet('zune_tablet', 'zune_tablet', f'{GAL}/pexels_36103492.jpg', '0x6a1b9a@0.28', 'zune', 'tablet', 'quickplay', D['zune_tablet'], '0xf3d6ff')
    section_tablet('win8_tablet', 'win8_tablet', f'{GAL}/pexels_31585397.jpg', '0x4a148c@0.34', 'windows 8', 'tablet', 'tam ekran başlangıç', D['win8_tablet'], '0xeacbff')
    scene('tv_scene', f'{HI}/6980724.jpg', 100, TV_Q, 'xbox', 0.0, D['tv_scene'], 3.2)
    section_tv('xbox', 'xbox', 'xbox 360', 'televizyon', 'rehber · görselleştirici · temalar', D['xbox'], '0x7ee17e')
    section_tv('mc', 'mc', 'media center', 'televizyon', 'klasik, yeniden', D['mc'], '0x7fc8ff')
    outro(D['outro'])

# join with transitions, then the music
if not ONLY or 'final' in ONLY:
    ins, fl, t, prev = [], [], 0.0, '[0:v]'
    for i, (n, d, tr, td) in enumerate(SEG):
        ins += ['-i', f'{W}/seg_{n}.mp4']
    t = SEG[0][1]
    for i in range(1, len(SEG)):
        n, d, tr, td = SEG[i]
        off = t - td
        lab = f'[x{i}]'
        fl.append(f'{prev}[{i}:v]xfade=transition={tr}:duration={td}:offset={off:.3f}{lab}')
        prev = lab; t = off + d
    total = t
    print('total', total)
    ins += ['-i', MUSIC]
    fl.append(f'[{len(SEG)}:a]atrim=0:{total},afade=t=in:d=0.6,afade=t=out:st={total - 2.0}:d=2.0[a]')
    ff(ins + ['-filter_complex', ';'.join(fl), '-map', prev, '-map', '[a]', '-c:v', 'libx264', '-crf', '18',
              '-preset', 'slow', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart',
              f'{P}/z_launcher_tanitim.mp4'], 'final')
