#!/bin/sh
# Tests of tools/hooks/commit-msg:  sh tools/hooks/test-commit-msg.sh  (exit 0: all passed).

hook="$(cd "$(dirname "$0")" && pwd)/commit-msg"
work=$(mktemp -d) || exit 2
trap 'rm -rf "$work"' EXIT
failed=0
count=0

# expect <accept|reject> <name> <message>
expect() {
    count=$((count + 1))
    printf '%s' "$3" > "$work/message"
    if sh "$hook" "$work/message" 2> "$work/error"; then got=accept; else got=reject; fi
    if [ "$got" != "$1" ]; then
        failed=$((failed + 1))
        echo "FAILED: $2: expected $1, got $got"
        sed 's/^/    /' "$work/error"
    fi
}

seventy_two="Stream menu: the bitrate page keeps its value after an error (#123) abcd"
seventy_three="${seventy_two}f"
[ ${#seventy_two} -eq 72 ] || { echo "test setup: the 72-character subject has ${#seventy_two}"; exit 2; }

expect accept "subject only" "Docs: add the build guide"
expect accept "subject, empty line, body" "Docs: add the build guide

Replaces the two workstation notes.

[skip ci]"
expect accept "subject of exactly 72 characters" "$seventy_two"
expect reject "subject of 73 characters" "$seventy_three"
expect reject "empty message" ""
expect reject "blank subject" "   

body"
expect reject "only comments" "# Please enter the commit message
# On branch main"
expect accept "comment lines do not count" "# a comment first
Docs: add the build guide
# another comment

Body."
expect reject "WIP subject" "WIP"
expect reject "WIP prefix" "WIP: stream menu"
expect reject "wip in lower case" "wip stream menu"
expect reject "WIP inside the subject" "Stream menu (WIP)"
expect reject "fixup commit" "fixup! Docs: add the build guide"
expect reject "squash commit" "squash! Docs: add the build guide"
expect accept "a word that only contains wip" "Swipe: follow the finger"
expect reject "body directly under the subject" "Docs: add the build guide
Replaces the two workstation notes."
expect reject "[skip ci] in the subject" "Docs: add the build guide [skip ci]"
expect accept "long merge subject written by git" "Merge branch 'feature/a-very-long-branch-name-that-goes-on-and-on-and-on' into main-line"
expect accept "long revert subject written by git" "Revert \"Stream menu: the bitrate page keeps its value after an error (#123) abcde\"

This reverts commit 0123456."
expect accept "text below the scissors line is ignored" "Docs: add the build guide

Body.
# ------------------------ >8 ------------------------
# Do not modify or remove the line above.
diff --git a/x b/x
+a line that is not part of the message"

if [ "$failed" -eq 0 ]; then
    echo "commit-msg: $count cases passed"
else
    echo "commit-msg: $failed of $count cases failed"
    exit 1
fi
