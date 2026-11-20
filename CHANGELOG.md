# Changelog

All notable changes to Delos are documented in this file, newest first.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and Delos adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html); what counts as its public API is declared in [docs/VERSIONING.md](docs/VERSIONING.md).

The 0.x versions are development versions, not published releases; 0.1.0 to 0.15.0 are reconstructed or tagged milestones of the history. 1.0.0 is the first public release.

Not verified on real hardware yet: variable refresh rate (no VRR display was available) and dual-screen handhelds such as the AYN Thor (tested only on emulators and the shape simulator). Delos is developed and tested with one handheld (AYN Odin 2 Portal Max, Android 13) and Vibepollo on Windows; there is no recorded test with Apollo, Sunshine or a Linux host.

## [Unreleased]

### Added
- Game overview ([#107](https://github.com/DELOS-STREAM/DELOS-android/issues/107), variant A · Descent): Down on a game (a swipe up on its cover, or a tap or click on the "Game info" hint under it) continues the dashboard downward into a page about the game. The cover flies from its card into the header beside the title, the year and developer, the critics' and players' rating rings, the age rating, Play, App settings and the streaming stats; About (summary, the storyline expanding in place, a facts card), Screenshots and videos (a carousel with a full-screen viewer), Details, Your streaming, Links and Similar games follow. The hero behind it deepens its blur as the page scrolls, the glass cards blur it for real, and everything reverses on the way back (B, Up from the header, the back button or pulling the page down). Controller, keyboard, touch (focus first) and mouse (hover focuses) all work; reduced motion keeps only the fades. The cover is always the library card's image; IGDB supplies the data, screenshots and similar games' covers through a new metadata Worker endpoint, `GET /v1/igdb/game-details`, cached with the game's metadata for 7 days, its images in a 150 MB disk cache. Screenshots load directly from IGDB's image server, which sees the device's IP (docs/gui/GAME_INFO.md, "Credentials and privacy"). Covered by unit tests; laid out on an emulator, not yet checked on the AYN Odin 2 Portal Max.
- In Settings and the app settings, the right menu button (Start) on a choice or slider row opens the same list of values or slider popup a tap opens, so a controller shows every value at once; a custom app setting's list ends with "Reset to profile value", and a custom slider's menu offers Adjust and the reset.
- A mouse or trackpad in the shell: moving over an item focuses it where it is (the row and lists never scroll to follow the pointer; the wheel and the trackpad scroll them, and a plain mouse wheel scrolls the library row), a right click or two-finger click opens the item's menu and closes an open menu beside it, and the hint bar shows the left and right click. Buttons that take no focus (hint pills, the back arrow, tab pills, small buttons) light up and lift slightly under the pointer.
- Every shell action has a key on a hardware keyboard, with its key cap in the hint bar: arrows, Return or Space, Esc, Tab for the side menu, O for the context menu, F for filter and search, [ and ] for the tabs. Keys with Ctrl, Alt or Meta and letters typed into a text field are left alone.

### Changed
- The press of every button, cover, row and tile is one shared look: it drops to 0.96 of its size at rest (below its focused size) with a brief glow pulse, starts the moment a finger or mouse button goes down, and a tap or click acts once the squeeze is seen.
- The GPU impact chip shows the chip icon again instead of a graphics card, because handhelds have a system on a chip rather than a separate graphics card.
- Frame pacing lists Fast V-Sync first and Warp Drive and Warp 2 last (Fast V-Sync, Lowest latency, Balanced, Balanced with FPS limit, Smoothest video, Warp Drive, Warp 2), and video codec lists H.264, HEVC, AV1, then PyroWave (after Automatic), in settings, per-app settings and the stream menu, so the familiar choices come first.
- Touch: on the home screen a tap on a game cover that does not have the focus only focuses it (the background, the info and the chips show that game); a tap on the focused cover starts or resumes it, as Settings rows already work, so a tap meant to look at a game never launches it. A controller and a long press are unchanged.
- The library filter (Y on the home screen) takes several values in every row (for example two genres, a theme and Native HDR): a chip toggles on and off, values in one row match any of them and the rows must all match. The library changes only when the choices are confirmed with the "Show N apps" pill, which counts the matching apps live, or with Y (Return on a keyboard; Space toggles a chip); B closes without applying and Reset clears the choices. One press down from the last row reaches the pill, and the home screen's Filter hint counts every chosen value. The confirmed choices are kept as before, and a filter kept by an older version opens with its values.

## [1.2.0] - 2026-11-17
_Touch everywhere, the last input decides the hints, new controller glyphs, Guide and Back for the stream menu, anchored on-screen controls; the project moves to the DELOS-STREAM organization._

### Added
- Button hints use the approved complete Xbox, DualSense, DualShock 4 and Joy-Con font glyphs, including menu buttons, shoulders, triggers, sticks, D-pad and Guide. Nintendo physical confirm/back stays south/east (B/A); unknown pads keep their existing labels. The fonts also include keyboard, mouse and trackpad symbols, used by the keyboard and mouse hints. No extra disc is drawn around a complete cap. Physical controller verification is pending ([#100](https://github.com/DELOS-STREAM/DELOS-android/issues/100)).
- Touch (issue [#92](https://github.com/DELOS-STREAM/DELOS-android/issues/92)): every choice row now has an option list: a card at the row with the current value checked and focused, a long list scrolls, a tap beside it closes it. It replaces the context menu of the first version. This covers all Settings tabs, the per-app settings, the side menu's Theme and Background, Debug info and the stream menu's Mode, Supersampling quality and Touchscreen mode; rows that were only toggles in disguise (Theme, Animations, Companion dimming, Default PC, Statistics position, the display rows) have one too.
- Touch: a tap on the focused slider row opens a popup with a ◀ and a ▶ button (hold to repeat, faster the longer it is held), the slider, and the value as a number to tap and type with the on-screen keyboard (the bitrate in Mbit/s); the value is validated, rounded to the slider's grid and clamped to its range. It works on every slider, also the stream menu's. The controller is unchanged. Covered by unit tests; tested on the AYN Odin 2 Portal Max (Android 13).
- Touch: the launch scene and the stream's loader have a small Cancel pill at the bottom, and every touch on them is blocked, so nothing underneath reacts.
- Touch: a swipe in from the left edge opens the side menu on the screens that have it; the panel, the scrim and the blur follow the finger (pulled past fully open the panel moves a little further, damped), and on release it opens past 40 % of its width or with a fast swipe, else it springs back. A swipe to the left on the open menu closes it the same way. With reduced motion the panel does not follow and a long enough swipe toggles it. With Android's gesture navigation a 200 dp band in the middle of the left edge is kept from Back for it; with 3-button navigation the whole edge works. The decisions are covered by unit tests; tested on the AYN Odin 2 Portal Max (Android 13).
- On-screen controls opacity ([#92](https://github.com/DELOS-STREAM/DELOS-android/issues/92), [#101](https://github.com/DELOS-STREAM/DELOS-android/issues/101)): a slider in Settings → Controls (on devices with a touchscreen) and on the stream menu's More page, where it changes the running on-screen controls at once. Both set the same value of the profile. Covered by unit tests; tested on the AYN Odin 2 Portal Max (Android 13).
- The last input is the current input ([#103](https://github.com/DELOS-STREAM/DELOS-android/issues/103)): a touch, a mouse, a keyboard key or a controller's button or stick decides what the hints show, also with a controller connected (connecting or disconnecting a pad only changes which glyphs a controller gets; unplugging it brings back what was used before). With touch the hint bar and the stream menu keep only the main action with a tap symbol, with a mouse the same with the font's left-click cap, with a keyboard the font's Return, Esc and arrow key caps. Pills that a finger presses keep working, and their button glyph shows only for an input that has that button; the toast's Y only for a controller. The Cancel pill of the launch, the stream's loader and the portal (link test, waking a PC) shows while a finger, a mouse or a keyboard is the input and gives way with a controller, with a fade, a slight scale and slide (only a fade with reduced motion); the portal no longer needs a first tap to offer it. Covered by unit tests with event sequences; tested on the AYN Odin 2 Portal Max (Android 13).
- Touch, mouse and keyboard: the library's Filter stays in the hint bar as a pill without a glyph, and a tap on it opens the filters like Y ([#103](https://github.com/DELOS-STREAM/DELOS-android/issues/103)); with a controller it is the Y hint again. Covered by a unit test; tested on the AYN Odin 2 Portal Max (Android 13).
- Touch, mouse and keyboard: a round back button at the left of the status bar, before DELOS, on every screen that can go back ([#103](https://github.com/DELOS-STREAM/DELOS-android/issues/103)). It does what B does and gives way with a controller, the brand gliding with it. Covered by a unit test; tested on the AYN Odin 2 Portal Max (Android 13).

### Changed
- The project moved to the [DELOS-STREAM](https://github.com/DELOS-STREAM) organization and the repository is now `DELOS-STREAM/DELOS-android` ([#99](https://github.com/DELOS-STREAM/DELOS-android/issues/99)). The update check, About, the reports and every link point there; the old addresses redirect, so 1.0.0 and 1.1.0 still find this update.
- Guide/Home and Back during a stream ([#102](https://github.com/DELOS-STREAM/DELOS-android/issues/102)): a tap of the controller's Guide/Home button or of the device's built-in Back button opens the stream menu and sends nothing to the PC; a hold of half a second sends Guide to the PC (Steam overlay, Game Bar), pressed while the button is still held. L3 + R3 no longer opens the stream menu by default: your own stream menu buttons (Settings → Controls) stay, are none by default, keep a combination you recorded, and can be cleared (Not set). The Hold Back for Guide setting of 1.1.0 is removed, as a hold is now always Guide (a tap of Back no longer sends Back to the PC). Covered by unit tests; tested on the AYN Odin 2 Portal Max (Android 13).
- Touch (issue [#92](https://github.com/DELOS-STREAM/DELOS-android/issues/92)): the option list and the slider popup of settings rows are now the stream menu's popover card with its caret, grown out of the tapped row: vertically centred on it (never closer than 24 to the screen's edges), growing out of its caret to the right (the caret on the card's left edge, pointing into the row at its right part, left of the value; it flips to the left only when the right has no room), with the shell's real background blur under the card (the stream menu has no blur source and keeps the translucent glass), opening and closing like the stream menu's popover, without dimming. The slider popup is twice as wide with large touch targets: ◀ ▶ buttons of 96, a value field 88 high with its unit, a 96 high track. The controller is unchanged. Tested on the AYN Odin 2 Portal Max (Android 13).
- One caret for every glass card that points at something (the dashboard's context menu, the settings popover, the stream menu's popover, all four sides): the average of the two carets that were used (60 wide, 24 high, a slightly rounded base and tip), drawn by one function.

### Fixed
- A long press on a cover of the library (and on any element with a context menu: PC circles, unpaired PCs, customized settings rows) opens its context menu by touch. It never worked: only a tap was handled, the menu opened by Start or X only (not a regression of the touch pull request). A tap is unchanged. Covered by a unit test; tested on the AYN Odin 2 Portal Max (Android 13).
- Touch on settings rows ([#92](https://github.com/DELOS-STREAM/DELOS-android/issues/92)): a first tap focuses a row and shows its help, a tap on the focused row changes it; the switch and a choice's arrows take their own taps (◀ goes back), and a focused choice row with several values lists them to pick from. Keys and the gamepad are unchanged. Tested by touch on a Galaxy Tab S9+ (Android 16). (by [@Mimir-apk](https://github.com/Mimir-apk), [#93](https://github.com/DELOS-STREAM/DELOS-android/pull/93))
- The link test (and waking a PC) can be cancelled by touch: a tap shows a Cancel button, and taps no longer reach the screen underneath. The link test was tested on a Galaxy Tab S9+ (Android 16); waking a PC was not tested. (by [@Mimir-apk](https://github.com/Mimir-apk), [#93](https://github.com/DELOS-STREAM/DELOS-android/pull/93))
- The update card no longer stays on Installing until the app restarts when Android's install prompt cannot be shown; the install ends as failed. Covered by a unit test; not reproduced on a device. (by [@Mimir-apk](https://github.com/Mimir-apk), [#93](https://github.com/DELOS-STREAM/DELOS-android/pull/93))
- Starting a game can be cancelled at any moment, by B and by touch, in every phase of the loader: before, B did nothing once the stream window had opened (and the first phase lasts under a second). Cancelling ends only the connection attempt and returns to the library; the game on the PC keeps running. Covered by unit tests; tested on the AYN Odin 2 Portal Max (Android 13).
- The stream menu's swipe up no longer starts at the very edge of the screen, where Android's gesture bar takes it: it starts in the bottom 18 % of the screen above the system's gesture strip. A tap outside the stream menu (above or beside the tiles) closes it; it never quits or disconnects and the tap does not reach the game.
- On-screen controls: Back, Guide, Start and the trackpad are centred again. An old layout that only repeated a default (stored by an earlier version, or by Artemis before it, for the screen size of that time) kept them left of the centre; it is dropped, and a layout saved by earlier versions is converted once. Layouts are now the same on every resolution and screen shape: only the controls you changed are stored, each against its side of the screen (the left edge, the right edge or, for the middle controls, the centre) in units of the screen's height, and the default layout is never stored. The controls are also laid out on the area they are drawn in rather than on the display size Android reports ([#101](https://github.com/DELOS-STREAM/DELOS-android/issues/101)).

### Removed
- The **Hold Back for Guide** setting of 1.1.0: a hold of Back (or of Guide) now always sends Guide, and a tap opens the stream menu ([#102](https://github.com/DELOS-STREAM/DELOS-android/issues/102)).
- **L3 + R3** as the default stream-menu buttons: the combination is empty by default and stays recordable in Settings → Controls (a recorded one, L3 + R3 included, is kept) ([#102](https://github.com/DELOS-STREAM/DELOS-android/issues/102)).

## [1.1.0] - 2026-11-16
_Hold Back for Guide and updated libraries._

### Added
- Hold Back for Guide (Settings → Controls, off by default): holding a gamepad's Back button for half a second sends Guide (the Xbox button) to the PC, for handhelds without a Guide button; a short press still sends Back, on release. Requested on Reddit ([#90](https://github.com/DELOS-STREAM/DELOS-android/issues/90)); tested in a stream on the AYN Odin 2 Portal Max.

### Security
- Libraries updated: BouncyCastle 1.87 (fixes in certificate and key parsing), OkHttp 5.5.1, jmDNS 3.6.5, Gson 2.15.0, Haze 2.1.0, and the build tool Gradle 9.9.1.

## [1.0.0] - 2026-11-16
_The first public release: console-style streaming client for Android handhelds with in-app updates and report upload._

Everything listed under 0.1.0 to 0.15.0 below is part of this release; this section lists what is new since 0.15.0.

### Added
- In-app updates: Delos checks the latest GitHub release once a day (Settings → About → Check for updates, on by default; Check now asks at once), shows a card on the start and home screens with the release notes, and on Update downloads the APK, checks its SHA-256 against the release's `SHA256SUMS.txt` and hands it to Android's installer. Nothing is installed without your yes and Android's prompt; Later hides the offer for 3 days. Not verified on a device.
- Problem reports with the log attached: in a build that has the Delos report service (`DELOS_REPORTS_URL`), "Report this problem" (the card, and a new "Report a problem" row in Debug info) uploads the cleaned log to a small Cloudflare Worker (`tools/report-worker/`) that keeps it for 30 days behind a link, and opens the GitHub draft with that link in it. "Save the log to Downloads instead" keeps the earlier behavior, and a failed upload falls back to it with a toast that names the reason. Nothing is sent before the user chooses. The service runs on Cloudflare; on 2026-11-16 a report from the AYN Odin 2 Portal Max uploaded its log and opened the GitHub draft with the link.

### Changed
- A new installation starts with the look chosen for DELOS: the Delos palette, the dark theme (also when the system is light), the Contour background, game colors and game backgrounds on (blur 5, opacity 80 %) and the interface at 100 %. Existing settings are kept.

### Security
- The updater contacts only `api.github.com` (and the GitHub download host after you press Update), over HTTPS with redirects checked and a 250 MB limit; the downloaded file is deleted unless its SHA-256 matches the published checksum. See [docs/UPDATES.md](docs/UPDATES.md).
- A report log is cleaned twice before it is stored: on the device by the scrubber, and by the report service, which repeats the pattern rules (addresses, e-mail addresses, URL queries, ids, keys, tokens) and says how many matches it still changed. Anyone who has the link can read the log for 30 days; the service holds no credentials and keeps no address.

## [0.15.0] - 2026-11-15
_Streaming time, stream encryption readout, security hardening, updated delos-common-c._

### Added
- Streaming time: Delos counts how long each app was streamed, per profile and PC, and shows the total and when it was last streamed on the home screen ("0 hours" and "Never" before the first stream).
- Stream encryption readout: Debug info shows what the last stream encrypted (video, audio, control), and the full statistics panel has an "Encryption" row. It reports what the PC negotiated; nothing is enforced.
- The license texts of the bundled libraries are packaged in the APK. The app does not display them yet.

### Changed
- The launch tunnel weights the palette colors: the main color is stronger (×1.5), the second color stronger (×1.3) and the third color weaker (×0.5).
- Text that does not fit on one line (context menu titles and items, row values, app names) scrolls with a fading edge while it is focused instead of being cut off.
- The stream start warnings (HDR10, HEVC, AV1, PyroWave), the connection messages of a stream start, the mouse emulation and custom key messages and the service labels are translated into the ten languages; they were English only.

### Removed
- The unused "root" build variant with its input capture helper. It was never part of a build.

### Fixed
- Leaving a stream while it is still connecting (Home, lock, a lost surface) stops the connection; before, the next start could fail with "stream operation still running". Not verified on a device.
- A reply from SteamGridDB that is not its JSON (a Wi-Fi sign-in page, a maintenance page) no longer crashes the app while the library is open; it shows a message.
- Stream profiles are saved atomically: an interrupted save no longer loses every stream profile.
- A stream that starts before the previous one is fully closed keeps its back, menu and keyboard callbacks.
- The control screen of an external-display stream no longer repeats its "no stream" message every 500 ms after it closed.
- PyroWave: a crash at the end of a 10-bit/HDR stream or on an HDR switch. Not verified on a device.
- PyroWave: release builds no longer send the stack-dump signal to a stalled video thread, which could deadlock it.
- Native code: failed allocations, thread attaches and lookups are handled instead of crashing the process. Not verified on a device.
- Release builds keep the class the SteamGridDB match file is read into; code shrinking could drop its fields.
- A USB Xbox controller announces its buttons to the PC, and an Xbox One controller no longer reports the guide button as held until it is pressed once.
- Adding a PC by an IPv6 address typed without brackets (`2001:db8::1`) uses the whole address instead of reading its last group as the port.
- The app list of a PC appears with its first answer instead of one poll later.
- "Report this problem" opens the report form made for the app, which asks only for what the app cannot fill in.

### Security
- A launch from outside Delos (a shortcut made by another app, an intent of another app, an opened `.art` file) asks before it starts a game, opens a PC or sends Wake-on-LAN, like `art://launch` links. Launches that Delos makes itself are signed and start without a prompt.
- The pairing key, the client certificate and id, the PC database and the diagnostic logs are excluded from cloud backup and device-to-device transfer. A restored or transferred installation pairs with its PCs again.
- A paired PC is accepted only with its pinned certificate; a certificate that the system trusts no longer passes as well.
- An unverified HTTP reply of a paired PC no longer changes its stored name, addresses or MAC address.
- The id a host sends is validated before it names files or database rows; a host with an invalid id is not added.
- A game name in a SteamGridDB search is sent as one path segment, so a name with "/" cannot reach another endpoint with the user's API key.
- Text that another app passes in an intent is no longer shown in the stream result overlay.
- delos-common-c, the streaming core, is updated with the changes of moonlight-common-c: replayed RTSP and control messages, STUN answers from the wrong address or with a bad length, oversized or malformed video frames, empty audio packets and ENet disconnects meant for another peer are rejected; a use-after-free of queued controller input and uninitialised bytes in the keep-alive ping are fixed. Not yet tested on a real stream.

## [0.14.0] - 2026-11-14

_Variable refresh rate, new palettes and avatars, UI sounds, rumble fixes._

### Added
- Variable refresh rate setting (Auto / On / Off). Unverified on real VRR hardware.
- Eight colour palettes with dark muted backgrounds, and sixteen illustrated adult avatars (Owl, Hacker, Wolf and Dragon added).
- A UI sound set called Soft; UI sounds follow the media volume.
- Hidden apps shown through "Show hidden apps" carry an eye-slash badge.
- Rumble and controller-state diagnostics.

### Changed
- The animated hero loops with a cross-fade and keeps a Gaussian blur.
- A library entry is called an app, not a game, where it can be Desktop or a program.

### Fixed
- An offline PC stays shown as paired.
- Rumble diagnostics no longer crash on Android 13.

### Removed
- The "Fix gyro handling and vibrations" setting: it did not help. The stuck rumble with gyro on is documented as a host DualSense emulation bug.

## [0.13.0] - 2026-11-12

_Screen shapes, dual screen with companion pages._

### Added
- Layouts for other screen shapes: the shell adapts to the window size, with a debug shape simulator.
- Dual-screen support: a Main screen setting, and a companion screen with pages for every screen of the app. Tested on emulators only, not on a real Thor.
- Idle dimming of the companion screen (Off, 1, 2, 5, 10 minutes).
- Documentation of the target handhelds' displays.

### Changed
- The shell is landscape only and keeps clear of the display cutout.
- Compact layouts for the home row, settings tabs, artwork and the stream menu on small or square stages.
- The context menu and stream menu popover are placed on the real stage.

### Fixed
- A very narrow stage (a square window) crashed the home grid.
- The hero and animated background cover every window shape.

### Removed
- The Home grid layout; the row stays.

## [0.12.0] - 2026-11-10

_Localization in ten languages._

### Added
- Ten languages: the shell and the stream follow the Android per-app language, with settings search that understands each language.

### Changed
- Help, shortcut and external-display screens follow the app language on Android 10 to 12.
- The longest translations are shortened to fit their slots.

## [0.11.0] - 2026-11-10

_Stream defect fixes, bundled app art, diagnostics and problem reports._

### Added
- Background diagnostics: a rolling on-device log with personal data scrubbed, a device info block, and a problem report card offered on the next start after a crash or ANR, with a prefilled GitHub issue. "Keep diagnostic logs" switch in Settings > About.
- Island-style hero art for the bundled apps (Desktop, Remote Input).

### Changed
- The streaming activity is split into smaller parts and renamed DelosStreamActivity.
- The stream plans for the refresh rate the display runs after the mode switch.
- Launch links and shortcuts poll the PC right away.
- Icon audit: every icon reviewed against its meaning.
- Less drawing work on the home screen: the focus glow is blurred once per shape, and the dimming behind a context menu no longer recomposes the tiles (the effect was not measured).

### Fixed
- Long text no longer gets stuck when typing into the stream.
- Keys held on the host are released when the stream loses focus.
- Surface size after turning the upscaler off, and the Picture-in-Picture aspect ratio.
- Right Alt as Command (Mac) shows the Command glyph.

## [0.10.0] - 2026-11-09

_Gyro smoothing, context menu redesign, Game activity refactor begins._

### Added
- Gyro smoothing (off by default), available per game and in the stream menu.
- Issue forms, a pull request template and CONTRIBUTING.
- Impact chips and a custom-settings badge for games with their own settings.

### Changed
- Gyro options are named External gamepad gyroscope and Built-in device gyroscope.
- The context menu is a glass card anchored to its element, with submenus and danger rows.
- Live upscaler changes in the stream menu are kept for the game automatically.
- More dashboard spacing between covers.

## [0.9.0] - 2026-11-07

_Link guidance, latency work, SGSR verification, per-game settings redesign._

### Added
- Link guidance: test the link and get a recommended bitrate for PyroWave, H.264, HEVC and AV1, with a hint in the stream menu.
- A latency section in the statistics and measurement logging.
- Impact chips (GPU, latency, network) next to settings rows.
- Per-game settings with a hero banner, reset with Undo and per-tab counts.

### Changed
- SGSR 1 and SGSR2-DV are checked against the official reference; the upscaler modes are renamed accordingly.
- Stream threads get higher priority and stay on the big cores; SGSR drops stale frames and limits the GPU work in flight (the effect on latency was not measured).
- Supersampling level 1 is labelled 1.33x.

### Fixed
- Artwork loading with animated tiles, takedown placeholders and temporary SteamGridDB failures.

## [0.8.0] - 2026-11-07

_Hero art, artwork page redesign, PyroWave 4:4:4, Sharpen only, settings motion._

### Added
- Tilting hero art on the home screen, and the artwork page redesigned with SteamGridDB filters and animated artwork.
- PyroWave 4:4:4 as a manual chroma setting.
- A Sharpen only upscaler mode.
- Option to hide phone-oriented settings for the handheld-first phase.

### Changed
- The settings tabs, top menu and lists glide with one focus plate and calm scrolling.
- Separate bitrates per video codec; PyroWave bitrate follows the effective chroma.
- Animated covers play on the visible tiles.
- PyroWave diagnostics in the statistics panel.

### Fixed
- Hero flashes while an animation is released.
- Metadata search ranks console-only entries last instead of dropping them.

## [0.7.0] - 2026-11-04

_Illustrated avatars, quit and exit flow, launch transition._

### Added
- Twelve illustrated profile avatars.
- A launch transition into the stream and a matching exit through the portal, with the animated background fading in.
- Launch links for ES-DE from the app context menu.

### Changed
- Quitting an app waits until the PC has really quit it and reports the real result.
- The upscaler sharpens the stream when it is turned on.
- Dead classic menu and asset paths removed; on-screen keyboards decoupled.
- The product flavor naming is dropped.

### Fixed
- Exported poster reads are limited to the box-art cache.
- Ordinary confirmations that had no reason are removed.

## [0.6.0] - 2026-10-30

_Game metadata, library filters, HDR badges, launcher icon._

### Added
- Game metadata badges and library filters.
- Unverified HDR badges (HDR/SDR unverified) in the library.
- Live PyroWave bitrate recommendations.
- A Delos ring-and-moon launcher icon.

### Changed
- Bitrates are remembered per codec and games inherit them.
- Stream menu shortcut sees gamepad buttons.
- Concise help panels and a polished filter modal.

### Fixed
- Artwork refresh after cancelling the picker.
- The supersampling row needing two presses and a black flash when the HDR PyroWave upscaler changed.
- Per-game overrides for shortcuts, the PyroWave bitrate reset, a right-menu race and search.

## [0.5.0] - 2026-10-28

_Per-game settings, SGSR upscaling, external display, help panels, Phosphor icons._

### Added
- Per-game stream and display settings.
- Live stream quality controls and SGSR upscaling, also on PyroWave, and native-resolution supersampling.
- External display support, including an HDR result.
- Help panels explaining settings and stream controls.

### Changed
- Phosphor icons replace the interface symbols.
- The packet loss option is renamed Wi-Fi keep-alive.
- Dashboard logos and title motion polished.

## [0.4.0] - 2026-10-25

_PyroWave codec._

### Added
- PyroWave, a GPU wavelet codec, as a codec choice, with HDR10.
- Statistics: 1% low FPS and total latency.
- Stream menu: brightness for the stream.

### Changed
- Bitrate slider steps of 1 Mbps.

### Fixed
- A freeze with PyroWave.

## [0.3.0] - 2026-10-24

_Palettes, backgrounds, home row and on-screen controls polish._

### Added
- Three-colour palettes, new animated backgrounds and game colours taken from the cover.
- A battery charging ring and an on-screen controls preview.

### Changed
- Home row ordered by last played, with a springy wrap-around.
- On-screen controls in the Delos style.
- Status bar widths and gaps made steady.

## [0.2.0] - 2026-10-24

_Native Delos UI, profiles, settings, stream menu, SteamGridDB artwork._

### Added
- A native Delos UI in Kotlin and Compose: home with the PC's library, game launch, first-launch, pairing, devices and start screens.
- Profiles with their own settings and stream profiles, and native settings.
- The Delos stream menu and statistics panel, replacing the Artemis options.
- SteamGridDB artwork.
- Colour palettes, clock format and battery indicator.

### Changed
- The Delos UI is the only UI.
- Frosted glass for controls over the animated background.
- Leaving the stream through a portal back into home.

### Removed
- Four settings that did nothing useful, and Moonlight and Artemis leftovers.

## [0.1.0] - 2026-10-23

_Fork baseline: Moonlight 12.2 merge, Android 10 minimum, dead-code removal, Delos branding._

### Added
- Delos identity, versioning, CI and English documentation.
- Moonlight Android 12.2 merged, with moonlight-common-c at the Moonlight 12.2 pin.

### Changed
- Android 10 is the minimum; modern platform APIs.
- Frame pacing respected and the decoder render loop cleaned up.
- HDR, full range, AV1 and clipboard sync are no longer labelled Experimental.
- English comments, logging and branding.

### Removed
- SBS 3D, performance logging and other dead code.

[Unreleased]: https://github.com/DELOS-STREAM/DELOS-android/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.15.0...v1.0.0
[0.15.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.14.0...v0.15.0
[0.14.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.13.0...v0.14.0
[0.13.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/DELOS-STREAM/DELOS-android/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/DELOS-STREAM/DELOS-android/releases/tag/v0.1.0
