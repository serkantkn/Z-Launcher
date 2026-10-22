// An example hub written with ZetaHubKit: a small to-do list. Apache-2.0.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.serkantkn.zeta.hubkit.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.serkantkn.zeta.hubkit.sample"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":hubkit"))
}
