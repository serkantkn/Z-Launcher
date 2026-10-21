# Play Console'a verilecek beyanlar

Buradaki metinler konsola yapıştırılmak üzere yazıldı. Uygulama tek bir paket olarak hem
telefon/tablette hem Android TV'de yayımlanır; TV'ye özgü maddeler her bölümde ayrıca yazıldı,
TV'yi Play'e ekleme adımları bölüm 6'da. Hepsi uygulamanın gerçekte yaptığı
şeyi anlatıyor; bir inceleme uzmanı APK'yı açıp baktığında söylenenle göreceği şey aynı
olmalı, yoksa ilk ret ikincisini de getirir.

---

## 1. Erişilebilirlik API beyanı

> **Bu en riskli maddedir.** Google erişilebilirlik API'sinin engelli kullanıcılara yardım
> amacıyla kullanılmasını bekler; bizim kullanımımız bir arayüz özelliğidir. Beyan, servisin
> yaptığını eksiksiz ve abartısız anlatmalı: inceleme uzmanı servisin ayar dosyasında
> `canRetrieveWindowContent="true"` görecek, beyan bunu açıklamalı.

**Uygulamanız bir erişilebilirlik aracı mı?** Hayır (`isAccessibilityTool` tanımlı değil).

**Hangi erişilebilirlik özelliklerini kullanıyorsunuz?**

> - Donanım tuşu filtreleme (`flagRequestFilterKeyEvents`): yalnızca ses artırma ve azaltma
>   tuşları işlenir; diğer tüm tuşlar sisteme olduğu gibi geçer.
> - Etkileşimli pencere listesi (`flagRetrieveInteractiveWindows`, bunun için
>   `canRetrieveWindowContent`): yalnızca pencerelerin türü ve ekrandaki konumu okunur, durum
>   çubuğunun görünüp görünmediğini anlamak için. Pencerelerin içindeki düğümler okunmaz.
> - Pencere durumu olayları (`typeWindowStateChanged`): yalnızca paketi
>   `com.android.systemui` olan olaylara bakılır; olayın sınıf adı ve kısa metninden Android
>   bildirim panelinin açıldığı anlaşılır. Diğer uygulamaların olayları hemen bırakılır.
> - Genel eylemler: `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` (eski sürümlerde `BACK`) ile
>   bildirim paneli kapatılır.
>
> **Android TV'de** servis başka işler yapar (aynı servis, aynı ayar dosyası):
>
> - Donanım tuşu filtreleme: yalnızca kumandanın Home tuşu (`KEYCODE_HOME`) işlenir; Google TV
>   ana ekranı yerine Z Launcher açılır. Diğer bütün tuşlar sisteme olduğu gibi geçer.
> - Genel eylemler: kullanıcı televizyon arayüzünün "kapat" penceresinde "TV'yi kapat"ı seçerse
>   `GLOBAL_ACTION_POWER_DIALOG`, "uyku"yu seçerse `GLOBAL_ACTION_LOCK_SCREEN`. Kendiliğinden
>   hiçbir zaman çağrılmaz.
> - Erişilebilirlik kaplaması (`TYPE_ACCESSIBILITY_OVERLAY`): eşlenen telefondan gelen arama ve
>   mesaj bildirimi, açık uygulamanın üstünde ekranın köşesinde birkaç saniye gösterilir.
>   Dokunulamaz, odak almaz.

**Bu, uygulamanın hangi işlevi için gerekli?**

> Z Launcher, canlı karolar ve hub'lardan kendi arayüzünü kuran bir başlatıcıdır. Servis bu arayüzün iki
> parçası içindir ve her ikisi de kullanıcı ayarlardan açmadıkça çalışmaz:
>
> 1. **Ses çubuğu.** Android'de donanım tuşları yalnızca ön plandaki uygulamaya ulaşır; başlatıcı
>    arka plandayken kendi ses çubuğunu gösterebilmenin tek yolu ses tuşlarını
>    erişilebilirlik servisiyle filtrelemektir.
> 2. **Eylem merkezi.** Durum çubuğundan aşağı çekince Android'in bildirim paneli yerine
>    uygulamanın kendi eylem merkezi açılır. Bunun için servis durum çubuğunun yerini pencere listesinden
>    bulur, Android paneli yine de açılırsa (kilit ekranı dışındaki başka bir yoldan) bunu
>    SystemUI'nin pencere olayından anlar, paneli kapatır ve eylem merkezini gösterir. Kilit
>    ekranında hiçbir şey yapmaz; orada Android'in kendi paneli kalır.

