# Delos documentation

## Purpose

The index of every document of the repository, grouped by kind. All documents follow one style, precise, direct
and structured, defined in [ENGINEERING_RULES.md](ENGINEERING_RULES.md#documentation-style).

`tools/docs/check_links.py` reads the two lists "Living specifications" and "Dated evidence" below: a document is
checked as a living specification only when it is listed there.

## Start here

| Document | What it answers |
|---|---|
| [../README.md](../README.md) | What Delos is, what it works with, where to get it |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | How to report a problem and how to contribute |
| [../SECURITY.md](../SECURITY.md) | How to report a vulnerability; what Delos encrypts, stores and sends |
| [../CHANGELOG.md](../CHANGELOG.md) | What changed in each version, and what is not verified on real hardware |
| [BUILDING.md](BUILDING.md) | How to build, test and lint on macOS, Linux and Windows |
| [ENGINEERING_RULES.md](ENGINEERING_RULES.md) | The permanent rules of the project and the documentation style |
| [VERSIONING.md](VERSIONING.md) | Semantic Versioning, the public API, tags, the release checklist |
| [THIRD_PARTY.md](THIRD_PARTY.md) | Origin, version, license and checksum of every bundled library and binary |
| [CREDITS.md](CREDITS.md) | The people and projects Delos builds on |

## Living specifications

Each describes how a part of Delos behaves now and must match the code. Dated records sit in their
`Evidence (YYYY-MM-DD)` sections.

| Document | Area |
|---|---|
| [GUI.md](GUI.md) | The interface: design principles and the index of the documents in `gui/` |
| [gui/START_AND_HOME.md](gui/START_AND_HOME.md) | The launcher icon, first launch, profile avatars, the start screen, home, the launch of a game, streaming time |
| [gui/ARTWORK.md](gui/ARTWORK.md) | SteamGridDB artwork: covers, hero, logo, the artwork screen |
| [gui/GAME_INFO.md](gui/GAME_INFO.md) | Game information and the library filters |
| [gui/MENUS_AND_SETTINGS.md](gui/MENUS_AND_SETTINGS.md) | The context menu, the settings rows and tabs, impact chips |
| [gui/CONTROLS_AND_TEXT.md](gui/CONTROLS_AND_TEXT.md) | Controller mapping, text input, text that does not fit |
| [gui/MOTION_AND_COLORS.md](gui/MOTION_AND_COLORS.md) | The motion language, toasts, the problem report card, color roles |
| [gui/STREAM_MENU.md](gui/STREAM_MENU.md) | The stream screen and the stream menu |
| [gui/FULL_KEYBOARD.md](gui/FULL_KEYBOARD.md) | The Full keyboard over the stream |
| [gui/SCREEN_SHAPES.md](gui/SCREEN_SHAPES.md) | Layouts for other screen shapes, the screen previews, the layout tools |
| [gui/SHELL_ARCHITECTURE.md](gui/SHELL_ARCHITECTURE.md) | The code map of the shell, design constraints, stream exits |
| [SETTINGS.md](SETTINGS.md) | The Settings screen, the settings catalog, impact chips, profiles |
| [PER_APP_SETTINGS.md](PER_APP_SETTINGS.md) | Settings that one app overrides |
| [STREAM_QUALITY.md](STREAM_QUALITY.md) | Bitrate, pacing, encryption, link test, SGSR 1, variable refresh rate, gyro smoothing, latency |
| [SGSR2_VIDEO.md](SGSR2_VIDEO.md) | The SGSR2-DV upscaler |
| [RUMBLE.md](RUMBLE.md) | Rumble feel, presets, the rumble laboratory |
| [DUAL_SCREEN.md](DUAL_SCREEN.md) | Display classification and the companion on a second panel |
| [EXTERNAL_DISPLAY.md](EXTERNAL_DISPLAY.md) | Streaming to an external display |
| [LAUNCH_TRANSITION.md](LAUNCH_TRANSITION.md) | The arrival of the stream picture after a launch |
| [DIAGNOSTICS.md](DIAGNOSTICS.md) | The diagnostic log, the scrubber, crash capture, problem reports, the issue forms |
| [LOCALIZATION.md](LOCALIZATION.md) | Languages, string files, writing rules, glossaries |
| [ICONS.md](ICONS.md) | The icon fonts and how they are generated |
| [ICON_AUDIT.md](ICON_AUDIT.md) | The inventory of every icon |

## Dated evidence

Each records what was researched or measured on one day. It is not edited afterwards, except to add "superseded by".

| Document | Date | Subject |
|---|---|---|
| [VIDEO_OPTICAL_FLOW_RESEARCH.md](VIDEO_OPTICAL_FLOW_RESEARCH.md) | 2026-10-26 | Feasibility of optical flow for video upscaling on the test device |
| [HANDHELD_DEVICES.md](HANDHELD_DEVICES.md) | 2026-11-11 | The displays of the target handhelds, with sources |
| [CONTROLLER_STATE.md](CONTROLLER_STATE.md) | 2026-11-12 | The stuck rumble with the gyro on: evidence that it is a host problem |
| [prototype/delos-gui.html](prototype/delos-gui.html) | 2026-10 | The HTML prototype of the first design |

## Inherited

| Document | Origin |
|---|---|
| [DECODER_ERRATA.md](DECODER_ERRATA.md) | Moonlight Android: notes on hardware decoders of older devices, unchanged |

## Tools

| Directory | What it is |
|---|---|
| `tools/docs/` | `check_links.py`: relative links, anchors and the structure of the living specifications |
| `tools/hooks/` | `commit-msg`: the commit message check; `pre-push` and `check-branch-name.sh`: the branch name check ([ENGINEERING_RULES.md](ENGINEERING_RULES.md#9-commit-messages-and-branch-names)) |
| `tools/l10n/` | `find_literals.py`: user-visible text that is not a string resource ([LOCALIZATION.md](LOCALIZATION.md)) |
| `tools/fonts/` | The font and icon generator ([ICONS.md](ICONS.md)) |
| `tools/icon/` | The launcher icon generator ([gui/START_AND_HOME.md](gui/START_AND_HOME.md#1-android-launcher-identity)) |
| `tools/avatars/` | The profile avatar importer ([gui/START_AND_HOME.md](gui/START_AND_HOME.md#3-profile-avatars)) |
| `tools/artwork/` | The generator of the bundled app artwork |
| `tools/sounds/` | `make_ui_sounds.py`: the generator of the interface sounds |
| `tools/layout/` | Scripts that drive the debug previews through screen shapes on an emulator ([gui/SCREEN_SHAPES.md](gui/SCREEN_SHAPES.md)) |
| `tools/pyrowave/` | The vendor script and patches of the PyroWave source ([THIRD_PARTY.md](THIRD_PARTY.md)) |
| `tools/versioning/` | `create-tags.sh`: the tags of the reconstructed 0.x versions ([VERSIONING.md](VERSIONING.md)) |
| `tools/metadata-worker/` | The metadata service, with its own [README](../tools/metadata-worker/README.md) |
