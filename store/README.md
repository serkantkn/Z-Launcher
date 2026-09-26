# Mağaza materyali

| Dosya | Ne için | Durum |
|---|---|---|
| `feature-graphic.png` | Öne çıkan görsel, 1024 × 500 | Hazır (Türkçe alt satır) |
| `feature-graphic-en.png` | Aynısı, İngilizce alt satır | Hazır |
| `screenshots/*.png` | Telefon ekran görüntüleri, 1080 × 2400 | **Taslak** |
| `../play_store_icon.png` | Uygulama ikonu, 512 × 512 | Hazır |
| `tanitim-videosu.mp4` | Tanıtım videosu, 1920 × 1080 (16:9), 2 dk 24 sn | Hazır (emülatör çekimi, Pro sürüm) |

Metinler (uygulama adı, kısa ve tam açıklama, iki dilde) `../STORE.md` dosyasında.

## Ekran görüntüleri hakkında

Buradakiler emülatörde, test içeriğiyle alındı: kişiler, mesajlar ve fotoğraflar önceki
oturumlarda üretilmiş örneklerdir. Mağaza sayfasında en çok bakılan şey ekran görüntüleridir
ve bunlar o iş için yeterince iyi değil — düzeni gösterir, uygulamayı satmaz.

Yayından önce kendi telefonunda, kendi fotoğrafların, kişilerin ve müziğinle almanı öneririm.
İyi bir set şöyle olur:

1. Başlangıç ekranı, kutucuklar dolu ve canlı (kişi kutucuğunda yüzler, müzik kutucuğunda
   kapak, hava durumu kutucuğunda gerçek hava)
2. Müzik hub'ı, bir şarkı çalarken, albüm kapağı görünür hâlde
3. Fotoğraflar hub'ı, gerçek fotoğraflarla
4. Telefon veya Mesajlar hub'ı
5. Ayarlar → görünüm: tema ve vurgu rengi seçimi (uygulamanın ne kadar kişiselleştiğini
   gösterir)
6. Zune liste düzeni (kutucuk düzeninin alternatifi olduğunu gösterir)

Play en az 2, en fazla 8 telefon görüntüsü ister; 16:9 ile 9:16 arasında, kısa kenarı en az
1080 piksel. Tablet görüntüleri isteğe bağlıdır ama tablet düzeni varsa eklemeye değer.


## Tanıtım videosu hakkında

`tanitim-videosu.mp4` — 1920 × 1080 (16:9), 30 fps, 2 dk 24 sn, sessiz. Play'in tanıtım
videosu alanı bir YouTube bağlantısı ister, dosya yüklemez: videoyu YouTube'a yükleyip
bağlantısını Play Console'a yazman gerekir. Yüklerken listelenmemiş (unlisted) değil, herkese
açık olmalı, yoksa Play kabul etmez.

### Kurgu

Her karede solda büyük ince yazı, altında ekranın büyütülmüş bir parçası, sağda telefonun
kendisi duruyor — çerçevenin tamamı kullanılıyor, telefon 16:9'un ortasında yalnız kalmıyor.
Bölüm başlıkları tam alan vurgu rengi ve karo ızgarasıyla açılıyor; geçişler Metro'nun dili
olan yatay/dikey kaydırmalar, yumuşak çapraz geçiş yok.

Sırasıyla: açılış · **zune başlangıç ekranı** (hub listesi, listenin kayması, favoriler
bölmesine geçiş, turnike) · **windows phone panosu** (canlı karoların dönmesi, düzenleme kipi,
karo boyutu diyaloğu, karonun büyümesi) · **köşeli → yuvarlatılmış** · **vurgu rengi** magenta,
camgöbeği, lime, turuncu, mor · **koyu → açık tema** · **sosyal hub** · **klavye** ve tahmin
çubuğu · **hub'lar**: fotoğraflar, müzik, kamera, telefon, kişiler, mesajlar, internet,
dosyalar, hava durumu, e-posta, saat, takvim, notlar, hesap makinesi — her biri panodan
turnike animasyonuyla açılıyor.

Köşe biçimi, vurgu rengi ve tema değişimleri **tek karede** oluyor: her durum ayrı ayrı
yakalanıp aynı çerçevede sert kesme ile birleştirildi. Böylece ayarlar ekranı videoda hiç
görünmüyor, değişimi anında görüyorsun.

### Bilinmesi gerekenler

**Pro sürümle çekildi.** Vurgu renkleri, özel duvar kağıdı, sosyal hub ve klavye ücretsiz
sürümde kilitli; videoda hepsi görünüyor. Mağaza açıklamasında bunların Pro olduğu yazıyor,
ama videoya bir "Pro" ibaresi eklemek istersen yerinde olur.

Sesi yok. Bilerek: telifsiz olduğundan emin olmadığım bir müziği mağaza materyaline koymak
istemedim. Müzik eklemek istersen YouTube'un kendi ses kitaplığı ya da satın alınmış bir
lisans en güvenlisi.

İçerik emülatörde, üretilmiş test verisiyle çekildi: fotoğraflar emülatörün sanal sahnesinden
kameranın kendisiyle çekildi, albüm kapakları ve şarkı adları uydurmadır, kişiler ve mesajlar
örnek veridir. Gerçek telefonunda kendi içeriğinle çekersen daha inandırıcı olur — ekran
görüntüleri için de aynı şey geçerli.

Videoda uygulama adı ya da logo geçmiyor.

### Yeniden çekmek gerekirse

Emülatörün ekran kaydı **değişken kare hızı** yazıyor ve ekran durgunken hiç kare üretmiyor;
zaman damgasına göre kesince ffmpeg bir sonraki kareye atlıyor ve yanlış an videoya giriyor.
Her kaydı kesmeden önce sabit 30 fps'e çevirmek gerekiyor. Ayrıca başlangıç ekranında aşağı
doğru sürükleme bildirim gölgesini açıyor; kayıt sırasında yalnızca yukarı doğru kaydırma
kullanılmalı.
