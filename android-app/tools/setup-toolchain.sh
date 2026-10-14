#!/usr/bin/env bash
# FIRSAT360 — Gradle'sız Android araç zincirini kurar (Linux x86_64). Her dosya SHA-256 ile doğrulanır.
#
# Neden: Android Studio/Gradle kullanılamayan (Google Maven erişimi olmayan) ortamlarda APK üretmek için.
# Android Studio'nuz varsa buna gerek yok: android-app/ klasörünü Android Studio'da açıp Gradle ile derleyin.
#
# Kaynaklar ve güven notu:
#  - android-36.jar / android-26.jar: yalnızca DERLEME/DENETİM için platform saplama (stub) jar'ları; APK'ya girmez.
#    GitHub: Reginer/aosp-android-jar (API 36), Sable/android-platforms (API 26).
#  - aapt2 (2.20-15009934): npm "aaptjs3@2.0.2" paketindeki Linux ikilisi (Google build-tools derlemesi olduğu
#    beyan ediliyor; bu ortamda Google'ın kendi dağıtımıyla karşılaştırılamadı).
#  - d8.jar (D8 8.2.2) ve apksigner.jar: npm "@drxiaozhi/minapk@0.4.0" paketinden. Paketin NOTICE dosyası bu
#    dosyaların Google build-tools 34.0.0 arşiviyle bayt bayt aynı olduğunu ve SHA-256 değerlerini belirtir; aşağıdaki
#    değerler o beyanla eşleşti.
#  - Kotlin 2.2.20 ve ProGuard 7.8.1: resmî GitHub sürümleri (SHA-256 burada hesaplandı).
set -euo pipefail
DEST="${1:-$HOME/firsat360-toolchain}"
mkdir -p "$DEST"; cd "$DEST"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

fetch() { # url sha256 çıktı
  curl -fsSL --retry 3 -o "$3" "$1"
  echo "$2  $3" | sha256sum -c --quiet || { echo "SHA-256 UYUŞMADI: $1"; rm -f "$3"; exit 1; }
}

fetch https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-36/android.jar \
  231e29e345e85c7107bde3e08ece052c067ebbe9d72256f036b9a126e4dde0f6 android-36.jar
fetch https://raw.githubusercontent.com/Sable/android-platforms/master/android-26/android.jar \
  cdc1846376a14b0370cc63454a129606b4a52cc50ada75ef0d4cf956b1ad2daa android-26.jar

( cd "$TMP" && npm pack --silent aaptjs3@2.0.2 @drxiaozhi/minapk@0.4.0 >/dev/null \
  && tar xzf aaptjs3-2.0.2.tgz && cp package/bin/x64/linux/aapt2 "$DEST/aapt2" && rm -rf package \
  && tar xzf drxiaozhi-minapk-0.4.0.tgz && cp package/tools/d8.jar package/tools/apksigner.jar "$DEST/" )
echo "e4dff6060827a3e401bce376e47916f2f93a89602af54973ffd3fdfc2528be3f  aapt2" | sha256sum -c --quiet
echo "d43c8a94c9b1f1da1a7cc49c32b81e8cee1708b37ee8b530a81a0688222b42c0  d8.jar" | sha256sum -c --quiet
echo "eefdd6aed9db9fb849e4c98a50d8741e19d1b674ba6547220bcb9c3ed152123a  apksigner.jar" | sha256sum -c --quiet
chmod +x aapt2

fetch https://github.com/JetBrains/kotlin/releases/download/v2.2.20/kotlin-compiler-2.2.20.zip \
  81f0264c9073b5cbbdb3ff8418cf2c5dac076879fc156fa1a6462f5a5acc4420 "$TMP/kotlin.zip"
unzip -q -o "$TMP/kotlin.zip" -d "$DEST"
fetch https://github.com/Guardsquare/proguard/releases/download/v7.8.1/proguard-7.8.1.tar.gz \
  57133f21ba8f0abcf0b173060861f150498940428791cd97512f4b68a6dae14b "$TMP/pg.tgz"
tar xzf "$TMP/pg.tgz" -C "$DEST"

cat <<MSG
Araç zinciri hazır: $DEST
Derleme:
  TOOLCHAIN=$DEST KOTLINC_HOME=$DEST/kotlinc PROGUARD_HOME=$DEST/proguard-7.8.1 ./build-nogradle.sh
Gereken diğer araçlar: JDK 17+ (java, javac, javap, keytool), python3, zip, unzip, npm, curl.
MSG
