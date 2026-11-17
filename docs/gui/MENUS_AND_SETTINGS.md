# Menus and settings rows

## Purpose

Menus and settings lists are the surfaces where the user chooses actions and changes values with the D-pad, the stick
or touch. This document specifies the shared list focus plate, the context menu, list containment and scrolling, the
settings rows with their impact chips, the settings tabs, the top menu pill, the cover badges of the home row, the
per-app settings screen layout and the link guidance rows.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Layout of menus and settings, list focus motion | Design principles and the index of GUI documents: [GUI.md](../GUI.md) |
| Context menu: card, caret, placement, header, rows, submenus | Home row, covers and the start screen: [START_AND_HOME.md](START_AND_HOME.md) |
| List containment, settings list scroll, settings focus order | Motion durations, toasts and color roles: [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| Settings row focus and impact chips | Stream menu and its popover: [STREAM_MENU.md](STREAM_MENU.md) |
| Settings tabs and the tab pill, top menu pill | Per-app settings details, focus order and reset: [PER_APP_SETTINGS.md](../PER_APP_SETTINGS.md) |
| Custom-settings badge and hidden badge on covers | Setting keys, values and impact levels: [SETTINGS.md](../SETTINGS.md) |
| Per-app settings screen layout | PyroWave in-stream hint and Link block: [STREAM_QUALITY.md](../STREAM_QUALITY.md#pyrowave-link-guidance-18) |
| Link guidance rows in Settings | Debug previews of all screens and screen shapes: [SCREEN_SHAPES.md](SCREEN_SHAPES.md) |
| Side menu by touch, on-screen controls opacity row | |

## 1. Layout

- Menus and settings follow a PS5-like layout.
- The main settings screen also shows the main categories as icon tabs that can be switched
  ([Settings tabs](#9-settings-tabs)).

## 2. List focus motion

Ordinary lists share one moving plate, not independently fading focus layers. The API is `RowGlideHost` /
`rowGlideTarget`, or `focusEffects(glide = true)`.

| Property | Value | Constant |
|---|---|---|
| Travel time of the plate | `520 ms` | `Motion.ROW_FOCUS` |

The shared plate applies to these lists:

- settings rows and per-app/debug rows
- side-menu quick settings
- context menus (including home Options and device/profile/artwork/metadata choices)
- settings-search results
- filter chips
- first-run avatar choices
- stream-popover rows and key/command pills

Rules:

1. Each host owns its local coordinates, scroll viewport, clipping and palette.
2. Fill, ring, corner, scale and optional shadow decoration match that surface.
3. The plate tracks current target bounds plus a decaying carry on both axes, so scrolling and unequal item sizes do
   not restart its travel.
4. Frame state is read during drawing. Shadow paths/paint are reused.
5. First focus and reduced motion place the plate directly on the target.

| Surface | Plate decoration |
|---|---|
| Context menus | Accent fill (17 %) with a 3 px ring and a soft glow; red on destructive rows |
| Filters | Only a ring |
| Stream rows | Their inset ring/shadow, no outer glow |

These keep their own, separate motion:

- cover/artwork-grid motion
- circle-menu glide
- dock ring
- top/settings pills
- keyboard key focus
- toast motion

Isolated form actions and small Save/Cancel groups retain their own treatment.

## 3. Context menu

The context menu is implemented in `ui/ContextMenuLayer.kt`; placement and submenu logic are in
`ui/ContextMenuPlacement.kt`. It is variant A of the approved prototype (issue #78); `docs/prototype/delos-gui.html`
shows it.

### 3.1 Glass card and caret

The menu is an anchored glass card with the prototype's caret. The card is the prototype's `.cm-glass`.

| Element | Value |
|---|---|
| Card corner | 34 |
| Blur of the screen under the card | 46 (Haze `colorFilter` + `tint`) |
| Saturation of the screen under the card | 1.6 |
| Tint | rgba(34, 44, 84, .50) |
| White sheen from the top | 13 % fading out at 52 % |
| Rim | 1 px at 11 % white |
| Lit top edge | 1.5 px at 26 % white |
| Shadow | `0 40 90 -20` black 66 % |
| Card width | 470..640, as wide as its longest row |
| Caret | The shared caret ([3.1.1](#311-the-caret)): 60 wide, 24 high; the tip's nominal distance for placement is 30 (`CARET_DEPTH`, the room around the card) |
| Scrim under the menu | Light, 26 %, no blur |
| Covers beside the card | Dimmed: saturate .6, brightness .5 |
| Info block above the covers | Dimmed to 40 % |
| Focused cover | Keeps its colors |

#### 3.1.1 The caret

ONE caret for every glass card that points at something: the dashboard's context menu (covers, PC circles), the settings' option list and slider popup, and the stream menu's popover, on every side they use (bottom, top, left, right). It is one function, `cardCaretPath` in `ui/CardCaret.kt`, and the card's outline and the caret are ONE path. The numbers are the average, in whole stage pixels, of the two carets the app had before (the stream popover's: a straight flank, a tip 20 wide at 18.7 out, 23 high; the context menu's: a rounded base, a tip 12 wide at 13 out, 24 high). Apple copies them 1:1:

| Number | Value |
|---|---|
| Base width (on the card's edge) | 60: from -30 to +30 along the edge from the caret's centre |
| Height (the tip's apex out of the edge) | 24 |
| Flank | A cubic Bézier from the base end (u 30, v 0) with controls (21, 3) and (14, 8) to the tip's join (8, 16): rounded at the base (the flank leaves the edge at a slant, not at a corner), nearly straight after it |
| Tip | A quadratic Bézier between the joins (-8, 16) and (8, 16) with the control (0, 32): its apex is 24 out and the rounding is modest |
| Mirror | The same flank on the other side, mirrored (u to -u) |
| Corners of the card | Unchanged: 34 on the context menu, 44 on the stream popover and the settings popover (the caret keeps at least 72 from the card's corners) |

(u, v): u along the edge from the caret's centre, v out of the edge. The path runs clockwise round the card: the caret is inserted into the edge it is on, the top and right edges in the direction of u, the bottom and left edges against it (the start is the base end that comes first). Scale every number with the stage. `CardCaretTest` pins the points on each of the four sides.

### 3.2 Focus look of the owning element

The element a menu belongs to keeps its focus look (ring, scale, glow, pulse, plate) while its menu is open, although
the keyboard focus is in the menu.

1. `focusEffects` draws `focused || held`. `onLookChange` tells callers that draw their own colors.
2. `FocusTracker.hold()` is called when the menu opens.
3. The hold ends when the element has the focus again (no gap, no second animation).
4. `FocusTracker.release()` is the safety net 150 ms after the menu closed.
5. `onFocusChange`, the focus sounds and the hero / info follow the real focus only.

### 3.3 Anchor and placement

The anchor is the element's bounds as they are lit.

| Fact | Value | Constant |
|---|---|---|
| Growth of the focused cover, included in the anchor | 5.5 % | |
| Growth direction of a centered cover | All sides | |
| Growth direction of the wide first cover | Only to the left, so its right edge stays | |
| Focus ring around the cover | 10 stage pixels | |
| Caret tip position | 5 stage pixels inside the cover's edge | `TIP_GAP = -5` |
| Gap between covers | 44 stage pixels | `ROW_GAP` |
| Width of the wide cover | Three covers plus two gaps | |

Half of the 30 px caret is over the cover with its ring, for both cover kinds.

| Case | Position of the card | Caret |
|---|---|---|
| Default | Beside the element, to its right, vertically centered on it | On the card's left |
| The card would leave the stage | Flipped to the element's left, vertically centered on it | |
| The menu says so (`ContextMenu.below`: the PC circles, the start screen's profile chip) | Below the element | On top |
| `ContextMenu.below` and the card does not fit below | Above the element | On the bottom |

| Margin | Value |
|---|---|
| Card from the stage's top and bottom (the stream popover's margin) | 120 px |
| Largest card height that results; the card scrolls inside | 840 |
| Card from the stage's sides | 60 px |
| Caret from the card's corners | 72 px |

`placeContextMenu` computes the placement and is unit tested. The opening point of the pop is the caret's tip.

### 3.4 Header

The header shows a rounded tile, a small status line, the title and one line of facts. Other menus pass no
`MenuHeader` and show just the title.

| Element | Value |
|---|---|
| Tile | 52 x 78, rounded, corner 14 |
| Tile color | The game's main color: the first of the cover's `GameColors` |
| Tile color rendering | hsl(hue, saturation x 80 %, 52 %) at 85 % with a faint white sheen from the top |
| Tile glyph | Filled controller glyph: near black on a light color, white on a dark one |
| Status line | Small; a green dot while the game runs |
| Title | The game's name |
| Line of facts | Last played, another game running, Custom settings, Hidden |

| Condition | Tile color |
|---|---|
| The game has artwork and game colors are on | The game's main color |
| The game has no artwork | The placeholder art's hue |
| The game colors are off or the cover is gray | The theme's accent |
| The color is not computed yet | `HeaderTint.Pending`: the neutral chip of the submenu's back button (white at 10 %) |

Color availability:

1. Every `GameTile` hands its cover to `HeroBitmaps.analyzeCover` as soon as it is decoded, off the main thread, once
   per image (`CoverColorCache`, in memory). The color is there from the menu's first frame.
2. In the short time before it is, `HeaderTint.Pending` shows the neutral chip.
3. The neutral chip fades to the color once over `Motion.FADE`. It never shows the accent first.

### 3.5 Rows

| Element | Value |
|---|---|
| Row height | 64 px |
| Row content | The icon, the label and, for a submenu, a chevron |
| Button glyph on a row | None; hints stay in the hint strip: A Select, B Close / Back |
| Destructive rows (`MenuItem.danger`) | Quit game, Quit X and start, Remove from Delos |
| Destructive row color | Red |
| Plate while a destructive row is focused | The shared plate (`RowGlideHost`, 520 ms) turns red |

Choosing a destructive row is the confirmation. There are no dialogs.

The items and their order are whatever the caller builds: home game menu, PC and device menus, profiles, pair, artwork
and metadata lists, a setting's reset.

### 3.6 Submenus

Submenus are `MenuItem.children`; the path is in `ShellState.menuPath`.

1. A submenu swaps inside the same card.
2. The rows stagger in again.
3. The card's height glides 520 ms.
4. The plate starts on the first row.
5. B goes back one level. Focus returns to the row that opened the submenu.
6. On touch the header's back chip goes back one level.

### 3.7 Scrolling, focus and containment

| Mechanism | Behavior |
|---|---|
| `InitialFocus` | Focus starts on the first row |
| `focusTrap` | Focus stays inside the menu |
| `rememberFocusedListNavigation` | A held D-pad stops at the first / last row |
| `rememberMarginBringIntoView` with `FocusScroller` / `ListScrollDriver` | Margin scroll, 600 / 950 ms ([Settings list scroll](#5-settings-list-scroll)) |
| Fades at the card's list edges | 34 px, while the list scrolls |
| Closing the menu | Focus goes back on the element |
| Reduced motion | Fades only |

## 4. List containment

1. Eager ordered lists reuse `FocusedListNavigation` so held navigation cannot outrun layout or escape through an
   uncomposed neighbor.
2. Closed menus, side-menu lists and search results stop even on fresh input at their first/last row.
3. Regular settings retain their deliberate fresh-Up exit ([Settings focus order](#6-settings-focus-order)).
4. A horizontal host can use the same ordered guard on Left/Right.
5. Filter and key/command chip rows never wrap. Each is one row (`ScrollRow`) that scrolls sideways with an edge fade
   when its chips do not fit.
6. The chip rows of the filter modal keep spatial two-dimensional navigation and their outer containment. They are
   not flattened into a linear order.
7. Context menus bound long lists to the available height and use the same animated margin-scroll driver
   (600/950 ms).
8. Search results eagerly compose their bounded catalog (at most 110 rows).

## 5. Settings list scroll

`MarginBringIntoView` / `FocusScroller` are in `ui/Stage.kt`; `ListScrollMotion` and `ListScrollDriver` are in
`ui/Motion.kt`. Settings, per-app settings, artwork, debug info, filter, stream-menu popover and `Layers` lists share
them.

1. Moving focus scrolls only when the row would leave the safe margin.
2. Foundation 1.12 ignores a custom `BringIntoViewSpec.scrollAnimationSpec`. The spec therefore measures and returns
   zero framework distance while `FocusScroller` drives the scroll.
3. The scroll is a critically damped spring.
4. Retargeting adds distance to what remains and preserves velocity.
5. Fast nearby retargets brake to avoid overshoot.
6. A drag or list endpoint stops the scroll.
7. Reduced motion scrolls immediately.

| Scroll distance | Time to settle to 2% |
|---|---|
| Row-sized step (90 px or less) | 600 ms |
| From 700 px | 950 ms (the time grows between the two) |

## 6. Settings focus order

The order is implemented in `FocusedListNavigation.kt` / `FocusedListPolicy.kt`.

1. Global settings, per-app settings and debug info use eager `Column.verticalScroll` lists, not viewport-limited
   lazy focus targets.
2. All neighbors remain composed and measurable while visual scrolling catches up.
3. Native Up/Down events follow keyed row order instead of spatial search through animated rows.
4. Holding Up reaches the first row and stops.
5. Only a fresh Up press already on the first row may leave toward the top menu.
6. Down stops at the last row.
7. Explicit neighboring focus requesters and a group exit guard also prevent semantic focus moves escaping
   vertically.
8. Dynamic debug rows retain stable requesters. Collapsing/removing the focused row restores its nearest surviving
   predecessor.
9. Tab selection, L1/R1 and row Left/Right adjustments are not affected by the focus order.

| Row count of the largest global tab | Value |
|---|---|
| Visible rows | 41 |
| With all hidden phone rows restored | 71 |

## 7. Settings row focus

`SettingRowList` / `SettingRow` are in `ui/Rows.kt`. The rows use the shared plate of
[List focus motion](#2-list-focus-motion).

| Plate element | Value |
|---|---|
| Fill | Palette `surface2`, translucent, without Haze blur |
| Accent ring | 3 px, no gap, corner 24, scale 1.02 |
| Glow | 20 / 50 / −24 |

1. Hosted rows draw no local glass, ring or glow.
2. Side-menu settings use this same host, not separate frosted focus layers.
3. The host's size and `fadeEdges` apply to the complete plate.

| Transition | Duration |
|---|---|
| Row scale, icon and arrow/value colors | 520 ms |
| Press squeeze | 120 ms |
| Adjustments and value changes | Immediate |
| Entry fade | 120 ms |
| Gap before the exit fade (prevents blinking between rows) | 70 ms |
| Exit fade | 280 ms |

### 7.1 Touch

A tap is not the A button on a settings row ([#92](https://github.com/Meitoncz/Delos/issues/92)). Keys and the gamepad are unchanged.

| Tap on | Result |
|---|---|
| A row without the focus | Focuses it, so its help shows; nothing changes |
| The focused toggle, or its switch on any row | Flips it |
| A choice's ◀ or ▶ arrow on any row | One option back or forward |
| The focused choice row | Its options as a list at the row ([7.2](#72-options-list)); a pick sets it |
| The focused slider row | Its popup ([7.3](#73-slider-popup)); the track in the row itself still takes taps and drags |
| An action or text row | Opens it on the first tap |

1. A disabled row also focuses on the first tap, so its help can say why; the next tap plays the error cue.
2. Every choice row has a list: a row built without its options (`RowValue.Choice.options`) steps as A. The popups need the shell's `LocalRowPicker`; where none is provided, a choice steps and a slider keeps its track.
3. Rows that have a list or a popup: every choice and slider of Settings (all tabs), the per-app settings, the side menu (Theme, Background), Debug info (the rumble laboratory's sliders) and the stream menu (Supersampling quality, Mode, Touchscreen mode, the sliders of Brightness, Bitrate, Upscaler and More). In the stream menu only choice and slider rows wait for the second tap; its toggles and actions act on the first.

### 7.2 Options list

The list is the **contextual popover**: the stream menu's popover card (glass, one outline with its caret; `cardCaretPath` in `ui/CardCaret.kt`, `paintPopoverGlass` in `StreamMenuPopover.kt`) grown out of the tapped row, on both platforms (the iPad has the same). It is not a context menu: it has no header.

| Element | Value |
|---|---|
| Scrim | None, like the stream menu's popover: nothing is dimmed; a tap beside the card closes it |
| Card | 560 wide (the slider's is 1040), corner 44 (the stream popover's), padding 20 above and below; at least 24 from the stage's sides |
| Glass | In the shell the screen behind is really blurred: the context menu's glass (`menuGlass`: blur 46 under the navy tint at 80 %, with its white sheen), the card clipped to its outline with the caret (`cardCaretPath`); the shell's blur source (`hazeSource`) is on while a popup is open. The stream menu has no blur source (the stream is a surface), so there the card is the stream popover's translucent glass (`paintPopoverGlass`). Both: the popover's shadow, no outline |
| Place | Vertically centred on the tapped row and kept 104 below the stage's top (the status bar, and the cutout) and 24 above its bottom edge (a compact stage uses the context menu's margins). The card grows out of its caret TO THE RIGHT: the caret is on its left edge pointing left into the row, its tip 360 from the row's right edge (left of the value's text), moved left as far as the card needs the room to end 24 before the stage's right edge (at most to 120 inside the row's left edge). Only when the right side has no room it flips: the caret on the right edge, its tip 120 inside the row's right edge, the card to the left. When neither side has the room the card is narrowed, last |
| Caret | The shared caret ([3.1.1](#311-the-caret)) at the row's middle (never on a rounded corner), its tip in the row |
| Title | The row's name, Figtree 20 semibold, third text color, 36 high |
| Option | 64 high, 4 apart, Figtree 23 semibold, corner 28; the focused one has a 17 % tint of the main color and a 3 px ring; the current one has a check (30) |
| Long list | The card is cut to the room and the list scrolls inside it; the current option is in view when it opens |
| Motion | The stream menu popover's: it grows from the caret's tip (`popEnter`, 560 ms with the overshooting spring, a fade in 336) and shrinks back into the row in 240 ms; reduced motion: a fade |
| Controller | The focus stays in the card, on the current option; up and down move, A picks, B closes |

A pick sets the value and closes the card; the focus returns to the row.

### 7.3 Slider popup

The same card and place as [7.2](#72-options-list) (contextual, caret into the row), 1040 wide and 300 high, made for a finger: the row's name, the value, and below it a ◀ button, the slider and a ▶ button. Every change applies at once (a drag when the finger lifts), and the row behind shows it.

| Element | Value |
|---|---|
| Value | A pill 88 high with the number (Figtree 52) and its unit (32; "40 Mbps"; the slider's off text at its lowest value), a pencil on its right. A tap turns it into a field |
| Typing | The on-screen keyboard (decimal where the slider has a divisor). The number is in the readable unit (Mbit/s for the bitrate); a point or a comma. Only digits and one separator can be typed |
| Applying | Done applies it. The number is rounded to the slider's grid and clamped to its range (typing 400 on a slider that ends at 150 applies 150); text that is no number turns the pill red with the error cue and stays open. A tap beside the card applies a valid number and closes; B leaves the field first and drops the text |
| Buttons | Round, 96 (at least 72, 56 dp), 24 from the track. A press steps once; a hold repeats after 380 ms every 55 ms with growing steps (1, 5, 10, then up to a fortieth of the range, as a held key on the row). A button is dimmed at its end |
| Slider | 96 tall (touch height), track 14, knob 30; a tap or a drag sets the value on the slider's grid when the finger lifts |
| Keyboard | The card moves up above the keyboard (`moveAboveIme`) |
| Controller | Left and right step (a held key accelerates), A closes, B closes |

The arithmetic (`SliderEdit`, `HoldRepeat`, `PickerLayout`) is pure and unit tested.

## 8. Impact chips

Impact chips (issue #40) are implemented in `ui/ImpactChips.kt` and passed as `SettingRow(impact = …)`. The design is
the approved prototype variant B with the chips on their own line; the reason for that line is under
[Device checks](#device-checks-date-not-recorded).

The cost-bearing settings rows carry three tiny chips: video resolution, frame rate, bitrate, codec, HDR, PyroWave
chroma, frame pacing, upscaler on/off, mode, sharpness, supersampling.

### 8.1 Row layout

The chips are on their own line above the title, left-aligned to the title's x. The title keeps the full width.

| Left | Middle, top to bottom | Right |
|---|---|---|
| icon | chips / title / secondary line | value |

The secondary line is the description, or the CUSTOM badge and Profile value on the per-app screen.

### 8.2 Chip

A chip is a glyph and a 3-step bar indicator.

| Element | Value |
|---|---|
| Glyph size | 25 tpx |
| Glyphs | GPU `memory`, Latency timer, Network wifi |
| Bar width | 6 px |
| Bar heights | 9 / 15 / 21 px |
| Distance between chips | 26 px |

| Level | Bars filled | Color |
|---|---|---|
| None | The chip is dimmed to 40 % | |
| Low | One | The palette's secondary color |
| Medium | Two | The main color |
| High | Three | The warm red (`LowRed` of the battery) |

The levels come from the pure `impact(key, values)` (`settings/SettingsImpact.kt`) and follow the current values
live; see [SETTINGS.md](../SETTINGS.md) (docs/SETTINGS.md).

### 8.3 Row height

| Row | Height | Constant |
|---|---|---|
| Plain row | 92 px | |
| Every row with chips | 120 px reserved | `IMPACT_ROW_MIN_HEIGHT` in `ui/Rows.kt` |

| Line of a row with chips | Size |
|---|---|
| Label | 26 tpx |
| Second line | 19 tpx |
| Chips line | About 28 px |

1. The three lines do not fit in a plain row; this is why every row with chips reserves 120 px.
2. The 120 px apply also when the row has no second line, so all cost-bearing rows of a list are one height.
3. Larger text scales grow the row as they do any row.
4. Rows without chips keep the plain height.
5. Scrolling and the gliding plate measure rows, so they need no special case.

### 8.4 Legend

While a row with chips is focused, the description panel shows a legend under the scrolling help, outside the scroll
area. The legend holds the three glyphs with their names and "Cost of the current value: 1 bar low, 2 medium, 3 high".

## 9. Settings tabs

The tabs of settings, per-app settings and artwork are `TabPill` in `screens/SettingsScreen.kt`.

### 9.1 Tabs are touch only

1. The tabs are never focusable. The D-pad and the stick pass over them.
2. Up from the first row does not reach the tab row.
3. Left/Right inside rows is not affected.
4. The tabs have no focus ring.
5. A tap selects a tab (select cue, a short squeeze).
6. L1 / R1 step through the tabs. The L1 / R1 labels stand beside the row.
7. The selected state is shown only by the pill.

### 9.2 Tab pill

The accent pill behind the selected tab is `SelectionPillHost` in `ui/SelectionPill.kt`.

| Element | Value |
|---|---|
| Pill color | `colors.accent`, the main color of the focus ring and filled focus |
| Selected tab's icon (filled) and label | Dark, `colors.onAccent` |
| Icon and label when another tab is selected | Fade to `colors.text2` |
| Glide to the newly selected tab | The geometry spring (`GEO_STIFFNESS`/`GEO_DAMPING`) |
| Leading edge | 1.5× stiffer |
| Trailing edge | 0.75× |
| Color cross-fade | Over `FADE` |

1. The different edge stiffness makes the pill stretch and squash with the gap and follow the new tab's width.
2. The pill wraps across the bar.
3. The pill starts in place on first composition.
4. The pill jumps with reduced motion.

## 10. Top menu pill

The top menu is the capsule of round Settings / Profile / Devices buttons in the status bar, `TopNav` in
`ui/Bars.kt`.

### 10.1 Buttons and focus

1. The buttons are focusable, in the same order, with the same scale (1.12), glow and accent fill.
2. The focus is cyclic like the stream menu's tile bar: Right on the last button goes to the first, Left on the first
   goes to the last.
3. The wrap is set through `focusProperties` on the first and last button.
4. With one button there is no wrap.
5. Up and Down are not affected.

### 10.2 Transient pill

A transient accent pill glides from the old button to the new one only while the focus moves from one of these
buttons to another, wrap-around included. It is `TransientPillHost` / `TransientPill` in `ui/SelectionPill.kt` and
shares `PillGeometry` and the edge springs with the tab pill.

| Element | Value | Constant |
|---|---|---|
| Input that triggers it | D-pad, stick or touch | |
| Detection of a move | A gain within 100 ms of a loss | `TransientPillMotion` |
| Start shape | The old button as it looks focused: the circle scaled by 1.12 | |
| Leading edge | 1.5× stiffer | |
| Trailing edge | 0.75× | |
| End shape | The new button's focused look, never smaller than it | |
| Handover condition | Both edges within 2 px and the button's own focus scale settled | `FOCUS` after the gain |
| Handover fade | 140 ms | `HANDOVER_MS` |
| Icon colors | Cross-fade | `FADE` |

1. The pill is one shape all the way: it starts as the old button, stretches between the two and arrives at the new
   button.
2. At the handover the button's own accent fill fades in over the pill with the same bounds and color, so there is no
   pop. The pill is then removed.
3. The button's glow and scale are untouched, so nothing pulses twice.
4. Nothing extra is drawn at rest, when the focus enters the menu from the game row or leaves it, or with reduced
   motion.

## 11. Custom-settings badge

The badges are drawn by `GameTile` in `HomeScreen.kt`.

### 11.1 Custom-settings badge

An app shows the badge when its current profile stores at least one real per-app setting: a key of
`GameSettingsStore.KEYS`. This is the same notion as the "N custom settings" chip. Private codec bitrate slots alone
do not count.

| Element | Value |
|---|---|
| Shape | A small round icon |
| Position | The cover's top-right corner, 16 px from the corner |
| Glyph | The filled `Symbols.TUNE` glyph, 30 tpx, `accent3OnDark` |
| Disc | The dark translucent disc of the artwork busy spinner: 56 px, `#8C050A14` |
| Layer | Above the idle-filtered art |
| Tiles | The wide first tile and the normal covers alike, scaling with the tile |
| TalkBack | "Custom settings for this app" |

### 11.2 Hidden badge

A hidden app is only in the row while the side menu's "Show hidden apps" is on. It shows the same icon-only pill.

| Element | Value |
|---|---|
| Glyph | The filled `Symbols.VISIBILITY_OFF` glyph in white at 85 % |
| Accent | None: it is a state, not a highlight |
| TalkBack | "Hidden app" |

### 11.3 Shared rules

1. When both badges apply, the two pills stand side by side in the top-right corner, the hidden one to the left with
   an 8 px gap.
2. While the artwork busy spinner shows, it takes the corner and the icons wait.
3. The top-left Running / Last played badge is unaffected.
4. `HomeScreen` computes the set of custom app ids once per library, profile or stored-settings change
   (`rememberCustomGames`) and passes a Boolean to each tile.
5. In `rememberCustomGames` a `SharedPreferences` change listener on the profile's settings bumps a version, and
   `GameSettingsStore.customAppIds` reads the map once.

## 12. Per-app settings

The screen is `GameSettingsScreen.kt`, the "hero banner" design. Details, focus order and reset are in
[PER_APP_SETTINGS.md](../PER_APP_SETTINGS.md). The screen shows no cover.

### 12.1 Hero banner

The hero banner is `GameBanner`; its geometry is in `GameSettingsLayout`.

| Element | Value |
|---|---|
| Height | 290 stage px |
| Artwork | The game's artwork like the app's hero: hero, else cover, else the PC's art |
| Artwork without any art | The placeholder art |
| Rendering | `HeroBitmaps.render` with the Appearance hero blur |
| Bitmap | The same cached bitmap as the dashboard background, its stage-sized top |
| Scrim | From the left and from the top, for legibility |
| Fade into the page | `fadeMask`, opaque to 35 % |

### 12.2 Vertical layout

| Element | Position or size |
|---|---|
| Persistent status bar: the DELOS logo at the left, Wi-Fi / battery / clock at the right | y 0..104 |
| Header block, on the banner | y 106..198 |
| Stream / Display `SelectionPillHost` tabs | y 206..272, exactly where the global settings' tabs are |
| Shared `SettingRowList` (gliding plate, `FocusScroller`, entrance rise) | Starts at y 296 |
| Top fade of the list | 14 px |
| Padding inside the list | 30 px |
| First row | Begins at y 326 like the global list's, so its ring and scale are fully visible |
| List width | 1080 |
| Rows | From x 150 as the title |
| List height | 688 px |
| Bottom fade of the list | 70 px |
| Rows that fit with the 120 px rows of the impact chips | About five (688 px list height minus the 70 px bottom fade) |
| Description panel | Starts at y 312 like the global one |

### 12.3 Header block

| Element | Value |
|---|---|
| Eyebrow | `APP SETTINGS · <PC>` |
| App's name | Unbounded 46, 1.35 line height so descenders are never cut |
| Shared `Chip` | "N custom settings" / "Follows your profile" |
| `PillButton` | Reset all |

### 12.4 Tabs and description panel

- Each tab shows the number of custom settings of that tab as a quiet accent3 count (`TabPill(badge)`).
- The description panel on the right is the global one with a source box.

| `SettingsPanelFrame` element | Value |
|---|---|
| Width | 520 |
| Padding | 44 |
| Radius | 34 |
| Icon | 120 px |

### 12.5 Custom rows and reset

A custom row carries the override marker of `SettingRow` (`RowOverride`): accent3 tint with a faint ring, a CUSTOM
badge and "Profile: <value>".

| Element | Value |
|---|---|
| Reset of one row | X resets it directly |
| Indicator on the focused row | A compact pill "Reset" + X glyph that fades/pops in |
| Layout width of the pill | None: it is drawn over the free space left of the value |
| Long wording "Reset to profile value" | In the context menu, the hint bar and the accessibility label |
| Touch | A 52 px round button on every custom row |
| Reduced motion | Fade only |

1. The title column never changes width and the title stays on one line.
2. Reset and Reset all toast with Undo (Y or a tap) and never ask.

## 13. Link guidance

Link guidance applies to every codec. PyroWave also gets the in-stream hint and Link block:
[STREAM_QUALITY.md](../STREAM_QUALITY.md#pyrowave-link-guidance-18). The rows are in
`shell/screens/LinkGuidanceRows.kt` and use no new visual language.

### 13.1 Rows

| Row | Text | Behavior |
|---|---|---|
| Bitrate row hint on the Stream tab | `Recommended: 225 Mbps · link carries up to 665 Mbps` | States the reason: not tested, link carries, the PC's link, drops frames |
| Test link | `Tested yesterday on Wi-Fi`, `Not tested on Wi-Fi yet` or why the PC cannot test | A starts the test |
| Use recommended bitrate | | Fills the bitrate on A, with a toast; disabled while the bitrate already equals it |

1. For an ordinary codec the rows appear when the PC can test or a result exists.
2. Test link and Use recommended bitrate are two ordinary action rows that follow the bitrate row with the same plate.
3. The per-app screen shows the same hint on its bitrate row and in its help, without Test link.

### 13.2 Link test

The test uses the portal (`Portal`).

| Element | Value |
|---|---|
| Cancel | Cancellable like a PC wake-up: B stops it, the hint bar says Cancel; on touch a tap shows a Cancel button that does the same |
| Touch | The portal takes every touch while it is up, so nothing underneath reacts |
| Orb | With the network-check icon |
| Title | "Testing the link to <PC>" |
| Line | `Testing 250 Mbps · stay on Wi-Fi · B cancels` |
| Result | The ordinary toast: success, or the reason in one sentence |
| Focus after the test | Returns to the row that started it |

The test is only reachable from Settings, never over a stream, and never starts by itself.

## 14. Side menu by touch

The side menu (Select) also opens and closes by a drag. `SideMenuSwipe` (pure) decides; `sideMenuSwipe` in `SideMenuGesture.kt` watches the shell's touches before its children do.

| Rule | Value |
|---|---|
| Where an opening drag starts | The left 40 dp of the window (`EDGE_DP`) |
| When it opens | On screens with the side menu (`Screen.sideMenu`), with no layer, popup, launch, portal, result or button capture; never over a stream (a separate window) |
| Claimed as the menu's | Past the touch slop, more sideways than vertical, to the right (opening) or to the left (closing); a vertical scroll and a tap stay with the screen |
| Closing drag | A drag to the left anywhere while the side menu is open and no popup is over it |
| Panel during the drag | Follows the finger: progress = travel / panel width (680 stage px); past fully open it is damped and stops at 6 % of the width (`RUBBER`); the panel reaches 6 % past the left edge, so no gap shows |
| Scrim and blur during the drag | Their opacity is the progress, instead of their fade |
| Release | Opens past 40 % of the width (`THRESHOLD`); from open, closes past 40 % to the left; a release at 600 dp/s or faster (`FLING_DP_PER_S`) goes the way it moves |
| Settle | The geometry spring (`Motion.GEO_DAMPING`, `GEO_STIFFNESS`) from the release speed; the open and back cues play when the menu changes state |
| Reduced motion | The panel does not follow; a drag of 40 % of the width toggles the menu with its normal fade |
| Keys during a drag | Select does nothing until the menu has settled |

Gesture navigation: Android reserves both side edges for Back. While the drag can start, the shell excludes a band of the left edge from the system gesture (`Modifier.systemGestureExclusion`, which sets `View.setSystemGestureExclusionRects`): 40 dp wide and at most 200 dp high (Android honours at most 200 dp per edge), centred vertically (`SideMenuSwipe.exclusion`). Above and below the band, an edge swipe is Back; a drag that starts just right of the system's gesture strip still opens the menu. With 3-button navigation the whole edge works.

## 15. On-screen controls opacity

The opacity of the on-screen controls (`seekbar_osc_opacity`, `PreferenceConfiguration.oscOpacity`, default 90 %) is a native slider row: Settings → Controls, section "On-screen Controls Settings", 0 to 100 % in steps of 5, only on a device with a touchscreen. It writes the current profile's value through `OscOpacity`; the other on-screen gamepad settings stay hidden (`PhoneOnlySettings`). A tap on the focused row opens the slider popup ([7.3](#73-slider-popup)). The stream menu's More page has the same slider, applied live ([STREAM_MENU.md](STREAM_MENU.md#27-popover)).

## Code map

| Path or class | Role |
|---|---|
| `RowGlideHost` / `rowGlideTarget`, `focusEffects(glide = true)` | Shared moving focus plate of ordinary lists |
| `Motion.ROW_FOCUS` | Travel time of the plate |
| `ui/ContextMenuLayer.kt` | Context menu |
| `ui/ContextMenuPlacement.kt` | Context menu placement (`placeContextMenu`, `TIP_GAP`) and submenu logic |
| `ContextMenu.below`, `MenuItem.danger`, `MenuItem.children`, `MenuHeader` | Menu model: placement below, destructive rows, submenus, header |
| `ShellState.menuPath` | Path of the open submenu |
| `FocusTracker.hold()` / `FocusTracker.release()` | Focus look of the element that owns an open menu |
| `HeroBitmaps.analyzeCover`, `CoverColorCache`, `HeaderTint.Pending` | Main color of the context menu header tile |
| `FocusedListNavigation.kt` / `FocusedListPolicy.kt` | Ordered focus navigation and containment of eager lists |
| `ScrollRow` | One-row chip rows that scroll sideways |
| `ui/Stage.kt` | `MarginBringIntoView` / `FocusScroller` |
| `ui/Motion.kt` | `ListScrollMotion` and `ListScrollDriver` |
| `ui/Rows.kt` | `SettingRowList` / `SettingRow`, `RowOverride`, `IMPACT_ROW_MIN_HEIGHT`, `rowTouch` (what a tap does on a row) |
| `ui/RowPicker.kt` | `RowPickerLayer`: the options list and the slider popup of a row ([7.2](#72-options-list), [7.3](#73-slider-popup)); `RowPopup`, `LocalRowPicker`, `rowPopupFor` |
| `ui/CardCaret.kt` | `CardCaret` (the one caret's numbers and points), `cardCaretPath`, `caretCurvePath` ([3.1.1](#311-the-caret)) |
| `ui/RowPickerLogic.kt` | Pure: `SliderEdit` (step, snap, read, parse typed text), `HoldRepeat`, `PickerLayout` (placement of the card and of its caret) |
| `ShellState.picker` | The open popup of a row (over every layer; the side menu's rows open one too) |
| `ui/ImpactChips.kt` | Impact chips |
| `settings/SettingsImpact.kt` | Pure `impact(key, values)` |
| `screens/SettingsScreen.kt` | `TabPill` |
| `ui/SelectionPill.kt` | `SelectionPillHost`, `TransientPillHost` / `TransientPill`, `PillGeometry`, `TransientPillMotion`, `HANDOVER_MS` |
| `ui/Bars.kt` | `TopNav`, the top menu |
| `HomeScreen.kt` | `GameTile` badges, `rememberCustomGames`, `ROW_GAP` |
| `GameSettingsStore.KEYS`, `GameSettingsStore.customAppIds` | Per-app setting keys and the set of custom app ids |
| `GameSettingsScreen.kt` | Per-app settings screen: `GameBanner`, `GameSettingsLayout`, `SettingsPanelFrame` |
| `shell/screens/LinkGuidanceRows.kt` | Link guidance rows |
| `Portal` | Portal that shows the link test |
| `SideMenuSwipe.kt` | Pure: the side menu drag's start band, gesture exclusion band, claim, progress with rubber band, release decision ([14](#14-side-menu-by-touch)) |
| `SideMenuGesture.kt` | `SideMenuDrag` (the progress while a drag owns the menu), `Modifier.sideMenuSwipe` |
| `settings/OscOpacity.kt` | The on-screen controls' opacity value of the profile ([15](#15-on-screen-controls-opacity)) |

## Verification

| Test | What it enforces |
|---|---|
| `CardCaretTest` | The caret's numbers and its points on every side ([3.1.1](#311-the-caret)) |
| Unit test of `placeContextMenu` | Context menu placement: side, flip, below/above, margins and caret distance ([Anchor and placement](#33-anchor-and-placement)) |
| `RowTouchTest` | What a tap does on every kind of row, and which rows have a popup ([7.1](#71-touch)) |
| `RowPickerLogicTest` | Slider stepping, snapping, typed input (units, rounding, clamping, rejection), the hold's acceleration, and the placement of the card ([7.2](#72-options-list), [7.3](#73-slider-popup)) |
| `SideMenuSwipeTest` | The side menu drag: start band, exclusion band, claim, progress and rubber band, release by distance and fling, reduced motion ([14](#14-side-menu-by-touch)) |
| `OscOpacityTest` | The opacity row's value: default, range, the value the stream reads ([15](#15-on-screen-controls-opacity)) |

Debug previews:

| Command | What it draws |
|---|---|
| `--es preview_screen settings_list [--ez preview_auto true]` | 40 real rows with the shared focus tracker, eager focus order, scroll driver and plate |
| `--es preview_screen settings_tabs [--ez preview_auto true]` | The settings tabs and the tab pill; L1 / R1 and taps only |
| `--es preview_screen top_menu [--ez preview_auto true]` | The top menu pill |
| `--es preview_screen game_settings [--ez preview_touch true] [--ez preview_auto true]` | The per-app settings screen with fixed data |

Automatic playback of `settings_list` is finite:

| Step | Count | Interval |
|---|---|---|
| Slow downward steps | 12 | 650 ms |
| Fast steps | Three | 150 ms |
| Up key-downs with increasing native repeat counts | 40 | 50 ms |
| Key-up | One | |

The playback ends with an assertion that focus ended on the first row. It never forces focus back to the first row
after the hold.

Automatic playback of `top_menu` has eight steps, 1.2 s apart: right, right, right (wraps), left (wraps), left, left,
right, right. Then it rests.

### Device checks (date not recorded)

- Impact chips: on the Odin, chips beside the name covered it. The chips therefore stand on their own line above the
  title ([Row layout](#81-row-layout)).
- Settings row focus: the shared plate of [Settings row focus](#7-settings-row-focus) is the accepted one.

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

Touch on settings rows ([7.1](#71-touch) to [7.3](#73-slider-popup)): the unit tests cover the rules and the arithmetic, not the look. Not verified on a device: the position and look of the contextual card (caret, motion) against the iPad's, the size of the slider popup's controls under a finger, the on-screen keyboard lifting the slider popup (also in the stream menu's window), a hold on the ◀ and ▶ buttons, and the popups in the stream menu.

Side menu by touch ([14](#14-side-menu-by-touch)): `SideMenuSwipeTest` covers the decisions only. Not verified on a device: the panel following the finger, the rubber band, the settle spring, the scrim during a drag, and the exclusion band with gesture navigation.

## Related

- [GUI.md](../GUI.md)
- [START_AND_HOME.md](START_AND_HOME.md)
- [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md)
- [STREAM_MENU.md](STREAM_MENU.md)
- [SCREEN_SHAPES.md](SCREEN_SHAPES.md)
- [PER_APP_SETTINGS.md](../PER_APP_SETTINGS.md)
- [SETTINGS.md](../SETTINGS.md)
- [STREAM_QUALITY.md](../STREAM_QUALITY.md#pyrowave-link-guidance-18)
- Issue #40: impact chips
- Issue #78: context menu prototype
