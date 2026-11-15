#!/bin/sh
# Tests tools/verify-binaries.sh against throwaway git repositories in a temporary directory.
# Usage: sh third_party/verify-binaries-test.sh   (exit status 0 = all cases passed)
set -eu

here=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
script=$here/../tools/verify-binaries.sh

if command -v sha256sum >/dev/null 2>&1; then
    sha256_of() { sha256sum < "$1" | cut -d ' ' -f 1; }
else
    sha256_of() { shasum -a 256 < "$1" | cut -d ' ' -f 1; }
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT HUP INT TERM

failed=0
case_number=0

# new_repo: a repository with two listed binaries (one with a space in its name) and one text file.
new_repo() {
    case_number=$((case_number + 1))
    repo=$tmp/case$case_number
    mkdir -p "$repo/third_party" "$repo/libs/arm64-v8a" "$repo/res font"
    printf 'static library' > "$repo/libs/arm64-v8a/libdemo.a"
    printf 'font bytes' > "$repo/res font/demo font.ttf"
    printf 'plain text\n' > "$repo/notes.txt"
    {
        printf '%s  %s\n' "$(sha256_of "$repo/libs/arm64-v8a/libdemo.a")" 'libs/arm64-v8a/libdemo.a'
        printf '%s  %s\n' "$(sha256_of "$repo/res font/demo font.ttf")" 'res font/demo font.ttf'
    } > "$repo/third_party/CHECKSUMS.sha256"
    git -C "$repo" init -q
    git -C "$repo" add .
}

# expect <status> <text that must appear in the output, or ''> <case name>
expect() {
    want_status=$1
    want_text=$2
    name=$3
    status=0
    output=$(sh "$script" "$repo" 2>&1) || status=$?
    if [ "$status" -ne "$want_status" ]; then
        echo "FAIL: $name: exit status $status, expected $want_status"
        echo "$output" | sed 's/^/    /'
        failed=$((failed + 1))
    elif [ -n "$want_text" ] && ! printf '%s\n' "$output" | grep -F -q -- "$want_text"; then
        echo "FAIL: $name: output does not contain '$want_text'"
        echo "$output" | sed 's/^/    /'
        failed=$((failed + 1))
    else
        echo "ok: $name"
    fi
}

new_repo
expect 0 '2 binaries match' 'all listed binaries match'

new_repo
printf 'new library' > "$repo/libs/arm64-v8a/libnew.so"
git -C "$repo" add libs/arm64-v8a/libnew.so
expect 1 'UNLISTED BINARY: libs/arm64-v8a/libnew.so' 'a new tracked binary without a line fails'

new_repo
printf 'jar' > "$repo/Tool.JAR"
git -C "$repo" add Tool.JAR
expect 1 'UNLISTED BINARY: Tool.JAR' 'the extension match ignores case'

new_repo
printf 'not tracked' > "$repo/scratch.bin"
expect 0 '' 'an untracked binary is ignored'

new_repo
printf 'tampered' > "$repo/libs/arm64-v8a/libdemo.a"
expect 1 'MISMATCH: libs/arm64-v8a/libdemo.a' 'a changed binary fails'

new_repo
printf 'tampered' > "$repo/res font/demo font.ttf"
expect 1 'MISMATCH: res font/demo font.ttf' 'a changed binary with a space in its path fails'

new_repo
rm "$repo/libs/arm64-v8a/libdemo.a"
expect 1 'MISSING: libs/arm64-v8a/libdemo.a' 'a listed file that is gone fails'

new_repo
printf 'ghost' > "$repo/ghost.bin"
printf '%s  %s\n' "$(sha256_of "$repo/ghost.bin")" 'ghost.bin' >> "$repo/third_party/CHECKSUMS.sha256"
expect 1 'LISTED BUT NOT TRACKED: ghost.bin' 'a line for an untracked file fails'

new_repo
head -n 1 "$repo/third_party/CHECKSUMS.sha256" >> "$repo/third_party/CHECKSUMS.sha256"
expect 1 'LISTED TWICE: libs/arm64-v8a/libdemo.a' 'a duplicate line fails'

new_repo
printf 'abc123  libs/arm64-v8a/libdemo.a\n' > "$repo/third_party/CHECKSUMS.sha256"
expect 1 'MALFORMED LINE' 'a short hash fails'

new_repo
printf '# a comment\n' >> "$repo/third_party/CHECKSUMS.sha256"
expect 1 'MALFORMED LINE' 'a line that is not a checksum fails'

new_repo
rm "$repo/third_party/CHECKSUMS.sha256"
expect 2 'not found' 'a missing checksum file is an error'

if [ "$failed" -ne 0 ]; then
    echo "verify-binaries-test: $failed case(s) failed"
    exit 1
fi
echo "verify-binaries-test: all $case_number cases passed"
