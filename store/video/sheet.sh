#!/bin/zsh
# sheet.sh CLIP N  -> CLIP_sheet.png with N frames evenly spaced, times labelled
f=$1; n=${2:-12}; d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 $f); out=${f%.mp4}_sheet.png
ffmpeg -v error -y -i $f -vf "fps=$n/$d,scale=270:-1,drawtext=fontfile=/System/Library/Fonts/Supplemental/Arial.ttf:text='%{pts\:hms}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.6,tile=6x$(( (n+5)/6 ))" -frames:v 1 $out; echo $out
