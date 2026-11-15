# Per-app stream settings

## Purpose

Every app of a PC's library can override a fixed set of stream and display settings of the user's profile. This
document specifies the App settings screen, which settings can be overridden, how values are inherited and
stored, and how a launch reads them.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The App settings screen, reset and Undo | Pixel positions of the screen: `GameSettingsLayout` and [gui/MENUS_AND_SETTINGS.md](gui/MENUS_AND_SETTINGS.md#12-per-app-settings) |
| The per-app keys, inheritance, codec bitrate slots | The global settings and profiles: [SETTINGS.md](SETTINGS.md) |
| Automatic saving from the stream menu | The upscaler and gyro smoothing themselves: [STREAM_QUALITY.md](STREAM_QUALITY.md) |
| How a launch reads the values | |

The classes keep the older `GameSettings*` names; the user-visible word is "app".

## Behavior

### The screen

The screen opens from an app's context menu on the home screen, **App settings**. It is built from the shell's
own components (`GameSettingsScreen.kt`). The numbers below repeat the constants of `GameSettingsLayout`.

- **Banner** (`GameSettingsLayout.BANNER_HEIGHT`, the top 290 stage px): the app's artwork blurred like the
  home background (the Appearance hero blur, the cached lens bitmap of the home screen) and fading into the
  page; the hero, else the cover or the PC's art as on the home screen, else the generated placeholder art. A
  scrim keeps the text legible on bright art. On it: the eyebrow "APP SETTINGS · <PC>", the app's name, a chip
  that says how many settings are custom for this app ("3 custom settings") or "Follows your profile",
  and the **Reset all** pill.
- **Tabs**: Stream / Display, at the same height as the global settings' tabs (`SettingsLayout.TABS_Y`; L1/R1
  and a tap; never focusable), each with the number of its custom settings.
- **List** (from y=296, 1080 wide at a 1920 stage): the shared rows with the gliding plate. **Panel** on the
  right: the global settings panel (520 wide) with the focused option's icon, name, a source box (**Custom for
  this app** with the profile's value, or **From profile** with the current value), its help (scrolling) and
  the note "Saved automatically · applies next stream". Nothing focused: how many settings are custom.
- L1/R1 change tabs, left/right adjust choices and sliders, A changes a value, B returns to the home screen.
  Touch uses the same controls.

A setting this app overrides is marked on its row: a violet (accent3) tint, a **CUSTOM** badge and
"Profile: <value>" instead of "From profile". An override equal to the profile value is still an override.

Resetting never asks for confirmation; each action shows a toast with **Undo** (Y on the controller or a
tap, while the toast is visible; it writes back only what the reset removed, including the codec bitrate
slots, and keeps anything changed since):

- **X** on a focused custom row resets it to the profile value ("Back to the profile value"); a pill
  "Reset to profile value" with the X glyph fades in on that row and the hint bar shows X. The right
  menu button still opens the row's context menu with the same **Reset to profile value** (custom rows
  only). With touch, a round reset button is shown on every custom row (the input of the last event
  decides: pointer press = touch, any key = controller).
- **Reset all** is the pill on the banner. It clears all of this app's overrides ("All settings follow
  the profile again"), is dimmed and skipped by the D-pad while the app has no custom settings, and
  after it acts the focus returns to the row last focused.

Focus order: the list is entered on its first row. **Up** from the first row (a fresh press) goes to
the Reset all pill (the only focusable thing above the list; tabs are touch only as in the global
settings); **Down** from the pill returns to the row last focused. Rows are left and entered with
Up/Down as before; Left/Right adjust the focused row.

### Inheritance and codec bitrates

The per-app keys are listed under "Settings" below. A key that is not in that list is a profile setting.

