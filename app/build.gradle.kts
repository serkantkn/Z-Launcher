import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.serkantkn.zeta"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.serkantkn.zeta"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "0.9.1_beta-3.1"
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
        // The app on Google Play and GitHub: Pro is bought inside it (ProStore). Its id is the
        // app's id for good - Play never lets it change.
        create("free") {
            dimension = "tier"
            buildConfigField("boolean", "IS_PREMIUM", "false")
        }
        // For development: always Pro, beside the real app rather than in its place.
        create("premium") {
            dimension = "tier"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "IS_PREMIUM", "true")
        }
    }

    // The upload key, read from keystore.properties — a file that is deliberately not in the
    // repository (see .gitignore). Without it the release still builds, unsigned, which is what
    // a machine that has no business signing anything should produce.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            // The launcher is the one app that is always resident, so its footprint is its
            // manners. R8 takes the 95 MB of dex this app links against — the extended icon set
            // alone is 38 MB, of which 150 icons are used — down to what is actually reached.
            isMinifyEnabled = true
            isShrinkResources = true
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

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf")
    )
    // Uncomment to see which composables skip and which classes are stable:
    // reportsDestination = layout.buildDirectory.dir("compose_compiler")
    // metricsDestination = layout.buildDirectory.dir("compose_compiler")
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
    // Rich text in notes: bold, colours, sizes, fonts, lists, alignment; HTML and Markdown in and out.
    // Only its foundation editor is used; its Material 3 wrapper would pull Material 3 up to an
    // alpha for the whole app, so it is left out.
    implementation(libs.richeditor.compose) {
        exclude(group = "org.jetbrains.compose.material3")
        exclude(group = "org.jetbrains.compose.material")
    }
    // Draws the first frame of a video, so a clip has a thumbnail in the gallery.
    implementation(libs.coil.video)

    // JavaMail (Email hub: IMAP + SMTP)
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    // Pro, bought once inside the app through Google Play.
    implementation(libs.billing.ktx)
    // When the Pro trial began, kept by Google Play services where clearing the app's data does
    // not reach it (ProTrial).
    implementation(libs.play.services.blockstore)

    // What Zeta and other apps' hubs speak (ZetaHubKit, PLATFORM.md).
    implementation(project(":hubkit-protocol"))

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

    // CameraX (camera hub)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.extensions)

    // DataStore & Serialization
    implementation(libs.androidx.datastore.preferences)
    
    // Biometric; MainActivity extends FragmentActivity for its prompt, so fragment is named too
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    // Testing
    testImplementation(libs.junit)
    // android.jar stubs org.json, so the real implementation is put on the unit test classpath
    testImplementation("org.json:json:20240303")
    // ...and the XML pull parser, which the platform has as kxml: the news hub reads feeds with it
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// Some tests read the app's own sources (HubStandardTest keeps every hub on ZuneHub); a change to
// a source that compiles to the same classes must still run them again.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/java").withPropertyName("appSources").withPathSensitivity(PathSensitivity.RELATIVE)
}
