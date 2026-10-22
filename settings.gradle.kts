pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
// }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Zeta"
include(":app")
// ZetaHubKit (PLATFORM.md): what another app needs to give Zeta a hub, and an example of one.
include(":hubkit-protocol", ":hubkit", ":hubkit-sample")
project(":hubkit-protocol").projectDir = file("hubkit/protocol")
project(":hubkit").projectDir = file("hubkit/kit")
project(":hubkit-sample").projectDir = file("hubkit/sample")
