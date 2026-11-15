# Changelog

All notable changes to Delos are documented in this file, newest first.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and Delos adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html); what counts as its public API is declared in [docs/VERSIONING.md](docs/VERSIONING.md).

The 0.x versions are reconstructed milestones of the development history, not published releases. 1.0.0 will be the first public release.

Not verified on real hardware yet: variable refresh rate (no VRR display was available) and dual-screen handhelds such as the AYN Thor (tested only on emulators and the shape simulator). HDR colour was judged by eye on one OLED handheld (2026-10-27); colour and luminance were not measured. Delos is developed and tested with one handheld (AYN Odin 2 Portal Max, Android 13) and Vibepollo on Windows; there is no recorded test with Apollo, Sunshine or a Linux host.

## [Unreleased]

### Added
- Streaming time: Delos counts how long each app was streamed, per profile and PC, and shows the total and when it was last streamed on the home screen ("0 hours" and "Never" before the first stream).
- Stream encryption readout: Debug info shows what the last stream encrypted (video, audio, control), and the full statistics panel has an "Encryption" row. It reports what the PC negotiated; nothing is enforced.
- The license texts of the bundled libraries are packaged in the APK. The app does not display them yet.

### Changed
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

[Unreleased]: https://github.com/Meitoncz/Delos/compare/v0.14.0...HEAD
[0.14.0]: https://github.com/Meitoncz/Delos/compare/v0.13.0...v0.14.0
[0.13.0]: https://github.com/Meitoncz/Delos/compare/v0.12.0...v0.13.0
[0.12.0]: https://github.com/Meitoncz/Delos/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/Meitoncz/Delos/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/Meitoncz/Delos/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/Meitoncz/Delos/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/Meitoncz/Delos/compare/v0.7.0...v0.8.0
[0.7.0]: https://github.com/Meitoncz/Delos/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/Meitoncz/Delos/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/Meitoncz/Delos/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Meitoncz/Delos/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Meitoncz/Delos/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Meitoncz/Delos/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Meitoncz/Delos/releases/tag/v0.1.0