> 3. **Televizyonda Home tuşu.** Google TV, kullanıcı başka bir başlatıcı seçse bile kumandanın
>    Home tuşunu kendi ana ekranına bağlar. Z Launcher'ın televizyonda ana ekran olarak
>    kullanılabilmesinin tek yolu bu tuşu servisle yakalamaktır. Aynı servis, kullanıcının
>    seçtiği "TV'yi kapat / uyku" eylemlerini ve eşlenen telefonun bildirimini gösterir.

**Kullanıcıya nasıl açıklanıyor? (belirgin açıklama ve onay)**

> Kullanıcı Ayarlar → ekran+ses → ses çubuğu ya da Ayarlar → eylem merkezi'nden servisi açmak
> istediğinde, Android'in erişilebilirlik ayarlarına gönderilmeden önce tam ekran bir iletişim
> kutusu çıkar. Kutuda servisin bu iki işi, diğer uygulamaların ekranındaki yazıları okumadığı,
> hiçbir şey kaydetmediği ve dışarı göndermediği yazar; "Kabul et" ve "İptal" düğmeleri vardır.
> Yalnızca "Kabul et"e basınca ayarlar açılır. Aynı açıklama Android'in erişilebilirlik
> ekranında servisin altında da görünür (`accessibility_service_description`).
> Televizyonda kutu Ayarlar → sistem → ana ekran → "Home tuşu Z Launcher'ı açsın"dan çıkar ve
> servisin televizyondaki üç işini anlatır (`a11y_disclosure_body_tv`); televizyonda servisin
> adı "Z Launcher Home tuşu"dur.

**Toplanan veri:** Hiçbiri. Servis hiçbir şey kaydetmez, saklamaz ve göndermez. Pencere türü,
konumu ve SystemUI olaylarının metni yalnızca o an karar vermek için kullanılır ve atılır.

**Video:** Servisin açıklama kutusu → kabul → Android erişilebilirlik ayarlarında açma → başka
bir uygulama açıkken ses tuşuna basınca uygulamanın ses çubuğu → durum çubuğundan çekince
eylem merkezi. **Televizyon için ikinci bir parça ekle:** TV'de ayarlar → sistem → ana ekran →
açıklama kutusu → kabul → servisi açma → başka bir uygulama (ör. YouTube) açıkken Home tuşu →
Z Launcher açılır; Görevler → kapat → "uyku". Play Console tek video alanı veriyor; iki parçayı tek
videoda birleştir.

---

## 2. SMS ve Çağrı Kaydı izin beyanı

Google bu izinleri yalnızca varsayılan SMS veya telefon uygulaması olabilen uygulamalara
verir. Z Launcher her iki rolü de üstlenebilir: manifestte `ACTION_SENDTO` (sms/smsto/mms),
`ACTION_DIAL`, `ACTION_CALL` niyet filtreleri, `HeadlessSmsSendService`, `WpInCallService`,
`SmsReceiver` ve `MmsReceiver` tanımlıdır.

**Temel işlev açıklaması:**

> Uygulama, mesajlaşma (Mesajlar hub'ı) ve telefon (Telefon hub'ı) hub'larını içerir ve
> kullanıcı isterse varsayılan SMS ve varsayılan telefon uygulaması olarak ayarlanabilir.
> Mesajlar okunur, yazılır, gönderilir ve bildirilir; çağrılar cevaplanır, başlatılır ve
> çağrı kaydı gösterilir. İzinler yalnızca bu hub'lar için kullanılır; hiçbir mesaj veya
> çağrı bilgisi cihazdan çıkmaz, üçüncü tarafa aktarılmaz.

**İzinler ne zaman isteniyor:** Yalnızca uygulama varsayılan uygulama yapıldıktan sonra.
Mesajlar hub'ı, telefon hub'ının arama geçmişi ve ayarlardaki izin anahtarları, uygulama
varsayılan SMS ya da telefon uygulaması değilse izin penceresi açmaz, önce Android'in "varsayılan
uygulama yap" penceresini açar (rol isteği). SMS ve çağrı kaydı izinlerini Android bu rolle
birlikte verir; rehber ve SIM bilgisi gibi rolün kapsamadığı izinler ancak rol alındıktan sonra
istenir. Uygulama varsayılan değilken bu izinler hiç istenmez.

