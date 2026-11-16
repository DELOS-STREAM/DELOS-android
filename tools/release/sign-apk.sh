#!/bin/sh
# Signs the release APKs of Delos with the release key and writes the release assets (docs/VERSIONING.md, "Release assets").
#
#   sh tools/release/sign-apk.sh <version> [keystore]
#
# <version>   the version being released, for example 1.0.0 (must equal versionName in app/build.gradle).
# [keystore]  the keystore file; default ~/Keys/delos-release.jks. Alias: delos (override with DELOS_KEY_ALIAS).
#
# Input:  the unsigned release APKs of `./gradlew :app:assembleRelease` (app/build/outputs/apk/release/).
# Output: ~/Releases/Delos-<version>/delos-<version>-<abi>.apk and SHA256SUMS.txt.
#
# The password is asked for once on the terminal (nothing is echoed) and passed to apksigner through a pipe: it is never
# written to a file, to the process list or to the output. The certificate that signed the files must have the SHA-256
# fingerprint recorded in docs/VERSIONING.md, or the script deletes its output and stops.
set -eu

die() { echo "sign-apk: $*" >&2; exit 1; }

VERSION=${1:-}
[ -n "$VERSION" ] || die "usage: sh tools/release/sign-apk.sh <version> [keystore]"
printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$' || die "'$VERSION' is not a version (X.Y.Z or X.Y.Z-rc.N)"
KEYSTORE=${2:-$HOME/Keys/delos-release.jks}
ALIAS=${DELOS_KEY_ALIAS:-delos}
[ -f "$KEYSTORE" ] || die "keystore not found: $KEYSTORE"

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
IN="$ROOT/app/build/outputs/apk/release"
OUT="$HOME/Releases/Delos-$VERSION"

# The version in the build file must be the one being released (a pre-release suffix is not part of versionName's numbers)
BASE=${VERSION%%-*}
MAJOR=$(sed -n 's/.*def versionMajor = \([0-9]*\).*/\1/p' "$ROOT/app/build.gradle")
MINOR=$(sed -n 's/.*def versionMinor = \([0-9]*\).*/\1/p' "$ROOT/app/build.gradle")
PATCH=$(sed -n 's/.*def versionPatch = \([0-9]*\).*/\1/p' "$ROOT/app/build.gradle")
[ "$BASE" = "$MAJOR.$MINOR.$PATCH" ] || die "app/build.gradle says $MAJOR.$MINOR.$PATCH, not $BASE"

# apksigner needs Java. A terminal without JAVA_HOME finds only macOS's stub `java`, so use the JDK the build uses (docs/BUILDING.md).
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  for J in /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home; do
    [ -x "$J/bin/java" ] && { JAVA_HOME=$J; break; }
  done
fi
[ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] || die "no Java found: install it with 'brew install openjdk@21' (docs/BUILDING.md)"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

# Build tools: the newest installed
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
[ -n "$BT" ] && [ -x "$BT/apksigner" ] && [ -x "$BT/zipalign" ] || die "apksigner or zipalign not found under $SDK/build-tools"

# The fingerprint recorded in the repository
EXPECTED=$(sed -n 's/^| Certificate SHA-256 | `\([0-9A-F:]*\)` |$/\1/p' "$ROOT/docs/VERSIONING.md")
[ -n "$EXPECTED" ] || die "no certificate fingerprint in docs/VERSIONING.md"

set -- "$IN"/app-*-release-unsigned.apk
[ -f "$1" ] || die "no unsigned release APK in $IN (run ./gradlew :app:assembleRelease first)"

[ ! -e "$OUT" ] || die "$OUT already exists: move it away first"
mkdir -p "$OUT"
trap 'rm -rf "$OUT"' EXIT INT TERM

printf 'Keystore password (not shown): '
stty -echo 2>/dev/null || true
IFS= read -r PASS
stty echo 2>/dev/null || true
printf '\n'
[ -n "$PASS" ] || die "empty password"

ABIS=""
for APK in "$@"; do
  ABI=$(basename "$APK" | sed 's/^app-\(.*\)-release-unsigned\.apk$/\1/')
  TARGET="$OUT/delos-$VERSION-$ABI.apk"
  ALIGNED="$OUT/.aligned-$ABI.apk"
  "$BT/zipalign" -f -p 4 "$APK" "$ALIGNED"
  printf '%s\n' "$PASS" | "$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" --ks-pass stdin \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --out "$TARGET" "$ALIGNED"
  rm -f "$ALIGNED" "$TARGET.idsig"
  "$BT/apksigner" verify --verbose "$TARGET" >/dev/null || die "verification failed for $TARGET"
  CERT=$("$BT/apksigner" verify --print-certs "$TARGET" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//')
  [ "$CERT" = "$EXPECTED" ] || die "$TARGET is signed with $CERT, not with the recorded key $EXPECTED"
  ABIS="$ABIS $ABI"
  echo "signed and verified: $(basename "$TARGET")"
done
PASS=""

( cd "$OUT" && shasum -a 256 delos-"$VERSION"-*.apk > SHA256SUMS.txt )
trap - EXIT INT TERM
echo
echo "Release assets in $OUT:"
ls -l "$OUT" | awk 'NR>1 {printf "  %-40s %s bytes\n", $9, $5}'
echo
cat "$OUT/SHA256SUMS.txt"
echo
echo "Certificate SHA-256 (matches docs/VERSIONING.md): $EXPECTED"
