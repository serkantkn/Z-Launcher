# Zeta hub platformu — tasarım belgesi

2026-10-22. Kararlar: kütüphanenin adı **ZetaHubKit**; dış hub'lar ücretsiz sürümde de çalışır;
TV ilk sürümde yok (bölüm 7.3).

## 1. Amaç

Zeta bir işletim sistemi gibi davransın: başka geliştiriciler Zeta için **hub** yazabilsin,
kullanıcı bu hub'ları Zeta'nın kendi hub'larının yanında, onlardan ayırt edemeden kullansın.
Geliştiriciler yeni şeylere ihtiyaç duydukça Zeta'nın altyapısı büyüsün.

Seçilen model (sohbette "A"): **hub'ı geliştiricinin uygulaması tarif eder, Zeta çizer.**
Geliştirici bir kütüphane ekler, o kütüphanenin öğeleriyle hub'ının sayfalarını, listelerini,
düğmelerini ve canlı karosunu tarif eder. Zeta bu tarifi alır ve kendi bileşenleriyle telefonda,
tablette, Aura'da ve televizyonda çizer. Dokunuşlar geliştiricinin uygulamasına geri gider.

### İlkeler

1. **Her hub Zeta gibi görünür.** Renk, yazı, hareket, uçan başlık, tablet bölmesi, TV kumandası
   Zeta'nındır; geliştirici bunları yeniden yapmaz, bozamaz da.
2. **Kod Zeta'ya girmez.** Hub'ın kodu kendi uygulamasında, kendi sürecinde, kendi izinleriyle
   çalışır. Zeta yalnızca tarif alır, olay gönderir. Bu hem güvenlik hem Play kuralları için
   şart: Play'deki bir uygulama dışarıdan çalıştırılabilir kod indiremez (Device and Network
   Abuse), ama başka bir uygulamayla konuşabilir.
3. **Kütüphane küçük başlar, ihtiyaçla büyür.** v1 bugünkü hub'ların çoğunu yapabilecek kadar
   öğe içerir; yeni öğeler sürüm numarasıyla eklenir, eski hub'lar çalışmaya devam eder.
4. **Bir kez yaz, her yerde çalışsın.** Geliştirici telefon, tablet ve TV için ayrı ekran
   yazmaz; düzeni Zeta seçer (bugünkü `ZuneHub` standardının aynısı).
5. **Kullanıcının izni olmadan hiçbir hub eklenmez**, ve hub'lar Zeta'nın verisine (kişiler,
   mesajlar, aramalar) Zeta üzerinden erişemez.

### Kapsam dışı (v1)

- Hub'ın ekranı kendisinin çizmesi (model B: Zeta'nın bileşenlerini bir arayüz kütüphanesi olarak
  vermek). İleride oyunlar ve özel ekranlar için ikinci katman olarak eklenebilir (bölüm 15).
- Başka bir uygulamanın ekranını Zeta'nın içine gömmek (`SurfaceControlViewHost`).
- Hub'ların Zeta'nın kişilerine, mesajlarına, müziğine erişmesi.
- Eklenti mağazası (katalog); ayrı bir aşama (bölüm 13).

## 2. Terimler

| Terim | Anlamı |
|---|---|
| **Hub uygulaması** | Bir ya da daha çok hub sunan, geliştiricinin yazdığı Android uygulaması. |
| **ZetaHubKit** | Geliştiricinin projesine eklediği kütüphane. Kodda kısaca "HubKit": iki modül, `hubkit-protocol` ve `hubkit`. |
| **Protokol** | Zeta ile hub uygulaması arasındaki konuşmanın kuralları ve veri biçimi; tek bir tamsayı sürümü var. |
| **Tarif** | Hub'ın o anki görünümünün veri olarak anlatımı: sayfalar, öğeler, alt çubuk, canlı karo. |
| **Ev sahibi (host)** | Zeta'nın hub uygulamalarını bulan, onlara bağlanan ve tarifleri çizen kısmı. |
| **Yerleşik hub** | Zeta'nın kendi hub'ları (telefon, müzik, …), bugünkü `HubType`. |
| **Dış hub** | Bir hub uygulamasından gelen hub. |

## 3. Genel yapı

