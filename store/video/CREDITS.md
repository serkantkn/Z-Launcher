# Tanıtım videosu: kaynaklar

`store/z-launcher-tanitim.mp4` (1920×1080, 96 sn) emülatör kayıtlarından kurgulandı: her arayüz kendi
cihazının çerçevesinde (telefon, tablet, televizyon), sade ve yavaşça dönen bir zeminde, aşağıdaki müzikle. Arayüz görüntülerinin hepsi Z Launcher'ın kendisi.

## Görseller — Pexels (Pexels Lisansı: ücretsiz, ticari kullanım serbest, atıf gerekmez)

Emülatörlerin galerisine kondu; videoda resimler hub'ında ve karolarda görünür:
36103492, 40133180, 20208915, 40279310, 36673126, 31585397, 34258456, 15560911, 163915, 31048512.

## Cihaz çerçeveleri

Android Studio'nun kendi cihaz çizimleri (`device-art-resources`: pixel_8, pixel_tablet, tv_1080p).

## Müzik — Pixabay (Pixabay İçerik Lisansı: ücretsiz, ticari kullanım serbest, atıf gerekmez)

"Stylish Deep Electronic" — NverAvetyanMusic ([sayfası](https://pixabay.com/music/future-bass-stylish-deep-electronic-262632/)).

## Yazı tipi

Selawik (SIL Open Font License), uygulamanın kendi yazı tipi.

## Yeniden kurmak

1. `./fetch_stock.sh` — görselleri ve müziği `work/stock` altına indirir.
2. Emülatörde `rec.sh SERIAL start|stop NAME` ile kayıtlar alınır (`ui.sh` ekrandaki yazılara dokunur,
   `sheet.sh` bir kaydın kare sayfasını çıkarır). Kayıtlar 30 kareye çevrilir (`cfr_NAME.mp4`) ve
   `python3 clips.py body` iyi kısımlarını keser.
3. `python3 build.py` — çerçeveli bölümleri ve son videoyu üretir (ffmpeg 8; tek adım için ör. `build.py v2_xbox final`).

Microsoft'un logoları ve sesleri kullanılmadı; videonun sonunda "Microsoft ile ilişkili değildir" notu var.
