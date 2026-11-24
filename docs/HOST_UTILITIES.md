# Host utilities in the app list

Built-in entries that streaming hosts add to the app list. DELOS gives them bundled artwork and never looks them up on SteamGridDB (`shell/data/BundledArtwork.kt`). Research 2026-11-24 from each project's source:

| Host | Repository @ commit | Where the entries are defined |
|---|---|---|
| Sunshine | `LizardByte/Sunshine` master (raw files) | `src_assets/{windows,linux,macos}/assets/apps.json` |
| Apollo | `ClassicOldSong/Apollo` @ `0fb740a` | `src_assets/*/assets/apps.json`; `src/process.h:37-40` (UUIDs); `src/process.cpp:1417-1540` (runtime entries) |
| Vibepollo | `Nonary/Vibepollo` @ `adc5c5a` (fork of Apollo) | `src_assets/*/assets/apps.json`; `src/legacy_generated_app_identities.h:7-10`; `src/builtin_virtual_display.h`; `src/process.cpp:4925-4975`; `src/remote_session.cpp:57-63,147-160,180-187`; `src/config_playnite.cpp:66-85` |
| Vibeshine | `Nonary/vibeshine` @ `aa6699a` (fork of Sunshine) | `src_assets/*/assets/apps.json`; `src/remote_session.cpp:56-62,141-151`; `src/confighttp.cpp:1782,2337` |
| Sunshine Foundation | `qiin2333/Sunshine-Foundation` @ `1f9ea16` | `src_assets/*/assets/apps.json` (no runtime entries found in `src/process.cpp`) |
| ArtLight | `onaiaku/ArtLight` @ `95254e2` | `server/src/process.h:44-47`, `server/src/process.cpp:4648-4752`, `server/src/remote_session.cpp:78-84` (Apollo's and Vibepollo's sets together) |

## Entries

"File" = written in the default `apps.json` at install (the user can rename or delete it; it gets a random UUID on Apollo-family hosts, none on Sunshine). "Runtime" = added by the host to every app list, not in `apps.json`, name fixed in code.

| Host | Name as listed | Fixed UUID | What it does | Origin |
|---|---|---|---|---|
| Sunshine, Apollo, Vibepollo, Vibeshine, Sunshine Foundation, ArtLight (all OS) | `Desktop` | none | Streams the desktop, no command | File |
| Sunshine, Apollo, Sunshine Foundation (Linux only) | `Low Res Desktop` | none | Desktop after an `xrandr` mode change | File |
| all of the above (all OS) | `Steam Big Picture` | none | Opens Steam in Big Picture (`steam://open/bigpicture`) | File |
| Sunshine before the rename | `Steam BigPicture` [UNVERIFIED] | none | Same | File (old installs keep it) |
| Apollo, ArtLight | `Desktop (fallback)` | `EAAC6159-089A-46A9-9E24-6436885F6610` | Desktop; shown only when `apps.json` could not be loaded | Runtime |
| Apollo, Vibepollo, ArtLight (Windows, virtual display driver OK) | `Virtual Display` | `8902CB19-674A-403D-A587-41B092E900BA` | Desktop on a new virtual display of the client's size | Runtime |
| Apollo, Vibepollo, ArtLight (option "input only mode") | `Remote Input` | `8CB5C136-DA67-4F99-B4A1-F9CD35005CF4` (= `NvApp.REMOTE_INPUT_UUID`) | Controller/keyboard to the host, no video | Runtime |
| Apollo, Vibepollo, ArtLight (same option) | `Terminate` | `E16CBE1B-295D-4632-9A76-EC4180C857D3` | Ends the running app; not a stream | Runtime |
| Vibepollo, Vibeshine, ArtLight | `Resume` | `9a1c5a25-58fe-40e0-b9aa-7d3f00000001` | Resumes the running session | Runtime (remote session controls) |
| same | `Disconnect Monitor` | `…7d3f00000002` | Drops this client's monitor session | Runtime |
| same | `Disconnect Input` | `…7d3f00000003` | Drops this client's input session | Runtime |
| same | `Terminate` | `…7d3f00000004` | Ends the running app | Runtime |
| same | `Remote Monitor` | `…7d3f00000005` | Watch the running session (video, no input) | Runtime; Vibeshine/Vibepollo can also restore it into `apps.json` with this UUID |
| same | `Remote Input` | `…7d3f00000006` | Input into the running session, no video | Runtime; same note |
| same | the running game's own title | `…7d3f00000007` | A duplicate tile of the running game ("resume here") | Runtime; **a real game: keep its normal art** |
| Vibepollo, Vibeshine (Playnite option) | `Playnite (Fullscreen)` | none (flag `playnite-fullscreen` in `apps.json`) | Opens Playnite's fullscreen mode | Written into `apps.json` by the host |
| Sunshine Foundation (Windows) | `Big Screen` | none (`default-id: big-screen`) | The fork's own big-screen launcher | File |
| Sunshine Foundation (Windows) | `Xbox Game` | none | Opens the Xbox app (`start xbox:`) | File |
| Sunshine Foundation (Windows) | `Touch Keyboard`, `Desktop Pet` | none | Menu commands under Desktop [INFERENCE: nested under `menu-cmd`, likely not app-list entries] | File |
| GeForce Experience GameStream (discontinued) | `Steam`; `Desktop` only when the user added `mstsc.exe` [UNVERIFIED] | none | Steam Big Picture; desktop | Host scan / user |