**Telefon bağlantısı bu izinleri istemez.** Telefona gelen aramayı ve mesajı eşlenen
televizyona bildiren özellik, Z Launcher varsayılan telefon/SMS uygulamasıysa rolün verdiği
izinleri kullanır; değilse aramaları ve mesajları o uygulamaların **bildirimlerinden** okur
(bildirim dinleyicisi, bölüm 4). Kendisi yalnızca `READ_PHONE_STATE` (çalıyor / cevaplandı /
bitti) ve `READ_CONTACTS` (numarayı ada çevirmek) ister; ikisi de bu beyanın kapsamında değildir.
`PhoneLinkSmsReceiver` manifestte durur ama `RECEIVE_SMS` yalnızca SMS rolüyle geldiği için
uygulama varsayılan değilken hiçbir şey almaz.

**Video:** Konsol, akışı gösteren kısa bir video ister. Gerekli olan: uygulamayı varsayılan
SMS uygulaması yapma → bir mesaj gelmesi → Mesajlar hub'ında okunup yanıtlanması; sonra
varsayılan telefon uygulaması yapma → çağrı ekranı → çağrı kaydı.

---

## 3. Tüm dosyalara erişim (MANAGE_EXTERNAL_STORAGE)

> Uygulama, cihazdaki her türlü dosyayı gezebilen, açabilen, taşıyabilen, yeniden
> adlandırabilen ve paylaşabilen bir dosya yöneticisi (Dosyalar hub'ı) içerir. Kullanıcı
> herhangi bir klasörü açabilmeyi beklediği için erişim belirli medya türleriyle
> sınırlandırılamaz. İzin yalnızca Dosyalar hub'ı ilk kez kullanıldığında, kullanıcıya
> nedeni anlatılarak istenir; uygulamanın geri kalanı izin verilmeden de çalışır. Hiçbir
> dosya cihazdan çıkarılmaz.

---

## 4. Diğer hassas izinler

| İzin | Gerekçe |
|---|---|
| `QUERY_ALL_PACKAGES` | Başlatıcı, yüklü tüm uygulamaları listelemek zorundadır (televizyonda Uygulamalar şeridi ve program kitaplığı da). Google bu kullanımı açıkça kabul eder. |
| `PACKAGE_USAGE_STATS` | Uygulama listesinde "en çok kullanılanlar" sıralaması. Kullanıcı açmazsa istenmez. |
| `SYSTEM_ALERT_WINDOW` | WP ses çubuğu ve bildirim şeridi, diğer uygulamaların üstünde gösterilir. Televizyonda erişilebilirlik servisi kapalıysa telefon bildirimi bununla gösterilir. |
| Bildirim dinleyicisi | Canlı kutucuklardaki bildirim sayıları ve önizlemeleri. Telefon bağlantısı açıksa (kullanıcı bir televizyon eşlediyse) telefon, SMS ve mesajlaşma uygulamalarının bildirimlerinden gönderenin adı — kullanıcı açtıysa mesajın ilk 300 karakteri — eşlenen televizyona iletilir. Hiçbir bildirim diske yazılmaz. |
| `READ_PHONE_STATE` | Telefon hub'ı; telefon bağlantısında aramanın çaldığını, cevaplandığını ya da cevapsız kaldığını anlamak. |
| `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_NETWORK_STATE` | Telefon bağlantısı: aynı Wi-Fi'daki televizyonu mDNS (NSD) ile bulmak. Normal izinler, beyan gerekmez. |
| `ACCESS_FINE_LOCATION` | Yalnızca ayarlar → bağlantılar'da bağlı Wi-Fi ağının adı (Android SSID'yi bu izin olmadan vermez). İsteğe bağlı, kullanıcı bağlantılar sayfasından ister; arka planda konum yok. Hava durumu konumu ~1 km'ye yuvarlar. |
| `BLUETOOTH_CONNECT` | Yalnızca ayarlar → bağlantılar'da şu an bağlı Bluetooth cihazlarının adları. Tarama, eşleştirme, bağlanma yok. İsteğe bağlı. |
| `SCHEDULE_EXACT_ALARM` | Saat hub'ındaki alarmlar ve zamanlayıcı, takvim hatırlatmaları — dakikası dakikasına çalması gerekir. Kullanıcının "Alarmlar ve hatırlatıcılar" ayarından verdiği izin; Android 14'ten itibaren kapalı gelir, saat hub'ı kullanıcıyı o ayara yönlendirir. `USE_EXACT_ALARM` bilerek kullanılmıyor: Play onu asıl işi çalar saat ya da takvim olan uygulamalara ayırıyor. Bu iznin beyan formu yok. |
| `USE_FULL_SCREEN_INTENT` | Gelen çağrı ekranı ve çalan alarm, kilit ekranında tam ekran açılır. Play'in "Tam ekran amaç izni" beyanında "Arama" ve "Alarm" seçilir. |
| `REQUEST_INSTALL_PACKAGES` | Dosyalar hub'ı bir dosya yöneticisidir ve .apk dosyalarını sistem yükleyicisine verir; İnternet hub'ı da kendi indirdiği .apk'yı açar. Bu izin olmadan yükleyici hiç açılmaz. Kullanıcı yine de ayarlardan "bilinmeyen uygulama" izni verir ve her yüklemeyi kendisi onaylar; uygulama kendi başına hiçbir şey yüklemez. Play bu izni "dosya yöneticisi / tarayıcı" gerekçesiyle kabul eder, beyanda bu gerekçe seçilmeli. |

