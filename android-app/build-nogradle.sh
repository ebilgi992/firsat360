#!/usr/bin/env bash
# FIRSAT360 — Gradle/Google Maven erişimi olmayan ortamda debug APK üretir (API 36 araç zinciri).
#
# Araç zinciri (tools/setup-toolchain.sh ile kurulur, SHA-256 doğrulamalı):
#   android-36.jar (derleme platformu), android-26.jar (minSdk API denetimi), aapt2, d8.jar (R8/D8 8.2.2),
#   apksigner.jar, Kotlin derleyicisi 2.2.20, ProGuard 7.8.1 (stdlib küçültme).
# Çıktı: build-nogradle/FIRSAT360-<sürüm>-debug.apk  (minSdk 26, targetSdk 36)
set -euo pipefail
cd "$(dirname "$0")"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
VERSION_CODE="${VERSION_CODE:-12}"
# RELEASE=1: debug imzası atılmaz; imzasız release APK + Google Play için AAB üretilir. RELEASE_KEYSTORE verilirse
# (RELEASE_KEY_ALIAS, RELEASE_STORE_PASS, RELEASE_KEY_PASS ortam değişkenleriyle) ikisi de yükleme anahtarıyla imzalanır.
# Parolalar komut satırına/günlüğe yazılmaz (apksigner/jarsigner "env:" ile okur).
RELEASE="${RELEASE:-0}"
# FEED_URL: günlük güncellenen yayının sabit adresi (ör. https://<kullanıcı>.github.io/firsat360/feed.json). Boşsa
# uygulama yalnızca içindeki veriyle çalışır. Kullanıcıya gösterilmez.
FEED_URL="${FEED_URL:-}"
PKG="com.firsat360.app"
# APP_ID: kurulum kimliği (applicationId). Kod paketi ve R sınıfı değişmez; yalnızca cihazdaki uygulama kimliği.
APP_ID="${APP_ID:-$PKG}"
APK_SUFFIX="${APK_SUFFIX:-}"
RENAME=""; [ "$APP_ID" != "$PKG" ] && RENAME="--rename-manifest-package $APP_ID"
TC="${TOOLCHAIN:-/home/claude/toolchain}"
AJ="${ANDROID_JAR:-$TC/android-36.jar}"
MINJ="${MIN_API_JAR:-$TC/android-26.jar}"
AAPT2="${AAPT2:-$TC/aapt2}"
D8="${D8_JAR:-$TC/d8.jar}"
SIGNER="${APKSIGNER_JAR:-$TC/apksigner.jar}"
BUNDLETOOL="${BUNDLETOOL_JAR:-$TC/bundletool.jar}"
KC="${KOTLINC_HOME:-$TC/kotlinc}"
PG="${PROGUARD_HOME:-$TC/proguard-7.8.1}"
OUT="$PWD/build-nogradle"
SRC="app/src/main"
export JAVA_TOOL_OPTIONS=""

for f in "$AJ" "$MINJ" "$AAPT2" "$D8" "$SIGNER" "$KC/bin/kotlinc" "$PG/lib/proguard.jar"; do
  [ -e "$f" ] || { echo "Eksik araç: $f (tools/setup-toolchain.sh)"; exit 2; }
done
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/rjava" "$OUT/min"

echo "[1/9] Manifest hazırlanıyor (paket, sürüm)"
sed -e "s#<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">#<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PKG\" android:versionCode=\"$VERSION_CODE\" android:versionName=\"$VERSION_NAME\">#" \
    "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"

echo "[2/9] Kaynaklar derleniyor ve bağlanıyor (aapt2, API 36)"
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res-compiled.zip"
OVERLAY=""
if [ -n "$FEED_URL" ]; then
  case "$FEED_URL" in https://*) ;; *) echo "FEED_URL https:// ile başlamalı"; exit 2;; esac
  mkdir -p "$OUT/res-over/values"
  printf '<?xml version="1.0" encoding="utf-8"?>\n<resources><string name="feed_url" translatable="false">%s</string></resources>\n' \
      "$FEED_URL" > "$OUT/res-over/values/config.xml"
  "$AAPT2" compile --dir "$OUT/res-over" -o "$OUT/res-over.zip"
  OVERLAY="-R $OUT/res-over.zip"
  echo "      yayın adresi: $FEED_URL"