Not read (other server families, names not verified): Wolf (Games on Whales), Duo, Sunshine forks without their own entries.

Facts that matter for matching:
- Vibepollo/Vibeshine put **leading spaces** before the titles of the runtime controls and of the running-game duplicate (`ranked_title`, 1 to 5 spaces) so that sorting clients list them first. Names must be trimmed before comparing.
- The same name can carry different UUIDs: `Remote Input` is `8CB5C136-…` (Apollo's own entry) or `9a1c5a25-…06` (remote session control); `Terminate` is `E16CBE1B-…` or `9a1c5a25-…04`.
- Vibepollo treats `Remote Input`, `Remote Monitor`, `Virtual Display`, `Terminate` as reserved names (`remote_session.cpp:90-94`): a user's own app cannot take them there.
- No host localises these names; all are English literals in code or in `apps.json`. A user can rename the file entries.

## Matching rule

1. **UUID first**, case-insensitive, exact:
   - `8CB5C136-DA67-4F99-B4A1-F9CD35005CF4`, `9a1c5a25-58fe-40e0-b9aa-7d3f00000006` → Remote Input
   - `9a1c5a25-…05` → Remote Monitor
   - `8902CB19-674A-403D-A587-41B092E900BA` → Virtual Display
   - `EAAC6159-089A-46A9-9E24-6436885F6610` → Desktop
   - `E16CBE1B-295D-4632-9A76-EC4180C857D3`, `9a1c5a25-…04` → Terminate
   - `9a1c5a25-…01` → Resume; `…02` → Disconnect Monitor; `…03` → Disconnect Input
   - `9a1c5a25-…07` → **not a utility**: look the art up by the trimmed title as for any game (the tile's ID differs from the game's, so the cache key should be the title or the real game's UUID).
2. **Then the name**, trimmed (leading/trailing whitespace) and lower-cased, exact match only (no "contains": "Desktop Dungeons" is a game):
   - `desktop`, `desktop (fallback)`, `low res desktop` → Desktop
   - `virtual display` → Virtual Display
   - `remote input` → Remote Input; `remote monitor` → Remote Monitor
   - `terminate` → Terminate; `resume` → Resume; `disconnect monitor`, `disconnect input` → Disconnect
   - `steam big picture`, `steam bigpicture`, `steam` → Steam Big Picture
   - `playnite (fullscreen)`, `playnite` → Playnite
   - `big screen` → Big Screen; `xbox game` → Xbox
3. A match means: **never query SteamGridDB** for this app, use bundled art. A user-chosen custom image should still win over the bundled one.
4. Risk of the name rule: a real game called exactly "Resume", "Terminate", "Steam" or "Desktop" would get utility art. [INFERENCE] negligible; the user's own art override covers it.

