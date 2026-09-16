# Z Launcher — Gizlilik Politikası

Son güncelleme: 16 Eylül 2026 · Sürüm 1.0

## Kısaca

Z Launcher'ın sunucusu yok. Hiçbir veriniz bize ulaşmıyor, çünkü ulaşabileceği bir yer yok.
Uygulamada reklam ağı, analiz aracı, çökme raporlama servisi veya kullanıcı hesabı
bulunmuyor. Okuduğu her şey — kişileriniz, mesajlarınız, fotoğraflarınız, notlarınız —
telefonunuzda kalır.

Telefondan çıkan tek şey, sizin istediğiniz bir işi yapmak için gereken en küçük bilgidir:
hava durumu için yaklaşık konum, şarkı sözü için parça adı, arama önerisi için yazdığınız
metin. Aşağıda tek tek yazılı.

## Telefonda kalan veriler

Bunların hiçbiri telefonunuzdan çıkmaz. İzinlerin her biri yalnızca ilgili bölüm
kullanıldığında istenir; vermezseniz o bölüm çalışmaz, uygulamanın kalanı çalışmaya devam
eder.

| Ne | Niçin |
|---|---|
| Kişiler | Kişiler hub'ı, arayan kişinin adı, başlangıç ekranına sabitlenen kişiler |
| Çağrı kaydı ve telefon durumu | Telefon hub'ı, cevapsız çağrı bildirimi, telesekreter |
| SMS / MMS | Mesajlar hub'ı, canlı kutucuktaki mesaj önizlemesi |
| Takvim | Takvim hub'ı ve ajanda kutucuğu |
| Fotoğraf, video, müzik, dosyalar | Fotoğraflar, Müzik ve Dosyalar hub'ları |
| Kamera ve mikrofon | Kamera hub'ı, sesli not, klavyedeki sesle yazma |
| Bildirimler | Canlı kutucuklardaki bildirim sayıları ve önizlemeler |
| Yüklü uygulamalar ve kullanım istatistikleri | Uygulama listesi, en çok kullanılanlar |
| Klavye ile yazdıklarınız | Kelime tahmini ve düzeltme |

Klavye hakkında ayrıca: **şifre alanlarında hiçbir şey öğrenilmez**, uygulamanın "bunu
öğrenme" bayrağı (gizli sekmeler, tek kullanımlık kodlar) dinlenir ve şifre yöneticisinin
"hassas" olarak işaretlediği kopyalar pano geçmişine yazılmaz.

## Telefondan çıkan veriler

Her biri isteğe bağlıdır ve yalnızca o özelliği kullandığınızda gerçekleşir. Hiçbirine
kimliğiniz, cihaz numaranız veya reklam kimliğiniz eklenmez.

**Hava durumu — open-meteo.com**
Yaklaşık konumunuz (enlem/boylam) veya aradığınız şehir adı gönderilir. Hesap gerektirmez,
kimlik taşımaz.

**Şarkı sözleri ve albüm kapakları — lrclib.net, musicbrainz.org, coverartarchive.org,
itunes.apple.com**
Yalnızca ayarlardan açtıysanız. Gönderilen şey çalan parçanın adı, sanatçısı ve albümüdür.
Dosyanız yüklenmez.

**Arama önerileri — seçtiğiniz arama motoru (Google, Bing, DuckDuckGo, Yandex)**
Tarayıcıda yazdığınız metin, siz yazarken öneri almak için gönderilir. Ayarlardan
kapatılabilir; kapalıyken hiçbir şey gitmez.

**E-posta — sizin belirlediğiniz sunucular**
Girdiğiniz IMAP/SMTP sunucusuna doğrudan bağlanılır. Aradan geçen kimse yok; bizim
sunucumuz da yok.

**Google Drive — googleapis.com**
Yalnızca Dosyalar hub'ında bir Drive hesabı bağlarsanız. Yetkilendirme Google'ın kendi
ekranıyla yapılır; jeton telefonda şifreli olarak saklanır.

**Tarayıcı**
Ziyaret ettiğiniz siteler doğrudan sizinle o site arasındadır; uygulama araya girmez, bir
yere bildirmez.

## Nerede ve nasıl saklanır

Her şey uygulamanın kendi özel alanında, telefonunuzda durur.

- **E-posta şifreleri ve bulut jetonları**: AES-GCM ile, anahtarı Android Keystore'da tutulan
  bir kasada. Anahtar telefondan çıkmaz ve yedeklenmez.
- **Kilitli notlar**: gövdesi aynı şekilde şifrelenir. Telefon kök erişimiyle açılsa bile
  kilitli notun içeriği okunamaz.
- **Gezinme geçmişi ve indirmeler**: ayrı bir dosyada, yedekleme dışında.

## Yedekleme

Android'in yedekleme ve yeni telefona aktarma özelliği açıksa, uygulamanın **görünüm
ayarları ve başlangıç ekranı düzeni** yedeklenir. Şunlar bilerek dışarıda bırakılmıştır ve
telefondan çıkmaz: e-posta hesapları ve önbelleği, notlar ve ekleri, klavyenin öğrendiği
kelimeler ve pano geçmişi, bulut hesapları, takvim kayıtları, gezinme geçmişi, konum
tercihi, şifre kasası.

## Çocuklar

Uygulama çocuklara yönelik değildir ve yaş bilgisi toplamaz.

## Verilerinizi silmek

Uygulamayı kaldırmak, sakladığı her şeyi siler. Kaldırmadan silmek isterseniz Android
Ayarlar → Uygulamalar → Z Launcher → Depolama → Verileri temizle. Bizde silinecek bir hesap
veya kayıt yoktur, çünkü hiçbir zaman oluşmaz.

## Değişiklikler

Bu politika değişirse yukarıdaki tarih güncellenir ve değişiklik uygulamanın "neler
değişti" ekranında belirtilir.

## İletişim

Sorularınız için: **<buraya iletişim e-posta adresini yaz>**
