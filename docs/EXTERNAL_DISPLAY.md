# External display support

## Purpose

In the external-display mode Delos streams to an attached external display and keeps the device's own screen for control.
This document specifies what the user sees and how controller and keyboard input reaches the stream and the
stream menu on the other display.

## Scope

Only a display that Delos classifies as **external** (a monitor, a TV or a wireless display) is a target of this mode. A second panel that is part of the device (a dual-screen handheld), an overlay display and a virtual display are never chosen; the classification, its signals and the manual override are in [DUAL_SCREEN.md](DUAL_SCREEN.md). The stream menu itself is specified in [GUI.md](GUI.md), "Stream menu".

## Behavior

- The Delos launch scene continues onto the external display without the legacy Moonlight loading UI or Android's side transition.
- The host request and stream surface keep the resolution and frame rate selected in Delos. The TV's native mode is the output canvas, not a replacement stream configuration.
- The Delos stream menu is shown on the external display. The built-in Odin controller supports the configured menu shortcut, D-pad, analog navigation, A/B activation and back, and left/right adjustment of choices and sliders. Its bar and popover keep 48 stage pixels from every screen edge (overscan, [gui/STREAM_MENU.md](gui/STREAM_MENU.md#2-stream-menu)).
- HDR continues through both MediaCodec and PyroWave paths.
- Removing the external display closes its menu and controller activity before returning to the shell. The first game context menu waits for the refreshed running-game state, so Resume and Quit are immediately available.
- A stream on the external display that fails to start, loses its connection or is closed by the PC ends without the classic error dialog (which only showed on the TV): `StreamOutcome` opens the shell on the device's own screen (`setLaunchDisplayId(DEFAULT_DISPLAY)`) with the same result overlay or "The PC ended the stream" toast as any other stream ([gui/SHELL_ARCHITECTURE.md](gui/SHELL_ARCHITECTURE.md#4-stream-exits)).
- The obsolete external-display notification, its runtime permission and its receiver were removed; focus changes go through `ExternalDisplayFocus`.
- The stream menu's brightness slider sets the brightness of the device's own screen (the controller activity), because the TV ignores window brightness. After the screen dims from inactivity it returns to that value on the next touch.

## Input architecture

Android treats gamepads and keyboards as untargeted input. On a normal multi-display device they go to the globally focused display, which is the Odin screen in this layout, while the stream menu is rendered by the `DelosStreamActivity` activity on the TV. Android does not dynamically route the built-in controller to that second display.

`ExternalDisplayControlActivity` is therefore the single bridge for external-display input:

1. Normal controller keys enter its `dispatchKeyEvent()` path.
2. Keys claimed by an IME enter the supported `ExternalControllerView.onKeyPreIme()` path first. A key that route leaves unhandled continues to the IME and then to `dispatchKeyEvent()`; `onKeyPreIme()` does not hand keys to the stream itself.
3. Both paths call `DelosStreamActivity.handleExternalDisplayKeyEvent()`; a key it handles is not routed again.
4. With the menu closed, the original `KeyEvent` or `MotionEvent` continues to the existing stream input handlers unchanged.
5. With the menu open, `StreamMenu` owns every key event, keyboard and controller alike, and nothing reaches the stream. It emits semantic UI actions: move focus, activate, back (B, Back or Esc), or adjust the focused control. It does not inject fabricated D-pad events into the TV window.

`FocusTracker` exposes the focused control's current activation and adjustment callbacks to this route. Analog navigation has an explicit edge and repeat policy. The original `InputConnection` and `commitText` behavior remain enabled for the Android keyboard and future controller UI.

The accessibility keyboard service uses the same routes: the controller activity's router when the external controller activity has focus, and `DelosStreamActivity.dispatchKeyEvent()` (menu shortcut first) when the stream window on the device has focus, so enabling it can never bypass the menu.

## Code map

| Path (under `app/src/main/java/com/limelight/`) | Role |
|---|---|
| `utils/ExternalDisplayControlActivity.java` | The activity on the device's screen that receives input for the stream on the other display |
| `ui/ExternalControllerView.java` | `onKeyPreIme()`: keys that an input method claims |
| `utils/ExternalDisplayFocus.java` | Focus changes between the two displays |
| `DelosStreamActivity.java` | `handleExternalDisplayKeyEvent()`: the one entry of those keys into the stream |
| `shell/StreamResult.kt` | `StreamOutcome`: where the shell opens after a stream on the external display ends |

## Verification

Regression tests cover analog edge/repeat behavior, pre-IME gamepad routing, and retained `commitText` support (`ExternalControllerViewTest`).

### Not verified

- No run with an external display after 2026-10-27 is recorded in this document. The stream menu as a bottom bar,
  the result overlay after a failed stream and the display classification were added after that date.

## Related

- [DUAL_SCREEN.md](DUAL_SCREEN.md), [GUI.md](GUI.md).
- Issue [#22](https://github.com/DELOS-STREAM/DELOS-android/issues/22) (device and external-display compatibility).

## Evidence (2026-10-27)

AYN Odin 2 Portal Max with an external TV: loading transition, configured resolution, HDR, menu opening, D-pad, analog, A/B, adjustable controls and return behavior were accepted by the owner. The debug build, the unit tests of that day (68) and Android lint (0 errors) passed. The display classification of [DUAL_SCREEN.md](DUAL_SCREEN.md) was added later and is not part of this record.
