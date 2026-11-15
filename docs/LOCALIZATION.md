# Localization

## Purpose

How Delos text is stored, written, translated and checked: the languages, where English text lives in the code,
the rules for writing a string, the glossary of each language and the tests that enforce them.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Languages, string files, keys, writing rules, brand names | Where a text appears on screen and how much room it has: [GUI.md](GUI.md) |
| Tone, glossaries and terminology per language | The settings catalog that reads its texts from resources: [SETTINGS.md](SETTINGS.md) |
| Text fit, fonts and scripts, tooling | |

Tracking issue: [#10](https://github.com/Meitoncz/Delos/issues/10).

## Languages

English is the base and the wording of the app itself. No inherited Moonlight/Artemis translation remains. Every
language is translated from the English text of Delos.

| Tag (`locales_config.xml`) | Folder | Language | Address |
|---|---|---|---|
| `en` | `values` | English (base) | neutral |
| `cs` | `values-cs` | Czech | informal *ty* |
| `es` | `values-es` | Spanish | informal *tú* |
| `pt-BR` | `values-pt-rBR` | Portuguese (Brazil) | *você* |
| `fr` | `values-fr` | French | informal *tu* |
| `de` | `values-de` | German | informal *du* |
| `ja` | `values-ja` | Japanese | polite です/ます |
| `ko` | `values-ko` | Korean | polite 해요체 |
| `zh-CN` | `values-zh-rCN` | Simplified Chinese | neutral, no honorifics |
| `uk` | `values-uk` | Ukrainian | informal *ти* |

The list lives in three places that must agree. Adding a language means editing all three.

| Place | Role |
|---|---|
| `res/xml/locales_config.xml` | The languages the system's per-app language picker lists |
| `language_names` / `language_values` in `res/values/arrays.xml` | The in-app Language setting |
| `SUPPORTED_*` in `StringResourcesTest` | Fails when the lists differ or when a `values-<lang>` folder exists for an unsupported language |

## How English text lives in the code

1. **Every user-facing string is a resource** in `res/values/strings_<area>.xml`. No literal goes into Kotlin or Java.
   `tools/l10n/find_literals.py` lists what is still hardcoded in the shell. It reports none; the count stays at zero.
2. **Inside composition** the Compose resource functions are used directly:
   `stringResource(R.string.profile_rename)`, `stringResource(R.string.profile_restored, name)`,
   `pluralStringResource(R.plurals.profile_signed_in, count, model, count)`.
3. **Outside composition** (toast, dialog and menu callbacks, `ShellState`/`ContextMenu`/`DialogSpec` builders, stores
   that produce messages) the text is resolved when the object is built, from a `Resources` the screen captured in
   composition.
   - `val res = textResources()` (`shell/ui/Text.kt`) captures it. It reads the configuration, so the screen recomposes
     when the language changes.
   - The text is then `res.getString(R.string.x, arg)` / `res.getQuantityString(R.plurals.y, n, n)`.
   - `res` is passed down as the first parameter of the helper functions, as `ProfileScreen.kt` does.
   - `ShellState`, `Toast`, `MenuItem` and `DialogSpec` keep holding plain `String`s: they are already localized when
     created.
   - A `@StringRes` id is not stored in a model that outlives the language.
4. **Plain Kotlin classes with no screen** (data/ clients, stores) return a typed result (an enum, a sealed class,
   error codes) and the screen maps it to a string. They do not get a `Context` just to format a message. A message
   that must stay English (a log line, `DiagLog`, a developer-only error) stays a literal. It is excluded from the
   finder by being a log call, or with a trailing `// l10n-ignore`.
5. **Legacy Java UI** (stream activity, `GameMenu`, preferences XML) keeps using `R.string`. Those 381 strings stay in
   `res/values/strings.xml`, which is the "legacy" area. A legacy string that moves to the shell moves to its area
   file.
6. **Not text:** technical readouts (stream statistics, `%.1f ms`, resolutions like `1920 × 1080`, codec names, key
   names such as `Ctrl`) are formatted in code with `Locale.US` on purpose and are not translated. A surrounding
   sentence is translated.

### The Language setting and per-app locales

One source of truth: the **Android per-app locale**, wrapped by `shell/settings/AppLanguage.kt`. The in-app
**Language** row (`list_languages`; `default` follows the system) and the system's per-app language picker (Android
13+, which lists exactly `locales_config.xml`) change the same value, so they always agree. There is no
`UiHelper.setLocale` and no manual `Configuration` override.

**Android 13+**

- `AppLanguage.choose` calls `LocaleManager.setApplicationLocales`.
- The system updates the process and every activity.
- `ShellActivity` and `DelosStreamActivity` declare `locale` in `configChanges`, so they change in place, with no
  restart.
- Compose recomposes because `LocalConfiguration` is replaced.
- `stringResource` / `textResources()` read the activity's own `Resources`, which the system updated.
- `Locale.getDefault()` (number and date formatting, Han glyph forms) follows too.

**Android 10-12** (minSdk 29)

- There is no platform per-app locale.
- The tag is kept in the `app_language` preference file.
- `AppLanguage.wrap` applies it in `attachBaseContext` of every activity (shell, stream, help, shortcut trampoline,
  external display control, companion). `AppLanguage.initApplication` applies it for the application context.
- Choosing a language recreates the foreground activity. The shell returns to its start screen, because its
  navigation state is not restored.
- `AppCompatDelegate.setApplicationLocales` and its `AppLocalesMetadataHolderService` were considered and are not
  used: they need an `AppCompatActivity` with an AppCompat theme, and the shell is a `ComponentActivity`.

**Default**

- No app locale set = follow the system language.
- The resource system then picks the app's translation of the first system language that has one, else English.
  `AppLanguage.effectiveTag` models this for the UI.
- `pt` resolves to `pt-BR`, and `zh` / `zh-Hans` to `zh-CN`.
- `pt-PT` and `zh-TW` have no translation and show English.

**The row is a mirror**

- `ShellActivity` keeps the current profile's `list_languages` equal to the app locale: `AppLanguage.mirror` runs when
  it binds and on resume, so a change made in system settings shows in the row.
- `ShellActivity` turns a change of that preference into `AppLanguage.choose` (`onPreferenceChanged`).
- The language is therefore app-wide, never per profile.
- A language stored in the old, stream-only preference is adopted once at first start (`initApplication`) if no app
  locale is set.

**Callers**

- Nothing else needs to apply the language.
- An activity calls `AppLanguage.wrap` in `attachBaseContext` (needed on Android 10-12 only) and declares `locale` in
  `configChanges` if it must not restart.
- Code that must re-read the language reads `LocalConfiguration.current` (that is what `textResources()` does).

**Fallback**

- An Android locale without a `values-<lang>` folder, or a key missing from one, shows the English text.
- This is intentional while languages are being filled in: lint `MissingTranslation` is off, and
  `StringResourcesTest` enforces coverage rules on every folder that exists.

### Settings catalog and settings help

