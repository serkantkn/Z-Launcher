# Implementation Plan - Fix Missing jlink Build Error

The build is failing because the Android Gradle Plugin (AGP) is attempting to use a JRE from an IDE extension (`redhat.java`) which is either missing or does not contain the `jlink` tool required for building against Android SDK 37.

## Analysis
- The Gradle Daemon was previously started with the JRE path: `/Users/serkan/.antigravity-ide/extensions/redhat.java-1.55.0-darwin-arm64/jre/21.0.11-macosx-aarch64`.
- This JRE is likely a minimal runtime for the Language Server and lacks full JDK tools like `jlink`.
- Although `gradle.properties` attempted to override `org.gradle.java.home`, a `gradle/gradle-daemon-jvm.properties` file was forcing Gradle to look for any Java 21, and it prioritized the extension's JRE.
- Even after stopping daemons and removing the properties file, some cached transform metadata or toolchain discovery might still point to the invalid path.

## Proposed Changes

### 1. Project Configuration
- [x] **[DELETE] [gradle-daemon-jvm.properties](file:///Users/serkan/AndroidStudioProjects/ZuneLauncher/gradle/gradle-daemon-jvm.properties)**: (Already done) This file was causing Gradle to auto-discover the broken JRE.
- [MODIFY] **[gradle.properties](file:///Users/serkan/AndroidStudioProjects/ZuneLauncher/gradle.properties)**:
    - Ensure `org.gradle.java.home` and `org.gradle.java.installations.paths` are correctly escaped for paths containing spaces.
    - Explicitly set `android.jdk.home` to the same path to ensure AGP uses the correct JDK for its internal tasks.

### 2. Environment Cleanup
- Run `./gradlew --stop` again to ensure no "zombie" daemons are holding onto the old path.
- Clear Gradle toolchain metadata if necessary (by deleting `~/.gradle/toolchains/metadata.bin`).

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileFreeDebugJavaWithJavac` to verify the specific failing task now passes.
- Run `./gradlew --version` to confirm the Daemon is running with the Android Studio JBR.

### Manual Verification
- Check that the build completes successfully without the `jlink` error.
