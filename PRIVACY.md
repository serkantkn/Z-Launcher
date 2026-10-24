# Zeta — Gizlilik Politikası

Son güncelleme: 20 Ekim 2026 · Sürüm 1.4

## Kısaca

Zeta'nın sunucusu yok. Hiçbir veriniz bize ulaşmıyor, çünkü ulaşabileceği bir yer yok.
Uygulamada reklam ağı, analiz aracı, çökme raporlama servisi veya kullanıcı hesabı
bulunmuyor. Okuduğu her şey — kişileriniz, mesajlarınız, fotoğraflarınız, notlarınız —
telefonunuzda kalır.

Telefondan çıkan tek şey, sizin istediğiniz bir işi yapmak için gereken en küçük bilgidir:
hava durumu için yaklaşık konum, şarkı sözü için parça adı, arama önerisi için yazdığınız
metin, haberler için seçtiğiniz konular ve siteler. Aşağıda tek tek yazılı.

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
| Hassas konum *(isteğe bağlı)* | Yalnızca ayarlar → bağlantılar'da bağlı olduğunuz Wi-Fi ağının adını göstermek için: Android ağ adını yalnızca bu izni olan uygulamalara verir. Konumunuz bu izinle okunmaz; hava durumu yuvarlanmış, yaklaşık konumu kullanır. Tarayıcıda konum izni verdiğiniz siteler, her tarayıcıda olduğu gibi, konumu hassas olarak alabilir. |
| Yakındaki cihazlar *(isteğe bağlı)* | Yalnızca ayarlar → bağlantılar'da şu an bağlı Bluetooth cihazlarının adlarını göstermek için. Cihaz aranmaz, eşleştirilmez, bağlanılmaz. |

Klavye hakkında ayrıca: **şifre alanlarında hiçbir şey öğrenilmez**, uygulamanın "bunu
öğrenme" bayrağı (gizli sekmeler, tek kullanımlık kodlar) dinlenir ve şifre yöneticisinin
"hassas" olarak işaretlediği kopyalar pano geçmişine yazılmaz.

## Telefondan çıkan veriler

Her biri isteğe bağlıdır ve yalnızca o özelliği kullandığınızda gerçekleşir. Hiçbirine
kimliğiniz, cihaz numaranız veya reklam kimliğiniz eklenmez.

**Hava durumu — open-meteo.com**
Yaklaşık konumunuz (enlem/boylam, yaklaşık bir kilometreye yuvarlanmış) veya aradığınız şehir adı gönderilir. Hesap gerektirmez,
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

**Bulut dosyaları — uygulama kendisi bağlanmaz**
Dosyalar hub'ında buluttan bir dosya açtığınızda ya da buluta kaydettiğinizde Android'in kendi
dosya seçicisi kullanılır; dosyayı telefonunuzdaki bulut uygulaması (Google Drive, OneDrive,
Dropbox…) getirir ya da götürür. Uygulama hiçbir bulut hesabına bağlanmaz, oturum açmaz ve
hesap bilgisi tutmaz.

**Haberler — news.google.com ve takip ettiğiniz siteler**
Yalnızca haberler hub'ı açıldığında ya da haber karosu başlangıç ekranındayken. Google Haberler'den telefonun dili ve ülkesine göre seçtiğiniz konuların akışı, takip ettiğiniz sitelerin RSS akışları ise doğrudan o sitelerden alınır. Bir haberi açtığınızda resmi ve özeti haberin kendi sayfasından istenebilir. Konum ya da kimlik gönderilmez.

**Reklam ve izleyici engelleme listesi — raw.githubusercontent.com**
Tarayıcıda engelleme açıksa, Steven Black'in açık engelleme listesi bir kez, sonra haftada bir yalnızca Wi-Fi'dayken indirilir. Yalnızca dosya istenir; hiçbir şey gönderilmez.

**Tarayıcı**
Ziyaret ettiğiniz siteler doğrudan sizinle o site arasındadır; uygulama araya girmez, bir
yere bildirmez. Engelleme açıksa, sayfaların reklam ve izleyici sunucularına yaptığı istekler telefonda durdurulur. Başlangıç sayfasındaki site karolarının simgeleri Google'ın simge servisinden site adıyla istenir. "Sayfayı çevir" seçildiğinde sayfa Google Çeviri üzerinden açılır.

## Telefon ile televizyon arasında (telefon bağlantısı)

Zeta televizyonda da çalışır. Telefonunuzu aynı Wi-Fi ağındaki bir televizyonla
eşlerseniz, telefon gelen aramaları ve mesajları o televizyona bildirir; televizyon bunları
ekranın köşesinde gösterir. Bu özellik siz eşleştirme yapmadıkça hiçbir şey göndermez.

