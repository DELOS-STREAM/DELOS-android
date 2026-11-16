# Versioning

## Policy

Delos follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). A version is `MAJOR.MINOR.PATCH`,
three non-negative integers without leading zeros (rule 2).

| # | Rule | SemVer rule |
|---|---|---|
| 1 | The public API of Delos is declared in "Public API" below. | 1 |
| 2 | A released version is never modified. A correction is a new version. | 3 |
| 3 | `0.y.z` is initial development: anything may change and the public API is not stable. | 4 |
| 4 | `1.0.0` is the first public release and defines the public API. | 5 |
| 5 | PATCH increases when a release holds only backward-compatible bug fixes. | 6 |
| 6 | MINOR increases when a release adds backward-compatible functionality or marks a part of the public API as deprecated. PATCH returns to 0. | 7 |
| 7 | MAJOR increases when a release changes the public API incompatibly. MINOR and PATCH return to 0. | 8 |
| 8 | A version bump is a release decision: it happens only when the owner announces a release. No release is being made now. | Owner rule |

The version 2.0.0 of the specification supersedes the earlier wording of this document, in which MAJOR meant a
redesign of the whole app. A redesign that keeps the public API compatible is a MINOR release.

## Public API

Delos is an app, not a library. Its public API is what other software, stored data and other installations
depend on. An incompatible change of any row is a MAJOR change from `1.0.0` on.

| # | Part | What is promised | Incompatible change (examples) |
|---|---|---|---|
| A1 | `art://` links | The link forms `art://host` and `art://launch` (`ShellLink`) keep their meaning and parameters. | Removing a link form or a parameter; changing what a parameter means. |
| A2 | Launch intents, launcher shortcuts and exported components | A shortcut that a released version pinned, the intent extras of the exported entry points (`ShortcutTrampoline`, `ShellActivity`), and the names and intent filters of the exported activities, services and providers of `AndroidManifest.xml` keep working. | A pinned shortcut that no longer starts its app; removing or renaming an exported component. |
| A3 | The `.art` file format | A file that a released version accepted is still accepted with the same meaning. | Removing or renaming a line of the format. |
| A4 | Stored data and backup | A newer version reads, or migrates, the data of every version since `1.0.0`: settings and profiles (shared preferences, `profiles.json`), per-app settings, the PC database and the pairing identity; what `SECURITY.md` lists as backed up is restored by a newer version. Caches (artwork, game metadata) may be rebuilt. | A format that an installed older version cannot be migrated from; an update that loses profiles, settings or pairings. |
| A5 | Host compatibility | The hosts that the README names as tested keep working: Vibepollo on Windows, reporting protocol `appversion` `7.1.431` (measured 2026-11-16 on the test host). Newer host versions that keep the protocol are expected to work. Apollo, Sunshine and Linux hosts are not part of the promise until a test is recorded. | Dropping the tested host, or a change that stops the protocol version above from working. |
| A6 | Minimum Android version | `minSdk` in `app/build.gradle` (`29`, Android 10). | Raising `minSdk`. |

