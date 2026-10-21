#!/usr/bin/env bash
# ItzEditor manual Android build (no Gradle).
# Supports:
#   UNSIGNED=1  -> produce the unsigned APK expected by F-Droid
#   unset/0     -> produce a locally signed release APK (existing workflow)
set -euo pipefail

cd "$(dirname "$0")"

# Prefer the SDK explicitly supplied by the build environment (e.g. F-Droid).
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK_ROOT" ]]; then
  echo "ERROR: ANDROID_HOME or ANDROID_SDK_ROOT must point to an Android SDK." >&2
  exit 1
fi
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

if [[ -n "${JAVA_HOME:-}" && ! -x "${JAVA_HOME}/bin/javac" ]]; then
  echo "ERROR: JAVA_HOME is set but javac was not found: $JAVA_HOME" >&2
  exit 1
fi

PLATFORM_VERSION="${ANDROID_PLATFORM_VERSION:-34}"
BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-${PLATFORM_VERSION}/android.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"

for required in \
  "$ANDROID_JAR" \
  "$BUILD_TOOLS/aapt2" \
  "$BUILD_TOOLS/zipalign" \
  "$BUILD_TOOLS/apksigner" \
  "$BUILD_TOOLS/lib/d8.jar"
do
  if [[ ! -e "$required" ]]; then
    echo "ERROR: Required Android SDK component is missing: $required" >&2
    echo "Install Android platform ${PLATFORM_VERSION} and Build Tools ${BUILD_TOOLS_VERSION} in the build environment." >&2
    exit 1
  fi
done

if ! command -v javac >/dev/null 2>&1; then
  echo "ERROR: javac was not found in PATH. Set JAVA_HOME to a JDK 17 installation." >&2
  exit 1
fi
if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java was not found in PATH. Set JAVA_HOME to a JDK 17 installation." >&2
  exit 1
fi

export PATH="$BUILD_TOOLS:$PATH"

SRC="app/src/main"
OUT="build-manual"
VERSION_CODE="${VERSION_CODE:-5}"
VERSION_NAME="${VERSION_NAME:-1.0.4}"
PACKAGE_NAME="me.fndlabs.itzeditor"

if [[ ! -d "$SRC/res" || ! -f "$SRC/AndroidManifest.xml" || ! -d "$SRC/java" ]]; then
  echo "ERROR: Expected Android source files under $SRC were not found." >&2
  exit 1
fi
if [[ ! -f "proguard-rules.pro" ]]; then
  echo "ERROR: proguard-rules.pro was not found in the repository root." >&2
  exit 1
fi

# Clean output to avoid stale class/dex files leaking into the APK.
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> aapt2 compile resources"
"$BUILD_TOOLS/aapt2" compile --dir "$SRC/res" -o "$OUT/compiled-res.zip"

echo "==> aapt2 link (R.java + unsigned APK)"
sed "s|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PACKAGE_NAME\">|" \
  "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BUILD_TOOLS/aapt2" link -o "$OUT/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --min-sdk-version 26 --target-sdk-version 34 \
  "$OUT/compiled-res.zip"

echo "==> javac"
find "$SRC/java" "$OUT/gen" -name '*.java' -print > "$OUT/sources.txt"
if [[ ! -s "$OUT/sources.txt" ]]; then
  echo "ERROR: No Java source files found." >&2
  exit 1
fi
javac -encoding UTF-8 -source 17 -target 17 \
  -cp "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "==> R8 (minify + obfuscate)"
CLASS_FILES=()
while IFS= read -r -d '' class_file; do
  CLASS_FILES+=("$class_file")
done < <(find "$OUT/classes" -name '*.class' -print0)
if [[ ${#CLASS_FILES[@]} -eq 0 ]]; then
  echo "ERROR: javac produced no class files." >&2
  exit 1
fi

R8_ARGS=(
  --release
  --min-api 26
  --lib "$ANDROID_JAR"
  --pg-conf proguard-rules.pro
  --pg-map-output "$OUT/mapping.txt"
  --output "$OUT/dex"
)
java -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.R8 "${R8_ARGS[@]}" "${CLASS_FILES[@]}"

if [[ ! -f "$OUT/dex/classes.dex" ]]; then
  echo "ERROR: R8 did not produce $OUT/dex/classes.dex." >&2
  exit 1
fi

echo "==> insert dex into APK"
cp "$OUT/app-unsigned.apk" "$OUT/app-with-dex.apk"
python3 -c "import zipfile; z=zipfile.ZipFile('$OUT/app-with-dex.apk','a'); z.write('$OUT/dex/classes.dex','classes.dex',zipfile.ZIP_DEFLATED); z.close()"

echo "==> zipalign"
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT/app-with-dex.apk" "$OUT/app-aligned.apk"

if [[ "${UNSIGNED:-0}" == "1" ]]; then
  # F-Droid must build from source without using the developer's release key.
  OUTPUT_APK="$OUT/ItzEditor-v${VERSION_NAME}-unsigned.apk"
  cp "$OUT/app-aligned.apk" "$OUTPUT_APK"
  echo "==> verify unsigned APK structure"
  "$BUILD_TOOLS/aapt2" dump badging "$OUTPUT_APK" | head -4 || true
  echo
  echo "BERES: $OUTPUT_APK"
  ls -lh "$OUTPUT_APK"
  exit 0
fi

echo "==> release signing"
mkdir -p release
KEYSTORE="release/itzeditor-release.keystore"
STOREPASS_FILE="release/.storepass"
if [[ ! -f "$KEYSTORE" ]]; then
  STOREPASS="$(openssl rand -hex 16)"
  printf '%s' "$STOREPASS" > "$STOREPASS_FILE"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$STOREPASS" -keypass "$STOREPASS" \
    -alias itzeditor -keyalg RSA -keysize 4096 -validity 10950 \
    -dname "CN=ItzEditor, O=eLgorythm, L=Purbalingga, ST=Jawa Tengah, C=ID" >/dev/null 2>&1
  echo "Release keystore created: $KEYSTORE"
fi
if [[ ! -f "$STOREPASS_FILE" ]]; then
  echo "ERROR: Keystore exists but password file is missing: $STOREPASS_FILE" >&2
  exit 1
fi
STOREPASS="$(cat "$STOREPASS_FILE")"
if [[ -z "$STOREPASS" ]]; then
  echo "ERROR: Release keystore password file is empty." >&2
  exit 1
fi

cp "$OUT/mapping.txt" "release/mapping-${VERSION_NAME}.txt"
OUTPUT_APK="$OUT/ItzEditor-v${VERSION_NAME}-release.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$STOREPASS" \
  --key-pass "pass:$STOREPASS" \
  --out "$OUTPUT_APK" "$OUT/app-aligned.apk"
"$BUILD_TOOLS/apksigner" verify "$OUTPUT_APK"
"$BUILD_TOOLS/aapt2" dump badging "$OUTPUT_APK" | head -4 || true
echo
echo "BERES: $OUTPUT_APK"
ls -lh "$OUTPUT_APK"