- **Ne gider:** arayanın ya da mesaj gönderenin adı (rehberdeki adı, yoksa numarası),
  bildirimin türü (arama, cevapsız arama, mesaj), mesajın geldiği uygulamanın adı ve
  telefonunuzun adı. **Mesajın metni varsayılan olarak gönderilmez**; ancak telefonda
  "mesajın metnini de gönder"i açarsanız ilk 300 karakteri gider.
- **Nereye gider:** yalnızca eşlediğiniz televizyona, doğrudan yerel ağınız üzerinden. Arada
  bir sunucu yoktur, internete çıkmaz, bize ulaşmaz.
- **Nasıl korunur:** eşleştirme televizyonda gösterilen tek kullanımlık 6 haneli kodla yapılır;
  her bildirim yalnızca o iki cihazın bildiği bir anahtarla (AES-GCM) şifrelenir. Anahtarlar
  Android Keystore ile korunur.
- **Nasıl okunur:** Zeta varsayılan telefon ve SMS uygulamasıysa aramaları ve mesajları
  kendisi görür; değilse telefon ve mesaj uygulamalarınızın bildirimlerinden okur (bunun için
  bildirim erişimi gerekir). WhatsApp gibi diğer uygulamaların mesajları da isteğe bağlı olarak
  bildirimlerden okunur.
- **Televizyonda:** gelen bildirim yalnızca ekranda gösterilir ve birkaç saniye sonra kaybolur;
  kaydedilmez. Televizyon yalnızca eşlediği telefonların adını ve anahtarını saklar.
- **Kapatmak:** telefonda ayarlar → bağlantılar → TV bağlantısı'ndan her tür ayrı ayrı ya da
  tamamen kapatılır; eşlenen cihaz her iki taraftan da kaldırılabilir.

## Televizyonda

Televizyonda erişilebilirlik servisi açılırsa yalnızca üç iş için kullanılır: kumandanın Home
tuşunu yakalamak, televizyon arayüzünün "kapat" penceresinde seçtiğinizde televizyonu kapatma
menüsünü açmak ya da uyutmak ve eşlediğiniz telefonun bildirimini diğer uygulamaların üstünde
göstermek. Ekrandaki yazılar okunmaz, hiçbir şey kaydedilmez ve gönderilmez.

## Nerede ve nasıl saklanır

Her şey uygulamanın kendi özel alanında, telefonunuzda durur.

- **E-posta şifreleri ve bulut jetonları**: AES-GCM ile, anahtarı Android Keystore'da tutulan
  bir kasada. Anahtar telefondan çıkmaz ve yedeklenmez.
- **Kilitli notlar**: gövdesi aynı şekilde şifrelenir. Telefon kök erişimiyle açılsa bile
  kilitli notun içeriği okunamaz.
- **Gezinme geçmişi ve indirmeler**: ayrı bir dosyada, yedekleme dışında.
- **Okuma listesi, çevrimdışı kopyaları ve videoların kaldığınız yerleri**: ayrı dosyalarda, yedekleme dışında.

## Yedekleme

Android'in yedekleme ve yeni telefona aktarma özelliği açıksa, uygulamanın **görünüm
ayarları ve başlangıç ekranı düzeni** yedeklenir. Şunlar bilerek dışarıda bırakılmıştır ve
telefondan çıkmaz: e-posta hesapları ve önbelleği, notlar ve ekleri, klavyenin öğrendiği
kelimeler ve pano geçmişi, bulut hesapları, takvim kayıtları, gezinme geçmişi, okuma listesi, videoların kaldığınız
yerleri, konum tercihi, şifre kasası.

**Pro denemesi.** 10 günlük denemeyi başlattığınızda, başladığı an (bir tarih, başka hiçbir şey)
uygulamanın ayarlarında, Google Play hizmetlerinin Block Store alanında ve gün olarak, Android'in
uygulama için tuttuğu bileşen ayarlarında saklanır; sonuncusu, uygulamanın verisi temizlense de
denemenin yeniden başlamaması içindir ve uygulama kaldırılınca silinir. Telefonun yedeklemesi açıksa
tarih, uygulama kaldırılıp yeniden kurulduğunda da korunur; uçtan uca şifreli yedekleme varsa Google
hesabınızın yedeğiyle yeni telefona geçer. Kimliğiniz, hesabınız ya da cihaz numaranız eklenmez; bize gelmez.

## Çocuklar

Uygulama çocuklara yönelik değildir ve yaş bilgisi toplamaz.

## Verilerinizi silmek

Uygulamayı kaldırmak, sakladığı her şeyi siler. Kaldırmadan silmek isterseniz Android
Ayarlar → Uygulamalar → Zeta → Depolama → Verileri temizle. Bizde silinecek bir hesap
veya kayıt yoktur, çünkü hiçbir zaman oluşmaz.

## Değişiklikler

Bu politika değişirse yukarıdaki tarih güncellenir ve değişiklik uygulamanın "neler
değişti" ekranında belirtilir.

## İletişim

Sorularınız için: **<buraya iletişim e-posta adresini yaz>**
