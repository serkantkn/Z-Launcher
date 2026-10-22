// ZetaHubKit: what a developer adds to an app to give Zeta a hub (PLATFORM.md). Apache-2.0.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "com.serkantkn.zeta.hubkit"
    // Built against an older platform than Zeta on purpose: a library's compileSdk becomes the
    // least its users must compile against, and ZetaHubKit needs nothing newer than Android 9.
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        aarMetadata {
            minCompileSdk = 28
        }
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
    api(project(":hubkit-protocol"))
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
}

mavenPublishing {
    publishToMavenCentral()
    // Signed only where a key is given (~/.gradle/gradle.properties, never this repository):
    // publishing to the local repository needs none.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    coordinates(artifactId = "zetahubkit")
    configure(
        com.vanniktech.maven.publish.AndroidSingleVariantLibrary(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty(),
            sourcesJar = com.vanniktech.maven.publish.SourcesJar.Sources(),
            variant = "release"
        )
    )
    pom {
        name.set("ZetaHubKit")
        description.set("Write a hub for Zeta: describe its pages, lists, commands and live tile; Zeta draws it.")
    }
}
