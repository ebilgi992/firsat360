#!/usr/bin/env bash
# İmzasız release APK/AAB'yi KENDİ yükleme anahtarınızla imzalar (anahtar ve parolalar sizde kalır).
# Kullanım:
#   export RELEASE_STORE_PASS=...  RELEASE_KEY_PASS=...       # komut geçmişine yazmamak için: read -s RELEASE_STORE_PASS
#   bash tools/sign-release.sh upload.jks upload FIRSAT360-0.6.0-beta-release-unsigned.aab FIRSAT360-0.6.0-beta-release-unsigned.apk
# Anahtar oluşturma (bir kez, güvenli yerde saklayın; kaybederseniz Play Console'dan yükleme anahtarı sıfırlama gerekir):
#   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 9125
set -euo pipefail
KS="$1"; ALIAS="$2"; shift 2
: "${RELEASE_STORE_PASS:?RELEASE_STORE_PASS ortam değişkeni gerekli}" "${RELEASE_KEY_PASS:?RELEASE_KEY_PASS gerekli}"
SIGNER="${APKSIGNER_JAR:-$HOME/firsat360-toolchain/apksigner.jar}"
for f in "$@"; do
  case "$f" in
    *.aab) out="${f%-unsigned.aab}.aab"; cp "$f" "$out"
           jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KS" -storepass:env RELEASE_STORE_PASS \
             -keypass:env RELEASE_KEY_PASS "$out" "$ALIAS" >/dev/null && jarsigner -verify "$out" | tail -1 && echo "→ $out" ;;
    *.apk) out="${f%-unsigned.apk}.apk"
           if [ -f "$SIGNER" ]; then java -jar "$SIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass env:RELEASE_STORE_PASS \
             --key-pass env:RELEASE_KEY_PASS --out "$out" "$f"; else apksigner sign --ks "$KS" --ks-key-alias "$ALIAS" \
             --ks-pass env:RELEASE_STORE_PASS --key-pass env:RELEASE_KEY_PASS --out "$out" "$f"; fi && echo "→ $out" ;;
  esac
done