```
 Geliştiricinin uygulaması (kendi süreci)          Zeta (kendi süreci)
┌──────────────────────────────────────┐        ┌───────────────────────────────────────────┐
│ class KargoHub : ZetaHubService()    │        │ HubDirectory: kurulu hub'ları bulur,       │
│   hub() = zetaHub { page { ... } }   │        │   kullanıcı onayını tutar                  │
│                                      │ Binder │ HubConnection: bağlanır, tarifi alır,      │
│ hubkit: tarif DSL'i, durum, olaylar  │◄──────►│   olayları gönderir, çökmeyi yakalar       │
│ hubkit-protocol: Parcelable'lar,AIDL │        │ ExternalHubScreen: tarifi ZuneHub ve       │
│                                      │        │   Zeta bileşenleriyle çizer                │
│ (isteğe bağlı) ZetaTiles.update(...) │──────► │ HubTileProvider: canlı karo verisi         │
└──────────────────────────────────────┘        └───────────────────────────────────────────┘
```

- `hubkit-protocol` Zeta'nın deposunda ayrı bir Gradle modülü olur; Zeta da aynı modülü kullanır,
  böylece iki taraf aynı veri tiplerini konuşur.
- `hubkit`, geliştiricinin yazdığı kısmı kolaylaştıran Kotlin DSL'i ve servis tabanıdır.
- İkisi Apache-2.0 lisansla yayınlanır (bölüm 12); Zeta'nın kendisi bugünkü kaynak görüntüleme
  lisansında kalır.

## 4. Geliştiricinin gördüğü

### 4.1 Projeye ekleme

```kotlin
dependencies {
    implementation("com.serkantkn.zeta:hubkit:1.0.0")
}
```

### 4.2 Manifest

```xml
<service
    android:name=".KargoHub"
    android:exported="true"
    android:label="@string/hub_title"
    android:icon="@drawable/ic_hub">
    <intent-filter>
        <action android:name="com.serkantkn.zeta.action.HUB" />
    </intent-filter>
    <meta-data android:name="com.serkantkn.zeta.hub.protocol" android:value="1" />
    <meta-data android:name="com.serkantkn.zeta.hub.television" android:value="false" />
</service>
```

- `android:label` hub'ın küçük harfli adı olur (Aura listesindeki kelime, büyük başlık).
- `android:icon` tek renkli bir simge olmalı (karolar ve tablet başlangıcı için); kütüphane
  bunu derleme sırasında denetler.
- `television` true ise hub ileride TV'de de sunulacak (ilk sürümde TV yok); bu durumda bütün öğeleri kumandayla kullanılabilir
  olmalı (kütüphanenin öğeleri zaten öyle).

### 4.3 Hub'ı yazmak

```kotlin
class KargoHub : ZetaHubService() {

    private var kargolar by mutableHubStateOf(emptyList<Kargo>())   // değişince hub yeniden tarif edilir

    override fun hub(): HubSpec = zetaHub {
        page("yolda") {
            val yolda = kargolar.filter { !it.teslim }
            if (yolda.isEmpty()) empty("henüz kargo yok", actionLabel = "ekle") { yeniKargo() }
            list(yolda, key = { it.no }) { k ->
                item(k.ad, subtitle = k.durum, meta = k.tarih, opens = { kargoSayfasi(k.no) })
            }
        }
        page("teslim edildi") { ... }

        bar {
            action("ekle", HubIcon.ADD) { yeniKargo() }
            action("yenile", HubIcon.REFRESH) { yenile() }
            menu("ayarlar") { ayarlar() }
        }

        liveTile {
            count = kargolar.count { !it.teslim }
            line = kargolar.firstOrNull()?.let { "${it.ad}: ${it.durum}" }
        }
    }

    private fun kargoSayfasi(no: String) = subPage(title = bul(no).ad, key = no) {
        section("hareketler")
        for (h in bul(no).hareketler) item(h.yer, subtitle = h.saat)
        button("takip numarasını kopyala") { kopyala(no) }
    }
}
```

- `hub()` bir **tarif** döndürür; `mutableHubStateOf` ile tutulan bir değer değişince, her olaydan
  sonra ya da `invalidate()` çağrılınca kütüphane tarifi yeniden kurup gönderir (Compose'un yeniden
  oluşturmasına benzer, ama ekran çizmez).
- Gerçek, çalışan bir örnek: `hubkit/sample` (yapılacaklar listesi).
- Tıklama, uzun basma, metin girişi gibi olaylar geliştiricinin verdiği lambdalara gelir.
  Kütüphane bunları kimliklerle eşler; geliştirici kimlikle uğraşmaz.
- **Dil**: Zeta bağlıyken servisin kaynakları Zeta'nın dilindedir; `getString` hub'ın metinlerini
  Zeta'nın dilinde verir (telefonun dili başka olsa da). Tarih ve sayılar için `hubLocale`.