- Stream: resolution, FPS, bitrate, codec, PyroWave chroma (`list_pyrowave_chroma`), frame pacing and
  Asynchronous decoding (`checkbox_async_decode`), then the gyroscope rows: External gamepad gyroscope
  (`checkbox_gamepad_motion_sensors`), Built-in device gyroscope (`checkbox_gamepad_motion_fallback`), Always use built-in
  device gyroscope (`checkbox_force_device_motion`) and Gyro smoothing (`seekbar_gyro_smoothing`). They are hidden where
  motion is unavailable, and Gyro smoothing is disabled while neither source is on in this app's effective values. A
  launch reads them through the snapshot like the other keys (sensor setup and the live `gyroSmoothing` included).
  Stream menu → More → Gyro smoothing applies live and, like the upscaler (next section), is kept for this app
  automatically as its `seekbar_gyro_smoothing` override; the profile is never written from the menu.
- Display: HDR, full color range, scaling mode, Variable refresh rate (`list_vrr`, hidden below Android 11),
  resolution scale, virtual display, enforced display mode and the upscaler (on/off `checkbox_sgsr`, mode
  `list_sgsr_quality`, sharpness `seekbar_sgsr_sharpness` and supersampling quality
  `list_sgsr_supersampling_level`, 1 = 1x, 2 = 2x; also a profile setting, default 1x). Hardware rules and
  dependencies match the global settings screen.
- Each row says **From profile** or **Custom for this app**. Unchanged values follow later edits to
  the user profile. Changes are saved immediately and apply when the next stream starts or resumes.
- Each selected codec keeps its own bitrate for this user, PC and app. Switching away saves the
  current bitrate only when the app owns a bitrate override; inherited profile values are never
  captured. Switching to a codec with a saved value restores it as an app override.
- With no saved value, first use of PyroWave always writes its recommended bitrate, creating an
  override when necessary. First use of an ordinary codec from another ordinary codec keeps the
  current bitrate and inheritance. Leaving PyroWave for an ordinary codec with no saved app value
  removes the bitrate override, so the app follows the profile's current bitrate again.
- Hand edits change the current bitrate but update its codec slot only when switching away.
  Returning to PyroWave restores its saved value, including a hand-edited value. Resolution/FPS/HDR
  changes while PyroWave is selected still recalculate its recommendation; other codecs keep their
  bitrate on quality edits. Recalculation becomes the saved PyroWave value when leaving that codec.
- With effective codec PyroWave, the bitrate row and live help also show the recommendation from the
  effective (inherited or overridden) resolution/FPS/HDR, separately from the chosen bitrate. The
  row keeps its inheritance label; other codecs show no additional recommendation.
- Resetting the bitrate row removes that app's current override and all its codec bitrate slots.
  Reset all also removes all slots, without changing other apps or the profile.
- The upscaler rows (SGSR) are unavailable for H.264, HEVC and AV1 on devices below OpenGL ES 3.1.
  An unknown stored supersampling level is read as 1x; a saved 2x that does not fit falls back to 1x
  like the live choice.
- Advanced custom resolution/FPS definitions remain in the profile; their choices are available here.
- Audio, other controls, appearance and other global options continue to come from the user profile.

### The menu keeps the live upscaler and gyro smoothing for the app

