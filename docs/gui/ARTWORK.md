# Artwork

## Purpose

Delos shows a portrait cover, a wide cover, a background (hero) and a logo for every game. This document specifies
where the images come from (SteamGridDB, the PC, the bundled artwork of the built-in entries), how the home screen
draws and animates the hero, how the Artwork page lets the user choose images, how animated artwork plays, and how the
bundled assets are rebuilt.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| SteamGridDB integration, image kinds, selection precedence | Index of the GUI documents and the design principles: [GUI.md](../GUI.md) |
| Hero on the home screen: fade, blur, tilt, scaling, frame cost | Home screen layout, tiles, launching a game: [START_AND_HOME.md](START_AND_HOME.md) |
| Artwork page: toolbar, grid, search, filter sheet | Context menu, app options, settings pages: [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) |
| Animated artwork: hero, tiles, logo, picker tiles | Shared text dialog: [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md) |
| Bundled artwork of the built-in entries and its identity policy | Motion values (`popEnter`, `popExit`, fades): [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| Rebuilding the bundled assets | Screen shapes: [SCREEN_SHAPES.md](SCREEN_SHAPES.md) |
| | Game information and library filters: [GAME_INFO.md](GAME_INFO.md) |
| | Launch loader and portal: [LAUNCH_TRANSITION.md](../LAUNCH_TRANSITION.md) |
| | Stream exits: [SHELL_ARCHITECTURE.md](SHELL_ARCHITECTURE.md) |
| | Preference keys: [SETTINGS.md](../SETTINGS.md) |

## 1. SteamGridDB

1. The SteamGridDB integration is built in. The user enters their own API key. Images are downloaded on the client
   side.
2. For every game Delos downloads four images:

   | Image | Use |
   |---|---|
   | Portrait cover | Portrait tiles |
   | Wide cover | The wide first tile |
   | Large background (hero) | The game background of the home screen |
   | Logo | The top area of the home screen, the game info |

3. The download must also run automatically for all games at once.
4. A committed artwork selection invalidates existing image readers even if leaving the picker cancels its coroutine
   during the download.
5. Selected hero artwork takes precedence over the SteamGridDB cover and the PC fallback. Clearing it restores that
   fallback order.
6. NSFW and humor images are never requested and never shown.
7. Host artwork synchronization decisions are recorded in [#15](https://github.com/Meitoncz/Delos/issues/15).

## 2. Hero on the home screen

### 2.1 Presentation

1. When a game is focused on the home screen, its logo is shown in the top area. The covers sit a bit lower.
2. The background changes to the game's hero image with a fade in/out and a blur effect.
3. The game background covers the whole screen and fades out diagonally from the top-right to the bottom-left corner.
4. Blur amount and opacity are configurable ([Settings](#settings)).
5. The hero image blends with the dynamic background: the animated effects (aurora, waves, silk, contour lines…) light
   it up instead of covering it.
6. The foreground animated/procedural background keeps its shader, timing and blending.
7. Logos, covers, text and controls do not tilt or blur.

### 2.2 Blur and tilt

The hero image alone is blurred and tilted.

| Element | Value |
|---|---|
| Blur | Gaussian blur: three box blurs, rendered once into a cached bitmap |
| Tilt | Calm, seamless, symmetric 3D tilt around the image's center |
| Tilt loop | 25 s |
| Tilt amplitude | 15° |
| Camera distance | 2.2× the longer side |
| Far edge shift | About 92 px at the extreme on the 2100 px hero area |
| Overscan | 2 %; it keeps image edges outside the viewport |
| Minimum overscan (computed) | 1.0065 is the minimum that keeps all four viewport edges covered |
| Reduced motion | Holds the hero still |

### 2.3 Hero scaling

`ui/HeroGeometry.kt` holds the hero geometry. It is pure and unit tested. One geometry serves every path that draws the
hero, so the still → animated fade does not jump:

- the cached bitmap,
- the legacy launch / stream-exit snapshot,
- the sharp blur-0 hero,
- the playing animation.

| Case | Rule |
|---|---|
| Wide hero (SteamGridDB 1920×620 / 3840×1240 / 1600×650, wide covers) | Scaled to fill the width of the 2100×1260 hero area and aligned to the top (1920×620 → 2100×678 px). It is not cropped to 5:3: that crop loses about half of the image. |
| Image whose width-fit height reaches the area's height (aspect 5:3 or less: box art or a cover used as the background) | Fills the area and is cropped. |
| Placeholder art | Not affected by hero scaling. |

Below a wide hero's band the area is filled by an extension, so no bar stays empty:

| Element | Value |
|---|---|
| Bottom edge of the band | Fades softly over 160 px (a smoothstep, `HeroGeometry.FADE_STOPS`) into the extension |
| Extension | A copy of the same image stretched to cover the whole area |
| Extension blur | 40 px |
| Extension darkening | Blended 55 % toward the dark background color (14, 18, 36) after the blur |

Applied on top of this composition, with their own rules unchanged: the user's blur (Appearance → App background),
saturate(1.15), the diagonal fade and the tilt with its overscan.

| Bitmap | Size | Rule |
|---|---|---|
| Cached hero bitmap | 420×252 (the area at 1/5) | Rendered only on a cache fill. Its cache key carries a composition version, so bitmaps of an older layout are never reused. |
| Extension bitmap | 210×126, a bitmap of its own | At most 24 are kept. |

The game's colors are taken from the plain crop, not the darkened composition. The live paths draw the extension from
that small bitmap and the band at full resolution with the same fade as a mask.

### 2.4 Frame cost of the home screen

The render thread, not the main thread, is the bottleneck. Every full-screen offscreen layer whose content changes is
rendered again on every display frame, and the hero's tilt changes it every frame. With the 120 Hz display the rules
are:

| # | Rule | Detail |
|---|---|---|
| 1 | The game background's opacity is not a group layer. | It multiplies each draw of the hero (`CompositingStrategy.ModulateAlpha`). The fade into the theme color (`alpha .9` gradients) is drawn over the hero at its own opacity. The theme color is also what lies under the hero, so the picture is the same. |
| 2 | The cross-fade to the next game's background is Compose's `Crossfade` (900 ms). | It reuses a hero that comes back (focus moving back and forth between two games). A hand-made cross-fade keeps the superseded copy of the same game playing too, one decoder and set of layers per step, and makes fast listing janky. |
| 2 | The animated hero starts only after the game has stayed focused for 500 ms (`AnimatedPlayback.HERO_DWELL_MS`). | The still hero shows meanwhile. The animated hero ends at once when the focus leaves. |
| 3 | The animated background repaints at most 30 times a second while a game background is shown. | 60 otherwise. |
| 4 | A tile's focus glow (the blurred `dropShadow`) exists only while the tile is focused or recently was. | It stays attached 2.5 s after the focus leaves, so focus moving back and forth between two tiles never attaches and detaches it. |

The measured frame cost of this design is under [Device checks (date not recorded)](#device-checks-date-not-recorded).

## 3. Artwork page

The Artwork page opens from app options → Artwork and is drawn by `ArtworkScreen`. Its layout is the approved prototype
variant B. It has no side column.

### 3.1 Layout

From top to bottom:

| Part | Content |
|---|---|
| Header and tab pills | `SelectionPillHost`; touch only; L1 / R1. The row's R1 badge ends exactly at the content's right edge, the Filter pill's. |
| Toolbar | The SteamGridDB game chip (name, year, id, image count of the tab), "Search for another name" (X) and "Filter" (Y, with a count badge of the active filters). |
| Result line | "10 of 41 covers"; the "of N" shows only when the unfiltered list is loaded. |
| Active filters | Removable chips plus Reset. They share one `RowGlideHost` plate, 520 ms. |
| Grid | Full width; see [Grid](#32-grid). |
| Strip about the focused image | Style, Static / Animated, In use, size, file format, score and author, date. Only what SteamGridDB sent is shown. |

### 3.2 Grid

| Property | Value |
|---|---|
| First tile | "From your PC" / "Name only" |
| Tile focus | `focusEffects` |
| Tile in use | "In use" badge |
| Animated image | A play badge "Animated"; icon only on covers |
| Initial focus | The tile in use; the first tile when none is |
| Scroll | `FocusScroller` with the margin bring-into-view |
| Entrance | The staggered rise of the first eight tiles |

The focused tile has room (`ArtGridLayout` in `screens/FamilyLayout.kt`, every
[screen shape](SCREEN_SHAPES.md)):

1. The room between two tiles covers the focused tile's growth, its ring and 12 px:
   `gap = tile × 0.06 + 8 + 12`, 28 px at least.
2. Rows get the same from the tile's height.
3. The tile is what the columns then leave.
4. A uniform room of 24 px everywhere is not used: it leaves a focused cover's ring 9 px from its neighbors.
5. The grid's box is the stage's width and the tiles are padded in by the margin. The ring and the glow (box-shadow
   0 18 50 -12) of a tile in the first or last column, the first or the last row are therefore inside the viewport.
6. The grid's edges fade only while rows continue behind them.

| Tab | Tiles per row | Tile at 1920 × 1080 | Room at 1920 × 1080 |
|---|---|---|---|
| Covers | 7 | 204 × 306 | 32 / 38 px |
| Wide covers | 4 | 373 | 42 |
| Backgrounds | 3 | 506 | 50 |
| Logos | 5 | 294 | 38 |

### 3.3 Search

"Search for another name" opens the shared text dialog ([CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md)), then the context
menu of the games found. Choosing one re-matches the artwork.

### 3.4 Filter sheet

| Input | Action |
|---|---|
| Y or the Filter pill | Drops the sheet down under the toolbar (`popEnter` / `popExit` from the pill, [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md)). The page is dimmed; the grid stays visible and updates live. |
| A | Sets an option |
| X | Resets |
| B or Y | Closes; focus returns to where it was |

The focus is held inside the sheet while it is open.

| Filter | Covers | Wide covers | Backgrounds | Logos |
|---|---|---|---|---|
| Type | All / Static / Animated | All / Static / Animated | All / Static / Animated | Not offered |
| Style | alternate, blurred, white logo, material, no logo | alternate, blurred, white logo, material, no logo | alternate, blurred, material | official, white, black, custom |
| Dimensions | 600×900 / 660×930 / 342×482 | 920×430 / 460×215 | 1920×620 / 3840×1240 / 1600×650 | None |
| Sort | highest score, newest, oldest | highest score, newest, oldest | highest score, newest, oldest | highest score, newest, oldest |

1. Type, style and dimensions are SteamGridDB query parameters (`types`, `styles`, `dimensions`, `mimes`).
2. "All" is two requests, still plus animated, because the answers do not mark animation.
3. Sorting is client side.
4. There are no per-option counts: they would need extra requests.
5. Filters are kept per tab in memory and reset when the screen is left.
6. The filter logic is in `data/ArtworkFilters.kt` (`ArtFilterRules`, `ArtworkInfo`). Parsing is `SgdbImage.parse`.

## 4. Animated artwork

### 4.1 Format and storage

1. Animated SteamGridDB images are animated WebP only. WebM and GIF are not requested; a WebM entry is skipped.
2. Choosing an image stores the downloaded bytes as they are for every kind (cover, wide cover, hero, logo). The
   animation stays in the artwork store's file; nothing is re-encoded or downscaled.
3. The still paths decode the file's first frame (BitmapFactory). Everything that does not animate keeps working, and
   the still is always the animation's first frame.
4. `ArtworkStore.animatedFile` answers with that file when it is an animated WebP (header check, kept per file size and
   date).
5. There is no setting for animated artwork.

| Place | Animated | Section |
|---|---|---|
| Hero of the home screen | Yes, on Android 12+ | [Hero](#42-hero) |
| Home screen tiles, logo and game page | Yes | [Home screen tiles, logo and game page](#43-home-screen-tiles-logo-and-game-page) |
| Picker tiles | Yes | [Picker tiles](#44-picker-tiles) |
| Launch loader's card | No, first frame only | |
| Stream exit | No, first frame only | |
| Animated GIF/WebM files | No | |

### 4.2 Hero

The home screen's game background plays the chosen animated hero automatically (`GameArt.animated`, `HeroLive` in
`ui/Backdrop.kt`) through `ImageDecoder` / `AnimatedImageDrawable`.

| Decoding property | Value |
|---|---|
| Frames | Decoded on demand |
| Frame width | No wider than they are drawn and at most 1050 px |
| Frames in memory | One |
| Canvas limit | ≤ 100 million pixels |
| Android version | Android 12+; below it the first frame stays |

Performance pipeline:

1. The blur makes the full 2100 × 1260 resolution pointless. A `RenderEffect` on a rotating full-size layer is re-run
   by the render thread on every display frame; its measured cost is under
   [Device checks (date not recorded)](#device-checks-date-not-recorded).
2. The band, its soft bottom edge, the extension, the platform blur (`RenderEffect`, σ scaled with the layer, saturate
   1.15) and the diagonal fade are drawn into a small offscreen layer.
3. The size of that layer is `HeroGeometry.liveScale`:

   | User's blur | Layer size |
   |---|---|
   | From 8 stage px up | A quarter of the area |
   | From 4 stage px | Half of the area |
   | A sharper hero | The full area |

   σ is never below 2 layer pixels.
4. HWUI keeps the layer as a texture and renders it again only when a new animation frame arrives (the animation's own
   rate).
5. Every display frame (120 Hz) the tilt just draws that texture, scaled up and rotated, with no effect, no mask layer
   and no recomposition (the orbit is read in the layer block).
6. The diagonal fade is part of the layer, so it tilts with the image like the cached still hero's.
7. The live hero fades in over the blurred still hero (a cached bitmap). The still then leaves composition (with its
   second tilt clock) until the animation stops.

Loop. An `AnimatedImageDrawable` cannot seek, so its own loop would jump from the last frame to the first.

1. The hero plays each pass as a drawable of its own that plays once (`LoopedAnimation` in `ui/HeroLoop.kt`).
2. The loop's length is read from the file (`AnimatedWebp.loopMillis`, the sum of the `ANMF` frame durations).
3. During the last `HeroLoopTimeline.fadeMillis` of a pass the next pass is started at its first frame and fades in
   over it, inside the same small layer (a `saveLayerAlpha` at the eased opacity). At the loop point the new pass is
   fully visible and the old one is released.

| Timing | Value |
|---|---|
| `HeroLoopTimeline.fadeMillis` | 700 ms, never over a third of the loop |
| Decode lead of the next pass | 500 ms before it starts, so outside the fades one drawable plays |
| Loop under 1.5 s | Keeps the platform's plain loop |
| `AnimatedPlayback.HERO_HOLD_MS` | 1 s, longer than the cross-fade, which removes the hero itself |

Leaving. When the animation stops while the hero is still on screen (the focus moved on and the hero cross-fades out, a
launch begins), it is paused on the frame it showed and released after `AnimatedPlayback.HERO_HOLD_MS`. The still's
first frame therefore never shows on the way out. A game that is focused again lets go of it at once and starts fresh.

Playback conditions. The pure rules are `AnimatedPlayback.shouldPlay`.

| Condition | Hero animation |
|---|---|
| The hero is visible and the window is resumed (`repeatOnLifecycle`) | Plays |
| Launch loader / portal | Does not play |
| Background | Does not play |
| In a stream (a separate resumed activity) | Does not play |
| Reduced motion or the system's animator scale 0 | Does not play; the first frame shows, like the tilt |

### 4.3 Home screen tiles, logo and game page

1. The chosen animated cover (portrait tiles), wide cover (the wide first tile) and logo loop wherever they show on the
   dashboard: the row tiles (`GameTile`) and the logo in the game info.
2. The animation is an `AnimatedArtLayer` over the still image. The still keeps showing until the decoder is ready; the
   animation then fades in over it with the app's fade.
3. The animation is drawn with the same crop as the still (`ContentScale.Crop` cover, fitted and start-aligned logo)
   and the same color filter (the idle filter of an unfocused cover).
4. Only visible tiles play (the keys of the row's `visibleItemsInfo`, no count cap). A tile that scrolls out releases
   its decoder and holds the still again.
5. The frames are decoded at the displayed size, never the full canvas, from the file on disk (no heap copy).

   | Image | Decode size |
   |---|---|
   | Tile | Tile width × height at its focused scale |
   | Logo | 900 × 180 stage px |

   The rule is `AnimatedPlayback.decodeSize` with the view's size: cover or fit, never enlarged.
6. The home screen has one `AnimationTicker` for all of it (`DashboardArt` in `screens/DashboardArt.kt`). It runs only
   while something may play.
7. The launch loader's card and the stream exit keep the first frame.

Nothing plays (still frames) in these states (`rememberArtworkMotion`, `AnimatedPlayback.shouldPlay`):

| State | Plays |
|---|---|
| A dialog, the side menu or the filter sheet covers the screen | No |
| A context menu is open | Yes: a context menu does not stop it. The covers stay visible beside its card, dimmed, and keep playing without a restart (`layerStopsDashboardArt`). |
| Launch loader, portal, stream exit or its result | No |
| In a stream or the background | No |
| Reduced motion or the system's animations off | No |

### 4.4 Picker tiles

1. Every visible animated tile loops (no count cap).
2. A tile's animation (the animated thumbnail, else the full WebP) is fetched once into a file in the app's cache
   (never the heap) and decoded from there at 520 px across at most.
3. These tiles show the first frame and hold no decoder: off-screen tiles; all tiles while the filter sheet or a dialog
   covers the grid; all tiles in the background; all tiles with reduced motion.
4. All tiles share one `AnimationTicker`. The drawables report new frames, the ticker turns them into one redraw per
   frame, and it does not run while nothing plays.

## 5. Built-in entries

1. Desktop, Remote Input, Remote Monitor and Steam Big Picture use fixed bundled cover/wide/hero artwork and render
   their names instead of logos.
2. These images always win over old local choices, SteamGridDB and host artwork. Existing user files are ignored, never
   deleted.
3. Their context menu has neither Artwork nor Metadata.
4. A stale artwork route is a read-only preview with a Back button.
5. Built-ins never fetch game metadata or create its cache, show no genre/HDR/HLTB badges, and appear only while all
   library filters are inactive ([GAME_INFO.md](GAME_INFO.md)).

`BundledArtwork.kind(App)` is the shared identity policy:

| Entry | Identity |
|---|---|
| Remote Input/Monitor | The fixed host UUIDs, even after renaming, with name fallbacks for older hosts |
| Desktop and Steam Big Picture | Trimmed case-insensitive names (including `Steam BigPicture`). Their installation-specific generated UUIDs are not assumed constant. |

## 6. Rebuilding the bundled assets

Command: `python tools/artwork/generate.py --preview` (Pillow 11.3.0, fonttools 4.67.0).

| Input or output | Detail |
|---|---|
| Phosphor Fill sources | Pinned and checksum-verified; cached in ignored `build/phosphor-icons` |
| Steam emblem | The checksum-verified canonical Valve geometry in `tools/artwork/steam-emblem.svg` |
| Covers | 600×900 |
| Wide covers | 920×430 |
| Heroes | 1920×1080, text-free |
| Previews | Stay in `build/artwork-preview` |

## Settings

| Setting | Where | Detail |
|---|---|---|
| Blur amount and opacity of the game background | Appearance → App background | Keys and values: [SETTINGS.md](../SETTINGS.md) |
| Animated artwork | None | There is no setting |

## Code map

| Path or class | Role |
|---|---|
| `ui/HeroGeometry.kt` | Hero scaling, pure; `HeroGeometry.FADE_STOPS` (soft bottom edge), `HeroGeometry.liveScale` (size of the live hero's layer) |
| `ui/Backdrop.kt` | `HeroLive`: the animated hero of the home screen |
| `ui/HeroLoop.kt` | `LoopedAnimation`: each pass of the hero as a drawable that plays once; `HeroLoopTimeline.fadeMillis` |
| `AnimatedWebp.loopMillis` | The loop's length: the sum of the `ANMF` frame durations |
| `AnimatedPlayback` | `shouldPlay` (pure playback rules), `decodeSize`, `HERO_DWELL_MS`, `HERO_HOLD_MS` |
| `GameArt.animated` | The chosen animated hero of a game |
| `ArtworkStore.animatedFile` | Answers with the stored file when it is an animated WebP |
| `AnimatedArtLayer` | The animation over the still image of a tile or logo |
| `AnimationTicker` | One redraw per frame for all animated images of a screen |
| `screens/DashboardArt.kt` | `DashboardArt`: the home screen's one `AnimationTicker` |
| `layerStopsDashboardArt` | Which covering layer stops the dashboard's animations |
| `rememberArtworkMotion` | Whether artwork may animate |
| `GameTile` | The row tiles of the home screen |
| `ArtworkScreen` | The Artwork page |
| `SelectionPillHost` | The tab pills |
| `RowGlideHost` | The plate shared by the active-filter chips and Reset |
| `FocusScroller` | Grid scroll |
| `screens/FamilyLayout.kt` | `ArtGridLayout`: tile sizes and the room between tiles |
| `data/ArtworkFilters.kt` | `ArtFilterRules`, `ArtworkInfo`: filter logic |
| `SgdbImage.parse` | Parsing of SteamGridDB answers |
| `BundledArtwork.kind(App)` | The shared identity policy of the built-in entries |
| `tools/artwork/generate.py` | Rebuilds the bundled assets |
| `tools/artwork/steam-emblem.svg` | The canonical Valve geometry of the Steam emblem |

## Verification

| Test | What it enforces |
|---|---|
| Unit tests of `ui/HeroGeometry.kt` | Hero scaling: one geometry for every path that draws the hero |

### Device checks (date not recorded)

| Device | Check | Result |
|---|---|---|
| Odin | Automated RIGHT/LEFT focus changes between two games with animated covers and hero (0.3 s apart), with the design of [Frame cost of the home screen](#24-frame-cost-of-the-home-screen) (Compose `Crossfade`, 500 ms dwell, low-resolution live hero) | About 3 % janky frames, p50 6 ms, the same as a still hero |
| Odin | A `RenderEffect` on a rotating full-size layer for the animated hero | 48 % janky frames |

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [GUI.md](../GUI.md)
- [START_AND_HOME.md](START_AND_HOME.md)
- [GAME_INFO.md](GAME_INFO.md)
- [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md)
- [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md)
- [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md)
- [SCREEN_SHAPES.md](SCREEN_SHAPES.md)
- [SHELL_ARCHITECTURE.md](SHELL_ARCHITECTURE.md)
- [LAUNCH_TRANSITION.md](../LAUNCH_TRANSITION.md)
- [SETTINGS.md](../SETTINGS.md)
- [#15](https://github.com/Meitoncz/Delos/issues/15): host artwork synchronization decisions
