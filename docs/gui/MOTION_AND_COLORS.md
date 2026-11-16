# Motion, toasts and colors

## Purpose

The shell uses one motion language, one notification card and one set of color roles on every screen, so one press,
one focus and one menu feel the same everywhere. This document specifies the motion rules and their building blocks,
the toast (the console notification), the problem report card and the roles of the palette's three colors.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Motion rules, building blocks and where each screen uses them | Stream menu motion that is particular to the dock: [STREAM_MENU.md](STREAM_MENU.md) |
| Toasts: kinds, text, Undo, time, motion, cost, accessibility, hosts, preview | Settings row focus: [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) |
| Problem report card: look, place, controller, motion, Report | Logic, markers and privacy rules of the problem report: [DIAGNOSTICS.md](../DIAGNOSTICS.md) |
| The Diagnostics rows of Debug info and the Keep diagnostic logs switch | Design principles and the list of GUI documents: [GUI.md](../GUI.md) |
| Color roles of `accent`, `accent2`, `accent3` and the rules of the eight palettes | |

## 1. Motion

One motion language serves the whole UI. Every screen animates with the motion language of the stream menu
([STREAM_MENU.md](STREAM_MENU.md)).

### 1.1 Sources

| Path or class | Content |
|---|---|
| `Motion` in `shell/ui/Motion.kt` | The numbers, easings and pure arithmetic. It has no Compose runtime in it. `MotionTest` pins them and the stream menu's values. |
| `shell/ui/MotionKit.kt` | The building blocks. |
| `StreamMenuMotion` | Only what is particular to the dock: its entrance clock and the page morph. |

### 1.2 Rules

| Motion | Rule | Value | Easing |
|---|---|---|---|
| Focus | A spring. | 420 ms | `EaseSpring` |
| Press | A spring. | 120 ms | `EaseSpring` |
| Enter | Things enter by rising and fading in, one after the other. | Rise 18 px, 46 ms apart, 420 ms each | `EaseOut` |
| Leave | Everything leaves faster than it came. | 240 ms | ease-in |
| Menu | A menu grows out of its anchor. Its glass fades in faster. It shrinks back. | From 0.86 with the spring; glass 336 ms; shrinks back in 240 ms | The spring |
| Changed value | A changed value slides in from the side the key came from. The chevron it went toward nudges. | Slide 22 px, nudge 7 px | |
| Colors and opacities | They fade. | 280 ms | CSS `ease` |

Reduced motion drops every transform (rise, scale, slide, glide) and keeps the fades. Springs snap.

Nothing ever delays a control:

1. Elements are focusable and usable while they enter. An entrance is only how they appear.
2. A screen's elements finish within about a second. At most the first 8 places wait for the ones before them.
3. An element that shows up later (data arrives, a lazy list scrolls) just appears.

### 1.3 Building blocks

| Building block | What it does | Values |
|---|---|---|
| `Modifier.staggerEntrance()` | Fade and rise by place. The place comes from the surrounding `EntranceScope`, from an index, or from the counter of a stream menu page. | |
| `focusEffects` | Focus spring, press squeeze, ring, glow. `pressScale = 1f` turns the squeeze off. | Squeeze to 0.97 for 130 ms |
| `GlideRingHost` + `Modifier.glideTarget` | One ring that glides to the focused element. `Circles` uses it. | 560 ms with a slight overshoot |
| `RowGlideHost` + `Modifier.rowGlideTarget` / `SettingRowList` | The same for a scrolling list of settings rows. See "Settings row focus" in [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md). | 520 ms |
| `MotionSwitch` | The knob springs across and the gradient track fades in. | |
| `ChoiceMotion` with `valueSlide` and `chevronBump` | The value slide of choice rows. | |
| `animateKnobScale` | A focused slider's knob grows. | To 1.18 |
| `popEnter` / `popExit` | Grow from an anchor. | |
| `dialogEnter` / `dialogExit` | The entrance and exit of a dialog. | |
| `sideEnter` / `sideExit` | The entrance and exit of the side menu. | |
| `screenTransition` | The central crossfade of `Shell`: the new screen fades in while it settles from a zoom, and the old one leaves. | From a 1.035 zoom; the old screen leaves in 240 ms |

The gliding ring of `GlideRingHost` follows these rules:

1. The ring is drawn at the focused element's bounds of the same frame plus a carry that shrinks to nothing, so a row
   that scrolls carries its ring along.