`SettingsCatalog` reads titles, summaries, entries and dialog messages from `res/xml/preferences.xml` and `arrays.xml`
through the resources it was given, so they come from the legacy strings of the current language. There is no
`Locale.ENGLISH` override. The help texts are string resources too.

**Catalog refresh**

- The catalog rebuilds itself when the language changes: a screen that shows labels calls
  `profiles.catalog.refresh(textResources())` before it reads `all` / `visible`. The screen already recomposes on a
  language change because `textResources()` reads the configuration.
- Keys, kinds, defaults and ranges do not depend on the language, so stores (`StreamSettingsStore`,
  `StreamProfileStore`) keep using the shared catalog without refreshing it.
- The legacy summaries that the catalog reads also appear in the classic UI. They are the same resources, so there is
  one text.

**Help texts**

| Text | Resource name | Lookup |
|---|---|---|
| Description of a setting | `help_<setting key>` in `res/values/strings_settings_help.xml` | `SettingsHelp.description(res, key, fallback)` |
| Per-choice explanation | `help_<setting key>_<value>` in `res/values/strings_settings_help.xml` (`help_list_fps_60`) | `SettingsHelp.choice(res, key, value)` |
| Dynamic texts | `help_dynamic_*`, formats with a positional argument | Fallback of the two lookups above |
| Stream menu help | `stream_help_<slug of the item key>` | `StreamMenuHelp.description(res, key)` |

- In a per-choice name the value is lower-cased and every character but letters and digits becomes `_`, so
  `fast-vsync` is `help_frame_pacing_fast_vsync`.
- `SettingsHelp.description` and `SettingsHelp.choice` find the strings with `getIdentifier` and fall back to the XML
  summary or to the dynamic texts.
- The stream menu help keys stay the English item names, which are identifiers: `MenuKey` / `MoreRow` constants and
  the rumble preset enum name, never shown. `StreamMenuLayoutTest` fails when a menu key has no `stream_help_*`
  string.
- Lint cannot see `getIdentifier` uses, so `res/raw/keep.xml` keeps `help_*` and `stream_help_*`.
- These strings are always formatted, so a literal percent sign is written `%%` there.

**Other settings texts in `res/values/strings_settings.xml`**

| Text | Source |
|---|---|
| Settings tabs | `SettingsTab.label(res)` / `description(res)` |
| Appearance choices | `Palette`, `BackgroundKind`, `ClockFormat`: `label(res)` |
| Labels for native resolutions, custom values, layouts | `SettingsCapabilities.choices(res, spec, values)` |
| Typed-text errors | `StreamSettingsStore.writeText(res, …)` |

**Help length**

- `SettingsHelp.LINE_CHARS` / `MAX_LINES` and the tests that check help length describe the **English** text in the
  432 px box. They run against the base `values/` resources (`qualifiers = "en"`).