- Hub uygulamasının kendi Activity'si olması gerekmez; isterse bir ayar ekranı ya da giriş ekranı
  için `openActivity(intent)` ile kendi ekranını açabilir (Zeta bunu ayrı uygulama gibi açar).

### 4.4 Denemek

- Zeta'nın ayarlarında **geliştirici modu**: kurulu ama onaylanmamış hub'ları onaysız gösterir,
  protokol günlüğünü (gelen tarifler, giden olaylar, süreler, hatalar) bir sayfada tutar,
  hub'ı yeniden bağlar.
- Kütüphane, tarifin kurallara uyduğunu (zorunlu alanlar, sınırlar, öğe sürümleri) birim testte
  denetleyebilen bir `HubSpecValidator` içerir.
- v1'de Zeta olmadan önizleme yok; geliştirici Zeta'yı (ücretsiz sürüm) cihazına kurar.

## 5. Bulma, onay ve yaşam döngüsü

### 5.1 Bulma

- Zeta, `com.serkantkn.zeta.action.HUB` eylemini karşılayan servisleri
  `PackageManager.queryIntentServices` ile bulur. `QUERY_ALL_PACKAGES` zaten var; yine de
  manifeste bu eylem için bir `<queries>` girdisi eklenir (izin kaldırılırsa da çalışsın).
- Paket kurulunca, güncellenince, kaldırılınca liste yenilenir (`IconPackRepository.watchPackages`
  gibi bir paket alıcısıyla).
- Protokol sürümü Zeta'nın desteklediğinden yüksekse hub "Zeta'yı güncelle" notuyla gösterilir,
  açılmaz.

### 5.2 Onay

- Yeni bulunan hub kendiliğinden listeye girmez. Ayarlar › hub'lar sayfasında ve "hub ekle"
  penceresinde (bugünkü `AddHubDialog`) **"<uygulama>, <hub adı> hub'ını eklemek istiyor"**
  olarak görünür; kullanıcı eklerse listeye ve başlangıca girer.
- Onay paket adı **ve imza sertifikasının özeti** ile tutulur. Aynı adla başka imzalı bir paket
  gelirse onay geçersiz olur.
- Kullanıcı bir dış hub'ı istediği an kaldırabilir; kaldırılan hub'ın uygulaması silinmez.

### 5.3 Bağlanma

- Zeta, hub açılırken servise `bindService` ile bağlanır (`BIND_AUTO_CREATE`), ilk tarifi ister.
- Hub kapanıp arka plana gönderildiğinde (bugünkü `sendToBackground`) bağlantı bir süre açık
  kalır, sonra kesilir; son tarif önbellekte tutulur, hub tekrar açılınca anında çizilir ve
  bağlantı arkadan kurulur.
- Hub uygulaması çökerse ya da 5 saniyede yanıt vermezse Zeta hub'ın yerine "<hub> yanıt
  vermiyor · yeniden dene" sayfası gösterir; Zeta'nın kendisi etkilenmez.

## 6. Konuşma (protokol v1)

### 6.1 Taşıyıcı

- Bağlı servis + **AIDL**. Bütün çağrılar `oneway` (iki taraf da birbirini bekletmez, ANR olmaz).
- Hub servisinin döndürdüğü arayüz `IZetaHub`, Zeta'nın verdiği geri çağrı arayüzü `IZetaHost`.

```aidl
oneway interface IZetaHub {
    void hello(in HostInfo host, IZetaHost callback);   // protokol, yetenekler, dil, tema, form
    void event(in HubEvent event);                     // tıklama, metin, sayfa değişimi, görünürlük
    void requestRange(String listId, int from, int to);// uzun listelerde parça isteği
    void bye();
}

oneway interface IZetaHost {
    void spec(in HubSpecParcel spec);                  // tam tarif
    void patch(in HubPatchParcel patch);               // değişen düğümler (anahtarla)
    void range(String listId, int from, in List<HubNode> items, int total);
    void command(in HostCommand command);              // alt sayfa aç/kapat, bildirim, iletişim kutusu
}
```

### 6.2 El sıkışma

`HostInfo` Zeta'nın ne olduğunu söyler; hub bunu bilmek zorunda değildir, kütüphane kullanır:

