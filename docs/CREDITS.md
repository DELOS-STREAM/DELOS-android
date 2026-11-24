# Credits

Delos stands on the work of many people. All credit for the streaming core goes to the Moonlight authors and to ClassicOldSong.

- **[Moonlight Android](https://github.com/moonlight-stream/moonlight-android)** and [moonlight-common-c](https://github.com/moonlight-stream/moonlight-common-c): Cameron Gutman, Diego Waxemberg, Aaron Neyer, Andrew Hennessy and all contributors. Moonlight is the work of students at Case Western and was started as a project at MHacks.
- **[Artemis Android](https://github.com/ClassicOldSong/moonlight-android)** and [Apollo](https://github.com/ClassicOldSong/Apollo): ClassicOldSong and contributors.
- **[Sunshine](https://github.com/LizardByte/Sunshine)**: LizardByte and contributors. **[Vibepollo](https://github.com/Nonary/Vibepollo)**: Nonary and contributors.
- Features that other Moonlight forks pioneered are credited to their authors: Moonlight V+ (qiin2333), Artemide (derflacco), Moonlight X (MoreOrLessSoftware), Moonlit Artemis (4had45), Moonlight X Turnip (NoCrypt) with libadrenotools (bylaws), and Axixi2233. The feature list was compared with [Starwire](https://github.com/SicarioGaming/Starwire) (SicarioGaming, GPL-3.0); this records inspiration, not a claim that its code was copied.
- **Video and upscaling:** [PyroWave](https://github.com/Themaister/Granite) and Granite (Hans-Kristian Arntzen, MIT), [volk](https://github.com/zeux/volk) (MIT), and Qualcomm Snapdragon Game Super Resolution (see `app/src/main/assets/licenses/`).
- **Fonts and icons:** [Unbounded](https://github.com/googlefonts/unbounded) and [Figtree](https://github.com/erikdkennedy/figtree) (SIL Open Font License), [Phosphor Icons](https://phosphoricons.com/) (MIT).
- **Sounds:** interface sounds from [Open Sounds](https://www.romainraynal.fr/en/opensounds) by Romain Raynal (royalty-free); the intro's sound track is made for DELOS by Meitoncz.
- **Game information and artwork:** [IGDB](https://www.igdb.com/), [PCGamingWiki](https://www.pcgamingwiki.com/) (data under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/) unless otherwise noted), [SteamGridDB](https://www.steamgriddb.com/) (artwork belongs to its authors), Steam store user tags, and [HowLongToBeat](https://howlongtobeat.com/) through an unofficial client adapted from [howlongtobeatpy](https://github.com/ScrappyCocco/HowLongToBeat-PythonAPI) (MIT, Copyright (c) 2018 Michele). Provider terms are separate from this project's license; provider credentials live only in the metadata service, never in the app or this repository.
- **Link test:** follows the probe of Vibepollo and the pass rules of its reference client, Nonary's [moonlight-qt](https://github.com/Nonary/moonlight-qt) (see [STREAM_QUALITY.md](STREAM_QUALITY.md#pyrowave-link-guidance-18)).

## Libraries

Delos ships the libraries below. [THIRD_PARTY.md](THIRD_PARTY.md) records the version, origin and checksum of each one;
this list names the projects and their licenses only. The full license texts are in `app/src/main/assets/licenses/`,
listed in its `INDEX.txt`.

### Native code

| Library | Used for | License |
|---|---|---|
| [OpenSSL](https://www.openssl.org/) (`libcrypto`) | Encryption of the stream | Apache-2.0 |
| [Opus](https://opus-codec.org/) (`libopus`) | Audio decoding | BSD-3-Clause |
| [ENet](https://github.com/cgutman/enet) (Cameron Gutman's fork) | The control channel of the stream | MIT |
| [nanors](https://github.com/sleepybishop/nanors) | Reed-Solomon error correction | MIT |
| [PyroWave](https://github.com/Themaister/pyrowave), [Granite](https://github.com/Themaister/Granite), [volk](https://github.com/zeux/volk) | The PyroWave video decoder | MIT |
| [Vulkan-Headers](https://github.com/KhronosGroup/Vulkan-Headers) | Vulkan declarations for PyroWave | Apache-2.0 OR MIT |
| [Snapdragon Game Super Resolution](https://github.com/SnapdragonGameStudios/snapdragon-gsr) shaders | Upscaling | BSD-3-Clause |

### Java and Kotlin libraries

| Library | Used for | License |
|---|---|---|
| [AndroidX and Jetpack Compose](https://developer.android.com/jetpack), [Material Components](https://github.com/material-components/material-components-android) | The interface | Apache-2.0 |
| [JetBrains Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) | Brought in by Haze | Apache-2.0 |
| [Haze](https://github.com/chrisbanes/haze) | Background blur | Apache-2.0 |
| [Kotlin](https://kotlinlang.org/) standard library, [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), JetBrains annotations | Language runtime | Apache-2.0 |
| [OkHttp](https://square.github.io/okhttp/) and [Okio](https://square.github.io/okio/) | HTTP and HTTPS requests to the PC and to the game information providers | Apache-2.0 |
| [Bouncy Castle](https://www.bouncycastle.org/) | The client certificate and the pairing cryptography | MIT |
| [JmDNS](https://github.com/jmdns/jmdns) with the [SLF4J](https://www.slf4j.org/) API | Finding PCs on the local network | Apache-2.0; SLF4J: MIT |
| [Gson](https://github.com/google/gson) with Error Prone annotations | JSON | Apache-2.0 |
| [JCodec](https://github.com/jcodec/jcodec) | Reading H.264 stream headers | BSD-2-Clause |
| [ShieldControllerExtensions](https://github.com/cgutman/ShieldControllerExtensions) | Features of NVIDIA Shield controllers | MIT |
| Guava ListenableFuture, JSpecify | Brought in by AndroidX | Apache-2.0 |
| `desugar_jdk_libs` | Newer Java APIs on older Android versions | GPL-2.0 with Classpath exception |

Delos itself is licensed under GPL-3.0 ([LICENSE](../LICENSE)).
