import java.util.Properties

/**
 * Cloud sign-in configuration. The Microsoft client id is read from local.properties (which is not
 * in version control) so no credential ends up in the repository:
 *
 *     microsoft.clientId=00000000-0000-0000-0000-000000000000
 *
 * Google needs no id in the code: Play services matches this package name and signing certificate
 * against the OAuth client registered in Google Cloud Console.
 */
val cloudProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val microsoftClientId: String = (cloudProperties.getProperty("microsoft.clientId") ?: "").trim()
val cloudRedirectScheme = "zunelauncher"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.serkantkn.zunelauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.serkantkn.zunelauncher"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"$microsoftClientId\"")
        buildConfigField(
            "String",
            "MICROSOFT_REDIRECT_URI",
            "\"$cloudRedirectScheme://oauth/microsoft\""
        )
        manifestPlaceholders["cloudRedirectScheme"] = cloudRedirectScheme
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/LICENSE", "META-INF/DEPENDENCIES")
        }
    }

    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            applicationIdSuffix = ".free"
            versionNameSuffix = "-free"
            buildConfigField("boolean", "IS_PREMIUM", "false")
        }
        create("premium") {
            dimension = "tier"
            versionNameSuffix = "-pro"
            buildConfigField("boolean", "IS_PREMIUM", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)

    // Coil
    implementation(libs.coil.compose)

    // JavaMail (Email hub: IMAP + SMTP)
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    implementation(libs.play.services.auth)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.palette)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // DataStore & Serialization
    implementation(libs.androidx.datastore.preferences)
    
    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Testing
    testImplementation(libs.junit)
    // android.jar stubs org.json, so the real implementation is put on the unit test classpath
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}