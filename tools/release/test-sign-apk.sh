#!/bin/sh
# Tests tools/release/sign-apk.sh in a temporary copy of the repository layout, with a throwaway keystore and a real
# unsigned release APK (needs app/build/outputs/apk/release/ from ./gradlew :app:assembleRelease, keytool and the Android build tools).
#   sh tools/release/test-sign-apk.sh
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
SRC_APK=$(ls "$ROOT"/app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk 2>/dev/null | head -1)
[ -f "$SRC_APK" ] || { echo "test-sign-apk: build the release APK first (./gradlew :app:assembleRelease)"; exit 2; }
KEYTOOL=${KEYTOOL:-${JAVA_HOME:+$JAVA_HOME/bin/keytool}}
[ -x "${KEYTOOL:-}" ] || KEYTOOL=/opt/homebrew/opt/openjdk@21/bin/keytool
[ -x "$KEYTOOL" ] || KEYTOOL=$(command -v keytool)
[ -x "$KEYTOOL" ] || { echo "test-sign-apk: keytool not found"; exit 2; }

export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}   # kept when HOME is replaced below
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
export HOME="$T/home"; mkdir -p "$HOME"
PASS='Correct-Horse-Battery-9'
"$KEYTOOL" -genkeypair -keystore "$T/test.jks" -alias delos -keyalg RSA -keysize 2048 -validity 30 -storepass "$PASS" -keypass "$PASS" -dname "CN=Test" >/dev/null 2>&1
FP=$("$KEYTOOL" -list -v -keystore "$T/test.jks" -alias delos -storepass "$PASS" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p')
[ -n "$FP" ] || { echo "test-sign-apk: cannot read the test fingerprint"; exit 2; }

mkrepo() {  # mkrepo <dir> <fingerprint> <major.minor.patch>
  mkdir -p "$1/tools/release" "$1/docs" "$1/app/build/outputs/apk/release"
  cp "$ROOT/tools/release/sign-apk.sh" "$1/tools/release/"
  printf '        def versionMajor = %s\n        def versionMinor = %s\n        def versionPatch = %s\n' $(echo "$3" | tr . ' ') > "$1/app/build.gradle"
  printf '| Certificate SHA-256 | `%s` |\n' "$2" > "$1/docs/VERSIONING.md"
  cp "$SRC_APK" "$1/app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk"
  cp "$SRC_APK" "$1/app/build/outputs/apk/release/app-armeabi-v7a-release-unsigned.apk"
}

PASSED=0; FAILED=0
ok() { PASSED=$((PASSED+1)); echo "ok   $1"; }
bad() { FAILED=$((FAILED+1)); echo "FAIL $1"; }
run() { printf '%s\n' "$PASS" | sh "$1/tools/release/sign-apk.sh" "$2" "${3:-$T/test.jks}" 2>&1; }

# 1. the whole path
R1="$T/r1"; mkrepo "$R1" "$FP" 1.2.3
OUT=$(run "$R1" 1.2.3); CODE=$?
D="$HOME/Releases/Delos-1.2.3"
[ "$CODE" = 0 ] && [ -f "$D/delos-1.2.3-arm64-v8a.apk" ] && [ -f "$D/delos-1.2.3-armeabi-v7a.apk" ] && ok "signs both APKs and names them as the release asset contract says" || bad "full run (exit $CODE): $OUT"
( cd "$D" && shasum -a 256 -c SHA256SUMS.txt >/dev/null 2>&1 ) && [ "$(wc -l < "$D/SHA256SUMS.txt" | tr -d ' ')" = 2 ] && ok "SHA256SUMS.txt lists both files and every checksum matches" || bad "SHA256SUMS.txt"
BT=$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)
"$BT/apksigner" verify "$D/delos-1.2.3-arm64-v8a.apk" >/dev/null 2>&1 && ok "apksigner verifies the output" || bad "apksigner verify"
printf '%s' "$OUT" | grep -qF "$PASS" && bad "the password appears in the output" || ok "the password is not printed"
[ -z "$(ls -A "$D" | grep -E '^\.|idsig|aligned')" ] && ok "no temporary file is left behind" || bad "leftovers in $D"

# 2. an existing output directory is never overwritten
OUT=$(run "$R1" 1.2.3); [ $? -ne 0 ] && ok "refuses to overwrite an existing release directory" || bad "overwrite"

# 3. a different key than the recorded one: nothing is left
R3="$T/r3"; mkrepo "$R3" "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF" 2.0.0
OUT=$(run "$R3" 2.0.0); CODE=$?
[ "$CODE" -ne 0 ] && [ ! -e "$HOME/Releases/Delos-2.0.0" ] && printf '%s' "$OUT" | grep -q "not with the recorded key" && ok "a key other than the recorded one is refused and its output is deleted" || bad "wrong key (exit $CODE)"

# 4. the version must match the build file
R4="$T/r4"; mkrepo "$R4" "$FP" 1.2.3
OUT=$(run "$R4" 1.3.0); [ $? -ne 0 ] && printf '%s' "$OUT" | grep -q "says 1.2.3" && ok "a version that differs from app/build.gradle is refused" || bad "version mismatch"
run "$R4" "not-a-version" >/dev/null; [ $? -ne 0 ] && ok "a malformed version is refused" || bad "malformed version"

# 5. release candidate: the suffix is allowed, the numbers must match
OUT=$(run "$R4" 1.2.3-rc.1); [ $? -eq 0 ] && [ -f "$HOME/Releases/Delos-1.2.3-rc.1/delos-1.2.3-rc.1-arm64-v8a.apk" ] && ok "a release candidate version is accepted and named with its suffix" || bad "rc: $OUT"

# 6. wrong password: nothing is left
R6="$T/r6"; mkrepo "$R6" "$FP" 3.0.0
OUT=$(printf 'wrong\n' | sh "$R6/tools/release/sign-apk.sh" 3.0.0 "$T/test.jks" 2>&1); CODE=$?
[ "$CODE" -ne 0 ] && [ ! -e "$HOME/Releases/Delos-3.0.0" ] && ok "a wrong password fails and leaves no output" || bad "wrong password (exit $CODE)"

# 7. missing keystore and missing APKs
OUT=$(run "$R6" 3.0.0 "$T/none.jks"); [ $? -ne 0 ] && ok "a missing keystore is refused" || bad "missing keystore"
rm -f "$R6"/app/build/outputs/apk/release/*.apk
OUT=$(run "$R6" 3.0.0); [ $? -ne 0 ] && printf '%s' "$OUT" | grep -q "no unsigned release APK" && ok "missing unsigned APKs are reported" || bad "missing APKs"

echo; echo "test-sign-apk: $PASSED passed, $FAILED failed"
[ "$FAILED" = 0 ]
