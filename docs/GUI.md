# Delos UI specification

## Purpose

The specification of the Delos interface: every screen of the shell, the stream menu and the on-screen keyboard,
with their layout, motion, colors, controller navigation and the code that draws them. Layout ideas may borrow
from PS5, SteamOS and Switch, **the visual design must be original**. This document is the index: it states the
design principles that hold on every screen and names the document that specifies each area.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The shell: first launch, start screen, home, artwork, game facts and filters, menus, settings rows, toasts | The settings catalog and profiles: [SETTINGS.md](SETTINGS.md), [PER_APP_SETTINGS.md](PER_APP_SETTINGS.md) |
| Controls, text input, text that does not fit, motion, color roles | The arrival of the stream picture: [LAUNCH_TRANSITION.md](LAUNCH_TRANSITION.md) |
| Streaming time, the stream menu, the full keyboard | The second panel of a dual-screen device: [DUAL_SCREEN.md](DUAL_SCREEN.md) |
| Layouts for other screen shapes | Icons: [ICONS.md](ICONS.md); texts and languages: [LOCALIZATION.md](LOCALIZATION.md) |

This is a specification, not a task tracker: unfinished intentions and decisions live in
[GitHub Issues](https://github.com/DELOS-STREAM/DELOS-android/issues). The [HTML prototype](prototype/delos-gui.html) is a
historical reference of the first design (2026-10); where it differs from this document or from the app,
the app is right.

## Documents

Each document is a complete living specification of one area.

| Document | Area |
|---|---|
| [gui/START_AND_HOME.md](gui/START_AND_HOME.md) | The launcher icon, first launch, profile avatars, the start screen, home, the launch of a game, streaming time |
| [gui/ARTWORK.md](gui/ARTWORK.md) | SteamGridDB artwork: covers, hero, logo, the artwork screen |
| [gui/GAME_INFO.md](gui/GAME_INFO.md) | Game information, the library filters and the game overview |
| [gui/MENUS_AND_SETTINGS.md](gui/MENUS_AND_SETTINGS.md) | The context menu, the settings rows and tabs, impact chips, the top menu pill |
| [gui/CONTROLS_AND_TEXT.md](gui/CONTROLS_AND_TEXT.md) | Controller mapping, text input, text that does not fit |
| [gui/MOTION_AND_COLORS.md](gui/MOTION_AND_COLORS.md) | The motion language, toasts, the problem report card, color roles |
| [gui/STREAM_MENU.md](gui/STREAM_MENU.md) | The stream screen, the stream menu, the context menu and stream menu previews |
| [gui/FULL_KEYBOARD.md](gui/FULL_KEYBOARD.md) | The Full keyboard over the stream and its preview |
| [gui/SCREEN_SHAPES.md](gui/SCREEN_SHAPES.md) | Layouts for other screen shapes, the screen previews, the shape simulator, the layout tools |
| [gui/SHELL_ARCHITECTURE.md](gui/SHELL_ARCHITECTURE.md) | The code map of the shell, design constraints, stream exits, actions without confirmation |

## Design principles

The principles hold on every screen. The documents above specify how each screen applies them.

1. The UI is one continuous **experience from the very first launch**, not a set of separate screens.
2. The layout is **console-like**, modern and minimalist, with **lots of animations and effects** and
   **polished focus animations** everywhere (menus and anywhere else).
3. There are **several types of dynamic backgrounds**.
4. There is a **light / dark mode**, plus an extra **Material You** switch that takes the overall color palette
   from the system. With Material You on, **all dynamic backgrounds use only the system palette** (no other colors
   mixed in); the "System" wallpaper color is the default.
5. Overlays such as the **portal and the game loader blur the content behind them**.
6. The design is slightly **rounded**, with generous paddings and margins.
7. An **interface scale** setting enlarges text, the status bar and the button hints. The covers keep their size.
8. Icons are **Phosphor icons**, bundled offline under the MIT license: Regular at rest and Fill on focus, with a
   smooth crossfade. An icon is chosen by the meaning of its action or setting; identical actions stay consistent
   across screens. The color-palette selector uses **Palette** for every palette choice. The approved
   implementation and the generation workflow are in [ICONS.md](ICONS.md).

## Section finder

Code comments cite this specification as `docs/GUI.md, "<section>"`. The table names the document that holds each
cited section.

| Cited section | Document |
|---|---|
| "Android launcher identity", "First launch", "Profile avatars", "Start screen", "Home" | [gui/START_AND_HOME.md](gui/START_AND_HOME.md) |
| "Launching a game", "Streaming time" | [gui/START_AND_HOME.md](gui/START_AND_HOME.md) |
| "SteamGridDB" | [gui/ARTWORK.md](gui/ARTWORK.md) |
| "Game information and library filters" | [gui/GAME_INFO.md](gui/GAME_INFO.md) |
| "Game overview" | [gui/GAME_INFO.md](gui/GAME_INFO.md) |
| "Menus and settings", "Context menu", "Settings tabs", "Impact chips" | [gui/MENUS_AND_SETTINGS.md](gui/MENUS_AND_SETTINGS.md) |
| "Controls", "Text input", "Text that does not fit", "Keyboard and pointer" | [gui/CONTROLS_AND_TEXT.md](gui/CONTROLS_AND_TEXT.md) |
| "Motion", "Toasts", "Problem report card", "Color roles" | [gui/MOTION_AND_COLORS.md](gui/MOTION_AND_COLORS.md) |
| "Stream", "Stream menu", "Stream menu preview", "Context menu preview" | [gui/STREAM_MENU.md](gui/STREAM_MENU.md) |
| "Full keyboard", "Full keyboard preview" | [gui/FULL_KEYBOARD.md](gui/FULL_KEYBOARD.md) |
| "Screen shapes", "Screen previews" | [gui/SCREEN_SHAPES.md](gui/SCREEN_SHAPES.md) |
| "Code map and design constraints", "Stream exits" | [gui/SHELL_ARCHITECTURE.md](gui/SHELL_ARCHITECTURE.md) |

## Verification

- The unit tests named in the documents above enforce the geometry and the logic they describe (for example
  `StreamMenuLayoutTest`, `ImeShiftTest`, `MarqueeMotionTest`, `DockGeometryTest`, `HomeCompactLayoutTest`).
- Debug builds draw every screen with fixed data and without a PC: `--es preview_screen <name>` on `ShellActivity`
  ([gui/SCREEN_SHAPES.md](gui/SCREEN_SHAPES.md) lists the names). The shape simulator draws them in the shape of
  another display.
- `tools/layout/sheets.sh`, `tools/layout/crashcheck.sh` and `tools/layout/sweep.sh` drive those previews through
  the screen shapes of `tools/layout/shapes.txt` on an emulator.
- Device checks that are recorded are written into the document they belong to, with the device named.

### Not verified

- A stream on any screen shape other than 16:9, and the on-screen controls touched for real on such a panel: the
  emulator cannot run a stream ([gui/SCREEN_SHAPES.md](gui/SCREEN_SHAPES.md)).
- Everything on a real dual-screen handheld ([DUAL_SCREEN.md](DUAL_SCREEN.md)).
- The GUI documents have no dated evidence sections for device checks: where a document says that something was
  measured or accepted on the Odin without a date, the date was not recorded.

## Related

- [SETTINGS.md](SETTINGS.md), [PER_APP_SETTINGS.md](PER_APP_SETTINGS.md), [STREAM_QUALITY.md](STREAM_QUALITY.md),
  [DUAL_SCREEN.md](DUAL_SCREEN.md), [EXTERNAL_DISPLAY.md](EXTERNAL_DISPLAY.md), [LAUNCH_TRANSITION.md](LAUNCH_TRANSITION.md),
  [ICONS.md](ICONS.md), [LOCALIZATION.md](LOCALIZATION.md), [HANDHELD_DEVICES.md](HANDHELD_DEVICES.md).
- Issues [#9](https://github.com/DELOS-STREAM/DELOS-android/issues/9) (screen shapes), [#20](https://github.com/DELOS-STREAM/DELOS-android/issues/20) (UI, controller and touch validation), [#85](https://github.com/DELOS-STREAM/DELOS-android/issues/85) (streaming time).
