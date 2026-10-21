#!/bin/zsh
# rec.sh SERIAL start NAME | rec.sh SERIAL stop NAME
E=$1; C=${PROMO_DIR:-$(dirname $0)/work}/promo/clips; mkdir -p $C
case $2 in
  start) adb -s $E shell rm -f /sdcard/$3.mp4; (adb -s $E shell screenrecord --bit-rate 20000000 /sdcard/$3.mp4 >/dev/null 2>&1 &); sleep 1 ;;
  stop) adb -s $E shell pkill -INT screenrecord; sleep 2; adb -s $E pull -q /sdcard/$3.mp4 $C/$3.mp4; adb -s $E shell rm -f /sdcard/$3.mp4; ffprobe -v error -show_entries format=duration -of csv=p=0 $C/$3.mp4 ;;
esac
