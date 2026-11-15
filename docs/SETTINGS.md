# Settings

## Purpose

The Settings screen of the shell edits the same keys and value types as `app/src/main/res/xml/preferences.xml` and
adds its own native rows (`NativeSettings.kt`). This document specifies the tabs, the catalog that is read from the
XML, the impact chips, the help texts, the hidden settings and how settings belong to user profiles.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Tabs, rows, search, help texts, impact chips | The look and motion of the screen: [GUI.md](GUI.md) |
| The settings catalog and the stores that write preferences | Overrides of one app: [PER_APP_SETTINGS.md](PER_APP_SETTINGS.md) |
| Hidden phone-oriented settings | What the stream settings do: [STREAM_QUALITY.md](STREAM_QUALITY.md) |
| User profiles and their storage | Languages and translated texts: [LOCALIZATION.md](LOCALIZATION.md) |

## Tabs

Categories that cover several subjects are split by setting.

| Tab | Contents |
| --- | --- |
| Stream | Resolution, frame rate, bitrate, frame pacing, latency, codec, Wi-Fi keep-alive |
| Display | Scaling, orientation, display modes, variable refresh rate, virtual display, HDR, color range and performance overlay |
| Audio | Channels, audio effects, playback on the PC and interface sounds |
| Controls | Gamepad, mouse, touch, keyboard, clipboard, virtual controls, editable layouts and interface haptics |
| Appearance | Theme, wallpaper colors, backgrounds, interface scale, animation and the remaining interface options |
| Covers | PC and SteamGridDB artwork, API key, automatic and bulk downloads, per-app artwork selection |
| Devices | Default PC, skipping the start screen, device management and pairing |
| About | Version, project information, welcome screen, language, diagnostics and project links |

L1/R1 switch tabs. Left/right adjust a choice or slider; A toggles, cycles or opens an editor/action.
Sliders also support tapping and dragging, and commit a drag when the finger is released.
Focus is handed to a stable, zero-size target before a tab's lazy list is replaced, then silently to
the new row. Simply clearing focus allows a scrolled list's focus search to re-enter through Stream.
The handoff gives the switch one sound/haptic response without an intermediate tab focus. The list
reserves space above the focused row for its section heading, including when returning to the top.
Unavailable dependencies remain visible, dimmed and searchable. Hardware-inapplicable controls are
hidden, following the classic screen. The description panel stays outside controller focus. Long
help automatically scrolls down and back up, with pauses and fading text edges; manual scrolling is
disabled. With Animations set to Reduced the help stays still and can be scrolled by touch. A new
selection resets it to the top.

Y opens search from settings; the hint bar also shows this shortcut. Search accepts typed text or
letters selected with the controller. Results span all tabs and selecting
one scrolls to and focuses the actual setting, including disabled dependencies.

**About → Diagnostics → Keep diagnostic logs** (native row `about_diag_logs`, Phosphor `notebook`, default on) switches the rolling on-device
log and the crash file on or off for this device (not per profile; stored as `diag_logs_enabled` in the default preferences, applied at once).
It is not a catalog setting and has no per-app override. What is logged, how it is scrubbed and shared: [DIAGNOSTICS.md](DIAGNOSTICS.md).
The Debug info screen has the matching Diagnostics section (preview, Share, Copy summary, Clear).

The **About → Debug info** screen also contains an opt-in [rumble laboratory](RUMBLE.md)
with profile-scoped translation parameters, fixed test patterns and a live amplitude readout.
Translation is off by default. The four presets chosen there are offered in the normal Controls tab as the
**Rumble feel** row (Original, Soft, Kick-start, Pulsing; Controls → Gamepad, below *Enable Rumble*, hidden
without a vibration motor, no per-app override). The row is not stored: it shows and applies the rumble
preset keys through `RumblePreferences` (see [RUMBLE.md](RUMBLE.md)) and reads Custom after Advanced edits.

## Catalog and stores

