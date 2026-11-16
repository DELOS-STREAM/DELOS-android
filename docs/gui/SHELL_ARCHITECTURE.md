# Shell architecture and design constraints

## Purpose

This document specifies the implementation and design context of the Delos shell: the constraints every shell change
keeps, the classes that implement the shell, how a stream ends, which actions run without a confirmation, and how
`DelosStreamActivity` is split into helpers.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Design constraints of the shell: reused backend, UI toolkit, navigation, performance | Design principles and the index of the GUI documents: [GUI.md](../GUI.md) |
| Stage coordinates, global keys, controller identity, rumble feedback | Screen shapes: [SCREEN_SHAPES.md](SCREEN_SHAPES.md); controls and hints: [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md) |
| Background, blur and palette constraints | Motion, toasts and color roles: [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md) |
| Stream exits and the result overlay | Stream menu: [STREAM_MENU.md](STREAM_MENU.md); launching a game: [START_AND_HOME.md](START_AND_HOME.md) |
| Actions without confirmation | Full keyboard and its editor: [FULL_KEYBOARD.md](FULL_KEYBOARD.md) |
| Artwork and virtual controls constraints | SteamGridDB: [ARTWORK.md](ARTWORK.md); Phosphor icon generation and licensing: [ICONS.md](../ICONS.md) |
| `DelosStreamActivity` structure | |

## 1. Design constraints

### 1.1 Reused backend

1. `ShellActivity` is the launcher.
2. `ShellLink` handles external links and PC shortcuts.
3. Kotlin and Compose implement the shell.
4. Existing Java/backend behavior is reused rather than rewritten: `ComputerManagerService`, `NvHTTP` /
   `PairingManager`, `PreferenceConfiguration`, `ProfilesManager`, `ShortcutHelper` and `ServerHelper`.
5. Streams start through `ServerHelper.createStartIntent()` into `DelosStreamActivity`.
6. `GameLauncher.startIntent` wraps that call to attach the per-app settings. It is shared with launcher shortcuts.
7. Upstream-shared changes are kept small.

### 1.2 UI toolkit and navigation

