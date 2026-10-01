# Play Console'a verilecek beyanlar

Buradaki metinler konsola yapıştırılmak üzere yazıldı. Hepsi uygulamanın gerçekte yaptığı
şeyi anlatıyor; bir inceleme uzmanı APK'yı açıp baktığında söylenenle göreceği şey aynı
olmalı, yoksa ilk ret ikincisini de getirir.

---

## 1. Erişilebilirlik API beyanı

> **Bu en riskli maddedir.** Google erişilebilirlik API'sinin engelli kullanıcılara yardım
> amacıyla kullanılmasını bekler; bizim kullanımımız bir arayüz tercihidir. Savunma,
> servisin kapsamının gerçekten dar olmasına dayanıyor: pencere içeriği okumuyor, yalnızca
> ses tuşu olaylarını filtreliyor, kullanıcı açıkça açmadıkça hiç çalışmıyor.

**Hangi erişilebilirlik özelliklerini kullanıyorsunuz?**

> Yalnızca donanım tuşu filtreleme (`FLAG_REQUEST_FILTER_KEY_EVENTS`). Servis
> `canRetrieveWindowContent="false"` ile tanımlıdır; ekran içeriğini okuma, pencereleri
> inceleme, tıklama veya metin girme yetkisi istemez ve kullanmaz.

**Bu, uygulamanın hangi işlevi için gerekli?**

> Z Launcher, Windows Phone arayüzünü yeniden üreten bir başlatıcıdır. Ses seviyesi
> göstergesi de bu arayüzün parçasıdır. Android'de donanım tuşları yalnızca ön plandaki
> uygulamaya ulaştığı için, başlatıcı arka plandayken ses çubuğunu gösterebilmenin tek yolu
> tuş olaylarını erişilebilirlik servisiyle filtrelemektir. Servis yalnızca ses artırma ve
> azaltma tuşlarını işler; diğer tüm olaylar sisteme olduğu gibi geçer.

**Kullanıcıya nasıl açıklanıyor?**

> Ayarlar → Ses bölümünde, kullanıcı erişilebilirlik ayarlarına yönlendirilmeden önce şu
> metin görünür: "Servis yalnızca ses tuşlarını dinler: ekranındaki hiçbir şeyi okumaz,
> hiçbir kayıt tutmaz, hiçbir veri dışarı göndermez. İstediğin an kapatabilirsin." Aynı
> açıklama Android'in erişilebilirlik ekranında servisin altında da yazar.

**Toplanan veri:** Hiçbiri. Servis hiçbir şey kaydetmez, saklamaz ve göndermez.

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
| `SCHEDULE_EXACT_ALARM` | Saat hub'ındaki alarmlar ve zamanlayıcı — dakikası dakikasına çalması gerekir. |
| `REQUEST_INSTALL_PACKAGES` | Dosyalar hub'ı bir dosya yöneticisidir ve .apk dosyalarını sistem yükleyicisine verir; İnternet hub'ı da kendi indirdiği .apk'yı açar. Bu izin olmadan yükleyici hiç açılmaz. Kullanıcı yine de ayarlardan "bilinmeyen uygulama" izni verir ve her yüklemeyi kendisi onaylar; uygulama kendi başına hiçbir şey yüklemez. Play bu izni "dosya yöneticisi / tarayıcı" gerekçesiyle kabul eder, beyanda bu gerekçe seçilmeli. |

---

## 5. Veri güvenliği (Data safety) formu

**Toplanan veri:** Yok. Uygulamanın sunucusu yoktur; hiçbir veri geliştiriciye ulaşmaz.

**Paylaşılan veri:** Yok — aşağıdakiler kullanıcının kendi isteğiyle üçüncü taraf servislere
gider, geliştiriciye değil:

| Nereye | Ne | Ne zaman |
|---|---|---|
| open-meteo.com | Yaklaşık konum (enlem/boylam) veya şehir adı | Hava durumu hub'ı / kutucuğu kullanıldığında |
| lrclib.net, musicbrainz.org, coverartarchive.org, itunes.apple.com | Çalan parçanın adı, sanatçı, albüm | Ayarlardan "internetten sözler ve kapaklar" açıksa |
| Seçilen arama motoru | Tarayıcıya yazılan metin | Arama önerileri açıkken yazarken |
| Kullanıcının kendi IMAP/SMTP sunucusu | E-posta içeriği ve kimlik bilgileri | E-posta hesabı eklenmişse |
| googleapis.com (Drive) | Dosya listesi ve aktarılan dosyalar | Drive hesabı bağlanmışsa |

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
