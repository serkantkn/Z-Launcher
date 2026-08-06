# Walkthrough - Missing jlink Build Error Fixed

I have fixed the build issue where Gradle was unable to find the `jlink` executable.

## Changes Made

### 1. Fixed Java Development Kit (JDK) Configuration
Gradle was incorrectly using a minimal Java Runtime Environment (JRE) from an IDE extension instead of a full JDK. Full JDKs include tools like `jlink` which are required for building against Android 37.

- [MODIFY] **[gradle.properties](file:///Users/serkan/AndroidStudioProjects/ZuneLauncher/gradle.properties)**: Explicitly configured Gradle to use the Android Studio embedded JDK.
- [DELETE] **[gradle-daemon-jvm.properties](file:///Users/serkan/AndroidStudioProjects/ZuneLauncher/gradle/gradle-daemon-jvm.properties)**: Removed the file that was forcing Gradle to auto-discover the broken JRE.

### 2. Resolved Environment Variable Conflict
I discovered that having both `ANDROID_PREFS_ROOT` and `ANDROID_USER_HOME` environment variables set caused the Android Gradle Plugin (AGP) to fail while initializing its internal services.

## Verification Results

- Verified that stopping the Gradle Daemon and clearing toolchain metadata allows Gradle to pick up the correct JDK.
- Successfully executed the failing task: `./gradlew :app:compileFreeDebugJavaWithJavac`.

> [!IMPORTANT]
> To ensure your build continues to work from your terminal, I recommend unsetting the `ANDROID_PREFS_ROOT` environment variable in your shell profile (e.g., `.zshrc` or `.bash_profile`).
>
> Run the following command to check if they are set:
> ```bash
> printenv | grep ANDROID_
> ```
> If both are present, remove `export ANDROID_PREFS_ROOT=...` from your shell configuration files.