There is no save button. The same applies to More → Gyro smoothing, which writes only `seekbar_gyro_smoothing`
(`gyroSmoothingSaveValues`; the external/built-in gyroscope toggles are not in the menu). In the stream menu → Upscaler every change (on/off, mode, sharpness, supersampling quality)
applies live and is written automatically as this app's overrides of the four upscaler keys, in the active user's profile
for this PC and host app ID (`MenuAutoSave`, `GameSettingsStore.save`). A slider drag or held D-pad writes once, 500 ms
after the last change; closing the menu or ending the stream writes at once, so the last value set wins. Opening the menu
writes nothing, and nothing else is written: no bitrate, no other display setting, no profile value, no other app, no
profile is activated. No toast is shown. The values then show as **Custom for this app** in App settings → Display (and
the app's cover badge), and X / Reset to profile value / Reset all remove them like any override.

The saved values apply to future launches only; the running stream keeps its own (the launch snapshot is fixed). A stream
started without its launch identity (no snapshot, PC or app ID in the intent) has no per-app store and stays
session-only. Both renderers (MediaCodec SGSR and PyroWave) read the same live values. The supersampling level saved is the
one in effect, so a 2x that PyroWave already fell back from is saved as 1x. The mode is saved by its stored value, so Sharpen
only (`sharpen`) is saved and restored like the four SGSR modes; the level is still written, but Sharpen only ignores it.

`GameSettingsStore` stores sparse, typed values under `ui_game_<PC hash>_<app ID>_<setting key>` in
the user's `ui_profile_<UUID>` preferences. PC UUID plus host app ID distinguishes libraries; titles
and artwork do not affect identity. Creating a user copies these values with the other profile
settings, including private codec bitrate slots. Later edits are independent; deleting a user deletes
their overrides/slots too (after the 10 s undo period of the profile toast, see `ProfileStore.delete`;
`ProfileDeleteUndoTest` covers it), and removing a PC in Devices deletes both for every user, also in
profiles that are only waiting to be purged. Slot keys use the
same per-app prefix and never inherit the profile's codec slots or appear in effective-value maps,
the settings catalog or the stream's launch snapshot.

### Launch integration

`GameLauncher.startIntent` is the one place that builds an app's start intent: it reads the effective
virtual-display setting and attaches a snapshot of the per-app options to the `DelosStreamActivity` intent. The
Delos UI (`GameLauncher.prepare`) and launcher/PC shortcuts (`ShortcutTrampoline`) both use it, so every way to
start an app streams with that app's overrides. External-display launches carry the snapshot on the nested
stream intent. `ProfilesManager.getOverlayingSharedPreferences` applies this snapshot only when called with
that stream Activity (or a context wrapping it). No active profile is changed and no temporary global values
are written. Unrelated options retain the existing profile behavior. The snapshot survives Activity
recreation through its intent, and another app receives its own values at launch.

## Settings

The per-app keys are `GameSettingsStore.STREAM_KEYS` and `GameSettingsStore.DISPLAY_KEYS`. Defaults, ranges and
choices are those of the profile setting with the same key ([SETTINGS.md](SETTINGS.md)).

| Tab | Key | Row title (English) |
|---|---|---|
| Stream | `list_resolution` | Video resolution |
| Stream | `list_fps` | Video frame rate |
| Stream | `seekbar_bitrate_kbps` | Video bitrate |
| Stream | `video_format` | Video codec |
| Stream | `list_pyrowave_chroma` | PyroWave chroma |
| Stream | `frame_pacing` | Video frame pacing |
| Stream | `checkbox_async_decode` | Asynchronous decoding |
| Stream | `checkbox_gamepad_motion_sensors` | External gamepad gyroscope |
| Stream | `checkbox_gamepad_motion_fallback` | Built-in device gyroscope |
| Stream | `checkbox_force_device_motion` | Always use built-in device gyroscope |
| Stream | `seekbar_gyro_smoothing` | Gyro smoothing |
| Display | `checkbox_enable_hdr` | Enable HDR |
| Display | `checkbox_full_range` | Force full range video |
| Display | `list_video_scale_mode` | Video Scale Mode |
| Display | `list_vrr` | Variable refresh rate |
| Display | `seekbar_resolution_scale_factor` | Resolution Scale Factor |
| Display | `checkbox_use_virtual_display` | Use Virtual Display |
| Display | `checkbox_enforce_display_mode` | Enforce Display Mode |
| Display | `checkbox_sgsr` | Snapdragon Game Super Resolution |
| Display | `list_sgsr_quality` | Upscaler mode |
| Display | `seekbar_sgsr_sharpness` | Upscaler sharpness |
| Display | `list_sgsr_supersampling_level` | Supersampling quality |

| Stored as | Where |
|---|---|
| `ui_game_<PC hash>_<app ID>_<setting key>` | The user's `ui_profile_<UUID>` preferences |

## Code map

| Path (under `app/src/main/java/com/limelight/`) | Role |
|---|---|
| `shell/screens/GameSettingsScreen.kt` | The screen and `GameSettingsLayout` |
| `shell/settings/GameSettingsStore.kt` | The keys, sparse storage, inheritance, reset, `backup` / `restoreMissing` |
| `shell/settings/MenuAutoSave.kt` | The debounced automatic save from the stream menu |
| `shell/data/GameLauncher.kt` | `startIntent`: the launch snapshot |
| `profiles/ProfilesManager.java` | `getOverlayingSharedPreferences`: applies the snapshot for the stream activity |
| `ShortcutTrampoline.java` | Launcher and PC shortcuts, through `GameLauncher.startIntent` |

## Verification

`GameSettingsTest` covers live inheritance, PC/app/user isolation, copying and deleting users, forgetting a
PC, persistence across store recreation, resetting one/all overrides, PyroWave bitrate guidance, the actual
`PreferenceConfiguration` stream reader, wrapped contexts and external-display intent forwarding. It also
covers the menu's automatic save: exactly the four upscaler overrides (`upscalerSaveValues`) are written for that app only, a later
launch of it (and not of another app) reads them, including supersampling level 2, resets remove them,
an unknown stored level reads as 1x, the clamping of the saved values, and which launches can save. The
Undo of a reset (`backup` / `restoreMissing`) is covered too: it restores the removed values and codec
bitrate slots, keeps a value changed since, never writes a foreign key and touches no other app.
`MenuAutoSaveTest` covers the debounce with an injected clock (one write after the last change, flush, nothing on open, per-setting merge). `GameSettingsLabelTest` covers the pure screen rules (chip text, per-tab custom counts, the panel
summary, the "Profile: …" value labels). Debug preview of the look with fixed data:
`--es preview_screen game_settings [--ez preview_touch true] [--ez preview_auto true]`.

Device acceptance: open the settings of an app, check both tabs, change its resolution to 720p and
start or resume its stream. Verify 1280x720 in statistics, then check that the profile and
another app retain their resolution. Use X on a custom row (and the round button with touch), the row's
Reset to profile value in the right-menu, and the Reset all pill, then Undo (Y); check this app follows the
profile again. Check the banner (hero blur against the home screen, an app without artwork, bright art under
the title), Up from the first row to Reset all and Down back to the same row, Reset all dimmed without
custom settings, tab switches after scrolling, one feedback event per switch, touch
adjustment, help scrolling and focus returning to the home screen.

Device acceptance for the menu's automatic save: in a stream, open Stream menu → Upscaler, switch the upscaler
on and change mode, sharpness and supersampling quality; close the menu (no save button, no toast). End the stream and
open App settings → Display: the four rows say Custom for this app (the profile's Display settings unchanged). Start
the app again and check the upscaler comes up as set last (status line, statistics); another app keeps the profile
values. Opening the menu alone must not make any row custom. Reset all returns it.

### Not verified

- The redesigned screen (hero banner) has no recorded device run in this document.
- Variable refresh rate, PyroWave chroma and Asynchronous decoding as per-app settings have no recorded device run.

## Related

- [SETTINGS.md](SETTINGS.md), [GUI.md](GUI.md), [STREAM_QUALITY.md](STREAM_QUALITY.md).
- The idea of per-app settings is credited to MoreOrLessSoftware's Moonlight X; this is an original implementation.
  See [CREDITS.md](CREDITS.md).

## Evidence (2026-10-25)

AYN Odin 2 Portal, first version of the screen: the unit tests of that day passed (51, seven of them for these
settings), the debug build and Android lint passed, and the ARM64 build was installed on the device. The owner
confirmed that the appearance, the controller navigation, a per-app resolution and the isolation from the
profile and from another app worked. The redesigned screen (hero banner), Variable refresh rate, PyroWave chroma
and Asynchronous decoding as per-app settings were added later and have no recorded device run here.
