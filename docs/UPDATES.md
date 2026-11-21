# In-app updates

## Purpose

Delos checks the latest GitHub release of its own repository, tells the user in the app that a newer version exists,
and, when the user says yes, downloads the APK, verifies it and hands it to the Android installer. This document
specifies what is requested, when, what is verified before anything is installed, what the user sees, and what is not
verified on a device. Issue: [#84](https://github.com/DELOS-STREAM/DELOS-android/issues/84).

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The check, its schedule and its setting | The security model and the list of outbound endpoints: [SECURITY.md](../SECURITY.md) |
| The release asset contract the app depends on | Version numbers and the release checklist: [VERSIONING.md](VERSIONING.md) |
| Download, checksum, install, the offer card | The look rules of the cards: [gui/MOTION_AND_COLORS.md](gui/MOTION_AND_COLORS.md) |
| Debug-build testing against a local server | Strings and translation: [LOCALIZATION.md](LOCALIZATION.md) |

## Behavior

### Check

1. The request is `GET https://api.github.com/repos/DELOS-STREAM/DELOS-android/releases/latest`. The endpoint never returns drafts or
   pre-releases; the parser ignores them as well.
2. The request carries `User-Agent: Delos/<versionName>` and `Accept: application/vnd.github+json`. It carries no token, no
   account, no device identifier. GitHub sees the IP address of the device. Nothing else is sent.
3. The answer is cached with its `ETag`. A later check sends `If-None-Match`; `304 Not Modified` costs no API quota (GitHub
   allows 60 unauthenticated requests per hour per address).
4. The check runs when the shell starts and the last successful check is older than 24 hours, and when the user presses
   **Check now** in Settings > About. Within 24 hours the cached answer is evaluated again without a request.
5. A failed check is silent in the background and logged to `DiagLog` (tag `Update`). **Check now** shows the error in its
   row: no connection, rate limit, unusable answer. A failed check is not remembered, so the next start asks again.
6. With **Check for updates** off the updater makes no request at all, including **Check now**, which is disabled.
7. A debug build (application ID suffix `.debug`) never offers an update: the release APK is another app. See "Testing".

### Versions

1. The tag must be `vMAJOR.MINOR.PATCH[-prerelease]`. A tag that does not parse is ignored and logged.
2. The tag is compared with `BuildConfig.VERSION_NAME` by SemVer 2.0.0 precedence (`SemVer`): `1.0.0-rc.1 < 1.0.0`,
   `1.10.0 > 1.9.0`, build metadata is ignored.
3. Only a strictly newer version is offered. The comparison is repeated on every evaluation, so the offer ends when the
   installed version catches up.

### Release assets

The contract is in [VERSIONING.md](VERSIONING.md#release-assets). The app picks `delos-<version>-<abi>.apk` for the first
entry of `Build.SUPPORTED_ABIS` that the release has, and the line of that file in `SHA256SUMS.txt`. A release without a
matching APK, without `SHA256SUMS.txt`, without a line for the APK, or whose APK is empty or larger than 250 MB
(`UpdateHttp.MAX_APK_BYTES`) is not offered; the reason is logged and **Check now** says the version has no download for
this device. The checksum file is requested only when a newer release with the APK exists.

### Offer card

`shell/ui/UpdateOfferCard.kt` is built from the parts of the problem report card (`ProblemOfferCard`), centred on the screen
with the same 936 px width over a real blur of what lies under it: the glass card, accent disc, title and sentence, `PillButton`s without controller glyphs and the same pop-in motion. While it shows, the D-pad stays on its buttons. It is not a
dialog: the screen behind is dimmed a little (`OfferScrim`) and stays usable by touch; the card has the deep shadow of the game info panels (`offerShadow`). No new visual element was added except a thin progress bar built from the card's own colors.

| Rule | Value |
|---|---|
| Where | The start screen and the home screen; an offer found by **Check now** also shows over Settings at once (`UpdateController.requested`). Never over a layer, the portal, the launch loader or the stream result, never during a stream, and not while the problem card is shown (it comes first) |
| Title | `Delos <version> is available` |
| Text | The first three lines of the release notes as plain text (`ReleaseNotes`) |
| A | **Update**; focus starts here |
| B | **Later**: hides the offer of this version for 3 days (`UpdateSchedule.LATER_MS`); a newer version shows at once |
| Skip this version | Not included: a third button does not fit the two-button pattern of the card without a redesign |

States of the card:

| State | Card | A | B |
|---|---|---|---|
| Offer | Title and notes | Update | Later |
| Needs permission | One line: Android must allow Delos to install updates | Open settings | Later |
| Downloading | Progress bar and percent | | Cancel (stops, deletes the partial file, back to the offer) |
| Verified, installing | One line: Android is installing | | |
| Failed | One sentence for the cause | Try again (not for a signature failure) | Later |

The card does not appear on the companion screen of a dual-screen device.

### Download, verification, install

1. **Update** checks `PackageManager.canRequestPackageInstalls()`. When "install unknown apps" is not allowed for Delos, the
   card says so in one line and **Open settings** opens `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for Delos. When the
   shell resumes and the permission is allowed, the update goes on by itself.
2. The APK is downloaded with OkHttp to `cache/updates/`. Only HTTPS is accepted, also after redirects, which are followed by
   hand, at most five. The size is limited to 250 MB while reading, not only by the announced length. The file name must
   match `[A-Za-z0-9][A-Za-z0-9._-]*.apk`.
3. The SHA-256 of the file is compared with the line of `SHA256SUMS.txt` of the same release, read when the check found the
   release. A mismatch deletes the file, logs it, shows an error, and installs nothing.
4. The verified file goes to the installer only while the card is shown, so Android's prompt never lands on a stream or the
   launch loader. A `PackageInstaller` session writes the file; on Android 12 and later the session sets
   `USER_ACTION_NOT_REQUIRED`, which Android honors only where it allows it. Otherwise Android shows its own confirmation.
5. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (an APK signed with another key) is shown as: Android refused the update, it is not
   signed with the same key as the installed Delos. The signature check is Android's; Delos does not replace it.
6. The cached APK is deleted after every outcome and at the next process start.

An update is never installed without the user pressing **Update** and without Android's own installer.

### Obtainium

Obtainium reads the same GitHub releases and the same signing key. Both can be used on one device: whichever installs first,
the other sees the same version as installed. Nothing in the updater depends on Obtainium.

### Testing

A debug build offers nothing unless a test server is set: Settings > About > Updates > **Update test server** (debug builds
only) takes the URL of a file with the JSON of a release (the shape of GitHub's `releases/latest` answer). Plain HTTP is
accepted only for that URL, only in a debug build. The asset URLs in the JSON can point to the same server. The release APK
installs as a separate app because its application ID has no `.debug` suffix.

## Settings

| Key | Where | Values | Default |
|---|---|---|---|
| `update_check_enabled` | Settings > About > Updates > Check for updates, device-wide, default preferences | on / off | on |
| `update_last_check` | internal | epoch milliseconds of the last successful check | 0 |
| `update_etag`, `update_cached_latest` | internal | the last answer reduced to the tag and the candidate | none |
| `update_later_tag`, `update_later_until` | internal | the version "Later" was pressed for, and until when | none |
| `update_debug_server` | debug builds only | URL of a release JSON | none |

The row **Check now** is next to the switch; its value shows the last result: Up to date, New version X, no download for this
device, or the error.

## Code map

| Path | Role |
|---|---|
| `update/SemVer.kt` | Version parsing and precedence |
| `update/Release.kt` | GitHub answer parsing, release notes as plain text |
| `update/ReleaseAssets.kt` | APK and checksum selection by ABI, `SHA256SUMS.txt` parsing and verification |
| `update/UpdateCandidate.kt` | The offered release and the cached answer |
| `update/UpdateSchedule.kt` | 24-hour check interval, 3-day "Later" |
| `update/UpdateStore.kt` | Preferences |
| `update/UpdateHttp.kt` | HTTPS-only client, redirects, size limits, download |
| `update/UpdateChecker.kt` | The check: setting, schedule, ETag, asset resolution |
| `update/UpdateController.kt` | State machine: offer, permission, download, verify, install result |
| `update/PackageApkInstaller.kt`, `update/UpdateInstallReceiver.kt` | `PackageInstaller` session and its result |
| `update/Updates.kt` | The process-wide controller |
| `shell/ui/UpdateOfferCard.kt`, `shell/Shell.kt` | The card and its hook |
| `shell/screens/NativeSettings.kt`, `shell/screens/UpdateStatusText.kt` | The About rows |
| `res/values*/strings_updates.xml` | Texts in ten languages |

## Verification

Unit tests (`app/src/test/java/com/limelight/update/`, `shell/ui/UpdateOfferCardTest.kt`), run with
`./gradlew :app:testDebugUnitTest`:

| Test | Covers |
|---|---|
| `SemVerTest` | Precedence, the specification's own example, build metadata, invalid strings |
| `ReleaseParserTest` | A realistic GitHub answer, drafts, pre-releases, bad tags, garbage, notes as plain text |
| `ReleaseAssetsTest` | ABI selection, missing APK or checksum file, checksum lines, verification: ok, mismatch, missing |
| `UpdateScheduleTest` | 24 hours, a clock set back, "Later" for 3 days, a newer version |
| `UpdateCheckerTest` | Default on, off means no request, 24 hours, ETag and 304, forced check, errors, debug build, size cap |
| `UpdateHttpTest` | HTTPS only, redirects to HTTPS only, redirect limit, size cap by length and while reading |
| `UpdateControllerTest` | Offer, "Later", permission, download, checksum mismatch, cancel, install outcomes |
| `UpdateStoreTest` | Defaults and stored values |
| `UpdateOfferCardTest` | Card buttons per state, progress, failure texts, the Check now texts |

The network is a fake `OkHttpClient` interceptor; no test makes a request.

### Evidence (2026-11-16)

AYN Odin 2 Portal Max, Android 13, release 1.0.0 published on GitHub: a signed 0.15.0 test build (the same code with a lower
version, signed with the release key) was installed over the existing data. **Check now** offered DELOS 1.0.0, **Update**
downloaded the real `delos-1.0.0-arm64-v8a.apk` from GitHub, verified it and handed it to Android's installer; after the
system prompt the app was at 1.0.0 with its settings and pairing kept. The owner ran the flow with the controller.

### Not verified

- The redirect to "install unknown apps" on a device where DELOS did not have that permission yet, and the resume after returning.
- Whether `USER_ACTION_NOT_REQUIRED` suppresses Android's prompt for a later update.
- The status and message the system reports for an APK signed with another key (the mapping to the signature message is
  from the documented `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- The card on every screen shape and on the dual-screen device.
- The `armeabi-v7a` asset, and updating over an APK installed by Obtainium.

## Related

- [VERSIONING.md](VERSIONING.md): tags, release checklist.
- [SECURITY.md](../SECURITY.md): outbound endpoints.
- [DIAGNOSTICS.md](DIAGNOSTICS.md): the log the updater writes to.
