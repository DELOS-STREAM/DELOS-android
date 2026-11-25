# Icon audit

## Purpose

The inventory of every icon of the app, screen by screen: which glyph an element shows and what the glyph means. It
is the reference for choosing the icon of a row or action that is added. The glyphs that the icon audit of 2026-11-10
replaced are in [Evidence (2026-11-10): glyph changes of the audit](#evidence-2026-11-10-glyph-changes-of-the-audit).

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The rules for choosing a glyph | How icons are generated and how a glyph is added: [ICONS.md](ICONS.md) |
| The glyphs that repeat on purpose | The semantic names (`Symbols.X`): `tools/fonts/icons.txt` |
| The glyph of every element, screen by screen | |

Glyph names are Phosphor 2.1.2 (Regular, with the Fill twin used on focus).

## Rules

1. One glyph, one meaning, app-wide.
2. The same concept may appear in two places with the same glyph (the stream menu tile and its settings row, a chip
   and its filter section).
3. Different concepts never share a glyph.
4. The icon font holds every Phosphor glyph the generator is asked for. Adding one is a line in
   `tools/fonts/icons.txt` and a run of `tools/fonts/subset_fonts.py --icons-only` against the cached sources.
5. The same family, weight and Regular/Fill pairing are used everywhere.
6. The controller letters (A/B/X/Y glyph buttons), the Delos logo and the battery body are custom drawing.
7. `toastKindFor` reads ERROR, WIFI_OFF, LINK_OFF, CHECK_CIRCLE, CHECK and WARNING to choose the toast kind.

## Intentional repeats

| Glyph | Where | Why it is one meaning |
|---|---|---|
| `arrow-counter-clockwise` | Replay, the video player's back 10 s | turning time back |
| `drop` (opacity) | Keyboard opacity, OSC opacity, Free stick opacity (Controls); Game background opacity (Appearance) | the same property on different targets |
| `arrow-u-up-left` (reset) | Reset on-screen controls / Reset keyboard layout, Reset all, Reset to profile value, Reset filters, Reset metadata match, toasts | the same action |
| `file-arrow-down` (import) | Import keyboard layout, Import special buttons | the same action |
| `floppy-disk` (remember) | Remember mouse mode (Controls), Remember zoom/pan (Appearance) | the same behavior |
| `arrows-down-up` (swap axes) | Invert video resolution (Display), Swap trackpad axes (Controls) | swapping two axes |
| `timer` | Ultra low latency, Latency impact chip | latency |
| `wifi-high` | status bar, Network impact chip | the network connection |
| `sliders-horizontal` (custom) | custom-settings badge, "N custom settings" chip, per-app "Custom for this app" | a value that differs from the profile |
| `bezier-curve` (smoothing) | Gyro smoothing, stream menu gyro row, Debug smoothing | smoothing |
| `boot` (kick) | Kick-start preset, Debug Start kick | the start kick |
| `vibrate` | Enable rumble, More > Rumble, Debug Vibration motor | rumble / the vibration motor |
| `device-mobile-speaker` | Emulate rumble with vibration, Debug Vibrate once | the phone buzzing |
| `faders` | Vibration strength, Debug Strength | strength |
| `clock-countdown` | HLTB chip, HowLongToBeat provider row, Duration filter | completion time |
| `sparkle` | HDR setting, native HDR chip, HDR filter chip and section, metadata badge | HDR |
| `game-controller` | Controls tab, button-capture dialog, Debug controllers, Player avatar, and a game's placeholder tile and candidate lists | a controller; a game without artwork has nothing but its controller to show |
| `play` | Start game, play a video in the overview's viewer, right menu button hint on the AYN (a physical button printed with that triangle) | starting playback; the hint copies the hardware |
| `speaker-high` | Play audio on the PC, the sound of a video in the overview's viewer | sound on |
| `list` / `square` / `copy` | menu button hints | hardware glyphs, not features |
| `pencil-simple` | Rename (devices, profile, dialog field) | rename |
| `trash` | Delete profile, Remove from Delos, undo toast default | remove |
| `gear-six` | Settings nav button, App settings, side menu "All settings" | settings |
| `power` | Quit game on the context menu, bar tile, portal | quit |
| `wifi-high` / `wifi-medium` ... | status bar levels | one glyph family; level 3 and "full" are the same code point |

## Glyphs wanted but not available

Phosphor has no dedicated glyph for these concepts. The closest glyph is used and named in the table. No concept
needs a font from another family.

| Concept | Glyph used |
|---|---|
| HDR | a sparkle stands in |
| Sharpness / unsharp mask | aperture |
| Bitrate | gauge |
| Video codec | video file |
| Gyroscope | compass |
| Frame pacing | metronome |
| Supersampling | high definition |
| Haptic feedback and rumble motor | a bell and three wave shapes next to the generic vibrate glyph |
| Metered connection | cell-signal |
| A PlayStation or Xbox Guide button | a plain disc |
| IGDB / HowLongToBeat / PCGamingWiki logos | database, countdown clock, article; only the Windows, Steam and Android logos exist |

## Inventory

Every table has the same columns. `Glyph (Phosphor name)` is the glyph the element shows. `Symbol constant` is the
`Symbols` constant of that glyph: the semantic name of `tools/fonts/icons.txt` in upper case. `Note` states the
meaning of the glyph, where else it appears, or `repeat:` with the reason for a repeat listed in
[Intentional repeats](#intentional-repeats).

### Settings tabs

The header pills. The per-app settings tab pills use the same glyphs.

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Stream tab | `screencast` | `CAST` |  |
| Display tab | `monitor` | `MONITOR` |  |
| Audio tab | `waveform` | `GRAPHIC_EQ` | no vibration row uses the waveform |
| Controls tab | `game-controller` | `SPORTS_ESPORTS` | Joy-Con fix, Stream menu buttons and the About rows have their own glyphs |
| Appearance tab | `paint-brush` | `APPEARANCE` | the brush; the palette glyph is the Color palette row |
| Covers tab | `images` | `PHOTO_LIBRARY` | artwork; "Choose artwork for one game" and "Back to library" have their own glyphs |
| Devices tab | `desktop-tower` | `DESKTOP_WINDOWS` | the PC glyph of the device cards; the monitor-only `desktop` is the bundled Desktop app |
| About tab | `info` | `INFO` |  |

### Settings > Stream

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Resolution | `four-k` | `RESOLUTION` | "4K" says resolution; the plain rectangle is the Wide cover tab |
| Custom resolution | `ruler` | `CUSTOM_RESOLUTION` |  |
| Frame rate | `film-strip` | `FRAME_RATE` | film strip = frames |
| Unlock FPS limit | `lock-open` | `UNLOCK_FPS` | open padlock |
| Custom refresh rate | `wave-sine` | `CUSTOM_REFRESH` | sine wave = Hz |
| Bitrate | `gauge` | `NETWORK_CHECK` | gauge = bitrate on the slider, the stream menu tile and page |
| Manual bitrate entry | `pencil-line` | `MANUAL_BITRATE` | typing a value; the gauge is the slider's glyph |
| Metered network bitrate | `cell-signal-full` | `METERED_NETWORK` |  |
| Frame pacing | `metronome` | `FRAME_PACING` | metronome = even frame intervals; the timer means latency only |
| Async decoding | `queue` | `ASYNC_DECODE` |  |
| Low-latency frame balance | `scales` | `FRAME_BALANCE` | scales; the sliders glyph is reserved for "custom settings" |
| Ultra low latency | `timer` | `TIMER` | the bolt is the battery charging glyph; the timer is the Latency impact glyph too (same meaning) |
| Video codec | `file-video` | `CODEC` | a video file format; the film slate is the Artwork "Type" row |
| PyroWave chroma | `gradient` | `CHROMA` | color gradient, not the theme palette |
| Prevent packet loss | `broadcast` | `WIFI_KEEPALIVE` | broadcast; the heartbeat is the Debug heartbeat pattern |
| Test link (guidance row) | `network` | `LINK_TEST` | network graph; wifi is the status bar and the Network impact axis |
| Use recommended bitrate | `thumbs-up` | `RECOMMEND` | thumbs up = recommendation |

### Settings > Display

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Upscaler | `frame-corners` | `UPSCALER` | same glyph as the stream menu tile |
| Upscaler mode | `stack` | `UPSCALER_MODE` |  |
| Sharpness | `aperture` | `SHARPNESS` | aperture = focus/sharpness; the magic wand is the Wizard avatar |
| Supersampling quality | `high-definition` | `HIGH_QUALITY` |  |
| Virtual display | `monitor-play` | `SCREEN_SHARE` | same as the Virtual display chip |
| Auto orientation | `device-rotate` | `SCREEN_ROTATION` |  |
| Invert video resolution | `arrows-down-up` | `AXIS_SWAP` | repeat: swapping two axes, same meaning as Controls > Swap trackpad axes; other tab |
| View at top center | `align-top` | `DISPLAY_TOP` |  |
| Resolution scale | `arrows-out` | `SCALE` | the interface scale has `text-aa` |
| Video scale mode | `resize` | `SCALE_MODE` |  |
| Variable refresh rate | `wave-sine` | `CUSTOM_REFRESH` | repeat: Hz, same glyph as Stream > Custom refresh rate; other tab |
| Enforce display mode | `scan` | `DISPLAY_MODE` |  |
| HDR | `sparkle` | `HDR_ON` | sparkle everywhere HDR is meant (setting, native HDR badge, filter) |
| External display | `monitor-arrow-up` | `EXTERNAL_DISPLAY` |  |
| Performance overlay | `chart-bar` | `OVERLAY` | same as the stream menu tile |
| Compact overlay | `list-bullets` | `OVERLAY_COMPACT` |  |
| Statistics position | `layout` | `STATS_POSITION` |  |
| Reduce refresh rate | `trend-down` | `REDUCE_REFRESH` | trending down; the clock is the Clock row |
| Full color range | `circle-half` | `FULL_RANGE` |  |

### Settings > Audio

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Audio configuration | `speaker-hifi` | `SURROUND_SOUND` |  |
| Audio effects | `equalizer` | `EQUALIZER` |  |
| Play audio on the PC | `speaker-high` | `SPEAKER` |  |
| Keep the sound on the device (PC and PlayStation) | `speaker-low` | `PLAYER_VOLUME_LOW` | device output; distinct from the host-speaker setting |
| Interface sounds | `music-note` | `MUSIC_NOTE` | the Music genre has the piano keys |

### Settings > Controls: gamepad

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Stick deadzone | `target` | `STICK_DEADZONE` | L3/R3 and auto touch sensitivity have their own glyphs |
| Multiple controllers | `plugs-connected` | `GAMEPAD_DETECT` |  |
| USB driver | `usb` | `USB` |  |
| USB override | `plug` | `USB_OVERRIDE` |  |
| Wired DualSense support (PC and PlayStation) | `delos-pad-dualsense-r2` | `PAD_DUALSENSE_R2` | the physical adaptive trigger; controller caps stay unchanged |
| Mouse emulation | `mouse` | `MOUSE` |  |
| Analog scrolling | `mouse-scroll` | `ANALOG_SCROLL` |  |
| Flip face buttons | `circles-four` | `GAMEPAD_BUTTONS` |  |
| Gamepad touchpad as mouse | `hand-pointing` | `TOUCHPAD` |  |
| Motion sensors | `compass` | `MOTION_SENSORS` | the Adventure genre has the signpost |
| Motion fallback | `navigation-arrow` | `MOTION_FALLBACK` |  |
| Force device motion | `device-mobile` | `DEVICE_MOTION` |  |
| Gyro smoothing | `bezier-curve` | `SMOOTHING` | bezier curve = smoothing (also the stream menu gyro row and the Debug smoothing row) |
| Joy-Con fix | `wrench` | `JOYCON_FIX` | wrench |
| Controller battery report | `battery-full` | `BATTERY_FULL` |  |
| Enable rumble | `vibrate` | `VIBRATION` | the vibrate glyph is rumble; the haptics rows have their own |
| Rumble feel | `playlist` | `PRESET` | playlist = a chosen preset (same as the Debug current preset) |
| Emulate rumble with vibration | `device-mobile-speaker` | `DEVICE_VIBRATION` | the phone buzzing (same as Debug "Vibrate once") |
| Force vibration on device | `hand-fist` | `DEVICE_FORCE` | the fist = forced |
| Vibration strength | `faders` | `STRENGTH` | faders = strength (same as the Debug strength row) |

### Settings > Controls: touch, mouse and trackpad

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Touchscreen mode | `hand-tap` | `TOUCH_APP` | same as the stream menu row |
| Remember mouse mode | `floppy-disk` | `REMEMBER` | repeat: Remember zoom/pan on the Appearance tab, same meaning |
| Local mouse cursor | `cursor` | `CURSOR` | same as the stream menu row |
| Absolute mouse mode | `gps-fix` | `ABSOLUTE_MOUSE` | GPS fix = absolute position |
| Multi-touch gestures | `hand-swipe-left` | `TOUCH_GESTURES` |  |
| Mouse navigation buttons | `arrow-bend-double-up-left` | `MOUSE_NAV_BUTTONS` | back/forward; the horizontal arrows are the Adjust hint and Quit-and-start |
| Trackpad sensitivity X | `arrows-horizontal` | `AXIS_X` |  |
| Trackpad sensitivity Y | `arrows-vertical` | `AXIS_Y` |  |
| Trackpad drag-drop vibration | `hand-grabbing` | `DRAG_DROP` |  |
| Trackpad drag-drop threshold | `hourglass` | `HOLD_DURATION` | Turn-based strategy has the calendar |
| Swap trackpad axes | `arrows-down-up` | `AXIS_SWAP` | repeat: swapping two axes, see Invert video resolution |
| Touch sensitivity | `fingerprint-simple` | `TOUCH_SENSITIVITY` | fingerprint (same as the stream menu row) |
| Global touch sensitivity | `globe` | `PUBLIC` |  |
| Auto touch sensitivity | `arrows-counter-clockwise` | `AUTO_SENSITIVITY` | counter-clockwise arrows = follows the rotation |
| Touch sensitivity X | `arrows-in-line-horizontal` | `TOUCH_AXIS_X` | each of the three X/Y pairs has its own arrows |
| Touch sensitivity Y | `arrows-in-line-vertical` | `TOUCH_AXIS_Y` | see above |
| Touchpad sensitivity X | `arrow-line-right` | `TOUCHPAD_AXIS_X` | see above |
| Touchpad sensitivity Y | `arrow-line-up` | `TOUCHPAD_AXIS_Y` | see above |

### Settings > Controls: keyboard, clipboard, on-screen controls

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Force QWERTY | `text-t` | `FORCE_QWERTY` | the grid is the keyboard layout scheme row |
| Right Alt as Meta | `command` | `KEYBOARD_COMMAND_KEY` | the row is labeled "Right Alt as Command (Mac)", so the Command glyph is the right one (the same glyph names the stream menu "Send keys" row, a different screen) |
| Ignore synthetic events | `shield-check` | `SYNTHETIC_INPUT` |  |
| Commit text | `chat-text` | `TEXT_INPUT` |  |
| Smart clipboard sync | `clipboard-text` | `CLIPBOARD_SYNC` |  |
| Clipboard toast | `notification` | `NOTIFICATIONS` |  |
| Hide clipboard content | `lock-simple` | `CLIPBOARD_PRIVACY` |  |
| Keyboard opacity | `drop` | `OPACITY` | repeat: same property on three rows |
| Keyboard autofit | `corners-in` | `FIT_SCREEN` |  |
| Keyboard height | `arrows-out-line-vertical` | `HEIGHT` |  |
| Keyboard width | `arrows-out-line-horizontal` | `WIDTH` |  |
| Keyboard alignment | `align-center-horizontal` | `ALIGNMENT` |  |
| Sticky modifier keys | `push-pin` | `STICKY_KEYS` |  |
| Show on-screen controls | `joystick` | `JOYSTICK` | same as the stream menu row and Debug preview |
| Hide controls when a gamepad is present | `eye-slash` | `VISIBILITY_OFF` | same meaning as Hide game |
| On-screen controls vibration | `wave-sawtooth` | `OSC_HAPTICS` | the three tap-feedback rows each get one wave |
| Only show L3/R3 | `radio-button` | `BUTTON_L3R3` | radio button = a stick click |
| Show guide button | `disc` | `GUIDE_BUTTON` | the house is the Home portal; the disc is the center button |
| Free analog stick | `arrows-out-cardinal` | `FREE_STICK` |  |
| OSC opacity | `drop` | `OPACITY` | repeat: see Keyboard opacity |
| Free stick opacity | `drop` | `OPACITY` | repeat: see Keyboard opacity |
| Reset on-screen controls | `arrow-u-up-left` | `RESET` | repeat: same action as Reset keyboard layout |
| Show virtual keys | `keyboard` | `KEYBOARD` | same glyph as the stream menu Keyboard tile: the Delos keyboard |
| Clear default special buttons | `broom` | `CLEAR_DEFAULTS` | broom; the key is the SteamGridDB API key |
| Keyboard haptics | `wave-square` | `KEYBOARD_HAPTICS` | see On-screen controls vibration |
| Default layout scheme | `grid-nine` | `KEY_LAYOUT` |  |
| Square buttons | `selection` | `SQUARE_BUTTONS` | the filled square is the AYN menu-button hint |
| Reset keyboard layout | `arrow-u-up-left` | `RESET` | repeat: see Reset on-screen controls |
| Import keyboard layout | `file-arrow-down` | `IMPORT` | repeat: same action as Import special buttons |
| Export keyboard layout | `file-arrow-up` | `EXPORT` |  |
| Import special buttons | `file-arrow-down` | `IMPORT` | repeat: see above |
| Special key help | `question` | `HELP` |  |
| Interface haptics | `bell-ringing` | `HAPTICS` | bell ringing = haptic feedback of the interface (also the side menu row) |
| Stream menu buttons | `squares-four` | `STREAM_MENU_BUTTONS` | a grid of tiles = the stream menu |
| Swipe up to open the menu | `hand-arrow-up` | `SWIPE_UP` |  |

### Settings > Appearance

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Theme | `moon` | `DARK_MODE` | also the side menu row |
| Material You colors | `swatches` | `SYSTEM_COLORS` | also the side menu row |
| Color palette | `palette` | `PALETTE` | the user's choice; the tab and the chroma row have other glyphs |
| Match the game's colors | `eyedropper` | `MATCH_COLORS` | eyedropper = picks colors from the game; the brush is the tab |
| Clock | `clock` | `SCHEDULE` |  |
| Animated background | `waves` | `ANIMATION` | waves = flowing shader; the sparkle is HDR |
| Game background | `panorama` | `WALLPAPER` | the Artwork "Background" glyph; the picture is the Cover |
| Game background blur | `drop-half` | `BLUR_ON` |  |
| Game background opacity | `drop` | `OPACITY` | repeat: opacity is one property; other tab than the control opacities |
| Interface scale | `text-aa` | `FORMAT_SIZE` | text size; the arrows are the resolution scale |
| Animations | `pinwheel` | `REDUCED_MOTION` | pinwheel = motion; the record dot read as recording |
| Disable warnings | `warning` | `WARNING` |  |
| Remember zoom/pan | `floppy-disk` | `REMEMBER` | repeat: see Remember mouse mode |
| Picture in picture | `picture-in-picture` | `PICTURE_IN_PICTURE` |  |

### Settings > Covers

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| SteamGridDB API key | `key` | `KEY` | also the API key dialog field and the "Add your key" message |
| Download automatically | `cloud-arrow-down` | `CLOUD_DOWNLOAD` | cloud download = automatic; the counter-clockwise arrows are Auto touch sensitivity |
| Download missing artwork | `download-simple` | `DOWNLOAD` |  |
| Download all again | `arrows-clockwise` | `REFRESH` |  |
| Choose artwork for one game | `image-square` | `IMAGE_CHOOSE` | one picture; the library glyph is the tab |

### Settings > Devices

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Manage devices | `devices` | `DEVICES` |  |
| Pair a new PC | `link` | `ADD_LINK` | also the Devices and Start pair button |
| Default device | `star` | `STAR` | also the Start context menu and toast |
| Skip the start screen | `fast-forward` | `FAST_FORWARD` | same as "Skip to home" |

### Settings > About

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Version | `package` | `VERSION` | package |
| Based on | `tree-structure` | `ACCOUNT_TREE` |  |
| Replay the welcome | `arrow-counter-clockwise` | `REPLAY` | also Debug "Replay Test sample" |
| Game metadata · IGDB | `database` | `PROVIDER_IGDB` | database |
| Technical data · PCGamingWiki | `article` | `PROVIDER_PCGW` | article |
| Genre and theme tags · Steam | `steam-logo` | `STEAM` | Steam logo (also the Steam Big Picture tile) |
| Completion times · HowLongToBeat | `clock-countdown` | `COMPLETION_TIME` | countdown clock (also the HLTB chip and the Duration filter) |
| Language | `translate` | `LANGUAGE` |  |
| Debug info | `bug` | `DEBUG` |  |
| Keep diagnostic logs | `notebook` | `DIAGNOSTIC_LOGS` | the log (About tab, Diagnostics section; also the Debug info Diagnostic log row) |

### Debug info

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Diagnostic log | `notebook` | `DIAGNOSTIC_LOGS` | the log (same as the Keep diagnostic logs row) |
| Share diagnostic log | `share-network` | `SHARE_LOG` | sharing |
| Copy diagnostic summary | `copy-simple` | `COPY_SUMMARY` | copying |
| Clear diagnostic logs | `trash` | `DELETE` | delete (the same action as elsewhere) |
| Trigger test crash (debug builds) | `warning-octagon` | `TEST_CRASH` | a deliberate crash |
| Problem report card: Report this problem | `github-logo` | `REPORT_ISSUE` | opens the GitHub issue |
| Problem report card: Dismiss | `x` | `CLOSE` | close (the shared close glyph) |
| Android | `android-logo` | `ANDROID_LOGO` | brand glyph |
| Kernel | `cpu` | `MEMORY` |  |
| Device | `device-tablet` | `DEVICE_MODEL` | the model; the devices glyph is Manage devices |
| Vibration motor | `vibrate` | `VIBRATION` |  |
| Vibrators | `list-numbers` | `VIBRATORS` | a count |
| Amplitude control | `chart-line` | `AMPLITUDE_SUPPORT` | chart line |
| Last problem | `warning` | `WARNING` | a problem is a warning (info is the toast and About tab glyph) |
| Vibrate once | `device-mobile-speaker` | `DEVICE_VIBRATION` | same meaning as Emulate rumble with vibration |
| Continuous vibration | `infinity` | `CONTINUOUS` | infinity |
| Strength | `faders` | `STRENGTH` | same as the vibration strength setting |
| Current preset | `playlist` | `PRESET` | same as Rumble feel |
| Preset Original | `circle-dashed` | `PRESET_ORIGINAL` | every preset has its own glyph (also the stream menu page) |
| Preset Soft | `cloud` | `PRESET_SOFT` | see above |
| Preset Kick-start | `boot` | `PRESET_KICK` | see above (also the Start kick row) |
| Preset Pulsing | `wave-triangle` | `PRESET_PULSING` | see above |
| Live amplitude | `chart-bar-horizontal` | `LIVE_AMPLITUDE` | bar chart |
| Stop vibration | `stop` | `STOP` | stop |
| Replay Test sample | `arrow-counter-clockwise` | `REPLAY` | replay |
| Pattern Steady 25 % | `engine` | `PATTERN_STEADY_25` | engine; the eleven patterns each get their own glyph |
| Pattern Steady 50 % | `steering-wheel` | `PATTERN_STEADY_50` | steering wheel |
| Pattern Steady 75 % | `jeep` | `PATTERN_STEADY_75` | jeep |
| Pattern Steady 100 % | `rocket` | `PATTERN_STEADY_100` | rocket |
| Pattern Ramp up | `trend-up` | `PATTERN_RAMP` | trend up |
| Pattern 30 ms taps | `feather` | `PATTERN_TAP_30` | feather |
| Pattern 60 ms taps | `crosshair-simple` | `PATTERN_TAP_60` | crosshair |
| Pattern 100 ms taps | `hammer` | `PATTERN_TAP_100` | hammer |
| Pattern Heartbeat | `heartbeat` | `PATTERN_HEARTBEAT` | heartbeat |
| Pattern Explosion | `bomb` | `PATTERN_EXPLOSION` | bomb |
| Pattern Flutter | `fan` | `PATTERN_FLUTTER` | fan |
| Advanced | `gear-fine` | `ADVANCED` | fine gear |
| Enable rumble translator | `function` | `TRANSLATOR` | function |
| Gain | `plus-minus` | `GAIN` | plus-minus |
| Curve | `chart-line-up` | `CURVE` | rising curve |
| Minimum amplitude | `arrow-line-down` | `MIN_AMPLITUDE` | line down |
| Start kick | `boot` | `PRESET_KICK` | boot (repeat: same concept as the Kick-start preset) |
| Low motor weight | `arrow-fat-down` | `LOW_WEIGHT` | arrow down |
| High motor weight | `arrow-fat-up` | `HIGH_WEIGHT` | arrow up |
| Smoothing | `bezier-curve` | `SMOOTHING` | same as Gyro smoothing |
| Reset parameters | `arrow-u-up-left` | `RESET` | it is a reset |
| Preview on-screen controls | `joystick` | `JOYSTICK` |  |
| No controller / each controller | `game-controller` | `SPORTS_ESPORTS` | repeat: one glyph per connected controller, same meaning |
| Continuous vibration on controllers | `repeat` | `PADS_CONTINUOUS` | repeat arrows (the device row has the infinity) |

### Header, side menu, nav bar and button hints

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Settings nav button | `gear-six` | `SETTINGS` | also App settings and the side menu "All settings" |
| Profile nav button | `user-circle` | `ACCOUNT_CIRCLE` | the profile concept everywhere |
| Devices nav button | `devices` | `DEVICES` |  |
| Status bar wifi | `wifi-high` | `WIFI` / `NETWORK_WIFI_3_BAR` | the connection concept; levels use the same family |
| Status bar charging bolt | `lightning` | `BOLT` | the bolt means charging only |
| Left menu button hint | `square` | `SQUARE` | a physical button glyph; AYN only |
| Right menu button hint | `play` | `PLAY_ARROW` | a physical button glyph; AYN only; repeat of Start game |
| Adjust hint | `arrows-left-right` | `SWAP_HORIZ` | left/right adjust |
| Side menu Theme / Material You / Background / Sounds / All settings | the glyphs of the Settings rows |  | same glyphs as the Settings rows |
| Side menu Haptics | `bell-ringing` | `HAPTICS` | follows Interface haptics |
| Side menu Show hidden games | `eye` | `VISIBILITY` |  |

### Start, Devices, Pair PC and Profile

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| PC card | `desktop-tower` | `DESKTOP_WINDOWS` | PC tower; Start, Devices, Pair and the connecting portal |
| Pair a new PC | `link` | `ADD_LINK` |  |
| Device menu: Connect | `sign-in` | `LOGIN` |  |
| Device menu: Rename | `pencil-simple` | `EDIT` | also Profile Rename and the rename dialog field |
| Device menu: Wake the PC | `alarm` | `WAKE` | an alarm clock; the power glyph is Quit |
| Device menu: Unpair | `link-break` | `LINK_OFF` |  |
| Device menu: Remove from Delos | `trash` | `DELETE` | same meaning as Delete profile and the undo toast |
| Device menu: Start Delos with this PC | `star` | `STAR` |  |
| Device card expand chevron | `caret-down` | `EXPAND_MORE` |  |
| Pair: Pair with a PIN | `numpad` | `DIALPAD` | also the PIN dialog field |
| Pair: Pair with a one-time code | `password` | `PASSWORD` | also the passphrase field |
| Pair: Enter address | `map-pin-plus` | `ADD_ADDRESS` | map pin with a plus |
| Pair: Search again | `arrows-clockwise` | `REFRESH` |  |
| Pair: Cancel | `x` | `CLOSE` |  |
| Address dialog field | `map-pin` | `MAP_PIN` | map pin |
| Profile: Change avatar | `smiley` | `FACE` |  |
| Profile: Rename | `pencil-simple` | `EDIT` |  |
| Profile: Switch profile | `users` | `SWITCH_ACCOUNT` |  |
| Profile: Add profile | `user-plus` | `PERSON_ADD` |  |
| Profile: Delete profile | `trash` | `DELETE` |  |
| Profile menu entries / Playing as | `user-circle` | `ACCOUNT_CIRCLE` |  |
| Profile name field | `identification-badge` | `BADGE` | the Logo tab uses a custom wordmark plate; the badge is the name only |
| First run: Get started | `arrow-right` | `ARROW_FORWARD` |  |
| First run: Skip to home | `fast-forward` | `FAST_FORWARD` | same meaning as Skip the start screen |
| First run: Save / Create profile | `check` | `CHECK` |  |
| Welcome toast | `hand-waving` | `WAVING_HAND` |  |
| Home: no PC paired | `devices` | `DEVICES` |  |
| Home: PC offline | `wifi-slash` | `WIFI_OFF` | unreachable (same as the "cannot be reached" toast) |
| Home: loading | `spinner-gap` | `LOADING` | spinner (also the SteamGridDB loading message) |
| Home: no games | `game-controller` | `SPORTS_ESPORTS` | a game |

### Home: tiles, context menu and game info

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Bundled Desktop tile | `desktop` | `DESKTOP_APP` | the desktop; the PC glyph is `desktop-tower` |
| Bundled Remote input tile | `mouse-simple` | `REMOTE_INPUT` | mouse |
| Bundled Remote monitor tile | `television-simple` | `REMOTE_MONITOR` | television |
| Bundled Steam Big Picture tile | `steam-logo` | `STEAM` | brand glyph |
| Game placeholder tile | `game-controller` | `SPORTS_ESPORTS` | a game; repeat of the controller concept |
| Running section | `pulse` | `RUNNING` | pulse; the play triangle is Start game |
| Last played section | `clock-counter-clockwise` | `HISTORY` |  |
| Context: Resume | `play-pause` | `RESUME` | play/pause; same as the bar tile |
| Context: Quit game | `power` | `POWER_SETTINGS_NEW` | same as the bar tile and portal |
| Context: Quit and start | `swap` | `SWAP_GAME` | swap |
| Context: Start game | `play` | `PLAY_ARROW` |  |
| Context: Artwork | `images` | `PHOTO_LIBRARY` |  |
| Context: Metadata | `file-magnifying-glass` | `METADATA` | file with a magnifier |
| Context: App settings | `gear-six` | `SETTINGS` |  |
| Context: Hide game / Show game | `eye-slash` / `eye` | `VISIBILITY_OFF` / `VISIBILITY` |  |
| Custom settings badge / chip | `sliders-horizontal` | `TUNE` | custom = adjusted; also the per-app "Custom for this app" box |
| Info chip: Running on PC | `pulse` | `RUNNING` | see Running section |
| Info chip: HDR | `sparkle` | `HDR_ON` | sparkle = HDR everywhere |
| Info chip: SDR | `sun-dim` | `SDR` | dim sun |
| Info chip: unverified HDR | `seal-question` | `HDR_UNKNOWN` | sealed question mark (help is a question mark) |
| Info chip: HLTB | `clock-countdown` | `COMPLETION_TIME` | countdown clock; history is Last played |
| Info chip: Virtual display | `monitor-play` | `SCREEN_SHARE` |  |
| Genre chips | see [Genres (IGDB)](#genres-igdb) | `GENRE_*` |  |
| Theme chips | see [Themes (IGDB)](#themes-igdb) | `THEME_*` |  |

### Game overview

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| "Game info" hint: touch / mouse | `caret-up` / `caret-down` | `EXPAND_LESS` / `EXPAND_MORE` | open more (also the section cue and Read the story) |
| Section: About | `book-open` | `OVERVIEW_ABOUT` |  |
| Section: Screenshots and videos | `images-square` | `OVERVIEW_MEDIA` | not `images` (the artwork library) |
| Section: Details | `list-dashes` | `OVERVIEW_DETAILS` |  |
| Section: Your streaming | `clock-user` | `OVERVIEW_STREAMING` | the player's own time |
| Section: Links, and a link of another kind | `link-simple` | `OVERVIEW_LINKS` |  |
| Section: Similar games | `grid-four` | `OVERVIEW_SIMILAR` |  |
| Play / App settings | `play` / `gear-six` | `PLAY_ARROW` / `SETTINGS` | as Start app and App settings of the game's menu |
| Link: opens outside the app | `arrow-square-out` | `LINK_OPEN_EXTERNAL` |  |
| Link: official site / store / video site / wiki | `globe-simple` / `storefront` / `video-camera` / `book-bookmark` | `WEBSITE_*` |  |
| Link: Steam | `steam-logo` | `STEAM` |  |
| A video in the carousel and the viewer | `youtube-logo` | `VIDEO_PLAY` | a YouTube video, played in the app |
| Video player: play / pause | `play` / `pause` | `PLAYER_PLAY` / `PLAYER_PAUSE` | repeat: `play` is Start game's |
| Video player: back / forward 10 s (with a drawn "10") | `arrow-counter-clockwise` / `arrow-clockwise` | `PLAYER_SEEK_BACK` / `PLAYER_SEEK_FORWARD` | repeat: `arrow-counter-clockwise` is Replay's; both turn time back |
| Video player: previous / next item | `skip-back` / `skip-forward` | `PLAYER_PREVIOUS` / `PLAYER_NEXT` |  |
| Video player: sound / muted | `speaker-high` / `speaker-slash` | `PLAYER_SOUND` / `PLAYER_MUTED` | repeat: `speaker-high` is Play audio on the PC's |
| Video player: low / no volume | `speaker-low` / `speaker-none` | `PLAYER_VOLUME_LOW` / `PLAYER_VOLUME_NONE` | the sound button at a low or zero volume |
| Volume dial: speaker / muted | `speaker-high` / `speaker-slash` | `SPEAKER` / `PLAYER_MUTED` | same sound meaning as the player |
| Volume dial: cable/USB headphones | `headphones` | `VOLUME_HEADPHONES` | Regular output glyph |
| Volume dial: Bluetooth badge | `bluetooth` | `VOLUME_BLUETOOTH` | beside headphones, absent when muted |
| A video that cannot play | `warning` | `WARNING` |  |
| Game modes: single player / with others | `user` / `users-four` | `MODE_SINGLE` / `MODE_MULTI` |  |
| Perspective | `cube-focus` | `PERSPECTIVE` |  |
| Platforms: PC and Linux / Mac / consoles | `desktop-tower` / `laptop` / `television-simple` | `DESKTOP_WINDOWS` / `PLATFORM_MAC` / `PLATFORM_CONSOLE` |  |
| Multiplayer facts | `users-three` | `GROUP` |  |
| Languages | `translate` | `LANGUAGE` | as the app's language |
| Data credit | `info` | `INFO` |  |
| Back (touch, mouse) / viewer: previous, next, close | `arrow-left` / `caret-left`, `caret-right`, `x` | `ARROW_BACK` / `CHEVRON_*`, `CLOSE` |  |

### Metadata and artwork

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Metadata: Search IGDB | `magnifying-glass` | `SEARCH` |  |
| Metadata: Refresh | `arrows-clockwise` | `REFRESH` |  |
| Metadata: Reset match | `arrow-u-up-left` | `RESET` | it is a reset |
| Metadata: Reset library filters | `arrow-u-up-left` | `RESET` | it is a reset |
| Metadata: Previous matches | `arrow-left` | `ARROW_BACK` | paging |
| Metadata: More matches | `arrow-right` | `ARROW_FORWARD` | paging |
| Metadata: Search for another name | `list-magnifying-glass` | `SEARCH_NAME` | list with a magnifier (also the Artwork button) |
| Metadata: candidate game | `game-controller` | `SPORTS_ESPORTS` | a game |
| Artwork tab Cover | `image` | `IMAGE` |  |
| Artwork tab Wide cover | `rectangle` | `ASPECT_RATIO` |  |
| Artwork tab Background | `panorama` | `WALLPAPER` |  |
| Artwork tab Logo | `delos-wordmark-plate` | `LOGO` | selected custom title-art plate (A), U+F000; not a trademark |
| Artwork: Search for another name | `list-magnifying-glass` | `SEARCH_NAME` | see above |
| Artwork: Filter / Change filters | `funnel` | `FILTER` | funnel |
| Artwork: Reset filters / Reset chip | `arrow-u-up-left` | `RESET` | it is a reset |
| Artwork: Clear filter chip | `x` | `CLOSE` |  |
| Artwork sheet: Type | `film-slate` | `MOVIE` |  |
| Artwork sheet: Style | `paint-roller` | `STYLE` | paint roller |
| Artwork sheet: Dimensions | `bounding-box` | `DIMENSIONS` | bounding box |
| Artwork sheet: Sort | `sort-ascending` | `SORT` | sort arrows |
| Artwork: Animated badge | `play-circle` | `ANIMATED` | play circle |
| Artwork: In use | `check` | `CHECK` |  |
| Artwork: empty search | `magnifying-glass` | `SEARCH` |  |
| Artwork: loading | `spinner-gap` | `LOADING` | spinner |
| Artwork: no key | `key` | `KEY` |  |
| Artwork: error | `warning-circle` | `ERROR` |  |
| Artwork: download toast | `download-simple` | `DOWNLOAD` |  |
| Artwork: Back | `arrow-left` | `ARROW_BACK` |  |
| Artwork: candidate game | `game-controller` | `SPORTS_ESPORTS` | a game |

### Filter modal

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Section Genre | `tag` | `GENRE_UNKNOWN` | the genre tag |
| Section Theme | `shapes` | `THEMES` | shapes (the drama mask is a chip) |
| Section HDR support | `sparkle` | `HDR_ON` |  |
| Section Duration | `clock-countdown` | `COMPLETION_TIME` | same as the HLTB chip |
| Chip HDR / SDR / Unknown | `sparkle` / `sun-dim` / `seal-question` | `HDR_ON` / `SDR` / `HDR_UNKNOWN` | same as the info chips |
| Reset | `arrow-u-up-left` | `RESET` | it is a reset |
| Done | `check` | `CHECK` |  |

### Dialogs, search keyboard, stream result, portals, toasts

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Dialog confirm | `check` | `CHECK` |  |
| Search keyboard: Delete | `backspace` | `BACKSPACE` | backspace key |
| Search keyboard: Clear | `x-circle` | `CLEAR` | circled x |
| Search keyboard: Search field | `magnifying-glass` | `SEARCH` |  |
| Settings Search button | `magnifying-glass` | `SEARCH` |  |
| Stream result: failure face | `smiley-sad` | `SENTIMENT_DISSATISFIED` |  |
| Stream result: Try again | `arrows-clockwise` | `REFRESH` | same retry/refresh arrows |
| Stream result: Back to library | `books` | `LIBRARY` | books |
| Stream result: expand/collapse | `caret-down` / `caret-up` | `EXPAND_MORE` / `EXPAND_LESS` |  |
| Portal: connecting | `desktop-tower` | `DESKTOP_WINDOWS` |  |
| Portal: wake | `alarm` | `WAKE` | see Wake the PC |
| Portal: link test | `network` | `LINK_TEST` | same as the Test link row |
| Portal: exit to Home | `house` | `HOME` |  |
| Portal: quitting | `power` | `POWER_SETTINGS_NEW` |  |
| Toast: info | `info` | `INFO` |  |
| Toast: success | `check-circle` | `CHECK_CIRCLE` |  |
| Toast: warning | `warning` | `WARNING` |  |
| Toast: error | `warning-circle` | `ERROR` |  |
| Toast: action / undo | `trash` | `DELETE` |  |
| Toast: unreachable | `wifi-slash` | `WIFI_OFF` |  |
| Toast: unpaired | `link-break` | `LINK_OFF` |  |
| Toast: reset | `arrow-u-up-left` | `RESET` | it is a reset |
| Toast: default device | `star` | `STAR` |  |
| Toast: download | `download-simple` | `DOWNLOAD` |  |
| Toast: searching | `magnifying-glass` | `SEARCH` |  |
| Toast: virtual display not ready | `monitor` | `MONITOR` | the display |
| Toast: wake sent | `alarm` | `WAKE` | see Wake the PC |
| Toast: profile name needed | `identification-badge` | `BADGE` |  |
| Impact chip GPU | `cpu` | `GPU` | owner's decision 2026-11-20: the chip, because these devices have an SoC rather than a graphics card; shares the glyph with the Debug kernel row, on another screen |
| Impact chip Latency | `timer` | `TIMER` | same as Ultra low latency |
| Impact chip Network | `wifi-high` | `WIFI` |  |
| Button capture dialog | `game-controller` | `SPORTS_ESPORTS` | a controller |
| Controller hint badge in menus | `game-controller` | `SPORTS_ESPORTS` | a controller |
| Context menu back/chevrons | `caret-left` / `caret-right` | `CHEVRON_LEFT` / `CHEVRON_RIGHT` |  |
| Full keyboard close | `x` | `CLOSE` |  |

### Stream menu bar and pages

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Bar: Resume | `play-pause` | `RESUME` |  |
| Bar: Keyboard | `keyboard` | `KEYBOARD` |  |
| Bar: Overlay | `chart-bar` | `OVERLAY` |  |
| Bar: Brightness | `sun` | `BRIGHTNESS_MEDIUM` | sun; the dim sun is SDR |
| Bar: Bitrate | `gauge` | `NETWORK_CHECK` |  |
| Bar: Upscaler | `frame-corners` | `UPSCALER` |  |
| Bar: More | `dots-three` | `MORE_HORIZ` |  |
| Bar: Disconnect | `sign-out` | `LOGOUT` |  |
| Bar: Quit game | `power` | `POWER_SETTINGS_NEW` |  |
| More: Send keys | `command` | `KEYBOARD_COMMAND_KEY` | key combinations |
| More: Send clipboard | `paper-plane-tilt` | `SEND` | paper plane |
| More: Get clipboard | `tray-arrow-down` | `RECEIVE` | tray with arrow (download is the artwork download) |
| More: Commands | `terminal-window` | `TERMINAL` |  |
| More: Task manager | `list-checks` | `TASK_MANAGER` |  |
| More: Zoom | `magnifying-glass-plus` | `ZOOM_IN` |  |
| More: Mouse | `mouse` | `MOUSE` |  |
| More: On-screen controls | `joystick` | `JOYSTICK` |  |
| More: Android keyboard | `cursor-text` | `ANDROID_KEYBOARD` | text cursor; the keyboard is the Delos keyboard tile on the bar |
| More: Touch sensitivity | `fingerprint-simple` | `TOUCH_SENSITIVITY` | same as the setting |
| More: Rumble | `vibrate` | `VIBRATION` |  |
| More: Gyro smoothing | `bezier-curve` | `SMOOTHING` | same as the setting |
| Page: Screen brightness | `sun` | `BRIGHTNESS_MEDIUM` |  |
| Page: Bitrate | `gauge` | `NETWORK_CHECK` |  |
| Page: Fill recommended | `thumbs-up` | `RECOMMEND` | same as Use recommended bitrate |
| Page: Apply | `check` | `CHECK` |  |
| Page: Upscaler Enabled | `frame-corners` | `UPSCALER` |  |
| Page: Supersampling quality | `high-definition` | `HIGH_QUALITY` |  |
| Page: Upscaler mode | `stack` | `UPSCALER_MODE` |  |
| Page: Sharpness | `aperture` | `SHARPNESS` | same as the setting |
| Page: Touchscreen mode | `hand-tap` | `TOUCH_APP` |  |
| Page: Local mouse cursor | `cursor` | `CURSOR` |  |
| Page: rumble presets | the preset glyphs of [Debug info](#debug-info) | `PRESET_*` | each preset has its own glyph, same as Debug |
| Popover back / choice chevrons / check | `caret-left` / `caret-right` / `check` | `CHEVRON_*` / `CHECK` |  |

### Profile avatars

The fallback glyph under the avatar image.

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Sailor | `sailboat` | `SAILING` |  |
| Voyager | `rocket-launch` | `ROCKET_LAUNCH` |  |
| Player | `game-controller` | `SPORTS_ESPORTS` |  |
| Fox | `paw-print` | `PETS` |  |
| Spark | `lightbulb-filament` | `SPARK` | filament bulb; the bolt is charging |
| Calm | `flower-lotus` | `SPA` |  |
| Knight | `sword` | `SWORDS` |  |
| Robot | `robot` | `ROBOT` | robot head (the chip is the kernel) |
| Cat | `cat` | `CAT` | a cat; the paw is Fox |
| Wizard | `magic-wand` | `MAGIC_WAND` | star is the default device |
| Racer | `speedometer` | `SPEED` | the gauge stands for nothing else |
| Ghost | `ghost` | `GHOST` | the face is Change avatar |
| Owl | `paw-print` | `PETS` | no bird glyph is in the font; the fallback is only seen until the illustration loads |
| Hacker | `terminal-window` | `TERMINAL` | a prompt |
| Wolf | `paw-print` | `PETS` | the same paw as Fox |
| Dragon | `paw-print` | `PETS` | no flame glyph is in the font |

### Genres (IGDB)

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Point-and-click | `cursor-click` | `GENRE_POINT_AND_CLICK` |  |
| Fighting | `boxing-glove` | `GENRE_FIGHTING` |  |
| Shooter | `crosshair` | `GENRE_SHOOTER` |  |
| Music | `piano-keys` | `GENRE_MUSIC` | piano keys (the note is Interface sounds) |
| Platform | `person-simple-run` | `GENRE_PLATFORM` |  |
| Puzzle | `puzzle-piece` | `GENRE_PUZZLE` |  |
| Racing | `flag-checkered` | `GENRE_RACING` |  |
| Real-time strategy | `clock-clockwise` | `GENRE_REAL_TIME_STRATEGY` | clock; the timer is latency |
| Role-playing | `shield-star` | `GENRE_ROLE_PLAYING` |  |
| Simulator | `airplane-tilt` | `GENRE_SIMULATOR` |  |
| Sport | `soccer-ball` | `GENRE_SPORT` |  |
| Strategy | `castle-turret` | `GENRE_STRATEGY` |  |
| Turn-based strategy | `calendar-check` | `GENRE_TURN_BASED_STRATEGY` | calendar; the hourglass is Hold duration |
| Tactical | `strategy` | `GENRE_TACTICAL` |  |
| Hack and slash | `axe` | `GENRE_HACK_AND_SLASH` | axe (the sword is the Knight avatar) |
| Quiz | `exam` | `GENRE_QUIZ` | exam (the question mark is Help) |
| Pinball | `circles-three` | `GENRE_PINBALL` |  |
| Adventure | `signpost` | `GENRE_ADVENTURE` | signpost (the compass is Motion sensors) |
| Indie | `guitar` | `GENRE_INDIE` | guitar (the bulb is the Spark avatar) |
| Arcade | `coin` | `GENRE_ARCADE` | coin (the ghost is the Ghost avatar) |
| Visual novel | `book-open-text` | `GENRE_VISUAL_NOVEL` |  |
| Card and board | `cards` | `GENRE_CARD_AND_BOARD` |  |
| MOBA | `users-three` | `GENRE_MOBA` |  |
| Unmapped genre or theme | `tag` | `GENRE_UNKNOWN` | also the Genre filter section |

### Themes (IGDB)

| Element | Glyph (Phosphor name) | Symbol constant | Note |
|---|---|---|---|
| Action | `fire` | `THEME_ACTION` |  |
| Fantasy | `crown` | `THEME_FANTASY` | crown (the wand is the Wizard avatar) |
| Science fiction | `planet` | `THEME_SCIENCE_FICTION` |  |
| Horror | `skull` | `THEME_HORROR` |  |
| Thriller | `siren` | `THEME_THRILLER` | siren (the bolt is charging) |
| Survival | `campfire` | `THEME_SURVIVAL` |  |
| Historical | `scroll` | `THEME_HISTORICAL` |  |
| Stealth | `detective` | `THEME_STEALTH` | detective (the crossed eye is Hide) |
| Comedy | `smiley-wink` | `THEME_COMEDY` | winking face (the face is Change avatar) |
| Business | `briefcase` | `THEME_BUSINESS` |  |
| Drama | `mask-sad` | `THEME_DRAMA` | sad mask |
| Non-fiction | `newspaper` | `THEME_NON_FICTION` |  |
| Sandbox | `cube` | `THEME_SANDBOX` | cube; Version has the package |
| Educational | `graduation-cap` | `THEME_EDUCATIONAL` |  |
| Kids | `balloon` | `THEME_KIDS` |  |
| Open world | `globe-hemisphere-west` | `THEME_OPEN_WORLD` |  |
| Warfare | `medal-military` | `THEME_WARFARE` | medal (the bomb is the Explosion pattern) |
| Party | `confetti` | `THEME_PARTY` |  |
| 4X | `map-trifold` | `THEME_4X` |  |
| Mystery | `binoculars` | `THEME_MYSTERY` | binoculars (the magnifier is Search) |
| Romance | `heart` | `THEME_ROMANCE` |  |

## Code map

| Path or class | Role |
|---|---|
| `tools/fonts/icons.txt` | The semantic names (`Symbols.X`) and the Phosphor glyph of each |
| `tools/fonts/subset_fonts.py` | The generator; `--icons-only` rebuilds the icon font from the cached sources |
| `app/src/main/java/com/limelight/shell/ui/Symbols.kt` | The generated `Symbols` constants |
| `settingIcon()` in `app/src/main/java/com/limelight/shell/settings/SettingsCatalog.kt` | One entry for every catalog row of the Settings |
| `toastKindFor` in `app/src/main/java/com/limelight/shell/ui/ToastLogic.kt` | Reads the toast icon to choose the toast kind |
| `app/src/test/java/com/limelight/shell/settings/SettingIconsTest.kt` (`SettingIconsTest`) | Tests the Settings rows |

## Verification

| Test | What it enforces |
|---|---|
| `SettingIconsTest` | Every catalog row has its own entry in `settingIcon()`. No two rows of one tab share a glyph, except the named repeats in [Intentional repeats](#intentional-repeats). No row reuses the glyph of a tab pill. |

The other screens are checked by eye on a device with this list:

1. Settings: every tab (Stream, Display, Audio, Controls, Appearance, Covers, Devices, About): row glyphs read, tab
   pills differ from every row, Controls is the longest list (about 65 rows).
2. Per-app settings: the same rows on Stream/Display, the custom badge and reset glyphs.
3. Side menu rows and the status bar (haptics bell, battery bolt, wifi).
4. Home: the context menu (Resume, Quit, Quit and start, Artwork, Metadata, App settings, Hide), the game info chips
   (Running, HDR, SDR, unverified HDR, HLTB, Virtual display), genre and theme pills, bundled tiles (Desktop, Remote
   input, Remote monitor, Steam Big Picture) while their artwork loads.
5. Filter modal: four sections and the HDR chips; metadata and artwork screens (tabs, sheet rows, badges, loading).
6. Devices / Start / Pair PC / Profile screens, dialogs (PIN, passphrase, address, rename), toasts (wake, link test).
7. Stream menu: bar tiles, More page rows, Bitrate and Upscaler pages, Rumble presets, Mouse page.
8. Debug info: every section (device, haptics, rumble laboratory presets and patterns, advanced rows, controllers).
9. Stream result (failure face, Try again, Back to library) and the exit/connect portals.

### Not verified

- This document records no dated device run of the list above.

## Related

- [ICONS.md](ICONS.md): how icons are generated and how a glyph is added.

## Evidence (2026-11-10): glyph changes of the audit

Date: the day of the icon audit, as the old text of this document names it. The audit reviewed every row of the
[Inventory](#inventory); a row that is not in the tables below kept its glyph (`keep`).

### What the audit did not change

- No download was needed to add glyphs; nothing in the fonts was swapped for another family.
- Not changed: the controller letters (A/B/X/Y glyph buttons), the Delos logo and the battery body (custom drawing),
  the toast kind rules (`toastKindFor` still reads ERROR, WIFI_OFF, LINK_OFF, CHECK_CIRCLE, CHECK, WARNING).
- Layout, sizes, colors and motion are untouched. Only the glyph a call site names changed.

### Replaced and added glyphs

`Before` is the glyph before the audit (`Was`), `After` the glyph after it (`Now`). `none (new row)` marks an element
that had no glyph before. An empty `Reason` means the reason is the note of the row in the [Inventory](#inventory).

| Screen | Element | Before | After | Reason |
|---|---|---|---|---|
| Settings tabs | Appearance tab | `palette` | `paint-brush` | the palette glyph also stood for the Color palette row and the PyroWave chroma row; the tab now has the brush |
| Settings tabs | Devices tab | `desktop` | `desktop-tower` | the PC glyph of the device cards (monitor-only desktop is now the bundled Desktop app) |
| Settings > Stream | Resolution | `rectangle` | `four-k` | a plain rectangle was also the Wide cover tab; "4K" says resolution |
| Settings > Stream | Frame rate | `speedometer` | `film-strip` | the gauge was shared with Unlock FPS, the stream menu Fill row and Use recommended bitrate; film strip = frames |
| Settings > Stream | Unlock FPS limit | `speedometer` | `lock-open` |  |
| Settings > Stream | Custom refresh rate | `pencil-line` | `wave-sine` | sine wave = Hz (was a pencil) |
| Settings > Stream | Manual bitrate entry | `gauge` | `pencil-line` | typing a value, no longer the slider's glyph |
| Settings > Stream | Frame pacing | `timer` | `metronome` | metronome = even frame intervals; the timer now means latency only |
| Settings > Stream | Low-latency frame balance | `sliders-horizontal` | `scales` |  |
| Settings > Stream | Ultra low latency | `lightning` | `timer` |  |
| Settings > Stream | Video codec | `film-slate` | `file-video` | a video file format; the film slate stays on the Artwork "Type" row |
| Settings > Stream | PyroWave chroma | `palette` | `gradient` |  |
| Settings > Stream | Prevent packet loss | `heartbeat` | `broadcast` | broadcast (was a heartbeat, now the Debug heartbeat pattern) |
| Settings > Stream | Test link (guidance row) | `wifi-high` | `network` |  |
| Settings > Stream | Use recommended bitrate | `speedometer` | `thumbs-up` |  |
| Settings > Display | Sharpness | `magic-wand` | `aperture` | aperture = focus/sharpness; the magic wand is the Wizard avatar and Fantasy theme again |
| Settings > Display | Variable refresh rate | none (new row) | `wave-sine` |  |
| Settings > Display | HDR | `sun` | `sparkle` | sparkle everywhere HDR is meant (setting, native HDR badge, filter); was a sun on the setting and a sparkle on the badge |
| Settings > Display | Reduce refresh rate | `clock` | `trend-down` |  |
| Settings > Controls: gamepad | Gyro smoothing | `sliders-horizontal` | `bezier-curve` |  |
| Settings > Controls: gamepad | Joy-Con fix | `game-controller` | `wrench` |  |
| Settings > Controls: gamepad | Rumble feel | `vibrate` | `playlist` |  |
| Settings > Controls: gamepad | Emulate rumble with vibration | `vibrate` | `device-mobile-speaker` |  |
| Settings > Controls: gamepad | Force vibration on device | `vibrate` | `hand-fist` | it was the same Phosphor glyph as the plain vibrate; the fist = forced |
| Settings > Controls: gamepad | Vibration strength | `waveform` | `faders` |  |
| Settings > Controls: touch, mouse and trackpad | Absolute mouse mode | `cursor` | `gps-fix` |  |
| Settings > Controls: touch, mouse and trackpad | Mouse navigation buttons | `arrows-left-right` | `arrow-bend-double-up-left` |  |
| Settings > Controls: touch, mouse and trackpad | Touch sensitivity | `hand-tap` | `fingerprint-simple` | fingerprint; was the touchscreen-mode glyph (same as the stream menu row) |
| Settings > Controls: touch, mouse and trackpad | Auto touch sensitivity | `target` | `arrows-counter-clockwise` |  |
| Settings > Controls: touch, mouse and trackpad | Touch sensitivity X | `arrows-horizontal` | `arrows-in-line-horizontal` |  |
| Settings > Controls: touch, mouse and trackpad | Touch sensitivity Y | `arrows-vertical` | `arrows-in-line-vertical` |  |
| Settings > Controls: touch, mouse and trackpad | Touchpad sensitivity X | `arrows-horizontal` | `arrow-line-right` |  |
| Settings > Controls: touch, mouse and trackpad | Touchpad sensitivity Y | `arrows-vertical` | `arrow-line-up` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Force QWERTY | `grid-nine` | `text-t` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | On-screen controls vibration | `vibrate` | `wave-sawtooth` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Only show L3/R3 | `target` | `radio-button` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Show guide button | `house` | `disc` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Clear default special buttons | `key` | `broom` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Keyboard haptics | `vibrate` | `wave-square` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Square buttons | `square` | `selection` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Interface haptics | `vibrate` | `bell-ringing` |  |
| Settings > Controls: keyboard, clipboard, on-screen controls | Stream menu buttons | `game-controller` | `squares-four` |  |
| Settings > Appearance | Match the game's colors | `paint-brush` | `eyedropper` | eyedropper = picks colors from the game (was a brush, which is the tab now) |
| Settings > Appearance | Animated background | `sparkle` | `waves` | waves = flowing shader (sparkle moved to HDR) |
| Settings > Appearance | Game background | `image` | `panorama` |  |
| Settings > Appearance | Interface scale | `arrows-out` | `text-aa` |  |
| Settings > Appearance | Animations | `record` | `pinwheel` |  |
| Settings > Covers | Download automatically | `arrows-counter-clockwise` | `cloud-arrow-down` | cloud download = automatic; the counter-clockwise arrows are now Auto touch sensitivity |
| Settings > Covers | Choose artwork for one game | `images` | `image-square` |  |
| Settings > About | Version | `cube` | `package` |  |
| Settings > About | Game metadata · IGDB | `game-controller` | `database` |  |
| Settings > About | Technical data · PCGamingWiki | `sun` | `article` |  |
| Settings > About | Genre and theme tags · Steam | `tag` | `steam-logo` |  |
| Settings > About | Completion times · HowLongToBeat | `game-controller` | `clock-countdown` |  |
| Settings > About | Keep diagnostic logs | none (new row) | `notebook` |  |
| Debug info | Diagnostic log | none (new row) | `notebook` |  |
| Debug info | Share diagnostic log | none (new row) | `share-network` |  |
| Debug info | Copy diagnostic summary | none (new row) | `copy-simple` |  |
| Debug info | Clear diagnostic logs | none (new row) | `trash` |  |
| Debug info | Trigger test crash (debug builds) | none (new row) | `warning-octagon` |  |
| Debug info | Problem report card: Report this problem | none (new row) | `github-logo` |  |
| Debug info | Problem report card: Dismiss | none (new row) | `x` |  |
| Debug info | Android | `cube` | `android-logo` |  |
| Debug info | Device | `devices` | `device-tablet` |  |
| Debug info | Vibrators | `vibrate` | `list-numbers` |  |
| Debug info | Amplitude control | `sliders-horizontal` | `chart-line` |  |
| Debug info | Last problem | `info` | `warning` |  |
| Debug info | Vibrate once | `vibrate` | `device-mobile-speaker` |  |
| Debug info | Continuous vibration | `waveform` | `infinity` |  |
| Debug info | Strength | `sliders-horizontal` | `faders` |  |
| Debug info | Current preset | `vibrate` | `playlist` |  |
| Debug info | Preset Original | `vibrate` | `circle-dashed` |  |
| Debug info | Preset Soft | `vibrate` | `cloud` |  |
| Debug info | Preset Kick-start | `vibrate` | `boot` |  |
| Debug info | Preset Pulsing | `vibrate` | `wave-triangle` |  |
| Debug info | Live amplitude | `waveform` | `chart-bar-horizontal` |  |
| Debug info | Stop vibration | `vibrate` | `stop` |  |
| Debug info | Replay Test sample | `vibrate` | `arrow-counter-clockwise` |  |
| Debug info | Pattern Steady 25 % | `vibrate` | `engine` |  |
| Debug info | Pattern Steady 50 % | `vibrate` | `steering-wheel` |  |
| Debug info | Pattern Steady 75 % | `vibrate` | `jeep` |  |
| Debug info | Pattern Steady 100 % | `vibrate` | `rocket` |  |
| Debug info | Pattern Ramp up | `vibrate` | `trend-up` |  |
| Debug info | Pattern 30 ms taps | `vibrate` | `feather` |  |
| Debug info | Pattern 60 ms taps | `vibrate` | `crosshair-simple` |  |
| Debug info | Pattern 100 ms taps | `vibrate` | `hammer` |  |
| Debug info | Pattern Heartbeat | `vibrate` | `heartbeat` |  |
| Debug info | Pattern Explosion | `vibrate` | `bomb` |  |
| Debug info | Pattern Flutter | `vibrate` | `fan` |  |
| Debug info | Advanced | `sliders-horizontal` | `gear-fine` |  |
| Debug info | Enable rumble translator | `vibrate` | `function` |  |
| Debug info | Gain | `sliders-horizontal` | `plus-minus` |  |
| Debug info | Curve | `sliders-horizontal` | `chart-line-up` |  |
| Debug info | Minimum amplitude | `sliders-horizontal` | `arrow-line-down` |  |
| Debug info | Start kick | `sliders-horizontal` | `boot` |  |
| Debug info | Low motor weight | `sliders-horizontal` | `arrow-fat-down` |  |
| Debug info | High motor weight | `sliders-horizontal` | `arrow-fat-up` |  |
| Debug info | Smoothing | `sliders-horizontal` | `bezier-curve` |  |
| Debug info | Reset parameters | `sliders-horizontal` | `arrow-u-up-left` |  |
| Debug info | Continuous vibration on controllers | `waveform` | `repeat` |  |
| Header, side menu, nav bar and button hints | Side menu Haptics | `vibrate` | `bell-ringing` |  |
| Start, Devices, Pair PC and Profile | Device menu: Wake the PC | `power` | `alarm` | an alarm clock; the power glyph is Quit and was the same glyph |
| Start, Devices, Pair PC and Profile | Pair: Enter address | `plus` | `map-pin-plus` |  |
| Start, Devices, Pair PC and Profile | Address dialog field | `network` | `map-pin` |  |
| Start, Devices, Pair PC and Profile | Home: PC offline | `power` | `wifi-slash` |  |
| Start, Devices, Pair PC and Profile | Home: loading | `arrows-clockwise` | `spinner-gap` |  |
| Home: tiles, context menu and game info | Bundled Desktop tile | `desktop-tower` | `desktop` | the desktop (was the PC glyph) |
| Home: tiles, context menu and game info | Bundled Remote input tile | `game-controller` | `mouse-simple` |  |
| Home: tiles, context menu and game info | Bundled Remote monitor tile | `game-controller` | `television-simple` |  |
| Home: tiles, context menu and game info | Bundled Steam Big Picture tile | `game-controller` | `steam-logo` |  |
| Home: tiles, context menu and game info | Running section | `play` | `pulse` |  |
| Home: tiles, context menu and game info | Context: Quit and start | `arrows-left-right` | `swap` |  |
| Home: tiles, context menu and game info | Context: Metadata | `magnifying-glass` | `file-magnifying-glass` |  |
| Home: tiles, context menu and game info | Info chip: Running on PC | `play` | `pulse` |  |
| Home: tiles, context menu and game info | Info chip: SDR | `sun` | `sun-dim` |  |
| Home: tiles, context menu and game info | Info chip: unverified HDR | `question` | `seal-question` |  |
| Home: tiles, context menu and game info | Info chip: HLTB | `clock-counter-clockwise` | `clock-countdown` |  |
| Home: tiles, context menu and game info | Genre chips | 23 glyphs | see [Genres (IGDB)](#genres-igdb) | see Genres |
| Home: tiles, context menu and game info | Theme chips | 21 glyphs | see [Themes (IGDB)](#themes-igdb) | see Themes |
| Metadata and artwork | Metadata: Reset match | `arrows-clockwise` | `arrow-u-up-left` |  |
| Metadata and artwork | Metadata: Reset library filters | `arrows-clockwise` | `arrow-u-up-left` |  |
| Metadata and artwork | Metadata: Previous matches | `magnifying-glass` | `arrow-left` |  |
| Metadata and artwork | Metadata: More matches | `magnifying-glass` | `arrow-right` |  |
| Metadata and artwork | Metadata: Search for another name | `pencil-simple` | `list-magnifying-glass` |  |
| Metadata and artwork | Artwork tab Logo | `identification-badge` | `delos-wordmark-plate` |  |
| Metadata and artwork | Artwork: Search for another name | `magnifying-glass` | `list-magnifying-glass` |  |
| Metadata and artwork | Artwork: Filter / Change filters | `sliders-horizontal` | `funnel` |  |
| Metadata and artwork | Artwork: Reset filters / Reset chip | `arrows-clockwise` | `arrow-u-up-left` |  |
| Metadata and artwork | Artwork sheet: Style | `palette` | `paint-roller` |  |
| Metadata and artwork | Artwork sheet: Dimensions | `rectangle` | `bounding-box` |  |
| Metadata and artwork | Artwork sheet: Sort | `star` | `sort-ascending` |  |
| Metadata and artwork | Artwork: Animated badge | `play` | `play-circle` |  |
| Metadata and artwork | Artwork: loading | `arrows-clockwise` | `spinner-gap` |  |
| Filter modal | Section Genre | `game-controller` | `tag` |  |
| Filter modal | Section Theme | `mask-happy` | `shapes` |  |
| Filter modal | Section HDR support | `sun` | `sparkle` |  |
| Filter modal | Section Duration | `clock-counter-clockwise` | `clock-countdown` |  |
| Filter modal | Chip HDR / SDR / Unknown | `sparkle` / `sun` / `question` | `sparkle` / `sun-dim` / `seal-question` |  |
| Filter modal | Reset | `arrows-clockwise` | `arrow-u-up-left` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Search keyboard: Delete | `arrow-left` | `backspace` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Search keyboard: Clear | `x` | `x-circle` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Stream result: Try again | `arrow-clockwise` | `arrows-clockwise` | same retry/refresh arrows (RESTART_ALT removed) |
| Dialogs, search keyboard, stream result, portals, toasts | Stream result: Back to library | `images` | `books` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Portal: wake | `power` | `alarm` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Portal: link test | `gauge` | `network` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Toast: reset | `arrow-clockwise` / `arrows-clockwise` | `arrow-u-up-left` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Toast: wake sent | `power` | `alarm` |  |
| Dialogs, search keyboard, stream result, portals, toasts | Impact chip GPU | `cpu` | `graphics-card` | reverted to `cpu` by the owner's decision of 2026-11-20 (SoC devices, no graphics card) |
| Stream menu bar and pages | Bar: Brightness | `sun-dim` | `sun` |  |
| Stream menu bar and pages | More: Send clipboard | `upload-simple` | `paper-plane-tilt` |  |
| Stream menu bar and pages | More: Get clipboard | `download-simple` | `tray-arrow-down` |  |
| Stream menu bar and pages | More: Android keyboard | `keyboard` | `cursor-text` |  |
| Stream menu bar and pages | More: Touch sensitivity | `hand-tap` | `fingerprint-simple` |  |
| Stream menu bar and pages | More: Gyro smoothing | `sliders-horizontal` | `bezier-curve` |  |
| Stream menu bar and pages | Page: Screen brightness | `sun-dim` | `sun` |  |
| Stream menu bar and pages | Page: Fill recommended | `speedometer` | `thumbs-up` |  |
| Stream menu bar and pages | Page: Sharpness | `magic-wand` | `aperture` |  |
| Stream menu bar and pages | Page: rumble presets | `vibrate` | `PRESET_*` |  |
| Profile avatars | Spark | `lightning` | `lightbulb-filament` |  |
| Profile avatars | Robot | `cpu` | `robot` |  |
| Profile avatars | Cat | `paw-print` | `cat` | was the same paw as Fox |
| Profile avatars | Wizard | `star` | `magic-wand` |  |
| Profile avatars | Ghost | `smiley` | `ghost` |  |
| Profile avatars | Owl | none (new row) | `paw-print` |  |
| Profile avatars | Hacker | none (new row) | `terminal-window` |  |
| Profile avatars | Wolf | none (new row) | `paw-print` |  |
| Profile avatars | Dragon | none (new row) | `paw-print` |  |
| Genres (IGDB) | Music | `music-notes` | `piano-keys` |  |
| Genres (IGDB) | Real-time strategy | `timer` | `clock-clockwise` |  |
| Genres (IGDB) | Turn-based strategy | `hourglass` | `calendar-check` |  |
| Genres (IGDB) | Hack and slash | `sword` | `axe` |  |
| Genres (IGDB) | Quiz | `question` | `exam` |  |
| Genres (IGDB) | Adventure | `compass` | `signpost` |  |
| Genres (IGDB) | Indie | `lightbulb` | `guitar` |  |
| Genres (IGDB) | Arcade | `ghost` | `coin` |  |
| Themes (IGDB) | Fantasy | `magic-wand` | `crown` | crown (the wand is Sharpness and the Wizard avatar) |
| Themes (IGDB) | Thriller | `lightning` | `siren` |  |
| Themes (IGDB) | Stealth | `eye-slash` | `detective` |  |
| Themes (IGDB) | Comedy | `smiley` | `smiley-wink` |  |
| Themes (IGDB) | Drama | `mask-happy` | `mask-sad` | sad mask (the happy mask was also this theme's section glyph) |
| Themes (IGDB) | Warfare | `bomb` | `medal-military` |  |
| Themes (IGDB) | Mystery | `magnifying-glass` | `binoculars` |  |

### Kept glyphs that lost other uses

| Screen | Element | Glyph | Finding |
|---|---|---|---|
| Settings tabs | Audio tab | `waveform` | the waveform was also used by two vibration rows; those moved |
| Settings tabs | Controls tab | `game-controller` | was also on Joy-Con fix, Stream menu buttons and two About rows |
| Settings tabs | Covers tab | `images` | artwork; "Choose artwork for one game" and "Back to library" no longer reuse it |
| Settings > Display | Resolution scale | `arrows-out` | the interface scale had the same glyph |
| Settings > Audio | Interface sounds | `music-note` | the Music genre moved to piano keys |
| Settings > Controls: gamepad | Stick deadzone | `target` | the target also stood for L3/R3 and auto touch sensitivity |
| Settings > Controls: gamepad | Motion sensors | `compass` | the compass also stood for the Adventure genre |
| Settings > Controls: touch, mouse and trackpad | Trackpad drag-drop threshold | `hourglass` | the hourglass also stood for Turn-based strategy |
| Settings > Controls: keyboard, clipboard, on-screen controls | Right Alt as Meta | `command` | the row is labeled "Right Alt as Command (Mac)", so the Command glyph is the right one (the same glyph names the stream menu "Send keys" row, a different screen). The Windows logo glyph was tried and reverted |
| Settings > Appearance | Color palette | `palette` | the user's choice; the tab and the chroma row gave theirs up |
| Header, side menu, nav bar and button hints | Status bar charging bolt | `lightning` | the bolt now means charging only |
| Profile avatars | Racer | `speedometer` | the gauge no longer stands for anything else |
| Themes (IGDB) | Sandbox | `cube` | cube (Version moved to package) |
