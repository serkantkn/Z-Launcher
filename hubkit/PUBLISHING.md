# ZetaHubKit'i Maven Central'a yayınlamak

Bu dosya senin için: ilk yayın bir kez yapılacak hazırlık ister, sonrakiler tek komut. Hesap senin adına
olduğu için hesabı sen açarsın; imza anahtarını istersen ben hazırlarım.

Koordinatlar `gradle.properties`'te: grup `io.github.serkantkn`, sürüm `VERSION_NAME` (şu an `0.1.0`).
Yayın ayarı her iki modülün `build.gradle.kts`'inde (`mavenPublishing { }`, eklenti
`com.vanniktech.maven.publish`).

## Bir kez

1. **Hesap.** <https://central.sonatype.com> → "Sign in" → **GitHub ile giriş**. `io.github.serkantkn` adı
   GitHub hesabınla kendiliğinden doğrulanır (Namespaces sayfasında "Verified" görünmeli).
2. **Erişim anahtarı.** Hesap › "Generate User Token". Çıkan kullanıcı adı ve şifreyi **depoya değil**
   `~/.gradle/gradle.properties` dosyasına yaz:

   ```properties
   mavenCentralUsername=...
   mavenCentralPassword=...
   ```

3. **İmza anahtarı.** Maven Central her dosyanın imzalı olmasını ister.
   - `brew install gnupg`
   - `gpg --full-generate-key`: RSA 4096, süresiz; ad ve e-posta senin; bir parola.
   - `gpg --keyserver keys.openpgp.org --send-keys <ANAHTAR_KİMLİĞİ>`: açık anahtar herkese açık bir
     sunucuya gider, Central imzayı onunla doğrular.
   - Gizli anahtarı yine `~/.gradle/gradle.properties`'e ekle:

   ```properties
   signingInMemoryKey=<aşağıdaki komutun tek satırlık çıktısı>
   signingInMemoryKeyPassword=<parola>
   ```

   Tek satırlık hali (başlık satırları ve satır sonları olmadan):

   ```bash
   gpg --export-secret-keys --armor ANAHTAR_KİMLİĞİ | grep -v '\-\-' | grep -v '^=.' | tr -d '\n'
   ```

   Anahtarı ve parolayı kaybetme; yedeğini al. Yeni bir anahtarla da yayınlanabilir ama eski sürümleri
   doğrulayan anahtar o olur.

## Her sürümde

1. `gradle.properties`'te `VERSION_NAME`'i artır (0.1.0 → 0.1.1 ya da 0.2.0).
2. Kökten:

   ```bash
   ./gradlew :hubkit-protocol:publishAndReleaseToMavenCentral :hubkit:publishAndReleaseToMavenCentral
   ```

3. <https://central.sonatype.com/publishing> → yayın "Published" olunca (genelde 10-30 dakika) Maven
   Central'da görünür. Sitedeki ve `hubkit/README.md`'deki sürüm numarasını güncelle.

Yayınlanan bir sürüm geri alınamaz ve değiştirilemez; bir hata çıkarsa yeni bir sürüm yayınlanır.
