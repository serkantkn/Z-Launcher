#!/bin/zsh
# Downloads the stock pictures and the music the promo is cut from (see CREDITS.md) into work/stock.
D=${PROMO_DIR:-$(dirname $0)/work}/stock; mkdir -p $D/hi $D/gallery $D/music
px(){ curl -sL -A "Mozilla/5.0" -o "$2" "https://images.pexels.com/photos/$1/pexels-photo-$1.jpeg?auto=compress&cs=tinysrgb&w=$3"; }
for id in 6980724 6373205 286565; do px $id $D/hi/$id.jpg 3000; done
for id in 36103492 40133180 20208915 40279310 36673126 31585397 34258456 15560911 163915 31048512; do px $id $D/gallery/pexels_$id.jpg 1920; done
curl -sL -A "Mozilla/5.0" -e "https://pixabay.com/" -o $D/music/stylish.mp3 "https://cdn.pixabay.com/download/audio/2024/11/10/audio_593e8523e6.mp3?filename=nveravetyanmusic-stylish-deep-electronic-262632.mp3"
