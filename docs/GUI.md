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
5. Overlays such as the **portal and the game loader blur the content behind them**, and **every semi-transparent
   surface is glass**: it blurs what lies behind it for real ([Glass](#glass)).
6. The design is slightly **rounded**, with generous paddings and margins.
7. An **interface scale** setting enlarges text, the status bar and the button hints. The covers keep their size.
8. Icons are **Phosphor icons**, bundled offline under the MIT license: Regular at rest and Fill on focus, with a
   smooth crossfade. An icon is chosen by the meaning of its action or setting; identical actions stay consistent
   across screens. The color-palette selector uses **Palette** for every palette choice. The approved
   implementation and the generation workflow are in [ICONS.md](ICONS.md).

## Glass

Every semi-transparent surface blurs what lies behind it for real (Haze). A surface is a panel, pill, chip, card,
overlay on a picture, dialog, menu, toast, hint bar pill, top bar pill, info box, dock, sheet or popover with a
translucent fill.

| Where the surface lies | Its glass |
|---|---|
| On a page, over the animated background | `glass(tint)`: the shell's background haze (`LocalBackdropHaze`, recorded once for the whole screen), 8 px |
| Inside the game overview | The same `glass(tint)`: the page's own backdrop haze; its cards `overviewGlass` |
| Over the whole screen (side menu, context menu, dialogs, filters, row popups, the result, the button capture, the problem report card) | `backdropBlur(haze, …)` over the shell's screen haze, which records the screen only while one of them is up (and 600 ms after) |
| On a picture (a cover's badges and Game info chip, an artwork tile's caption and Animated badge, the launch scene's Cancel pill) | `backdropBlur` over the picture itself: the picture is a `hazeSource` of its own, 16 to 24 px |
| Over the stream's video (a `SurfaceView`): the statistics, the classic notification and performance overlays | Nothing can blur it: a darker tint, black at 70 % |
| The stream menu's window | The window blurs the stream behind it (`FLAG_BLUR_BEHIND`, Android 12 with cross-window blur); its tiles and cards are drawn over that blur |

Not surfaces, and so flat: full-screen dims and veils (scrims), dividers, tracks, bar segments, text shadows,
gradient masks and fades, focus rings and glows, the stream's video. A fill inside a glass surface stays flat too: a
blur is never nested in a blur. The companion panel's surfaces stay flat: its background is a flat opaque colour, so a
blur would draw the same colour.

Performance rules:

- A screen blurs through one shared source (the background haze, or the screen haze for the layers). A picture is
  the only other source, its own, and a home cover records itself only while it carries glass (a badge, a corner pill,
  the spinner, the focused cover's Game info chip): no extra layer per cover.
- A grid of many small surfaces is not glass one by one: the settings search's letter keys stay a flat tint, since
  one blur per key (30 to 45 of them) cost janky frames on the emulator (0.6 % to 9.8 %).
- No blur radius is animated per frame; a glass surface fades with its alpha.
- Below Android 12 Haze draws the tint alone, made more opaque (`backdropBlur`'s fallback).

`TranslucentSurfaceTest` fails on a translucent `.background(…)` (a palette surface or scrim, an alpha below `FF`, a
`.copy(alpha = …)`) on a line without glass. Its allowlist names every exception and why; an entry that matches no
line any more fails too.
## Focus spacing

A focused element's ring, glow and lift never touch or overlap the next focusable element. The rule holds for every
focusable: covers, carousels, buttons, pills, chips, list rows, settings rows, menu items, the top navigation, filter
chips, keyboard keys and the stream menu tiles.

- The **reach** of a focus look is how far it extends past the element's edge at rest, toward a neighbour:
  the scale growth at that edge plus the ring outset plus the ring band.
  - A ring drawn inside the element's scaled layer grows with it:
    reach = (scale − 1) · size / 2 + scale · (outset + band).
  - A ring drawn around the scaled bounds does not grow with it: the overview's shared ring, the gliding rings and
    plates. For these, reach = (scale − 1) · size / 2 + outset + band.
  - The size is the longest element of the group along the axis toward the neighbour.
- Two focusable siblings are at least **2 · reach + 8** stage pixels apart. The 8 px stay clear even while the ring
  glides to the neighbour. The glow is a soft shadow and counts only through this clear space.
- Gaps grow. Elements never shrink to make room. A layout that runs out of room scrolls, peeks less or shows one
  column fewer.
- **Dense grids** use the inner-ring variant: the letter picker of the settings search and the Full keyboard. Their
  keys do not lift and draw the ring inside the key. The reach is then zero or negative, and the existing gaps
  already hold the 8 px of clear space.
- **Rows of labelled pills** (buttons, chips) are as wide as their label, so a fixed gap cannot hold the rule. Such
  a row (`FocusRow`) widens its gap to the rule for its widest pill.
- In code, `ui/FocusSpacing.kt` holds the looks (`FocusLook`) and the fixed groups (`FocusGap`). Each screen takes
  its gap from a named `FocusGap` or computes it with `FocusLook.minGap`. `FocusSpacingTest` checks every group
  against its look.

Examples, in stage pixels:

| Group | Element | Look | Reach | Gap |
|---|---|---|---|---|
| Home covers | 300 wide | 1.055, ring 5 out, band 5 | 18.8 | 46 |
| Overview screenshots and videos | 520 wide | 1.06, ring 6 out, band 4 | 25.6 | 60 |
| Overview similar games | 260 wide | 1.06, ring 6 out, band 4 | 17.8 | 44 |
| Stream menu tiles | 150 wide | 1.12, ring band inside the 186 box | 18 | 44 |
| Settings rows | up to 120 high | 1.02, plate ring band 3 | 4.2 | 18 |
| Full keyboard keys | any | no lift, band 4 at 1 px inside | −1 | 6 |

## Focus row navigation

Moving one row up or down, with a controller or a keyboard, puts the focus on the first item of the new row: the
leftmost focusable whose vertical centre lies in that row's band. It never lands on the horizontally nearest item.

- Returning to the row the focus came from restores the item it left. For example, Down from the top navigation
  returns to the cover that had the focus.
- A row that scrolls sideways starts at its leftmost item on screen, not at its first item.
- The media viewer's dock: Down from the video timeline goes to Play.
- 2-D grids keep their column: the artwork picker grid, the avatar grid, the settings-search letter picker and the
  Full keyboard.
- The owner approved these four exceptions. Any other exception needs the owner's approval.
- The row is found from the item the move would reach without the rule: every item in that direction whose vertical
  centre lies within that item's height. A screen's own focus override or key handling comes first. A move never
  leaves the open dialog, menu or side layer.
- In code, `ui/RowFirst.kt` holds the rule, `ui/RowNavigation.kt` applies it to the shell, and
  `OverviewNavigator` applies it to the game overview. `RowFirstTest` and `OverviewRowFirstTest` check it.

## Section finder

Code comments cite this specification as `docs/GUI.md, "<section>"`. The table names the document that holds each
cited section.

| Cited section | Document |
|---|---|
| "Focus spacing", "Focus row navigation" | This document |
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
