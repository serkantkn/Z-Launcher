// The words Zeta and a hub app speak: the binder interfaces, the node tree and the names in it.
// Shared by Zeta and by ZetaHubKit, so both sides always mean the same thing. Apache-2.0.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.serkantkn.zeta.hubkit.protocol"
    // Built against an older platform than Zeta on purpose: a library's compileSdk becomes the
    // least its users must compile against, and ZetaHubKit needs nothing newer than Android 9.
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        aarMetadata {
            minCompileSdk = 28
        }
    }

    buildFeatures {
        aidl = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Readable by projects still on Kotlin 2.0: a library should not make its users upgrade.
kotlin {
    coreLibrariesVersion = "2.0.21"
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    testImplementation(libs.junit)
    // HubNode carries an android.os.Bundle; the rules are tried on real ones.
    testImplementation("org.robolectric:robolectric:4.14.1")
}

mavenPublishing {
    publishToMavenCentral()
    // Signed only where a key is given (~/.gradle/gradle.properties, never this repository):
    // publishing to the local repository needs none.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    coordinates(artifactId = "zetahubkit-protocol")
    configure(
        com.vanniktech.maven.publish.AndroidSingleVariantLibrary(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
            sourcesJar = com.vanniktech.maven.publish.SourcesJar.Sources(),
            variant = "release"
        )
    )
    pom {
        name.set("ZetaHubKit protocol")
        description.set("What Zeta and an app that gives it a hub say to each other: the binder interfaces, the node tree and the rules.")
    }
}
