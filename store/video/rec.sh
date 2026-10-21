#!/bin/zsh
# rec2.sh SERIAL start NAME WxH | rec2.sh SERIAL stop NAME
# The emulator records on the host at 60 fps; the guest's own screenrecord managed ~6.
E=$1; C=${PROMO_DIR:-$(dirname $0)/work}/promo/hq; mkdir -p $C
case $2 in
  start) rm -f $C/$3.webm; adb -s $E emu screenrecord start --fps 60 --size $4 --bit-rate 20000000 $C/$3.webm >/dev/null; sleep 0.8 ;;
  stop) adb -s $E emu screenrecord stop >/dev/null; sleep 3
        ffmpeg -v error -y -i $C/$3.webm -vf fps=60 -c:v libx264 -crf 12 -preset fast -pix_fmt yuv420p $C/cfr_$3.mp4
        ffprobe -v error -show_entries format=duration -of csv=p=0 $C/cfr_$3.mp4 ;;
esac
