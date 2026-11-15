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
| **Right menu button (Start)**, at the top right of the handheld | Opens the **context menu** for the focused item. |

The **two menu buttons at the top left and top right** of the handheld are used in the UI.

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

### Button hints

The hints show the buttons as they look on the device.

| Device | Left menu button | Right menu button |
|---|---|---|
| AYN handhelds (Odin, Thor) | A square | A play arrow |

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
| `ImeShiftTest` | The arithmetic of the keyboard-avoidance shift (`ImeShift`). |
| `MarqueeMotionTest` | `MarqueeMotion.bounceOffset`, the motion of a row that goes to its end and back. |
| `StreamMenuLayoutTest` | Fails for a stream menu caption text that does not fit the three-line box at every interface scale. |

On a device, `adb logcat -s DelosIme` shows the keyboard-avoidance values of a debug build (see
[Debug log](#debug-log)).

### Device checks (date not recorded)

- The Odin's SwiftKey reports 111 px while it covers 58% of the screen, but the flag follows the keyboard.

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [GUI.md](../GUI.md)
- [START_AND_HOME.md](START_AND_HOME.md)
- [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md)
- [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md)
- [STREAM_MENU.md](STREAM_MENU.md)
- [FULL_KEYBOARD.md](FULL_KEYBOARD.md)
- [LOCALIZATION.md](../LOCALIZATION.md)