Not part of the public API: the asset names of a release (a contract with the in-app updater, [Release assets](#release-assets)), the field ids of the issue form `app_report.yml` (a contract between the app and GitHub, kept by `IssueFormContractTest`), the look and layout of the screens, texts and translations, default values of
settings, the diagnostic log format, debug previews and their intent extras, everything in `tools/`, and the
internal classes of the app.

Minimum host version (decided by the lead on 2026-11-16 because the owner could not name one; the owner can veto): the promise is the tested host, not a version
number. Vibepollo reports the Sunshine protocol version in `appversion` (`7.1.431` on the test host); Delos has no
minimum above it. A minimum for Apollo and Sunshine is written here when a test with them is recorded.

## Which number increases

| Change in a release | Number | Commit mark ([ENGINEERING_RULES.md](ENGINEERING_RULES.md#9-commit-messages-and-branch-names)) | Changelog section |
|---|---|---|---|
| An incompatible change of the public API | MAJOR | `!` after the type or scope, or a `BREAKING CHANGE:` footer | Changed or Removed, with the word "Breaking" |
| New backward-compatible functionality | MINOR | `feat` | Added, or Changed for existing functionality |
| A part of the public API is deprecated | MINOR | `feat` or `refactor`, said in the body | Deprecated |
| Backward-compatible bug fixes only | PATCH | `fix` | Fixed, or Security for a vulnerability |
| No change of behavior (`docs`, `test`, `refactor`, `build`, `ci`, `chore`, `style`) | none by itself | the type | none, unless users can see it |

The highest number of all commits since the last release decides. The types state the smallest increase; the owner
may choose a larger one (rule 7 allows MINOR for substantial improvements of private code). While the version is
`0.y.z`, a breaking change increases MINOR and is still marked with `!`.

## Pre-release versions and build metadata

Not exercised: no pre-release and no build with metadata was made yet.

| # | Rule | SemVer rule |
|---|---|---|
| 1 | A pre-release is `X.Y.Z-<identifiers>`, for example `1.0.0-rc.1`. Delos uses `rc.N` with N from 1. Identifiers hold only `0-9A-Za-z-`, are not empty, and numeric ones have no leading zero. | 9 |
| 2 | A pre-release precedes its normal version: `1.0.0-rc.1 < 1.0.0-rc.2 < 1.0.0`. It may not meet the compatibility promise of its normal version. | 9, 11 |
| 3 | Build metadata is `+<identifiers>` after the version, for example `1.0.0+g912bc0d`. It is ignored for precedence. Delos release builds carry none. | 10 |
| 4 | `versionName` is the complete SemVer string, with the pre-release part and the build metadata when present. | |
| 5 | A pre-release has the `versionCode` of its normal version: `1.0.0-rc.1`, `1.0.0-rc.2` and `1.0.0` are all `10000`. | |
| 6 | The tag of a pre-release is `vX.Y.Z-rc.N`, and its GitHub release is marked as a pre-release. | |

`versionCode` has no room between two versions (`1.0.1` is `10001`), so it cannot order pre-releases. Rule 5 is the
simplest rule that is correct: Android installs an APK over one with the same `versionCode` and refuses only a lower
one, so each release candidate and the final version install over each other, and no pre-release blocks the update
to a later normal version. The cost: a tool that compares only `versionCode` does not see `1.0.0` as newer than
`1.0.0-rc.1`; the order among them is in `versionName` only. Pre-releases are therefore distributed only as GitHub
pre-releases for testing.

## versionCode

`versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`, computed in `app/build.gradle` from the same three numbers as `versionName`. Example: 1.2.3 is 10203. Keep MINOR and PATCH below 100.

## Reconstructed 0.x versions

The 0.x versions were set retroactively on the milestones of the history (Delos' own history starts on 2026-10-19). They are not published releases. They were numbered before Semantic Versioning 2.0.0 and the commit convention were adopted (2026-11-15): each milestone increased MINOR, whatever it contained. The numbers and their tags stay as they are. Each version ends on the commit in the table; its changes are in [CHANGELOG.md](../CHANGELOG.md).

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
| 0.14.0 | 2026-11-14 | `912bc0d049e54e9a7fb8b09180921d3237bce9d9` | Variable refresh rate, new palettes and avatars, UI sounds, rumble fixes |
| 0.15.0 | 2026-11-15 | tag `v0.15.0` | Streaming time, stream encryption readout, security hardening, updated delos-common-c (first version tagged when it was made) |

Rows 0.1.0 to 0.14.0 were set when the history was organized; later versions are tagged when they are made. Anything newer than the last row is under "Unreleased" in the changelog.

## Release assets

The in-app updater ([UPDATES.md](UPDATES.md)) depends on the files of a release. A release that does not follow this
contract is not offered by the updater.

| # | Rule |
|---|---|
| 1 | The tag is `vX.Y.Z` (or `vX.Y.Z-rc.N` for a pre-release); the release is published, not a draft. |
| 2 | One APK per ABI named `delos-<version>-<abi>.apk`, where `<version>` is the tag without `v` and `<abi>` is an Android ABI name: `delos-1.0.0-arm64-v8a.apk`, `delos-1.0.0-armeabi-v7a.apk`. |
| 3 | One file `SHA256SUMS.txt` with a line `<sha256>  <file name>` for every APK, as `sha256sum` writes it (lower-case hex, two spaces). |
| 4 | Every APK is signed with the release key. An update over an installed Delos needs the same key. |
| 5 | An asset is never replaced after publication (policy rule 2). A wrong asset is a new version. |

The first release candidate that is published is also the first test of this contract with the real updater. A pre-release is
not offered by the updater (GitHub's latest-release endpoint skips it); it is installed by hand.

## Tags

Tags are named `vMAJOR.MINOR.PATCH` (for example `v0.4.0`) and are annotated. The `v` is a convention of tag names; it is not part of the version: the tag is `v1.2.3`, the version is `1.2.3`. `tools/versioning/create-tags.sh` creates the tags v0.1.0 to v0.14.0 locally on the commits above; it is idempotent, refuses to overwrite an existing tag and pushes only with `--push`.

The local clone also has older upstream tags (v9.x, v10.x, v20.x). They belong to Moonlight and Artemis, are not Delos versions and are not pushed.

## Adding a new version

1. Only when the owner announces a release. The new number follows "Which number increases" above.
2. Update `versionMajor`, `versionMinor`, `versionPatch` in `app/build.gradle` (and the comment, if needed).
3. In `CHANGELOG.md`, move the "Unreleased" entries under a new heading `## [X.Y.Z] - YYYY-MM-DD`, group them under the six headings of [Keep a Changelog 1.0.0](https://keepachangelog.com/en/1.0.0/) (Added, Changed, Deprecated, Removed, Fixed, Security; a heading without entries is left out), leave an empty `## [Unreleased]` on top, and update the compare links at the bottom: a new line `[X.Y.Z]: …/compare/vPREVIOUS...vX.Y.Z` and `[Unreleased]: …/compare/vX.Y.Z...HEAD`.
4. Commit, then create an annotated tag on that commit: `git tag -a vX.Y.Z -m "Delos X.Y.Z"` and push it.
5. Create a GitHub release only when the owner announces it. **The release text must always show the complete changelog of that version**, visible directly on the release page: paste the whole `## [X.Y.Z]` section from `CHANGELOG.md` (the theme line and every list under its headings), never a shortened summary, a "see CHANGELOG.md" link alone or GitHub's auto-generated "what's changed" list instead of it. A link to the full `CHANGELOG.md` and the compare range may follow below the pasted section. (Owner rule, 2026-11-15.) A convenient way: `gh release create vX.Y.Z --title "Delos X.Y.Z" --notes-file <(awk '/^## \[X.Y.Z\]/{f=1;next} /^## \[/{f=0} f' CHANGELOG.md)`.

The version shown in Settings > About, in the diagnostic report and in the problem report comes from `BuildConfig.VERSION_NAME`, so there is nothing else to edit.

## Release checklist

Every line holds before the tag of a release is pushed. The rules behind it: [ENGINEERING_RULES.md](ENGINEERING_RULES.md), sections 5 and 7.

| # | Check | How |
|---|---|---|
| 1 | The changelog section is complete. | Read `git log vPREVIOUS..HEAD --no-merges --format=%s` line by line against the new `## [X.Y.Z]` section: nothing a user can see is missing, and the new number is the one "Which number increases" gives for the `feat`, `fix` and `!` commits of the range. |
| 2 | Every statement of a benefit is backed. | Each line of the section, of `README.md` and of the release text that says faster, smoother, sharper, lower latency or "works with" has its evidence in `docs/` (device, Android version, host, date, number or observation) or says "not tested". |
| 3 | The "Not verified on real hardware yet" paragraph at the top of `CHANGELOG.md` is current. | It names every feature without a device test, the test device and the tested host. It is pasted under the changelog section on the release page. |
| 4 | In-app texts of untested features say so. | Read the help texts of those features in `app/src/main/res/values/strings*.xml`. |
| 5 | Comparisons with other apps are re-checked. | Repeat the check, update its date in the text. |
| 6 | Public links work. | `python3 tools/docs/check_links.py`; open every external link of `README.md`. The website must show the Delos site, not a hosting placeholder. |
| 7 | Texts that depend on the release state are updated. | "no releases yet" in `README.md`, `CONTRIBUTING.md`, `SECURITY.md` and `.github/ISSUE_TEMPLATE/bug_report.yml`. |
| 8 | The build is verified on the commit that gets the tag. | The local run of the commands in [BUILDING.md](BUILDING.md) is green on that commit: unit tests, lint, debug and release build, binary checksums, links. CI repeats them when the tag is pushed (line 12). |
| 9 | The release APKs are the real thing. | Built with `DELOS_METADATA_URL` and `DELOS_REPORTS_URL` set, signed with the release key, installed once on a device: pair, open the library, settings, artwork, start and end a stream. A file with the SHA-256 of each APK is attached to the release. |
| 10 | Bundled libraries are current. | Compare the versions of OpenSSL, Opus and the `moonlight-common-c` pin in [THIRD_PARTY.md](THIRD_PARTY.md) with their upstreams (`git ls-remote --tags`); record what is behind and why. |
| 11 | Issues are tidy. | `gh issue list --search "is:open no:milestone"` and `--search "is:open no:label"` are empty; the milestone of the release is closed. |
| 12 | CI runs for the release. | The commit that gets the tag has no `[skip ci]` line. Pushing the tag `vX.Y.Z` starts the Build workflow; the release is published only after that run is green. This applies to every public release, starting with 1.0.0. The workflow has no manual start. A tag cannot be moved or deleted: when the run fails, the fix is released under the next PATCH number. A release candidate tag (`vX.Y.Z-rc.N`) runs the same workflow first. |
| 12 | The release text is the complete changelog section. | Step 5 of "Adding a new version" above. |
| 13 | The release assets follow the contract. | [Release assets](#release-assets): both APKs and `SHA256SUMS.txt` are attached, each APK has its line, and `sha256sum -c SHA256SUMS.txt` passes in the download folder. |

Not defined yet: who holds the release signing key and how it is backed up. No release is made before that is written here.
