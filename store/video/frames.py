"""Copies Android Studio's device art for the phone, the tablet and the TV into PNGs build.py reads."""
import sys
from PIL import Image
A, F = sys.argv[1:3]
for d in ['pixel_8', 'pixel_tablet']:
    for k in ['back', 'mask']:
        Image.open(f'{A}/{d}/{k}.webp').save(f'{F}/{d}_{k}.png')
for k in ['back', 'fore']:
    Image.open(f'{A}/tv_1080p/{k}.png').save(f'{F}/tv_{k}.png')
