#!/usr/bin/env bash
# Build APK ItzEditor tanpa Gradle (daemon Gradle tidak bisa jalan di VM ini).
# Toolchain: aapt2 -> javac -> d8 -> zipalign -> apksigner (build-tools 35.0.0).
# Selalu build dari direktori bersih agar kelas sisa tidak bocor ke dex.
set -euo pipefail

cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/home/hatch/tools/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/home/hatch/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"

ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"
SRC="app/src/main"
OUT="build-manual"
VERSION_CODE=5
VERSION_NAME="1.0.4"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> aapt2 compile resources"
"$BUILD_TOOLS/aapt2" compile --dir "$SRC/res" -o "$OUT/compiled-res.zip"

echo "==> aapt2 link (R.java + APK mentah)"
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="me.fndlabs.itzeditor">|' \
  "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BUILD_TOOLS/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --min-sdk-version 26 --target-sdk-version 34 \
  "$OUT/compiled-res.zip"

echo "==> javac"
find "$SRC/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 \
  -cp "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> R8 (minify + obfuscate, pengganti ProGuard)"
java -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.R8 \
  --release --min-api 26 \
  --lib "$ANDROID_JAR" \
  --pg-conf proguard-rules.pro \
  --pg-map-output "$OUT/mapping.txt" \
  --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')
# Simpan mapping per versi untuk deobfuscate stack trace crash
cp "$OUT/mapping.txt" "release/mapping-$VERSION_NAME.txt"

echo "==> masukin dex ke APK"
cp "$OUT/app-unsigned.apk" "$OUT/app-with-dex.apk"
(cd "$OUT" && zip -q app-with-dex.apk -j dex/classes.dex)

echo "==> zipalign"
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/app-with-dex.apk" "$OUT/app-aligned.apk"

echo "==> keystore rilis (dibuat sekali, dipakai semua update)"
KEYSTORE="release/itzeditor-release.keystore"
STOREPASS_FILE="release/.storepass"
mkdir -p release
if [ ! -f "$KEYSTORE" ]; then
  STOREPASS=$(openssl rand -hex 16)
  echo -n "$STOREPASS" > "$STOREPASS_FILE"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$STOREPASS" -keypass "$STOREPASS" \
    -alias itzeditor -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=ItzEditor, O=eLgorythm, L=Purbalingga, ST=Jawa Tengah, C=ID" >/dev/null 2>&1
  echo "Keystore rilis baru dibuat: $KEYSTORE"
fi
STOREPASS=$(cat "$STOREPASS_FILE")

echo "==> tanda tangan rilis"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$STOREPASS" \
  --key-pass "pass:$STOREPASS" \
  --out "$OUT/ItzEditor-v$VERSION_NAME-release.apk" "$OUT/app-aligned.apk"

"$BUILD_TOOLS/apksigner" verify "$OUT/ItzEditor-v$VERSION_NAME-release.apk"
"$BUILD_TOOLS/aapt2" dump badging "$OUT/ItzEditor-v$VERSION_NAME-release.apk" | head -4
echo ""
echo "BERES: $OUT/ItzEditor-v$VERSION_NAME-release.apk"
ls -lh "$OUT/ItzEditor-v$VERSION_NAME-release.apk"
