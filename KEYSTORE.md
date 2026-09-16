# The upload key

Play signs what people download with a key Google holds. What it accepts from you is signed
with an **upload key**, and that one is yours: lose it and you cannot update the app, share it
and somebody else can upload as you. It never goes into the repository — `.gitignore` refuses
both the key and the file that holds its passwords.

## Making it, once

```bash
keytool -genkeypair -v \
  -keystore ~/zune-upload.jks \
  -storetype JKS \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias zune-upload
```

Answer the questions (name, organisation, country); they end up in the certificate and nobody
sees them. Keep the file and the password somewhere you will still have them in five years —
a password manager, not this machine alone.

## Telling Gradle about it

Create `keystore.properties` next to `settings.gradle.kts`:

```properties
storeFile=/Users/serkan/zune-upload.jks
storePassword=…
keyAlias=zune-upload
keyPassword=…
```

That is all. `./gradlew :app:bundleFreeRelease` then produces a signed `.aab` in
`app/build/outputs/bundle/freeRelease/`. Without the file the build still works and produces an
unsigned artefact, so a machine that has no business signing anything cannot.

## What goes to Play

- **Free**: `app/build/outputs/bundle/freeRelease/app-free-release.aab` (`…zunelauncher.free`)
- **Pro**: `app/build/outputs/bundle/premiumRelease/app-premium-release.aab` (`…zunelauncher`)

Two application ids means two listings. Keep the same upload key for both — one key, one
identity.
