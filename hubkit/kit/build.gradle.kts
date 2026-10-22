// ZetaHubKit: what a developer adds to an app to give Zeta a hub (PLATFORM.md). Apache-2.0.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.serkantkn.zeta.hubkit"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":hubkit-protocol"))
    testImplementation(libs.junit)
}
