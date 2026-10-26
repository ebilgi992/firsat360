plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.firsat360.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.firsat360.app"
        minSdk = 26
        // Google Play: 31.08.2026'dan itibaren yeni uygulama/güncellemeler API 36 hedeflemeli.
        // Kenardan kenara düzen (pencere boşlukları) ve OnBackInvokedCallback ile geri hareketi kodda ele alındı.
        targetSdk = 36
        versionCode = 20
        versionName = "1.8.0-beta"
    }

    signingConfigs {
        // Projedeki sabit DEBUG anahtarı (herkese açık parola) — Gradle'sız derlemeyle aynı imza (signing/README.md)
        create("fixedDebug") {
            storeFile = file("../signing/firsat360-debug.jks")
            storePassword = "android"
            keyAlias = "firsat360debug"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixedDebug") }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // İmzalama: anahtar bilgileri ortam değişkenlerinden okunur, depoya eklenmez.
            val ks = System.getenv("FIRSAT360_KEYSTORE")
            if (ks != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(ks)
                    storePassword = System.getenv("FIRSAT360_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("FIRSAT360_KEY_ALIAS")
                    keyPassword = System.getenv("FIRSAT360_KEY_PASSWORD")
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "json" }
}

// Uygulama kodu AndroidX veya üçüncü taraf kütüphane kullanmaz (yalnızca Android çerçevesi + Kotlin stdlib).