fi
"$AAPT2" link -I "$AJ" --manifest "$OUT/AndroidManifest.xml" -o "$OUT/res.apk" -A "$SRC/assets" \
    --java "$OUT/gen" $RENAME --min-sdk-version 26 --target-sdk-version 36 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" -0 json "$OUT/res-compiled.zip" $OVERLAY

echo "[3/9] Kotlin derleniyor (yalnızca Android API'leri: -no-jdk, android-36.jar)"
"$KC/bin/kotlinc" "$SRC/java" "$OUT/gen" -cp "$AJ" -no-jdk -no-reflect -jvm-target 1.8 \
    -Xlambdas=class -Xsam-conversions=class -Xstring-concat=inline -XXLanguage:-EnumEntries \
    -d "$OUT/app.jar" 2>&1 | grep -v -E "^(Picked up|warning: .*(deprecated|unused|never used|Unused|is never))" || true
test -f "$OUT/app.jar"
javac -nowarn -source 8 -target 8 -bootclasspath "$AJ" -d "$OUT/rjava" $(find "$OUT/gen" -name '*.java') 2>&1 | grep -v -E "^(Picked up|warning)" || true
( cd "$OUT/rjava" && zip -q -r ../r.jar . )

echo "[4/9] ProGuard ile küçültülüyor (Kotlin stdlib'in kullanılmayan kısımları atılır)"
cat > "$OUT/proguard.pro" <<PRO
-injars $OUT/app.jar
-injars $OUT/r.jar
-injars $KC/lib/kotlin-stdlib.jar(!META-INF/versions/**,!META-INF/*.kotlin_module,!META-INF/MANIFEST.MF)
-outjars $OUT/min.jar
-libraryjars $AJ
-dontobfuscate
-dontoptimize
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**
-dontnote **
-keep class $PKG.** { *; }
PRO
java -jar "$PG/lib/proguard.jar" @"$OUT/proguard.pro" 2>&1 | grep -v -E "^(Picked up)" | tail -2

echo "[5/9] minSdk 26 API denetimi ve açılış sırası denetimi"
python3 tools/check_min_api.py "$OUT/min.jar" "$MINJ" tools/min-api-allowlist.txt
python3 tools/check_startup_order.py "$SRC/java/com/firsat360/app/MainActivity.kt"

echo "[6/9] DEX üretiliyor (d8, min-api 26)"
java -cp "$D8" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJ" --output "$OUT" "$OUT/min.jar" 2>&1 \
    | grep -v -E "^(Picked up|Warning: An error occurred when parsing kotlin metadata|Info: Class .* has malformed kotlin.Metadata|Warning: Unexpected error during rewriting of Kotlin metadata|com.android.tools.r8.internal|$)" || true
test -f "$OUT/classes.dex"

if [ "$RELEASE" = "1" ]; then
  echo "[7/9] Release: APK + AAB (bundletool)"
  test -f "$BUNDLETOOL" || { echo "Eksik araç: $BUNDLETOOL"; exit 2; }
  cp "$OUT/res.apk" "$OUT/unsigned.apk"
  ( cd "$OUT" && zip -q unsigned.apk classes.dex )
  python3 tools/zipalign.py "$OUT/unsigned.apk" "$OUT/aligned.apk"
  REL_APK="$OUT/FIRSAT360-${VERSION_NAME}${APK_SUFFIX}-release-unsigned.apk"
  cp "$OUT/aligned.apk" "$REL_APK"
  # AAB: kaynaklar protobuf biçiminde yeniden bağlanır, base modülü oluşturulur
  "$AAPT2" link --proto-format -I "$AJ" --manifest "$OUT/AndroidManifest.xml" -o "$OUT/res-proto.apk" -A "$SRC/assets" \
      $RENAME --min-sdk-version 26 --target-sdk-version 36 \
      --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" -0 json "$OUT/res-compiled.zip" $OVERLAY
  rm -rf "$OUT/base" "$OUT/base.zip" && mkdir -p "$OUT/base/manifest" "$OUT/base/dex"
  ( cd "$OUT/base" && unzip -q ../res-proto.apk && mv AndroidManifest.xml manifest/ && cp ../classes.dex dex/ && zip -q -r ../base.zip . )
  REL_AAB="$OUT/FIRSAT360-${VERSION_NAME}${APK_SUFFIX}-release-unsigned.aab"
  rm -f "$REL_AAB"
  java -jar "$BUNDLETOOL" build-bundle --modules="$OUT/base.zip" --output="$REL_AAB" 2>&1 | grep -v "^Picked up" || true
  java -jar "$BUNDLETOOL" validate --bundle="$REL_AAB" 2>&1 | grep -v "^Picked up" | head -3
  if [ -n "${RELEASE_KEYSTORE:-}" ]; then
    echo "[8/9] Yükleme anahtarıyla imzalanıyor (parolalar ortam değişkeninden)"
    : "${RELEASE_KEY_ALIAS:?RELEASE_KEY_ALIAS gerekli}" "${RELEASE_STORE_PASS:?}" "${RELEASE_KEY_PASS:?}"
    java -jar "$SIGNER" sign --ks "$RELEASE_KEYSTORE" --ks-pass env:RELEASE_STORE_PASS --ks-key-alias "$RELEASE_KEY_ALIAS" \
        --key-pass env:RELEASE_KEY_PASS --out "${REL_APK%-unsigned.apk}.apk" "$REL_APK"
    cp "$REL_AAB" "${REL_AAB%-unsigned.aab}.aab"
    jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$RELEASE_KEYSTORE" -storepass:env RELEASE_STORE_PASS \
        -keypass:env RELEASE_KEY_PASS "${REL_AAB%-unsigned.aab}.aab" "$RELEASE_KEY_ALIAS" >/dev/null
    echo "[9/9] Doğrulama"
    java -jar "$SIGNER" verify "${REL_APK%-unsigned.apk}.apk" && echo "APK imzası geçerli"
    jarsigner -verify "${REL_AAB%-unsigned.aab}.aab" | tail -1
  else
    echo "[8/9] RELEASE_KEYSTORE verilmedi → imzasız çıktılar (tools/sign-release.sh ile imzalayın)"
  fi
  ls -la "$OUT"/FIRSAT360-*release*
  exit 0
fi

echo "[7/9] APK birleştiriliyor ve 4 bayta hizalanıyor"
cp "$OUT/res.apk" "$OUT/unsigned.apk"
( cd "$OUT" && zip -q unsigned.apk classes.dex )
python3 tools/zipalign.py "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[8/9] Debug anahtarıyla imzalanıyor (v2+v3)"
KS="${DEBUG_KEYSTORE:-$(pwd)/signing/firsat360-debug.jks}"  # projedeki sabit DEBUG anahtarı (bkz. signing/README.md)
mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias firsat360debug \
    -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=FIRSAT360 Debug,O=FIRSAT360,C=TR" >/dev/null 2>&1
fi
APK="$OUT/FIRSAT360-${VERSION_NAME}${APK_SUFFIX}-debug.apk"
java -jar "$SIGNER" sign --ks "$KS" --ks-pass pass:android --ks-key-alias firsat360debug --out "$APK" "$OUT/aligned.apk"

echo "[9/9] Doğrulama"
java -jar "$SIGNER" verify --verbose "$APK" | head -4
"$AAPT2" dump badging "$APK" | grep -E "^(package|sdkVersion|targetSdkVersion|application-label:|uses-permission|launchable)"
ls -la "$APK"
echo "TAMAM: $APK"
