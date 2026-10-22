// The words Zeta and a hub app speak: the binder interfaces, the node tree and the names in it.
// Shared by Zeta and by ZetaHubKit, so both sides always mean the same thing. Apache-2.0.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.serkantkn.zeta.hubkit.protocol"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    testImplementation(libs.junit)
}
