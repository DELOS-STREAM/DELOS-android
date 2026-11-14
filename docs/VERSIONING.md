# Versioning

## Policy

Delos uses semantic versioning, `MAJOR.MINOR.PATCH`.

- The first PUBLIC release will be 1.0.0. Everything before it is 0.x, reconstructed from history.
- PATCH (1.0.x, 1.1.x): hotfixes and small fixes, no new features.
- MINOR (1.x.0): bigger updates and new features.
- MAJOR (X.0.0): a redesign of the whole app (not planned).
- A version bump is a release decision: it happens only when the owner announces a release. Releases are not being made now.

## versionCode

`versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`, computed in `app/build.gradle` from the same three numbers as `versionName`. Example: 1.2.3 is 10203. Keep MINOR and PATCH below 100.

## Reconstructed 0.x versions

The 0.x versions were set retroactively on the milestones of the history (Delos' own history starts on 2026-10-19). They are not published releases. Each version ends on the commit in the table; its changes are in [CHANGELOG.md](../CHANGELOG.md).

| Version | Date | Commit | Theme |
|---|---|---|---|
| 0.1.0 | 2026-10-23 | `3ea12fbda13cd8148ea7ed34bb5c27a9ccaf20c4` | Fork baseline: Moonlight 12.2 merge, Android 10 minimum, dead-code removal, Delos branding |
| 0.2.0 | 2026-10-24 | `d42c805e8ca326a7c8374f93ba0fc8e739b5a061` | Native Delos UI, profiles, settings, stream menu, SteamGridDB artwork |
| 0.3.0 | 2026-10-24 | `ad44873390a0a8ce45194e428d8ca3d6d9e1f266` | Palettes, backgrounds, home row and on-screen controls polish |
| 0.4.0 | 2026-10-25 | `2bc3fa1c6a28d00f3c930cf21aae4258ab7d5f77` | PyroWave codec |
| 0.5.0 | 2026-10-28 | `fa06daeaed0cf9b9d0eaadfb9e7174c037046c29` | Per-game settings, SGSR upscaling, external display, help panels, Phosphor icons |
| 0.6.0 | 2026-10-30 | `c44d9e1de8588f0cad34cc7a2ff1fb54f3bcd838` | Game metadata, library filters, HDR badges, launcher icon |
| 0.7.0 | 2026-11-04 | `744b5f18f20d3c2f210c86bcedd1661a8bda9b79` | Illustrated avatars, quit and exit flow, launch transition |
| 0.8.0 | 2026-11-07 | `547438bb5e57b6ed31b4cb72878c0402cfcfa366` | Hero art, artwork page redesign, PyroWave 4:4:4, Sharpen only, settings motion |
| 0.9.0 | 2026-11-07 | `a763ea00313643b3a81a7c3de18043b409996bce` | Link guidance, latency work, SGSR verification, per-game settings redesign |
| 0.10.0 | 2026-11-09 | `51626aa17d38c32fcfc02cf375d764b3e5e84df4` | Gyro smoothing, context menu redesign, Game activity refactor begins |
| 0.11.0 | 2026-11-10 | `a5c6b5b930b9d3599d2e11914f3400deda0d4f32` | Stream defect fixes, bundled app art, diagnostics and problem reports |
| 0.12.0 | 2026-11-10 | `40a3eadaaba8fa154ccec9866de6a03f61de5d9e` | Localization in ten languages |
| 0.13.0 | 2026-11-12 | `7f5882e6e994d74a9c70baa39a3183e25f30a465` | Screen shapes, dual screen with companion pages |
| 0.14.0 | 2026-11-14 | `54be0b6d8967921e54d89cc8a55494f35402f1d7` | Variable refresh rate, new palettes and avatars, UI sounds, rumble fixes |

The last row is the state of `main` when the history was organised, the expected state before 1.0.0. Anything newer is under "Unreleased" in the changelog.

## Tags

Tags are named `vMAJOR.MINOR.PATCH` (for example `v0.4.0`) and are annotated. `tools/versioning/create-tags.sh` creates the tags v0.1.0 to v0.14.0 locally on the commits above; it is idempotent, refuses to overwrite an existing tag and pushes only with `--push`.

The local clone also has older upstream tags (v9.x, v10.x, v20.x). They belong to Moonlight and Artemis, are not Delos versions and are not pushed.

## Adding a new version

1. Only when the owner announces a release.
2. Update `versionMajor`, `versionMinor`, `versionPatch` in `app/build.gradle` (and the comment, if needed).
3. In `CHANGELOG.md`, move the "Unreleased" entries under a new heading `## [X.Y.Z] - YYYY-MM-DD`, group them Added / Changed / Fixed / Removed, and update the compare links at the bottom.
4. Commit, then create an annotated tag on that commit: `git tag -a vX.Y.Z -m "Delos X.Y.Z"` and push it.
5. Create a GitHub release only when the owner announces it.

The version shown in Settings > About, in the diagnostic report and in the problem report comes from `BuildConfig.VERSION_NAME`, so there is nothing else to edit.