1. The shell uses Compose foundation/ui/animation and the Delos design system, not Material components.
2. The shell owns its small navigation stack, animated transitions and overlay focus.
3. The fonts are the bundled Unbounded/Figtree (OFL).
4. Phosphor icon generation/licensing is documented in [ICONS.md](../ICONS.md).
5. The ordinary flow has no confirmation dialogs. Legitimate security prompts for external input remain.
   The actions this affects are in [Actions without confirmation](#5-actions-without-confirmation).
6. Further layout changes need the owner's agreement.

### 1.3 Data

1. `PcRepository` binds the computer service and exposes StateFlows.
2. Profile preference files isolate appearance, startup, hidden games, last-played state and PC names.
3. Hidden apps reuse the existing `HiddenApps` preferences.
4. The running game sorts before last-played ordering.

### 1.4 Library performance

The library performance constraints are kept:

| Element | Constraint |
|---|---|
| Hero blur | Uses a small bitmap off the main thread. |
| Animated background | Invalidation is limited. |
| Contour | Uses batched lines. |
| Haze | Active only for visible overlays. |
| Focus animation | The values are read during drawing. |
| Idle-cover dim | Uses a filter. |
| Row navigation | Retains edge wrapping and the existing fade/glide behavior. |

### 1.5 Launch scene and stream overlays

1. LaunchOverlay and StreamLoader form one scene.
2. AGSL dive effects use the older-device ring fallback.
3. `StreamMenu` provides the configured button combo/edge gesture and the On/Compact/Off overlay cycle. The menu is
   specified in [STREAM_MENU.md](STREAM_MENU.md).
4. `StreamExit` owns the accepted normal exit scene.

### 1.6 Artwork

1. SteamGridDB supplies portrait/wide covers, hero and logo. It is specified in [ARTWORK.md](ARTWORK.md).
2. The API key is device-local.
3. Artwork lives in application storage.
4. Hand-picked files survive bulk redownloads.
5. Desktop is manually selected.
6. Dashboard logos use the accepted `800 ms` slide/fade with no scale. Reduced motion shows them immediately.
7. The reserved title area and the accepted LIBRARY footer styling are kept.

### 1.7 Virtual controls

1. Virtual controls use `OscStyle`: thin glass outlines, Figtree capitals, filled pressed state, circular face/stick
   buttons, shoulder/menu pills and a chevron D-pad.
2. The stick-button overlap is intentional.
3. Debug info provides a preview without starting a stream.

## 2. Input and feedback

### 2.1 Stage and focus

1. `Stage` provides the prototype's 1920×1080 design coordinates and the interface scale. The coordinates are planned
   per window, see [SCREEN_SHAPES.md](SCREEN_SHAPES.md).
2. The reusable focus modifier applies to touch as well as controller elements.

### 2.2 Global keys

`GlobalKeys` handles input before focus dispatch.

| Input | Action |
|---|---|
| Left/right | Adjust choice/slider rows. |
| A / Enter / D-pad center | Activate. |
| B | Goes back. |
| Select | Opens the side menu. |
| Start / X | Opens the focused element's context menu (if it has one). |
| L1 / R1 | Go to the current screen (`shell.onShoulder`, only while no layer is open): `SettingsScreen`, `GameSettingsScreen` and `ArtworkScreen` register a handler. |
| Y | Opens search. |
| System HOME / BACK / APP_SWITCH | Remain Android functions. |

### 2.3 Controller identity and feedback

| Element | Value |
|---|---|
| Name the Odin's built-in controller identifies as | Xbox Wireless Controller |
| Vendor | `0x2020` |
| Product | `0x0112` |
| AYN hints | The square/play-arrow menu glyphs |
| Navigation feedback on rumble-only devices | The established full-strength `50 ms` |
| Confirmation feedback on rumble-only devices | The established full-strength `70 ms` |

Sound/haptics remain configurable.

## 3. Backgrounds and palettes

### 3.1 Backgrounds and blur

1. Animated backgrounds are time-based.
2. Shader backgrounds require Android 13+ and use the existing fallback on older systems.
3. Overlay blur has a dim/translucent fallback where blur is unavailable.

### 3.2 Palettes

1. Each palette has main, secondary and accent colors.
2. Wallpaper colors use the tertiary accent.
3. Cover-derived palettes follow these rules:

| Step | Rule |
|---|---|
| Rejection | Near-black/white/gray is rejected. |
| Hue grouping | Hue is grouped in `15°` buckets. |
| Weight | Share is weighted by chroma squared. |
| Separation | The three selected hue groups are kept at least `30°` apart. |

This deliberate CAM16/Hct behavior is preserved. Wu quantization is not substituted for it.

## 4. Stream exits

### 4.1 Paths

| How the stream ends | Path |
|---|---|
| The player's own Disconnect / Quit app | Keeps the `StreamExit` portal. |
| The stream ends by itself | Goes through `StreamOutcome`. No classic error dialog is shown. |

A stream that ends by itself runs this sequence:

1. `DelosStreamActivity` ends.
2. It opens `ShellActivity` on the stream's PC (Home, with the PC selected) with a `StreamResult` in the intent extras.
3. The shell takes the result once. `StreamResult.take` removes the extras, so a recreated or resumed activity never
   shows it again.
4. External-display streams open the shell on the device's own screen.

### 4.2 Outcomes

There are three outcomes.

| No. | Outcome | Cause | What the shell shows |
|---|---|---|---|
| 1 | **Failed to start** | A connection stage failed, the decoder cannot start, a shortcut's PC is offline/unpaired. | The **result overlay** |
| 2 | **Connection lost** | The stream terminated with an error. | The **result overlay** |
| 3 | **Host closed** | The game exited, the PC ended the session normally. | No overlay; Home with the toast "The PC ended the stream" |

### 4.3 Result overlay

`StreamResultLayer` draws the result overlay.

| Element | Specification |
|---|---|
| Surface | Full-screen glass over Home. |
| Face | A sad face (Phosphor `smiley-sad`, `Symbols.SENTIMENT_DISSATISFIED`) in an accent-tinted circle. It settles in and floats gently. With reduced motion it fades only. |
| Title | Short, Unbounded: "Couldn't start the stream", "Connection lost", "The PC closed the stream". |
| Sentence | One calm Figtree sentence. |
| Pill **Try again** | Primary, focused first. It starts the same game on the same PC through `GameActions.start` → `GameLauncher`, so the per-app settings are read again. An offline PC or a game that is gone gets a toast. |
| Pill **Back to library** | The second of the two pills. |
| **Details** | Never open by default. It expands the technical text: stage, error code and the former dialogs' hints (permission, timeout, firewall ports, blocked network, DRM, HDR). |

| Input | Action |
|---|---|
| A / Enter | Activate. |
| B | **B = Back to library**. |
| D-pad left/right | Moves between the pills. |
| D-pad **down** | Reaches **Details**. |
| Menu buttons | Do nothing. |

The focus stays inside the overlay. The overlay asks for no confirmation.

### 4.4 Failure text

`describeStreamFailure` (pure, `StreamResult.kt`) maps stage / MoonBridge error code / ports / blocked network to
title, sentence and details. `StreamResultTest` covers each case.

### 4.5 Loader on every start

1. Every start (Home, shortcut, `.art`, link) shows the Delos loader.
2. `ShortcutTrampoline` shows the launch scene itself (`ShortcutSplash`, the same `LaunchOverlay` Home shows) while it
   finds and wakes the PC.
3. `ShortcutTrampoline` then hands the progress to `StreamLoader`.
4. Shortcut inputs that name no known PC (bad UUID, PC not found, unreadable `.art`) end with a toast.

Launching from Home is specified in [START_AND_HOME.md](START_AND_HOME.md).

## 5. Actions without confirmation

### 5.1 Deleting a profile

Deleting a profile asks nothing.

1. The profile disappears at once and the next one takes over. The first-launch screens return when it was the last.
2. A toast offers **Undo** (`Y` or a tap) for `10 s`.
3. `ProfileStore` keeps the profile's settings, per-app overrides and control layouts until then.
4. Undo restores them in the old place. The profile is current again unless another profile was chosen meanwhile.
5. The kept data is purged when the `10 s` end, or at the next start if Delos was closed meanwhile.
6. A restart never resurrects a deleted profile.
7. Another toast replaces the Undo toast. The profile then stays deleted.

Toasts are specified in [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md).

### 5.2 Clear All in the keyboard editor

1. The keyboard editor's **Clear All** hides the keys without asking.
2. The button becomes **Restore** until editing ends or keys are added.
3. The layout is saved as before.

The editor is specified in [FULL_KEYBOARD.md](FULL_KEYBOARD.md).

### 5.3 Launching another game while one runs

1. A PC shortcut or `.art` launch of another game while one runs never quits the running game and asks nothing.
2. Delos opens that PC's games with a toast naming the running game ("quit it from Delos first").
3. Quitting stays the explicit "Quit X and start" choice in Delos' own game menu. The reason: `ShortcutTrampoline`
   cannot tell a pinned shortcut from an intent of another app.
4. `art://launch` links keep their security prompt.

## 6. `DelosStreamActivity` structure

The structure is tracked in [#29](https://github.com/DELOS-STREAM/DELOS-android/issues/29).

### 6.1 What the activity keeps

`DelosStreamActivity` is the streaming activity. It keeps:

1. The lifecycle.
2. The `onCreate` orchestration.
3. Thin delegates for its external callers: `StreamMenu`, `StreamLoader`, `StreamContainer`,
   `ExternalDisplayControlActivity`, `KeyboardAccessibilityService`.
4. The `EXTRA_*` keys.
5. The Activity and view input callbacks as one-line delegates.
6. The input grab and local cursor.
7. The evdev mouse callbacks.
8. The controller wiring.

Self-contained parts live in helpers the activity creates. `DelosStreamActivity` reaches each input handler through a
small `Host` it implements.

### 6.2 Helpers

| Helper | Responsibility | Reached through |
|---|---|---|
| `StreamIntentParams` | Launch intent extras, certificate, `NvHTTP` creation. | Created by `DelosStreamActivity`. |
| `binding/input/CommitTextSender` | IME commit text in ≤512-byte UTF-8 chunks, Backspace for deleted text. | Created by `DelosStreamActivity`. |
| `utils/StreamClipboardSync` | Smart and manual clipboard sync. | Created by `DelosStreamActivity`. |
| `utils/WifiLockHolder` | High-performance and low-latency Wi-Fi locks for the stream's lifetime. | Created by `DelosStreamActivity`. |
| `binding/video/StreamDisplayPlanner` | Pure: display mode, refresh rate, Cap FPS pacing adjustment, aspect ratio and the surface frame rate from plain display and preference data. | `DelosStreamActivity` reads the `Display` before the `StreamConfiguration` is built and applies the plan to the window and `StreamContainer`. |
| `ui/StreamWindowController` | Requested orientation, picture-in-picture auto-enter and parameters, hiding the overlays and sensors in PiP, multi-window and the immersive system UI. | `DelosStreamActivity` forwards the Activity callbacks and the USB permission prompt's PiP suppression to it and provides a `Host` with the views and on-screen controls. |
| `binding/video/StreamSurfaceController` | The stream surface's buffer size for the upscaler, the decoder side of the surface callbacks, the live upscaler and supersampling controls and the launch arrival's decoder link. | `DelosStreamActivity` forwards the `StreamContainer` callbacks and starts and stops the connection through its `Host`. `StreamMenu`, `StreamLoader` and `StreamExit` call it via `DelosStreamActivity.getSurfaceController()`. |
| `StreamSurfaceHost` | The narrow interface through which the renderers reach the surface, not the activity. | The renderers. |

### 6.3 Input handlers

Input lives in `binding/input`.

| Handler | Responsibility | Reached through |
|---|---|---|
| `StreamKeyHandler` | See the list below. | `GameMenu` and `StreamMenu` call it via `DelosStreamActivity.getKeyHandler()`. |
| `StreamPointerHandler` | Handles the pointer sources left after `DelosStreamActivity`'s gamepad passthrough: mice (captured or not), styluses and erasers, touchpads and the touchscreen with its touch contexts, the 3/4/5-finger taps, the touch sensitivity and the pan and zoom mode. It owns the `PanZoomHandler`. | `StreamMenu` and `ExternalDisplayControlActivity` call it via `DelosStreamActivity.getPointerHandler()`. |
| `MouseModeController` | Picks the touchscreen's mouse mode (the profile's mode, the external display's trackpad override, remote input's forced trackpad, the stream menu's switch and its optional persistence) and installs the matching touch contexts in the pointer handler. | `StreamMenu` calls it via `DelosStreamActivity.getMouseModeController()`. |

`StreamKeyHandler`:

1. Routes keys: the stream menu, the Full keyboard and gamepad buttons first, then the keyboard.
2. Tracks the modifiers and the keys it has down on the host. The keys are released there when the window the keys
   come from loses focus. The modifier state is cleared on focus gain too.
3. Runs the client-side Ctrl+Alt+Shift+Z/Q/C combos once every modifier is up.
4. Sends the menus' special-key combinations.

## Code map

| Path or class | Role |
|---|---|
| `ShellActivity` | The launcher. Opened on the stream's PC with a `StreamResult` when a stream ends by itself. |
| `ShellLink` | Handles external links and PC shortcuts. |
| `ServerHelper` | `ServerHelper.createStartIntent()` starts streams into `DelosStreamActivity`. |
| `GameLauncher` | `GameLauncher.startIntent` wraps the start intent to attach per-app settings; shared with launcher shortcuts. |
| `GameActions` | `GameActions.start` starts the game for **Try again**. |
| `ComputerManagerService`, `NvHTTP`, `PairingManager`, `PreferenceConfiguration`, `ProfilesManager`, `ShortcutHelper` | Existing Java/backend behavior the shell reuses. |
| `Stage` | The prototype's 1920×1080 design coordinates and the interface scale. |
| `GlobalKeys` | Handles input before focus dispatch. |
| `PcRepository` | Binds the computer service and exposes StateFlows. |
| `HiddenApps` | The existing preferences that hold hidden apps. |
| `ProfileStore` | Keeps a deleted profile's settings, per-app overrides and control layouts for the Undo period and restores them. |
| `StreamMenu` | The configured button combo/edge gesture and the On/Compact/Off overlay cycle. |
| `StreamExit` | Owns the accepted normal exit scene (the portal). |
| `StreamOutcome` | The path of a stream that ends by itself. |
| `StreamResult` (`StreamResult.kt`) | The result carried in the intent extras; `StreamResult.take` removes the extras; `describeStreamFailure` maps a failure to title, sentence and details. |
| `StreamResultLayer` | The result overlay. |
| `ShortcutTrampoline` | Shows the launch scene for shortcut starts, finds and wakes the PC, hands the progress to `StreamLoader`. |
| `ShortcutSplash` | The launch scene `ShortcutTrampoline` shows; the same `LaunchOverlay` Home shows. |
| `LaunchOverlay`, `StreamLoader` | One launch scene. |
| `OscStyle` | The style of the virtual controls. |
| `DelosStreamActivity` | The streaming activity; see [`DelosStreamActivity` structure](#6-delosstreamactivity-structure) for it and its helpers. |

## Verification

| Test | What it enforces |
|---|---|
| `StreamResultTest` | Covers each case of `describeStreamFailure`. |

Debug info provides a preview of the virtual controls without starting a stream.

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [GUI.md](../GUI.md): index of the GUI documents and the design principles.
- [SCREEN_SHAPES.md](SCREEN_SHAPES.md): how the stage is planned per window.
- [START_AND_HOME.md](START_AND_HOME.md): launching a game.
- [STREAM_MENU.md](STREAM_MENU.md): the stream menu.
- [FULL_KEYBOARD.md](FULL_KEYBOARD.md): the keyboard editor.
- [MOTION_AND_COLORS.md](MOTION_AND_COLORS.md): toasts, motion and color roles.
- [ARTWORK.md](ARTWORK.md): SteamGridDB.
- [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md): controls and hints.
- [ICONS.md](../ICONS.md): Phosphor icon generation and licensing.
- [#29](https://github.com/DELOS-STREAM/DELOS-android/issues/29): `DelosStreamActivity` structure.
