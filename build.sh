#!/usr/bin/env bash
# Builds out/LensPreset.apk without Gradle: aapt, javac, d8, zipalign, apksigner.
#
#   ./build.sh              -> X.Y.Z-dev.N
#   RELEASE=1 ./build.sh    -> X.Y.Z
#
# Toolchain (override via environment):
#   ANDROID_SDK   default $ANDROID_SDK_ROOT, $ANDROID_HOME or ~/Android/Sdk
#   BUILD_TOOLS   default 35.0.0 (needs aapt, d8, zipalign, apksigner; the d8 in 34.0.0
#                 crashes on anonymous classes compiled for API 10)
#   PLATFORM_JAR  default platforms/android-10/android.jar
#
# Everything is compiled against the Android 2.3.3 (API 10) platform jar, so a call the camera's
# Android 2.3.7 does not have fails here rather than on the camera.
#
# Signing: set ANDROID_KEYSTORE_B64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and
# ANDROID_KEY_PASSWORD to sign with the project key; otherwise out/debug.keystore is generated.
# The camera accepts any v1 signature, but an APK signed with another key cannot be installed over
# an existing install: remove the app first.
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"

: "${ANDROID_SDK:=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}}"
: "${BUILD_TOOLS:=35.0.0}"
: "${PLATFORM_JAR:=$ANDROID_SDK/platforms/android-10/android.jar}"
BT="$ANDROID_SDK/build-tools/$BUILD_TOOLS"
AJ="$PLATFORM_JAR"
for f in "$AJ" "$BT/aapt" "$BT/zipalign" "$BT/apksigner" "$BT/lib/d8.jar"; do
  [ -e "$f" ] || { echo "missing: $f (install: sdkmanager \"platforms;android-10\" \"build-tools;$BUILD_TOOLS\")" >&2; exit 1; }
done

# shellcheck source=tools/version.sh
. tools/version.sh
echo "building $VERSION_NAME (versionCode $VERSION_CODE)"

OUT=out/build
rm -rf "$OUT"
mkdir -p "$OUT"/{stubs,gen,classes,dex,assets}
JAVAC_OPTS=(-encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" -Xlint:-options -nowarn)

echo "[1/7] Sony API stubs (compile-time only, never packaged)"
find camera-adapter/stubs/src -name '*.java' > "$OUT/stubs.txt"
javac "${JAVAC_OPTS[@]}" -d "$OUT/stubs" "@$OUT/stubs.txt"
(cd "$OUT/stubs" && jar cf ../stubs.jar .)

# One asset, read with one open: on an A5100 every AssetManager list or open took about half a
# second, and twenty separate files kept the app loading for ten seconds. Format: core's AssetBundle.
echo "[2/7] assets: profiles and language tables, bundled"
BUNDLE="$OUT/assets/lenspreset.bundle"
: > "$BUNDLE"
add_to_bundle() {
  printf '@@ %s %s %d\n' "$1" "$(basename "$2")" "$(wc -c < "$2" | tr -d ' ')" >> "$BUNDLE"
  cat "$2" >> "$BUNDLE"
  printf '\n' >> "$BUNDLE"
}
for f in i18n/*.json; do add_to_bundle i18n "$f"; done
for kind in cameras lenses presets; do
  for f in profiles/$kind/*.json; do add_to_bundle "$kind" "$f"; done
done

MANIFEST="$OUT/AndroidManifest.xml"
sed -e "s/android:versionCode=\"[^\"]*\"/android:versionCode=\"$VERSION_CODE\"/" \
    -e "s/android:versionName=\"[^\"]*\"/android:versionName=\"$VERSION_NAME\"/" \
    app/AndroidManifest.xml > "$MANIFEST"

# The bundle is stored uncompressed (-0 bundle), so opening it needs no inflating.
echo "[3/7] aapt: R.java"
"$BT/aapt" package -f -m -0 bundle -J "$OUT/gen" -M "$MANIFEST" -S app/res -A "$OUT/assets" -I "$AJ"

echo "[4/7] javac against API 10"
{ find "$OUT/gen" core/src/main/java camera-adapter/src/main/java app/src -name '*.java'; } > "$OUT/sources.txt"
javac "${JAVAC_OPTS[@]}" -cp "$OUT/stubs.jar" -d "$OUT/classes" "@$OUT/sources.txt"

echo "[5/7] d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 10 \
  --lib "$AJ" --classpath "$OUT/stubs.jar" --output "$OUT/dex" "@$OUT/classes.txt"

echo "[6/7] package and align"
"$BT/aapt" package -f -0 bundle -M "$MANIFEST" -S app/res -A "$OUT/assets" -I "$AJ" -F "$OUT/unaligned.apk"
(cd "$OUT/dex" && "$BT/aapt" add ../unaligned.apk classes.dex >/dev/null)
"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"

echo "[7/7] sign (v1 only: the camera does not understand v2/v3)"
APK=out/LensPreset.apk
SIGN_OPTS=(--min-sdk-version 10 --v1-signing-enabled true --v2-signing-enabled false --v3-signing-enabled false)
if [ -n "${ANDROID_KEYSTORE_B64:-}" ]; then
  KS="${RUNNER_TEMP:-$ROOT/out}/release.keystore"
  printf '%s' "$ANDROID_KEYSTORE_B64" | base64 -d > "$KS"
  trap 'rm -f "$KS"' EXIT
  KS_PASS="${ANDROID_KEYSTORE_PASSWORD:?}" KEY_PASS="${ANDROID_KEY_PASSWORD:?}" \
    "$BT/apksigner" sign --ks "$KS" --ks-key-alias "${ANDROID_KEY_ALIAS:?}" \
    --ks-pass env:KS_PASS --key-pass env:KEY_PASS "${SIGN_OPTS[@]}" --out "$APK" "$OUT/aligned.apk"
else
  KS=out/debug.keystore
  [ -e "$KS" ] || keytool -genkeypair -keystore "$KS" -alias lenspreset -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass android -keypass android -dname "CN=LensPreset debug" >/dev/null 2>&1
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android "${SIGN_OPTS[@]}" \
    --out "$APK" "$OUT/aligned.apk"
fi
"$BT/apksigner" verify --min-sdk-version 10 "$APK"
if command -v sha256sum >/dev/null; then sha256sum "$APK" > "$APK.sha256"; else shasum -a 256 "$APK" > "$APK.sha256"; fi
echo "BUILD OK: $ROOT/$APK ($VERSION_NAME, versionCode $VERSION_CODE)"