- `shell/settings/SettingsCatalog.kt` reads titles, summaries, defaults, dependencies, choices, types,
  ranges, units and action URLs directly from the existing XML, in the language of the resources it is
  given (it is rebuilt when the app language changes). The `list_languages` row (Misc) switches the
  language of the whole app, see `shell/settings/AppLanguage.kt`. How the catalog and the help texts are
  locale-aware: [LOCALIZATION.md](LOCALIZATION.md).
- `SettingsCapabilities.kt` contains the display, codec, HDR, sensors, USB, vibration, PiP and
  touchscreen rules ported from `StreamSettings`. Native and custom resolutions/frame rates are
  added to the original choices. Explicitly unavailable codec choices are omitted.
- `StreamSettingsStore.kt` writes typed preferences. Each selected codec (Automatic, H.264, HEVC, AV1
  and PyroWave) remembers its own bitrate when the user switches away, and restores it on return.
  Hand edits change the current bitrate immediately; the codec's saved value updates only on exit.
  With no saved value, first use of PyroWave calculates its recommendation. First use of another
  ordinary codec preserves the current bitrate; leaving PyroWave for an ordinary codec without a
  saved value uses its normal resolution/FPS default in profile settings.
  Resolution/FPS/HDR/chroma changes while on PyroWave recalculate its recommendation; other codecs preserve
  their bitrate on those changes. Returning to PyroWave with a saved value restores it instead of
  recalculating just because the codec changed.
  Codec bitrate slots are private profile data, copied/deleted with the profile, never settings rows
  or effective stream values. For per-app settings they are isolated by profile, PC and app:
  inherited rates are never saved into app slots, and leaving PyroWave for an unsaved ordinary
  codec restores profile inheritance. See [PER_APP_SETTINGS.md](PER_APP_SETTINGS.md).
  Custom numeric input rejects invalid and non-finite values before writing.