- Translations are not held to them (see [Text fit](#text-fit)); the box scrolls.

### Genre and theme names

- The genre and theme names on the game card and in the Y filter are resources in `res/values/strings_tags.xml`
  (`tags_genre_*`, `tags_theme_*`). They are looked up by IGDB's fixed ids, not by text.
- `genreName(res, genre)` / `themeName(res, theme)` in `shell/ui/GenreIcons.kt` return the translated name at draw time
  (`textResources()` in composition), and IGDB's English `name` for an id that has no string yet.
- Genre and theme ids overlap numerically, so the two lookups are separate.
- The ranking, the filters and the Steam mapping (`shell/ui/SteamTags.kt`, `GameTags.kt`) keep the ids and the English
  names as **data**.
- The Steam tag names are matched against what the store returns (always English) and are never shown or translated,
  which is why those table lines carry `// l10n-ignore`.
- A genre or theme that is added needs its id in `IGDB_*_NAMES`, `genreNameRes`/`themeNameRes`, the icon functions and
  a string. `TagNamesTest` fails when they disagree or the English text differs from IGDB's name.

## File layout

One file per area, so that people working in parallel and translators touch different files and merge conflicts stay
rare. The same file names are used in every language folder (`values-de/strings_home.xml` translates
`values/strings_home.xml`). The test only needs the keys, but identical names keep diffs reviewable.

| File | Area |
|---|---|
| `strings.xml` | legacy Java UI and preferences (existing, shrinking) |
| `strings_common.xml` | words shared by several areas: OK, Cancel, Save, Undo, Back, On, Off, units |
| `strings_home.xml` | start screen, side menu, dashboard, first run (boot, welcome) |
| `strings_stream_menu.xml` | in-stream menu, its help, loader, stream result and statistics labels, upscaler status, controller button names |
| `strings_stream.xml` | the stream activity and what starts it: warnings at the start of a stream, the encryption readout, shortcut files, service names |
| `strings_connection.xml` | messages (toasts) of a stream that is being started: what the PC answered and why the start stops or changes |
| `strings_rumble.xml` | rumble feel names and the sentences about a rumble technique that cannot run |
| `strings_devices.xml` | PCs, add/remove/wake, device setup, status |
| `strings_pairing.xml` | pairing dialogs and PIN flow |
| `strings_profile.xml` | profiles, avatars, profile dialogs (**reference**) |
| `strings_settings.xml` | settings tabs, rows, native settings screens, per-app settings |
| `strings_settings_help.xml` | settings help texts (`help_*`) |
| `strings_filters.xml` | artwork and library filters |
| `strings_metadata.xml` | game metadata, tags, genres, sources |
| `strings_tags.xml` | genre and theme names of the game card and filter (`tags_*`) |
| `strings_artwork.xml` | artwork picker and progress |
| `strings_toasts.xml` | toasts that belong to no single screen, the kind words (INFO, DONE …) and the toast screen-reader text |
| `strings_debug.xml` | debug info screen (user-visible) |
| `strings_keyboard.xml` | on-screen keyboard labels and its settings |
| `strings_diagnostics.xml` | log export, the problem report card (typed `Problem` → sentences), log preview note |
| `strings_link.xml` | link test and guidance |
| `strings_playtime.xml` | the streaming-time block of the home info row |
| `strings_shell.xml` | top bar, side menu, hint bar, link prompts |
| `strings_firstrun.xml` | first run: avatar and profile creation |
| `strings_settings_screen.xml` | native settings screens: titles, descriptions, search, letter picker |
| `strings_companion.xml` | the companion screen on the second panel of a dual-screen device ([DUAL_SCREEN.md](DUAL_SCREEN.md)) |
| `strings_companion_screens.xml` | the companion's fallback and welcome cards: screen titles and purposes, the hint list, battery |
| `strings_companion_pc.xml` | the companion's PC cards, pairing steps and library states |
| `strings_companion_content.xml` | the companion's artwork, filter, profile and boot pages |
| `strings_companion_flow.xml` | the companion's launch loader, result and problem pages |
| `strings_companion_help.xml` | the companion's game card, settings help and stream pages |
| `strings_companion_dim.xml` | the idle-dimming rows of Settings → Display ([DUAL_SCREEN.md](DUAL_SCREEN.md)) |

A text that fits no area gets its own area file (and a row here); an unrelated file is not overloaded.

## Keys

**Form**

- Keys are `snake_case`, English, prefixed with the area / feature: `profile_`, `settings_`, `help_`, `home_`,
  `library_`, `stream_menu_`, `devices_`, `pairing_`, `filters_`, `metadata_`, `artwork_`, `toast_`, `dialog_`,
  `debug_`, `keyboard_`, `diag_`, `link_`, `common_`.
- Then comes the screen or feature, then the role when the role is not obvious: `profile_rename_title`,
  `profile_name_note`, `profile_name_required`.

| Role suffix | Meaning |
|---|---|
| `_title` | title |
| `_note` | explanatory line |
| `_action` | button |
| `_hint` | hint |
| `_label` | label |
| `_error` | error |
| `_toast` | toast |

**Rules**

- Keys name the **place**, not the English text, so rewording the English does not rename the key.
- A key is never reused for a different meaning.
- A key is never built by concatenation in code, except the documented `help_*` lookup.
- Keys are code only. They are never shown, never stored, never sent over the network. If a stored or protocol value
  looks like a label (a setting value, a stream kind), the value stays an identifier and the label is looked up from
  it.
- Words that look identical in English but differ elsewhere (`Open` a menu vs `Open` a file, `Set`) get separate keys.
- A `<!-- … -->` comment stands above any string whose meaning or arguments are not obvious (translators and the
  review pass read it).

## Writing a string

**Placeholders**

- Placeholders are positional: `%1$s`, `%2$d`. For one argument the positional form is fine; for two or more it is
  mandatory (the test rejects non-positional multi-argument strings).
- A translation may reorder them but must contain the same placeholders.
- A literal percent is written `%%`.
- `%n` is never used.

**No concatenation of translatable fragments**

- Word order, case and agreement differ per language.
- Not `"Signed in on " + model + ...`, not a `getString(a) + " " + getString(b)`.
- One full sentence per resource, with arguments.
- Prefixes like `"Error: "` + message are also fragments; the string uses `%1$s`.
- A fragment that is truly generic (a list separator, a middle dot) is a resource of its own.

**Plurals**

- Plurals use `<plurals>`, never `if (n == 1) "profile" else "profiles"`.
- English defines `one` and `other`.
- **Every quantity item contains the number placeholder** (`%d profile`, not `A profile`), so that the quantities of
  other languages ([Plural categories](#plural-categories)) can all use the same arguments.
- The number is passed twice when the sentence has another argument first (`getQuantityString(id, n, model, n)`), as in
  `profile_signed_in`.

**Arguments**

- Arguments are values, not sentences: names, numbers, device models.
- A localized word is never passed as an argument to be inserted into another sentence (`"No %1$s match"` with
  `tab.noun`). There is a string per case, or an `<string-array>` selected by index, so each language can inflect.

**Numbers and dates in text**

- Numbers go through the format arguments (`%d`, `%1$.1f`), which use the app locale (decimal comma in Czech).
- Dates and times use `java.text.DateFormat` / `DateTimeFormatter.ofLocalizedDate`, not an English pattern or an
  `Locale.ENGLISH` formatter.
- Technical readouts and developer-facing text (log files, report bodies, e-mail subject) stay English on purpose.

**Typography**

- The English source uses the real characters `…`, `·`, `×`, `–`, `’`.
- An apostrophe is escaped as `\'` or written `’`.
- `\n` is only for a deliberate line break.
- No HTML, no markup in strings.
- No trailing space, no leading space (a separator belongs in the format).

**Text the translator cannot see**

- No text in images or drawable names, no ids in labels.
- No keyboard-shortcut letters embedded in the sentence ("Press Y") without a way for the translator to see them:
  `Press %1$s to undo` with the button name as argument.

**Capitalization**

- English UI uses sentence case.
- Translators follow the convention of their language (German nouns, no title case in French/Spanish/Czech).

**What is not a resource**

- Strings that are not text for the user (log lines, identifiers, URLs, brand and codec names alone) are not
  resources.
- A string that must not be translated but must be a resource gets `translatable="false"`.

## "Delos" and other brand names

- The app name **Delos** is never translated, transliterated or inflected, in any language. The token appears exactly
  as `Delos`.
- Sentences are rephrased instead of declining it.

| Language | Form |
|---|---|
| Czech | *„aplikace Delos“*, not *„Delosu“* |
| German | *„Delos-Server“* (hyphen), *„die Delos-App“* |
| French | *„l’app Delos“* |
| Japanese | *„Delosの“* (a particle is fine) |
| Ukrainian | *„застосунок Delos“*, not *„Делос“* |
| English | No possessive `Delos's` either (*„the Delos driver“*, *„Delos starts with …“*) |

`StringResourcesTest` enforces it on every translation file:

- A translation whose English contains `Delos` must contain `Delos` unchanged.
- No translated string may contain `Delos` directly followed by a Latin letter (a case ending: *Delosu, Delosem,
  Delosa*) or `'s`.
- No translated string may contain a transliteration (*Делос, デロス, 델로스, 德洛斯*).
- Further patterns go into `DECLINED_DELOS` / `TRANSLITERATIONS` in the test.

Also untranslated: **Vibepollo**, **Apollo**, **Sunshine**, **Moonlight**, **Steam**, **SteamGridDB**, **IGDB**,
**HowLongToBeat**, **PCGamingWiki**, **PyroWave**, **Android**, **Wi-Fi**, **HDR**, **HEVC**, **H.264**, **AV1**,
**V-Sync**, **USB**, **PC**, **LFR**, key names (**Ctrl**, **Alt**, **Win**, **Esc**, **F11**), game and PC names,
profile names.

## Tone and style per language

- Short, direct, friendly and calm; the English is already this.
- A translation carries the meaning, not the words, and keeps the length close.
- The formality is the same for the whole app.
- UI labels are nouns or imperatives, with no full stops on single labels and buttons. Sentences in notes and toasts
  have them (as the English does).

| Language | Address and register | Notes |
|---|---|---|
| Czech | informal *ty* (imperative: „Vyber“, „Zkus“) | no vykání; gender-neutral phrasing where possible; Czech plurals one/few/many/other |
| Spanish | informal *tú* | neutral Latin-American-friendly vocabulary („ordenador“ vs „computadora“: use **PC**); infinitive for buttons |
| Portuguese (BR) | *você* | Brazilian spelling and vocabulary („tela“, „arquivo“, „controle“); infinitive or imperative for buttons, consistently |
| French | informal *tu* | space before `: ; ! ?` is a **no-break space U+00A0** (not U+202F: the bundled fonts lack it, see [Fonts and scripts](#fonts-and-scripts)); „Annuler“, „Enregistrer“; infinitive for buttons |
| German | informal *du* | compounds can be very long: hyphenate with „-“ (*Delos-Server*), prefer short words; capitalize nouns |
| Japanese | polite です/ます; buttons as plain noun/verb („保存“, „開く“) | no spaces between words, no `Delos` declension (use の, を, が), full-width punctuation 、。; katakana for loanwords per convention („コントローラー“) |
| Korean | polite 해요체 („저장해요“ in sentences); buttons as noun/verb stems („저장“) | spaces between words; particles attach to `Delos` after a space or directly („Delos를“): no change to the token |
| Simplified Chinese | neutral, no honorifics, no 您 | full-width punctuation `，。：`; no spaces between CJK and Latin is acceptable |
| Ukrainian | informal *ти* | Ukrainian, never Russian vocabulary; plurals one/few/many/other; „застосунок“ for app |

### Plural categories

The test and Android lint (`MissingQuantity`) require these plural categories.

| Languages | Categories |
|---|---|
| en/de | `one, other` |
| es/fr/pt | `one, many, other` |
| cs/uk | `one, few, many, other` |
| ja/ko/zh | `other` only |

## Glossary seed

Technical terms and brands that gamers know stay English in every language unless noted. This is a seed: translators
extend it, record decisions here and keep it consistent across all files.

| English | Rule | cs | es | pt-BR | fr | de | ja | ko | zh-CN | uk |
|---|---|---|---|---|---|---|---|---|---|---|
| stream (noun/verb) | keep English where natural, else translate | stream | stream / transmisión | stream / transmissão | stream / diffusion | Stream | ストリーム | 스트림 | 串流 | стрім |
| host | the PC being streamed from; prefer "PC" | PC | PC | PC | PC | PC | PC | PC | 电脑 | ПК |
| PC | keep | PC | PC | PC | PC | PC | PC | PC | PC | ПК |
| pair / pairing | translate | spárovat | emparejar | parear | associer | koppeln | ペアリング | 페어링 | 配对 | з’єднати |
| bitrate | keep | bitrate | bitrate | bitrate | débit | Bitrate | ビットレート | 비트레이트 | 码率 | бітрейт |
| codec | keep | kodek | códec | codec | codec | Codec | コーデック | 코덱 | 编解码器 | кодек |
| HDR | keep | HDR | HDR | HDR | HDR | HDR | HDR | HDR | HDR | HDR |
| upscaler | translate descriptively | upscaler | escalador | upscaler | upscaler | Upscaler | アップスケーラー | 업스케일러 | 超分 | апскейлер |
| supersampling | keep / transliterate | supersampling | supermuestreo | supersampling | supersampling | Supersampling | スーパーサンプリング | 슈퍼샘플링 | 超采样 | суперсемплінг |
| sharpness | translate | ostrost | nitidez | nitidez | netteté | Schärfe | シャープネス | 선명도 | 锐度 | різкість |
| frame pacing | keep English term, explain in help | frame pacing | frame pacing | frame pacing | frame pacing | Frame-Pacing | フレームペーシング | 프레임 페이싱 | 帧节奏 | frame pacing |
| controller | gamepad | ovladač | mando | controle | manette | Controller | コントローラー | 컨트롤러 | 手柄 | контролер |
| gyro | keep | gyro | giroscopio | giroscópio | gyroscope | Gyro | ジャイロ | 자이로 | 陀螺仪 | гіроскоп |
| rumble | vibration of a controller | vibrace | vibración | vibração | vibration | Vibration | 振動 | 진동 | 震动 | вібрація |
| library | the app list | knihovna | biblioteca | biblioteca | bibliothèque | Bibliothek | ライブラリ | 라이브러리 | 应用库 | бібліотека |
| app (library entry) | the generic word for anything the host's library lists: a game, a program, Desktop, Remote Input. Never „game“ for the entry | aplikace | app | app | appli | App | アプリ | 앱 | 应用 | застосунок |
| cover | cover art of an app | obal | portada | capa | jaquette | Cover | カバー | 커버 | 封面 | обкладинка |
| hero | the wide banner artwork | hero (banner) | hero (banner) | hero (banner) | hero (bannière) | Hero (Banner) | ヒーロー画像 | 히어로 이미지 | 主视觉图 | hero (банер) |
| profile | translate | profil | perfil | perfil | profil | Profil | プロフィール | 프로필 | 个人资料 | профіль |
| toast | never shown to users | – | – | – | – | – | – | – | – | – |

The per-language cells are proposals for the reviewer, not final. "Keep" rows must stay identical in every file.

### App vs. game

- An *app* is the library entry the user launches or manages (tiles, App settings, Quit app, counts, toasts, companion
  texts).
- *game* stays only where a text is about games as content or gaming software:
  - metadata and tags (genre, IGDB, HowLongToBeat, PCGamingWiki, native HDR in games),
  - SteamGridDB's own game database wording,
  - product names (Snapdragon Game Super Resolution, Xbox Game Bar, Steam Overlay),
  - gamepad / game controller, rumble,
  - generic gaming advice in touch and mouse help.
- When Desktop could be the thing meant, the text says app.
- The settings-search aliases (`settings_search_alias_game`, `SettingsSearchAliases.kt`) keep the old word searchable.
- One term per language: Czech *aplikace*, Spanish *app*, Brazilian Portuguese *app*, French *appli*, German *App*,
  Japanese アプリ, Korean 앱, Chinese 应用 (library 应用库), Ukrainian *застосунок*.

## Text fit

**Length per language**

| Languages | Length against English |
|---|---|
| German, French, Spanish, Portuguese, Ukrainian | 20-40 % longer |
| Czech | about 20 % longer |
| Very short labels (under 12 characters, such as buttons and circle items) | can double |
| Japanese, Korean, Chinese | shorter in width, but the glyphs are taller: line height and the 3-line limits need a check |

**Rules**

- The layout must cope with longer text; it does not rely on an exact character budget.
- Wrapping and the scrolling help box are preferred over truncation. The stage's marquee (`MarqueeText`) handles
  one-line labels that do not fit.
- The **translation** is shortened before the font is shrunk.
- A text is never abbreviated with a trailing dot to save space without the reviewer's approval.
- CJK text has no spaces: line breaking happens between any two characters. Manual breaks are never inserted and
  nothing relies on word count.
- No spaces are added around `Delos` in ja/zh unless the language needs them for readability.
- The English budgets in code (`SettingsHelp.LINE_CHARS`, `MAX_LINES`, toast width tests) describe English only.
- Fonts: see [Fonts and scripts](#fonts-and-scripts) (Cyrillic and CJK are drawn by the system fallback fonts; line
  heights are fixed only where the style sets `lineHeight`).

The test that checks the length of short slots is in [Text-fit check](#text-fit-check).

## Fonts and scripts

### Coverage of the bundled fonts

Findings from the shipped files (`res/font`, read with fontTools). `FontCoverageTest` re-reads their `cmap` and fails
if this table stops being true.

| Script | Figtree (body, variable 400-800) | Unbounded (titles, variable 400-700) | Drawn by |
|---|---|---|---|
| Basic Latin, Latin-1 | yes (no soft hyphen) | yes (no µ) | bundled |
| Latin Extended-A (cs, es, pt, fr, de letters) | yes (19 of the 128 are missing, letters no language here uses: Ĕ Ĩ Ĭ Ŏ Ţ Ŧ Ũ ĸ Ŀ ŉ ſ …) | all 128 | bundled |
| Typography `… · × – — ’ ‘ “ ” „ « » ° U+00A0` | yes | yes | bundled |
| `≈ ≤ ≥ ✓ ✕ ● ★`, U+202F, U+2009, U+200B | no | no | system fallback (avoid in text) |
| Ukrainian Cyrillic (А-Я, ґ є і ї, `ʼ`) | **no** (U+2019 `’` yes, U+02BC no) | **yes** (68 of 68 letters) | titles: Unbounded; body: system sans (Roboto) |
| Japanese kana and kanji, CJK punctuation | no | no | system CJK font |
| Korean Hangul | no | no | system CJK font |
| Simplified Chinese hanzi | no | no | system CJK font |
| Phosphor icons (`phosphor_regular`, `phosphor_fill`) | private-use code points U+E000-U+EE74, nothing else | | bundled |

`FontCoverageTest` also scans every `strings*.xml` of the base and of each language folder:

| Languages | Allowed characters |
|---|---|
| Latin languages | inside Figtree's characters |
| Ukrainian | Cyrillic inside Unbounded's characters |
| Japanese / Korean / Chinese | may add CJK blocks |

A translation that uses anything else (a stray `≈`, U+202F, a zero-width space) fails the test and is rewritten or
discussed.

### Decision: no per-language `FontFamily`, no extra font files

- `Figtree` and `Unbounded` (`shell/ui/Type.kt`) are built from `Font(R.font…)`.
- Compose turns these into platform typefaces (`Typeface.Builder` / `createFromResources`).
- A typeface built from a font file ends in the system default fallback chain. Checked in the Android 13 source:
  `Typeface.Builder.build` goes through `CustomFallbackBuilder`, whose `build()` appends `getSystemDefaultTypeface`;
  API 29 and up.
- A character the bundled font lacks is therefore drawn, glyph by glyph, by the system sans (Roboto or the OEM font,
  which has Cyrillic) or the system CJK font.
- Latin text is untouched: same fonts, same metrics, which keeps English and the Latin translations pixel-identical.
- Han characters take their Japanese / Korean / Simplified Chinese forms from the app locale. The app locale is the
  process default locale: the platform sets it from the app locale on Android 13+, `AppLanguage` on 10-12. A Japanese
  app on an English phone therefore still draws Japanese kanji.
- A per-language family is rejected: Compose cannot fall back per glyph between two of its own families, so it would
  replace Figtree for the whole run and make Latin brand names and numbers in a Ukrainian or Japanese sentence a
  different font from the rest of the app.

### Non-Latin text

What changes for non-Latin text, and what to look at on a device. None of it is verified on a device (see
[Not verified](#not-verified)).

**Line height**

| Font | Height |
|---|---|
| Noto Sans CJK | about 1.45 em |
| Figtree | 1.2 em |
| System sans | 1.17 em |

- A line that contains fallback glyphs is as tall as the taller font.
- Every style with an explicit `lineHeight` (most multi-line text, the stream menu, toasts, the icon style in
  `Type.kt`) keeps its height exactly.
- Single-line labels without one grow slightly in ja/ko/zh and stay centered in their boxes.

**Weights**

- The system fonts have their own weights: Medium, SemiBold and Bold map to the nearest weight the fallback font
  offers.

**Tracking**

- Styles with positive `letterSpacing` (the small caption labels, .14 em) also space out CJK glyphs.
- Such labels are not uppercased or tracked in translations that have no case.
- A visible gap is a bug to fix in that style, not by changing the font.

**Mixed scripts**

- Mixed Ukrainian text shows Cyrillic in the system sans and Latin brand names, digits and units in Figtree; titles in
  Unbounded are one font.
- Latin-only ASCII and punctuation (`…`, `·`, `×`, `’`) is always Figtree.
- French uses U+00A0 before `: ; ! ?` and inside `« »`; U+202F would be drawn by a fallback font with a different
  width.

## Settings search: aliases and the letter pickers

**Aliases**

- A row is found by its localized title, section, tab, description and by extra synonyms.
- The English synonyms (the old names of the motion and link rows) are always searched.
- `SearchAliases` (`shell/screens/SettingsSearchAliases.kt`) adds the `settings_search_alias_*` string of the current
  language, so a translator may add words that people of that language would type.

**Letter picker**

- The picker (`settings_screen_search_letters`) holds the alphabet of the language and the digits, nine keys to a row.
- More than 54 keys switch to smaller keys so that six rows are enough.
- The query is compared as typed, so a language whose titles are not written in its picker letters has its own
  matching rule (`SettingsSearchText.kt`, tested by `SettingsSearchTextTest`).
- Descriptions are matched as plain text only. Other languages are unchanged.

| Language | Picker keys | Matching rule | Example | Table and its test |
|---|---|---|---|---|
| cs | the whole Czech alphabet with diacritics (41 letters) and the digits: 51 keys, six rows of nine | the search is diacritic-sensitive | | |
| ja | the 46 basic hiragana in gojūon order | The query and the title are folded to a kana skeleton (katakana become hiragana; dakuten, small kana and ー are dropped). Kanji are read through a built-in table of readings. | `ひつとれと` finds ビットレート | `JapaneseReadings`; a test fails when a ja title gains a kanji the table lacks |
| ko | the 14 initial consonants (choseong) then A–Z and the digits | every Hangul syllable of a title is reduced to its initial consonant | `ㅎㅅㄷ` finds 해상도 | |
| zh-CN | A–Z and the digits | besides Latin text the letters match the pinyin initials of the title, from a built-in table of the characters the titles use | `sz` finds 设置 | `PinyinInitials`; a test fails when a title gains an uncovered character |

## What stays English on purpose

| Kind | What stays English |
|---|---|
| Names and legends | Brands, codec and protocol names, key names (`Ctrl`, `F11`), the controller letters **A/B/X/Y** and **L1/R1/L2/R2/L3/R3** (they are printed on the pad; prompts take them as arguments: `Press %1$s`), the technique code `PULSE`, exception text shown as data |
| Technical readouts | Statistics units and numbers (`ms`, `Mbps`, `KB`, resolutions), formatted with the app locale only where a decimal separator matters |
| Developer-facing text | `DiagLog`, the report and crash files, the e-mail subject and report headings, the debug-only "Trigger test crash" row, service labels in the manifest (not shown to the user), Steam tag names (data matched against the store) |
| Start / Select / Home | Where they have no common local name (es, fr, de, cs, pt-BR) the printed legend is kept |

## Terminology

Each table lists the terms one language uses. The register of each language is in
[Tone and style per language](#tone-and-style-per-language); the shared terms are in [Glossary seed](#glossary-seed).

### Czech

- Informal *ty* everywhere (imperatives: „Vyber“, „Zkus“).
- No feminine/masculine self-reference („Pokračuj, jen pokud odkaz pochází od tebe“).
- Czech plural forms: `one` (1), `few` (2-4), `many` (fractions, genitive singular), `other` (0, 5+).
- Setting titles that a sentence has to inflect are quoted as „Název“ instead of being declined.
- The letter picker of the settings search is in
  [Settings search: aliases and the letter pickers](#settings-search-aliases-and-the-letter-pickers).

| English | Czech | Note |
|---|---|---|
| stream | stream (noun), streamování (verb noun) | menu streamu, „stream“ stays |
| PC / host | PC | host software is „PC“, „server“ for Apollo/Vibepollo |
| gamepad / controller | gamepad | deliberately not „ovladač“: that word is the Czech for a software **driver** (USB ovladač) |
| driver | ovladač | |
| pair / unpair | spárovat / zrušit spárování | |
| rumble / vibration | vibrace | „Charakter vibrací“ = rumble feel |
| library | knihovna | |
| cover / wide cover / background / logo | obal / široký obal / pozadí / logo | artwork = „obrázky“ |
| link (device ↔ PC) | spojení | „Otestovat spojení“; „na této síti“ (locative, so `link_transport_other` = „této síti“, used after „na“) |
| refresh rate / frame rate | obnovovací frekvence / snímková frekvence | FPS kept |
| opacity | neprůhlednost | |
| on-screen controls / OSC | ovládání na obrazovce | |
| special / custom keys | speciální / vlastní klávesy | „Odeslat klávesy“ = Send keys |
| overlay (statistics) | Overlay | stream menu tile name kept |
| Undo / Back / Reset | Vrátit zpět / Zpět / Resetovat | |
| On / Off | Zapnuto / Vypnuto | |
| gyro / gyroscope | gyro (smoothing) / gyroskop | |
| upscaler, supersampling, bitrate, frame pacing, codec | upscaler, supersampling, bitrate, frame pacing, kodek | |
| sharpness / sharpen only | ostrost / Jen zostření | |
| tabs Stream / Display / Audio / Controls / Appearance / Covers / Devices / About | Stream / Obraz / Zvuk / Ovládání / Vzhled / Obaly / Zařízení / O aplikaci | |
| UI theme / game theme tag | motiv / téma | |

### Spanish

- Neutral Spanish (Spain and Latin America), informal *tú*, infinitive for buttons.
- "vídeo" with accent as in the RAE.

| English | Spanish | Note |
|---|---|---|
| PC / host | PC (host only in technical error texts) | |
| stream (noun/verb) | stream / transmitir | |
| pair / unpair / pairing | emparejar / desemparejar / emparejamiento | |
| settings | ajustes | |
| library | biblioteca | |
| controller / gamepad | mando | |
| rumble / haptics | vibración / vibración háptica | |
| artwork / cover / wide cover / background | ilustraciones / portada / portada panorámica / fondo | |
| profile | perfil | |
| app quit / resume | cerrar app / reanudar | |
| wake (a PC) | reactivar | |
| offline / online | sin conexión / en línea | |
| upscaler / supersampling / sharpness | escalador / supermuestreo / nitidez | |
| codec / bitrate / frame pacing | códec / bitrate / frame pacing | |
| Reset / Cancel / Done / Back | Restablecer / Cancelar / Hecho / Atrás | |
| Link test | Probar enlace / prueba de enlace | |
| Custom / Default | Personalizado / Predeterminado | |
| Debug info | Información de depuración | |

### Portuguese (Brazil)

- Register: *você*, Brazilian vocabulary.
- Buttons and labels in the infinitive (Salvar, Cancelar), notes in plain *você* phrasing.
- Delos is always written as „o app Delos“ / „do app Delos“.

| English | pt-BR | Note |
|---|---|---|
| stream (noun) / to stream | stream / transmitir, transmissão | |
| gamepad, controller | controle | |
| host (PC), server | PC, servidor | |
| pair / pairing | parear / pareamento | |
| library, profile | biblioteca, perfil | |
| settings | configurações | |
| Cancel, Save, Back, Delete, Remove, Add | Cancelar, Salvar, Voltar, Excluir, Remover, Adicionar | |
| Quit, Resume, Wake | Sair, Retomar, Ativar (Wake-on-LAN kept) | |
| On / Off, Enable / Disable | Ativado / Desativado, Ativar / Desativar | |
| on-screen controls, touchscreen | controles na tela, tela touch | |
| frame rate, refresh rate, latency | taxa de quadros, taxa de atualização, latência | |
| bandwidth, packet loss | largura de banda, perda de pacotes | |
| rumble, gyro | vibração, giroscópio | |
| sharpness, upscaler, supersampling, frame pacing, bitrate, codec | nitidez, upscaler, supersampling, frame pacing, bitrate, codec | |
| cover, hero, artwork | capa, hero (banner), arte | |
| clipboard, stylus, deadzone, stick | área de transferência, caneta, zona morta, analógico | |
| toast | aviso | |

### French

- Register: informal *tu*.
- Buttons and menu items in the infinitive.
- U+00A0 before `: ; ! ? %` and inside « ».

| English | French | Note |
|---|---|---|
| stream (noun/verb) | stream / streamer | |
| host / PC | PC (technical error texts: hôte) | |
| pair / unpair | associer / dissocier | |
| bitrate | débit | |
| frame rate / refresh rate | fréquence d’images / fréquence d’écran | |
| gamepad, controller | manette | |
| rumble | vibration | |
| cover / wide cover / hero (background) | jaquette / jaquette large / arrière-plan | |
| artwork | visuels | |
| library | bibliothèque | |
| profile | profil | |
| settings | réglages | |
| sharpness / Sharpen only | netteté / Netteté seule | |
| upscaler, supersampling, frame pacing, overlay | kept in English | |
| on-screen controls | commandes à l’écran | |
| clipboard | presse-papiers | |
| link (network path) / Test link | liaison / Tester la liaison | |
| Back (button) | Retour | |

### German

- Informal *du*; nouns capitalized.
- Hyphenated compounds with brands (*Delos-App*, *Vibepollo-Weboberfläche*).
- English gamer terms stay where the glossary says so.

| English | German | Note |
|---|---|---|
| pair / unpair | koppeln / Kopplung aufheben | |
| stream (noun) | Stream | |
| library | Bibliothek | |
| profile | Profil | |
| gamepad / controller | Gamepad (legacy settings) / Controller | |
| rumble | Rumble (technical settings), Vibration (UI) | |
| cover / wide cover / background | Cover / breites Cover / Hintergrund | |
| artwork, metadata | Artwork, Metadaten | |
| settings, device(s) | Einstellungen, Gerät(e) | |
| appearance (tab) | Design | |
| about (tab) | Über | |
| on-screen controls | Bildschirmsteuerung | |
| metered network | getaktetes Netzwerk | |
| link (network) / link test | Verbindung / Verbindungstest | |
| sharpness / sharpen only | Schärfe / Nur schärfen | |
| Apply / Resume / Disconnect | Anwenden / Fortsetzen / Trennen | |
| custom | Eigene(r/s) | |
| web UI | Weboberfläche | |

### Japanese

- Polite です/ます in sentences, plain noun/verb phrases on labels and buttons, full-width punctuation.
- `Delos` is preceded by a half-width space after a Japanese character (the test requires a non-letter before the
  token) and followed by a particle (`Delos を`-style spacing is not needed).

| English | 日本語 | Note |
|---|---|---|
| stream / streaming | ストリーム / ストリーミング | |
| host, PC | PC (ホスト where protocol-level) | |
| gamepad, controller | コントローラー | |
| pair / unpair | ペアリング / ペアリング解除 | |
| bitrate, frame rate, resolution | ビットレート, フレームレート, 解像度 | |
| latency, bandwidth, packet loss | 遅延, 帯域, パケットロス | |
| upscaler, supersampling, sharpness | アップスケーラー, スーパーサンプリング, シャープネス | |
| frame pacing | フレームペーシング | |
| rumble / vibration | 振動 | |
| on-screen controls (OSC) | 画面上のコントロール (OSC kept where English abbreviates) | |
| library, profile | ライブラリ, プロフィール | |
| cover / wide cover / background / logo | カバー / ワイドカバー / 背景 / ロゴ | |
| settings / About / Debug info | 設定 / バージョン情報 / デバッグ情報 | |
| stream menu | ストリームメニュー | |
| Apply / Resume / Quit app | 適用 / 再開 / アプリを終了 | |
| keep diagnostic logs | 診断ログを保存 | |

### Korean

- Polite 해요체 in sentences; buttons, tabs and labels as noun or verb stem.
- Particles on variable names use the 은(는) / 이(가) / 을(를) / 과(와) / (으)로 forms.
- `Delos` takes particles directly.

| English | Korean | Note |
|---|---|---|
| stream | 스트림 / 스트리밍 | |
| pair / unpair | 페어링 / 페어링 해제 | |
| library | 라이브러리 | |
| profile | 프로필 | |
| settings (tabs: Stream, Display, Audio, Controls, Appearance, Covers, Devices, About) | 설정 (스트림, 디스플레이, 오디오, 컨트롤, 외관, 커버, 기기, 정보) | |
| cover / wide cover / background / logo | 커버 / 와이드 커버 / 배경 / 로고 | |
| artwork / metadata | 아트워크 / 메타데이터 | |
| gamepad / controller | 게임패드 / 컨트롤러 | |
| rumble / vibration / haptics | 럼블 / 진동 / 햅틱 | |
| gyroscope | 자이로스코프 (자이로) | |
| bitrate / codec / frame rate | 비트레이트 / 코덱 / 프레임 레이트 | |
| upscaler / supersampling / sharpness | 업스케일러 / 슈퍼샘플링 / 선명도 | |
| frame pacing | 프레임 페이싱 | |
| on-screen controls | 화면 컨트롤 | |
| stream menu | 스트림 메뉴 | |
| Cancel / Save / Done / Reset / Undo | 취소 / 저장 / 완료 / 초기화 / 실행 취소 | |
| On / Off | 켜짐 / 꺼짐 | |
| offline / online | 오프라인 / 온라인 | |
| Debug info | 디버그 정보 | |

### Simplified Chinese

- Neutral register, no 您, full-width punctuation, no spaces between CJK and Latin.
- Latin tokens (brands, codecs, units, key and button names) stay as written.
- Arrows and ≈ ≤ ≥ ¼ are not in the allowed character set: menu paths use `>`, ranges use 至, ¼ is written 1/4.

| English | zh-CN | Note |
|---|---|---|
| stream | 串流 | |
| host | 电脑 / PC | |
| pair / unpair | 配对 / 取消配对 | |
| bitrate, frame rate, refresh rate | 码率, 帧率, 刷新率 | |
| resolution, latency, packet loss | 分辨率, 延迟, 丢包 | |
| codec, decoder | 编解码器, 解码器 | |
| upscaler, supersampling, sharpness | 超分, 超采样, 锐度 | |
| frame pacing | 帧节奏 | |
| controller / gamepad | 手柄 | |
| gyroscope, rumble, deadzone | 陀螺仪, 震动, 死区 | |
| analog stick, trackpad | 摇杆, 触控板 | |
| on-screen controls | 屏幕按键 | |
| library, cover, hero | 游戏库, 封面, 主视觉图 | |
| profile, avatar | 个人资料, 头像 | |
| overlay, statistics | 浮层, 统计信息 | |
| clipboard, log | 剪贴板, 日志 | |
| Fit / Fill / Stretch | 适应 / 填充 / 拉伸 | |

### Ukrainian

- Informal *ти*; buttons are short infinitives or nouns.
- Apostrophe is always `’`.
- Setting titles quoted in help texts match `values-uk/strings.xml`.

| English | Ukrainian | Note |
|---|---|---|
| app | застосунок (never „додаток“) | |
| PC / host | ПК | |
| stream | стрім (never „потік“) | |
| pair / pairing / paired | створити пару (button: З’єднати) / створення пари / спарений | |
| settings / setting | налаштування / параметр | |
| controller, gyro, rumble | контролер, гіроскоп, вібрація | |
| library, cover, background, logo | бібліотека, обкладинка, тло, логотип | |
| bitrate, codec, upscaler, supersampling, sharpness | бітрейт, кодек, апскейлер, суперсемплінг, різкість | |
| frame pacing | frame pacing | |
| Stream / Display / About tabs | Стрім / Екран / Про застосунок | |
| Quit app, Resume, Start | Закрити застосунок, Продовжити, Запустити | |
| arrows `→` in menu paths | `>` (the arrow is not in the Unbounded font) | |

## Workflow: adding or changing a string

**Adding a string**

1. The English string goes into the area file from the table in [File layout](#file-layout) (a file is created if none
   fits), with a key by the scheme in [Keys](#keys) and a comment for arguments and context. Any count uses
   `<plurals>`.
2. The code uses it: `stringResource`/`pluralStringResource` in composition; `res.getString(...)` from a
   `textResources()` captured in composition otherwise. The literal is deleted.
3. Check: `python3 tools/l10n/find_literals.py --list <File.kt>` shows no remaining hardcoded text in the touched
   file.
4. `./gradlew :app:testDebugUnitTest :app:lintDebug` runs. `StringResourcesTest` covers the base and every language
   folder that exists; lint covers formatting, quantities and unused resources.

**Other changes**

| Change | Procedure |
|---|---|
| Changing the meaning of an existing English string | The key is renamed (so stale translations disappear, as the test then flags them as missing/unknown), or every language file is updated in the same change. Fixing a typo keeps the key. |
| Removing a string | The key is deleted from the base and from every language file in the same change (the test reports unknown keys; lint reports unused resources). |
| Adding a language | `values-<lang>/strings*.xml` is created for all areas, the language is added to the three lists ([Languages](#languages)), the tests run. While some keys are missing, the English fallback is shown. |
| Translating | `res/values/strings_*.xml` is translated into `res/values-<lang>/strings_*.xml` (same file and key names). The placeholders are kept. The translation follows this guide and the glossary. |

## Tooling

**`tools/l10n/find_literals.py`**

- It finds hardcoded user-facing English literals in `app/src/main/java/com/limelight/shell/**/*.kt`.
- `--list [files…]` prints every literal with line numbers.
- `--markdown` prints a per-file count table.
- It is a heuristic: uppercase start, a space or more than 3 characters; logs, keys, identifiers, regexes and format
  keys are skipped.
- A line with a trailing `// l10n-ignore` is skipped.
- Python 3.9, standard library only.

**Lint**

| Check | State |
|---|---|
| `MissingTranslation` | disabled while English is the fallback |
| `ExtraTranslation`, `MissingQuantity`, `StringFormat*`, `UnusedResources` | active |

The tests are in [Verification](#verification).

## Code map

| Path or class | Role |
|---|---|
| `res/values/strings_<area>.xml` | English base strings, one file per area ([File layout](#file-layout)) |
| `res/values-<lang>/strings_*.xml` | Translations, same file and key names |
| `res/xml/locales_config.xml` | Language list of the system's per-app language picker |
| `res/values/arrays.xml` | `language_names` / `language_values` of the in-app Language setting |
| `res/raw/keep.xml` | Keeps `help_*` and `stream_help_*`, which are found with `getIdentifier` |
| `res/font` | The bundled fonts |
| `shell/settings/AppLanguage.kt` | Wraps the Android per-app locale |
| `shell/ui/Text.kt` | `textResources()` |
| `shell/ui/Type.kt` | `Figtree` and `Unbounded` families, text styles |
| `shell/ui/GenreIcons.kt` | `genreName(res, genre)` / `themeName(res, theme)` |
| `shell/ui/SteamTags.kt`, `GameTags.kt` | Ranking, filters and the Steam mapping; ids and English names as data |
| `shell/screens/SettingsSearchAliases.kt` | `SearchAliases`: the `settings_search_alias_*` string of the current language |
| `SettingsSearchText.kt` | Matching rules of the settings search per language |
| `RumblePresets`, `RumbleProblem`, `RumbleText.kt` | Typed rumble presets and problems, mapped to resources |
| `tools/l10n/find_literals.py` | Lists text that is not a resource ([Tooling](#tooling)) |
| `app/src/test/java/com/limelight/l10n/StringResourcesTest.kt` | Test of every translation file ([Verification](#verification)) |

## Verification

The tests run with the unit tests. `tools/l10n/find_literals.py` lists text that is not a resource.

| Test | What it enforces |
|---|---|
| `StringResourcesTest` | Keys, kinds, placeholders, plurals and the Delos rules of every language; details below |
| `TextFitTest` | Length of short slots ([Text-fit check](#text-fit-check)) |
| `TagNamesTest` | Genre and theme names |
| `FontCoverageTest` | The font table and the characters of every string file ([Fonts and scripts](#fonts-and-scripts)) |
| `SettingsSearchTextTest` | The matching rules of the settings search |
| `StreamMenuLayoutTest` | Every menu key has a `stream_help_*` string |

`StringResourcesTest` (`app/src/test/java/com/limelight/l10n/StringResourcesTest.kt`) checks, for every
`values-<lang>/strings*.xml`:

| Check | Rule |
|---|---|
| Keys | Same keys as the English base. No missing key unless the language is listed in `PARTIAL_LANGUAGES` in the test, which is empty and only for a language that is being filled in. No extra key, nothing for a `translatable="false"` key. |
| Kind | Same kind (string / plurals / array) |
| Placeholders | Equal placeholder multisets (positional index and conversion, order-independent); two or more arguments must be positional |
| Plurals | The plural quantities of the language |
| Values | No empty values |
| Brand | The Delos rules |

It also checks the English base itself and that the language lists agree.

### Status

| Language | String coverage | `TextFitTest` | Native-speaker audit |
|---|---|---|---|
| cs | every string | checked | not verified |
| es | every string | checked | not verified |
| pt-BR | every string | checked | not verified |
| fr | every string | checked | not verified |
| de | every string | checked | not verified |
| ja | every string | not covered by the test | not verified |
| ko | every string | not covered by the test | not verified |
| zh-CN | every string | not covered by the test | not verified |
| uk | every string | checked | not verified |

`find_literals.py` reports no hardcoded literal in the shell.

Text produced outside the UI is typed and mapped to resources:

| Text | Type and resources |
|---|---|
| Rumble presets and problems | `RumblePresets`, `RumbleProblem`, `RumbleText.kt` |
| Upscaler status | `UpscalerStatus` |
| Problem report card | `Problem` fields → `diag_problem_*` |
| Toast kinds | `ToastKind.wordRes` |

- The artwork "none of them matches" sentences do not contain the filter labels: the active filters are listed on
  their own line (`artwork_filtered_active`).
- The statistics position names are shared with the settings strings (`settings_native_stats_*`).

### Text-fit check

- `TextFitTest` covers cs, es, pt-BR, fr, de and uk.
- It flags a translation of a short UI slot that is more than twice the English length and longer than 12 characters.
- A short UI slot is a tab, chip, hint bar, side menu, button, menu row or option. It is found by key prefix and
  suffix, with English at most 28 characters.
- A label with no shorter natural wording is listed in `ACCEPTED` with the reason.
- The test also fails when an accepted entry stops being flagged.

Review result (date not recorded):

| Count | Total | cs | es | pt-BR | fr | de | uk |
|---|---|---|---|---|---|---|---|
| Flagged before the review | 29 | 1 | 5 | 5 | 10 | 4 | 4 |
| Accepted after the review | 17 | 0 | 3 | 4 | 7 | 1 | 2 |

The worst offenders (over 2.4 times) are shortened: Odpárovat, Háptica, Crépuscule, Mise en page, Haptique,
Entkoppeln, Abendrot, Fehler, З’єднати, Роз’єднати, Recentes.

### Not verified

- The translations were not audited by a native speaker of each language.
- Text fit is checked by length rules, not by looking at every screen in every language on a device.
- The behavior of non-Latin text in [Non-Latin text](#non-latin-text) is not verified: no device was used.

## Related

- [GUI.md](GUI.md): where a text appears on screen and how much room it has.
- [SETTINGS.md](SETTINGS.md): the settings catalog that reads its texts from resources.
- [DUAL_SCREEN.md](DUAL_SCREEN.md): the companion screen whose texts are in the `strings_companion*.xml` files.
- Tracking issue: [#10](https://github.com/Meitoncz/Delos/issues/10).
