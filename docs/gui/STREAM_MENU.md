# Stream and stream menu

## Purpose

The stream menu is the in-stream menu of Delos: nine glass tiles over the lower part of the stream with the pages
behind them in a popover. This document specifies the stream-related rows of the Upscaler page, the Bitrate page and
the statistics panel, the layout, navigation and motion of the stream menu, and the two debug previews that draw the
context menu and the stream menu without a stream.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Upscaler page rows, Bitrate recommendation row, Link block and SGSR output rows of the statistics panel, automatic saving of the menu's changes | Codec-specific behavior and measured results: [STREAM_QUALITY.md](../STREAM_QUALITY.md), [SGSR2_VIDEO.md](../SGSR2_VIDEO.md) |
| Stream menu: dock, tiles, head, caption, gradient, popover, row focus, navigation, touch, motion | Shared durations and easings (`Motion`): [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| Stream-menu entry points, delayed handlers, stream window flags, `ActiveKeyboardInput` | Delos Full keyboard: [FULL_KEYBOARD.md](FULL_KEYBOARD.md) |
| Context menu preview (debug builds) | The context menu itself and Settings: [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) |
| Stream menu preview (debug builds) | Stream exits and the `DelosStreamActivity` structure: [SHELL_ARCHITECTURE.md](SHELL_ARCHITECTURE.md) |
| | Index of the GUI documents and design principles: [GUI.md](../GUI.md) |

## 1. Stream

### 1.1 Upscaling

- SGSR upscaling and independent sharpening are implemented.
- Codec-specific behavior and measured results are documented in [STREAM_QUALITY.md](../STREAM_QUALITY.md) and
  [SGSR2_VIDEO.md](../SGSR2_VIDEO.md).
- Current quality/performance acceptance is tracked only in [#19](https://github.com/DELOS-STREAM/DELOS-android/issues/19).

### 1.2 Upscaler page

The Upscaler page of the stream menu changes a row with one press.

| Row | Content | Shown |
|---|---|---|
| **Enabled** | The on/off toggle for the upscaler and Sharpen only alike. | Always |
| **Supersampling quality** | The supersampling quality. | Only while the stream matches the display and the mode scales |
| **Mode** | SGSR 1 (Normal), SGSR 1 High Quality, SGSR2-DV, SGSR2-DV (Quality), **Sharpen only**. | Always |
| **Sharpness** | The sharpness slider. | Always |

- The supersampling quality, mode and sharpness rows show the value just requested, not the one the renderer applied
  on its last frame. A fallback from 2× to 1× corrects the row afterwards.
- Mode and Sharpness change live.
- Switching Mode to or from Sharpen only rebuilds the output stage the way the Enabled toggle does (the picture may
  flicker once), because Sharpen only draws at the stream's size.

In **Sharpen only**:

- The Supersampling quality row is hidden: nothing is scaled, so it has no meaning.
- The page is titled Upscaler.
- The Sharpness slider is the whole effect.
- The tile's status reads "On: Sharpen only" in the help.

### 1.3 Bitrate page, recommendation row

| Property | Value |
|---|---|
| Shown | PyroWave always; an ordinary codec once a link test exists |
| Position | A row above Apply |
| Text | `Recommended: 135 Mbps · drops frames at 225 Mbps` |
| Icon | Thumbs up, the glyph of every "recommended" row |
| Value | **Fill**, a scrolling label like every row |
| Refresh | Once a second, from the stream's own counters and the last link test |

- A fills the slider with the recommendation; only Apply sends it.
- The row's help says what the number rests on and, for an untested link, that testing is done in Settings before
  connecting. The host refuses probes during a stream, so there is no Test link here.
- An ordinary codec without a test shows only the slider and Apply.

The compact statistics line stays at the top center with its normal border. In it and in the full statistics panel (the FPS in the heading and the Rendering, 1% low, Network and Lost frames rows), network delay warns at 60 ms and shows an error at 100 ms; loss warns at 1.5% and shows an error at 5%. The FPS figure warns when the rendered rate falls below 85% of the sent rate and shows an error below 60%; the 1% low warns below 60% of the sent rate. Rate comparisons require a positive sent rate. Only the figures change color.

### 1.4 Statistics panel, Link block

The Link block is shown in the full panel, for PyroWave only, after Backlog.

| Row | Example | Meaning |
|---|---|---|
| **Receiving** | `212 of 225 Mb/s` | The app's whole received rate against the bitrate in force. |
| **Partial / lost** | | Frames per second. |
| **Verdict** | `Link keeps up at 225 Mbps`, `Link drops frames at 225 Mbps, try 135 Mbps`, `Device skips frames; the link is fine`, `Measuring` | |

### 1.5 Statistics panel, SGSR output rows

The SGSR output rows are shown in the full panel, for MediaCodec streams whose output goes through the upscaler or
Sharpen only. They use the same label/value style as the other rows.

| Row | Position | Example | Meaning |
|---|---|---|---|
| **Upscaling** | | | The GPU time per frame read from the frame fences (the CPU submit time until the first fence is read). |
| **Frame queue** | After Upscaling | `1.0 avg / 1 max waiting · age 0.6 / 1.9 ms · 0.0 skipped /s` | See the list below. |
| **Render latency** | Before Total latency | `6.1 / 7.9 / 9.3 ms avg/p95/max` | The time from the frame reaching the output stage to the display presenting it, over the last second. |

**Frame queue** states three values:

1. How many decoder frames were waiting when a draw started (1 = the output kept up).
2. How long the newest one had waited.
3. How many older frames per second were skipped because a newer one was ready (non-zero = the output thread cannot
   keep up with the decoder).

When the GPU already had two frames of work in flight, the Frame queue row ends with `· 30.0 GPU busy /s`: frames per
second that waited for it and were replaced by a newer one without being drawn (the GPU work per frame is longer than
the frame interval). After two seconds of that, one toast per stream and upscaler mode says "Upscaler is too heavy for
this frame rate: lower the supersampling or the frame rate". No setting changes by itself.

**Render latency**:

- It is shown only while the statistics overlay is on and the platform reports the display's present time.
- It does not include the controller, network, host or decoding, so it is not the delay between a press and the
  changed picture (see [Latency](../STREAM_QUALITY.md#latency-75)).

### 1.6 Automatic saving

- There is no save button. Every change on the Upscaler page applies live and is kept for this game automatically, as
  that game's overrides of the four upscaler settings (`MenuAutoSave`).
- The save happens about 500 ms after the last change, or when the menu closes or the stream ends.
- A PlayStation stream has no per-game settings: the same changes, and the frame generation switch and multiplier, are
  written to the PlayStation settings (`PsPrefs`), so the next console stream starts with them. On a PC stream frame
  generation set in the menu lasts for that stream only.
- The profile is never written from the menu.
- Nothing is written while the menu merely opens.
- There is no toast.
- A stream without its launch identity stays session-only.

### 1.7 Volume dial

The hardware volume keys use one display-only dial in the shell and stream, including the stream menu and exit window.
It has no focus, touch or controller target. It replaces Android's volume panel and the old stream-volume toast.

| Property | Approved value |
|---|---|
| Stage placement | Centre (960, 864) on 1920 × 1080; bottom clearance 92 stage pixels |
| Dial | 248 stage pixels; 8 px compositor background blur, translucent dark gradient, circular outline |
| Ring | Radius 91, stroke 8, round ends; gold `#f6b73c`, track `#eef3fb` at 12.5% |
| Fill motion | 420 ms `EaseSpring`, retargeted from the currently drawn level; clamped arc at both endpoints |
| Centre | 42 px output glyph, 43 px Unbounded Medium percentage, 22 px Figtree Medium percent sign; no route caption |
| Output glyph | Speaker; headphones for cable/USB; headphones with a Bluetooth badge; speaker-slash when muted |
| Appearance | 280 ms fade and 620 ms 16 px rise; 240 ms exit; hides 1500 ms after the last key-down |
| Reduced motion | Immediate level changes and appearance, no spring or slide |

The shared `VolumeController` exposes 0–15 steps (0–100%, rounded). Without a USB DualSense it changes Android's media
index with flag 0, so no system panel appears. With a USB DualSense it changes only the gain of audio played by Delos,
whether the sound is kept on the device or allowed onto the controller; the haptics track and its system index are untouched.
For device audio, the reference is the last real media index recorded for that physical output before controller takeover.
For controller audio, it is the active controller media index. Gain is the amplitude ratio derived from Android's decibel curve;
attenuation uses track volume and amplification uses saturated samples. The requested index is capped at the platform maximum.

If no device reference has ever been recorded, the initial gain is neutral and the dial starts at 9/15 (60%); this is not
persisted as a measured reference. A muted reference stays silent until Volume Up, then uses index 1 as its decibel reference.
Android can still enforce hardware mute on an off-route output: software cannot guarantee matching every hardware level.
Stream PCM, interface cues and the original 48 kHz stereo 24-bit intro all use this gain. The intro follows
`AudioTrack.getTimestamp`; the existing `SOUND_SYNC_MS = -350` visual offset is unchanged.

The HUD is a small non-focusable, non-touchable window. Android 12+ background blur can sample the real SurfaceView
through the compositor; Compose does not pretend to sample that video. Blur availability depends on the OS/compositor.
Placement on shorter stages clears the hint and cutout areas without shrinking the dial.

**Known limitation:** overview trailers (WebView/YouTube) are outside the shared PCM gain and device-output routing.
Their audio is not automatically level-matched when a DualSense is connected. Real-device blur, routing, loudness and
intro synchronization require hardware verification; a browser prototype does not prove those behaviors.

## 2. Stream menu

The dock has a camera pill at its top right, outside the tile order: 96 px high, the tiles' glass, the camera and the label
"Screenshot", with the tile row's 109 px inset from the top and from the right. It enters and leaves with the dock's head, takes the
app's focus ring, glow, 1.04 lift and press, fills its icon and, while focused, shows the primary action's hint under it in the glyph
of the input in use (the pad's A, the tap, the click or Return). Up from a dock tile focuses the camera; Down returns to that tile.
A or touch captures without closing the menu (prototype `screenshot-fx` A, owner 2026-11-27):

- The pill pulses: 0.93, then 1.07, then 1 in 520 ms.
- A lens wave (the intro's Shockwave profile) runs from the camera to every corner in 1.3 s: 22 px strength fading to 0, band
  60 to 160 px wide, a 7 % light on the front. It bends the copied frame, drawn only inside the band and only where the video is,
  under the menu's gradient; it fades in when the copy arrives. Android 13 and newer.
- A bright core flashes at the camera with a 16 % veil over the screen, in over 3 % of the run, out on a cubic.
- 26 motes ride the front out (left and down), fading with the wave.
- When the PNG is written, the camera turns into a check drawn in the accent colour and the label into "Saved"; the old word rises
  out blurred while the new one rises in, for about 2 s, then both return the same way. The pill keeps its width. A failure shows
  the usual error toast instead. Reduced motion: no pulse, wave, flash or motes; the check and the word switch at once.

PixelCopy reads only the video surface at its buffer size into an extended-range bitmap, so an HDR picture is converted
instead of written as washed-out code values; highlights above SDR white roll off softly into an 8-bit sRGB PNG in
`Pictures/DELOS`, published only after a successful save. Android's normal screenshot gesture is unchanged and captures
everything.

### 2.1 Design

The design is "Console tiles" (prototype B, approved):

1. **Nine glass tiles** in one row over the lower part of the stream.
2. The game's name and the A / B hints above the row's left end.
3. The **complete help text** of the focused tile under the row.
4. The pages behind the tiles in a **popover above the tile that opened it**, a caret on its bottom edge pointing at
   that tile.

- While the menu is open the player looks at the menu, not at the game, so it is generous: big tiles, the whole help
  text in large type, an unhurried entrance.
- The stream is never dimmed as a whole: only a soft gradient sits behind the dock (no card behind the tiles).
- Sizes are stage pixels (1920 × 1080, the dock bottom-aligned on it).
- The numbers of `StreamMenuLayout` are taken one for one from the CSS variables of the design prototype, which is not
  part of this repository. The prototype's `#quickmenu` / `#qmpop` show the same design.
- This section together with `StreamMenuLayout` is the specification.
- The classes that hold each part are listed in [Code map](#code-map).

### 2.2 Ownership and entry points

- `StreamMenu` owns every stream-menu entry point, including controller, touch and Back.
- Guide/Home and Back (issue [#102](https://github.com/DELOS-STREAM/DELOS-android/issues/102)): a **tap** of the controller's
  Guide/Home button or of the device's built-in Back button opens the stream menu and sends nothing to the PC; a
  **hold** of 500 ms sends Guide to the PC (Steam overlay, Game Bar). The hold is decided while the button is held:
  Guide goes down on the PC when the 500 ms pass and comes up with the button. `GuideTapHold` (pure state, the
  caller's clock, unit tested) decides; `ControllerHandler` keeps one per button and the hold timer. A Back that
  Android delivers as a system key instead of a controller key (`onBackPressed`) only opens the menu.
- The stream menu buttons (Settings → Controls → Stream menu) are the user's own combination, **none by default**
  (L3 + R3 was the default until 1.1.0: a profile that never recorded one has none now, a recorded one is kept). The
  row's list clears them (Not set) or records new ones.
- The 1.1.0 setting Hold Back for Guide is removed: a hold of Back is always Guide now.
- There is no classic dialog fallback.
- `GameMenu` remains only as the special-key action helper and storage constants used by the stream menu and keyboard
  layouts; it does not create a menu window.
- TV poster cache/provider paths are independent of the classic grid asset adapters, which are removed.

Three delayed actions use explicit main-Looper handlers:

| Action | Delay |
|---|---|
| Delayed shortcut releases | 25 ms |
| Pointer recapture after focus | 500 ms |
| USB attachment handling | 1000 ms |

- The stream window keeps its immersive/multi-window system-UI flags and display selection. These are compatibility
  behavior on Android 10+, not interchangeable with app-window bounds or the shell's separate modern insets
  controller.
- The custom and full on-screen keyboards resolve `ActiveKeyboardInput` for each event, not a static Activity or a
  cached session.
- The narrow target of `ActiveKeyboardInput` is weakly held, available only while connected, and owner-cleared when
  the stream Activity is destroyed. An old owner cannot clear a replacement stream.
- Accessibility focus/key forwarding and external controller window ownership keep their own paths.

### 2.3 Tiles

The tiles, in order:

| Tile | Popover page | Pill | Notes |
|---|---|---|---|
| Resume | No | No | |
| Keyboard | No | No | The Delos Full keyboard ([FULL_KEYBOARD.md](FULL_KEYBOARD.md)): A closes the menu, then toggles it. |
| Overlay | No | On / Off pill; says Compact | Toggle. |
| Brightness | Yes | No | |
| Bitrate | Yes | No | |
| Upscaler (Supersampling) | Yes | On / Off pill; shows its status word | Toggle. |
| More | Yes | No | |
| Disconnect | No | No | |
| Quit app | No | No | |

| Element | Value |
|---|---|
| Tile | 150 px glass, corner 42 |
| Gap | 40 |
| Row width | 9 × 150 + 8 × 40 = 1670, 125 on each side |
| Top edge of the tiles | y 628 |
| Icon | 64, Phosphor, filled on focus |
| Label | 24 SemiBold, 24 below the tile |
| On / Off pill | 18 bold, 34 high, at the tile's top right corner (right −12, top −14) |
| Focused tile scale | 1.12 (spring, 420 ms) |
| Focused label | Gold, 8 px lower |
| Gold ring | Box 186, corner 56, 4 px, outer glow and a soft glow inside that breathes over 2.6 s |
| Other tiles while a popover is open | Dimmed to 46 % (labels 40 %) |

- The **Keyboard** tile has the plain keyboard icon (`Symbols.KEYBOARD`, the one of the Settings > Controls
  virtual-keys row). Android's system keyboard in the More page has its own text-cursor icon.
- The Keyboard tile turns accent while the keyboard is up but has **no On / Off pill**.
- Android's system keyboard is the More row Open Android keyboard.
- The other toggles (Overlay, Upscaler) have an **On / Off pill** and turn accent: accent glass and icon, accent pill.
- **Focus** scales the tile, lights its glass, adds a glow and turns the label gold.
- One **gold ring** glides under the focused tile.
- While a popover is open the ring rests on the tile that opened it.
- The focus on the dock **wraps**: Right on the last tile goes to the first, Left on the first to the last. The ring
  glides across the bar like any move. The popover's rows do not wrap.

### 2.4 Head

| Element | Value |
|---|---|
| Game's name | Unbounded 36, one line |
| **A Select** / **B Back** hints | Stacked under the name; 44 px glyphs, label 24 |
| Position | Bottom edge 46 above the tiles, at the tile row's left edge |

### 2.5 Caption

- The focused tile's name is not repeated. Its **complete help** is centered under the labels.
- The caption never grows, scrolls or runs as a marquee.
- `StreamMenuLayoutTest` checks every text the caption can show (all tiles, every upscaler status) at scales 1.0, 1.15
  and 1.3 against the characters of a line. A text that does not fit fails the test and is shortened in
  `StreamMenuHelp`.
- A different text slides up 18 px out of a 6 px blur (420 ms).
- The caption is gone while a popover is open.

| Element | Value |
|---|---|
| Type | 28 with line height 1.4 |
| Width | At most 1100 |
| Line breaks | Balanced |
| Box | Exactly three lines at the current interface scale |

### 2.6 Gradient, scrim and glass

| Position | Black |
|---|---|
| Down to 34 % of the height | Transparent |
| 48 % | 22 % |
| 66 % | 55 % |
| 84 % | 80 % |
| Bottom edge | 90 % |

- An open popover adds a 22 % scrim.
- Glass is drawn as the prototype's tints and white gradients over a dark undercoat: 28 % under the tiles
  (`TILE_BACKING_ALPHA`), 30 % under the card (`CARD_BACKING_ALPHA`).
- The stream is a separate window (a `SurfaceView`), so Compose cannot blur it. The menu's window blurs the stream
  behind it instead (`FLAG_BLUR_BEHIND`, `StreamMenu`), on Android 12 and newer with cross-window blur enabled; the
  undercoat keeps the glass readable over a bright picture, and is all there is where the system cannot blur.

### 2.7 Popover

The popover is a glass card **above its own tile**, centered on the tile.

| Element | Value |
|---|---|
| Card glass | Tint rgba 34/44/84 at 50 %, white gradients |
| Card corner | 44 |
| Card shadow | 0 40 90 −20 |
| Distance from the stage's left and right edges | 120 |
| Box end | 322 above the stage's bottom edge |
| **Caret** | The shared caret (60 × 24, [MENUS_AND_SETTINGS.md 3.1.1](MENUS_AND_SETTINGS.md#311-the-caret)), ONE path with the card so the glass is continuous; at the tile's center, at least 72 from the card's corners |
| Padding | 40 × 30 (24 below) |
| Header | 64: the back circle (focusable, B does the same) and the title in Unbounded 30 |
| Rows | 72 tall with 8 between them, corner 28 |
| Row icon | 32 in a 56 column |
| Row label | 24 SemiBold |
| Row values | 24 |
| Switch | 78 × 44 |
| Slider | 240 × 12 with a 32 knob |
| Choices | Between two chevrons |

| Page | Width |
|---|---|
| Brightness | 780 |
| Bitrate | 860 |
| Upscaler | 980 |
| More and the pages opened from it | 920 |

Margin and height:

- **One margin M = 120** (`POPOVER_MARGIN`) is the card's gap to the left, right and top edges of the stage. It is the
  gap More (the rightmost popover, 920 wide centered on its tile, its right edge at 1800) has to the right edge.
- Every page keeps its content's height up to **one shared maximum**, the space from the card's bottom (734) up to M:
  614 (`popoverMaxHeight`). Small pages stay small.
- Beyond the maximum, the rows scroll inside the card with faded edges (D-pad focus scrolls the focused row into
  view). The header and the help stay fixed.
- More (11 rows) and a long list of keys scroll.
- The Upscaler page with Supersampling (601.6 tall) scrolls only at interface scale above 1.15.
- The card stays clear of the compact statistics line (bottom edge 61.2, at interface scale 1.3 68.8, by
  `compactStatsBottom`).
- The help of the focused row stands under the rows in a box of two lines (three from interface scale 1.15 up),
  complete, never scrolling.
- A card pushed against the right edge keeps M there too (the top gap and the side gap are equal) and the caret still
  points at its tile: the card is clamped, the caret stays inside its corners.
- The pages opened from More (Send keys, Commands, Mouse mode) are anchored over the More tile.

Rows of the More page:

| Row | Notes |
|---|---|
| Send keys | Opens a page. |
| Send clipboard | |
| Get clipboard | |
| Commands | Opens a page. |
| Task manager | |
| Zoom | |
| Mouse mode | When allowed. Opens a page. |
| On-screen controls | |
| **On-screen controls opacity** | See below. |
| **Open Android keyboard** | An action: closes the menu, then opens or closes Android's system keyboard. |
| Touch sensitivity | |
| Rumble feel | |
| **Gyro smoothing** | See below. |

**Gyro smoothing**:

- A 0 to 100 % slider in steps of 5, reading "0 % (off)" at 0.
- It is shown only where the Settings row is shown.
- It is dimmed with its reason in the help line while neither the external nor the built-in gyroscope is on.
- It applies at once to the running sensors.
- It is kept for this game automatically like the upscaler's settings: about 500 ms after the last change, or when the
  menu closes or the stream ends. It is saved as this game's `seekbar_gyro_smoothing` override, never the profile,
  which is changed in Settings.
- See [STREAM_QUALITY.md](../STREAM_QUALITY.md#gyro-smoothing-77).

**On-screen controls opacity**:

- A 0 to 100 % slider in steps of 5, the same profile value (`seekbar_osc_opacity`) as the row in Settings → Controls
  ([MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md#15-on-screen-controls-opacity)).
- It applies at once to the running on-screen controls (`DelosStreamActivity.setVirtualControllerOpacity`, which also
  keeps it for every later layout of them in this stream) and is saved in the profile at every change, not per game.
- It shows whether the on-screen controls are on or off.

**Key layout** and **Rotate** are not offered: the key-layout overlay is hidden (its code and preferences stay) and a
rotation re-lays out the overlays.

### 2.8 Popover row focus

- One local `RowGlideHost` inside the rows viewport carries the focus plate between rows in 520 ms.
- A remembered painter reuses its paths, paint and stroke. No row-local focus tint/ring/shadow fades behind it.
- Keys and Commands use a capsule plate while retaining their one-row (scrolling when it does not fit) layout and
  static white 10% pill base. The base composites above the traveling tint.
- Back stays fixed outside the plate and is the first ordered target on linear pages. Held Up stops there and Down
  stops at the last row.
- The row glide leaves More entry-row restoration, B/back and immediate Left/Right choice/slider adjustment as they
  are.

| Focus plate element | Value |
|---|---|
| Fill | Accent 17% |
| Ring | 3 px inset |
| Corner | 28 |
| Shadow | Inner shadow |
| Travel between rows | 520 ms |

### 2.9 Navigation

| Input | Where | Result |
|---|---|---|
| Left/Right | Dock | Move along the tiles. |
| A | Toggle or action tile | Acts at once. |
| A | Brightness, Bitrate, Upscaler or More | Opens its popover and puts the focus on its first row. |
| B | Popover | Closes it and returns the focus to its tile. |
| B | Send keys, Commands, Mouse mode | Goes back to that row of More first: these pages are opened from More. |
| B | Dock | Resumes the stream. |

- The focus cannot leave the popover with the D-pad.
- The open and close buttons (a tap of Guide/Home or Back, the user's own buttons, the swipe up from the band above the edge) and the external display's input
  routing are unchanged by the menu.

**Touch**:

| Tap | Result |
|---|---|
| On a tile | A. |
| On another tile with a page while a popover is open | Morphs the popover to that page. |
| On the same tile while its popover is open | Closes it. |
| On a tile without a page while a popover is open | Nothing. |
| Outside the card and the tiles while a popover is open | Closes the popover; the menu stays open. |
| Anywhere that is not a tile or the card, with no popover open (above the dock, beside it, on its text) | Closes the menu, like Resume. Never quits or disconnects, and the tap never reaches the game. |

**Swipe up** (turned off in Settings → Controls → Stream menu): the gesture does not start at the screen's edge, where Android's gesture bar takes it. It starts in a band above the edge: the bottom 18 % of the window's height, without the strip the system keeps for its gestures (the larger of the system gesture inset and 24 dp). It opens the menu once the finger has gone up by 12 % of the height and farther up than sideways. A touch that starts in the band is kept from the game until this is decided (on-screen controls in that band do not get it); a touch outside the band goes to the game as usual. While the loader is shown the swipe does nothing. The arithmetic is `StreamMenuSwipe` (unit tested).

### 2.10 Motion

Every number is a `--d-*` / `--ease-*` variable of `b.html`, copied to the shared `Motion`
([MOTION_AND_COLORS.md](MOTION_AND_COLORS.md)) and `StreamMenuMotion`.

The **entrance** runs on one clock (about 1.3 s):

| Element | Motion |
|---|---|
| Tile | Rises 90 px from a 0.8 scale with the overshooting spring (620 ms), 42 ms after the one before, starting 100 ms in |
| Labels | Fade in 140 ms after their tile |
| Head | Rises 24 px from 60 ms |
| Ring | Scales in from 0.7, 260 ms after the first focused tile |
| Caption | Rises 20 px from 520 ms |

The **exit**:

| Element | Motion |
|---|---|
| Tiles | Drop 46 px to a 0.92 scale (240 ms, ease-in) |
| Everything | Fades, the last tile first (8 ms apart) |
| Window | Closes 320 ms after the exit starts |

Focus:

| Element | Motion |
|---|---|
| Tile and icon | Springs |
| Ring | Glide 560 ms with a slight overshoot (`--ease-glide`) |
| Label | Gold |

**Popover**:

| Element | Motion |
|---|---|
| Dock | Sinks 160 px over 640 ms |
| Card, opening | Grows from 0.86 around the caret's tip with the spring (560 ms) while its glass fades in over 336 ms |
| Rows, opening | Rise 18 px and fade in 46 ms apart |
| Card, closing | Shrinks back to the caret in 240 ms |

Another tile's page **morphs** the card:

| Element | Motion |
|---|---|
| x, width, caret and height | Follow their targets with a spring (stiffness 190, damping ratio .8) |
| Old rows | Rise out in 140 ms |
| New rows, the title and the help | Enter after 150 ms |

Controls:

| Element | Motion |
|---|---|
| Switch knob | Springs across |
| Choice | Its chevron nudges 7 px and its new value slides in 22 px |
| Slider knob | Grows to 1.18 when focused |
| Pill that changes | Pops in from 0.6 |

With reduced motion every transform is dropped: only fades (and colors) remain, the ring and the dock jump, the
geometry snaps.

## 3. Context menu preview

The preview exists in debug builds. It draws the real context menu over a drawn dashboard (hero, game tiles, a PC
card), without the shell. `ContextMenuPreview` in `src/debug` implements it; `ShellActivity` hands the intent to it,
and `src/release` replaces it by a stub.

```
adb shell am start -n io.github.meitoncz.delos.debug/com.limelight.shell.ShellActivity --es preview_screen context_menu \
  [--es preview_state <idle|running|other|options|long|pc>]
```

The menu opens 700 ms after the start with its entrance and stays. A tap outside closes it.

| `preview_state` | What is drawn |
|---|---|
| `idle` | The game menu of a game (nothing runs). |
| `running` | The game runs (Resume, Quit app in red). |
| `other` | Another game runs. |
| `options` | The Options submenu is open (the mechanism no real menu uses yet). |
| `long` | The 12 matches of a pick list that scroll inside the card. |
| `pc` | The PC menu below its card with the caret on top. |

## 4. Stream menu preview

The preview exists in debug builds. It draws the real menu without a stream, over a sunset "game scene", with made-up
values. `FakeStreamMenuHost` in `src/debug` supplies the values; `ShellActivity` hands the intent to
`StreamMenuPreview`, which `src/release` replaces by a stub.

```
adb shell am start -n io.github.meitoncz.delos.debug/com.limelight.shell.ShellActivity --es preview_screen stream_menu \
  --es preview_page <main|upscaler|bitrate|brightness|more|keys|commands|mouse|rumble> --ei preview_focus <tile 0..8> \
  [--ei preview_upscaler_mode <0..4; 4 = Sharpen only, whose page has no Supersampling quality row>] \
  [--ei preview_row <row of the page to focus>] [--ez preview_animate true]
```

| Extra | Type | Values | Required |
|---|---|---|---|
| `preview_screen` | `--es` | `stream_menu` | Yes |
| `preview_page` | `--es` | `main`, `upscaler`, `bitrate`, `brightness`, `more`, `keys`, `commands`, `mouse`, `rumble` | Yes |
| `preview_focus` | `--ei` | Tile `0..8` | Yes |
| `preview_upscaler_mode` | `--ei` | `0..4`; `4` = Sharpen only, whose page has no Supersampling quality row | No |
| `preview_row` | `--ei` | Row of the page to focus | No |
| `preview_animate` | `--ez` | `true` | No |

- The menu starts already open at that page without its entrance.
- `--ez preview_animate true` plays the entrance, so a screenshot after about 1.4 s shows the settled result.
- No profile is needed.
- Resume or B close the menu; a tap on the scene opens it again.

## Code map

| Path or class | Role |
|---|---|
| `StreamMenuLayout` | The numbers and the pure geometry of the stream menu. |
| `Motion` | The shared durations and easings. |
| `StreamMenuMotion` | The dock's own delays. |
| `StreamMenuBar` | The dock. |
| `StreamMenuPopover` | The popover and its rows. |
| `StreamMenuRoot` | The whole menu in one composable, so a window of its own can host it. |
| `StreamMenuHost` | The stream behind the menu. |
| `StreamMenu` | Implements `StreamMenuHost` with the live `DelosStreamActivity`; owns every stream-menu entry point. |
| `StreamMenuHelp` | The help texts of the caption. |
| `RowGlideHost` | Carries the focus plate between the popover's rows. |
| `MenuAutoSave` | Keeps the menu's changes for this game. |
| `GameMenu` | The special-key action helper and storage constants used by the stream menu and keyboard layouts. |
| `ActiveKeyboardInput` | The input target the custom and full on-screen keyboards resolve for each event. |
| `FakeStreamMenuHost` (`src/debug`) | Implements `StreamMenuHost` for the debug preview with fake state. |
| `StreamMenuPreview` | The stream menu preview; a stub in `src/release`. |
| `ContextMenuPreview` (`src/debug`) | The context menu preview; a stub in `src/release`. |
| `ShellActivity` | Hands the preview intent to `ContextMenuPreview` and `StreamMenuPreview`. |

## Verification

| Test | What it enforces |
|---|---|
| `StreamMenuLayoutTest` | Every text the caption can show (all tiles, every upscaler status) fits the characters of a line at scales 1.0, 1.15 and 1.3. |

Debug previews:

| Preview | Draws |
|---|---|
| [Context menu preview](#3-context-menu-preview) | The real context menu over a drawn dashboard. |
| [Stream menu preview](#4-stream-menu-preview) | The real stream menu over a sunset "game scene". |

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [GUI.md](../GUI.md)
- [FULL_KEYBOARD.md](FULL_KEYBOARD.md)
- [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md)
- [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md)
- [SHELL_ARCHITECTURE.md](SHELL_ARCHITECTURE.md)
- [STREAM_QUALITY.md](../STREAM_QUALITY.md)
- [SGSR2_VIDEO.md](../SGSR2_VIDEO.md)
- [#19](https://github.com/DELOS-STREAM/DELOS-android/issues/19)
