# Start, home and launch

## Purpose

This document specifies what the user sees from the Android launcher icon to the first frame of a stream: the launcher
identity, the first launch, the profile avatars, the start screen, the home screen, the launch loader and the streaming
time that the home screen shows for each app.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Android launcher identity | Design principles and the index of the GUI documents: [GUI.md](../GUI.md) |
| First launch and profile avatars | SteamGridDB covers, logos and hero backgrounds: [ARTWORK.md](ARTWORK.md) |
| Start screen | Game information and library filters: [GAME_INFO.md](GAME_INFO.md) |
| Home | Menus and settings: [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) |
| Launching a game | Launch transition, native paths, bounds and device limits: [LAUNCH_TRANSITION.md](../LAUNCH_TRANSITION.md) |
| Streaming time and the stat block on the home screen | Motion and color roles: [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| | Stage shapes: [SCREEN_SHAPES.md](SCREEN_SHAPES.md) |
| | Marquee and text that does not fit: [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md) |

## 1. Android launcher identity

1. The Android launcher uses the same Delos ring and orbiting moon as `LogoMark`, with the default dark Delos background
   and gold/cyan accents.
2. The ring is optically centered at (54, 54) on the 108dp canvas.
3. The complete mark, including the moon halo, fits inside the centered 66dp safe circle.
4. All layers use the same uniformly reduced geometry.
5. The Android 13 monochrome layer preserves the ring and moon.
6. The TV channel uses the same mark.
7. PC shortcut icons remain unchanged.

`python tools/icon/generate_launcher.py` rebuilds the icon:

| Output | Detail |
|---|---|
| Vectors | Rebuilt in place. |
| Legacy PNGs | 48/72/96/144/192px. |
| Previews | Circle, squircle and rounded-square color/monochrome previews, written to the ignored `build/icon-preview`. |

## 2. First launch

1. The user is asked to **create a profile**: a splash screen with **circular icons** (similar in spirit to the PS5 UI).
2. In the same style, the next step automatically shows the **PCs to pair, in circles**.
3. Logging in to a PC plays the main **"portal" animation** (not the same as the PlayStation Portal one).
4. The same kind of animation is used when connecting to a remote device elsewhere in the UI.

## 3. Profile avatars

### 3.1 The avatar set

A profile's avatar is one of **16 illustrated characters** (game-styled mascots with soft shading, no text), not an
icon.

| Index | Name | File | `Avatar.hue` | `HUE_HINTS` hue (degrees) | Procedural fallback |
|---|---|---|---|---|---|
| 0 | Sailor | `sailor.webp` | 205 | 187 | yes |
| 1 | Voyager | `voyager.webp` | 262 | 250 | yes |
| 2 | Player | `player.webp` | 330 | 213 | yes |
| 3 | Fox | `fox.webp` | 24 | 15 | yes |
| 4 | Spark | `spark.webp` | 48 | 43 | yes |
| 5 | Calm | `calm.webp` | 150 | 95 | yes |
| 6 | Knight | `knight.webp` | 232 | 212 | yes |
| 7 | Robot | `robot.webp` | 182 | 183 | yes |
| 8 | Cat | `cat.webp` | 84 | 25 | yes |
| 9 | Wizard | `wizard.webp` | 292 | 250 | yes |
| 10 | Racer | `racer.webp` | 356 | 358 | yes |
| 11 | Ghost | `ghost.webp` | 118 | 210 | yes |
| 12 | Owl | `owl.webp` | 150 | 150 | no |
| 13 | Hacker | `hacker.webp` | 320 | 320 | no |
| 14 | Wolf | `wolf.webp` | 200 | 205 | no |
| 15 | Dragon | `dragon.webp` | 110 | 110 | no |

1. Sailor, Voyager, Player, Fox, Spark and Calm are the original six, in this order. Knight, Robot, Cat, Wizard, Racer
   and Ghost follow. Owl, Hacker, Wolf and Dragon are the four newest, indices 12..15.
2. The profile stores only the avatar's **index**. Existing profiles keep theirs, and the order only ever grows at the
   end.
3. Each avatar's file is its lower-case name (`owl.webp`).
4. The font icon of Owl, Wolf and Dragon is the paw. The font icon of Hacker is the terminal.

### 3.2 Drawing

| Element | Value |
|---|---|
| Illustration | Transparent image, 384 px WebP |
| Character placement | In the central ~70%, because every badge is a circle |
| Background | Radial gradient `radial-gradient(circle at 35% 30%, c0, c1)` |
| Background of an imported avatar | The entry with `c0`/`c1` in `app/src/main/assets/avatars/avatars.json` |
| Background of an avatar without an entry (not imported yet) | Fixed per-avatar hue gradient, `hsl(h 80% 62%)` to `hsl(h+30 70% 38%)`, `h` = `Avatar.hue`, with its font icon |

1. The illustration is drawn over the radial-gradient background.
2. The font icon of the avatar is only the stand-in while the image loads.
3. The font icon stays when the image is missing from the bundle. A missing file never crashes: it is looked for once,
   logged once, and the badge keeps the icon.
4. Every imported avatar has an entry in `avatars.json`, computed by the background rule in
   [3.3](#33-the-background-rule).

### 3.3 The background rule

The background is always a dark, muted radial gradient in the tone of the avatar.

| Color | Formula | Place in the gradient |
|---|---|---|
| `c0` | `c0 = hsl(h, 32%, 22%)` | The lit center |
| `c1` | `c1 = hsl(h, 26%, 10%)` | The edge |

1. The values are the numbers of the approved set sheet.
2. The importer computes both colors from the avatar's hue `h` and writes them to `avatars.json`, keyed by the slug:
   `{"fox": {"c0": "#4a2f26", "c1": "#201613"}}`.
3. `h` comes from the importer's **`HUE_HINTS` table** (degrees; it wins): sailor 187, calm 95, spark 43, cat 25,
   racer 358, fox 15, robot 183, voyager 250, player 213, knight 212, wizard 250, ghost 210, owl 150, hacker 320,
   wolf 205, dragon 110. The table in [3.1](#31-the-avatar-set) lists the same values by index.
4. A slug that is not in the table gets the character's **dominant hue**. It is found the same way the cover colors of
   the game tiles are (`HeroBitmap.dominantHues`): 16-level RGB buckets, CAM16 chroma squared weights, gray and
   near-black/near-white clusters ignored, 15-degree hue groups.
5. A mostly gray character that is not in the table gets the avatar's own hue.
6. The table exists because the detector took the sailor's skin for his color and the calm stones' leaf for theirs.

### 3.4 The picker

The picker on the create/change-avatar screen shows all sixteen at once, with the avatar's name under each.

| Element | Value |
|---|---|
| Layout | **Two rows of eight** circles |
| Circle | 144 px |
| Cell | 184 px |
| Distance between cells | 32 px |
| Width of a row | 1696 px of the 1920 px stage |
| Margin a row keeps on each side | 52 px: 48 px outer margin plus the 4 px a focused edge circle's ring reaches beyond its cell |
| Layout on a compact stage | **4 columns** in 4 rows |

Navigation:

1. D-pad moves spatially. A selects.
2. Left at the start of a row and right at its end continue in the previous/next row and wrap.
3. Up/down move to the avatar in the same column of the other row.

Each row is a `ScrollRow`. Its behavior depends on the stage width:

| Stage | Behavior |
|---|---|
| From 1800 px | The rows do not scroll and look exactly as at 1920. |
| Not compact but narrower than 1800 px (1500 to 1800) | The circles never shrink or wrap. The rows scroll sideways. |
| Compact | 4 columns in 4 rows. |

On a stage that scrolls the rows:

1. An edge fade shows where more avatars continue.
2. The focused avatar is scrolled into view with the next one peeking in (`FOCUS_MARGIN`).
3. The ring and glow are never clipped (`bleed`).
4. Both rows share one scroll state, so a column stays a column.
5. A move that continues in the previous/next row scrolls both rows to the target avatar.

### 3.5 Importing the images

The images come from an **image generator** (gpt-image-2).

| Property | Value |
|---|---|
| Format | 1024 x 1024 RGBA PNG |
| File name | `<index>_<slug>.png` (`00_sailor.png` ... `15_dragon.png`) |
| Content | Character on a transparent background, filling about 70-75% of the canvas |

The importer takes whatever is present: any subset, and it is run again when more images arrive.

| Command | What it does |
|---|---|
| `python tools/avatars/import.py [INPUT_DIR]` | Imports the PNGs of the directory that holds them (steps below). Needs Pillow 11.3.0 with WebP. |
| `python tools/avatars/import.py --preview-dir DIR` | Writes contact sheets: 280/96/72/56 px on the dark badge and on the fixed-hue gradient, plus a family sheet of all sixteen in two rows of eight with the colors the app draws. The default directory is `build/avatars-preview/`, ignored by git. |
| `--preview-all` | Also shows the dark badge of the slugs that are not imported. |
| `python3 -m unittest tools/avatars/test_import.py` | Checks the color rule, the hint table and that the importer's slugs and hues match `Profiles.kt`. Run from the repository root, by hand; needs Pillow only. |

`python tools/avatars/import.py [INPUT_DIR]` does the following:

1. It validates every PNG: RGBA, square, alpha, character centered within 6% and 60-85% of the canvas. Validation only
   warns.
2. It resizes the image to 384 px with Lanczos.
3. It **replaces** `app/src/main/assets/avatars/<slug>.webp`.
4. It writes the dark colors of **every imported slug** to `avatars.json`. The entries of slugs not in the run stay.
   A run with all sixteen PNGs refreshes the whole file.
5. It prints a table of what was imported, kept or has no image yet.

### 3.6 Procedural fallback

1. Slugs without a generated image keep a **procedural fallback illustration** from `tools/avatars/generate.py`.
2. The generator is run as `python tools/avatars/generate.py`, with the options `--preview` and
   `--fast --only fox cat`.
3. The importer overwrites the webp of every slug it imports. The generator is therefore run only for slugs that still
   have no image.
4. The generator draws only the first twelve. **Owl, Hacker, Wolf and Dragon have no procedural drawing and need an
   imported image**. Until then the app shows their hue gradient and icon.

## 4. Start screen

The start screen is shown on every launch.

1. Every time Delos starts, it opens on a **start screen with the remote devices**, in the same circle style as the
   first launch.
2. The **current / last used profile** is shown in the top-left corner, with a quick way to **switch profiles or create
   a new one**.
3. The settings hold a **"Default device to connect to automatically"** picker and a switch to **skip the start
   screen**. With the switch on, Delos connects straight to the default device.

## 5. Home

1. After connecting to a PC, the main layout shows the **apps / games with the correct aspect ratio for SteamGridDB
   covers** (portrait). The artwork is specified in [ARTWORK.md](ARTWORK.md).
2. Games are laid out as **one horizontally scrolling row of portrait covers** with the logo, hero background and info
   above it.
3. The **first game is wide** (landscape SteamGridDB cover).
4. The focused game stays near the left edge and the row fades out at the edges.
5. A **top center bar** holds icons for **settings, profile and devices** (e.g. renaming a PC on the client side).
6. On the **right**, separately: **battery, Wi-Fi** and other status. Delos draws its own status bar; the system bars
   are hidden.
7. The info row above the covers holds the stat block of [7.5](#75-the-stat-block-on-the-home-screen).

## 6. Launching a game

The specification of the effect is [LAUNCH_TRANSITION.md](../LAUNCH_TRANSITION.md). See
[#62](https://github.com/Meitoncz/Delos/issues/62).

### 6.1 The tunnel

1. Android 13+ launch uses the approved **hyperspace tunnel**: fixed-center symmetric lens curvature, traveling
   ribs/bands, **24 streaks + 24 independently distributed dark clouds**, moving peripheral bloom and a high-contrast
   palette-aware gradient.
2. `HyperspaceConfig.RENDER_SCALE` is **0.6** (1152×648 at 1920×1080).
3. The shader and brush belong only to the active overlay and animate in drawing, not composition.
4. Android versions below 13 retain the previous ring/cover renderer as the visual fallback, with the same information
   block.
5. Reduced motion uses a static dark gradient, the same information block and a **500 ms** fade. The animation clock is
   not started.
6. The effect was designed as a separate HTML prototype that is not part of this repository. The launch-loader drawing
   in `docs/prototype/delos-gui.html` is older and does not show it.
7. The native constants and shader are in `shell/ui/HyperspaceConfig.kt` and `HyperspaceTunnel.kt`.

### 6.2 The information block

1. The shell, shortcut splash and stream loader share the scene clock, last drawn progress and inherited connection
   line.
2. The game's existing SteamGridDB **logo** replaces the cover, with Unbounded title fallback.
3. Below it are read-only **effective resolution · codec · FPS**, a real connection-stage line, a thin progress track
   and percentage.
4. Before codec negotiation, automatic selection is labeled **Auto**. The loader then uses the active codec and decoded
   dimensions.

Launch-parameter inspection is strictly read-only:

1. Games without overrides read the global profile.
2. Games with overrides read their effective game view.
3. Both suppress legacy preference-normalization writes.
4. The per-app editor still rejects keys that are not per-app.
5. Loading a label never migrates or modifies user settings.

### 6.3 Connection lines

1. Each real witty connection line receives its **1200 ms** visible dwell, including its remaining time across window
   handoff.
2. Each line queued behind the current one is told in order while the connection runs.
3. When the game opens, the lines still queued are dropped. The game never waits for them.
4. Network and decoder initialization continue independently.

### 6.4 Exit and stream arrival

1. After connection readiness (without waiting for queued lines), the loader waits for a **presented video frame**, not
   merely `connectionStarted`, with the existing **3 s** bound.
2. The exit expands the tunnel and opens a soft central aperture. The information block fades at its start.
3. The revealed image is the actual underlying video surface, not a screenshot.
4. The **real stream image** arrives at **118% zoom with blur**, elastic spatial warp/skew and temporary RGB separation,
   then settles to exactly **100%, sharp, without residual effects**.
5. Resources warm early, but output takeover starts only partway through the exit (table below).
6. Static/low-FPS sources reuse their latest real GPU frame for animation.
7. Runtime failure is logged and cancels the effect rather than holding the loader indefinitely.
8. Direct MediaCodec returns to its original surface after the temporary GLES pass.
9. Existing SGSR and PyroWave paths apply arrival in their GPU presentation context.
10. Ten-bit/HDR keeps FP16/P010/PQ metadata without SDR conversion.
11. Normal low-latency presentation and the accepted shell/exit/snapshot handover resume after cleanup.
12. Reduced motion does not activate stream effects.
13. See [native paths, bounds, verification and device limits](../LAUNCH_TRANSITION.md).

### 6.5 Timings and bounds

| Element | Value | Constant |
|---|---|---|
| Tunnel render scale | **0.6** (1152×648 at 1920×1080) | `HyperspaceConfig.RENDER_SCALE` |
| Streaks | 24 | `HyperspaceConfig.STREAK_COUNT` |
| Dark clouds | 24 | `HyperspaceConfig.CLOUD_COUNT` |
| Visible dwell of a connection line | **1200 ms** | `HyperspaceConfig.LINE_MS` |
| Wait for a presented video frame | **3 s** bound | `FRAME_WAIT_MS` in `StreamLoader.kt` |
| Exit duration | **2200 ms** | `HyperspaceConfig.OPENING_MS` |
| Fade of the information block | The first **34%** of the exit | `HyperspaceConfig.INFO_FADE_END` |
| Soft central aperture | **68–88%** of the exit | `HyperspaceConfig.IRIS_START`, `IRIS_END` |
| Reduced-motion fade | **500 ms** | `HyperspaceConfig.REDUCED_OPENING_MS` |
| Zoom of the arriving stream image | **118%**, settling to exactly **100%** | `HyperspaceConfig.STREAM_INITIAL_ZOOM` |
| Start of output takeover | **55%** of the exit | `LaunchArrival.ACTIVATE_AT` |
| Readiness check | At **60%** of the exit, with a **1.5 s** bound | `LaunchArrival.READY_CHECK_AT`, `READY_WAIT_MS` |
| Completion | **1 s** bound | `LaunchArrival.COMPLETE_WAIT_MS` |

### 6.6 Game-window readiness

1. Game-window readiness behavior and its proposed host protocol are recorded in
   [#13](https://github.com/Meitoncz/Delos/issues/13) and [#14](https://github.com/Meitoncz/Delos/issues/14).
2. A visible game window is not a guarantee that gameplay loading has finished.

## 7. Streaming time

Every app keeps a total of the time its stream was actually connected, summed over all sessions (like playtime in
Steam). Remote Desktop, Remote Input and Remote Monitor count like any app.

### 7.1 What counts

1. The counted time runs from the connection being established until the stream ends: Disconnect, Quit game, the host
   closing it, a lost connection, the stream window going away.
2. Time while connecting, while the connection is down or while the stream is suspended is not counted.
3. Nothing is ever discarded: short sessions are added like long ones.

### 7.2 Storage

1. The total is keyed by profile, PC and app id, so a profile switch never mixes times.
2. The keys and their file are listed under [Settings](#settings).
3. The times are stored next to the per-app settings. Deleting a profile deletes its times.
4. The times are local only: never uploaded, never part of the companion or any backup export.
5. There is no reset.

### 7.3 Recovery

`DelosStreamActivity` calls `PlaytimeTracker` (pure logic, injected monotonic clock):

| Call | Where |
|---|---|
| `begin` | `onCreate` |
| `setConnected(true)` | `connectionStarted` |
| `end` | `stopConnection` and `onDestroy` |

1. Elapsed slices are written to `PlaytimeStore` every 30 s and at every state change, as additions to the total (and
   the timestamp).
2. There is no open-session record.
3. A crash or kill therefore loses at most the last 30 s, and the next start has nothing to close and nothing to double
   count.

### 7.4 Reading and formatting

| Function | Result |
|---|---|
| `PlaytimeStore.playtime(profileId, pcUuid, appId)` | `Playtime(totalMillis, lastStreamedMillis)`. Both zero: never streamed. |
| `PlaytimeStore.changes` | Counts every addition, so a reader reads again when it changes. |
| `PlaytimeFormat.parts(resources, millis, locale, short)` | The amount as number and unit. |
| `LastStreamed.format(resources, lastMillis, ...)` | `Never` or the relative day. |

`PlaytimeFormat.parts` formats the amount as follows:

| Amount | Number | Unit | Examples |
|---|---|---|---|
| No time at all | `0` | The hours unit, in the plural form of 0 in the language | `0 hours`, `0 hodin`, `0 Stunden`, short `0 h` |
| Under one hour | Whole minutes, at least 1 | Minutes | `45` and `minutes` |
| From one hour | One decimal in the locale's separator; a whole number of hours without a decimal | Hours | `20.4` and `hours`, `20,4` and `hodiny` in Czech |

`short` gives the units `h` and `min`.

`LastStreamed.format` computes the relative day in the device's clock and time zone with calendar days:

| Case | Text |
|---|---|
| Nothing is recorded (zero or no timestamp) | `Never` |
| The same day | `Today` |
| The day before | `Yesterday` |
| 2 to 6 days | `N days ago` |
| 7 to 34 days: 1 to 4 weeks | `N weeks ago` |
| Later | The date as the locale writes a medium date (`Sep 3, 2026`, `03.09.2026`) |
| A time in the future (clock set back) | Today |

### 7.5 The stat block on the home screen

The block follows the design of issue #85.

#### Placement

1. There is no `APP INFO` label, and no string for it either, on every stage.
2. The block stands at the **start (left) of the info row**. Its left edge is on the logo's and on the badge-row start.
3. The block is followed by 30 px of air (no divider) and the badges.
4. There is no plate behind the block.
5. The block (`PlaytimeBlock.kt`) is centered vertically, with the badges centered on it as a whole. The row's height is
   the block's.
6. The logo or title is vertically centered between the bottom of the status bar and the top of that row, still
   left-aligned.

| Element | Value |
|---|---|
| Left edge on a standard stage | x = 150 |
| Left edge on a compact stage | 72 |
| Air between the block and the badges | 30 px (`HomeCompact.BLOCK_GAP_X`) |
| Height of the badges | 37 |
| Height of the block and of the row | 44 x scale (44 px at interface scale 1; it grows with the scale) |

#### Language

1. Every number, date and upper-casing of the block takes its locale from the resources the strings come from:
   `PlaytimeFormat.localeOf(resources)`, the first locale of their configuration.
2. The locale is not taken from `Locale.getDefault()`: the app switches its language per app, which the resources follow
   and the JVM default does not.
3. Czech therefore reads `20,4 hodiny`, German `03.09.2026`.
4. `PlaytimeStoreTest` and `LastStreamedTest` set the JVM default to US and still get the language of the resources.

#### Look

The font is Figtree, like the rest of the body.

| Element | Text style | Color |
|---|---|---|
| Eyebrow `STREAMING TIME` | Small caps, Figtree Bold 13, letter spacing 0.16 em | Accent color |
| Number of the amount | 30, Bold, tabular figures | Text color |
| Unit of the amount | 20, SemiBold | Text 2 |
| Eyebrow `LAST STREAMED` | As the first eyebrow | Text 3 |
| Relative day | 23, SemiBold | Text 2 |

| Geometry | Value |
|---|---|
| Number and unit | On a common baseline, 8 px apart |
| Divider width | 2 px |
| Divider height | 38 x scale px, exactly centered on the block |
| Divider color | The accent at 70 % in the middle, fading to transparent over the top and bottom 22 % of its height |
| Distance between the columns | 22 px |
| Block height | 44 px at interface scale 1; it grows with the scale |

1. The eyebrow `STREAMING TIME` stands over the amount, then comes the divider, then the eyebrow `LAST STREAMED` over
   the relative day.
2. Eyebrows are upper-cased with the locale (`String.uppercase(locale)`).

#### Readability

1. The block stands on the darker left side of the game background.
2. Its texts carry only a soft dark shadow: black at 60 %, 2 px down, blur 8.
3. There is no plate, tint or gradient behind it.
4. The colors are the B look's: accent eyebrow, text 3 eyebrow, text and text 2 values.

#### Interaction and visibility

1. The block is not interactive: never focusable, no action, nothing for controller navigation.
2. It is not shown in the per-app settings header.
3. It shows always, for the focused app, and it follows the focus.
4. An app never streamed shows `0 hours` and `Never` (string `playtime_never`).
5. A total without a timestamp shows `Never` as its last-streamed value.
6. It reads the same key as the tracker: the current profile id from `ProfileStore.currentId`, the PC's uuid, the app
   id.
7. It is read again whenever time is added, the profile changes and when the shell resumes after a stream (`ON_RESUME`,
   which also redoes the relative day after midnight).

#### The fit rule

The rule is implemented by `InfoBar`, `PlaytimeFit` and `BadgesFit`.

1. The block keeps its fixed place at the start and its **full form**: both columns, the long unit.
2. Then come 30 px of air (`HomeCompact.BLOCK_GAP_X`). There is no divider between the block and the badges.
3. The eyebrows' trailing letter spacing is counted in the column, so the longest text never crowds the badges.
4. The badges are the flexible part. They never wrap and are never under the block.
5. The badges take the width right of that gap up to the right edge of the info area. No gap is needed there.
6. When their natural width is more, they scroll in that room with `MarqueeContent` in its `bounce` mode.
   `MarqueeContent` is the engine of `MarqueeText`, for any single-line content.

The `bounce` mode (`MarqueeMotion.bounceOffset`, tested by `MarqueeMotionTest`):

| Step | Value |
|---|---|
| Wait before the first scroll | 0.7 s |
| Scroll to the end | At the marquee's speed (56 px/s) |
| Pause at the end | 0.9 s |
| Scroll back | Not stated separately |
| Pause at the start, then repeat | 0.9 s |

1. There is no repeated copy of the content.
2. The left fade shows only once content is hidden at the left, the right one until the end is reached. The fade is
   therefore only where something is cut.
3. Reduced motion keeps the row still with the right fade.

Last resorts:

1. On a very narrow stage the block stands **above** the badges (`HomeCompact.blockAbove`). The block and the badges
   each get the full width, and the badges scroll there too. The block shortens in its own order: without LAST
   STREAMED, short unit, scroll.
2. A safety net: if the actual full block would leave the badges less than 160 px (a text longer than the layout allows
   for), the block shortens in the same order, so the badges never vanish.

`HomeCompact.blockAbove` applies when the info area is under `460 x interface scale + 30 + 160` px wide: the widest
block, its air and 160 px of badges.

| Interface scale | The block stands above on a stage under |
|---|---|
| 1 | 794 px wide |
| 1.15 (the default) | 863 px wide |
| 1.3 | 932 px wide |

No handheld stage is that narrow. The 480-wide stress stage is.

#### 16:9 and the wide and tall shapes

1. The position of the covers' row does not depend on the block.
2. The info row is 44 x scale high.
3. The info row ends 44 px above the focused cover's ring (`HomeStandard.INFO_RING_GAP`). At least 24 is required.
4. The title box of 200 (the logo 180 high with 10 px of air) is centered between the status bar and the row.
5. The logo keeps its full 180 px at every scale. The build before this layout drew it 157 at 1.15 and 115 at 1.3.

| Interface scale | Title box starts at | Free above and under the title box | Logo to bar and to row | Ring clearance |
|---|---|---|---|---|
| 1 | 156.5 | 52.5 px | 62.5 | Not stated |
| 1.15 | 154.1 | 34.5 px | 44.5 | 44 |
| 1.3 | 151.7 | 16.5 px | 26.5 | Not stated |

#### Compact stages

1. The block needs no row of its own. It stands beside the badges, which do not wrap, so the row is one line.
2. The row's height is the block's, 44 x scale. It replaces the two rows of badges plus a block row.
3. The block does not reduce the covers: `twoRowsHeight` is still counted by the comfortable cover size.
4. The room that the single row saves goes to the title box (up to 160, a logo up to 140 high instead of 100) and the
   gaps.
5. The title box is centered: the gap above it and the one under it are equal and at least 16.
6. The least gaps 16 / 24 / 28 still hold on all six stages at both scales.

| Stage | Cover scale at interface scale 1 | Cover scale at interface scale 1.15 |
|---|---|---|
| 1099x957 | 0.655 | 0.655 |
| 1488x893 | 0.767 | 0.690 |
| 1191x893 | 0.716 | 0.690 |
| 1108x831 | 0.636 | 0.560 |
| 1051x1051 | 0.624 | 0.624 |
| 722-high | 0.45 | 0.45 |

## Settings

The streaming time is stored in the profile's own preferences file: `ui_profile_<id>`, or the default preferences before
the first profile exists.

| Key | Type | Value |
|---|---|---|
| `ui_playtime_<uuid of PC uuid>_<appId>` | Long | The total, in milliseconds |
| `ui_playtime_last_<uuid of PC uuid>_<appId>` | Long | The wall-clock time (epoch milliseconds) of the last addition: the end of the last flushed slice, i.e. when the app was last streamed |

The start screen has two settings, described in [4. Start screen](#4-start-screen): the **"Default device to connect to
automatically"** picker and the switch to skip the start screen.

## Code map

| Path or class | Role |
|---|---|
| `LogoMark` | The Delos ring and orbiting moon that the launcher icon repeats |
| `tools/icon/generate_launcher.py` | Rebuilds the launcher vectors, the legacy PNGs and the previews |
| `Profiles.kt` | The avatars' slugs and hues (`Avatar.hue`) that the importer must match |
| `app/src/main/assets/avatars/avatars.json` | `c0`/`c1` background colors of the imported avatars, keyed by the slug |
| `app/src/main/assets/avatars/<slug>.webp` | The avatar illustrations |
| `tools/avatars/import.py` | Importer of the generated avatar images; holds the `HUE_HINTS` table |
| `tools/avatars/generate.py` | Procedural fallback illustrations of the first twelve avatars |
| `tools/avatars/test_import.py` | Unit test of the importer |
| `HeroBitmap.dominantHues` | Dominant-hue detection shared with the cover colors of the game tiles |
| `ScrollRow` | A row of the avatar picker |
| `shell/ui/HyperspaceConfig.kt` | Native constants of the hyperspace tunnel |
| `HyperspaceTunnel.kt` | The tunnel and its shader |
| `docs/prototype/delos-gui.html` | Holds an older launch-loader drawing that does not show the tunnel |
| `DelosStreamActivity` | Calls `PlaytimeTracker` at the stream's state changes |
| `PlaytimeTracker` | Pure logic of the streaming time, with an injected monotonic clock |
| `PlaytimeStore` | Stores and reads the totals and timestamps; `changes` counts every addition |
| `PlaytimeFormat` | Formats the amount as number and unit; `localeOf(resources)` gives the locale |
| `LastStreamed` | Formats the last-streamed value |
| `ProfileStore.currentId` | The current profile id |
| `InfoBar.kt` | The info row |
| `PlaytimeBlock.kt` | The block and `StatDivider` |
| `HomeStandardInfo.kt` and `HomeCompactLayout.kt` | The numbers of the layout |
| `PlaytimeFit`, `BadgesFit` | The fit rule, with `InfoBar` |
| `MarqueeContent`, `MarqueeMotion.bounceOffset` | Scrolling of the badges in the `bounce` mode |

## Verification

| Test | What it enforces |
|---|---|
| `tools/avatars/test_import.py` | The color rule, the hint table and that the importer's slugs and hues match `Profiles.kt` |
| `PlaytimeStoreTest`, `LastStreamedTest` | With the JVM default set to US, the texts still get the language of the resources |
| `MarqueeMotionTest` | `MarqueeMotion.bounceOffset` |

The avatar test is run by hand from the repository root:

```
python3 -m unittest tools/avatars/test_import.py
```

### Trying the stat block without streaming

1. The keys under [Settings](#settings) can be written with `adb shell run-as <package> ...` into the preferences XML.
2. The file is `shared_prefs/ui_profile_<id>.xml`, or the default preferences file when no profile exists.
3. A total is written as `<long name="ui_playtime_<uuid>_<appId>" value="73440000" />` (20.4 h).
4. The last-streamed value is written in epoch milliseconds.

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [GUI.md](../GUI.md): index of the GUI documents.
- [ARTWORK.md](ARTWORK.md): SteamGridDB covers, logos and hero backgrounds.
- [LAUNCH_TRANSITION.md](../LAUNCH_TRANSITION.md): the launch transition.
- [SCREEN_SHAPES.md](SCREEN_SHAPES.md): standard and compact stages.
- [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md): text that does not fit.
- [#13](https://github.com/Meitoncz/Delos/issues/13) and [#14](https://github.com/Meitoncz/Delos/issues/14):
  game-window readiness and its proposed host protocol.
- [#62](https://github.com/Meitoncz/Delos/issues/62): the hyperspace launch effect.
- Issue #85: the stat block on the home screen.
