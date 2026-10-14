// UYARI: Bu Gradle yapılandırması, Google Maven'a erişimi olmayan geliştirme ortamında DERLENEREK TEST EDİLEMEDİ.
// Doğrulanmış derleme yolu: ./build-nogradle.sh. Android Studio'da açıldığında AGP/Kotlin sürümleri gerekirse güncellenmelidir.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FIRSAT360"
include(":app")
