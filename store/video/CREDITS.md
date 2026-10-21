# Tanıtım videosu: kaynaklar

`store/z-launcher-tanitim.mp4` (1920×1080, 60 kare/sn, 96 sn) emülatör kayıtlarından kurgulandı: her arayüz kendi
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
2. Emülatörler donanım hızlandırmasıyla açılır (`emulator -avd ... -gpu host`) ve `rec.sh SERIAL start NAME WxH`
   / `rec.sh SERIAL stop NAME` ile kaydedilir: emülatörün kendi kaydı, bilgisayar tarafında, 60 kare/sn.
   (Cihazın içindeki `screenrecord` saniyede ~6 kare alıyordu; arayüz kasıyormuş gibi görünüyordu.)
   `ui.sh` ekrandaki yazılara dokunur, `sheet.sh` bir kaydın kare sayfasını çıkarır.
   `python3 clips.py body` kayıtların iyi kısımlarını keser.
3. `python3 build.py` — çerçeveli bölümleri ve son videoyu üretir (ffmpeg 8; tek adım için ör. `build.py v2_xbox final`; bölüm adları kodda eski kısa adlarıyla).

Videoda başka bir şirketin ürün adı, logosu ya da sesi geçmiyor; tasarımlar kendi adlarıyla anılıyor:
Aura, Mozaik, Panorama, Konsol, Salon.
