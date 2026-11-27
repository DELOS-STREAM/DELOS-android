# Screen shapes

## Purpose

The Delos shell runs in windows from 1:1 up to 21:9 (issue #9). This document specifies how the stage is planned from
the window size, the shape classes, the layout of every screen on a wider, taller or compact stage, the stream's
geometry on other shapes, the companion screen's stage, and the debug tools that draw and compare the shapes: the
shape simulator, the screen previews and the contact sheets.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Orientation, stage planner, shape classes, safe area | Index and design principles: [GUI.md](../GUI.md) |
| Rules for every shape: tab rows, focus room, fades | The 16:9 screens: [START_AND_HOME.md](START_AND_HOME.md), [ARTWORK.md](ARTWORK.md), [GAME_INFO.md](GAME_INFO.md), [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) |
| Screens on a wider or taller stage | Controls and text input: [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md) |
| Compact stages and the compact settings family | Motion, toasts, problem report card, colors: [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| The companion screen's stage and card rules | Content per screen of the companion, its state and command flow: [DUAL_SCREEN.md](../DUAL_SCREEN.md) |
| The stream on other shapes: on-screen controls, saved layouts, key grid, orientation, video rectangle | Stream menu at 16:9: [STREAM_MENU.md](STREAM_MENU.md); Full keyboard: [FULL_KEYBOARD.md](FULL_KEYBOARD.md) |
| Shape simulator, screen previews, contact sheets | Shell structure and stream exits: [SHELL_ARCHITECTURE.md](SHELL_ARCHITECTURE.md) |
| | The displays of the target handhelds and the class each gets: [HANDHELD_DEVICES.md](../HANDHELD_DEVICES.md) |

## 1. Orientation and supported windows

1. The shell is landscape only: `sensorLandscape` on `ShellActivity` and on `ShortcutTrampoline`, which shows the
   launch scene.
2. The stream activity keeps its own orientation logic ([Orientation](#94-orientation)).
3. The shell supports windows from 1:1 up to 21:9.
4. The 16:9 1080p look of the prototype stays pixel-identical.

## 2. Stage planner

`Stage()` hands the window size in dp to `StagePlanner.plan` (`ui/StagePlanner.kt`). The planner returns the dp per
stage pixel, the stage size in stage pixels and a `StageShape`. `LocalStage` (`StageMetrics`) carries `stageWidth`,
`stageHeight`, `shape` and the cutout `safe` insets next to `dpPerPx`.

| Element | Value |
|---|---|
| Scale | `min(w / 1920, h / 1080)`, unless that is under the legibility floor |
| Legibility floor | 0.43 dp per stage px |
| Reference panel | AYN Odin 2 Portal: 1920 × 1080 at the 369 dpi it reports = 832.5 × 468.3 dp, 0.4336 dp per stage px |
| Distance of the floor from the reference | The floor sits 0.8 % below it, so text is never physically smaller than there |

1. Under the floor the scale stays at 0.43 and the stage shrinks (fewer stage pixels across). This is reflow, not
   scaling.
2. Every 16:9 window of 826 dp width or more gets exactly 1920 × 1080 and the same `dpPerPx` as without the floor.
   This covers the Odin, the Thor's top panel and every 1080p panel at or below about 372 dpi.
3. 4:3 is 1920 × 1440 and 21:9 is 2520 × 1080, also unchanged by the floor.

### 2.1 Safe area

1. Backgrounds draw edge to edge.
2. The status bar and the hint bar are padded by the display cutout: `WindowInsets.displayCutout` in stage pixels,
   `StageMetrics.safe`. It is zero on the Odin, so nothing moves there.
3. Other edge-anchored content can read `LocalStage.current.safe`.

## 3. Shape classes

`StageShape` has four classes.

| Class | Rule |
|---|---|
| *Standard* | 16:9 within 3 % |
| *Wide* | Wider than Standard |
| *Tall* | Narrower than Standard, down to 4:3 (about 1.29 with the tolerance) |
| *Compact* | A stage under 1500 stage px wide (small panels, split-screen halves), or squarish (narrower than 4:3, 1:1) |

1. A shrunk stage can be Standard in aspect with fewer than 1920 stage px. Screens drawn for 1920 × 1080 do not fit it
   until they are made adaptive.
2. Screens read `px` / `tpx`. The Wide and Tall layouts are in
   [Screens on a wider or taller stage](#5-screens-on-a-wider-or-taller-stage), the Compact layouts in
   [Compact stages](#6-compact-stages) and [Compact settings family](#7-compact-settings-family).
3. The displays of the target handhelds and the class each gets: [HANDHELD_DEVICES.md](../HANDHELD_DEVICES.md).

## 4. Rules for every shape

### 4.1 Tab rows

Applies to `TabRow`, `TabRowLayout`.

1. A row of tabs with its L1 / R1 badges is either exactly centered on the stage or its outer edge is exactly flush
   with the content's edge. Nothing in between.
2. A centered row may overhang the content by 12 px in all; nothing is squeezed. At 1920 × 1080 the Settings row is
   12 px wider than the content: L1 begins 6 px left of the title, R1 ends 6 px right of the panel.
3. A row wider than that, or than the room beside a title or a button, scrolls in the room between the content's edges
   (`tabScroll`): fixed sizes and gaps, a fade at the edge with more behind it, the selected tab brought into view.

| Screen | Placement of the tab row |
|---|---|
| Settings | Centered |
| Compact Settings | Centered, or flush against the search circle when it would run into it |
| Per-app | Centered under the banner |
| Per-app, compact | Centered while the name leaves the room, else flush against the Reset all circle |
| Artwork | The R1 badge ends on the content's right edge (the earlier layout ended 65 px short of it) |

### 4.2 Focus room in scrolling layers

Owner's rule.

1. A layer that clips or fades (`fadeEdges`, `ScrollRow`) is made larger than its content with `Modifier.focusRoom`.
   The content is padded by the same amount and nothing moves. A focused item's scale, ring and glow are not cut.
2. The settings lists are 30 px wider on each side (`settingListViewport`: the plate's glow reaches 41 px beyond a
   row).
3. The Artwork grid is the stage's width.
4. Their edges fade only while content continues behind them (`fadeEdges(stage, scroll, …)`), so nothing is dimmed at
   the first and at the last row.

### 4.3 A focus is never cut

1. A scroll container and a layer with a fade clip at their own bounds. The ones that hold focusable items are larger
   than their place by the reach of the focus glow, and their content is padded by as much.
2. The layout does not move.
3. An edge fades only while there is more to scroll to on that side. The first item of a row at its start and the last
   one at its end are never faded.

| Layer | Extra room | Mechanism or reason |
|---|---|---|
| Circle rows | 84 px | `ScrollRow(bleed = …)`; the glow of a 220 px circle at 1.16 |
| Context menu's rows | 16 px | `Modifier.overdraw` |
| Home's row | Reaches down to the stage's bottom edge | The glow under a cover is about 110 px |
| Stream dock's scroll area | Starts 110 px above the tiles | |

## 5. Screens on a wider or taller stage

Every absolute 1920 / 1080 of a screen is `LocalStage.current.stageWidth` / `stageHeight`, with the same value at
1920 × 1080.

| Screen | Element | Rule |
|---|---|---|
| Settings | Search pill | Keeps its distance from the right edge (x = stage width − 390) |
| Per-app settings | Header | The stage width less the two 150 px margins |
| Per-app settings | List | Runs from x 132 to 38 px short of the right-anchored 520 px panel (stage width − 840 wide) |
| Artwork | Tabs, toolbar, filter strip, info strip | Anchored to the edges; the info strip is 194 px above the bottom |
| Artwork | Grid | Takes the height between the strips; the columns scale with its width (7 / 4 / 3 / 5 per 1720 px) |
| Filter modal | Height | Follows the stage (stage height − 260) |
| Debug info | List | Fills the stage (weighted list, edge padding) |

1. Circle rows (`Circles`: Devices, Start, Pair, Profile) stay one centered row on every shape. When the circles do
   not fit, the row scrolls sideways with an edge fade. While they fit it is the plain centered row.
2. The filter modal's chips, the Artwork sheet's options and the stream popover's key/command pills do not wrap. They
   scroll the same way through `ScrollRow` (`ui/Controls.kt`), which gives its row its own focus-scroll margin instead
   of the surrounding list's.
3. Nothing wraps into a second line except text and the info row's non-focusable facts and tags.

## 6. Compact stages

The compact shell screens are in `ui/Compact.kt`. They apply only when `stage.shape == COMPACT`; every other shape is
untouched. The stage is 1000–1500 stage px wide and 720–1100 high with the text as large as on the Odin, so everything
else reflows.

### 6.1 Rules

The owner's rules for a compact stage:

1. A row that does not fit scrolls: one row, a fade at the cut edges, the focus whole with the next item peeking in.
   It is never squeezed or wrapped.
2. Outer margins are at least 48 px (`Compact.MARGIN`). The status bar and covers start at 72. The cutout comes on
   top.
3. There are 24 px between neighboring blocks.
4. A focused element's growth, ring and 12 px of air fit the gap to its neighbor.
5. Circles stay circles and covers keep their ratio.
6. Text keeps its size.

| Element | Value |
|---|---|
| `StageMetrics.compactScale` | Stage height / 1080, 0.62 at the least; scales avatars, circles and the gaps of centered screens |
| Interface scale | 1.0 by default (100 %); zooms both bars |
| Status bar height counted by a compact layout | 104 × scale |
| Hint bar height counted by a compact layout | 92 × scale |

The geometry is in pure functions with unit tests over the stages 1099 × 957, 1488 × 893, 1191 × 893, 1108 × 831,
1051 × 1051 and 1082 × 722.

### 6.2 Status bar

`CompactStatusBar` in `ui/Bars.kt`, `ui/StatusBarFit.kt`.

1. The navigation's buttons are 56 px (64 elsewhere).
2. The connected PC is only its status dot with the PC glyph: green online, gray offline. Its name is in Home's hint
   bar, on the Devices screen and in the side menu's header, and it is the pill's content description.
3. There is no wordmark under 1280 bar px.
4. `fitStatusBar` places the navigation in this order:
   1. On the middle of the bar, while that leaves 24 px to the brand cluster and to the status cluster. The status
      cluster (the cellular signal on a device with a modem, Wi-Fi, battery, clock) is measured, so a 12-hour clock and "100%" count.
   2. Otherwise in the middle of the free space between the two clusters.
   3. When even that is too narrow (interface scale 1.3), the gaps inside the navigation (14 → 6) and the status
      cluster (28 → 16) tighten.
   4. Last, the battery's percentage goes.
5. The start screen's profile chip follows the same rule on the bar's line (`CompactProfileChip`;
   `LocalStatusBarEdges` tells it where the status cluster starts). Its name is cut by an ellipsis when the room is
   shorter.

### 6.3 Hint bar

`CompactHintBar`, `HintFit.kt`.

1. The note with its label, then the note alone, stay only while every hint keeps a label.
2. Otherwise the note goes and the hints degrade one by one.
3. The rank is: A, Back, the screen's own X/Y/adjust, L1/R1, the menu buttons.
4. The most important hint takes the fullest label that still fits with all the less important ones as bare buttons,
   so the labels of the least important go first.
5. A hint with a shorter existing word (`Hint.shortLabel`: Reset, Back, Search, Choose) tries it before it loses the
   label.
6. The buttons alone come last.

### 6.4 Centered screens

1. Centered screens (`CenterColumn`) keep clear of both bars and scroll when still too tall.
2. Headlines are 48 (not 64) and at most the stage's width less 2 × 72. A long one, "Enter this PIN on <PC>", wraps.
3. The avatar picker has 4 columns.

### 6.5 Circle rows

`Circles`, `CircleItem`, `ui/CircleRowFit.kt`; used by Start, Devices, Pair a PC, Profile.

| Element | Value |
|---|---|
| Circle's box | Always square at its `compactScale` size |
| Item width | The widest of its circle, its label and its status line |
| Label | One line, at most 260; a longer PC name is cut in its middle |
| Status line | At most 240; one line where the text allows, two for the note of "Pair a new PC" |
| Gap | The prototype's 56 while the items fit with it, 44 when not |
| Focused circle | Grows 17 px to each side, its ring 12; a gap of 44 leaves 12 px of air |
| Scroll position | The focused circle is 150 px from the cut edge, so the next item peeks in |
| Compact outer margin | 72 px (the circle's edge; its ring has more than 40 px of it) |
| Peek gap (`CircleRowLayout.peekGap`) | The one gap (44 up to 120) that shows at least 60 px of the item cut by the stage's edge at the start and at the end of the row |

1. What does not fit with the 44 gap scrolls (`ScrollRow`).
2. Start and Devices show every paired PC, also at 16:9; there is no limit of five. At 16:9 the row scrolls from seven
   circles on.
3. In a compact row, what does not fit inside the outer margin scrolls with the peek gap, so there is always a visible
   hint that the row goes on.
4. `--ei preview_pcs <n>` gives the previews n PCs.

### 6.6 Home

`screens/HomeCompactLayout.kt`. Home is laid out top-down between the bars.

| Order | Element | Rule |
|---|---|---|
| 1 | Gap under the status bar | At least 16 px |
| 2 | Title box | 64 × scale to 160; centered between the status bar and the info row: the same gap above and under it, at least 16 |
| 3 | Info row | One line, as tall as the streaming-time block, 44 × scale. On a stage too narrow for the block beside the badges: the block, 12 px and a row of badges |
| 4 | Gap to the focused cover's ring | At least 24 px |
| 5 | Covers | See the cover height below |
| 6 | Gap from that ring to the hint bar | At least 28 px |

The cover height is the smallest of three values and never under 0.45 of the prototype's:

1. 0.40 of the stage's height.
2. The height at which the wide first cover, one whole cover and a third of the next end 24 px before the right edge.
   There the row's right fade (72 px, clear at 8) is still opaque enough to read. The wide first cover is focused: its
   left edge is on the 72 px margin, so the row starts that much plus its growth further in and the ring keeps 48+.
3. What the height leaves with comfortable gaps: 40 above, 44 under the covers.

Further rules:

1. The 722-high stage has 0.45. There the gaps keep to their least where the floor needs it.
2. The comfortable size counts the two rows of badges that the info row replaces, so the covers keep the size they
   have with those two rows.
3. What is left over first grows the title box, then goes to the gaps 0.5 : 0.5 : 2 : 2 (above the title : under it :
   above the covers : under them).
4. The gap between two covers is 44 × scale, at least the room a focused cover needs (27.5 at 0.6).
5. A placeholder cover sets its title smaller (down to 0.4) until its longest word fits a line.

### 6.7 Side menu

`SideMenuScroll`: the scroll area and its fade layer reach 40 px sideways and 56 px up and down beyond their place
(`overdraw`, the content padded by the same), so the focused row's scale, ring and glow are never cut. A fade exists
only at an edge with more to scroll to.

### 6.8 Context menu on a compact stage

| Element | Value | Constant |
|---|---|---|
| Distance from the sides | 48 px | |
| Card start | 8 px under the status bar | `ContextMenuLayout.compactTopMargin` |
| Card end | 16 px above the hint bar | `ContextMenuLayout.compactBottomMargin` |

1. A taller menu scrolls inside the card.
2. A menu that opens under its element (the PC circles) and fits neither under nor above it goes beside it.

### 6.9 Stream menu on a compact stage

`DockGeometry` in `StreamMenuLayout.kt`, `DockScroller` in `StreamMenuBar.kt`.

1. The tiles, gaps, ring, labels and caption are the 16:9 sizes.
2. The row of nine is wider than the stage and scrolls.
3. The wrap-around (Left on Resume) glides to the other end, and a finger drags the row.
4. The popover's caret follows its tile on the stage.
5. While a card is open, the game's name and the hints fade out where the card covers them.
6. A stage under 800 px high sets the two hints side by side and sinks the dock without its labels (they fade out),
   which leaves the card three rows instead of two.

| Element | Value |
|---|---|
| First tile | 80 px from the left edge; its ring and glow are whole |
| Focused tile | 150 px from the edges while the row can still move (`dockScrollFor`); the next tile peeks in |
| Fade | 80 px, only at an edge where the row goes on |
| Popover card | Keeps 48 px from the sides and the top and scrolls inside |
| Dock | Sits 48 px above the bottom |

### 6.10 Problem card and toast

The problem card and the toast are 48 px from the edge, under the status bar.

## 7. Compact settings family

Issue #9. The family covers `StageShape.COMPACT`: 1:1 up to small 16:9 panels, stage about 1050 to 1490 px wide.

1. Text keeps its stage size (the legibility floor); the room around it shrinks.
2. `ui/CompactRows.kt` has `cpx` (stage px × `compactScale`, see [Compact stages](#6-compact-stages)) and `targetPx`
   (never under 64 px for a touched or focused element, never above the original).
3. The outer margin is the 48 px floor plus the cutout (`SettingsLayout.margin`).
4. The geometry that can be computed is pure and tested on every stage size of `tools/layout/shapes.txt`
   (`screens/FamilyLayout.kt`, `FamilyLayoutTest`).
5. The owner's rules it follows:
   - Scroll, never squeeze or wrap.
   - At least 24 px between blocks.
   - A focused element (its scale, ring and glow) has room and is never cut by a scrolling or fading layer.

### 7.1 Settings, per-app settings, Debug info

There is no 520 px panel. The list is full width and the description is a dock under it (`HelpDock`,
`HelpDockLayout`).

| Element | Value |
|---|---|
| Dock height | About a quarter of the stage's height, 210 to 270 px (the owner's request) |
| Dock header: icon disc | 48 px |
| Dock header: name | 22 px, one line |
| Dock header: impact legend | At the right, when the whole name fits beside it |
| Gap between list and dock | The list ends 16 px above the dock with a fade |

The dock gives way down to 210 px so the list keeps three whole rows where it can.

| Stage height | Whole rows of the list |
|---|---|
| 950 px and more | Three |
| 893 px | Two in Settings |
| 831 px | Two |
| 722 px (Pocket Micro) | One, the next row fading over the dock |

1. The dock's header stays put.
2. Everything else is ONE body under the header. It scrolls as a whole (the auto-scroll of long help) and fades at an
   edge only while it continues behind it.
3. The body holds, in this order:
   1. Per game, the source box first ("Custom for this app · Profile value …" / "From profile", wrapping inside the
      body).
   2. The value's detail and the help.
   3. The legend when the header has no room.
   4. What its bars mean.
   5. The autosave note.

Settings header:

1. Settings has no title, so the 8 tabs fit.
2. Only the selected tab keeps its label. L1 / R1 still step through the tabs.
3. The search is a 64 px circle flush with the content's right edge.

Per-game header:

1. The header is ONE row on a 220 px banner: the eyebrow and the name at the left, the tabs, and Reset all.
2. Reset all is the same circle. It carries the count of custom settings as an accent badge (the 16:9 header's "N
   custom settings" chip).
3. Per-app plain rows drop "From profile" (the dock says it). Custom rows keep their CUSTOM badge and profile line.

### 7.2 Settings, per-app settings, Debug info on a dual-screen device

1. While a companion window shows the description, it is not drawn at all on the main panel: neither the 16:9 panel on
   the right nor the compact dock under the list.
2. The list takes its room: the full content width at 16:9, the full height down to the hint bar when compact. The
   margins and fades are the same.
3. "Showing" is the window's real state (`CompanionSession.showsHelp`, set by the window's own composition), never
   just "a second display exists". The description is not lost while a window is starting, refused or gone.
4. Without a companion the description stays on the main panel; the 16:9 screens are pixel-identical.

### 7.3 Settings search

1. The field has the count of results beside it.
2. The letters are two rows of 64 px keys that scroll sideways together (`CompactLetterPicker`): the alphabet runs on
   from the first row into the second, Space is the last key, 26 px between keys, faded ends.
3. Delete and Clear are two circles that stay at the right.
4. The results are under them at the full width. Up from the first result goes back to the keys.

### 7.4 Artwork

`ArtworkLayout`. There is no title.

| Row | Content |
|---|---|
| First | The game chip and the collapsed tabs (flush right) |
| Second | The filter strip (one row that scrolls when the filter chips need more) and the Search and Filter pills |

1. The game chip drops year and id, then its image count, before the name would be cut.
2. The Search pill has the longest of "Search for another name" / "Search name" / "Search" that fits.
3. The tabs scroll (`tabScroll`) when the chip's 300 px minimum leaves them too little.
4. The grid keeps each kind's tile shape, with as many columns as keep the tile at the least width or more.

| Tile shape | Least tile width |
|---|---|
| 2:3 | 190 px |
| 920:430 | 210 px |
| 1920:620 | 290 px |
| 2:1 | 190 px |

5. Covers stop at 210 px and the room left goes into the gaps: 4 columns of 210 px with 54 px gaps at 1099 × 957,
   38 px at 1051 × 1051 (the earlier layout stretched the covers to 250 to 300 px).
6. A viewport too short for a focused row with its ring, glow and a peek of the next takes more, smaller tiles, never
   under 72 % of those widths. A 190 px cover is 285 px tall.

| Stage height | Smaller tile width |
|---|---|
| 893 px | 173 / 158 px |
| 831 px | 145 px |
| 722 px (Pocket Micro) | 141 px |

7. The info strip stays on every stage but the Pocket Micro, which has no room for it.
8. The first row's ring ends 24 px under the toolbar.
9. On wide / background / logo tiles under 300 px "In use" is a small check circle (the words would run into the
   tile's name).
10. The filter sheet lies over the toolbar with the live count in place of its title and scrolls its options with faded
    edges.
11. The library filter modal keeps the 48 px margins.

### 7.5 Keyboard

The keyboard (`FullKeyboard`) is width-aware and has no compact layout of its own. The check is under
[Verification](#verification).

## 8. Companion screen

Issue #76, [DUAL_SCREEN.md](../DUAL_SCREEN.md).

1. The second panel of a dual-screen device is a `CompanionActivity` on that display (own task, no key focus) with its
   own `Stage()`.
2. `LocalStage` there is planned from that display's size and density, independent of the main panel's. The Thor's
   1240 × 1080 panel is a Compact stage.
3. Rule 0: every screen has useful extra content there and none of it ever scrolls.
4. Each page is one glass card built from the shared tokens: Phosphor icons, gold primary, sea-glass secondary, impact
   chips.

| Element | Value |
|---|---|
| Text | At least 28 stage px |
| Targets | At least 64 stage px |
| Margins | 48 px |

5. The cards are:
   - the focused game;
   - the focused PC with its actions (Wake, Library, Rename, Default, Unpair, Remove);
   - the PIN in very large digits;
   - the focused artwork at its own ratio;
   - the active filters with Reset;
   - the profile;
   - the launch steps;
   - the full text of a result or problem;
   - the description of the focused settings row;
   - the stream tiles and keyboard;
   - for every other screen, a status card with its purpose, the connected PC, the clock, the battery and the main
     hint bar's entries.
6. What does not fit is dropped by priority, cut at a sentence boundary or counted (`CompanionFit`, `FitColumn`).
7. The stream menu's long lists are paged, never scrolled.
8. The settings screens publish the description of the focused row (`PublishCompanionHelp`, "Settings help" in
   [DUAL_SCREEN.md](../DUAL_SCREEN.md#9-settings-help)). The main panel then gives the room of its own description to the list
   ([section 7.2](#72-settings-per-app-settings-debug-info-on-a-dual-screen-device)).
9. Content per screen, state and command flow, burn-in policy, the idle-dimming setting (Settings → Display, only with
   a companion display) and previews: [DUAL_SCREEN.md](../DUAL_SCREEN.md#7-companion-content), "Companion content" and the sections after it.

## 9. The stream on other shapes

Issue #9. `OscGeometry`, `OscDefaultLayout`, `VideoFit`, `KeyBoardGeometry` and
`StreamWindowController.requestedOrientation` are pure.

1. They are covered by `OscGeometryTest`, `KeyBoardGeometryTest`, `VideoFitTest` and `StreamWindowControllerTest` over
   1:1, 5:4, 31:27, 4:3, 3:2, 16:10, 16:9 (four resolutions), 20:9, 21:9, 32:9 and portrait.
2. Every 16:9 output is pinned against the old arithmetic (copied into the tests), so 16:9 at any resolution is
   identical. So is every shape of 3:2 and wider.

### 9.1 On-screen controls

`VirtualController`, default layout on the 128 × 72 grid. One unit is height / 72. The panel is the frame the controls
are drawn in (their positions are margins in it), laid out again when its size changes. It is not the display metrics:
on Android 13 they leave out a navigation bar at the side and a display cutout, which the full-screen stream and the
edge-to-edge shell draw under.

| Panel | Layout |
|---|---|
| 3:2 and wider (108 units wide or more) | The unshrunk grid: left cluster against the left edge, right cluster against the right edge, Back / Guide / Start and the trackpad centered |
| Squarer than 3:2 (4:3, 5:4, 31:27, 1:1) | The whole layout shrinks by `width / (1.5 × height)`: 8/9 at 4:3, 2/3 at 1:1 |

1. From 3:2 up, a wider panel only widens the gap in the middle. This is what `rightDisplacement` does. Without the
   shrink, `rightDisplacement` alone goes negative and the clusters cross below 16:9.
2. Squarer than 3:2, the shrink lets the clusters and the middle row still fit side by side.
3. Squarer than 3:2, the upper controls (sticks, face buttons, triggers) keep to the top edge. The lower ones (D-pad,
   right stick, L3 / R3, Back / Start, trackpad) keep to the bottom edge.
4. Buttons keep their proportions and nothing overlaps that does not at 16:9.
5. The smallest face button at 1:1 is 6.7 units: 67 px on a 720 px square (about 6.6 mm at the RG Cube's 257 ppi).
6. There is no minimum size.

### 9.2 Saved layouts

`OscPlacement`, `OscLayoutStore`. A layout is the same on every resolution and shape (#101).

1. The default layout is never stored. `OscDefaultLayout` computes it for the panel every time.
2. Leaving the editor stores only the controls the user changed (moved, resized or turned off). A control that is on
   and where the default puts it is not stored and follows the default.
3. A stored control keeps to its anchor, the side the default layout keeps it to (`OscDefaultLayout.anchorX`,
   `anchorY`, read from the 16:9 grid): the left cluster to the left edge, the right cluster to the right edge,
   Back / Guide / Start and the trackpad to the horizontal centre; the upper half to the top edge, the lower half to
   the bottom edge.
4. Its offset from the anchor and its size are in layout units (`Panel.unit()`, height / 72, times the shrink below
   3:2), so a layout made on the Odin looks the same on a phone, a tablet or 21:9, and the middle stays centred.
5. The result is always on the panel.
6. Storage: the profile's `OSC` preferences, one JSON document per layout under `layout.<id>`: `version` (2), `id`
   (`global` for every game; a game's own layout gets its own id) and `elements`, by element id: `enabled`,
   `anchorX` (`LEFT`, `CENTER`, `RIGHT`), `anchorY` (`TOP`, `BOTTOM`), `x`, `y`, `width`, `height` in units.
7. Version 1 stored every element in absolute pixels under its element id (`LEFT`, `TOP`, `WIDTH`, `HEIGHT`,
   `ENABLED`; `REF_W` and `REF_H`, the panel's size, since phase D). It is converted once when a layout is loaded:
   - With its panel size: dropped when it is that panel's default, else converted against that panel.
   - Without it: elements that are an old default of one panel (Delos' or Artemis' before it, found by
     `OscDefaultLayout.oldDefaults`; at least two elements must agree) are dropped, the rest are converted against the
     display metrics, which those versions laid out on.
   - The Odin had Artemis' default of 1920 × 1025 (the metrics without the 55 px gesture bar) stored for Back, Guide,
     Start and the trackpad: Guide's centre at 880, the trackpad's at 853, so they sat left of the centre in the stream
     and the preview, whatever the panel.
8. `--es preview_saved_from <W>x<H>` shows on the emulator how a layout changed on another panel comes out.

### 9.3 Key grid

`KeyBoardController`, the editable key overlay. It is hidden in the Delos UI; the code is kept.

1. Its saved layouts are still absolute pixels with the panel's size (`REF_W`, `REF_H`), carried to another panel by
   `OscGeometry.mapSaved`: scaled by `min(width / REF_W, height / REF_H)`, keeping the distance to the nearest edge by
   thirds of the old width and height.
2. The keys are limited to an eighteenth of the width.
3. The D-pad is anchored to the right edge. Both it and the stick are clamped onto the panel.
4. At 1:1 a key is 4 units, 40 px on 720 (about 4 mm): too small to use by touch.
5. The Full keyboard (Compose, stage based; [FULL_KEYBOARD.md](FULL_KEYBOARD.md)) is the keyboard to use there.

### 9.4 Orientation

1. `isSquarishScreen` has the threshold long side / short side under 1.3: 1:1, 5:4, the 31:27 panels.
2. 4:3 at 1.333 is not squarish and keeps the "lock to the current orientation" rule.
3. A squarish panel shows a stream about as large in either orientation, so it always follows the shape of the video:
   landscape for 16:9 or square, portrait for a taller stream.
4. A squarish panel does not use `FULL_USER` (any orientation, flipping with the accelerometer of a tilted handheld),
   also when neither the on-screen controls nor a native resolution ask for a lock.
5. A square native stream (the RG Cube's 720 × 720) locks landscape, not portrait.
6. The on-screen controls force landscape unless the resolution is native.

### 9.5 Video rectangle

`VideoFit.measure`, used by `StreamContainer` and `StreamView`.

| Mode | Rule | Examples |
|---|---|---|
| Fit | Keeps all of the picture and bars the long side of what is left over | 16:9 on 4:3 is 960 × 540 with 90 px bars; on 1:1 720 × 405 with 157 / 158 px bars; on 21:9 pillarbox 300 px each side |
| Fill | Covers the panel; the parent crops the overflow evenly | 16:9 on 1:1 keeps the middle 720 of 1280 px, 56 % |
| Stretch | Fills the panel whatever the aspect | |

1. There is no integer-scale or 1:1 pixel mode in this app.
2. `--es preview_screen stream_video --es preview_state <fit|fill|stretch> [--es preview_video <W>x<H>]` draws it.

### 9.6 Stream shell parts

1. The loader, result card, exit portal and statistics panel run on the stage.
2. In the compact statistics line at 1:1 the last figure wraps onto a second line.
3. The two backdrop blur radii (`StreamExit`, `StreamMenu`) are `50 × min(width / 1920, height / 1080)` px. This is the
   stage scale, not the legibility floor, and it is kept that way.
4. Nothing else in `binding/` or the stream shell hard-codes 1920 / 1080 / 16:9.

## 10. Shape simulator

Debug builds only: `ShapeSimulator` in `src/debug`. Release builds have no-op stubs in `src/release`.

1. The simulator draws the whole shell, or any `preview_screen`, in a centered box of the simulated panel's pixel size
   with `LocalDensity` set to the simulated density.
2. The box is scaled down uniformly when it does not fit. The density is scaled with it, so the dp maths is the
   panel's.
3. Outside the box it is black.
4. A strip at the bottom names the size, dpi, dp size, stage and class.
5. The strip carries a pink chip, *SIMULATED · name · tap to end*, so a running simulation is never forgotten. A tap
   on the chip ends it. The chip is never focusable.
6. While nothing is simulated the same boxes fill the screen at the real size and density, so starting or ending a
   simulation keeps every screen's state.

### 10.1 Debug menu

Path: Settings → About → Debug info → *Screen shape simulator*, debug builds only. `ShapeSimulatorMenu` adds the rows
through `DebugToolRow`; the release build adds none. The texts are English like the crash trigger row.

| Row | Function |
|---|---|
| *Simulated screen* | Opens a choice list (the context-menu component, D-pad and A / B) with *Real screen* and every shape of `ShapeCatalog` (name, pixels, dpi, e.g. "AYN Thor, bottom · 1240x1080 @420") |
| *Custom size* | The shared text dialog with width, height and dpi |
| *Cutout* | Steps through none / left / top / right / notch |
| *Dual-screen simulation* | Picks a dual-screen device of docs/HANDHELD_DEVICES.md (Thor, RG DDS, Pocket DS, Retroid + add-on) or Off |
| *Companion display* | Sets the second panel alone |
| *Companion custom size* | Sets the second panel alone |
| *Swap panels* | Exchanges main and companion (Settings → Display → Main screen "Swapped") |

1. The cutout insets are 96 px, 56 px for the notch. They are drawn in the panel and given to the stage as the safe
   area.
2. A choice applies live and survives restarts. It is kept in a preference file of its own
   (`delos_debug_shape_simulator`), never in a profile.
3. The adb extras ([section 10.3](#103-adb-extras)) override the choice for that launch only and are not remembered. A
   later menu change wins.
4. The list is the one `ShapeCatalog` (`src/debug`). The simulator, this menu and `tools/layout/shapes.txt` use it.
5. `tools/layout/shapes.txt` stays a plain-text mirror of the catalog's *sheet* shapes for the scripts.
   `ShapeCatalogTest` fails when they differ, so change both together. The catalog also holds more devices than the
   sheets.

### 10.2 Dual-screen simulation

The simulation runs on one physical screen, e.g. the Odin.

1. It draws the shell in the main panel's shape and the companion window's content in the second panel's shape.
2. The two are side by side or stacked, whichever shows them larger (one common scale), with a thin divider and a
   label on each panel.
3. The companion half is the very `CompanionContent` of `CompanionActivity` in the same `CompanionTheme`.
4. It is fed with the same state the real window gets: `ShellState.companionState()` (the pages the shell's screens
   publish, from every screen) or `companion_fake`.
5. It runs with `inWindow = true`, so the settings give their description to it exactly as with a real second display.
6. It is touch-only (`focusProperties { canFocus = false }`): the controller keeps driving the main half.
7. Main is the panel with more pixels, as `DualScreen` "Automatic" picks.

### 10.3 Adb extras

| Extra | Meaning |
|---|---|
| `--es preview_shape <W>x<H>@<dpi>` | Simulates that panel alone |
| `--es preview_cutout <L>,<T>,<R>,<B>` | Insets in simulated pixels, drawn as a notch |

They combine with `preview_screen` and `preview_state`:

```
adb shell am start -S -n io.github.meitoncz.delos.debug/com.limelight.shell.ShellActivity --es preview_shape 1240x1080@420 \
  --es preview_screen home
```

## 11. Screen previews

Screen previews with fake data run in debug builds (`ScreenPreviews` in `src/debug`) and need no PC or network. The
extra is:

`--es preview_screen <home|artwork|devices|start|pair|filter|dialog|loader|result|settings|settings_search|debug_info|profile|game_options|side_menu|home_context|problem|stream_exit|boot|create_profile|pair_pc|stream_stats|stream_osc|stream_video>`

| `preview_screen` | Note |
|---|---|
| `home` | |
| `artwork` | |
| `devices` | |
| `start` | |
| `pair` | |
| `filter` | |
| `dialog` | |
| `loader` | |
| `result` | |
| `settings` | The real screen |
| `settings_search` | The real screen |
| `debug_info` | The real screen |
| `profile` | The real screen |
| `game_options` | The real screen |
| `side_menu` | |
| `home_context` | The game menu opened by the Start key 3 s after the start |
| `problem` | |
| `stream_exit` | |
| `boot` | |
| `create_profile` | |
| `pair_pc` | |
| `stream_stats` | Takes `--es preview_state <full|compact>` and `--es preview_position <TOP_LEFT|…>` |
| `stream_osc` | Draws the stream's own geometry ([section 9](#9-the-stream-on-other-shapes)) |
| `stream_video` | Draws the stream's own geometry ([section 9](#9-the-stream-on-other-shapes)) |

1. They run next to the older previews: `context_menu`, `top_menu`, `settings_tabs`, `settings_list`, `game_settings`,
   `stream_menu`, `full_keyboard`, `toast`.
2. `game_settings` is the older component preview of the per-app list.
3. The preview tells the settings capability probe there is no PyroWave, because its native Vulkan probe dies on the
   emulator's SwiftShader.
4. The shell's global keys reach the previewed shell too: L1 / R1, X, Y, Start, Select. The activity's own listener is
   not set in a preview.
5. `adb shell input keyevent 103` switches a previewed screen's tab and `100` opens its filter sheet, next to the
   D-pad keys (19 to 23).

Other extras of the previews:

| Extra | Preview | Specified in |
|---|---|---|
| `--ei preview_pcs <n>` | Circle rows | [Circle rows](#65-circle-rows) |
| `--es preview_saved_from <W>x<H>` | `stream_osc` | [Saved layouts](#92-saved-layouts) |
| `--es preview_state <fit|fill|stretch>`, `--es preview_video <W>x<H>` | `stream_video` | [Video rectangle](#95-video-rectangle) |
| `--es preview_shape`, `--es preview_cutout` | Every preview | [Adb extras](#103-adb-extras) |

## 12. Contact sheets

The tools are in `tools/layout/`.

| File | Role |
|---|---|
| `shapes.txt` | Lists the shapes: names, `W x H @ dpi`, optional cutout. A mirror of the sheet shapes of `ShapeCatalog` |
| `screens.txt` | Lists the previews |
| `sheets.sh` | Takes the screenshots and builds the contact sheets. Needs the fail-closed adb wrapper and the stream gate helper |

```
tools/layout/sheets.sh --adb <adb wrapper> --gate <stream gate> shapes <label>
tools/layout/sheets.sh --adb <adb wrapper> --gate <stream gate> baseline [label]
tools/layout/sheets.sh compare <labelA> <labelB>
```

| Command | Result |
|---|---|
| `shapes <label>` | Every screen in every shape -> `build/layout-sheets/<label>/` |
| `baseline [label]` | Every screen natively (16:9), for the identity check |
| `compare <labelA> <labelB>` | Pixel diff of two runs; exit 1 when a screen differs |

1. `sheets.sh` takes a screenshot first and refuses to run when the gate reports a stream. The gate is asked again
   before every launch.
2. It starts previews by intent only and never presses anything.
3. It starts the normal shell again at the end.
4. Contact sheets land in `<label>/sheets/by-screen/` and `by-shape/`.
5. `compare` ignores the status bar's Wi-Fi, battery and clock by default. `--mask none` compares everything.
   `--tolerance N` allows small differences.

## Code map

| Path or class | Role |
|---|---|
| `ui/StagePlanner.kt` (`StagePlanner.plan`, `StageShape`) | Plans the dp per stage pixel, the stage size and the shape class from the window size |
| `Stage()`, `LocalStage`, `StageMetrics` | Hand the window size to the planner; carry `stageWidth`, `stageHeight`, `shape`, `safe`, `dpPerPx` |
| `TabRow`, `TabRowLayout`, `tabScroll` | Tab rows: centered or flush, scrolling when too wide |
| `fadeEdges`, `ScrollRow` (`ui/Controls.kt`), `Modifier.focusRoom`, `Modifier.overdraw`, `settingListViewport` | Scrolling and fading layers with room for the focus |
| `ui/Compact.kt` (`Compact.MARGIN`, `StageMetrics.compactScale`) | Compact shell screens |
| `ui/CompactRows.kt` (`cpx`, `targetPx`) | Compact sizes of the settings family |
| `screens/FamilyLayout.kt`, `SettingsLayout.margin` | Pure geometry of the compact settings family |
| `HelpDock`, `HelpDockLayout` | The description dock under the compact settings lists |
| `CompactLetterPicker` | The two rows of letter keys of the compact Settings search |
| `ArtworkLayout` | Compact Artwork layout |
| `CompactStatusBar` in `ui/Bars.kt`, `ui/StatusBarFit.kt` (`fitStatusBar`), `CompactProfileChip`, `LocalStatusBarEdges` | Compact status bar |
| `CompactHintBar`, `HintFit.kt`, `Hint.shortLabel` | Compact hint bar |
| `CenterColumn` | Centered screens |
| `Circles`, `CircleItem`, `ui/CircleRowFit.kt`, `CircleRowLayout.peekGap` | Circle rows |
| `screens/HomeCompactLayout.kt` | Compact Home |
| `SideMenuScroll` | Side menu scroll area and fade layer |
| `ContextMenuLayout.compactTopMargin`, `ContextMenuLayout.compactBottomMargin` | Compact context menu margins |
| `DockGeometry` in `StreamMenuLayout.kt`, `DockScroller` in `StreamMenuBar.kt`, `dockScrollFor` | Compact stream menu dock |
| `CompanionActivity`, `CompanionContent`, `CompanionTheme`, `CompanionFit`, `FitColumn` | Companion screen and the fitting of its cards |
| `CompanionSession.showsHelp`, `PublishCompanionHelp`, `ShellState.companionState()` | The settings description on the companion and the state the window gets |
| `OscGeometry`, `OscDefaultLayout`, `VirtualController` | On-screen controls: default layout and saved layouts (`REF_W`, `REF_H`) |
| `KeyBoardGeometry`, `KeyBoardController` | Key grid |
| `StreamWindowController.requestedOrientation`, `isSquarishScreen` | Stream orientation |
| `VideoFit` (`VideoFit.measure`), `StreamContainer`, `StreamView` | Video rectangle |
| `StreamExit`, `StreamMenu` | Hold the two backdrop blur radii |
| `ShapeSimulator`, `ShapeSimulatorMenu`, `ShapeCatalog`, `DebugToolRow` (`src/debug`; stubs in `src/release`) | Shape simulator and its debug menu |
| `DualScreen` | Picks the main panel ("Automatic") |
| `ScreenPreviews` (`src/debug`) | Screen previews with fake data |
| `tools/layout/shapes.txt`, `tools/layout/screens.txt`, `tools/layout/sheets.sh` | Contact sheets |
| `ShellActivity`, `ShortcutTrampoline` | `sensorLandscape` |

## Verification

| Test | What it enforces |
|---|---|
| `StagePlannerTest` | The stage planner |
| `FamilyLayoutTest` | The computable geometry of the compact settings family on every stage size of `tools/layout/shapes.txt` |
| `ShapeCatalogTest` | Fails when `ShapeCatalog`'s sheet shapes and `tools/layout/shapes.txt` differ |
| `StatusBarFitTest` | The compact status bar |
| `CircleRowFitTest` | The circle rows |
| `HomeCompactLayoutTest` | Compact Home |
| `DockGeometryTest` | The compact stream menu dock |
| `OscGeometryTest`, `KeyBoardGeometryTest`, `VideoFitTest`, `StreamWindowControllerTest` | The stream geometry over 1:1, 5:4, 31:27, 4:3, 3:2, 16:10, 16:9 (four resolutions), 20:9, 21:9, 32:9 and portrait; every 16:9 output is pinned against the old arithmetic |

The debug tools that draw these layouts are the [shape simulator](#10-shape-simulator), the
[screen previews](#11-screen-previews) and the [contact sheets](#12-contact-sheets).

### Checks (date not recorded)

The source names no date for these checks, and no device.

1. The keyboard (`FullKeyboard`) was only checked for the compact settings family: it is width-aware.
2. The Full keyboard was checked at 1:1, 4:3, 31:27 and 21:9.
3. The loader, result card, exit portal and statistics panel were checked at 1:1 and 4:3 (contact sheets).

### Not verified

The emulator cannot run a stream: the native library dies with SIGILL. Not verified:

1. The stream itself on any panel.
2. The on-screen controls touched for real (previews draw the real views without touch).
3. The orientation lock on a device.
4. The cutout / grip-covered edges of a phone in a grip (the window is the display metrics; no safe-area insets are
   applied to the controls).
5. The physical size of controls on the real panels (needs the Odin, an RG Cube / 406H, a Thor).

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [GUI.md](../GUI.md): index of the GUI documents.
- [DUAL_SCREEN.md](../DUAL_SCREEN.md): the companion screen, "Companion content", "Settings help".
- [HANDHELD_DEVICES.md](../HANDHELD_DEVICES.md): the displays of the target handhelds and the class each gets.
- [STREAM_MENU.md](STREAM_MENU.md), [FULL_KEYBOARD.md](FULL_KEYBOARD.md),
  [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md), [ARTWORK.md](ARTWORK.md), [START_AND_HOME.md](START_AND_HOME.md).
- Issue #9: screen shapes.
- Issue #76: the companion screen.