- `screens/SettingsScreen.kt`, `NativeSettings.kt` and `SettingsActions.kt` render the design system's
  rows, description panel, tab bar and search, and handle editing, reset, import/export and links.
  Resets run immediately and report a toast. Android's document picker handles file selection.
  When PyroWave is selected, the bitrate row and live help also show its recommended Mbps for the
  current resolution/FPS/effective chroma/HDR, independently of the chosen bitrate. SDR 4:4:4 uses
  its own regression coefficients; HDR uses the 4:2:0 recommendation even if 4:4:4 remains saved.
  Other codecs show the recommendation only when the PC can test the link or a result exists (docs/STREAM_QUALITY.md, "Ordinary codecs"). With a PC known, the hint also carries the reason and the age of the
  last link test (`Recommended: 225 Mbps · link tested at 340 Mbps`), and two rows follow the bitrate row in the
  Stream tab: **Test link** (starts the UDP probe through a cancellable portal; explains it when
  the PC cannot test) and **Use recommended bitrate** (fills the bitrate on the user's press; nothing else ever
  changes it). The PC is the one whose library is open, else the first paired one. The per-app bitrate row carries
  the same hint but no Test link. Method, thresholds and storage: [STREAM_QUALITY.md](STREAM_QUALITY.md#pyrowave-link-guidance-18).
- `list_pyrowave_chroma` (4:2:0 default / 4:4:4, Stream tab, also a per-app override) is only enabled with the
  PyroWave codec and no HDR. 4:4:4 adds `VIDEO_FORMAT_PYROWAVE_444` to the offer next to the 4:2:0 flag
  (`PyroWaveFormats`); the host picks, and the renderer follows the negotiated format. With HDR on, 4:4:4 is
  never offered (the P010 output is 4:2:0 only) and the stream uses 4:2:0.
- `list_sgsr_quality` (Display tab, also a per-app override, depends on `checkbox_sgsr`) has five modes; the stored
  values are `standard`, `high`, `temporal`, `temporal-quality` and **`sharpen`** (Sharpen only: no scaling, a
  contrast-adaptive sharpen pass at the stream's own resolution). `UpscalerMode` is the one definition of the
  values, labels and menu positions; an unknown stored value reads as `standard`, so a value from a newer build
  never breaks an older one. `seekbar_sgsr_sharpness` works in every mode and is the whole effect in Sharpen
  only. `list_sgsr_supersampling_level` is disabled in Sharpen only ("Not used by Sharpen only.") because that
  mode never scales. See [STREAM_QUALITY.md](STREAM_QUALITY.md#sharpen-only-mode).

## Performance impact chips (issue #40)

`impact(key, values): SettingImpact?` in `shell/settings/SettingsImpact.kt` (pure, unit-tested in `SettingsImpactTest`)
returns the cost of a setting's **current value** as three levels (`NONE`, `LOW`, `MEDIUM`, `HIGH`): `gpu`, `latency`,
`network`. `SettingRow` draws them as chips on their own line above the title (docs/GUI.md, "Impact chips"). The global settings screen passes
`impact(spec.key, values)` through `SettingsItem.impact`; the per-app screen can do the same with its effective values map.
Null means no chips (any other key, PyroWave chroma without PyroWave, supersampling with Sharpen only). With the upscaler off
its on/off, mode, sharpness and supersampling rows return all-`NONE` (dimmed chips). A level is a bucket, never a measurement.

- GPU of SGSR2-DV is its pass time over the frame period: about 6.85 ms (1080p to 1440p, the 1.33x level) or 10.23 ms (1080p
  to 4K, 2x) on the Odin (docs/STREAM_QUALITY.md); below 35 % Low, below 70 % Medium, else High. A pass longer than the frame
  period (4K at 120 FPS) is over budget and the latency is High. SGSR2-DV (Quality) has no number ("most GPU"): one level above Normal.
  Sharpen only and the sharpness pass are about 0.31 ms: Low. SGSR 1 modes are qualitative (Normal Low, Quality Medium).
- Network uses one scale: below 50 Mbps Low, below 150 Medium, else High. The bitrate row uses its own value; PyroWave rows use the
  real `PyroWaveBitrate` recommendation (4:2:0 / 4:4:4 / HDR, resolution, FPS); ordinary codecs use LinkPolicy's judgment table
  (20 / 40 / 80 Mbps at 1080p / 1440p / 4K 60 FPS, doubled for HEVC and AV1, tripled for H.264, capped at 150), only at 60 FPS.
- Codec, HDR, frame pacing and the remaining GPU / latency levels are qualitative, taken from the help texts (for example Warp
  "requests twice the FPS", Smoothness "adds buffering and latency", PyroWave "needs a fast link and far higher bitrate").

| Setting | GPU | Latency | Network |
|---|---|---|---|
| Video resolution | 4K Medium, else Low | 4K Medium, else Low | recommended Mbps of the codec at 60 FPS; PyroWave real; otherwise Medium (720p Low) |
| Video frame rate | upscaler pass share of the frame (PyroWave without upscaler: 60 Medium, above High; else Low) | 60 FPS Medium, above Low | PyroWave High; 60 Low, above Medium |
| Video bitrate | none | 150 Mbps and up Medium, else Low | by Mbps |
| Video codec | PyroWave High, else Low | Low | PyroWave High, H.264 Medium, else Low |
| HDR | on: Low | none | on: Low, PyroWave High |
| PyroWave chroma | 4:4:4 (SDR) High, 4:2:0 Medium | Low | High |
| Frame pacing | Warp 2 High, Warp Medium, else Low | Lowest latency / Fast V-Sync / Warp Low, Balanced and FPS limit Medium, Smoothness High | Warp 2 High, Warp Medium, else none |
| Variable refresh rate | no chips | no chips | no chips |
| Upscaler on/off, mode | Normal Low, Quality Medium, SGSR2-DV by the pass share (Quality one higher), Sharpen only Low | Low, SGSR2-DV Medium (High when over budget) | none |
| Upscaler sharpness | 0 none, else Low | 0 none, else Low | none |
| Supersampling quality | SGSR2-DV like the mode at that level; SGSR 1: 2x Medium, 1.33x Low | as the mode | none |

## Motion rows and Gyro smoothing (#77)

The motion rows of Controls → Gamepad keep their preference keys (stored values, profiles) and have these names:

| Key | Row |
|---|---|
| `checkbox_gamepad_motion_sensors` | External gamepad gyroscope (was *Allow use of gamepad motion sensors*; Android 12+) |
| `checkbox_gamepad_motion_fallback` | Built-in device gyroscope (was *Emulate gamepad motion sensor support*) |
| `checkbox_force_device_motion` | Always use built-in device gyroscope (was *Always use device's motion sensors*) |
| `seekbar_gyro_smoothing` | **Gyro smoothing**, 0 to 100 %, default 0 = off |

Search still finds the rows by their old names (the English aliases in `SearchAliases`, `screens/SettingsSearchAliases.kt`, next to the localized `settings_search_alias_*` synonyms). All four
are per-app settings (App settings, Stream tab, after the codec rows; `GameSettingsStore.GYRO_KEYS`), with the same hiding and
enabling rules evaluated on the app's effective values. Gyro smoothing is hidden where there is no motion source at all (below Android 12 without an
accelerometer or gyroscope feature; `SettingsCapabilities.motionHiddenKeys`) and disabled while both the external and the
built-in source are off (`gyroSourceEnabled`). The Settings screen is not open during a stream, so a change made there applies to the next stream. The stream menu's
More page has the same slider and applies at once (`PreferenceConfiguration.gyroSmoothing` is volatile and read by the
sensor threads) for this stream and keeps it as this app's `seekbar_gyro_smoothing` override automatically (`MenuAutoSave`, like the upscaler: about 500 ms after the last change, or when the menu closes or the stream ends). The menu never writes the profile; the Settings slider is the way to change the profile default. How it works and what it costs: [STREAM_QUALITY.md](STREAM_QUALITY.md#gyro-smoothing-77).

## Hidden phone-oriented and key-layout settings

Delos is handheld-first, so the touch-mouse and on-screen gamepad settings are hidden from the settings
list and search (issue #65): the mouse mode list, trackpad/touch sensitivity, drag-and-drop options,
absolute mouse mode, remembered mouse mode/zoom and pan, and the on-screen controls (show, opacity, free
stick, L3/R3 only, Guide button, hide with a gamepad, vibration, reset). The settings that exist only for
the key-layout overlay (`KeyBoardController`, no longer offered in the stream menu) are hidden too: show
at start (`checkbox_enable_keyboard`), the layout list, square buttons, reset, import and export of that
layout. Only the UI is affected: the XML
entries, defaults and stored values stay, so hidden settings keep applying and the stream menu still
works. `shell/settings/PhoneOnlySettings.kt` is the single switch: set `HIDDEN` to `false` (or remove
single keys from `KEYS`) to show them again. `SettingsCatalog.all` still lists every key; the screens
use `SettingsCatalog.visible`, and section headings disappear with their last item. The full-keyboard
settings (size, alignment, opacity, vibration, sticky modifiers), the custom keys import and the
built-in key combinations switch (they feed Send keys), multi-touch gestures, clipboard, PiP and device
rumble stay visible.

The full-keyboard settings keep their keys, defaults and texts; how the redesigned keyboard applies them (automatic fit, width and
height of the bar, alignment, opacity) is described under "Full keyboard" in [GUI.md](GUI.md).

## User profiles

All shell and stream preferences are stored in `ui_profile_<user UUID>`. On first use, the existing
effective stream values and the XML defaults are snapshotted. A new user copies the current user's
preferences, including stream settings; later changes are independent.

`StreamProfileStore` projects the stream keys into a regular Artemis `SettingsProfile`, using the same
UUID and a name of `Delos · <user name>`. It activates this profile when the user changes. Consequently
`PreferenceConfiguration` and the stream still read through the existing `ProfilesManager` overlay.
The classic profile editor activities were removed. The linked profile data remains
the compatibility layer used by the stream; unrelated Artemis profile data is preserved.

`ProfileLayouts` also gives each user private copies of OSC, the five keyboard layouts and custom
special keys. New users copy these layouts. Small, commented `Delos:` hooks in the existing layout
loaders and `GameMenu` select that storage, and the keyboard layout selector reads the active overlay.
Classic Artemis profiles that are not linked to shell users keep their original layout storage.

## Help texts

Every
catalog setting and choice has authored help. The right panel explains the currently selected
choice, interactions, applicability and when changes take effect. Long text scrolls automatically
between both ends with edge fades; the title stays fixed. The panel does not accept focus or manual
touch/controller scrolling. The same help is used in the per-app settings.
Help texts are kept factual and short (typically 3-5 lines of the settings box). The box holds about
7 lines of 31 characters at the largest interface scale (125%, where the text grows but the box does
not), so nothing scrolls there; `SettingsHelpFitTest` checks that every setting and value fits.
Settings apply on the next stream unless their text says otherwise.
Stream-menu actions and live controls have a right-hand panel too, including the native mouse-mode
page. Search includes choice descriptions. Preference keys and stored values are unchanged.

## Code map

| Path (under `app/src/main/java/com/limelight/shell/`) | Role |
|---|---|
| `settings/SettingsCatalog.kt` | Reads rows, defaults, dependencies and choices from `preferences.xml` |
| `settings/SettingsCapabilities.kt` | Which rows and choices the device offers |
| `settings/StreamSettingsStore.kt` | Writes typed preferences; codec bitrate slots |
| `settings/SettingsImpact.kt`, `ui/ImpactChips.kt` | The impact levels and their chips |
| `settings/SettingsHelp.kt` | Help texts and their notes |
| `settings/PhoneOnlySettings.kt` | The switch that hides phone-oriented settings |
| `data/Profiles.kt` (`ProfileStore`), `settings/StreamProfileStore.kt`, `settings/ProfileLayouts.kt` | Profiles, their projection into a stream profile, their private control layouts |
| `screens/SettingsScreen.kt`, `screens/NativeSettings.kt`, `screens/SettingsActions.kt` | The screen, the native rows, actions |
| `screens/SettingsSearchAliases.kt` | Search synonyms |

## Verification

`ShellSettingsTest` checks XML coverage, category assignment, numeric validation, nested dependencies,
device choices, copying/isolation/persistence of users, layout copying/reset and the actual
`PreferenceConfiguration` reader. Run the unit tests, the debug build and lint as described in
[BUILDING.md](BUILDING.md):

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

A changed stream setting is verified in a real stream on a device: the performance overlay shows the
resolution and bitrate in effect. The emulator cannot run a stream; it is used for the shell's layouts only
([BUILDING.md](BUILDING.md#run-it)).

### Not verified

- No device run after 2026-10-28 is recorded in this document; rows and tabs added later (variable refresh rate,
  gyro smoothing, impact chips) have their records, where any exist, in the documents of those features.

## Related

- [PER_APP_SETTINGS.md](PER_APP_SETTINGS.md), [GUI.md](GUI.md), [STREAM_QUALITY.md](STREAM_QUALITY.md), [RUMBLE.md](RUMBLE.md), [LOCALIZATION.md](LOCALIZATION.md).
- Issues [#40](https://github.com/Meitoncz/Delos/issues/40) (impact chips), [#65](https://github.com/Meitoncz/Delos/issues/65) (hidden settings), [#77](https://github.com/Meitoncz/Delos/issues/77) (gyro smoothing).

## Evidence (2026-10-23)

AYN Odin 2 Portal Max: the owner confirmed that a changed resolution was applied in a real stream. All eight
tabs, controller search/jump, the Y shortcut/hint, single feedback per tab switch and complete headings after
upward D-pad navigation were checked. The unit tests of that day (63) and lint (zero errors) passed.
The repeated tab-switch regression was checked over three complete cycles in both directions:
24 shoulder-button presses produced exactly 24 application vibrations.
The scrolled-list case was checked separately: six rows down in Stream then R1 to Display; six down
in Display then R1 to Audio; two down in Audio then L1 to Display; four down in Display then L1 to
Stream. Each switch produced exactly one application vibration.

## Evidence (2026-10-28)

AYN Odin 2 Portal Max: manual bitrate preservation was accepted by the owner. Profile and per-app
regressions passed, including inherited/custom bitrate and PyroWave resolution/FPS/HDR recalculation.
The build, the unit tests of that day (80) and lint (zero errors) passed.
