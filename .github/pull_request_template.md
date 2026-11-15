## What and why

<!-- Short description of the change and the reason for it. Link the issue if there is one. -->

## Checklist

Tick what applies; write "n/a" with the reason where a line does not apply. The rules behind the lines are in
`docs/ENGINEERING_RULES.md`.

- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passes
- [ ] Tests: a bug fix has a regression test, new logic has tests (or the body says `No test: <reason>`)
- [ ] Tested on a real device (which one and which Android version):
- [ ] UI change: screenshot attached, and it works with D-pad/stick and A/B/X/Y; no confirmation dialog in a normal flow
- [ ] Docs: the document of the changed behavior is updated, and `CHANGELOG.md` "Unreleased" for a user-facing change
- [ ] Claims: any statement about speed, quality, latency or compatibility names its measurement (device, date) or says "not tested"
- [ ] Strings: new or changed user-visible text exists in all ten languages (`docs/LOCALIZATION.md`)
- [ ] Binaries and dependencies: no new binary or dependency without its row in `docs/THIRD_PARTY.md` and its checksum
- [ ] Security: the change adds no listener, exported component, outbound endpoint or stored secret, or `SECURITY.md` is updated
- [ ] No personal data or secrets (IP addresses, keys, tokens, local paths)
- [ ] Commit subjects say what changed in at most 72 characters
