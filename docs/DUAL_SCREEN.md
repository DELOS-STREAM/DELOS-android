# Dual-screen and second displays

## Purpose

What Delos knows about the displays of a device and what it does with a second panel that is part of the device.
This document specifies the display inventory and classification (`DisplayInventory`, `DisplayClassifier`), the
companion window on a built-in second panel (activity host, lifecycle, the Main screen setting), what the
companion shows, and how to test it.

## Scope

Two different things must never be mixed up:

| Term | What it is | Examples | Specified in |
|---|---|---|---|
| External display | A monitor, a TV or a wireless display, attached for a while. The external-display mode streams to it. | The Odin attached to a TV over USB-C | [EXTERNAL_DISPLAY.md](EXTERNAL_DISPLAY.md) |
| Built-in companion | A second panel that is part of the device and always there. The external-display mode must never choose it. | The bottom panel of the AYN Thor, the top panel of the Ayaneo Pocket DS | This document |

| Covered here | Specified elsewhere |
|---|---|
| Display roles, classification rules, the Second display override | Streaming to an attached monitor or TV: [EXTERNAL_DISPLAY.md](EXTERNAL_DISPLAY.md) |
| Which panel is main, where the shell and the stream open | The shell's screens on the main panel: [GUI.md](GUI.md) |
| The companion window, its pages, the settings help, burn-in | The diagnostic log: [DIAGNOSTICS.md](DIAGNOSTICS.md) |
| Test procedures on the emulator | Dual-screen handhelds: [HANDHELD_DEVICES.md](HANDHELD_DEVICES.md) |

## 1. Display roles

`DisplayInventory.list(context)` returns every display `DisplayManager.getDisplays()` shows, lowest id first, each
with a `DisplayRole`:

