#!/bin/sh
# Verifies every tracked binary against third_party/CHECKSUMS.sha256 (docs/THIRD_PARTY.md).
#
# Fails when
#   - a tracked file with a binary extension is not listed (a new binary needs a record first),
#   - a listed file is missing, is not tracked by git, or has a different SHA-256,
#   - the checksum file has a malformed or duplicate line.
#
# Usage: sh tools/verify-binaries.sh [repository-root]
# The root defaults to the repository this script lives in. Needs git and sha256sum or shasum; no network.
set -eu

# Extensions that count as a binary. Images are not in the list: they are reviewed as assets.
binary_pattern='\.(a|o|so|obj|lib|dll|dylib|exe|ko|node|jar|aar|dex|apk|aab|class|pyc|whl|bin|spv|wasm|tflite|ttf|ttc|otf|woff|woff2|wav|ogg|mp3|zip|gz|tgz|bz2|xz|7z|jks|keystore)$'

if [ "$#" -gt 0 ]; then
    root=$1
else
    root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
fi
cd -- "$root"

list=third_party/CHECKSUMS.sha256
if [ ! -f "$list" ]; then
    echo "verify-binaries: $list not found in $root" >&2
    exit 2
fi

if command -v sha256sum >/dev/null 2>&1; then
    sha256_of() { sha256sum < "$1" | cut -d ' ' -f 1; }
elif command -v shasum >/dev/null 2>&1; then
    sha256_of() { shasum -a 256 < "$1" | cut -d ' ' -f 1; }
else
    echo "verify-binaries: neither sha256sum nor shasum is installed" >&2
    exit 2
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT HUP INT TERM

# Submodule contents are included when the submodules are checked out.
git -c core.quotePath=false ls-files --recurse-submodules > "$work/tracked"
grep -E -i -- "$binary_pattern" "$work/tracked" > "$work/binaries" || true

failures=0
fail() {
    echo "$1" >&2
    failures=$((failures + 1))
}

# Pass 1: every line is "<64 hex digits><two spaces><path>", and every listed file matches.
: > "$work/listed"
while IFS= read -r line || [ -n "$line" ]; do
    [ -n "$line" ] || continue
    expected=${line%%  *}
    path=${line#*  }
    case $line in
        *"  "*) ;;
        *) fail "MALFORMED LINE: $line"; continue ;;
    esac
    if [ "${#expected}" -ne 64 ] || [ -n "$(printf '%s' "$expected" | tr -d '0-9a-f')" ] || [ -z "$path" ]; then
        fail "MALFORMED LINE: $line"
        continue
    fi
    if grep -F -x -q -- "$path" "$work/listed"; then
        fail "LISTED TWICE: $path"
        continue
    fi
    printf '%s\n' "$path" >> "$work/listed"
    if ! grep -F -x -q -- "$path" "$work/tracked"; then
        fail "LISTED BUT NOT TRACKED: $path"
    elif [ ! -f "$path" ]; then
        fail "MISSING: $path"
    else
        actual=$(sha256_of "$path")
        if [ "$actual" != "$expected" ]; then
            fail "MISMATCH: $path (expected $expected, found $actual)"
        fi
    fi
done < "$list"

# Pass 2: every tracked binary has a line.
while IFS= read -r path; do
    if ! grep -F -x -q -- "$path" "$work/listed"; then
        fail "UNLISTED BINARY: $path"
    fi
done < "$work/binaries"

if [ "$failures" -ne 0 ]; then
    echo "verify-binaries: $failures problem(s). A binary changes only together with its line in $list and its row in docs/THIRD_PARTY.md." >&2
    exit 1
fi
echo "verify-binaries: $(wc -l < "$work/listed" | tr -d ' ') binaries match $list"