2. A changed place of the element drops the carry at once.
3. A new focus starts from where the ring is drawn right then. Nothing queues.

### 1.4 Where each screen uses it

| Screen | Motion |
|---|---|
| Stream menu | Unchanged, built from the same blocks. |
| Start, Devices, Profile, pairing | `CenterColumn` is an `EntranceScope`, so the headline and then every PC circle rise in turn. One gold ring glides between the circles. The check badge of the default PC pops in when the choice changes. |
| Home | The first covers rise in turn; covers a scroll brings in later do not animate. The empty and offline messages rise. Every cover squeezes when activated. |
| Settings, app settings, side menu | `SettingRow` rises in turn. One ring glides between the rows and their glass fades with the focus; the side menu's rows fade their own. A choice slides its new value in with the chevron nudge. A slider's knob grows on focus. The switch is the stream menu's. |
| Context menus | They grow out of their caret's tip (`popEnter` / `popExit`). The header and rows rise in turn. The plate glides from row to row. |
| Side menu | It slides in over 560 ms. The header and rows rise in turn. It leaves in 240 ms. |
| Dialogs and the filter modal | They settle in from 0.96. Title, note, fields and buttons rise in turn. They leave in 240 ms. |
| Toasts (the console notification) | See [Toasts](#2-toasts). |

## 2. Toasts

Every app message is the same console notification, in the shell, over the stream and on the external display.
`ui/ToastHost.kt` is the one composable. The pure logic is in `ui/ToastLogic.kt`.

### 2.1 Place and card

| Element | Value |
|---|---|
| Place | Top right |
| Distance from the top | 112 stage pixels (below the shell's status bar) |
| Distance from the right edge | 44 stage pixels |
| Fill | Flat and opaque, `#17223C` → `#0E1628` |
| Corner radius | 20 |
| Ring | A faint inset ring |
| Blur | None (the stream is a `SurfaceView`) |

- Over a stream the card covers the game's corner (a mini-map, the stats strip). The owner accepted that.
- The opaque fill keeps the card readable over a white game frame.

### 2.2 Kinds

| Kind | Color | Color source | Icon | Word above the text |
|---|---|---|---|---|
| `INFO` | The palette's cyan, `accent2` | Follows the palette | Info | INFO |
| `SUCCESS` | Mint | Fixed semantic color | Check circle | DONE |
| `WARNING` | Orange | Fixed semantic color | Warning triangle | WARNING |
| `ERROR` | Coral | Fixed semantic color | Exclamation | ERROR |
| `ACTION` | The palette's third color, light | Follows the palette | Trash | UNDO |

- Each kind is a solid icon block with its icon, the word above the text, and the kind color on the glow and the
  block.
- A toast without an icon of its own shows its kind's icon.

The kind is chosen in this order:

1. A toast that carries an action is always `ACTION`.
2. A caller passes the kind: `ShellState.showToast(…, kind = ToastKind.ERROR)` or
   `DelosToast.show(context, text, DelosToast.LONG, ToastKind.WARNING)`.
3. Without a kind from the caller, `toastKindFor` reads it from the icon:

| Icon | Kind |
|---|---|
| `Symbols.ERROR` / `WIFI_OFF` / `LINK_OFF` | Error |
| `CHECK_CIRCLE` / `CHECK` | Success |
| `WARNING` | Warning |
| Everything else | Information |

### 2.3 Text and size

| Element | Value |
|---|---|
| Font | Figtree 600 |
| Size | 25 × interface scale (100 / 115 / 130 %) |
| Lines | Two lines at most, then an ellipsis |
| Maximum text width | `min(560, stage width − 360) × scale` |
| Card width | As wide as the widest line, measured once per toast |

- There is no marquee: a toast is never focused, and a scrolling line cannot be stopped.
- Everything on the card scales with the interface scale.
- 16:9, 21:9 and 4:3 only change the stage width.

### 2.4 Undo

- A toast with an action shows the gold chip with the white `Y` disc and the label (like the hint glyphs).
- **Y** on the controller (`GlobalKeys` → `ShellState.runToastAction`) or a tap runs the action.
- The chip squeezes for `PRESS_HOLD` before the card leaves.
- Undo exists only in the shell: the stream and controller windows host the card without any input.

### 2.5 Time

`ToastTiming` is the one place for the times.

| Toast | Time |
|---|---|
| Stream hint, short | 4 s |
| Stream hint, long | 6.5 s |
| Shell toast | 4.5 s |
| Toast with an action | 10 s |

The gold line along the bottom edge is the remaining time. It is one 4 px bar with no track and no glow. It draws in
(`EaseOut` 420 ms from +280) and then drains.

### 2.6 Motion

The numbers are in `Motion` and `ToastTiming`.

The card springs in from outside the right edge while it grows from 94 % to 100 % around its right edge. It fades
over `FADE` (280).

| Enter spring | Value |
|---|---|
| Damping ratio | 0.62 |
| Stiffness | 150 |
| First reaches its place | After 233 ms |
| Overshoot | 8.4 % of the travel at 327 ms, a single overshoot |
| Within 1 % | After 627 ms |

Everything else is timed against the landing (`LANDING_MS` 230):

| Element | Motion | Start | Constants and easing |
|---|---|---|---|
| Icon block | Pops from `POP_FROM_PILL` with a 50° turn. | +130 | `PILL_POP`, `EaseSpring` |
| Word, text and chip | Rise `RISE`, one `STAGGER_ROW` after the other. | From +140 | `ROW_IN` |
| Halo | A soft kind-colored halo (peak 42 % at the edge, reach 150 px) rises with the card. It spreads from 45 % of its reach to all of it while the toast is shown and fades out over the rest of the time the toast is shown. | Fade from +200 | `Ease` |
| Light sweep | One light sweep crosses the card. | From +250 | `EaseOut`, 860 ms |
| Timer line | Draws in. | From +280 | See [Time](#25-time) |

- The card leaves 72 px to the right and fades in `EXIT` (240 ms, `In`).
- A new toast replaces the one on the stage. There is never a queue. The old card leaves at once and the new one
  enters `ROW_DELAY` (120 ms) later, so the two cross.
- A toast that arrives in that gap replaces the waiting one, which never shows.
- Reduced motion: only the fades remain (no slide, scale, rise, pop, sweep).

### 2.7 Cost

- One clock ticks only while a toast is on the stage. An idle host draws and invalidates nothing.
- Every animated value is read in the draw phase (`graphicsLayer` / `drawBehind`). Nothing recomposes per frame.
- The drop shadow (peak 22 %, reach 44 px) and the sweep are many-stop eased gradients, built once per toast and
  faded by the `alpha` of the draw call. The drop shadow has four edge strips and four corner quadrants with a
  smoothstep falloff.
- The halo is drawn as rings 2 px apart whose opacity follows the same smoothstep falloff.
- The result is smooth at any opacity, with no blur, no `Path` and no per-frame allocation.

### 2.8 Accessibility

The newest card is a live region (polite, assertive for an error). Its description is the kind word and the text
(and the Undo label). TalkBack announces it although the card can never take focus, touch or key input.

### 2.9 Hosts

- `DelosToast` routes a message to the shell's own state or to a non-interactive decor overlay (`DelosToastOverlay`)
  in the stream and external-display controller windows.
- The overlay rejects touch, key and generic-motion dispatch, blocks descendant focus, is padded by the display
  cutout and sits below the stream-menu dialog.
- Hosts are weakly registered for their Activity lifecycle, including pending initialization messages.
- Service/helper messages use the visible foreground host.
- The only system-toast fallback is when no Delos host is visible (`DelosToast`).
- Closing the separate stream-menu dialog does not remove the stream toast host or discard a result delivered after
  stream focus returns.

The windows that blur or cover the stream (the stream menu, the exit) would blur the decor overlay behind them:

1. Each such window gets a second host as the topmost layer of its own window (`DelosToastOverlay.coverView`). It is
   above the tiles, the popover and the statistics strip, sharp, with the same non-interactive rules.
2. Both hosts draw one shared `ToastStage` (slots, start time, clock).
3. Only the newest covering window's host is active (`ToastCovers`). It takes over after its first drawn frame. The
   stream window's host takes over again when the covering window is gone. The toast carries on without restarting.
4. The controller window has no blurring window.

### 2.10 Preview

`ToastPreview` in `src/debug` shows the toast on the drawn game scene in debug builds:

```
adb shell am start -n io.github.meitoncz.delos.debug/com.limelight.shell.ShellActivity --es preview_screen toast --es preview_kind <info|success|warning|error|action> [--ei preview_double 1] [--ei preview_long 1]
```

| Option | Effect |
|---|---|
| `double` | A second toast 350 ms later, to watch the hand-over. |
| `long` | A message that needs the ellipsis. |

A tap on the scene or `Y` shows the toast again. `Y` is Undo on the `action` toast.

## 3. Problem report card

After a crash, an ANR, an unclean exit or a failed stream, Delos offers a report once. The logic, markers and privacy
rules are in [DIAGNOSTICS.md](../DIAGNOSTICS.md).

### 3.1 Card

`ui/ProblemOfferCard.kt` is a glass card in the top right corner of the start screen and the home screen, under the
toast's place.

| Element | Value |
|---|---|
| Corner | 34 |
| Fill | The shared `glass` fill |
| Border | 1 px `line` |
| Padding | 36 px |
| Width | 780 px |
| Distance from the top | 230 px |
| Distance from the right edge | 64 px |

| Content | Style |
|---|---|
| An accent disc with the bug icon | |
| Title: *Delos had a problem*, or *A stream could not run* | Unbounded 28 |
| One sentence for the kind of problem | Figtree 22 |
| The one-line technical summary | 18, at most two lines, ellipsis |
| The note: what Report does and that nothing is sent before the user chooses. With the report service (`DELOS_REPORTS_URL`) it is `shell_problem_note_upload`: Report sends a cleaned log to the Delos report service, kept 30 days, readable by anyone who has the link, and opens a GitHub draft | 20 |

The card has two `PillButton`s (compact):

| Button | Style |
|---|---|
| **Report this problem** | Primary, GitHub logo, an **A** glyph |
| **Dismiss** | Close icon, a **B** glyph |

With the report service the card has a third `PillButton` in its own row under those two, **Save the log to Downloads instead**: compact, not primary, the
download icon, no button glyph. It reuses the pill of Dismiss; no new visual was designed for it. A build without the report service has no such row and
shows the old note.

### 3.2 Behavior

- The card is not a dialog: no scrim, no blur of the screen, no confirmation.
- It shows only while no layer, portal, launch loader or stream result is up and the shell is on Start or Home
  (`ShellState.offerShown`). The result screen of a failed stream comes first and the card follows it.

| Input | Effect |
|---|---|
| Initial focus | On Report this problem (silently, like a layer). |
| A | Activates the focused button. |
| Left / right | Moves between the two buttons of the first row. Down reaches **Save the log to Downloads instead**. |
| B | Dismiss from anywhere while the card shows (a back handler registered after the layers'). |
| D-pad | Can leave the card for the dashboard. |
| Touch | Tap any button. |

- Dismiss returns the focus to the screen.
- Motion: `popEnter` / `popExit` from the top right corner (`Motion.POP_IN` with the overshooting spring,
  `Motion.EXIT` out; reduced motion: fades).
- Report uploads the cleaned log and opens the prefilled GitHub issue in the browser 0.9 s later with the link of the log in it; a toast says so
  (`Log uploaded; its link is in the draft. It is kept for 30 days.`). Without the report service, after a failed upload, or for **Save the log to
  Downloads instead**, it saves the log to Downloads and shows a toast (`Log saved to Downloads as ... Attach it to the issue.`, or with the reason of
  a failed upload). The rules are in [DIAGNOSTICS.md](../DIAGNOSTICS.md).
- The card on the second panel of a dual-screen device (`CompanionFlowPages.kt`) has Report and Dismiss and the same note; it has no Downloads row.

### 3.3 Diagnostics rows

**Debug info → Diagnostics** is the first section after the device rows (`DebugInfoScreen`):

| Row | Notes |
|---|---|
| *Diagnostic log* | An info row with the size and line count of what Share sends. Its panel previews the scrubbed text. |
| *Report a problem* | Only with the report service. An action row with the report icon; its help is the note of the card. Starts the same report as the card, without a detected problem. |
| *Save the log to Downloads instead* | Only with the report service. An action row with the download icon; saves the log and opens the draft, uploads nothing. |
| *Share diagnostic log* | |
| *Copy diagnostic summary* | |
| *Clear diagnostic logs* | |
| *Trigger test crash* | In debug builds only. |

The on/off switch of the diagnostic logs is in [Settings](#settings).

## 4. Color roles

Color is a seasoning, not a coating. The neutral text and surfaces carry the screen. The palette's three colors split
the accents about **60 / 25 / 15** by visual weight.

### 4.1 The three colors

| Color | Role | Share | Delos palette | Material You |
|---|---|---|---|---|
| `colors.accent` | Main | ~60 % | Gold 40° | Main |
| `accent2` | Secondary | ~25 % | Cyan 190° | +52° |
| `accent3` | Accent | ~15 % | Pink 335° | Tertiary |

- Material You derives the same three from the wallpaper and follows the same roles.
- Light themes use darker shades; `accent3` is dark there.

The derived colors are all in `ui/Colors.kt`:

| Color | Use |
|---|---|
| `onAccent3` | Content on an accent3 fill: dark in the dark theme, white in the light one. |
| `accent3OnDark` | A cover badge. |
| `accent3Line` | The faint hairline of the palette's third color. |

### 4.2 Palettes

The eight palettes are `Palette` in `settings/AppearanceStore.kt`: Delos, Cinder, Peony, Nebula, Twilight, Cobalt,
Glacier, Seafoam. They follow common rules:

1. The background is dark and muted: its own hue and a saturation of 7-38 %, a darker neutral for Glacier and
   Twilight. It is never the main color.
2. The main color carries about 60 %, the secondary 25 % and the third 15 % of the accents.
3. The schemes are mixed (warm with cool, a pink or gold spark), not monochrome.
4. There is only one green (Seafoam).
5. Three palettes are *soft* (Twilight, Glacier, Seafoam): pastel accents (lower saturation, higher lightness)
   instead of the vivid ones.
6. A stored choice of a removed palette falls back to Delos.

### 4.3 Where each color is used

Text stays neutral: labels of buttons and hints, titles, kickers and values are white or the gray text colors in
every palette. Color goes into shapes and icons.

| Color | Used for |
|---|---|
| `accent` (main, ~60 %) | The focus ring and its glow, filled focus, on states (switch and slider fill, tiles that are on), primary buttons, the selected tab, the gold of the active label in the stream menu. |
| `accent2` (secondary, ~25 %) | Icons of rows in the settings and the stream menu, arrows, the second half of every gradient (switch, slider, progress), the check badge of a selected circle, spinners. |
| `accent3` (accent, ~15 %) | The cherry on top, small and never text: the dot under the current page in the top bar, the slider halo of the stream menu, the `Running` and cover badge icons, the ring ripple of the portal overlay and the end dot of the launch ring. |

The animated backgrounds follow the same weights: the main color about 60 % of the lights, the secondary one a
quarter, the accent the rest and fainter.

## Settings

| Setting | Path | Type | Identifier |
|---|---|---|---|
| Keep diagnostic logs | **Settings → About → Diagnostics → Keep diagnostic logs** | On/off switch, native row | `about_diag_logs` |

## Code map

| Path or class | Role |
|---|---|
| `shell/ui/Motion.kt` (`Motion`) | The numbers, easings and pure arithmetic of the motion language. |
| `shell/ui/MotionKit.kt` | The motion building blocks. |
| `StreamMenuMotion` | What is particular to the dock: its entrance clock, the page morph. |
| `ui/ToastHost.kt` | The one composable of the toast. |
| `ui/ToastLogic.kt` | The pure logic of the toast (`toastKindFor`, `ToastTiming`). |
| `DelosToast` | Routes a message to the shell's own state or to a decor overlay; holds the only system-toast fallback. |
| `DelosToastOverlay` | The non-interactive decor overlay in the stream and external-display controller windows; `coverView` is the second host of a window that blurs or covers the stream. |
| `ToastStage` | The shared slots, start time and clock that both hosts draw. |
| `ToastCovers` | Decides that only the newest covering window's host is active. |
| `ToastPreview` in `src/debug` | The debug preview of the toast. |
| `ui/ProblemOfferCard.kt` | The problem report card. |
| `ShellState.offerShown` | The condition under which the problem report card shows. |
| `DebugInfoScreen` | Holds the Diagnostics section of Debug info. |
| `ui/Colors.kt` | The accent colors and their derived colors. |
| `settings/AppearanceStore.kt` (`Palette`) | The eight palettes. |

## Verification

| Test | What it enforces |
|---|---|
| `MotionTest` | Pins the numbers, easings and pure arithmetic of `Motion` and the stream menu's values. |

The debug preview `ToastPreview` draws the toast; see [Preview](#210-preview).

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [GUI.md](../GUI.md)
- [STREAM_MENU.md](STREAM_MENU.md)
- [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md)
- [DIAGNOSTICS.md](../DIAGNOSTICS.md)
