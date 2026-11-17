# Controls and text

## Purpose

This document specifies how the Delos shell is operated with a controller, where and how text is typed, how the
field being edited stays clear of Android's keyboard, and how a one-line label that is longer than its room is shown.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Primary input, the two menu buttons, the navigation rule | Side menu, settings search and its letter pad: [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) |
| Text input: the dialog, `TextFieldPill`, when Android's keyboard opens | The stream's own keyboards (Full keyboard, More → Open Android keyboard): [FULL_KEYBOARD.md](FULL_KEYBOARD.md), [STREAM_MENU.md](STREAM_MENU.md) |
| Keyboard avoidance (`ImeAvoidance`) | Profile pages and the home info row: [START_AND_HOME.md](START_AND_HOME.md) |
| Text that does not fit (`MarqueeText`) | Toasts, the `Motion.GEO_*` spring and reduced motion: [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| | Context menu, stream menu and its caption: [STREAM_MENU.md](STREAM_MENU.md) |
| | Rule for translators: [LOCALIZATION.md](../LOCALIZATION.md) |
| | Index and design principles: [GUI.md](../GUI.md) |

## 1. Controls

### Buttons

| Input | Action |
|---|---|
| **D-pad or left stick + A, B, X, Y** | Primary input. |
| **Left menu button (View/Select)**, at the top left of the handheld | Slides the **side menu** in from the left. |
| **Swipe in from the left edge** (touch) | Opens the side menu, which follows the finger; a swipe to the left closes it ([MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md#14-side-menu-by-touch)). |
| **Right menu button (Start)**, at the top right of the handheld | Opens the **context menu** for the focused item. |

The **two menu buttons at the top left and top right** of the handheld are used in the UI.

During a stream ([STREAM_MENU.md](STREAM_MENU.md#22-ownership-and-entry-points)):

| Input | Action |
|---|---|
| **Tap of Guide/Home or Back** | Opens the **stream menu**; nothing is sent to the PC. |
| **Hold of Guide/Home or Back** (500 ms) | Sends **Guide** to the PC while held (Steam overlay, Game Bar). |
| **Own stream menu buttons** (Settings → Controls → Stream menu, none by default) | Pressed together, open or close the stream menu. L3 + R3 is no longer the default. |

### Context menu button

1. Only elements with something to offer have a context menu. There is no generic fallback menu.
2. The **Options** hint shows only while the focused element has a menu.
3. Where A already opens the same menu, Options does nothing.

| Element | Options (right menu button) |
|---|---|
| Game tiles | Opens the context menu. |
| Unpaired PCs on the pairing screen | Opens the context menu. |
| PC circles on the start screen | Opens the context menu. |
| Customized rows in the app settings | Opens the context menu. |
| The profile chip | Does nothing: A already opens the same menu. |
| PC circles on Devices | Does nothing: A already opens the same menu. |

On touch a **long press** on an element that has a context menu focuses it and opens the same menu, anchored like Start does (game tiles, PC circles, unpaired PCs, customized rows). A short tap is unchanged. Nothing opens over another layer, the launch, the portal or a row popup (`longPressOpensContext`, `focusEffects` in `ui/Focus.kt`). The stream menu has none.

### Button hints

The hints show the buttons as they look on the device.

| Device | Left menu button | Right menu button |
|---|---|---|
| AYN handhelds (Odin, Thor) | A square | A play arrow |

Every hint (hint bars, the stream menu's dock, dialogs, settings, the artwork screen, the companion window) draws the
glyphs of the connected controller's family (`ControllerGlyphs`, `shell/ui/ControllerGlyphs.kt`). The family comes
from the controller used last (the pad of the last button press or stick push), else the first gamepad connected, and is
evaluated again on connect, disconnect and change (`InputManager.InputDeviceListener`). It is detected from
`InputDevice`: vendor `0x054c` is PlayStation (DualShock 4 by product id or name, else DualSense), `0x057e` Nintendo,
`0x045e` Xbox; for other vendors the device name decides (dualsense, dualshock, ds4, playstation, nintendo, joy-con,
pro controller, xbox); anything else is generic and draws the Xbox letters (LB/RB only on an Xbox pad).

| Place | Xbox, generic | PlayStation | Nintendo |
|---|---|---|---|
| South (confirm) | A | cross | B |
| East (back) | B | circle | A |
| West | X | square | Y |
| North | Y | triangle | X |
| Shoulders, triggers | LB RB LT RT (generic: L1 R1 L2 R2) | L1 R1 L2 R2 | L R ZL ZR |
| Left, right menu button | View, Menu icons | View, Menu icons | − and + |
| Touchpad | | rounded rectangle | |

Only the drawing changes, never the mapping: confirm stays the south button (`KEYCODE_BUTTON_A`, which Android puts
on the south button of a Nintendo pad too) and back the east one. `Glyph.A`, `Glyph.B`, ... name positions. The
PlayStation shapes and the touchpad are vector drawings (`Canvas`), there are no image assets. The Apple app uses the
same table.

### Input kind

The last input is the current input (issue [#103](https://github.com/Meitoncz/DELOS/issues/103)), the rules of DELOS for
Apple (its docs/ARCHITECTURE.md, "Input kind and the hints"). `ControllerGlyphs.kind` (`InputKind`: controller, keyboard,
pointer, touch) is what the hints and the input-dependent controls are drawn for.

- **What sets the kind** (`InputKindState`, the classification in `InputKinds`, `shell/ui/InputKind.kt`). The last
  deliberate input wins: a finger or a pen going down is touch, a mouse button, a mouse moving or scrolling (also the
  stream's captured pointer and a trackpad) is the pointer, a key press of a physical keyboard with letters is the keyboard,
  a button press of a pad (its d-pad and the d-pad keys Android makes of its stick) or its stick or hat pushed past half its
  travel (`StickPress`: held until it is back inside 30 %, so stick noise and wobble are no input) is the controller. Key
  releases, the volume and power keys, a handheld's own Back key, a TV remote, the navigation bar and the on-screen keyboard
  are none. A pad connecting or disconnecting is no input either: it only says whether a pad is there and which family it is.
  The shell's window (`ShellActivity.dispatchKeyEvent`, `dispatchTouchEvent`, `dispatchGenericMotionEvent`) and the stream's
  (the same three in `DelosStreamActivity`, and the captured pointer) feed it before anything else sees the event.
- **No input yet, or the controller left** (`InputKind.resolve`): a connected gamepad means controller, else the last kind
  that was not the controller, else the device's default (touch with a touchscreen, else the controller). So the Odin starts
  with the controller, a tap switches to touch, a button press back; unplugging a pad brings back the touch or keyboard
  hints at once.
- **Hints by kind** (`Glyph.forKind`, `List<Hint>.adaptedTo`, `shell/ui/HintKinds.kt`; the screens only write controller
  hints): controller: unchanged; touch: only the primary action (A: Select, Start, Connect, Change), drawn with the hand-tap
  symbol on the cue disc (`Glyph.TAP`: the shared font has no touch caps); pointer: the same with the font's left-click cap
  (`Glyph.CLICK`, `Symbols.MAC_MOUSE_CLICK_LEFT`); keyboard: Return for the primary action, Esc for back, the left and right
  arrows for the adjust cue, the font's complete key caps (`Glyph.Cap`, `InputCap`: `MAC_RETURN`, `MAC_ESCAPE`,
  `MAC_ARROW_LEFT` + `MAC_ARROW_RIGHT`), drawn like the pad caps (`GlyphButton`, no disc); the other actions
  (tabs, menu buttons, X, Y) have a control on the screen or no key and are dropped. This applies to the hint bar, the
  companion window's copy of it and the stream menu's dock. A tappable hint (`Hint.tappable`: the library's Filter) stays for
  every kind: while no controller is the input the bar draws it as a glyphless glass pill with its label (44 high, 24 at the
  sides; it cross-fades into the hint with `Motion.ROW_IN`, reduced motion a fade), and a tap runs `HintBar(onTap)`, which
  opens the filters exactly like Y (`GlobalKeys.canFilter` / `onFilter`). The Artwork screen has its own Filter pill.
- **Pills a finger can press** keep working for every kind. The button glyph on them (`PillButton(glyph = ...)`, drawn by
  `PillGlyph`: the update and problem cards, the artwork search, filter and sheet pills) shows only for a kind that has that
  button (a key cap for Return and Esc) and gives way like `InputKindVisibility`, the pill narrowing with it. The toast's Y
  chip shows its Y only for a controller; the settings rows' reset pill drops its X for a keyboard, and the per-app settings
  show the round reset button on every custom row while a finger or a mouse is the input.
- **`InputKindVisibility`** (`shell/ui/InputKindVisibility.kt`) is the one component for chrome that depends on the kind
  (`InputKindRule.WITHOUT_CONTROLLER` / `WITH_CONTROLLER`, and `visible` for the screen's own condition): it fades with a
  small scale (`Motion.POP_FROM_SCALE`) and slide (`Motion.CHEVRON_BUMP` twice) over `Motion.ROW_IN` with `EaseOut` (in a row
  from its start edge, in a column or on its own rising from below), reduced motion a plain `Motion.FADE`; along `collapse` it
  takes its size and its `before` / `after` gaps only while shown, so the neighbours glide; it takes no touches and is hidden
  from accessibility while hidden, and is not composed once faded out. The launch scene's and the loader's Cancel pill and
  the portal's Cancel pill use it: shown for touch, pointer and keyboard, hidden while a controller is the input (B cancels).
  The status bar's back button uses it too (Apple's `BackButton`): a 68 px glass circle (surface tint, 1 px line border) with
  the back arrow at 34, 24 px before the brand, on every screen that can go back (`Screen.back`), in the regular and the
  compact bar (`StatusBarLayout.BACK_*`); the brand glides as it comes and goes. A tap runs the activity's back dispatch, so it
  does exactly what B does (the screen's own back handler first, the shell's with the BACK cue otherwise).

### Navigation

Navigation must be **intuitive and direct**. Pressing a direction takes the focus where the user wants to go, ideally
in **one press**. A sequence such as left, down, down to reach something is never required.

## 2. Text input

### Where text is typed

1. **Text is typed in one place: the dialog** (`DialogLayer`, a `DialogSpec` with `DialogField`s). This covers
   everything from the pairing PIN and the PC's name to the API keys and a profile's name.
2. The one exception is the settings search, which has its own letter pad next to the field.
3. Every text field in the shell is the one `TextFieldPill` (dialogs and the settings search). It is built on the
   `TextFieldState` flavor of `BasicTextField` with `showKeyboardOnFocus` off.
4. The `value: String` overload of `BasicTextField` and any other text widget are not used: they start the input
   method on every focus, which shows the keyboard.
5. A new text field needs nothing. New text entry is a new `DialogSpec`.
6. The stream's own keyboards (Full keyboard, More → Open Android keyboard) are separate and unchanged.

### Profile name

The profile pages (first run "Who's playing?", new and edit profile) do not have an input.

| Element | Behavior |
|---|---|
| Name | Shown in a labeled `ValuePill`. |
| **Change name** | Opens the very same dialog as Rename profile on the profile screen (`openProfileNameDialog`, title "Profile name"). A blank name gets the same toast. |
| D-pad order | Avatars, Change name, Create / Save. |
| Choosing an avatar | Moves the focus to Change name. |
| Accepting the dialog | Shows the new name and moves the focus on to Create / Save. |
| Create without a name | Opens the dialog. |

### When Android's keyboard opens

**Android's keyboard opens only when the field is activated.**

| Event | Keyboard |
|---|---|
| A tap on the field | Opens. |
| A / the D-pad center key on the field | Opens. |
| Moving the focus onto the field (D-pad, stick, or code such as the dialog's first field when it opens) | Never opens. |
| The focus leaves the field | Closes. |
| Back / B while the keyboard is open | Closes. The field keeps focus and the keyboard stays closed until the next tap or A. |

`ShellActivity` has `windowSoftInputMode="stateHidden|adjustResize"`:

- `stateHidden`: the keyboard does not come back by itself when the window regains focus.
- `adjustResize`: the keyboard's inset reaches the app on every API level. The shell is edge to edge, so the window
  itself is never resized.

## 3. Keyboard avoidance

**The keyboard never covers the field being edited: the editing field moves to the top quarter of the screen,
regardless of the keyboard.**

### Mechanism

1. There is one mechanism: `ImeAvoidance` in `shell/ui/ImeAvoidance.kt`. No screen has its own `imePadding`.
2. **Only what is marked moves.** `Modifier.moveAboveIme()` is on the dialog column in `DialogLayer`.
3. A text field outside such a container does not move anything. This is the settings search, which is already high.
4. The mechanism lives in `Shell` only, so the stream, the stream menu and `ExternalDisplayControlActivity` are
   untouched.
5. The arithmetic is `ImeShift` (`ImeShiftTest`).

| Part of the dialog screen | Moves |
|---|---|
| Card, shadow and blur (the dialog column) | Yes |
| The scrim, the status bar, the background and the bars | No, they stay put |

### Engagement and visibility

1. `TextFieldPill` is *engaged* from A / the D-pad center key or a tap until the focus leaves it.
2. While it is engaged **and the keyboard is visible**, its mover glides up into the band (see
   [Band rule and shift](#band-rule-and-shift)).
3. The glide uses the shared `Motion.GEO_*` spring. With reduced motion it is a snap.
4. A field already in the band does not move the mover.
5. The moment the keyboard is hidden (SwiftKey's chevron, Back) the mover glides back to its place and the field
   keeps the focus. A or a tap opens the keyboard again and the mover glides up again.
6. "Visible" is the system's flag (`rootWindowInsets.isVisible(ime())`) or any measured keyboard height from the
   sources in [Keyboard measurement](#keyboard-measurement). The flag is reliable where the height is not (see
   [Device checks](#device-checks-date-not-recorded)).

### Band rule and shift

| Constant | Value | Meaning |
|---|---|---|
| `EDIT_BAND_BOTTOM_FRACTION` | 0.25 of the window height (0.20 in a portrait window) | The field's bottom edge is at most this far from the top. |
| `TOP_MARGIN` | 48 stage px | What moves keeps this distance from the window's top edge. A dialog card never touches or crosses it. |
| `EDIT_FLOOR_FRACTION` | 0.40 of the window height | The field must end up at or above this in any case. |

1. The band rule never uses the keyboard height, because keyboards can be resized, floated, split or switched at any
   moment and may live in a transparent full-screen window.
2. The field wins over the `TOP_MARGIN` margin. The shift is
   `min(band, max(moverTop - TOP_MARGIN, fieldBottom - 0.40 * H))`.
3. A dialog taller than the free area lets its top leave the screen, because the field wins.
4. The buttons may end up behind the keyboard. The keyboard's Done calls the same `onDone`, and D-pad Down leaves the
   field and closes the keyboard.

Worked example on the Odin (1080 px), the Rename profile dialog:

| Quantity | Value |
|---|---|
| Card top | 324 |
| Field | 514..598 |
| Shift | 276 px |
| Card after the shift | 48 px below the edge |
| Field bottom after the shift | 322 |

### Keyboard measurement

The keyboard is still measured. The largest of these sources wins:

| Source | Condition |
|---|---|
| Compose's `WindowInsets.ime` | None |
| The root window insets | None |
| The visible display frame | When it lost more than 15% of the window |

1. The shift is the larger of the band's shift and the measured one, so a keyboard that is measured to cover more can
   only push the field higher.
2. The measured part is capped by the same margin, except for what keeps the field itself above the keyboard.
3. The dialog card is brought in with the field as far as the measured keyboard asks.
4. A layout listener and a 60 ms poll while a field has focus catch a keyboard that shows without an inset event.
5. Places are window pixels measured against the window as it is at that moment, with the mover's own offset left
   out. The shift is a layout offset, so the node before it keeps its place.
6. A window that `adjustResize` really shrank is therefore never shifted twice.
7. The stage scale only enters in the margin.

### Debug log

Debug builds log every change under the tag `DelosIme` (`adb logcat -s DelosIme`). A log line holds:

| Field |
|---|
| Inset and its visible flag |
| Compose |
| Visible frame |
| Keyboard |
| Engaged |
| Visible |
| Band |
| Window size and whether it was resized |
| The field's bounds and live top |
| The shift |
| `softInputMode` |

## 4. Text that does not fit

One rule applies to every one-line label that can be longer than its room (a long app name, a translation, a large
interface scale). The component is `MarqueeText` / `MarqueeContent` in `shell/ui/MarqueeText.kt`.

### Rules

1. **A text that fits is never touched.** Nothing fades and nothing moves.
2. **A text that does not fit stays on one line.** Its right end fades out instead of ending in an ellipsis, so the
   first letters are always intact.
3. **It scrolls only while it is active**, which normally means its row or item is focused.
4. The left end fades in only while text is hidden there.
5. A text loops around with a gap before its repeat.
6. A row of several items (the badges of the home info row) goes to its end and back instead (`bounce`,
   `MarqueeMotion.bounceOffset`, covered by `MarqueeMotionTest`).
7. **Reduced motion keeps it still**: the text shows its start with the faded end and never scrolls.
8. A new one-line label that can overflow uses `MarqueeText` with `active` bound to the focus of its row. It does not
   add its own truncation or scrolling.
9. For translators the rule is in [LOCALIZATION.md](../LOCALIZATION.md): prefer a shorter translation over relying on
   the scroll.

### Sizes and timings

| Element | Value |
|---|---|
| Fade of the right end | 36 stage px |
| Pause before the scroll starts | 700 ms |
| Scroll speed | 56 stage px per second |
| Fade of the left end | 28 px |
| Rest after a lap | 900 ms, then the scroll starts again |
| Gap before the repeat of a looping text | 56 px |

### Where each treatment applies

| Treatment | Where | Reason or rule |
|---|---|---|
| `MarqueeText` (**where it is used**) | The stream menu's title, popover headings and values | |
| | Context menu titles, their second line and their items | |
| | Choice and value texts of the settings rows and the row labels of lists | |
| | The app's name on the artwork page and in the per-app settings header | |
| | The badges and the streaming-time block of the home info row | |
| Ellipsis (**where an ellipsis is still used**) | Toasts (two lines, then an ellipsis) and eyebrows above titles | Text that can never be focused and so could never be scrolled or stopped by the user. |
| Neither (**where nothing may scroll or be cut at all**) | The stream menu's caption (the help under the tiles) | Its texts must fit the three-line box at every interface scale. `StreamMenuLayoutTest` fails for a text that does not, and the text is then shortened. |

## Code map

| Path or class | Role |
|---|---|
| `shell/ui/InputKind.kt` | `InputKind`, `InputKindState`, `StickPress`, `InputKinds`: what sets the input kind. |
| `ControllerGlyphs` | The glyph family and the input kind; fed by the windows' event dispatch. |
| `shell/ui/HintKinds.kt` | `Glyph.forKind`, `adaptedTo`: the hints for the input kind. |
| `shell/ui/InputKindVisibility.kt` | `InputKindVisibility`, `PillGlyph`: chrome shown by the input kind. |
| `DialogLayer` | The dialog, the one place where text is typed. Its dialog column carries `Modifier.moveAboveIme()`. |
| `DialogSpec`, `DialogField` | A dialog and its fields. New text entry is a new `DialogSpec`. |
| `TextFieldPill` | Every text field in the shell (dialogs and the settings search). |
| `ValuePill` | Shows the profile name, labeled, on the profile pages. |
| `openProfileNameDialog` | Opens the profile name dialog (Change name, Rename profile). |
| `ShellActivity` | Has `windowSoftInputMode="stateHidden\|adjustResize"`. |
| `shell/ui/ImeAvoidance.kt` | `ImeAvoidance`, the one mechanism that moves the editing field above the keyboard. |
| `ImeShift` | The arithmetic of the shift. |
| `shell/ui/MarqueeText.kt` | `MarqueeText` / `MarqueeContent`, the component for a one-line label that can overflow. |
| `MarqueeMotion.bounceOffset` | The motion of a row that goes to its end and back (`bounce`). |

## Verification

| Test | What it enforces |
|---|---|
| `InputKindTest` | The input kind after event sequences (touch, click, key, button, stick, connect, disconnect), as DELOS for Apple tests them. |
| `HintKindsTest` | The hints and pill glyphs for each input kind, and the visibility rules. |
| `ImeShiftTest` | The arithmetic of the keyboard-avoidance shift (`ImeShift`). |
| `MarqueeMotionTest` | `MarqueeMotion.bounceOffset`, the motion of a row that goes to its end and back. |
| `StreamMenuLayoutTest` | Fails for a stream menu caption text that does not fit the three-line box at every interface scale. |

On a device, `adb logcat -s DelosIme` shows the keyboard-avoidance values of a debug build (see
[Debug log](#debug-log)).

### Device checks (date not recorded)

- The Odin's SwiftKey reports 111 px while it covers 58% of the screen, but the flag follows the keyboard.

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

- Guide/Home and Back during a stream (tap = stream menu, hold of 500 ms = Guide on the PC): unit tested
  (`GuideTapHoldTest`), not yet checked in a stream on the Odin (the built-in Back, a controller's Guide, the Steam
  overlay or Game Bar opening on the PC).

## Related

- [GUI.md](../GUI.md)
- [START_AND_HOME.md](START_AND_HOME.md)
- [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md)
- [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md)
- [STREAM_MENU.md](STREAM_MENU.md)
- [FULL_KEYBOARD.md](FULL_KEYBOARD.md)
- [LOCALIZATION.md](../LOCALIZATION.md)