| Alan | Örnek |
|---|---|
| `protocol` | 1 |
| `components` | Zeta'nın çizebildiği öğeler ve sürümleri: `{list:1, tileGrid:1, map:0, …}` |
| `formFactor` | `PHONE`, `TABLET`, `TELEVISION` |
| `layout` | `AURA`, `MOSAIC`, `PANORAMA`, `CONSOLE`, `LOUNGE` (yalnız bilgi; çizim Zeta'nın) |
| `locale`, `dark`, `accent` | dil, koyu tema, vurgu rengi (hub kendi resimlerini buna göre seçebilir) |

### 6.3 Tarif biçimi

- Tarif bir düğüm ağacıdır (`HubNode`): `type`, `key`, sürüm, alanlar, çocuklar. Parcel olarak
  taşınır; kütüphane ağacı DSL'den üretir.
- **Anahtarlar**: her düğümün kararlı bir anahtarı vardır. Güncellemeler (`patch`) yalnızca
  değişen düğümleri gönderir; Zeta listeyi kaydırma konumunu ve animasyonları koruyarak günceller.
- **Boyut sınırı**: Binder işlemleri ~1 MB ile sınırlı. Bir mesaj 256 KB'ı geçemez; 200 öğeden
  uzun listeler `list(total = n)` ile parça parça istenir (`requestRange`), Zeta görünen aralığı
  ister.
- **Resimler** tarifin içine konmaz. Hub uygulaması bir `content://` URI verir (kütüphanenin
  FileProvider'ı; okuma izni Zeta'ya verilir) ya da `https://` adresi verir (Zeta indirir,
  Coil önbelleğine alır). Simgeler için `ZetaIcon` adlarıyla Zeta'nın kendi simge seti
  kullanılabilir.
- **Metin** düz metindir; biçim yalnızca öğenin türünden gelir (başlık, gövde, ek bilgi).
  Hub yazı tipi, boyut, renk veremez — ilkelerin 1. maddesi.

### 6.4 Olaylar

`HubEvent`: `CLICK(key)`, `LONG_CLICK(key)`, `TEXT(key, value)` (yazarken 250 ms gecikmeyle),
`SUBMIT(key)`, `TOGGLE(key, checked)`, `PAGE(index)`, `VISIBLE(bool)`, `BACK`,
`MENU(key, itemKey)`. Kütüphane bunları geliştiricinin lambdalarına çevirir.

### 6.5 Komutlar (hub → Zeta)

`OPEN_SUBPAGE(spec)`, `CLOSE_SUBPAGE`, `CLOSE_HUB`, `TOAST(text)`,
`DIALOG(title, text, buttons)` (Zeta'nın çevirmeli iletişim kutusu), `OPEN_ACTIVITY(intent)`
(hub uygulamasının kendi ekranı), `SHARE(text|uri)`, `OPEN_URL(url)` (Zeta'nın internet hub'ında),
`SCROLL_TO(key)`.

## 7. Öğeler (bileşen kataloğu v1)

Her öğe, Zeta'da bugün var olan bir bileşene karşılık gelir; böylece ilk sürüm büyük ölçüde
mevcut parçaların bir araya getirilmesidir. "Sürüm" sütunu protokoldeki öğe sürümüdür.

### 7.1 Hub düzeyi

| Öğe | Anlamı | Zeta'daki karşılığı |
|---|---|---|
| `hub(title)` | Hub'ın adı | `ZuneHub(title, zuneTitleKey)`; Aura'da büyük başlık |
| `page(heading)` | Bir pivot sayfası / tablette soldaki başlık | `ZuneHub(tabs, page)` |
| `bar { action; menu }` | Alt çubuk: en çok 4 düğme, sınırsız menü | `ZuneHubBar(WpBarAction, WpBarMenuItem)` |
| `liveTile { … }` | Karo ve Aura satırı (bölüm 8) | `W10MHubTile`, `HubLiveLine` |
| `takeover { … }` | Sayfaların yerine tek bir ekran (ör. giriş yapın) | `ZuneHub(takeover)` |
| `subPage(title) { … }` | Hub'ın üstünde açılan sayfa; tablette bölme | `pageHingeIn` / `ZuneHubPane` |
| `aboveContent { … }` | Sayfaların üstünde sabit satır (ör. arama kutusu) | `phoneAboveContent` |

### 7.2 Sayfa içi

| Öğe | Alanlar | Zeta'daki karşılığı |
|---|---|---|
| `section(title, action?)` | Bölüm başlığı | `MetroRule` |
| `item(title, subtitle?, meta?, image?, count?, opens?)` | Liste satırı; resim kare ya da yuvarlak | ortak bir `ZetaListRow` (bugün her hub'da ayrı; birleştirilecek) |
| `list(items, total?, letters?)` | Uzun, tembel yüklenen liste; `letters` harf atlama getirir | `LazyColumn` + `ZuneAlphabetIndex` / `ZuneAlphabetJump` |
| `tiles(columns) { tile(title, image?, color?, span) }` | Karo ızgarası (albüm, kişi, kategori) | `LazyVerticalGrid` + karo yüzü |
| `letterTiles` | Harf karoları | `PeopleLetterTile` (ortaklaştırılacak) |
| `text(text, style)` | `title`, `body`, `caption` | Zeta tipografisi |
| `bigNumber(value, unit?)` | Büyük sayı | `MetroBigNumber` |
| `image(uri, aspect)` | Tek resim | Coil |
| `button(label)` | Düz düğme | `MetroButton` (ortaklaştırılacak) |
| `switchRow(title, subtitle?, checked)` | Anahtar satırı | `MetroSwitchRow` |
| `valueRow(title, value)` | Değer satırı | `MetroValueRow` |
| `choiceRow(title, subtitle?, selected)` | Seçim satırı | `SettingChoiceRow` |
| `chips(options, selected)` | Seçim çipleri | `MetroChip` |
| `slider(value, range)` | Kaydırıcı | `WpSlider` |
| `textField(value, placeholder)` | Metin kutusu | `MetroTextField` |
| `searchBox(query, placeholder)` | Arama kutusu | `ZuneSearchBar` |
| `progress(value?)` | İlerleme çizgisi / belirsiz | `MetroProgressLine` |
| `empty(message, action?)` | Boş sayfa | `MetroEmpty` |
| `notice(message, action?)` | Bilgi satırı | `MetroNotice` |
| `loading()` | Yükleniyor | Zeta'nın nokta animasyonu |

### 7.3 Davranışlar Zeta'nındır

- **Aura**: hub'ın adı listede, dokununca uçup büyük başlık olur, kapanınca geri iner
  (`ZuneHubFlight`, anahtar `ext:<paket>/<servis>`). Bir `item` alt sayfa açıyorsa (`opens`)
  satırın başlığı otomatik olarak `zuneFlightLabel` olur, alt sayfa başlığı uçuşla gelir.
- **Mozaik**: hub karosu, başlangıçta sabitlenir, boyutu ve görünümü (`TileLook`) yerleşik
  hub'larınki gibi değiştirilir.
- **Tablet**: başlıklar solda, `subPage` sağdaki bölmede açılır.
- **TV**: ilk sürümde dış hub'lar TV'de sunulmaz (karar 2026-10-22). Protokol buna hazır kalır:
  ileride `television=true` diyen hub'lar Konsol ve Salon'da açılır; odak, kumanda, alt çubuğun
  komut şeridine dönüşmesi Zeta'nın olur. O zamana kadar Zeta bu bilgiyi okur ama kullanmaz.
- **Arka planda çalışma, görev değiştirici, bölünmüş ekran**: dış hub'lar da yerleşikler gibi
  arka plana gider; önizleme görüntüsü Zeta'nın kendi çizdiğinden alınır.

### 7.4 Öğe ekleme süreci ("altyapıyı ihtiyaca göre büyütmek")

1. Geliştirici GitHub'da "yeni öğe" şablonuyla istek açar: ne göstermek istiyor, hangi hub'da.
2. Öğe tasarlanır: alanları, telefon/tablet/Aura/TV'de nasıl çizileceği, kumandayla kullanımı.
3. `hubkit-protocol`'e öğe eklenir (öğe sürümü 1), Zeta çizer, kütüphanede DSL fonksiyonu çıkar.
4. Zeta'nın bir sonraki sürümü `HostInfo.components` içinde yeni öğeyi duyurur.
5. Yeni öğeyi kullanan hub, eski Zeta'da çalışabilsin diye bir **yedek** verir:
   `map(...) orElse { item("haritada aç") }`. Kütüphane, Zeta öğeyi bilmiyorsa yedeği gönderir.

Aday ikinci dalga öğeler: harita, grafik, takvim ızgarası, medya oynatıcı kontrolleri, video,
konuşma balonları (mesajlaşma türü hub'lar için), sürükle-sırala listesi.

## 8. Canlı karo ve Aura satırı

Hub yalnızca veriyi verir; karoyu Zeta tasarımına göre çizer.

```kotlin
liveTile {
    count = 3                                  // köşedeki sayı; Aura'da adın yanındaki sayı
    line = "2 kargo bugün geliyor"             // Aura'daki canlı satır; karonun arka yüzü
    title = "kargolarım"                       // geniş karoda başlık
    items = listOf(TileLine("Kitap", "dağıtımda", "14:20"))   // geniş/büyük karoda satırlar
    photos = listOf(uri1, uri2)                // varsa karo resim döndürür
}
```

- Bu, bugünkü `W10MHubTile(badgeCount, liveTitle, liveSubtitle, liveItems, photoUris)` ve
  `HubLiveLine(text, count)` ile birebir örtüşür; yeni bir karo çizimi gerekmez.
- **Hub açık değilken güncelleme**: hub uygulaması kendi zamanlamasıyla (WorkManager, push)
  `ZetaTiles.update(context, MyHub::class.java) { count = 3; line = "…" }` ya da servisin içinden
  `publishTile()` (tarifteki `liveTile`'ı gönderir) çağırır. Kütüphane bunu Zeta'nın dışa açık
  `ContentProvider`'ına (`content://com.serkantkn.zeta.hubs/tile`) yazar. Zeta çağıranı
  `Binder.getCallingUid()` ile tanır; yalnızca onaylı hub'ların yazmasını kabul eder, sıklığı
  sınırlar (dakikada en çok 4).
- TV geldiğinde aynı veri Konsol karosunda ve Salon'daki satırda gösterilecek (`TvPlace.line`, `image`).

## 9. Zeta'da değişmesi gerekenler

Bugün bir hub kapalı bir enum (`HubType`, 42 dosyada 291 kullanım) ve her yüzey kendi
`when (hub)` dalını taşıyor. İlk ve en büyük iş bunu açmak.

### 9.1 `HubId`

```kotlin
sealed interface HubId {
    val key: String                           // kalıcı kimlik: "PHONE", "ext:com.ornek.kargo/.KargoHub"
    data class Builtin(val type: HubType) : HubId
    data class External(val component: ComponentName) : HubId
}
```

- Kalıcı biçimler bugünkülerle uyumlu kalır: hub sırası ve kaldırılan hub'lar JSON'da isim
  olarak tutuluyor, karo kimlikleri `hub:NAME`. Dış hub'lar `hub:ext:<paket>/<servis>` olur.
  `readHubOrder` ve gezinme durumunun `Saver`'ı bilinmeyen adları zaten atlıyor; kaldırılmış bir
  hub uygulaması bu yüzden hiçbir şeyi bozmaz.

### 9.2 Dokunulacak yerler (kod haritasından)

| Yer | Bugün | Değişiklik |
|---|---|---|
| `SettingsDataStore.readHubOrder`, `removedHubs`, `readStartTiles` | `HubType.name` listeleri | `HubId.key`; onaylı dış hub'lar listeye eklenir |
| `StartTileItem.hubType`, `fromHub` | `HubType.valueOf` | `HubId` çözümü |
| `ZuneNavigationState` (`hubStack`, `backgroundHubs`, `Saver`) | `List<HubType>` | `List<HubId>` |
| `LauncherScreen.hubContentFor`, `RenderHubScreen` | her hub için `when` | `Builtin` → bugünkü ekran, `External` → `ExternalHubScreen` |
| `HomeHubScreen.liveLineFor`, `StartTile`, `getHubIcon` | `when (hubType)` | dış hub için `HubTileProvider` verisi ve uygulamanın simgesi |
| `HubSwitcher`, `HubPreviewStore` | `HubType` anahtarlı | `HubId` anahtarlı |
| Aura uçuş anahtarları (`flightKey`, `UNTITLED_HUBS`) | `hubType.name` | `HubId.key` |
| `AddHubDialog`, ayarlar › hub'lar (`SettingsViewModel.hubOrder`) | `HubType.entries` | yerleşik + onaylı/onay bekleyen dış hub'lar |
| `HubTargetPicker`, `hubTargetApps` | `Map<HubType, String>` | yalnız yerleşik hub'lar (dış hub zaten bir uygulama) |
| TV başlangıçları (`TvStartScreen.tvPlaces`, `XboxStart`, `VistaStart`) | sabit liste | ilk sürümde değişmez; TV gelince `television=true` dış hub'lar |
| `MainActivity.EXTRA_OPEN_HUB`, `HubBridge` | `HubType` adı | `HubId.key` (bildirimden dış hub açmak için) |

`HubType`'ın kendisi kalır; yerleşik hub'lar onu kullanmaya devam eder. Değişiklik "hub her
zaman `HubType`'tır" varsayımını kaldırmaktır.

### 9.3 Yeni parçalar

- `data/hubs/HubDirectory.kt`: bulma, onay (paket + imza özeti), paket değişikliklerini izleme.
- `data/hubs/HubConnection.kt`: bağlanma, el sıkışma, tarif önbelleği, zaman aşımı, yeniden
  bağlanma, olay gönderme.
- `ui/screens/external/ExternalHubScreen.kt`: tarifi `ZuneHub` ile çizen ekran; alt sayfalar,
  bölme. `HubStandardTest` bu ekranı da denetler — o da standart dışına çıkamaz.
- `ui/screens/external/HubNodeRenderer.kt`: öğe türünden Zeta bileşenine eşleme (bölüm 7).
- `data/hubs/HubTileProvider.kt`: dışa açık `ContentProvider`, canlı karo verisi.
- Ayarlar › hub'lar: dış hub listesi, onay, kaldırma, geliştirici modu.
- Hub'lar arası ortak satır bileşeni `ZetaListRow` (bugün her hub'ın kendi satırı var; dış
  hub'lar için birleştirilmesi gerekiyor, yerleşik hub'lar zamanla ona geçebilir).

## 10. Sürümleme

- **Protokol sürümü** tek bir tamsayı (v1). Yalnızca geriye uyumsuz değişiklikte artar; bu
  olursa Zeta eski sürümü de bir süre konuşmaya devam eder.
- **Öğe sürümleri** ayrı: yeni öğe ya da bir öğeye yeni alan eklemek protokolü artırmaz;
  `HostInfo.components` ile duyurulur (bölüm 7.4).
- **Kütüphane** anlamsal sürümlenir (`1.x`). `hubkit` hangi Zeta sürümünün neyi desteklediğini
  bilir ve yedekleri kendisi seçer.
- Zeta bilmediği bir alanı yok sayar, bilmediği bir öğeyi yedeğiyle (yoksa hiç) çizer; asla çökmez.

## 11. Güvenlik ve gizlilik

- **Kod yalıtımı**: hub kodu kendi sürecinde. Zeta yalnızca tarif okur; tarifte çalıştırılabilir
  hiçbir şey yok (betik, HTML, ifade dili yok).
- **Kim kiminle konuşuyor**:
  - Hub servisi yalnızca Zeta'nın bağlanmasına izin verir: kütüphane `onBind` sırasında çağıranın
    imza sertifikasını Zeta'nın bilinen sertifikalarıyla (yayın ve geliştirme anahtarı)
    karşılaştırır.
  - Zeta yalnızca onaylı ve imzası değişmemiş hub'larla konuşur.
- **Veri akışı tek yönlü**: Zeta hub'a kullanıcının kişilerini, mesajlarını, konumunu, başka
  hub'ların verisini vermez. Hub neye ihtiyaç duyuyorsa Android izinlerini kendi uygulamasında
  ister. Zeta'dan hub'a giden tek şey olaylar ve `HostInfo`'dur.
- **Sınırlar**: mesaj boyutu (256 KB), güncelleme sıklığı (saniyede en çok 10 `patch`), canlı
  karo yazımı (dakikada 4), zaman aşımı (5 sn). Aşan hub geçici olarak durdurulur, geliştirici
  modunda nedeni gösterilir.
- **Bağlantılar**: `OPEN_URL` yalnızca `https`; `OPEN_ACTIVITY` yalnızca hub'ın kendi paketindeki
  ekranlar için.
- **Play kuralları**: Zeta hiçbir şey indirip kurmaz ve kod çalıştırmaz; hub uygulamaları Play'de
  kendi başına yayınlanan normal uygulamalardır. Zeta tarafında yeni izin gerekmez.

## 12. Lisans ve yayın

- `hubkit-protocol` ve `hubkit`: **Apache-2.0**. Geliştiriciler ticari ya da kapalı kaynak hub
  yazabilir.
- Yayın: Maven Central (`com.serkantkn.zeta:hubkit`, ürün adı ZetaHubKit), kaynak Zeta deposunda ayrı bir dizinde
  (`hubkit/`), kendi `LICENSE` dosyasıyla.
- Zeta'nın kendisi (çizici dahil) bugünkü kaynak görüntüleme lisansında kalır.
- Belgeler: sitede bir "geliştiriciler" sayfası (öğe kataloğu ekran görüntüleriyle, her düzende
  nasıl göründüğü), örnek hub'lar deposu.

## 13. Dağıtım ve keşif

- v1: hub uygulamaları Play'de ya da GitHub'da normal uygulama olarak yayınlanır; kullanıcı
  kurar, Zeta bulur, kullanıcı onaylar.
- Sonraki aşama: **hub kataloğu**. GitHub'da bir depoda `hubs.json`; geliştiriciler pull
  request ile ekler, onaylanır. Zeta'da bir "hub'lar" sayfası bunu Zeta tasarımıyla gösterir;
  "yükle" Play sürümünde hub'ın Play sayfasını açar, GitHub sürümünde APK'yı kurabilir.
  Sunucu gerekmez (GitHub Pages).

## 14. Yol haritası

| Aşama | İçerik | Süre (kaba) |
|---|---|---|
| 0 ✓ | `HubId` düzenlemesi; davranış değişmez, testler geçer | yapıldı (2026-10-22) |
| 1 ✓ | `hubkit-protocol`, `hubkit`, örnek hub, `HubDirectory`, onay, `HubConnection`, `ExternalHubScreen`; v1 öğeleri (sayfa, satır, liste, bölüm, metin, düğme, boş, bilgi, yükleniyor, anahtar, değer, alt çubuk, alt sayfa) | yapıldı (2026-10-22) |
| 2 ✓ | Tablet bölmesi, Aura uçuşu; canlı karo (açıkken tariften, kapalıyken `ZetaTiles` ile), Aura satırı, Mozaik karosu; onayda uygulamanın adı; ayarlar › hub › diğer uygulamaların hub'ları; hub'ın notları Zeta tasarımında; hub uygulamasının metinleri Zeta'nın dilinde; kopan bağlantı yeniden kurulur | yapıldı (2026-10-22) |
| 3 | `hubkit` DSL'i, doğrulayıcı, geliştirici modu, örnek hub (ör. "kargolarım"), belgeler | ~1 hafta |
| 4 | Kalan v1 öğeleri, sınırlar, güvenlik denetimi, Maven yayını | ~0,5 hafta |
| 5 | Hub kataloğu | ayrı karar |

İlk kullanılabilir sürüm (aşama 0–3) kabaca 4–5 hafta.

## 15. İleride: geliştiricinin kendi çizdiği hub'lar (model B)

Bazı uygulamalar (oyunlar, editörler, kamera benzeri) şablonlara sığmaz. İleride:

- Zeta'nın görsel dili ayrı bir arayüz kütüphanesi olarak verilir (`hubkit-ui`): hub iskeleti,
  pivotlar, karolar, alt çubuk, tipografi, renkler.
- Bu hub'lar kendi Activity'lerinde çalışır; Zeta onları listesinde gösterir, açılış geçişini
  yaklaşık taklit eder. Uçan başlık iki süreç arasında birebir yapılamaz.
- Bunun için Zeta'nın arayüz kodunun bir bölümünün açık lisansla verilmesi gerekir; ayrı karar.

## 16. Açık sorular

1. ~~Ücretsiz mi, Pro mu?~~ **Karar (2026-10-22): dış hub'lar ücretsiz sürümde de çalışır.**
   Mevcut Pro ayrıcalıkları (ör. karo başına görünüm) dış hub'ların karolarına da aynı kuralla
   uygulanır.
2. ~~Kütüphanenin adı~~ **Karar: ZetaHubKit** (kodda `hubkit`, sınıflar `HubKit…`).
3. ~~TV~~ **Karar: ilk sürümde yok**; telefon ve tabletle başlanır.
4. **Örnek hub'lar**: hangi hub'ı örnek olarak yapalım? (Adaylar: kargo takibi, döviz/borsa,
   yapılacaklar listesi, RSS.)
5. **Yerleşik hub'ların geleceği**: bazı yerleşik hub'lar (hesap makinesi, haberler) zamanla
   aynı kütüphaneyle yeniden yazılıp ayrı uygulama olarak mı dağıtılsın? Bu, platformun en iyi
   sınaması olur ama Zeta'yı küçültür; v1 için önerilmiyor.
6. **İzin isteyen hub'lar**: bir hub'ın konum ya da kamera gibi bir izne ihtiyacı varsa, Zeta'nın
   içinden "izin ver" dendiğinde hub uygulamasının izin ekranı açılır (`OPEN_ACTIVITY`). Bu
   geçiş yeterince akıcı mı, yoksa ileride Zeta tasarımında bir izin öğesi mi gerekir?
