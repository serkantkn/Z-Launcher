# Play Console'a verilecek beyanlar

Buradaki metinler konsola yapıştırılmak üzere yazıldı. Hepsi uygulamanın gerçekte yaptığı
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

**Bu, uygulamanın hangi işlevi için gerekli?**

> Z Launcher, Windows Phone arayüzünü yeniden üreten bir başlatıcıdır. Servis bu arayüzün iki
> parçası içindir ve her ikisi de kullanıcı ayarlardan açmadıkça çalışmaz:
>
> 1. **Ses çubuğu.** Android'de donanım tuşları yalnızca ön plandaki uygulamaya ulaşır; başlatıcı
>    arka plandayken Windows Phone ses çubuğunu gösterebilmenin tek yolu ses tuşlarını
>    erişilebilirlik servisiyle filtrelemektir.
> 2. **Eylem merkezi.** Durum çubuğundan aşağı çekince Android'in bildirim paneli yerine Windows 10
>    Mobile eylem merkezi açılır. Bunun için servis durum çubuğunun yerini pencere listesinden
>    bulur, Android paneli yine de açılırsa (kilit ekranı dışındaki başka bir yoldan) bunu
>    SystemUI'nin pencere olayından anlar, paneli kapatır ve eylem merkezini gösterir. Kilit
>    ekranında hiçbir şey yapmaz; orada Android'in kendi paneli kalır.

**Kullanıcıya nasıl açıklanıyor? (belirgin açıklama ve onay)**

> Kullanıcı Ayarlar → ekran+ses → ses çubuğu ya da Ayarlar → eylem merkezi'nden servisi açmak
> istediğinde, Android'in erişilebilirlik ayarlarına gönderilmeden önce tam ekran bir iletişim
> kutusu çıkar. Kutuda servisin bu iki işi, diğer uygulamaların ekranındaki yazıları okumadığı,
> hiçbir şey kaydetmediği ve dışarı göndermediği yazar; "Kabul et" ve "İptal" düğmeleri vardır.
> Yalnızca "Kabul et"e basınca ayarlar açılır. Aynı açıklama Android'in erişilebilirlik
> ekranında servisin altında da görünür (`accessibility_service_description`).

**Toplanan veri:** Hiçbiri. Servis hiçbir şey kaydetmez, saklamaz ve göndermez. Pencere türü,
konumu ve SystemUI olaylarının metni yalnızca o an karar vermek için kullanılır ve atılır.

**Video:** Servisin açıklama kutusu → kabul → Android erişilebilirlik ayarlarında açma → başka
bir uygulama açıkken ses tuşuna basınca Windows Phone ses çubuğu → durum çubuğundan çekince
eylem merkezi.

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
| `QUERY_ALL_PACKAGES` | Başlatıcı, yüklü tüm uygulamaları listelemek zorundadır. Google bu kullanımı açıkça kabul eder. |
| `PACKAGE_USAGE_STATS` | Uygulama listesinde "en çok kullanılanlar" sıralaması. Kullanıcı açmazsa istenmez. |
| `SYSTEM_ALERT_WINDOW` | WP ses çubuğu ve bildirim şeridi, diğer uygulamaların üstünde gösterilir. |
| Bildirim dinleyicisi | Canlı kutucuklardaki bildirim sayıları ve önizlemeleri. Hiçbir bildirim diske yazılmaz. |
| `ACCESS_FINE_LOCATION` | Yalnızca ayarlar → bağlantılar'da bağlı Wi-Fi ağının adı (Android SSID'yi bu izin olmadan vermez). İsteğe bağlı, kullanıcı bağlantılar sayfasından ister; arka planda konum yok. Hava durumu konumu ~1 km'ye yuvarlar. |
| `BLUETOOTH_CONNECT` | Yalnızca ayarlar → bağlantılar'da şu an bağlı Bluetooth cihazlarının adları. Tarama, eşleştirme, bağlanma yok. İsteğe bağlı. |
| `SCHEDULE_EXACT_ALARM` | Saat hub'ındaki alarmlar ve zamanlayıcı, takvim hatırlatmaları — dakikası dakikasına çalması gerekir. Kullanıcının "Alarmlar ve hatırlatıcılar" ayarından verdiği izin; Android 14'ten itibaren kapalı gelir, saat hub'ı kullanıcıyı o ayara yönlendirir. `USE_EXACT_ALARM` bilerek kullanılmıyor: Play onu asıl işi çalar saat ya da takvim olan uygulamalara ayırıyor. Bu iznin beyan formu yok. |
| `USE_FULL_SCREEN_INTENT` | Gelen çağrı ekranı ve çalan alarm, kilit ekranında tam ekran açılır. Play'in "Tam ekran amaç izni" beyanında "Arama" ve "Alarm" seçilir. |
| `REQUEST_INSTALL_PACKAGES` | Dosyalar hub'ı bir dosya yöneticisidir ve .apk dosyalarını sistem yükleyicisine verir; İnternet hub'ı da kendi indirdiği .apk'yı açar. Bu izin olmadan yükleyici hiç açılmaz. Kullanıcı yine de ayarlardan "bilinmeyen uygulama" izni verir ve her yüklemeyi kendisi onaylar; uygulama kendi başına hiçbir şey yüklemez. Play bu izni "dosya yöneticisi / tarayıcı" gerekçesiyle kabul eder, beyanda bu gerekçe seçilmeli. |

---

## 5. Veri güvenliği (Data safety) formu

**Toplanan veri:** Yok. Uygulamanın sunucusu yoktur; hiçbir veri geliştiriciye ulaşmaz.

**Paylaşılan veri:** Yok — aşağıdakiler kullanıcının kendi isteğiyle üçüncü taraf servislere
gider, geliştiriciye değil:

| Nereye | Ne | Ne zaman |
|---|---|---|
| open-meteo.com | Yaklaşık konum (enlem/boylam, ~1 km'ye yuvarlanmış) veya şehir adı | Hava durumu hub'ı / kutucuğu kullanıldığında |
| lrclib.net, musicbrainz.org, coverartarchive.org, itunes.apple.com | Çalan parçanın adı, sanatçı, albüm | Ayarlardan "internetten sözler ve kapaklar" açıksa |
| Seçilen arama motoru | Tarayıcıya yazılan metin | Arama önerileri açıkken yazarken |
| Kullanıcının kendi IMAP/SMTP sunucusu | E-posta içeriği ve kimlik bilgileri | E-posta hesabı eklenmişse |

**Şifreleme:** Aktarımda TLS. Cihazda: hesap şifreleri ve bulut jetonları AES-GCM (anahtar
Android Keystore'da), kilitli notların gövdesi aynı şekilde.

**Silme:** Uygulamayı kaldırmak her şeyi siler; ayrıca Ayarlar → Uygulamalar → Verileri
temizle. Silinecek sunucu kaydı yoktur.

---

## 6. Yayın öncesi kontrol listesi

- [ ] Yükleme anahtarı üretildi ve yedeklendi (bkz. `KEYSTORE.md`)
- [ ] `PRIVACY.md` / `PRIVACY.en.md` içindeki iletişim e-posta adresi dolduruldu
- [ ] GitHub Pages açıldı, gizlilik politikası adresi konsola yazıldı
- [ ] Mağaza görselleri ve açıklama yüklendi
- [ ] Yukarıdaki beyanlar dolduruldu
- [ ] İçerik derecelendirme anketi tamamlandı
- [ ] İç test kanalında bir tur denendi (gerçek cihazda kurulum, varsayılan başlatıcı yapma)
