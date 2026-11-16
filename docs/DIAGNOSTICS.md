# Diagnostics: logging, crash capture and problem reports

## Purpose

Delos keeps a small log of what it did, on the device, so that a problem can be reported with facts instead of memories.
**Nothing leaves the device unless the user starts it**: there is no analytics, no automatic upload, no account and no token
anywhere in this feature. What the user starts is either **Report** (the cleaned log goes to the Delos report service and a
GitHub draft opens), **Save the log to Downloads instead**, or **Share**. This document specifies what is logged, what is removed
before anything is shared or uploaded, what the report service stores, how a crash is detected and offered for reporting, and the
contract between the app and the GitHub issue forms.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The rolling log, its sources and its switch | The look of the report card and of the Debug info screen: [GUI.md](GUI.md) |
| The scrubber and the device block | The security model as a whole: [SECURITY.md](../SECURITY.md) |
| Sharing, crash capture, the report offer, the report service as the app uses it, the issue forms | The service itself (API, limits, deploy): [tools/report-worker/README.md](../tools/report-worker/README.md) |
| | The `ControllerSend` and rumble diagnostic lines: [CONTROLLER_STATE.md](CONTROLLER_STATE.md), [RUMBLE.md](RUMBLE.md) |

## What is logged

A rolling log in the app's private storage (`files/diagnostics/diag-0.log` and `diag-1.log`):

- **Two files of at most 1 MiB each**. When the current file would grow past that, the older one is deleted and the current one becomes the
  older one, so the log holds between 1 and 2 MiB of the newest lines (a few thousand lines).
- **INFO and above in a release build, DEBUG as well in a debug build.** A line is `2026-11-10 12:34:56.789 I/Stream [main] message`, in the
  device's time zone.
- **A writer thread** (`DiagLogWriter`, minimum priority) does all disk work. `DiagLog.log` formats one line and offers it to a queue of 1024;
  when the queue is full the line is dropped (a later line says how many) instead of blocking the stream, render or input thread. Before
  `DiagLog.init`, or with the switch off, a call does nothing.
- **Switch:** Settings → About → **Keep diagnostic logs** (default on, device-wide, key `diag_logs_enabled` in the default preferences). Off stops
  new lines, including the crash file; what is on the disk stays until it is cleared (Debug info → Clear diagnostic logs).

Sources, all into the same sink:

| Source | What |
|---|---|
| `LimeLog` (`info` / `warning` / `severe`) | the existing Java log: connection, display mode, decoder recovery and failure, the client id line, pairing, ... (tag `Lime`) |
| native log callback | `BridgeClLogMessage` (moonlight-common-c) and the PyroWave renderer / YUV copy / thread tuning lines, through `DelosDiagLog` in `callbacks.c` and `MoonBridge.bridgeNativeLog`; limited to 60 lines per second, the text crosses JNI as bytes (never `NewStringUTF` on foreign text) |
| stream events | stage starting, stream started and stopped, **negotiated codec / chroma / HDR, size, rate and bitrate** (`StreamEvents`), connection quality changes, the stream result (`Stream ended: ...`) |
| `DelosPyroDiag` | the PyroWave start line and **one periodic line every 30 s** (logcat of a debug build still gets every one), UDP drop counters, SGSR overload and optical-flow governor events, the native swapchain and present-mode lines |
| toasts | every **warning and error** toast of the shell and of the stream window (tag `Toast`) |
| displays | one line per Android display at process start and one per display added, changed or removed (tag `Display`; `DisplayInventory`): id, name, size, density, refresh rate, state, flags and Delos's classification with its reason. The name is the system's device name ("Built-in Screen", "HDMI Screen", "Overlay #1"); a wireless display's name is replaced by "wireless display". See docs/DUAL_SCREEN.md |
| crashes | the uncaught exception with its stack trace (tag `Crash`), a decoder crash that takes the app down, the `Problem` lines of the offer |

## What the scrubber removes

`LogScrubber` (pure, `LogScrubberTest`) runs over the **whole text** of every report, summary and prefilled issue address after it is built
(the file on the disk stays raw: it is private to the app). It errs on the side of removing: a harmless version number with four dotted
numbers is replaced too. Names and addresses get a number, the same for the same value within one report (`<ip-1>`, `<pc-2>`), so the log
stays readable.

