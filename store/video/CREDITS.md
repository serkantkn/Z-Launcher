# Tanıtım videosu: kaynaklar

`store/z-launcher-tanitim.mp4` (1920×1080, 96 sn) emülatör kayıtlarından, aşağıdaki stok
görsellerden ve müzikten kurgulandı. Arayüz görüntülerinin hepsi Z Launcher'ın kendisi.

## Görseller — Pexels (Pexels Lisansı: ücretsiz, ticari kullanım serbest, atıf gerekmez)

| Kullanım | Pexels kimliği |
| --- | --- |
| Oturma odası, duvardaki televizyon (giriş, TV sahnesi) | [6980724](https://www.pexels.com/photo/6980724/) |
| İki elle tutulan telefon (telefon sahnesi) | [6373205](https://www.pexels.com/photo/6373205/) |
| Masadaki tablet (tablet sahnesi) | [286565](https://www.pexels.com/photo/286565/) |
| Galeri ve arka planlar (emülatörlere de kondu) | 36103492, 40133180, 20208915, 40279310, 36673126, 31585397, 34258456, 15560911, 163915, 31048512 |

## Müzik — Pixabay (Pixabay İçerik Lisansı: ücretsiz, ticari kullanım serbest, atıf gerekmez)

"Stylish Deep Electronic" — NverAvetyanMusic ([sayfası](https://pixabay.com/music/future-bass-stylish-deep-electronic-262632/)).

## Yazı tipi

Selawik (SIL Open Font License), uygulamanın kendi yazı tipi.

## Yeniden kurmak

1. `./fetch_stock.sh` — görselleri ve müziği `work/stock` altına indirir.
2. Emülatörde `rec.sh SERIAL start|stop NAME` ile kayıtlar alınır (`ui.sh` ekrandaki yazılara dokunur,
   `sheet.sh` bir kaydın kare sayfasını çıkarır, `quad.py` bir fotoğraftaki ekranın köşelerini bulur).
3. `python3 build.py` — bölümleri ve son videoyu üretir (ffmpeg 8; tek adım için ör. `build.py seg_xbox final`).

Microsoft'un logoları ve sesleri kullanılmadı; videonun sonunda "Microsoft ile ilişkili değildir" notu var.
