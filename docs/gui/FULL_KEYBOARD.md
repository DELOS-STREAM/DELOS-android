# Full keyboard

## Purpose

The full keyboard is the on-screen keyboard that Delos draws over the stream so that the user can type into the PC by
touch or with a controller. This document specifies its design, keys, sizes, touch and controller behavior, motion,
visibility rules, settings and the debug preview.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The **Flat console** design of the keyboard: bar, keys, legends, popup, hint strip | Color roles and the shared motion values: [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| Key rows, labels, Shift and Caps Lock | The stream menu and its Keyboard tile: [STREAM_MENU.md](STREAM_MENU.md) |
| Size, fit and the keyboard settings | The settings screens: [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md), [../SETTINGS.md](../SETTINGS.md) |
| Touch input and controller input while the keyboard is up | The stage and screen shapes: [SCREEN_SHAPES.md](SCREEN_SHAPES.md) |
| Motion of the keyboard, visibility rules | The external display's control screen: [../EXTERNAL_DISPLAY.md](../EXTERNAL_DISPLAY.md) |
| The debug preview of the keyboard | The index of the GUI documents: [../GUI.md](../GUI.md) |

## 1. Design and placement

1. The keyboard is the on-screen keyboard over the stream. It has the **Flat console** design: a solid bar docked to
   the bottom edge, flat keys, a hint strip under them.
2. It opens from three places:

   | Entry point |
   |---|
   | Stream menu → the Keyboard tile |
   | A four-finger tap |
   | The button on the external display's control screen |

3. It is one Compose layer in `shell/keyboard` (see [Code map](#code-map)) inside a `ComposeView`.
4. `KeyBoardLayoutController` adds the `ComposeView` to the stream's layout, bottom-docked and as wide as the layout.
   `KeyBoardLayoutController` is the class that `DelosStreamActivity` and `ExternalDisplayControlActivity` use, through
   `show`, `hide`, `toggleVisibility`, `refreshLayout`, `isKeyboardVisible` and `shown`.
5. The keyboard never takes the focus.
6. Where nothing of the keyboard is drawn, nothing is touchable: a finger on the game area goes to the stream even
   while a key is held.

## 2. Keys

### Key rows

The keyboard has 80 keys in six rows, each key with a weight. `FullKeyboardKeysTest` pins the keys, rows and weights.

| Row | Keys |
|---|---|
| 1 | Esc, F1–F12, Ins, Del |
| 2 | ~\` 1–0 _- += Back |
| 3 | Tab QWERTY… {[ }] \|\\ |
| 4 | Caps ASDF… :; "' Enter |
| 5 | Shift ZXCV… <, >. ?/ ↑ Hide |
| 6 | Ctrl Win Alt Space Home End PgUp PgDn ← ↓ → |

### Labels

The keys show what they type (`keyLabel`, US layout).

| Keys | Label |
|---|---|
| Letters | Lower case. Upper case while Shift is held (touch or latched, also X on the controller) or Caps Lock is on. Lower case again when both apply. |
| Number row | `!@#$%^&*()` while Shift is down. |
| Punctuation keys | The two characters are stacked: the other one small above, the typed one below. Shift swaps them. |
| Space | A thin bar. |
| Arrows | Drawn strokes. |
| Home, End, PgUp, PgDn | A smaller legend (.8). |
| Hide | The Phosphor close icon (second accent, filled on focus) over "HIDE". |

1. The labels crossfade (280 ms, no layout change).
2. The labels update at once when Shift latches, unlatches or the keyboard hides.
3. The keyboard tracks Caps Lock itself: it toggles on every press of the Caps key, because the host's real state is
   not readable. The Caps Lock state survives hiding.
4. The stream's QWERTY setting only changes the codes sent, not the labels.
5. The popup shows the typed character too.
6. The legend is Figtree SemiBold at .38 of the key height.

### Colors

Colors come from the theme. The color roles are in [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md).

| Theme | Element | Color |
|---|---|---|
| Delos palette | Bar | The prototype's `#131c31`→`#0d1424` |
| Delos palette | Keys | `#1f2b46` |
| Delos palette | Function and modifier keys | `#151e35` |
| Delos palette | Hide key | Plum-tinted |
| Light theme | Bar | A light bar |

## 3. Look

### Geometry

| Element | Value |
|---|---|
| Key corner | 11 |
| Gap between keys | 6 |
| Additional gap around Esc, Ins and Space | 12 more |
| Bar padding above the rows | 22 |
| Bar padding below the rows | 78 |

### Key states

State never relies on color alone.

| State | Appearance |
|---|---|
| Focused | The key turns white with a dark legend. The gold ring (4 px, 3 px dark gap) surrounds it. |
| Pressed | The second accent (cyan) with a dark legend. The key squeezes to .97. |
| Sticky | Solid gold with a bar under the legend. |

### Key popup

| Property | Value |
|---|---|
| Shape | White bubble |
| Height | 1.15 keys tall |
| Position | 10 above the key |
| Content | The character typed |
| Shown for | Character keys only |
| Stays after the release | 75 ms |

### Hint strip

| Property | Value |
|---|---|
| Text | **A Press, B Close, L3 Shift, X Delete, Y Space** |
| Glyph circles | 36 px |
| `L3` | A two-letter glyph in the same circle |
| Label size | 22 |
| Right end | "ON-SCREEN KEYBOARD" |

1. "ON-SCREEN KEYBOARD" is left out when the keys are narrower than 1100 px, so the strip stays one line.
2. The strip only shows while a controller drives the keyboard.
3. Touching hides the strip, the ring and the focused key's white. The next D-pad press brings them back.

### Shade

The stream darkens a little behind the bar: the prototype's `#shade`, 0 to .38 from half the height down.

## 4. Size

Sizes in this section are in stage px. The design is laid out on the 1080-high stage like the rest of the UI
([SCREEN_SHAPES.md](SCREEN_SHAPES.md)).

### Default fit

| Element | Value |
|---|---|
| Width of the keys | The width less 24 each side, at most 1840 |
| Key height | 3.65 % of that width, 44 to 68 tall |

| Screen | Result |
|---|---|
| 16:9 | A bar of about half the screen (532 of 1080) |
| 21:9 | A centered keyboard of the same width |
| 4:3 | Smaller keys instead of a taller keyboard |

### Custom size

The custom size applies when the fit is turned off. The keys and their effect are in [Settings](#settings).

| Element | Value |
|---|---|
| Width | The width of the keys. 1000 = full width, never wider than the default. |
| Height | The height of the whole bar. The key height follows from it (24 to 130). |
| Alignment | The keys sit left, center or right inside the bar. The bar stays full width. |

## 5. Touch

1. Every key reads its own finger, so Shift + a letter, WASD and chords work.
2. Down sends the key down. Up or cancel sends it up.
3. A finger that wanders off its key still holds the key until it lifts.
4. With sticky modifiers on, a modifier held 300 ms latches and its release is not sent. Touching a latched modifier
   releases it.
5. Hide acts on release.
6. Keys go to the host through `KeyBoardLayoutController.sendKeyEvent` → `ActiveKeyboardInput`, with source 0.
7. Haptics fire on down, release and latch.
8. A physical keyboard keeps typing into the PC. The full keyboard never sees it.

## 6. Controller navigation

The controller behavior is designed handheld first. While the keyboard is up, the controller operates the keyboard and
nothing of the controller reaches the game.

### Buttons

| Input | Action |
|---|---|
| D-pad, left stick | Move the focus. |
| **A** | Presses the focused key. The key is held for as long as A is held. A modifier latches and unlatches. |
| **B**, Back | Closes the keyboard. |
| **L3** (the left stick click) | Toggles Shift as a latch. |
| **X** | Types **Backspace**. |
| **Y** | Types **Space**. |
| The controller's other buttons | Swallowed. |

1. A modifier pressed with A latches whatever the sticky setting says, because a controller cannot hold one key while
   pressing another.
2. While Shift is latched by L3, Shift is down on the host and the labels are shifted. L3 again releases it. The stick
   click never moves the focus.
3. X and Y send down on press and up on release.
4. X and Y go through the same send path as a touch (haptics, popup).
5. X and Y flash the Back and Space keys even when the layout hides them.
6. Closing the keyboard lets go of both X and Y.
7. The stream menu's open/close buttons keep working: they are handled first.
8. Rumble keeps running.

### Repeat timing

| Input | Delay | Interval | Source |
|---|---|---|---|
| X and Y while held | 350 ms | 100 ms | `PAD_REPEAT_DELAY_MS` / `PAD_REPEAT_INTERVAL_MS` |
| Hat direction until the hat is back at the center | 350 ms | 100 ms | `DpadTracker` |
| `DPAD_*` key events | Not specified here | Not specified here | The system repeats them |

The keyboard repeats X and Y itself and ignores the system's repeats.

### Focus moves

Moves are row-aware like the prototype (`KeyGrid`, `FullKeyboardInputTest`).

| Move | Rule |
|---|---|
| Left, right | Stay in the row and stop at its ends. The ring nudges 7 px against the edge. |
| Up, down | Go to the key of the next row under the *column* of the last horizontal move, else the nearest. |
| First focus | Q. |
| Later focus | The last key. |

### D-pad shapes

The D-pad works in both shapes a pad reports it.

| Shape | Source | Notes |
|---|---|---|
| Key events | `DPAD_*` | The system repeats them. |
| Hat axes | `AXIS_HAT_X` / `AXIS_HAT_Y` of the joystick motion event | Most handhelds (the Odin) send this shape. It never reaches the keys. |

`DpadTracker` (pure, tested) turns the hat into moves:

1. A newly pressed direction moves at once.
2. A timer repeats the last direction with the timing in [Repeat timing](#repeat-timing).
3. A device that sends both the hat and the key moves once per press.

### Button codes

A, B, X, Y and L3 are the raw Android codes. They are also recognized when a handheld's built-in pad reports them with
a plain keyboard source.

| Button | Code | Value |
|---|---|---|
| A | `BUTTON_A` | 96 |
| B | `BUTTON_B` | 97 |
| X | `BUTTON_X` | 99 |
| Y | `BUTTON_Y` | 100 |
| L3 | `BUTTON_THUMBL` | 106 |

### No stuck input

1. `ControllerHandler.setInputSuspended` lets go of everything the physical controllers hold (a neutral packet each)
   when the keyboard opens and again when it closes.
2. `ControllerHandler.setInputSuspended` sends nothing in between, also from the USB driver path.
3. Keys the keyboard itself holds are released when it is hidden.
4. Every latched modifier (Shift, Ctrl, Alt, Win) is released when the keyboard is hidden: each gets its key up and
   the latch is cleared (`ModifierLatch.releaseAll`).
5. Items 3 and 4 apply whichever way the keyboard closes (B, Hide, the menu toggle, PiP), so nothing stays down on the
   PC.

### Interception

1. The interception is `DelosStreamActivity.handleKeyDown/handleKeyUp/handleMotionEvent` (`visibleFullKeyboard()`),
   for controller sources only.
2. On the external display the same hooks work, because `ExternalDisplayControlActivity` already routes the
   controller's events to `DelosStreamActivity`.
3. The keyboard on the external display is the activity's own `KeyBoardLayoutController`.

## 7. Motion

All values come from `Motion.kt` / `MotionKit.kt`, like the stream menu ([STREAM_MENU.md](STREAM_MENU.md)). The shared
motion rules are in [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md).

| Motion | Value |
|---|---|
| Rows rise | 18 px with a fade-in, 420 ms each (ease-out) |
| First row delay | 120 ms |
| Stagger between rows | 46 ms apart |
| Bar's glass fade-in | 336 ms |
| Leave (everything) | 240 ms (ease-in, no stagger) |
| Gliding ring move between keys | 560 ms with the slight overshoot (`GlideRingHost`) |
| Ring follows the keys exactly while they are still rising | 900 ms |
| Ring appears after opening | 560 ms |
| Press squeeze | To .97 in 120 ms |
| Press hold | 130 ms (a tap is always seen) |
| Colors and opacities | 280 ms |
| Popup settle | The spring in 120 ms |
| Ring bump at the keyboard's edge | 7 px and returns (the choices' chevron nudge) |

1. Focus is not a scale on the keyboard. One gliding ring moves between the keys.
2. The ring is drawn over the keys (`inFront`), because their neighbors lie closer than the ring is wide.
3. The ring squeezes with the key under it.
4. Reduced motion: only fades.

## 8. Visibility

1. The visibility state is kept across rotation and PiP (`hide(true)` / `show()`).
2. `DelosStreamActivity.isFullKeyboardShown()` feeds the stream menu's Keyboard tile.
3. The Keyboard tile is the Delos keyboard. Android's system keyboard is the More row **Open Android keyboard**.
4. A rotation only re-reads the settings (`refreshLayout`). It does not rebuild the keyboard.

## 9. Full keyboard preview

The preview exists in debug builds. It draws the real keyboard on the sunset scene and needs no stream or profile.
`FullKeyboardPreview` is in `src/debug`, with a stub in `src/release`.

```
adb shell am start -n io.github.meitoncz.delos.debug/com.limelight.shell.ShellActivity --es preview_screen full_keyboard \
  [--ei preview_opacity 0..100] [--es preview_align left|center|right] \
  [--ez preview_custom_size true --ei preview_height <dp> --ei preview_width <dp>]
```

| Intent extra | Type | Value |
|---|---|---|
| `preview_screen` | `--es` | `full_keyboard` |
| `preview_opacity` | `--ei` | `0..100` |
| `preview_align` | `--es` | `left|center|right` |
| `preview_custom_size` | `--ez` | `true` |
| `preview_height` | `--ei` | `<dp>` |
| `preview_width` | `--ei` | `<dp>` |

1. Touch, D-pad, left stick, A, B and X work as in a stream.
2. The keys that would go to the PC are listed at the top left.
3. After B or the Hide key, a tap on the scene opens the keyboard again.

## Settings

| Label | Key | Unit | Effect |
|---|---|---|---|
| **Virtual Full Keyboard Disable Autofit** | `onscreen_keyboard_autofit` | - | Turns the fit off. The width and height settings then apply. |
| **Width** | `seekbar_onscreen_keyboard_width` | dp | The width of the keys. 1000 = full width, never wider than the default. |
| **Height** | `seekbar_onscreen_keyboard_height` | dp | The height of the whole bar, from which the key height follows (24 to 130). |
| **Align Mode** | `list_onscreen_keyboard_align_mode` | - | Puts the keys left, center or right inside the bar, which stays full width. |
| **Opacity** | `seekbar_keyboard_axi_opacity` | - | Dims the bar, keys and ring together. It does not dim the hints or the popup. |
| **Sticky modifier keys** | Not named here | - | Applies to the full keyboard: see [Touch](#5-touch). |
| Vibration | `checkbox_vibrate_keyboard` | - | Applies to the full keyboard: see [Touch](#5-touch). |

## Code map

| Path or class | Role |
|---|---|
| `shell/keyboard` | The package of the keyboard's Compose layer. |
| `FullKeyboard` | The Compose layer of the keyboard. |
| `FullKeyboardState` | The state of the keyboard. |
| `FullKeyboardKeys` | The key table. |
| `FullKeyboardInput` | The pure logic. |
| `keyLabel` | What a key shows (US layout). |
| `KeyGrid` | The row-aware focus moves. |
| `DpadTracker` | Turns the hat axes into moves (pure, tested). |
| `ModifierLatch.releaseAll` | Releases every latched modifier and clears the latch. |
| `PAD_REPEAT_DELAY_MS`, `PAD_REPEAT_INTERVAL_MS` | The repeat delay and interval of X and Y. |
| `KeyBoardLayoutController` | Adds the `ComposeView` to the stream's layout. The class `DelosStreamActivity` and `ExternalDisplayControlActivity` use (`show`, `hide`, `toggleVisibility`, `refreshLayout`, `isKeyboardVisible`, `shown`). |
| `KeyBoardLayoutController.sendKeyEvent` | Sends the keys to the host through `ActiveKeyboardInput`. |
| `ActiveKeyboardInput` | The path of the keys to the host. |
| `ControllerHandler.setInputSuspended` | Lets go of everything the physical controllers hold and sends nothing while the keyboard is up. |
| `DelosStreamActivity.handleKeyDown/handleKeyUp/handleMotionEvent` | The interception of controller events (`visibleFullKeyboard()`). |
| `DelosStreamActivity.isFullKeyboardShown()` | Feeds the stream menu's Keyboard tile. |
| `ExternalDisplayControlActivity` | Routes the controller's events to `DelosStreamActivity`. Owns the `KeyBoardLayoutController` of the external display. |
| `Motion.kt`, `MotionKit.kt` | The motion values. |
| `GlideRingHost` | The gliding focus ring. |
| `FullKeyboardPreview` | The debug preview in `src/debug`, a stub in `src/release`. |

## Verification

| Test | What it enforces |
|---|---|
| `FullKeyboardKeysTest` | Pins the 80 keys, six rows and weights. |
| `FullKeyboardInputTest` | The row-aware focus moves of `KeyGrid`. |

`DpadTracker` is pure and tested.

The debug preview that draws the keyboard is in [Full keyboard preview](#9-full-keyboard-preview).

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [STREAM_MENU.md](STREAM_MENU.md): the Keyboard tile and the More row **Open Android keyboard**.
- [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md): motion values and color roles.
- [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md): the choices' chevron nudge.
- [SCREEN_SHAPES.md](SCREEN_SHAPES.md): the stage.
- [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) and [../SETTINGS.md](../SETTINGS.md): the settings screens.
- [../EXTERNAL_DISPLAY.md](../EXTERNAL_DISPLAY.md): the external display's control screen.
- [../GUI.md](../GUI.md): the index of the GUI documents.