| Rule | Replaced by |
|---|---|
| Names of the PCs: their own and the ones given in Delos, read from the stored PC list (and each profile's `ui_pc_name_*`) at export time; whole words, case-insensitive, at least 3 characters | `<pc-N>` |
| Addresses and host names stored for the PCs (`ComputerDetails`) | `<host>` |
| Profile names and the device's own name (`Settings.Global.DEVICE_NAME`) | `<name>` |
| The client id (`uniqueid` file), the Android id, profile ids, PC uuids | `<id>` |
| The SteamGridDB API key (known value) | `<secret>` |
| IPv4 addresses (every octet 0-255) and IPv6 addresses (full, compressed, zone, IPv4-mapped, bracketed) | `<ip-N>` |
| MAC addresses (`:` or `-`) | `<mac-N>` |
| URLs: user info removed, host replaced (public services such as steamgriddb.com and github.com are kept), **every query value replaced** except small numbers, resolutions and booleans (`?devicename=<redacted>&uniqueid=<redacted>`), fragment dropped | the same URL with `<ip-N>` / `<pc-N>` / `<host>` |
| `key=value` or `key: value` for ids, tokens, secrets, `password`, `pin`, `authorization`, `serial`, `imei`, `hostname`, `user name`, `rikey`, ... and `Bearer` / `Basic` credentials | the value becomes `<redacted>` |
| Wi-Fi names (`SSID`, `BSSID` up to the end of the line or comma) | `<redacted>` |
| `UnknownHostException: x`, `Unable to resolve host "x"` | `<host>` |
| UUIDs, hexadecimal runs of 16 characters or more (client ids, fingerprints, keys), tokens of 32 or more letters, digits, `-` and `_` | `<uuid>`, `<hex>`, `<token>` |
| File paths with a user name (`/Users/x`, `/home/x`, `C:\Users\x`) and external volume ids (`/storage/ABCD-1234`) | `<user>`, `<volume>` |
| E-mail addresses; host names ending in `.local`, `.lan`, `.home`, `.internal`, `.ts.net`, `.duckdns.org`, ... | `<email>`, `<host>` |

`LogScrubberTest` covers each rule with lines as the app logs them (the parallel poll line, the `applist?devicename=...&uniqueid=...` request, the
`UID is now:` line, IPv6 with a zone, WoL MAC addresses, SSIDs) and that times, ports and ordinary lines stay unchanged and that scrubbing twice
changes nothing more. The device block (`DeviceInfo`) is built from a fixed list of facts and a fixed list of settings
(`StreamSettingsSnapshot.KEYS`): never the whole preference file.

## The device block

Model, manufacturer and device name, Android release / API / security patch / build id, SoC (`Build.SOC_MODEL` on Android 12+, else the hardware
name), ABI, GPU (the GL renderer the stream window last stored; the Vulkan device name is in the PyroWave lines of the log), the default
display's size, refresh rate and density, the kind of network (Wi-Fi, Ethernet, ...; never its name), locale, the UTC offset (not the region),
the Delos version and build type, and the stream settings: codec, resolution, frame rate, bitrate, PyroWave chroma, HDR, frame pacing and the
upscaler (mode, sharpness, supersampling). No PC names, addresses, tokens, API keys or game list.

## Sharing from Debug info

Settings → About → Debug info → **Diagnostics** (the first section after the device rows):

- **Diagnostic log**: an info row showing the size and line count of exactly what Share sends (for example `212 KB, 1,904 log lines`); the panel on
  the right previews the header, the device block and the newest 40 lines of the **scrubbed** text, built when the screen opens and after Clear.
- **Report a problem** and **Save the log to Downloads instead**: only in a build that has the report service (`DELOS_REPORTS_URL`). They start the same
  report as the card, without a detected problem: the first uploads the cleaned log and opens the GitHub draft with its link, the second saves it to
  Downloads and opens the draft for the user to attach it. The row help of **Report a problem** states what happens (the text of the card below).
- **Share diagnostic log**: writes that text to `cache/diagnostics/delos-diagnostic-<time>.txt` (older shared files are removed first), and opens
  the Android share sheet through the app's `FileProvider` (`provider_file_paths.xml`, `cache-path diagnostics`), with read access for that one file.
- **Copy diagnostic summary**: the device block and the last 100 lines, scrubbed, to the clipboard (a toast confirms it).
- **Clear diagnostic logs**: deletes both log files and the shared copy.
- **Trigger test crash**: only in debug builds (`BuildConfig.DEBUG`, the row is never added otherwise).

All of it is built on a worker thread from the PC list as it is at that moment.

## Crash capture and the report offer

**Markers** (all in `files/diagnostics`, nothing else is kept):

- `crash.txt` + `crash.marker`: the **uncaught-exception handler** (`CrashReporter`, chained to the previous handler, which still ends the process)
  logs the exception, flushes the log and writes `crash.txt` (exception, thread, process, the stack trace and the **last 200 log lines**, raw; format
  and parser in `CrashFile`), then `crash.marker`. A file without its marker is an interrupted write and is ignored. With the switch off nothing is
  written.
- `session.marker`: `foreground|background pid startedAt updatedAt`, written when the first activity starts and when the last one stops. A process
  that ended while the state was `foreground` ended abnormally. This is the only signal on **API 29**; from API 30 it is ignored.
- `ActivityManager.getHistoricalProcessExitReasons` (API 30+): `REASON_CRASH_NATIVE` (native crash), `REASON_ANR`, `REASON_CRASH` and
  `REASON_INITIALIZATION_FAILURE` of this app's main process, with the system's description (for example `signal 11 (SIGSEGV)`).

**Detection** (`ProblemDetector`, pure, `ProblemDetectorTest`), at the first start of the shell after the problem:

| Case | Result |
|---|---|
| nothing new | no offer |
| Java crash marker | `JAVA_CRASH` with the summary `IllegalStateException: message (at com.limelight.Foo.bar)` and the stack trace |
| native crash or ANR exit reason newer than the last offer | `NATIVE_CRASH` / `ANR` with the system's description |
| a `REASON_CRASH` exit of the same process as the crash file | the same crash (the file wins), never a second offer |
| a `REASON_CRASH` without a crash file | `JAVA_CRASH` with the system's description |
| API 29, previous process left `foreground` and no crash file | `UNCLEAN_EXIT` |
| other exit reasons (user, low memory, update), a process that ended in the background | no problem |
| several at once | one offer: Java crash, then native, ANR, unclean exit; the newest of a kind; every source looked at counts as offered |
| signals older than 7 days | dropped |
| a stream result `FAILED_TO_START` / `CONNECTION_LOST` (the secondary entry point; the result screen is unchanged) | `STREAM_FAILED`, at most one offer per 30 minutes |

**Once per problem:** the ledger (`diag_problems` preferences: the newest crash time, exit time and session start offered, and the last stream offer)
is stored at detection, so a problem is never offered again, whether it was reported, dismissed or ignored.

**The card** (`ProblemOfferCard`, docs/GUI.md): top right of the start screen and the home screen, below the toast's place, only when no layer,
portal, launch loader or stream result is up. It says what happened in one sentence, the one-line summary and a note. In a build **with** the report
service the note is: *Report sends a cleaned log to the Delos report service (kept 30 days, readable by anyone who has the link) and opens a GitHub
draft for you to check. Nothing is sent before you choose.* In a build **without** it: *Nothing is sent automatically. Report opens GitHub in your
browser with a draft for you to check, and saves a cleaned log file to Downloads for you to attach.* Focus starts on **Report this problem** (A);
left/right moves to **Dismiss**; **B dismisses** from anywhere while the card shows. Touch works too. With the report service a second row has the
quiet action **Save the log to Downloads instead** (a pill without a button glyph, reached with down). There is no scrim and no confirmation: the rest
of the screen stays usable. The card on the second panel of a dual-screen device has Report and Dismiss only and the same note. `Problem` carries typed
facts (kind, subject, detail, whether only the exit record told of it); the card text is built in the user's language from `diag_problem_*`
(`problemSentence`, `problemSummary` in `ProblemOfferCard.kt`), the subject (exception, signal, system description) shown as it is. `Problem.summary`,
the issue draft and the report stay English for the developers.

**Report this problem** (`ProblemReporter.report`, mode `ReportMode.UPLOAD`):

1. builds the scrubbed report (header, device block, a `Problem` section with the full stack trace, the whole log);
2. with the report service: shows the toast `Uploading the cleaned log…`, uploads the report (see "The report service") and builds the prefilled
   address with the returned link; a toast says `Log uploaded; its link is in the draft. It is kept for 30 days.` Without the service, or when the upload
   fails, or for **Save the log to Downloads instead** (`ReportMode.DOWNLOADS`, nothing is uploaded): saves the report to **Downloads** as
   `delos-diagnostic-<time>.txt` through `MediaStore.Downloads` (no permission needed); a toast says `Log saved to Downloads as ... Attach it to the issue.`
   after a choice or an old build, `The log was not uploaded (<reason>). Saved to Downloads as ... Attach it to the issue.` after a failed upload (the
   reasons: no connection, no answer in time, the service is busy, the log is too large, the service did not accept it), or that it could not be saved
   and that Debug info can share it;
3. after 0.9 s opens the **GitHub new-issue page in the browser** with the `app_report.yml` form filled in through query parameters named after
   the field ids: `title`, `what-happened` (after an upload `Diagnostic log: <link>` on the first line, then what happened and a request to add what the
   user was doing, then a line saying that the log was uploaded, is readable by anyone who has the link and works until its expiry date; after a save
   the file name to attach), `area` (`Other`, or `Pairing/connection` for a failed stream), `device`, `build`, `stream-settings`, `logs` (the summary and
   the first 12 stack frames). The address carries no `labels` parameter: the form sets the labels `bug`, `from-app` and `needs-triage` itself (they
   **must exist** in the repository);
4. resolves the problem (the crash files are deleted).

The address stays below 6000 characters: `logs` is cut first (a tenth at a time, with a note that the file has the rest), then `what-happened`; every
free-text field passes the scrubber; a cut is never inside a percent escape (`IssueUrl`, `IssueUrlTest`, `ProblemIssueTest`). The link and the note about
where the log is (`IssueDraft.logLink`, `IssueDraft.attachmentNote`) are added after the scrubber and are never cut: the scrubber replaces the host of an
address it does not know, and it takes a file name such as `delos-diagnostic-20261110-120000.txt` (32 letters, digits and hyphens) for a token. After a
save the user attaches the file in the browser: drag it into the **What happened?** box (a textarea with `render:` cannot take files, so the log box at
the end cannot).

**Dismiss** resolves the problem and returns the focus to the screen.

## The report service

The service is the Cloudflare Worker `delos-reports` ([tools/report-worker/README.md](../tools/report-worker/README.md)), a small program that stores a text
behind an unguessable link. The app uses it only when the build has its address (`DELOS_REPORTS_URL`, [BUILDING.md](BUILDING.md)); without it the
report flow is the one that saves to Downloads, and the card, the rows and the texts say so.

| Fact | Value |
|---|---|
| Who starts it | The user, per report, with **Report** on the card or in Debug info. There is no setting that uploads by itself and nothing is uploaded at a crash |
| What is uploaded | The text that **Save the log to Downloads instead** saves: the report after the scrubber (header, device block, problem section, log), as `text/plain; charset=utf-8`, gzip-compressed. The service accepts 128 KiB (`ReportUploader.MAX_BYTES`, the Worker's `MAX_BYTES`; a test checks that they agree): a larger report keeps the header, the device block and the problem section whole and loses its **oldest** log lines (`ReportUploader.fit`), so the uploaded log can be shorter than the file in Downloads |
| What the service does with it | Checks it, runs the pattern rules of the scrubber over it again (a JavaScript port, `scrub.js`), replaces a leftover with the same placeholder and stores the result in Workers KV under a random 128-bit id |
| What it stores | The cleaned text for 30 days (`ReportUploader.KEPT_DAYS`) and a counter of the uploads of the day. The Worker keeps no address, no user agent, no device id and no account, and logs none of them; Cloudflare, which runs it, handles the connection and sees the sender's IP address like any web host |
| Who can read it | Anyone who has the link, until it expires. The link goes into the GitHub draft, so it is **public** once the user submits the issue in a public repository. Nothing lists reports and nothing can delete one from the app |
| What the app accepts back | Only `<service address>/r/<22 characters>` with the id that the answer names; any other link is a failed upload (`ReportUploader.parse`) |
| Address | HTTPS only (loopback for tests); no redirect is followed |
| If it fails | Offline, a timeout (20 s without an answer, 30 s in all), 429, the daily cap, 413, any 5xx or an answer that is not the service's: the report is saved to Downloads as before, with a toast naming the reason (`UploadFailure`) |

**Scrubbed twice.** The app's scrubber knows the user's PC names, host names, profile names, client id and API key and removes them, together with what
the patterns find. The service cannot know those; it only repeats the patterns. A leftover is replaced and counted (`rescrubbed` in the answer; the app
logs the count, never the text). `ScrubContractTest` writes `tools/report-worker/test/fixtures/app-scrub-contract.json` from the app's scrubber and the
Worker's tests read it, so the two cannot drift apart: the Worker must leave the app's output unchanged and must produce it from raw lines that need no
knowledge of the install.

**What the user is told**, in all ten languages: the note of the card and of the **Report a problem** row (sends a cleaned log to the Delos report service,
kept 30 days, readable by anyone who has the link, opens a GitHub draft to check, nothing is sent before the user chooses), the toasts above, and the
form's own introduction (`.github/ISSUE_TEMPLATE/app_report.yml`).

## Field-id contract of the issue forms

The address that "Report this problem" opens names a form file (`template=`) and fills it in through query parameters named after
the form's field ids. An installed app keeps sending the same names, so the names are a contract between every app build and
`.github/ISSUE_TEMPLATE/`.

| What the app sends (`IssueUrl.build`) | Must exist in the form |
|---|---|
| `template=` the value of `IssueUrl.TEMPLATE` (`app_report.yml`) | A form file with exactly that name |
| `title` | Nothing: GitHub's own parameter |
| No `labels` parameter | The form's own `labels:` with `bug` and `from-app`. GitHub accepts `labels` in the address only from people who may label issues and documents a "404 Not Found" page for everyone else, so the form labels every report itself |
| `what-happened`, `device`, `build`, `stream-settings`, `logs` | Fields with these ids that take text |
| `area` with the value `Other` or `Pairing/connection` | A dropdown with the id `area` that offers both options, spelled exactly so |

Two forms carry these ids:

| Form | For | Notes |
|---|---|---|
| `bug_report.yml` | People who write a report by hand, and app builds whose `IssueUrl.TEMPLATE` was `bug_report.yml` (they also sent `labels=from-app`) | Has more required fields than the app fills in |
| `app_report.yml` | The app | Only "What happened?" and one confirmation checkbox are required; it sets the labels `bug` and `from-app` itself |

Rules, repeated in the comment at the top of both forms:

1. A form file that an app build names is never renamed or removed.
2. The ids `what-happened`, `area`, `device`, `build`, `stream-settings` and `logs` and the two `area` options are never renamed or removed.
3. A field that the app fills in keeps its type. New fields are optional.
4. `IssueUrlTest` and `ProblemIssueTest` check the address that the app builds. `IssueFormContractTest` reads both form files and fails
   when the file that `IssueUrl.TEMPLATE` names is missing, when a field id that the app sends is missing or has another type, when an
   `area` option that the app sends is missing, when `app_report.yml` requires a field that the app leaves empty, or when it no longer
   sets the labels `bug` and `from-app`.

## Code map

| Path (under `app/src/main/java/com/limelight/`) | Role |
|---|---|
| `diag/DiagLog.kt`, `diag/DiagLogWriter.kt` | The log call, the queue and the writer thread |
| `diag/StreamEvents.kt`, `diag/StreamSettingsSnapshot.kt` | Stream lines and the settings of the device block |
| `diag/LogScrubber.kt`, `diag/ScrubContextCollector.kt` | The scrubber and the known names, addresses and secrets it is given |
| `diag/DeviceInfo.kt`, `diag/DeviceInfoCollector.kt`, `diag/DiagnosticReport.kt`, `diag/DiagExport.kt` | The device block, the report text, sharing |
| `diag/CrashReporter.kt`, `diag/CrashFile.kt`, `diag/CrashText.kt` | The uncaught-exception handler and the crash file |
| `diag/ProblemDetector.kt`, `diag/ProblemMonitor.kt` | Detection at the next start, the ledger |
| `diag/ProblemReporter.kt`, `diag/ProblemIssue.kt`, `diag/IssueUrl.kt` | The report, the issue draft and the prefilled address |
| `diag/ReportUploader.kt`, `diag/ReportSubmission.kt`, `diag/ReportOutcomeText.kt` | The upload to the report service, the choice between upload and Downloads, the toasts |
| `shell/ui/ProblemOfferCard.kt`, `shell/companion/CompanionFlowPages.kt` | The card on the main display and on the second panel |
| `shell/screens/DebugInfoScreen.kt` | The Debug info rows, including **Report a problem** |
| `res/values*/strings_reports.xml` | The texts of the report service, in the ten languages |
| `tools/report-worker/` | The report service ([README](../tools/report-worker/README.md)) |

## Verification

Automated: `./gradlew :app:testDebugUnitTest --tests 'com.limelight.diag.*'` (scrubber, writer, report, detector, crash file, issue address, settings snapshot) and
`(cd tools/report-worker && npm ci && npm test)` (the service). The upload is tested against a loopback HTTP server: success, 429, the daily cap, 413, 5xx,
other statuses, a redirect that is not followed, a timeout, no connection, malformed and foreign answers, the feature off without an address; the fall back to
Downloads and the draft with the link in `ReportSubmissionTest`; what the server receives and the address contain none of the stored names in
`ReportPrivacyTest`; `ScrubContractTest` and `scrub.test.js` hold the two scrubbers together.

On a device with a debug build (never while streaming):

1. **Log and share:** Settings → About → Debug info → Diagnostics: the size row shows a count; focus it for the preview; **Share diagnostic log**
   opens the share sheet; **Copy diagnostic summary** copies; **Clear diagnostic logs** empties (the row reads `Reading…` and then a small count).
2. **Switch:** Settings → About → Keep diagnostic logs off, use the app, the size stays; on again.
3. **Crash and offer:** Debug info → **Trigger test crash**. Delos closes; start it again: the card appears on the start or home screen, once. A reports
   (a file in Downloads, the browser opens the prefilled form), B dismisses. Starting again shows nothing. In a build with `DELOS_REPORTS_URL`: A uploads, the toast
   says so, the browser opens with `Diagnostic log: <link>` as the first line of **What happened?**, and the link opens the text; **Save the log to Downloads instead**
   (down from Report) saves the file and uploads nothing; with the network off, Report saves the file and the toast names the reason.
4. **Failed stream:** with the PC off or a wrong port, start a game: the result screen is as before; after Back to library the card follows (not again
   within 30 minutes).
5. **Native crash:** with the app in the foreground, `adb shell run-as io.github.meitoncz.delos.debug kill -ABRT $(adb shell pidof io.github.meitoncz.delos.debug)`
   (expected to show as a native crash on Android 11+, and as an unclean exit on API 29). An ANR is offered the same way when the system reports one.
6. `adb shell run-as io.github.meitoncz.delos.debug ls files/diagnostics` lists the log files, `crash.txt`, `crash.marker` and `session.marker`.

### Not verified

- This document records no dated device run of the steps above.
- The scrubber does not remove app (game) titles, and names shorter than three characters are not recognized as names.
- How the two issue forms render on GitHub, and that GitHub fills them in from the address, was not checked after the forms changed.
- The report service was deployed and exercised with `curl` on 2026-11-16 (upload, read, re-scrub, rejects, rate limit; [README](../tools/report-worker/README.md), "Evidence"). Not
  verified: the real KV expiry after 30 days and the Free plan's CPU limit for a 128 KiB report.
- The upload, the card's second row, the Debug info rows and the toasts were not run on a device or an emulator. Whether the card with its second row fits the shortest
  compact stages was not checked.

## Related

- [SECURITY.md](../SECURITY.md), "Diagnostics" and "The report service".
- Issues [#71](https://github.com/Meitoncz/Delos/issues/71) and [#83](https://github.com/Meitoncz/Delos/issues/83).
- `.github/ISSUE_TEMPLATE/bug_report.yml`, `.github/ISSUE_TEMPLATE/app_report.yml`.
