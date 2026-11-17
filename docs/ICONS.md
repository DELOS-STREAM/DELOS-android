# Interface icons

## Purpose

The interface draws its icons from two bundled icon fonts, **Phosphor Regular and Fill** (chosen on 2026-10-28,
replacing Material Symbols Rounded). This document specifies where the fonts come from, how they are generated,
how an icon is chosen and how a custom glyph is added.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Sources, generation, glyph origins, custom glyphs | Which icon each screen and setting uses: [ICON_AUDIT.md](ICON_AUDIT.md) |
| The rule for choosing an icon | Origin and checksums of the bundled fonts: [THIRD_PARTY.md](THIRD_PARTY.md) |

## Design

The shared `Symbol` component crossfades between the two bundled fonts over 350 ms on focus.
The fonts use the same code points and advance widths, so the transition does not move labels.

## Sources and generation

- Official source: [phosphor-icons/web v2.1.2](https://github.com/phosphor-icons/web/tree/v2.1.2).
- License: MIT, included in `app/src/main/assets/licenses/Phosphor-LICENSE.txt`.
- `tools/fonts/icons.txt` maps stable Delos semantic names to Phosphor names or local names declared
  in `tools/fonts/custom.json`. Existing callers do not need to know the source family's names.
  New entries describe their actual action or setting.
- `tools/fonts/subset_fonts.py` downloads the versioned Regular and Fill TTFs, the code-point CSS
  and license. SHA-256 checks validate cached and downloaded sources before generating anything.
- Generated files: `res/font/phosphor_regular.ttf`, `res/font/phosphor_fill.ttf`, `Symbols.kt`
  and `docs/prototype/phosphor-icons.css`. Commit these along with mapping/generator changes.
- The prototype reads the Android fonts through relative paths; icon rendering needs no CDN.
  Its `data-icon` names use the generated CSS and the same Regular/Fill crossfade.
- The subsets hold one glyph per semantic name of `tools/fonts/icons.txt` (the generator writes the same
  names to `Symbols.kt`). No runtime library is needed.

Rebuild the icons (Python 3 with `fonttools` installed; any system):

```sh
python3 tools/fonts/subset_fonts.py --icons-only --work build/phosphor-icons
```

Without `--icons-only`, the Unbounded/Figtree generation also runs. A normal Gradle
build uses the committed subsets and needs neither Python nor a font download.

## Choosing icons

Settings use an icon for their meaning rather than inheriting one icon for a whole category.
Every icon of the app is listed in [ICON_AUDIT.md](ICON_AUDIT.md) with its meaning; the rule is
one glyph per meaning (a glyph is repeated only where the meaning is really identical, and the
allowed repeats are named there) and no two rows of one settings tab share a glyph
(`SettingIconsTest`). Add a glyph by giving it its own semantic name in `tools/fonts/icons.txt`
and re-running the generator; any Phosphor 2.1.2 icon is available, not only the bundled subset.
Examples include USB, motion sensors, cursor, gestures, horizontal/vertical sensitivity, key layout,
import/export, clipboard privacy, asynchronous decoding, upscaler mode and sharpness.

Stream menu actions distinguish keyboard/layout controls, task manager/statistics, sending/receiving,
and touchscreen/cursor. Resume uses Play/Pause; starting an app uses Play. Identical actions and
closely related settings deliberately share a symbol across screens, including the per-app settings.

The battery body is a custom drawing with a proportional charge fill; only its
charging bolt comes from the icon font. Controller caps, their lettering and the Delos logo are local font glyphs too.

## Glyph origins (alignment)

Upstream Phosphor glyphs have loose stored `xMin` bounds. Saving a subset recalculates tight bounds
without adjusting the horizontal side bearing, which changes the TrueType origin (`xMin - lsb`) and
pulls the artwork left within its 1024-unit advance (Plus, for example, moved 128 units to the left).

The generator therefore recalculates icon bounds and adjusts each left side bearing to preserve
the source origin. Advance widths, contours and intentional asymmetry stay unchanged.
The shared Android component and the prototype consume the same corrected fonts without offsets or
per-icon layout patches. The debug `IconFontInstrumentation` rasterizes Plus, Gear, Desktop and
User Circle from both bundled fonts at 24/48/96 px and measures the horizontal center:

```sh
adb -s <device> shell am instrument -w io.github.meitoncz.delos.debug/com.limelight.shell.ui.IconFontInstrumentation
```

After a change to the mapping or the generator: validate all semantic mappings against both fonts
(code points and metrics), check the prototype with the same local subsets, and check the icons on a
device in the shell, settings, context/side menus and stream menu, including focus transitions and
controller hints.

## Custom glyphs and the Logo tab

The Artwork Logo tab uses a custom glyph, **A: Wordmark plate**. `logo = delos-wordmark-plate`
resolves to **U+F000** in both subsets (`Symbols.LOGO`). Sources are
`tools/fonts/logo/a-wordmark-plate-{regular,fill}.svg`. The other SVGs in `tools/fonts/logo/`
(B "Title over a scene", C "Emblem + title") are candidates that were not chosen; they are not imported
into the fonts. No company logo or real game wordmark is used in any of them.

## Controller and Mac input glyph sources

The approved library contains 337 complete control glyph pairs (674 native 256-unit SVGs):
Xbox 38, DualSense 38, DualShock 4 38, Joy-Con 40 and Mac keyboard/mouse/trackpad 183.
Canonical sources live in `tools/fonts/controller/<family>/{outline,solid}/` and
`tools/fonts/mac/{outline,solid}/`. Outline maps to Regular and Solid maps to Fill.
The SVG bytes are preserved from the approved artwork; rejected controller prototypes are not imported.
These are complete caps, not letters to place inside an additional disc.
For 38-unit cap ink on the 192/256 drawing area, use a font em size of 38 / 0.75
(50.6667), without shrinking the source artwork.

`custom.json` explicitly assigns U+F001–U+F151 in approved inventory order; U+F000 remains
the Logo. Allocation checked every Unicode cmap in both complete, SHA-256-pinned
Phosphor 2.1.2 source fonts, not just the bundled subsets. Keep these assignments stable.
Local names are `delos-pad-<family>-<name>` and `delos-mac-<name>`.
Semantic names are `PAD_XBOX_<NAME>`, `PAD_DUALSENSE_<NAME>`,
`PAD_DUALSHOCK4_<NAME>`, `PAD_JOYCON_<NAME>` and `MAC_<NAME>` (hyphens become underscores).
Nintendo physical south/east/west/north remains B/A/Y/X; generic face/menu controls use Xbox
and generic shoulder/trigger labels retain L1/R1/L2/R2.

The original hardware-informed geometry does not incorporate commercial icon packs or SF Symbols.
Lettering derives from Figtree Medium and remains OFL-1.1, with notices beside both source sets
and in the app's `Figtree-OFL.txt`. Xbox/PlayStation Guide marks derive from Simple Icons 11.0.0
(CC0-1.0); `controller/brand-marks.json` retains original paths, versioned URLs and source hashes.
`Simple-Icons-CC0.txt` is kept with the sources and in the app's licence assets.
CC0 does not waive trademarks; the marks identify controls without endorsement.
Source and notice digests are recorded in `third_party/CHECKSUMS.sha256`; upstream provenance
is detailed in [THIRD_PARTY.md](THIRD_PARTY.md).

## Adding custom glyphs

`tools/fonts/custom.json` declares a local source name, explicit hexadecimal `codepoint`, and
`regular`/`fill` SVG paths relative to `tools/fonts/`. To add a glyph:

1. Supply closed paths on a 256×256 SVG grid. Resolve overlaps and contour winding before import;
   counters must have opposite winding to their surrounding contour. SVG even-odd attributes alone
   do not encode font counter semantics.
2. Add a manifest entry with an unused BMP private-use point (U+E000–U+F8FF). The generator checks
   every Unicode cmap of BOTH complete upstream fonts and rejects collisions, duplicate local points
   and names shadowing upstream icons. U+F000 was unused in both pinned full fonts.
3. Map a semantic name in `icons.txt` to that manifest name. Only referenced custom entries are imported.
4. Run the normal `--icons-only` command. Import scales to units-per-em, flips Y onto the source
   font's ascent/baseline convention, converts cubic curves to quadratic contours, and sets the shared
   1024-unit advance with zero origin. The bearing correction still preserves every origin.
   Cached upstream files remain untouched; import occurs in memory before subsetting.
5. Review generated fonts, symbols and CSS together, render Regular/Fill at actual use sizes and
   verify the Android crossfade and the look on a device.

## Console glyph candidates

`tools/fonts/console/` contains two original standing-console candidates,
**A: Open seam** and **B: Swept wing**, each in Regular and Fill. They use the Phosphor
256-unit design grid; Regular's 16-unit round strokes are already expanded into closed paths.
Fill keeps the rear-wing separation and disc slot as transparent negative space. Neither design
contains a logo or wordmark. **They are not part of the bundled fonts or the app.** They are kept
for a possible console glyph: both would then be imported through the custom-glyph mechanism above, each
with its own unused code point, compared in the app, and the one not chosen removed.

`tools/fonts/console/review.py` (standard library only) writes a self-contained, offline comparison sheet:

```sh
python3 tools/fonts/console/review.py --output build/console-glyph-review/index.html
```

## Code map

| Path | Role |
|---|---|
| `tools/fonts/subset_fonts.py` | The generator: downloads, verifies, subsets, writes the fonts, `Symbols.kt` and the prototype CSS |
| `tools/fonts/icons.txt` | Semantic name to Phosphor name or custom name |
| `tools/fonts/custom.json`, `tools/fonts/logo/`, `tools/fonts/console/` | Custom glyphs and candidates |
| `app/src/main/res/font/phosphor_regular.ttf`, `phosphor_fill.ttf` | The generated fonts |
| `app/src/main/java/com/limelight/shell/ui/Symbols.kt` | The generated code point constants |
| `app/src/main/java/com/limelight/shell/ui/Type.kt` | `Symbol`: the composable with the focus crossfade |
| `app/src/debug/java/com/limelight/shell/ui/IconFontInstrumentation.java` | The on-device centering check |
| `docs/prototype/phosphor-icons.css` | The same glyphs for the HTML prototype |

## Verification

`SettingIconsTest` checks the icon rules of the settings rows. `IconFontInstrumentation` (see "Glyph origins")
measures glyph centering on a device. The generator verifies the SHA-256 of every source it downloads.

### Not verified

- The Logo glyph has no recorded device check of its rasterization and focus crossfade.
- The console glyph candidates are not part of the app and were only reviewed in a browser.

## Related

- [ICON_AUDIT.md](ICON_AUDIT.md), [THIRD_PARTY.md](THIRD_PARTY.md), [GUI.md](GUI.md).

## Evidence (2026-10-28): replacement of Material Symbols

AYN Odin 2 Portal Max, ARM64 debug build: the icons in the shell were accepted by the owner, including the
Palette icon for the color-palette selector. The build, the unit tests of that day (80) and lint (0 errors)
passed. Every catalog setting had an explicit icon mapping and every semantic mapping resolved to a nonempty
glyph in both subsets with matching widths; regeneration was byte-identical. The two subsets together were smaller
than the Material Symbols subset they replaced (299,768 bytes).

## Evidence (2026-10-28): glyph origins

After the alignment fix, `IconFontInstrumentation` on the Odin measured a horizontal center error of 0 px for
every tested glyph at 24/48/96 px in both fonts, and the PC-selection screen was captured and checked by eye.
Chromium measured matching 96 px advances and 48 px ink centers for the same icons in both prototype fonts.
All glyph origins of both fonts matched the source. The captures were not committed.

## Evidence (2026-11-12): the Logo glyph

Generation completed from cached sources and from a fresh download. All other semantic code points, and the
compiled contours and horizontal metrics of all retained upstream glyphs, were unchanged. Both Logo glyphs have
bounds (96,128)–(928,768), advance 1024 and origin zero. Renders at 32/96 px beside Cover/Hero were inspected by
eye: title and counters stay visible, no clipped paths, matching alignment between the weights. The candidate
comparison found A weakest at 24 px (it can suggest a text card), B closest to the Cover/Hero icons and C
suggesting an achievement badge. Not recorded here: a device check of the Logo glyph's rasterization and focus
crossfade.