---

## 5. Veri güvenliği (Data safety) formu

**Toplanan veri:** Geliştiriciye hiçbir veri ulaşmaz; uygulamanın sunucusu yoktur. Telefon
bağlantısı için temkinli beyan aşağıda.

**Paylaşılan veri:** Yok — aşağıdakiler kullanıcının kendi isteğiyle üçüncü taraf servislere
gider, geliştiriciye değil:

| Nereye | Ne | Ne zaman |
|---|---|---|
| open-meteo.com | Yaklaşık konum (enlem/boylam, ~1 km'ye yuvarlanmış) veya şehir adı | Hava durumu hub'ı / kutucuğu kullanıldığında |
| lrclib.net, musicbrainz.org, coverartarchive.org, itunes.apple.com | Çalan parçanın adı, sanatçı, albüm | Ayarlardan "internetten sözler ve kapaklar" açıksa |
| Seçilen arama motoru | Tarayıcıya yazılan metin | Arama önerileri açıkken yazarken |
| Kullanıcının kendi IMAP/SMTP sunucusu | E-posta içeriği ve kimlik bilgileri | E-posta hesabı eklenmişse |
| Kullanıcının kendi televizyonu (yerel ağ) | Arayan/gönderen adı ve numarası, bildirim türü, uygulama adı, telefonun adı; isteğe bağlı mesaj metni | Telefon bağlantısı ile bir televizyon eşlenmişse |

**Telefon bağlantısı için formda ne işaretlenmeli?** Veri geliştiriciye ya da bir üçüncü tarafa
gitmez; kullanıcının kendi iki cihazı arasında, ev ağı içinde, uçtan uca şifreli (AES-GCM) gider.
Google'ın tanımı "cihazdan dışarı aktarılan veri"yi toplama sayar ve kendi cihazına aktarımı
açıkça muaf tutmaz. Ret riskini sıfırlamak için **temkinli beyan önerilir**: "Mesajlar → SMS
veya MMS", "Mesajlar → Diğer uygulama içi mesajlar" ve "Kişisel bilgiler → Ad"ı (arayanın adı)
**toplanıyor** olarak işaretle; hepsi için: *isteğe bağlı* (kullanıcı eşleme yapmadıkça olmaz),
amaç *Uygulama işlevselliği*, *geçici işleniyor* (televizyonda gösterilip atılır), *aktarımda
şifreli*. **Paylaşılan** olarak işaretleme: alıcı kullanıcının kendi cihazıdır, üçüncü taraf
değil.

**Şifreleme:** Aktarımda TLS. Cihazda: hesap şifreleri ve bulut jetonları AES-GCM (anahtar
Android Keystore'da), kilitli notların gövdesi aynı şekilde.

**Silme:** Uygulamayı kaldırmak her şeyi siler; ayrıca Ayarlar → Uygulamalar → Verileri
temizle. Silinecek sunucu kaydı yoktur.

---

## 6. Android TV'yi Play'e eklemek

Aynı uygulama paketi (.aab) televizyonda da çalışır; ayrı bir uygulama ya da ayrı bir mağaza
kaydı gerekmez. Manifest Android TV'nin şartlarını karşılıyor:
`android.software.leanback` ve `android.hardware.touchscreen` **zorunlu değil**,
`LEANBACK_LAUNCHER` niyet filtresi ve `android:banner` (320 × 180) var.

**Konsolda:**

1. Test ve yayınla → Kurulum → **Gelişmiş ayarlar** → **Form faktörleri** → Form faktörü ekle →
   **Android TV**. "Android TV'yi kapsayacak sürüm" olarak telefonla aynı yayın kanalını seç.
2. Ana mağaza kaydı → Android TV bölümüne **TV afişi** (`store/tv-banner.png`, 1280 × 720) ve
   **TV ekran görüntüleri** (`store/screenshots-tv/`, 1920 × 1080, en az 1) yükle.
3. Form faktörü sayfasında **Android TV'ye katıl** onayını ver. TV için Google ayrıca, elle
   inceleme yapar: onaylanana kadar uygulama telefonda yayında kalır, TV'de görünmez. İnceleme
   birkaç gün ile birkaç hafta sürebilir; ret gerekçesi e-postayla gelir.

**TV incelemesinde bakılanlar ve durumumuz:**

| Şart | Durum |
|---|---|
| Uygulama TV'nin kendi başlatıcısında afişiyle görünür | Var (`LEANBACK_LAUNCHER`, `tv_banner`) |
| Her ekran yalnızca kumandayla (yön tuşları, seç, geri) kullanılabilir; dokunmatik gerektiren yer yok | Bütün hub'lar, diyaloglar, ayarlar ve oynatıcı kumandayla denendi |
| Odaklanan öğe her zaman belli | Konsol'da büyüyen karo ve yeşil kenar, Salon'da cam kutu |
| Geri tuşu bir önceki ekrana döner, ana ekranda uygulamadan çıkmaz | Ana ekranda Geri en başa döner |
| Kenarlardan %5 güvenli alan | 48 × 27 dp kenar payı |
| Telefona özgü özellikler (kamera, telefon, SMS) TV'de istenmez | Kamera, telefon, mesajlar, kişiler hub'ları televizyonda gösterilmez |
| Metinler TV mesafesinden okunur | TV'de yazılar bir boy büyük |

**Riskler:**

- **Marka.** Tasarımların kendi adları var: Aura, Mozaik, Panorama (telefon ve tablet), Konsol ve Salon
  (televizyon). Uygulamada, mağaza metninde, sitede ve tanıtım videosunda başka bir şirketin ürün adı,
  logosu ya da sesi geçmiyor; yazı tipi açık lisanslı Selawik. Karo glifleri MIT lisanslı Fluent UI
  System Icons; lisansın istediği atıf README'de.

- **Erişilebilirlik servisi ile Home tuşu.** İncelemeci servisin Home tuşunu yakaladığını
  görecek. Beyan (bölüm 1) ve açıklama kutusu bunu açıkça söylüyor; servis isteğe bağlı ve
  kapalıyken uygulama yine çalışıyor (Home tuşu Google TV'yi açar). Ret gelirse ilk yapılacak:
  TV'de servisi tamamen kaldırıp Home tuşu özelliğini bırakmak; TV'nin geri kalanı etkilenmez.
- **"Başlatıcı" olarak TV.** Google TV, varsayılan başlatıcının değiştirilmesine izin vermiyor;
  Z Launcher TV'de sıradan bir uygulama olarak açılır. Mağaza metninde "Google TV'nin ana
  ekranının yerine geçer" gibi bir vaat yazma — metinler bu yüzden "olur", "açılır" diyor.

## 7. Yayın öncesi kontrol listesi

- [ ] Yükleme anahtarı üretildi ve yedeklendi (bkz. `KEYSTORE.md`)
- [ ] `PRIVACY.md` / `PRIVACY.en.md` içindeki iletişim e-posta adresi dolduruldu
- [ ] GitHub Pages açıldı, gizlilik politikası adresi konsola yazıldı
- [ ] Mağaza görselleri ve açıklama yüklendi
- [ ] Yukarıdaki beyanlar dolduruldu
- [ ] İçerik derecelendirme anketi tamamlandı
- [ ] İç test kanalında bir tur denendi (gerçek cihazda kurulum, varsayılan başlatıcı yapma)
- [ ] Android TV form faktörü eklendi, TV afişi ve TV ekran görüntüleri yüklendi (bölüm 6)
- [ ] İç test kanalından bir televizyona kurulup kumandayla denendi
- [ ] Erişilebilirlik videosuna televizyon parçası eklendi (bölüm 1)
- [ ] Veri güvenliği formunda telefon bağlantısı beyan edildi (bölüm 5)