| Role | Meaning | May be the stream target of the external-display mode |
|---|---|---|
| `MAIN` | the default display, id 0 | no (it is the device's own screen) |
| `BUILT_IN_COMPANION` | a second panel of the device | **no** |
| `EXTERNAL` | monitor, TV, wireless display | **yes** (the only role) |
| `VIRTUAL` | overlay display, app-created virtual display, private display | **no** |

1. `ServerHelper.getExternalDisplay` is `DisplayInventory.external`: the first display with the role `EXTERNAL`.
2. `getSecondaryDisplay`, which returned the first display with an id other than 0, does not exist.
3. `getActiveDisplay`, `createStartIntent`, the display listener of `DelosStreamActivity` and
   `ExternalDisplayControlActivity` all go through `ServerHelper.getExternalDisplay`.
4. With a real monitor or TV the behavior is not changed by the classification.

## 2. Classification rules

`DisplayClassifier.classify` is a pure function of `DisplayFacts` (read from the public `Display` API) and the
user's override ([Second display override](#3-second-display-override)). `DisplayClassifierTest` is the table.

### Rule order

For a display that is not the default one, the first rule that applies wins:

| # | Condition | Result |
|---|---|---|
| 1 | Override "Part of the device" | `BUILT_IN_COMPANION`, whatever the display is |
| 2 | `FLAG_PRIVATE` | `VIRTUAL` |
| 3 | The `type` field of `Display.toString()` is OVERLAY or VIRTUAL | `VIRTUAL` |
| 3 | The `type` field of `Display.toString()` is WIFI or EXTERNAL (HDMI on Android 10) | `EXTERNAL` |
| 3 | The `type` field of `Display.toString()` is INTERNAL (BUILT_IN on Android 10) | `BUILT_IN_COMPANION` |
| 4 | The product info (`Display.getDeviceProductInfo().getConnectionToSinkType()`, **API 31+**, not 34) is BUILT_IN | `BUILT_IN_COMPANION` |
| 4 | The product info is DIRECT or TRANSITIVE | `EXTERNAL` |
| 4 | The product info is UNKNOWN, or there is no product info | says nothing; the next rule decides |
| 5 | The name contains "overlay" or "virtual" (English only) | `VIRTUAL` |
| 6 | No `FLAG_PRESENTATION` | `BUILT_IN_COMPANION` |
| 7 | Otherwise | `EXTERNAL` |

With the override "External display":

- a `BUILT_IN_COMPANION` result becomes `EXTERNAL`;
- a `VIRTUAL` display stays `VIRTUAL`. The override can never make an overlay, a virtual or a private display a
  stream target.

The default is built so that **anything not clearly built-in and not clearly virtual is external**. The reasons:

- the external-display mode was accepted on a real monitor and TV;
- a wrong "external" on a panel is cured by the override;
- a wrong "companion" on a monitor would break the feature without a visible reason.

### What is known, what is a guess

| Signal | Status |
|---|---|
| An internal local display gets **no** `FLAG_PRESENTATION`; a non-internal one always does; so do overlay and wireless displays. A private virtual display and the OEM-configured private ports get `FLAG_PRIVATE`. | **Known**: AOSP `LocalDisplayAdapter` (Android 13 source) and the emulator ([Evidence (2026-11-11): emulator display dumps](#evidence-2026-11-11-emulator-display-dumps)). |
| Names: "Built-in Screen" for **every** internal display, "HDMI Screen" for external ones, "Overlay #N". | **Known, but locale-bound**: the system builds them from string resources, translated into the system language ("Integrierter Bildschirm", "Překryvná vrstva č. 1"). A name can prove "virtual" only in English and never proves "built-in", so it decides nothing else. A real monitor's name is not its EDID name. |
| `type` in `Display.toString()` (`type INTERNAL`, `type EXTERNAL`, `type OVERLAY`, ...). | **Best effort, absent on the API 33 emulator**: debug text of `DisplayInfo`, not API. The AOSP sources of Android 10, 12 and 14 print `, type <NAME>` there, but the text an app gets from `Display.toString()` on the emulator stops at `committedState` (observed), so the hint is "unknown" there and the other rules decide. `dumpsys display` shows the type, which is how the rules were checked. Debug info shows what the device returns ("type text"). |
| `getDeviceProductInfo()` / connection to sink | **Known to exist from API 31**; what a given panel or monitor reports was **not seen on a real device**. The emulator's internal display has product info with sink type UNKNOWN (0), an overlay display has none. |
| Display `FLAG_SECURE`, `FLAG_ROUND`, size, density, refresh rate, state | Listed in Debug info, **not used** as evidence (no size separates a small panel from a small monitor; a closed lid is a state). |
| How the AYN Thor, the Ayaneo Pocket DS and the Retroid add-on report their second panel; whether a vendor builds it as a normal internal display | **Unknown**: no such device was available. The rules assume AOSP behavior; the override and the Debug info listing are the way to correct and to report it. |
| What the Odin reports for a monitor on USB-C | **Not seen** (no ADB to the Odin). AOSP gives it `type EXTERNAL`, `FLAG_PRESENTATION` and EDID product info, which all three rules classify as `EXTERNAL`. Confirm once with a monitor attached: Debug info > Displays must show it as External. |

## 3. Second display override

Settings > Display > **Second display** overrides the classification. The key is in [Settings](#settings).

| Entry | Effect |
|---|---|
| Automatic (default) | the rules of [Classification rules](#2-classification-rules) |
| External display | every display other than the default one that is not virtual counts as external |
| Part of the device | every display other than the default one counts as a built-in companion (an overlay too, so it can stand in for a second panel when testing) |

## 4. Where the roles are shown

| Place | Content |
|---|---|
| **Settings > About > Debug info > Displays** | One row per display with its name and the role as the value. The panel on the right shows the id, size, density, refresh rate, state, flags, the two raw signals (the type text and the product info sink type) and the reason for the role. The list follows displays that come and go. |
| **Diagnostic log** (tag `Display`) | One line per display at process start and one for every display added, changed (only when the line differs) or removed. It enters every shared report. |

An example of a diagnostic log line, as written on the emulator:
`Display present: id 4 "Overlay #1" 1240x1080 420 dpi 60.0 Hz, state ON, flags PRESENTATION: VIRTUAL (the name says overlay or virtual)`

Privacy of the log line:

- A wireless display's name (a name somebody gave it) is replaced by "wireless display".
- The unique id of a display is never read.

## 5. Main panel and companion panel

On a device with a display classified `BUILT_IN_COMPANION` the two panels have different jobs: the **main** panel
shows the shell and the stream, the other one shows the **companion screen**.

With no such display (every single-screen device, a TV or monitor, an overlay in Automatic or External display
mode) none of this exists and nothing behaves differently:

- `DualScreen.plan` returns null;
- `placeOnMain` leaves the launch options alone;
- `mainDisplayId` is the default display.

### Which panel is main

The setting is Settings > Display > Main screen, key `display_main_screen` ([Settings](#settings)).
`DualScreenPlanner.plan` (pure, `DualScreenPlannerTest`) takes the display list and the setting and returns the
pair of panels.

1. The two panels are the default display (role `MAIN`) and the built-in companion (the lowest id if there are
   several). External and virtual displays are never part of the pair.
2. The setting selects the main panel:

| Entry | Main panel |
|---|---|
| **Automatic** | the panel with more pixels; equal panels: the default display (where the launcher put the app) |
| **Swapped** | the other panel |

3. The row exists only while the device has a companion display (`rememberDualScreenPlan`, which follows displays
   being added or removed and both display settings). It appears when an overlay display is switched on and set
   to "Part of the device", and goes when it is switched off.
4. Debug info > Displays adds a `panel: main` / `panel: companion` line to the display's details.

### Where the shell and the stream open

Stream (`Shell.kt` launch, `StreamExit`, `StreamResult`):

1. The stream's `ActivityOptions` get `setLaunchDisplayId(main)` through `DualScreen.placeOnMain`, and so does the
   shell when a stream ends.
2. `ServerHelper.getActiveDisplay` (modes, refresh rate) answers with the main panel instead of display 0.
3. A stream in the external-display mode is unchanged: it goes to the external display, and its end brings the
   shell back to the main panel.

Shell (`ShellPlacement`):

1. The launcher opens the shell on the display the user tapped.
2. If that is the companion panel, the shell starts itself again on the main panel (new task there,
   `EXTRA_PLACED` stops a second move) and ends. A link or stream result in the intent travels along.
3. While it runs, the shell watches the displays and the two display settings. Changing Main screen (or a display
   appearing) moves it, and the new shell opens on the Display tab of the settings, where the user was.
4. A launch that the display refuses is not repeated within 3 s.
5. A shell on neither panel (an external monitor, DeX) is left alone.

## 6. Companion window

The code is in `shell/companion/`.

### Host and task

1. The companion is a `CompanionActivity` on the panel that is **not** the owner's
   (`PanelPair.otherThan(ownDisplay)`). The value is null, so nothing is shown, when the owner is on an external
   monitor: the external-display mode is unchanged.
2. `CompanionHost` starts it for the running `ShellActivity` (`CompanionState.Shell(pcName)`) or
   `DelosStreamActivity` (`CompanionState.Stream`, built by `CompanionHost.forStream`).
3. It has a task of its own (`taskAffinity=""`, `FLAG_ACTIVITY_NEW_TASK | MULTIPLE_TASK`, `setLaunchDisplayId`),
   `excludeFromRecents` and no window animations.
4. It never starts without an owner: `CompanionSession` holds the owner and its content. An activity that finds
   no owner (the process was restarted with the task) or finds itself on another display than it was started for
   finishes at once.

### Why not a `Presentation`

A `Presentation` is not used. A design with a `Presentation` was built and tested, then removed.

1. WindowManager only takes a `TYPE_PRESENTATION` window on a display with `FLAG_PRESENTATION` (AOSP
   `WindowManagerService.addWindow` needs `Display.isPublicPresentation()`). The observed refusal is in
   [Evidence (2026-11-11): companion window on the emulator](#evidence-2026-11-11-companion-window-on-the-emulator).
2. The default display never has the flag, so a Presentation could never serve "Swapped".
3. An internal local display gets none by AOSP rule, so probably not the Thor's bottom panel either.
4. An activity can be launched on any display (checked on display 0 and on two kinds of second display:
   [Evidence (2026-11-11): companion window on the emulator](#evidence-2026-11-11-companion-window-on-the-emulator)).

### Lifecycle

| Event | What happens |
|---|---|
| owner `onResume` | `start()`: attach to `CompanionSession`, register the display and preference listeners, start the window if there is a companion display |
| display added / removed / changed, Second display or Main screen changed | reconcile: start when a target appeared, finish when it went, finish and start on the other panel when the target changed; nothing for events that change neither |
| display removed while shown | the system moves the task to the default display; the activity sees the configuration change, finds itself off its target and finishes (`CompanionActivity.closeUnlessOnTarget`), the host's reconcile finishes it too |
| owner `onStop` / `onDestroy` | `stop()`: unregister, detach; the window finishes unless the next owner has already attached. Not `onPause`: when the shell hands over to the stream the stream resumes (and attaches) before the shell stops, so the same window stays and only its content changes |
| launch refused or landed on the wrong display | the activity closes itself and the display is remembered as refused until an owner attaches again; no retry loop |
| configuration change | the activity handles orientation, screen size and the other configuration changes in place |

The configuration changes handled in place are declared as `orientation|screenSize|...`.

### Key focus

1. The window is `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_FULLSCREEN`, so it never takes key focus and
   touches still reach it. `dumpsys input` lists it as a touchable window of its display;
   `input -d <display> tap` is dispatched without an ANR.
2. NOT_FOCUSABLE alone is **not enough**. Starting an activity on the other display makes that display the *top
   focused display* (`mTopFocusedDisplayId`). It has no focused window, and controller and keyboard keys are
   dropped.
3. `moveTaskToFront` of the owner does not help: the owner is already the front task of its display, so nothing
   changes.
4. What works: once the companion has resumed, the owner is started again with
   `FLAG_ACTIVITY_NEW_TASK | REORDER_TO_FRONT` (`CompanionSession.returnFocusToOwner`, 250 ms after the
   companion's `onResume`). This delivers an intent to the running instance and makes its display the focused one
   again.
5. `ShellActivity.onNewIntent` handles a plain intent as nothing. `DelosStreamActivity` has no `onNewIntent`, so
   the stream ignores it.
6. The keyboard of the companion needs no key focus: it injects key events, it takes none.
7. If a real touch moves the top focus to the companion's display, the fallback is that the companion forwards
   keys to the owner. Whether it does is not verified ([Not verified](#not-verified)).

The observations behind items 2 to 4 are in
[Evidence (2026-11-11): companion window on the emulator](#evidence-2026-11-11-companion-window-on-the-emulator).

### Composition

1. Compose runs in the activity with its own Stage, so `LocalStage` is planned from **the companion display's**
   size and density. The Thor's 1240 x 1080 panel at 420 dpi is a Compact stage while the main panel keeps
   1920 x 1080.
2. `CompanionTheme` applies the profile's colors and reduced-motion setting.
3. The system bars are hidden through `FLAG_FULLSCREEN` and the legacy visibility flags, because only a focused
   window can hide them through the insets controller.
4. `CompanionContent(state)` is the one place the content is decided ([Companion content](#7-companion-content)).

## 7. Companion content

Issue [#76](https://github.com/Meitoncz/Delos/issues/76): useful extra content on EVERY screen, never scrolling.

The rule behind the design: *the second display is EXTRA room for what helps the main screen, never a place for
the main screens*.

1. The companion shows the focused item's details, an explanation, or a few touch buttons that run the
   controller's own paths.
2. **There is always something**: a screen without content of its own gets the fallback status card, never an
   empty "choose a game" card.

### No scrolling, ever

Rule 0: the companion never needs scrolling.

1. Every state fits completely in its panel, at every companion panel size.
2. There is no scroll container, scroll bar, edge fade or auto-scroll anywhere in the companion. No scrolling
   help card exists.
3. What does not fit is **dropped by priority, shortened at a sentence boundary, or counted** ("+3 genres").
4. A visual shrinks in ratio: artwork keeps its own aspect and is never cropped.

| Companion panel sizes |
|---|
| 1240 x 1080 @ 420 |
| 960 x 720 @ 300 |
| 1024 x 768 @ 320 |
| 720 x 720 @ 255 |
| 960 x 640 @ 330 |

| Element | Minimum |
|---|---|
| Text | at least 28 stage px |
| Touch targets | at least 64 stage px |
| Margins | 48 px |

Two layers enforce the rule.

**`CompanionFit`** (pure, `CompanionFitTest` with worst-case data on all five shapes) holds the budgets of a panel
(`FitBudget`). A tall panel has 900 stage px of height and more *after dividing by the interface scale*.

| Budget | Tall panel | Short panel |
|---|---|---|
| Body lines | 5 | 3 |
| Help lines | 6 | 3 |
| Genres | 4 | 2 |
| Hints | 5 | 2 |
| Facts | 6 | 4 |
| Error lines | 5 | 2 |
| Source block | 2 lines | 2 lines |
| Recovery sentence | 2 lines | 2 lines |

The short-panel budgets in the order of the table are 3 / 3 / 2 / 2 / 4 / 2.

| Function | Result |
|---|---|
| `summary` | the longest run of **complete leading sentences** that fits the line budget, else the authored short form, else "See the main screen for details." |
| `chips` | first N and a count, one row |
| `top` | no description recorded |
| `title`/`alias` | a name that does not fit one line becomes "PC · 7C2A" |

Text is measured with the real fonts (`rememberFit`), so a longer translation gets fewer sentences.

**`FitColumn`** (`CompanionLayout.kt`) is the net under the budgets:

1. A page's blocks are measured.
2. The optional ones (`Modifier.optional(priority)`) with the lowest priority are dropped while the sum is taller
   than the card.
3. A `Filler` or a `fills` block (hero, artwork) takes what is left.
4. A page can never overflow, whatever the language, the interface scale or the panel.

The panel is the companion's own stage (`LocalStage`, planned from its display) divided by the interface scale
(text grows with it). 1240 x 1080 at 420 dpi is a 1099 x 957 stage, at the default 115 % a 956 x 832 panel for
the fit, i.e. a *short* one.

## 8. Companion pages

### Page choice

1. The shell's companion window shows ONE page. `CompanionState.Shell(pcName, published, context)` carries what
   the shell's screens have published.
2. `chooseShellPage` takes the page of the highest `PagePriority`.
3. With nothing published it takes the **welcome card** (no PC is paired) or the **fallback card**.
4. Pages are immutable data with the callbacks of their touch buttons (owner side, the same code paths as the
   controller).
5. A page draws itself (`CompanionPage.Content`).
6. A new key crossfades (`Motion.FADE`, snapped with reduced motion). The same key is an update (a value, a
   clock) and does not crossfade.

| `PagePriority` | Value | Pages |
|---|---|---|
| overlay | 300 | launch, portal, result, problem, report |
| modal | 200 | the filter sheets |
| screen | 100 | the focused item of the screen and the settings help |
| game | 50 | Home's focused game |

### Pages by screen

| Screen on the main panel | Page | Specified in |
|---|---|---|
| **Start**, **Devices** (focused PC circle) | `PcPage` | [PC and library pages](#pc-and-library-pages) |
| **Pair a PC** | `PairHostPage`, `PairInfoPage` | [PC and library pages](#pc-and-library-pages) |
| **Pairing** (`Screen.Pin`) | `PinPage`, `CodePage`, `PairResultPage` | [PC and library pages](#pc-and-library-pages) |
| **Home** with games | `GamePage` | [The game card](#the-game-card) |
| **Home** without games | `PcPage`, `LibraryNoticePage` | [PC and library pages](#pc-and-library-pages) |
| **Artwork** | `ArtworkPage` | [Content pages](#content-pages) |
| **Library filters**, **Artwork filters** (sheets) | `FilterPage` | [Content pages](#content-pages) |
| **Profile**, **Create profile**, **Boot** | `ProfilePage`, `CreateProfilePage`, `BootPage` | [Content pages](#content-pages) |
| **Launch** (portal, `shell.launch`), **stream start** | `LoaderPage`, `PortalPage` | [Launch and connect](#launch-and-connect) |
| **Stream result**, **problem**, **report card** | `ResultPage`, `ProblemPage`, `ReportPage` | [Launch and connect](#launch-and-connect) |
| **Settings**, **a game's settings**, **Debug info** (focused row) | `SettingsHelpPage` | [Settings help](#9-settings-help) |
| **Any other screen** (Settings search, the on-screen controls preview, and every route added later) | `FallbackPage` | [The fallback](#the-fallback) |
| **No PC paired yet** | `WelcomePage` | [The fallback](#the-fallback) |
| **Stream** | `StreamContent`, `TypingContent` | [State in, commands out](#10-state-in-commands-out) |

Every page shows data from the screen's own state.

`contentKind(screen)`, `screenTitle` and `screenPurpose` are exhaustive `when`s over `Screen`: a route added to
the shell does not compile until it is placed. `ContentKind.FALLBACK` is a valid place (`CompanionRoutesTest`),
so a new screen cannot silently leave the companion empty.

### PC and library pages

`PcPage` (**Start**, **Devices**, focused PC circle) shows:

| Fact | Detail |
|---|---|
| Name | the PC's name (or its alias) |
| Status | online / asleep and wakes up / offline |
| Flags | connected and default |
| Host software | derived: GeForce Experience, Vibepollo when it advertises PyroWave, else Sunshine-based |
| Version and last seen | "unavailable": the app does not store them |
| Host name | shown when the user renamed the PC |
| Running game | the running game |
| Number of apps | cached ones for an offline PC |
| Saved MAC | yes/no |

The card shows 6 tall / 4 short facts; each unknown one carries its own label.

| `PcPage` button | Condition and action |
|---|---|
| Library | connect, primary |
| Wake PC | offline/asleep with a saved MAC, `DeviceActions.wake` |
| Search | no MAC: `rescan`, and the sentence that says why |
| Rename | no condition |
| Set/Clear default | no condition |
| Unpair | Devices, online only |
| Remove PC | below a rule; immediate, no confirmation |

Library, Wake PC and Search are alternatives. The line "Main: A Connect · N more hints" is built from the main
hint bar.

| Screen | Page | Content and buttons |
|---|---|---|
| **Pair a PC** | `PairHostPage`, `PairInfoPage` | The focused host (discovered / paired; software only when known) with Pair with PIN (`actions.pair`) and Use code (`pairWithCode`). The address and search circles explain themselves (Search again runs `rescan`). |
| **Pairing** (`Screen.Pin`) | `PinPage` | The PIN in very large digits (scaled to the box, never wrapped) with the instruction and the PC's name, readable across a desk; Cancel pairing. |
| **Pairing** (`Screen.Pin`) | `CodePage` | The one-time-code flow shows the instruction and "the companion never repeats the passphrase" (the passphrase is never published). |
| **Pairing** (`Screen.Pin`) | `PairResultPage` | After the PC accepted: Pairing complete with Open library / Back to hosts (visible for the 900 ms the screen stays). |
| **Home** without games, offline library | `PcPage` | The PC card with Wake or Search and Rename. |
| **Home** without games, loading | `LibraryNoticePage` | "Reading the library" with Back to PCs. |
| **Home** without games, no PC | `LibraryNoticePage` | no PC |
| **Home** without games, an empty library | `LibraryNoticePage` | "No applications" with Reconnect PC and PC details. A filtered-out library is the filter page's. |

### The game card

`GamePage` (**Home** with games) shows the focused game:

| Block | Content |
|---|---|
| Banner | hero, else cover, else placeholder hues |
| Name | the game's name |
| Genres | 4 tall / 2 short and a count |
| Metadata | HLTB and native HDR |
| Impact | the three impact chips |
| Buttons | Start / Resume / Settings (or Resume that game, Quit and start, Settings) |

### Content pages

| Screen | Page | Content and buttons |
|---|---|---|
| **Artwork** | `ArtworkPage` | The focused image large at its own ratio (the thumbnail the tile already decoded, never cropped) with size · type, score, author, added, In use and Use this image (the tile's own `apply`). The first choice shows its source lines, built-in artwork the wide image. |
| **Library filters**, **Artwork filters** (sheets) | `FilterPage` | The active filters as up to 3 chips and "+N", the count of matching items, "Change criteria on the main display." and Reset filters (primary, only while filters are active; the sheet's own reset, that tab only; the sheet stays open). |
| **Profile** | `ProfilePage` | Avatar, name, custom settings and per-app overrides (counted once per profile, off the main thread), default PC. |
| **Create profile** | `CreateProfilePage` | The live draft ("not saved"). |
| **Boot** | `BootPage` | The explanation of the first-run step with its real hints (B only when Boot can go back). |

`ProfilePage`, `CreateProfilePage` and `BootPage` are read-only; edits stay on the main display.

### Launch and connect

`LoaderPage` and `PortalPage` (**Launch**: portal, `shell.launch`; **stream start**):

1. They show "Starting <game>", the PC and the requested picture.
2. They show named steps with done / current / pending marks: Host found, Launch request accepted, Waiting for
   video, Stream ready. There is no invented percentage.
3. Short panels fold trailing steps into "+1 pending".
4. Cancel is shown only while cancellation really exists.
5. The loader never dims (`keepsAwake`).
6. The loader has the same key in the shell and in the stream activity (`CompanionHost.forStream`:
   `onLaunchAccepted` / `onConnectionStarted` / `onLoaderDone`), so the hand-over is an update, not a fade.

`ResultPage`, `ProblemPage` and `ReportPage` (**Stream result**, **problem**, **report card**) show:

1. the result's title and sentence cut at a sentence boundary (the page never claims the game stopped);
2. the first line of the details (stage, error code);
3. the recovery sentence;
4. Reconnect / Try again and Back (the very callbacks of `StreamResultLayer`);
5. Report / Dismiss of the report card.

### The fallback

`FallbackPage` (**Any other screen**: Settings search, the on-screen controls preview, and every route added
later) shows:

1. the screen's title and one-line purpose;
2. the connected PC with its status and running game;
3. the clock and the battery;
4. what the controller does here: the entries of the **very list the main hint bar draws**
   (`ShellState.companionContext`, published by `PublishCompanionContext` from `Shell`; 5 tall / 2 short, then
   "More hints on the main display: N").

`WelcomePage` (**No PC paired yet**) shows a short welcome and the one thing to do.

### Stream pages

`StreamContent` and `TypingContent` (**Stream**) show statistics, the nine stream-menu tiles and their pages, and
the keyboard ([State in, commands out](#10-state-in-commands-out)).

## 9. Settings help

`SettingsHelpPage` (`CompanionHelpContent.kt`) serves Settings, a game's settings and Debug info. The three
settings screens publish it with `PublishCompanionHelp(CompanionHelp)`. The identity is the row's key, so a
changed value updates the same page and does not fade.

### Card content

One glass card, no scrolling. The blocks, top to bottom:

| Block | Content |
|---|---|
| Heading | Disc with the row's icon, the row's name, the small line "Settings · Display" / "App settings" / "Debug info". A title too long for one line moves the section into the heading and the name under it. |
| **Source block** of a per-app row | Violet "Custom for this app · Profile value: 720p …" or neutral "From profile …", 2 lines, **always kept**. |
| Body | `CompanionFit.summary(full, short, helpLines, …)`, see below. |
| Text value | In the accent color. |
| Palette | The palette's three swatches. |
| Game-background preview | A proportional box that takes the leftover height. |
| Debug info's details | Monospace, bounded, "+N lines". |
| Availability | "Unavailable with the current settings or on this device". |
| Saving | "Saved automatically · applies next stream". |
| Impact | The **three impact chips always at the bottom** (dashes for a row without a stream cost). |

The body is chosen in this order:

1. complete leading sentences within 6 lines (3 on a short panel);
2. else `CompanionHelp.short`, an authored short form the screens derive from the catalog summary, the first help
   sentence or the row title;
3. else "See the main screen for details."

`CompanionHelp.notice` sets the body in the warm notice color (a setting whose Off state costs something: idle
dimming Off). The full text stays on the main panel's own description.

### The main panel while a window shows the help

1. The main panel is unchanged except for the description: the description (the 520 px panel at 16:9, the dock
   when compact) is not drawn and the list takes its room.
2. The state "showing" is real: `CompanionSession.showsHelp` is set by the window's own composition (`PageHost`,
   only while the *shown page* is a settings help, keyed by the activity and cleared when the page leaves, the
   help is gone or the window is destroyed).
3. The screens ask `hidesDescription(windowShowsHelp, help)`.
4. A second display that exists but has no window yet, a refused launch and a window on its way out all keep the
   description on the main panel.
5. On a device with no companion display nothing differs: the description stays on the main panel.

## 10. State in, commands out

State is one-way and immutable: `CompanionState.Shell(pcName, published, context)` and
`CompanionState.Stream(pcName, gameName, stats, commands, start)`. The host reads the owner's lambda inside
composition, so snapshot state held in it updates the window live.

### Shell

1. Every screen or layer publishes its page with `PublishCompanionPage(page)`. There is a token per publisher, so
   a screen that is being replaced clears only what it published itself, never the next screen's page.
2. `ShellState.companionPages` holds the pages. `ShellState.companionContext` holds the title, purpose, hints and
   connected PC for the fallback card.
3. The companion never launches anything by itself and asks nothing: no confirmation dialogs anywhere.

| Page | Commands |
|---|---|
| Game | `CompanionGame.onAction(ShellButton)` runs the controller's own paths: `GameActions.start` (starts or resumes), `quitAndStart`, `Screen.GameSettings` |
| PC pages | `DeviceActions` (`connect`, `wake`, `rename`, `setDefault`, `unpair`, `remove`, `rescan`, `pair`, `pairWithCode`, `paired`) |
| Filter pages | the sheets' own reset |
| Result pages | `retryStream` / `dismissResult` |

### Stream

The commands are `StreamCommands(menu, sendKey)`. `DelosStreamActivity` binds them
(`companionHost.bindStream(streamMenu)`).

1. `menu` is the **same `StreamMenuHost`** the stream menu on the main panel is built on.
   `StreamMenu.companionMenu()` is `GameHost` without a menu window: dismiss and blur do nothing, brightness
   applies to the stream's window. Every tile calls the handler of its twin.
2. The menu's getters are not observable: the screen reads them again after every command and once a second.
3. Keys go through `KeyBoardLayoutController.sendKeyEvent` (made static), the one path every Full keyboard key
   takes: Down when the finger lands, Up when it lifts, Shift held around a shifted character (US layout,
   `KeyMap`).

The stream's own pages follow Rule 0 ([No scrolling, ever](#no-scrolling-ever)):

| Element | Layout |
|---|---|
| Tiles | a 3 x 3 grid (flat tiles on a short panel) |
| Sheets (Brightness, Bitrate, Upscaler, More, Send keys, Commands, Mouse) | the same card with the optional caption lines dropped first |
| Long lists (the 30 special keys, the PC's commands, More) | **paged** with Previous / Next and "2 / 3" (`CompanionPaging`), never scrolled |
| Keyboard | keeps 64 px keys and a one-line echo |

### Statistics

1. `DelosStreamActivity.onPerfStats` hands the snapshot the panel already gets to `CompanionHost.onStats` on the
   decoder thread.
2. It is dropped there when no companion window exists and when it comes sooner than 2 Hz after the last one
   (`StatsThrottle`), then posted to the main thread.
3. Nothing is computed again.
4. Until the first statistics (or the loader's end) the stream state shows the loader page.

## 11. Burn-in

The burn-in policy for AMOLED panels is `CompanionBurnIn`. It is deliberately simple and has two measures.

| Measure | Rule |
|---|---|
| **Pixel shift** | All content moves by 3 dp every 2 minutes along a fixed serpentine path over a 3 x 3 grid (an 18-minute cycle), so no edge stays on the same pixels. It is **always on**, whatever the setting says. |
| **Dim** | In the **shell state** the content fades to 22 % after the user's delay without a touch. The touch that wakes it is swallowed (waking never presses a button). |

1. A stream is not dimmed: someone is looking at the menu and the statistics change.
2. A loader that is still waited at is not dimmed (`CompanionPage.keepsAwake`).
3. The dim timer is armed from the last touch (no polling).
4. A new page counts like a touch, so a controller moving the focus on the main panel keeps the second panel
   bright.
5. The idle-dimming setting and its delay are in [Settings](#settings). The window follows them live
   (`rememberDimPolicy`). No delay is hard-coded: the fixed 60 s / 5 min values do not exist.
6. Debug info > Displays shows `companion dimming: On, after N min` / `Off` in the companion display's details.
7. Not verified: that this is enough for a real AMOLED over months.

## Settings

All four settings are device-wide and live in the default shared preferences. The default shared preferences are
excluded from backup, so a value never travels to another device.

| Row in Settings > Display | Key | Entries | Default | Visible |
|---|---|---|---|---|
| **Second display** | `display_second_role` | Automatic / External display / Part of the device | Automatic | always |
| Main screen | `display_main_screen` | **Automatic** / **Swapped** | Automatic | only while a companion display exists |
| *Dim the second screen when idle* | `display_companion_dim` | On / Off | **On**, because of OLED burn-in | only while a companion display exists, the same visibility as the Main screen row it sits next to |
| *Dim after* | `display_companion_dim_minutes` | 1 / 2 / 5 / 10 minutes | 1 | as above; hidden while Off |

1. The stored value of `display_second_role` for "Part of the device" is `built_in`.
2. `display_main_screen` is `automatic` or `swapped`.
3. `CompanionDim` holds the two dimming settings.
4. The help of *Dim the second screen when idle* says that Off can burn the panel in and that a touch wakes it
   without pressing a button.
5. The effects are in [Second display override](#3-second-display-override),
   [Which panel is main](#which-panel-is-main) and [Burn-in](#11-burn-in).

## Code map

| Path (under `app/src/main/java/com/limelight/`) | Role |
|---|---|
| `display/DisplayInventory.kt`, `display/DisplayClassifier.kt` | The list of displays, their roles and the reasons |
| `shell/companion/CompanionActivity.kt`, `CompanionHost.kt`, `CompanionSession.kt`, `DualScreenState.kt` | The companion window, its lifecycle and which panel is main |
| `shell/companion/CompanionContent.kt`, `CompanionPage.kt`, `Companion*Page*.kt`, `Companion*Content.kt` | The pages and what each screen publishes to them |
| `shell/companion/CompanionHelp.kt`, `CompanionHelpContent.kt` | The settings help on the second panel |
| `CompanionLayout.kt` | `FitColumn`, the net under the fit budgets |

| Path (from the repository root) | Role |
|---|---|
| `app/src/debug/java/com/limelight/shell/CompanionPreview.kt` | Debug previews |

## Verification

### Unit tests

| Test | What it enforces |
|---|---|
| `DisplayClassifierTest` | the classification |
| `StreamDisplayPlannerTest` | the stream display plan |
| `DualScreenPlannerTest` | the pair of panels and which one is main |
| `Companion*Test` | the companion's logic, paging, fit and pages |
| `CompanionFitTest` | the fit budgets with worst-case data on all five shapes |
| `CompanionRoutesTest` | every route of the shell is placed; `ContentKind.FALLBACK` is a valid place |
| `CompanionLogicTest`, `CompanionDimSettingTest` | both dimming settings |

### Debug previews

The extras go on `ShellActivity`. The command line is
`--es preview_screen companion --es preview_state <name> [--es preview_sheet <brightness|bitrate|upscaler|more|keys|commands|mouse>]`,
combined with `--es preview_shape <W>x<H>@<dpi>` for the panel's shape. It draws the companion's content alone
with made-up data (`CompanionPreview` and `CompanionFakes*` in `src/debug`).

| Extra | Value | Effect |
|---|---|---|
| `preview_screen` | `companion` | draws the companion's content alone |
| `preview_state` | a name from the table below | the state drawn |
| `preview_sheet` | `brightness`, `bitrate`, `upscaler`, `more`, `keys`, `commands` or `mouse` (optional) | the sheet of the stream state |
| `preview_shape` | `<W>x<H>@<dpi>` | the panel's shape |
| `companion_fake` | a name from the table below | the real companion window gets that fake state |

| Area | `preview_state` / `companion_fake` names |
|---|---|
| PCs | `start_online` `start_asleep` `start_offline` `start_offline_wake` `devices` `worst_pc` `pair_list` `pair_pin` `pair_code` `pair_result` `home_offline` `home_empty` `home_loading` |
| Content | `artwork` `artwork_filter` `library_filter` `library_filter_none` `profile` `create_profile` `boot` |
| Launch and results | `loader` `loader_short` `loader_started` `loader_launching` `portal` `portal_closing` `result` `result_disconnected` `problem` `worst_error` `report` |
| Home, help, stream | `shell` `shell_running` `shell_other` `worst_genres` `help` `help_long` `help_short` `help_game` `help_unavailable` `help_debug` `help_palette` `help_background` `worst_help` `stream` `typing` |
| Neither | `fallback` (also `idle`) `welcome` |

The **real** companion window on the emulator's second display, with the normal shell on the main one:

1. Set Second display = Part of the device ([Setting the preference](#setting-the-preference)).
2. Add `--es companion_fake <name>` to the launch of `ShellActivity`. The owner then hands the window that fake
   state.
3. The fakes' buttons write to `adb logcat -s CompanionPreview` instead of doing anything.
4. Take the screenshot of that display with `adb -s <device> emu screenrecord screenshot --display 1 <dir>`
   (`screencap -d` does not take the virtual display's id).

The screen previews
(`preview_screen start|devices|pair|pair_pc|artwork|filter|profile|create_profile|boot|loader|result|problem|settings|game_options|debug_info|home`,
...) run the real shell on a state of their own:

1. `loader` and `result` publish through the same overlay publisher.
2. With a real second display the window shows what **that** shell publishes (`CompanionPreview.liveShell`) in
   the appearance of that shell (`CompanionPreview.liveAppearance`: theme, palette, Material You and scale follow
   the preview's own Settings live, as the real shell's window follows the real store).
3. Every screen can be driven live with the D-pad of the main display
   (`adb -s <device> shell input keyevent KEYCODE_DPAD_DOWN`).

The Odin's **Screen shape simulator** (Settings > About > Debug info) draws the very same `CompanionContent` next
to the shell in a second panel's shape (Thor, RG DDS, ...), fed with the pages the simulated shell publishes.

### How to test with an overlay display

Emulator or device, layout and classification checks only, never for streaming or performance.

1. Add the overlay display, and remove it after the test:

```
adb -s <device> shell settings put global overlay_display_devices "1240x1080/420"   # the Thor's bottom panel
adb -s <device> shell settings delete global overlay_display_devices                # remove it
```

2. `put ... ""` is rejected by the shell: use `delete`.
3. The overlay display appears as "Overlay #1", 1240x1080 at 420 dpi.
4. Check the expected roles:

| Second display | Expected role of the overlay display |
|---|---|
| Automatic | `VIRTUAL` |
| External display | `VIRTUAL` |
| Part of the device | `BUILT_IN_COMPANION` |

5. Check that the external-display mode with "Fully external display mode" on never moves the stream to it.
6. A real external monitor with Part of the device shows the other half: it becomes a companion and the
   external-display mode ignores it.

What the emulator reports for the overlay display is in
[Evidence (2026-11-11): emulator display dumps](#evidence-2026-11-11-emulator-display-dumps).

### How to test the companion on the emulator

#### The emulator's real second display (main test)

1. Use the AVD `delos-layout2`. It has a second display in `~/.android/avd/delos-layout2.avd/config.ini`
   (`hw.display1.width=1240`, `height=1080`, `density=420`, `flag=0`, offsets set by hand in Extended controls >
   Displays > Arrangement).
2. Wait for the display: it is created by the emulator's multidisplay service shortly after boot.
3. Remove and add it with `adb -s <device> emu multidisplay del 1` and `emu multidisplay add 1 1240 1080 420 0`.
   Its id changes every time it is removed and added.
4. Take screenshots with the emulator's own screenshot. Display 1 (`--display 1`) is the second display; display
   0's `screencap` shows only the main one:

```
adb -s <device> emu screenrecord screenshot <dir>                 # the main display
adb -s <device> emu screenrecord screenshot --display 1 <dir>     # the second display
```

5. Set Second display to "Part of the device" ([Setting the preference](#setting-the-preference)): the
   classifier says `VIRTUAL` (type) for this display, exactly as for the overlay display.

What `dumpsys display` reports for this display is in
[Evidence (2026-11-11): emulator display dumps](#evidence-2026-11-11-emulator-display-dumps).

#### Setting the preference

The real Settings screen cannot be opened on the emulator: the native library of this build dies with SIGILL on
the emulator CPU as soon as the stream settings are read. A stream cannot run there for the same reason.

1. Stop the app and write the device-wide preference into the debuggable app. The file does not exist before the
   first write.

```
adb -s <device> shell am force-stop io.github.meitoncz.delos.debug
adb -s <device> shell "run-as io.github.meitoncz.delos.debug sh -c 'cat > shared_prefs/io.github.meitoncz.delos.debug_preferences.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="display_second_role">built_in</string>
    <string name="display_main_screen">automatic</string>
</map>
XML
```

2. `display_main_screen` is `automatic` or `swapped`.
3. `run-as ... rm -f shared_prefs/io.github.meitoncz.delos.debug_preferences.xml` undoes it.
4. The Main screen row can be seen in the debug preview `--es preview_screen settings` (Display tab). Its
   preferences are in memory, so choose "Part of the device" there with the D-pad and then add the display.

#### Overlay display (second test)

1. Add the overlay display with the commands of
   [How to test with an overlay display](#how-to-test-with-an-overlay-display).
2. The emulator draws the overlay as a small window in the top left corner of display 0, so a normal `screencap`
   shows it (`screencap -d` cannot take it).
3. While an overlay display exists, the emulator's touch input to display 0 stops working (`input tap`); keys
   still work.

#### What to expect

The expected results are the ones recorded in
[Evidence (2026-11-11): companion window on the emulator](#evidence-2026-11-11-companion-window-on-the-emulator).

### Device checks (date not recorded)

Everything in this section was checked on the Android 13 emulator, not on a dual-screen device.

**Displays and the companion window** (the emulator's real second display and the overlay display):

- the display list and roles, the classification, the plan (unit tests);
- the companion activity with its own stage on the other display, its removal and return;
- the swap in both directions;
- no crash, no ANR;
- the key focus staying with the shell;
- the companion absent from the running shell's task and from Recents' UI (`excludeFromRecents`; the system's
  internal list shows the live task only).

**Companion content** (real second display, `companion_fake`):

- the shell state with a fake focused game (nothing running and another game running);
- the stream state and the keyboard (a real tap on the Keyboard tile switched to it);
- no crash, the main panel's window still the focused one (`mTopFocusedDisplayId=0`) after the touches;
- the three states at 1240 x 1080, 960 x 720 and 1024 x 768 through the shape simulator.

Left out of the stream's More page: rumble feel and gyro smoothing (the controller's own pages on the main
panel).

**Settings help** (real second display, Second display = Part of the device, the screen previews of Settings, an
app's settings and Debug info on the main panel at 1920 x 1080, the D-pad driving the focus):

- the second panel follows every focus move (rows with impact chips, plain rows, an unavailable row, a text row,
  the palette and the game-background rows, a custom and a plain per-app row, the per-app screen with Reset all
  focused, Debug info's device and display rows);
- finger scroll and the scroll bar on a page longer than the card. The companion has no scroll container
  ([No scrolling, ever](#no-scrolling-ever)), so this check does not describe the current page;
- the page in the simulator at 1240 x 1080 @ 420, 960 x 720 @ 300 and 1024 x 768 @ 320;
- the main panel without its description (16:9 and compact) while the window shows the help;
- removing the display brings the panel back and adding it takes it away again, the keys reach the main panel in
  both states (`mTopFocusedDisplayId=0`), no crash;
- without a second display the three screens at 1920 x 1080 @ 369 are identical to the build before the settings
  help was added (the only differences are the clock and the animated background's phase).

### Not verified

- **Anything on a real dual-screen device**:
  - how the Thor, the Pocket DS or the Retroid add-on report their panels (so whether Automatic picks the right
    companion and the shell may open on it);
  - whether a real second panel's touch reaches the companion and whether a finger on it moves the top focus;
  - whether launching an activity on the other built-in panel is allowed on those devices' firmware;
  - the layout on the real 4:3 panels beyond the simulated shape;
  - a finger on the real second panel while a controller moves the focus on the main one;
  - whether an always-on companion burns in an AMOLED panel (the policy is dimming and a slow pixel shift, not
    proven on a panel).
- **Key focus**: whether a real finger on the companion moves the top focus back to its display (an injected tap
  does not), and on the stream the effect of the re-delivered intent on a running stream.
- **Anything that needs a stream** (the native library dies with SIGILL on the emulator):
  - the stream's companion (`CompanionHost.forStream`, `CompanionState.Stream`), the stream launch on the main
    panel and the shell-to-stream-to-shell hand-off are only compiled;
  - the tiles' effect (overlay, brightness, bitrate, upscaler, More, Disconnect, Quit), the keys reaching the PC
    and the statistics arriving in the window.
- **Anything that needs a PC** (the emulator has none): the PC name in the shell's companion, the shell's buttons
  (Start, Resume, Quit and start, App settings), the live follow of the focused game, real artwork and metadata
  in the banner, the game-background preview with a real game's art (the previews have no hero, so only the
  empty frame with the name was seen).
- The crossfade between rows of the settings help: only settled screenshots were looked at, no frame of the fade.
- What a real panel or monitor reports as product info, how the dual-screen devices report their second panel and
  what the Odin reports for a monitor on USB-C
  ([What is known, what is a guess](#what-is-known-what-is-a-guess)).
- That the burn-in policy is enough for a real AMOLED over months ([Burn-in](#11-burn-in)).

## Related

- [EXTERNAL_DISPLAY.md](EXTERNAL_DISPLAY.md), [GUI.md](GUI.md), [HANDHELD_DEVICES.md](HANDHELD_DEVICES.md),
  [DIAGNOSTICS.md](DIAGNOSTICS.md).
- Issue [#76](https://github.com/Meitoncz/Delos/issues/76).

## Evidence (2026-11-11): emulator display dumps

Date: the day the record entered the repository. Device: Android 13 emulator, API 33. Source: `dumpsys display`.

### What the Android 13 emulator reports (AVD `delos-layout`, API 33, `dumpsys display`)

Display 0, no overlay:

```
DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4619827259835644672", 1920 x 1080, ..., density 420, touch INTERNAL, rotation 0, type INTERNAL, address {port=0, ...}, deviceProductInfo DeviceProductInfo{name=EMU_display_0, manufacturerPnpId=GGL, productId=1, ..., connectionToSinkType=0}, ...
mBaseDisplayInfo=DisplayInfo{"Built-in Screen", displayId 0", displayGroupId 0, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED, real 1920 x 1080, ..., type INTERNAL, uniqueId "local:4619827259835644672", ...
```

With `overlay_display_devices` set to `1240x1080/420`:

```
DisplayDeviceInfo{"Overlay #1": uniqueId="overlay:1", 1240 x 1080, ..., density 420, 420.0 x 420.0 dpi, ..., touch VIRTUAL, rotation 0, type OVERLAY, deviceProductInfo null, state ON, ..., FLAG_PRESENTATION, installOrientation 0}
mBaseDisplayInfo=DisplayInfo{"Overlay #1", displayId 3", displayGroupId 0, FLAG_PRESENTATION, FLAG_TRUSTED, real 1240 x 1080, ..., type OVERLAY, uniqueId "overlay:1", app 1240 x 1080, density 420 (420.0 x 420.0) dpi, layerStack 3, ..., deviceProductInfo null, ...}
```

So an overlay display is `type OVERLAY`, `FLAG_PRESENTATION` and no product info, and it is the only second
display the emulator offers: it cannot show what a built-in second panel looks like. The built-in side of the
table (no `FLAG_PRESENTATION`, `type INTERNAL`) comes from the AOSP source and is covered by unit tests, not by an
observation.

### The real second display of the AVD `delos-layout2`

`dumpsys display` shows it as:

| Field | Value |
|---|---|
| Name | `"Emulator 2D Display"` |
| Flags | `FLAG_PRESENTATION`, `FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS`, `FLAG_TRUSTED` |
| Size | 1240 x 1080 at 420 dpi |
| Type | `type VIRTUAL` |
| Product info | none |
| Unique id | `virtual:com.android.emulator.multidisplay:...` |

The flags as printed are
`FLAG_PRESENTATION | FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS | FLAG_TRUSTED`.

The classifier says `VIRTUAL` (type), and "Part of the device" is needed to make it a companion, exactly as for
the overlay display. It is not the Thor's plain internal panel (it carries `FLAG_PRESENTATION`), but since the
companion is an activity the flag does not matter.

## Evidence (2026-11-11): companion window on the emulator

Date: the day the record entered the repository. Device: Android 13 emulator, with the real second display of the
AVD `delos-layout2` and with the overlay display
([How to test the companion on the emulator](#how-to-test-the-companion-on-the-emulator)).

### `Presentation` refused

With the companion planned for display 0 as a `Presentation`:
`WindowManager: Attempted to add presentation window to a non-suitable display. Aborting.`
(`InvalidDisplayException`).

### Key focus observations

| Attempt | Observed |
|---|---|
| The companion activity started on the other display, `FLAG_NOT_FOCUSABLE` only | the main panel stopped reacting and Android showed "Delos Debug isn't responding" |
| `moveTaskToFront` of the owner | nothing changes |
| The owner started again with the flags of `CompanionSession.returnFocusToOwner` once the companion has resumed | `mTopFocusedDisplayId=0` and the shell's window focused with the companion resumed on the other display, keys reach the shell |
| An injected tap on the companion | does not move the top focus back to its display |

### What to expect (checked this way)

- Automatic, display present: the shell on display 0, the companion activity on the other display (dim mark, the
  PC name once a PC is chosen), `mTopFocusedDisplayId=0`, the shell's window focused, keys reach the shell.
- Removing the display: no crash, the companion is gone and the shell untouched; adding it again starts a new
  companion (`Companion started on display N` in the diagnostic log). Six remove/add cycles of the real second
  display left the same two activities and two windows (shell and one companion) after a GC, one
  `Companion started` per cycle, no crash, and the top focused display stayed 0.
- Swapped (preference set before the shell starts): the shell opens on the second display (it moves itself there
  from display 0 and ends), the companion is an activity on display 0; with the preference already swapped,
  adding the display while the shell runs moves the shell, and removing it lets the system bring the shell back
  to display 0 (it moves again when the display returns).
- The shell sent to Home: the companion finishes; a display added while the shell is stopped starts nothing; the
  shell back in front starts it.
