# Game information and library filters

## Purpose

The home screen shows facts and tags about the focused game in one row of badges, and the **Filter library** modal
narrows the library by genre, theme, HDR support and duration. This document specifies the badge row, the genre and
theme pills and their sources, the HDR/SDR badges, the filter modal, metadata matching, caching, the progress pill,
the metadata providers, the build configuration of the metadata Worker and the **game overview** below the dashboard.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| The information row and its badges | The streaming-time block at the start of the row: [START_AND_HOME.md](START_AND_HOME.md), "Streaming time" |
| Genre and theme pills: sources, ranking, colors, icons | Icon font and `tools/fonts/icons.txt`: [ICONS.md](../ICONS.md) |
| Native HDR, SDR and `HDR/SDR unverified` badges | Translated names and English data: [LOCALIZATION.md](../LOCALIZATION.md) |
| The **Filter library** modal | `MarqueeText` and labels that do not fit: [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md) |
| Metadata matching, caching, refresh and backoff | SteamGridDB artwork and its matches: [ARTWORK.md](ARTWORK.md) |
| The metadata progress pill | App options and Settings → About: [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md) |
| IGDB, PCGamingWiki, HowLongToBeat and Steam tag lookups | Worker deployment and secret handling: [tools/metadata-worker/README.md](../../tools/metadata-worker/README.md) |
| `DELOS_METADATA_URL`, credentials, privacy | Design principles and the index of GUI documents: [GUI.md](../GUI.md) |
| The game overview (variant A · Descent of issue [#107](https://github.com/DELOS-STREAM/DELOS-android/issues/107)) | The cover row it opens from: [START_AND_HOME.md](START_AND_HOME.md), "Home" |

## 1. Information row

1. The information area under the game title/logo is **one row, with no label**.
2. The row starts with the streaming-time block (see [START_AND_HOME.md](START_AND_HOME.md), "Streaming time"). The
   badges follow it.
3. The badges come in two groups: the **facts** first, then a divider, then the **tags**.
4. Without a genre or theme pill the divider is omitted too.
5. Built-in apps and games without metadata show the facts only.
6. The facts keep their usual pill look. The tag pills have exactly the same height, padding, font and icon size.
7. Like the facts, the tag pills appear when the metadata is there.

| Group | Badges, in order |
|---|---|
| Facts | `Running on <PC>` while the game runs; native HDR/SDR; the HowLongToBeat headline duration; `Virtual display` |
| Tags | The game's genre pill; the theme pill |

The PC's name is no chip while nothing runs.

### 1.1 Dividers

| Element | Width | Height | Shape | Color | Margins |
|---|---|---|---|---|---|
| Divider between facts and tags | 2 px | 28 px | Rounded bar | `colors.text3` at 55 % alpha | 16 px on both sides |
| Bar between the two entries of a pill | 1.5 px | 18 px | Rounded, thin vertical bar | The pill's own text color at 55 % alpha | 12 px on both sides |

### 1.2 Overflow

1. The row never wraps.
2. When the badges do not fit in the room right of the streaming-time block and its air, they stay one line and
   scroll in it with the edge fade of `MarqueeText`: slowly, with pauses, in a loop.
3. Reduced motion keeps them still.
4. The badges never scroll under the block.

## 2. Genre and theme pills

### 2.1 Entries of a pill

1. **Each pill shows up to two entries** inside one pill, set apart by the bar of [Dividers](#11-dividers):
   `[icon Shooter | icon Adventure] [icon Horror | icon Sci-fi]`.
2. Every entry has its own icon and name. The icon-to-text spacing is that of the other pills.
3. With one entry the pill shows just that entry, with no bar.
4. With no entry the pill is omitted.
5. **Steam tags only drive the card pills**, never the filter. IGDB's complete genre and theme lists stay in the
   metadata and keep feeding the Y filter (see [Filter library modal](#4-filter-library-modal)).

| Source | Used when |
|---|---|
| Steam tags | First choice, for a game with a Steam app id |
| IGDB ladder | The game has no Steam id, the Steam fetch failed or the store lists no mappable tag |

### 2.2 Steam tags

Code: `topGenres` and `topThemes` in `shell/ui/GameTags.kt`; the mapping in `shell/ui/SteamTags.kt`.

1. A game with a Steam app id (the first one IGDB lists) gets its Steam *user tags*, most voted first, from the
   metadata Worker: `GET /v1/steam/tags?app=<id>`.
2. The Worker reads the public store page. This is **unofficial and may change or be blocked**.
3. Of the first 15 tags, every tag the mapping table knows becomes a genre or a theme entry in vote order, without
   duplicates.
4. Features, modes, moods, art styles and meta tags (Singleplayer, Co-op, Indie, Atmospheric, Third Person, ...) are
   ignored.
5. The table maps each Steam tag name (case-insensitive) to exactly one IGDB genre or theme, so the icons and names
   are IGDB's.
6. A few "weak" mappings are only a fallback (table below).
7. When Steam gives one entry, the second slot is filled from IGDB's ranking ([IGDB ladder](#23-igdb-ladder)) with
   the first entry not shown yet. Indie and Action never fill.
8. The names shown come from `res/values/strings_tags.xml` by IGDB id (`genreName`, `themeName`), so they follow the
   app language.
9. The Steam tag names and the ranking stay English data (see [LOCALIZATION.md](../LOCALIZATION.md)).

| Steam tag | IGDB genre or theme (examples of the mapping) |
|---|---|
| Platformer | Platform |
| Action-Adventure | Adventure |
| Cooking | Simulator |
| Funny | Comedy |
| Open World | Open world |
| ... | ... |

| Weak mapping | Applies only when |
|---|---|
| Roguelike/Roguelite -> Role-playing (genre) | Fewer than two strong genres exist |
| Action/Superhero -> the Action theme | The game has no other theme (the rule IGDB's Action theme follows too) |

### 2.3 IGDB ladder

Genres are ordered by this ladder, and the pill shows the first two:

1. Shooter
2. Fighting
3. Racing
4. Role-playing
5. Real Time Strategy
6. Turn-based strategy
7. Tactical
8. Hack and slash/Beat 'em up
9. Platform
10. Adventure
11. Puzzle
12. MOBA
13. Music
14. Visual Novel
15. Card & Board Game
16. Quiz/Trivia
17. Pinball
18. Point-and-click
19. Sport
20. Simulator
21. Strategy
22. Arcade

Genres IGDB adds later come after these in IGDB's order. Indie is a business model, not a genre: it only shows when
it is the game's only entry.

Themes skip Erotic entirely. They skip Action unless it is the game's only usable theme (Life is Strange: Double
Exposure lists Action alone and shows it). The other themes follow this order:

1. Horror
2. Survival
3. Open world
4. Science fiction
5. Fantasy
6. Stealth
7. Warfare
8. Sandbox
9. Mystery
10. Thriller
11. Party
12. 4X
13. Historical
14. Comedy
15. Drama
16. Romance
17. Business
18. Educational
19. Kids
20. Non-fiction

Later additions come after these.

`mainGenre`/`mainTheme` are the first entries of this ranking (`rankedGenres`, `rankedThemes`).

### 2.4 Examples

From Steam tags:

- Cronos (Survival Horror, Horror, Sci-fi, Third-Person Shooter, ...) shows `Shooter | Adventure` and
  `Horror | Science fiction`. The second genre comes from IGDB's Shooter, Puzzle, Adventure.
- LEGO Batman (Co-op, Open World, Superhero, ..., Action-Adventure, Funny, ..., 3D Platformer, Adventure) shows
  `Adventure | Platform` and `Open world | Comedy`.
- Forza Horizon (Racing, Open World, Driving, ..., Automobile Sim, ...) shows `Racing | Simulator` and `Open world`.

From IGDB alone:

- LEGO Batman (Puzzle, Adventure) shows `Adventure | Puzzle`.
- Forza (Racing, Simulator, Sport, Arcade) shows `Racing | Sport`.
- Overcooked (Simulator, Strategy, Indie) shows `Simulator | Strategy`.

### 2.5 Pill colors

The **genre pill** is the accent of the row. The **theme pill** is fainter, so it stays behind the genre. Neither
pill animates.

| Property | Genre pill | Theme pill |
|---|---|---|
| Hue | The game's main color from its cover (`HeroBitmaps.coverHues`; the game background's while the cover's are unknown) | The game's *second* cover color (`hues[1]`); the main hue shifted by +40° when the game has only one |
| Hue with *Match the game's colors* off, with Material You, or while the colors are not known | The palette's accent hue | The palette's accent (third) hue while the colors are off or unknown |
| Fill, over the glass | `hsl(hue, 0.8 × vividness, 34 %)` at 35 % alpha | `hsl(hue2, 0.7 × the genre's saturation, 30 %)` at 16 % alpha |
| Border | 1 px `hsl(hue, …, 55 %)` at 55 % alpha | 1 px `hsl(hue2, …, 55 %)` at 45 % alpha |
| Icon and text | The same hue at 85 % lightness | 80 % lightness and 90 % alpha |

A light theme uses darker shades for the genre pill's icon and text.

### 2.6 Icons

1. Every genre and theme has its own Phosphor icon: `genreIcon` and `themeIcon` in `shell/ui/GenreIcons.kt`.
2. The neutral tag stands in for genres and themes IGDB adds later.
3. The theme icons come from `tools/fonts/icons.txt` like every other icon (see [ICONS.md](../ICONS.md)).

## 3. HDR and SDR badges

| State | Badge | Shown when |
|---|---|---|
| Native HDR | Gradient badge | The game has confirmed native HDR |
| Native SDR | Neutral SDR chip | The game is confirmed native SDR |
| Unverified | Muted `HDR/SDR unverified` chip | Native HDR/SDR could not be verified, and the record was evaluated successfully |
| No badge | None | The record is missing, or the unknown lookup failed or is backed off |

### 3.1 Native HDR badge

Native HDR uses a gradient badge instead of a rainbow. It has the size of every info pill.

| Element | Value |
|---|---|
| Fill | Diagonal linear gradient from cyan `#22D3EE` over indigo `#6366F1` to fuchsia `#D946EF` |
| Scrim | 12 % black over the gradient; it keeps the white text readable on the cyan end |
| Icon | The sparkle icon |
| Text | White |
| Border | 1 px of white at 35 % alpha |
| Glow | Very soft, static, 8 px blur, 35 % alpha, in the indigo behind the pill |

### 3.2 Unverified state

1. The `HDR/SDR unverified` chip means native HDR/SDR could not be verified: the wiki has no page, evidence is
   unknown/ambiguous, or claimed HDR is broken, disabled, simulated or platform/edition-restricted.
2. Only successfully evaluated unknown records (`updatedAt > 0` and `pcgwFailedAt == 0`) show this third state.
3. Missing records and failed/backed-off unknown lookups show no badge.
4. Previously confirmed HDR/SDR evidence remains visible during a later refresh failure.
5. The host's streaming HDR flag is not native game support.
6. Special K, ReShade, Auto HDR and RTX HDR do not qualify as native game HDR.

The evidence rules are in [PCGamingWiki](#73-pcgamingwiki).

## 4. Filter library modal

### 4.1 Opening, closing and layout

1. **Y** on the home screen opens the **Filter library** modal. Y or B closes it; Done closes it as well.
2. The modal is a centered glass panel with the title, a live result line as a pill and four sections of chips.
3. The result line reads `27 of 55 apps`, or `All 55 apps` without a filter.
4. Each section has a large Unbounded heading and its icon in an accent-tinted circle. Thin dividers separate the
   sections.
5. The chosen chip is filled with the accent.
6. A choice applies at once (no confirmation), and the library behind the modal updates with it.
7. Closing returns the focus to the game that was focused.

### 4.2 Sections

| Section | Chips | Notes |
|---|---|---|
| **Genre** | `All` plus every genre any game in the downloaded metadata lists | Each chip has its icon and number of games |
| **Theme** | The same for IGDB themes | Single-select; shown while the library has any theme |
| **HDR support** | `All`, `Native HDR`, `SDR`, `HDR/SDR unverified` | The sparkle, dim-sun and sealed-question-mark icons of the home badges |
| **Duration** | Any, <10 h, 10–30 h, 30–60 h, 60+ h, Unknown | From HowLongToBeat |

### 4.3 Input and scrolling

| Input | Action |
|---|---|
| D-pad up/down | Moves between the sections and the buttons |
| D-pad left/right | Moves within a row |
| **X** | Resets the filters |
| Y, B, Done | Closes the modal |

1. A long genre list scrolls the panel and keeps the focused chip in view. The panel's top and bottom edges fade out.
2. The focused chip is kept 150 px from the top edge, so the first row scrolls the heading into view.
3. The bottom padding keeps the last row's focus ring outside the fade, including at 125% text scale.
4. The Reset button is disabled while nothing is filtered.

### 4.4 Filter rules

1. While filters are active the home hint reads `Y Filter • N active`.
2. Filters follow the active profile: genre, theme, HDR and duration are stored per profile.
3. Filters combine across categories.
4. The Genre and Theme filters match a game when *any* of its genres/themes is the chosen one, not only the main
   genre and theme the home screen shows. The chip lists can therefore hold entries no tile displays as its main tag.
5. Both lists and their counts are read from the metadata store while the modal is open, so they follow records as
   they arrive and after a cache-schema refetch.
6. Filter genres are IGDB genres only. IGDB themes share no ID space with genres and are not substituted. Steam tags
   never feed the filter.
7. Unrounded HowLongToBeat seconds drive the Duration filter (see [HowLongToBeat](#74-howlongtobeat)).
8. The `HDR/SDR unverified` filter retains unknown-support matching, including pending/failed records without a
   badge. The display rule of [Unverified state](#32-unverified-state) does not change filter membership.
9. Empty results say to press Y and keep a controller/touch reset action.
10. Original library order, running-game state, artwork and cover layout are preserved.

## 5. Metadata matching

App options → Metadata provides IGDB search/match selection, refresh and reset.

### 5.1 Tolerant title comparison

Automatic matching compares titles tolerantly. It ignores differences in:

| Ignored difference | Example |
|---|---|
| Case | |
| Punctuation | |
| Diacritics | |
| A leading "The" | |
| "&"/"and" | |
| Roman/Arabic sequel numbers | |
| A bracketed note | "(Steam)" |
| Trailing edition qualifiers | "Definitive Edition", "GOTY", "Director's Cut" |

1. Different sequel numbers stay distinct.
2. A subtitle stays significant, with one exception: a host name without its subtitle ("Planet of Lana II") matches
   the one PC/Steam IGDB entry that extends it ("Planet of Lana II: Children of the Leaf") when nothing has the exact
   name.

### 5.2 Narrowing entries with the same tolerant name

IGDB entries with the same tolerant name are narrowed in this order:

1. The exact name.
2. A year written in the host name.
3. A Steam listing.
4. The PC platform.
5. The newest release.
6. An entry with at least three times the IGDB rating count of every other.

If the entries are still tied, the game stays unmatched until the user picks a match.

### 5.3 IGDB query and identifiers

1. The IGDB query drops symbol characters such as "®", "©" and "™". IGDB returns nothing at all for a query
   containing "®" or "©", which Steam titles like "LEGO® Batman™: Legacy of the Dark Knight" contain.
2. IGDB search is PC-focused: console-only entries are dropped, but entries without platform data are kept.
3. IGDB IDs, Steam AppIDs and host app IDs are distinct.
4. Metadata matches never overwrite manual SteamGridDB artwork matches (see [ARTWORK.md](ARTWORK.md)).
5. Explicit match operations belong to the shell, so changing a match can finish even if active filters remove its
   tile.
6. Refresh/Reset report "No IGDB match found" when the re-match finds nothing.

## 6. Caching, refresh and progress

### 6.1 Cache

1. Data/matches are cached in application files.
2. Missing provider data stays missing.
3. Each provider's failure time is stored in the record.
4. A failing provider is not asked about that game again for the backoff time, across focus changes and restarts,
   unless the user chooses Refresh.
5. A record stamped in the future is treated as stale.
6. The cache schema is bumped whenever matching or evidence rules change, so records classified by older rules are
   looked up again instead of being trusted for a week.
7. When a library opens, every cached record is loaded and published first, so filters and the genre list are
   complete immediately. Stale games are then refreshed.

| Rule | Value |
|---|---|
| Refresh of a fully answered record | After seven days |
| Refresh of an unmatched record | After six hours |
| Backoff of a failing provider, per game | 30 minutes |
| Concurrent lookups while refreshing stale games of an opened library | At most two |

### 6.2 Progress pill

1. Network metadata lookups reuse the artwork progress pill above the button hints, labeled "Downloading metadata"
   with completed/total games.
2. Cached-only reads and failure-backoff skips do not show progress.
3. Updates are serialized on main. Each batch is removed in `finally` on completion, error or cancellation.
4. Overlapping metadata operations display the largest active batch. Finishing one cannot clear another's counter.
5. Artwork takes priority while both stores are busy, so there is only one pill.
6. Both use the same existing artwork animation/reduced-motion behavior, retaining the last count and label during
   exit.

| Operation | Batch |
|---|---|
| Library batches | Count only games needing a provider refresh |
| Focused lookups, Refresh/Reset, match selection, IGDB search | A single-item batch |

## 7. Providers

### 7.1 Calls and failures

1. Provider calls run off the UI thread, are paced, and are interrupted when their coroutine is canceled.
2. IGDB and PCGamingWiki are reached through the Delos metadata Worker (`tools/metadata-worker`).
3. The Worker holds their credentials, authenticates (Twitch app token, PCGamingWiki bot session), builds the
   upstream queries itself from validated parameters and returns the providers' raw JSON.
4. The app keeps all matching, parsing, caching and backoff.
5. The app clients never follow redirects.
6. Each response in the table below is a provider failure that uses the per-provider backoff, never an empty or
   absent result.
7. Unavailable/changed provider responses are reported rather than replaced with another provider's metric.
8. Canceled work stops before further provider requests or publishing a resolved result.

| Response | Treated as |
|---|---|
| HTTP 429/503 from the Worker (its rate limit or an upstream throttle) | Provider failure |
| Any other non-2xx answer | Provider failure |
| Malformed JSON | Provider failure |
| A network error | Provider failure |

### 7.2 Steam tags lookup

1. The Steam tags are `steamTags` and `steamFailedAt` in the record.
2. They are only asked for games with a Steam id, after the other secondary lookups.
3. They follow the rules of [Calls and failures](#71-calls-and-failures), with one difference: a store page without
   tags is a successful empty list.
4. A failed Steam fetch keeps the tags stored earlier and is not shown as an error. Without earlier tags the pills
   use IGDB.
5. The record is retried after the 30-minute backoff.

### 7.3 PCGamingWiki

PCGamingWiki notes are reduced to reader text (references, templates' named arguments, URLs and link markup removed)
before the native-HDR heuristics run. The wiki's own HDR value decides:

| Wiki HDR value | Result |
|---|---|
| `false` | SDR |
| `hackable` | SDR. Notes never promote `hackable`, even when they describe native HDR output via Engine.ini. |
| `unknown` | Unknown |
| Missing value | Unknown |
| `true` | HDR |
| `always on` | HDR |
| `limited` | HDR (native HDR with flaws, e.g. No Man's Sky) |

A note withdraws a native value only when it says one of these:

- HDR needs Special K/ReShade/Auto HDR/RTX HDR/a mod.
- HDR is console/edition specific.
- HDR is "not native"/"not supported".
- HDR is currently broken, disabled, simulated or does not work.

Config files, command lines, improvement mods and "limited" brightness are caveats, not retractions.

Page lookup:

1. A game is looked up by every IGDB Steam AppID.
2. When no page lists one (or IGDB lists none), the game's title is used. The table below lists the pages that
   count.
3. After a Steam miss only a page whose Cargo row names no Steam app at all counts. Euro Truck Simulator 2's row has
   an empty AppID although the page is its own.
4. A page naming another Steam app is a different product.
5. One Steam ID or several, and one wiki page or several, only yield a result when the pages agree.

| Title lookup accepts | Example |
|---|---|
| A page named exactly that | |
| The target of a wiki redirect of that name | "Sea of Stars: Sunset Edition" is a redirect to "Sea of Stars" |
| A page whose title differs only in case and punctuation | "The Witcher 3: Wild Hunt: Remastered" |

### 7.4 HowLongToBeat

1. The unofficial HLTB client uses its website search/detail data, specifically positive `comp_all` with
   submissions, not `comp_main`, `comp_all_avg`, or an invented average.
2. Without a Steam ID the name decides: exactly, or the plain game for an edition title ("Sea of Stars: Sunset
   Edition" is listed as "Sea of Stars"), never an edition entry for a plain title.
3. One lookup is paced, skips a detail page that cannot be read, and stops on an HTTP error.
4. The discovered endpoint must itself be a search API.
5. The chip preserves the rendered **HowLongToBeat** headline text, including half-hours. Pages without the banner,
   such as some co-op games, get the same text from `comp_all` by the website's rounding.
6. Unrounded seconds drive the filters.
7. HowLongToBeat has no credentials and is queried directly from the device (see
   [Credentials and privacy](#8-credentials-and-privacy)).

| Limit | Value |
|---|---|
| Search pages read by one lookup | At most two |
| Detail pages read by one lookup | At most five |
| A failed search-endpoint discovery is remembered for | Five minutes |

## 8. Credentials and privacy

1. The IGDB client secret and the PCGamingWiki bot password are Worker secrets and never ship in the APK. Deploy and
   secret handling: [tools/metadata-worker/README.md](../../tools/metadata-worker/README.md).
2. Never commit credentials or log tokens/passwords.
3. Provider attribution is visible in Settings → About.
4. The adapted HLTB client's MIT notice is bundled in assets.

| Party | Sees | Logs |
|---|---|---|
| The metadata Worker | The game names the app looks up and the client IP address (Cloudflare's request metadata) | No query text, only a short hash on failures |
| HowLongToBeat | The device's IP and game names; it has no credentials and is still queried directly from the device | |
| IGDB's image server (`images.igdb.com`) | The device's IP and the image ids of the screenshots and of similar games' covers the overview shows; the images load directly from the device, without credentials | |
| YouTube (`i.ytimg.com`, `youtube.com`, `youtube-nocookie.com`) | The device's IP and the video ids whose thumbnails the overview shows, and one oEmbed request per video id (`youtube.com/oembed`, no cookies) that tells whether the video may be embedded. A video that is opened plays in the app's own YouTube player in privacy-enhanced mode (`youtube-nocookie.com`): YouTube sets no cookies before playback starts; once the video plays, YouTube may set cookies and local storage in the app's web view and plays its ads. Nothing is opened in a browser or the YouTube app | |

## 9. Game overview

Variant **A · Descent** of issue [#107](https://github.com/DELOS-STREAM/DELOS-android/issues/107), chosen by the owner on
2026-11-20: the dashboard continues downward into one vertical page about the focused game. Code:
`shell/screens/GameOverview.kt` and the `Overview*.kt` files beside it; the data: `GameDetails.kt`, `IgdbImageCache.kt`.

### 9.1 Opening and closing

1. Games with an IGDB match have an overview; built-in apps (Desktop, Steam Big Picture) and unmatched games have none.
   Down on those nudges the cover by 7 px.
2. A **"Game info" chip** sits on the focused cover's lower edge, centred, 22 px above it, in the dark glass of the cover's
   badges (so it reads over any art), drawn for the input in use:

   | Input | Glyph | Opens with |
   |---|---|---|
   | Controller | The D-pad cap with the down arm pressed (outline cut), bobbing | Down |
   | Keyboard | The ↓ key cap, bobbing | ↓ |
   | Touch | A small arrow up, bobbing up | A swipe up on the cover, or a tap on the chip |
   | Mouse | A small arrow down, bobbing; the chip darkens on hover | A click on the chip |

   The hint bar does not repeat it: the chip on the cover is the only place that names it. It stays clear of the hint
   bar, whose library note ("LIBRARY · N apps on <PC>") shows as on any other game.
3. A swipe up needs vertical intent (Compose's vertical touch slop, so the row's horizontal scroll and the side-menu swipe
   keep working). The cover follows the finger with resistance (`lift = max(dy, -260) × 0.55`, growing up to
   1.155) and opens past 40 px of lift; the flight starts where the finger left it.
4. **Opening** (`OverviewMotion`, all transforms and opacity, read while drawing). The card stays in its row until the
   overview's first layout knows the header's slot; then the flight takes over from it, at its place. The motion runs on
   a clock that advances at most 33 ms per frame (`SteadyFrameClock`): the overview's first frames are its heaviest, and
   on the frame clock itself they used to swallow most of the motion (the cover landed in the first frame that moved).
   A slow frame now holds the motion back instead of skipping it:
   - the other covers lift away one after another: 160 px + 30 px per cover of distance up, to 0.92, fading, 620 ms
     ease-out each, 45 ms apart by distance;
   - the info row rises 90 px out and fades;
   - the cover flies from the card's on-screen rectangle (with its focus scale or its dragged position) into the
     340 × 510 slot of the header: 780 ms `cubic-bezier(.2,.9,.25,1)`, through a midpoint 30 px above the straight line
     and 4 % larger, the corner going from 27.4 to 30 px. Only its frame changes shape (the wide first card's turns
     portrait): the pictures in it keep their proportions, cropped to the frame, and the card as the row drew it (its own
     art, a wide card's too, the current frame of its animation, its badge) crossfades into the header's cover over the
     first 60 % of the way;
   - the name flies from the dashboard's name to the header's (720 ms); a SteamGridDB logo does not fly: the dashboard's
     fades out where it is over the first 40 % of the flight and the header's fades in at its own place over the last
     50 % (and the other way round on closing); when the dashboard showed the other kind, the header's title rises in
     like its other parts;
   - the page rises from 120 px below (780 ms), the header's parts rise 18 px, 46 ms apart, after 260 ms (the cue
     after 500 ms);
   - the backdrop's own layers fade in over 900 ms; the rings fill and count up after 380 ms (1.3 s, ease-out-quart).
5. Nothing delays a control: Play has the focus from the first frame and the page takes input while it enters. The
   gold ring appears once the opening is over, for every input: a finger's first tap only focuses (focus first), so
   the focused part shows its ring and its focus growth after a tap too.
6. **Closing** (B, Esc, Back, Up from the header, the round back button, or pulling the page down 150 px at its top):
   a close during the opening waits for it; the card takes the focus again and the hint bar shows the dashboard's hints,
   the page scrolls to its top (420 ms), then everything reverses: the cover flies back into its card (640 ms
   ease-in-out), the page sinks 120 px and fades (560 ms), the header's parts leave first (240 ms). The dashboard comes
   back with the cover, as on the iPad: the other covers and the info row return together, 640 ms ease-in-out from
   where the opening left them, and arrive with the card (no stagger, no jump at the end). The page leaves composition
   one frame after the motion's last one.
7. **Reduced motion** keeps the fades and drops every transform: the cover and the title fade in place, nothing lifts,
   rises, drifts or bobs, the ring jumps.
8. App settings opens the per-app settings; when they close the overview is there again without the flight.

### 9.2 Layout (1920 × 1080 stage)

1. The page has 150 px sides. It fades in under the status bar (clear to 70 px, solid from 140 px) and out at the
   bottom 60 px. The backdrop darkens only under the page's text: towards the bottom, and at the left under the header
   from below the top bar (from 140 px, whole from 260 px). Nothing darkens the top bar, the brand or the PC: no band,
   no vignette.
2. **Header**, one screen minus 160 px high, 150 px from the top: the 340 × 510 cover (30 px corner, deep shadow)
   and, 72 px beside it, 24 px apart: the year and developers (15 px tracked accent eyebrow); the title: the game's logo
   when it has one (the dashboard's: the user's SteamGridDB choice, else the default logo, its animation playing; up to
   700 × 140 px), else the name (68 px Unbounded, up to 1150 px wide, balanced lines, the game's color gradient); the
   genre and theme tags (two each, as on the dashboard) and the HDR badge; the critics' and players' rings (104 px) with
   the age rating of the device's region; **Play** and **App settings** (86 px pills, 54 px at their sides); the
   streaming time, last streamed
   and HowLongToBeat.
   At the header's bottom a cue names the sections below ("About · Media · Details · Similar games") with a bobbing
   caret.
3. **Sections**, 70 px apart, each with a 30 px Unbounded heading and its icon:
   - **About**: the summary (28 px) with the storyline expanding in place under it (600 ms) and a "Read the story" /
     "Show less" button; a 560 px glass facts card (developer, publisher with "self-published", release date,
     ported by, engine, series);
   - **Screenshots and videos** with its count: a full-width carousel of 520 × 292 thumbnails, videos first; each
     picture lags 7 % behind the carousel's movement (parallax);
   - **Details** (glass card, two columns): genres, themes, game modes, perspective, platforms, age ratings with
     descriptors, multiplayer, languages, keywords (5);
   - **Your streaming** (glass card, three columns): the streaming time counting up when the card is first focused,
     last streamed (or "Running on <PC> now"), HowLongToBeat with a bar of the share streamed;
   - **Links**: IGDB's websites, the official site first (or the Steam page when IGDB lists none);
   - **Similar games**: 260 × 390 covers (24 px corner), "In library" on the ones the user owns;
   - the data credit.
4. A section without anything to show does not exist. While the details load, a spinner line stands in for the
   sections; when they cannot be loaded, one muted line. Ratings: no critics' rating reads "No critic reviews yet",
   fewer than 50 player ratings "… · few so far", no age rating "Not rated".
5. The glass cards blur the backdrop behind them for real (Haze, 30 px) under the prototype's light gradient.
6. The **backdrop** continues the dashboard's own background: its hero exactly as the dashboard shows it (the user's
   background blur, playing when animated) is drawn again from the shell's recording, not decoded or blurred a second time.
   Over it a 40 px blurred layer of the hero is crossfaded in by the scroll over 520 px (opacity only, no animated blur
   radius); it drifts up to 70 px slower than the page and zooms up to 10 %. Focusing a screenshot crossfades it to that
   screenshot, sharp (62 %) and blurred (800 ms), leaving the media back to the hero.
7. The header's cover plays its animated artwork as the card does (the art-motion setting and reduced motion apply); it
   stops when the overview leaves.
8. The page is a blur source over the shell's background: the top bar's glass and the screenshot viewer blur what lies
   under them now (the background and the page as it scrolls), not the dashboard behind the page.

### 9.3 Navigation

1. Up and Down walk the sections, Left and Right walk a row; the buttons, the carousel, the links and the similar
   games are rows the focus does not leave sideways. The nearest element in the direction wins (sideways distance
   weighs 2.2 times), and going back the way one came returns to where one came from (`OverviewNavigator`).
2. Up from the header goes back to the dashboard, the natural reverse of Down.
3. The focused section scrolls to 132 px under the top (620 ms ease-out); in a section taller than the view the
   element comes into view with 160 px around it; the header scrolls to the top. The carousel keeps 150 px beside the
   focused item.
4. One gold ring glides between the elements (560 ms, slight overshoot) in the page's coordinates, so it scrolls with
   the page and rides along with a scrolling carousel. A finger sees it and the focused part's growth too: its first
   tap only focuses, and the ring shows that it did.
5. Touch: focus first (a tap on an element that is not focused only focuses it), drag to scroll, pull down at the top
   to close. Mouse: hovering focuses without scrolling, the wheel scrolls, a click activates.
6. Activating: Play starts or resumes the game (with another game running, the game's menu offers the choices);
   App settings opens the per-app settings; a screenshot or video opens the viewer; a link opens the browser; a
   similar game the user owns closes this overview and opens its own; one outside the library says so in a toast.

### 9.4 Screenshot and video viewer

1. It zooms out of the thumbnail (560 ms) and back into it (440 ms), or fades when the thumbnail is scrolled away.
2. A screenshot is as large as fits 280 × 300 px inside the stage, 16:9; the thumbnail stays until the full picture
   (`screenshot_huge`, `1080p` on a 4:3 stage) is decoded. The page behind is blurred (40 px, a dark tint).
3. Left and Right browse: the new picture slides in 160 px from the side of the key while the old one leaves 90 px
   the other way; at the ends the picture nudges. L1 and R1 (keyboard `[` `]`) browse too, also on a video. B closes.
   The hint bar shows Browse and Back.
4. A filmstrip of the items sits 112 px above the bottom, the current one larger with the accent ring; the count and
   the item's name sit top left. A mouse gets round previous and next buttons; a mouse and a finger get a close
   button. A finger swipes sideways to browse and down to close.

**Videos** (`OverviewVideo.kt`, `YouTubeEmbeds.kt`). Trailers play only inside the app; nothing opens a browser or the
YouTube app.

1. A video is as large as fits 280 × 440 px inside the stage, 16:9, 70 px above the middle (1137 × 640 at 1080p), so
   its control bar fits under it: a glass bar 72 px tall, 22 px below the video, as wide as it, with back 10 s,
   play/pause, forward 10 s, the time, a progress track (a tap or click seeks there), the length and the sound. Nothing
   of DELOS lies on the player (YouTube's terms).
2. Once the viewer has settled on a video, YouTube's IFrame player loads in a web view on the picture: privacy-enhanced
   host `youtube-nocookie.com`, inline, autoplay, YouTube's own controls off (`controls=0`; the DELOS bar drives it
   through the IFrame API), related videos only from the same channel (`rel=0`), no annotations, no full-screen button.
   The page's base URL is `https://<application id>`, which sets the Referer YouTube requires of embedding apps. The
   thumbnail stays until the video plays. The web view never takes the focus; a finger or a mouse may use YouTube's own
   surface (an ad's Skip), but every navigation away from the page and every new window is refused. Ads are not blocked.
   Browsing away or closing removes the player; the app going to the background pauses it.
   The web view lives in a window of its own over the app's (`OverviewPlayerWindow.kt`: an application panel, hardware
   accelerated, that never takes the focus, so keys and Back stay with the shell), with the thumbnail, the DELOS overlay
   and the hint bar composed over it in that window; drawn inside the shell's Compose layers, a trailer played its sound
   over a black picture. The app going to the background removes that window and coming back puts it back. When a video
   starts, logcat tag `OverviewPlayer` (and the diagnostic log) gets `Playback started: webView.isHardwareAccelerated=…,
   layerType=…, windowType=…, windowHardwareAccelerated=…, window=W×H`; `true`, `NONE`, `1000` (application panel) and
   `true` are the expected values.
3. Keys on a video: Left and Right seek 10 s, A plays or pauses (from the start once it has ended), Y mutes or turns
   the sound on (keyboard F or M), L1 and R1 browse, B closes. The hint bar shows Seek 10 s, Play or Pause, Mute or
   Sound on, Previous, Next and Back.
4. Videos YouTube will not embed are left out of the carousel: when the details arrive, YouTube's oEmbed endpoint is
   asked once per video id and process (401: the owner disabled embedding; 400, 403, 404: invalid, private or
   removed); a failed check keeps the video. The player's errors 100, 101 and 150 drop a video the same way. The open
   viewer keeps its items until it closes.
5. A video that still cannot play shows its thumbnail dimmed with "This video can't be played" and why (its owner does
   not allow other apps, or YouTube could not load it, also when the player has not answered within 15 s), without any
   button; Left and Right browse then.

### 9.5 Data and caching

1. The details come from the Worker's `GET /v1/igdb/game-details?id=<igdb id>` (shape:
   [tools/metadata-worker/README.md](../../tools/metadata-worker/README.md), "Game details"). They are fetched when the
   overview opens or after a game has been focused for a second.
2. They are stored with the game's metadata record (cache schema 7) and refreshed after 7 days; a failure backs off
   30 minutes like the other providers and keeps the stored details. A game IGDB does not know is remembered as
   having no details until the next refresh.
3. The cover is exactly the library card's image (the user's SteamGridDB choice, else the default card art); the
   game itself needs no IGDB cover. Similar games the user owns show their library card, the others IGDB's
   `cover_big`.
4. IGDB images load directly from `images.igdb.com`; they are kept in a 150 MB disk cache (`cacheDir/igdb-images`)
   and a 32 MB memory cache of decoded pictures, decoded no larger than they are drawn. The first two thumbnails and
   the first full screenshot are fetched while the overview opens.


## Settings

| Setting | Kind | Value | Effect |
|---|---|---|---|
| `DELOS_METADATA_URL` | Non-secret `BuildConfig` string | The Worker's base URL; defaults to empty | Game metadata needs it |
| Genre, theme, HDR and duration filters | Stored per profile | See [Sections](#42-sections) | Filter the library of the active profile |
| *Match the game's colors* | App setting | On or off | Off: the pills use the palette's accent hues ([Pill colors](#25-pill-colors)) |

`DELOS_METADATA_URL`:

1. The value is read from the environment variable of that name or from the ignored `local.properties`. An empty or
   blank variable is ignored.
2. With the empty default the app reports "Game metadata is not available in this build" without contacting any
   provider.
3. CI builds carry no URL, so the feature is inert there.

## Code map

| Path or class | Role |
|---|---|
| `shell/ui/GameTags.kt` | `topGenres` and `topThemes`: the entries of the genre and theme pills; `rankedGenres` and `rankedThemes`: the IGDB ranking, whose first entries are `mainGenre`/`mainTheme` |
| `shell/ui/SteamTags.kt` | The mapping of Steam tag names to IGDB genres and themes |
| `shell/ui/GenreIcons.kt` | `genreIcon` and `themeIcon`: the Phosphor icon of every genre and theme |
| `res/values/strings_tags.xml` | The names shown, by IGDB id (`genreName`, `themeName`) |
| `tools/fonts/icons.txt` | Source of the theme icons, like every other icon |
| `HeroBitmaps.coverHues` | The game's cover colors used by the pills |
| `MarqueeText` | The edge fade and scrolling of the badge row when it does not fit |
| `tools/metadata-worker` | The Delos metadata Worker: IGDB, PCGamingWiki and `GET /v1/steam/tags?app=<id>` |
| `DELOS_METADATA_URL` (`BuildConfig`) | The Worker's base URL |
| `screens/GameOverview.kt`, `OverviewController.kt` | The game overview's page and its opening and closing timeline |
| `screens/OverviewNav.kt`, `OverviewModel.kt` | Its navigation, scroll targets and motion; what it shows from the details (`OverviewNavTest`, `OverviewModelTest`) |
| `screens/OverviewFocus.kt`, `OverviewParts.kt`, `OverviewViewer.kt` | Its gliding ring, its parts (rings, tags, age badges, glass) and the screenshot viewer |
| `data/GameDetails.kt`, `data/IgdbImageCache.kt` | The details' model and parser, IGDB image URLs and the image caches |

## Verification

### Not verified

The checks that apply to every GUI document are in [GUI.md](../GUI.md#not-verified).

## Related

- [START_AND_HOME.md](START_AND_HOME.md): the home screen and "Streaming time".
- [ARTWORK.md](ARTWORK.md): SteamGridDB artwork, its matches and its progress pill.
- [MENUS_AND_SETTINGS.md](MENUS_AND_SETTINGS.md): App options and Settings → About.
- [CONTROLS_AND_TEXT.md](CONTROLS_AND_TEXT.md): `MarqueeText`.
- [LOCALIZATION.md](../LOCALIZATION.md): translated names and English data.
- [ICONS.md](../ICONS.md): the icon font.
- [tools/metadata-worker/README.md](../../tools/metadata-worker/README.md): Worker deployment and secrets.
- [GUI.md](../GUI.md): index of the GUI documents.
