# Third-party code and binaries

Every binary and every vendored or generated component in this repository has a row here: where it comes
from, which version it is, how it was made, and under which licence it ships. A reader should be able to
check each claim; where a claim cannot be checked, this document says so.

Rules (see also `docs/ENGINEERING_RULES.md`):

- No tracked binary without a row here and a line in `third_party/CHECKSUMS.sha256`.
- A binary changes only together with its checksum line and its row, in the same commit.
- `sh tools/verify-binaries.sh` verifies the checksum file and fails on a tracked binary that is not
  listed. `sh third_party/verify-binaries-test.sh` tests that script.
- Gradle dependencies are pinned to exact versions and verified against `gradle/verification-metadata.xml`.
- Licence texts of everything shipped in the app are in `app/src/main/assets/licenses/` (`INDEX.txt` lists them).

Last full review: 2026-11-15.

## 1. Prebuilt native libraries: OpenSSL and opus

Eight static libraries under `app/src/main/jni/moonlight-core/`, 48.5 MB together, plus their headers
(`openssl/include/openssl/`, `opus/include/`). They are the only prebuilt machine code in the repository.

| File | Bytes | SHA-256 |
|---|---:|---|
| `openssl/arm64-v8a/libcrypto.a` | 11,368,142 | `d593ba8f576db9ff20d058bee7f0f154b4e000b447548533bc8f16eb95290b54` |
| `openssl/armeabi-v7a/libcrypto.a` | 7,475,116 | `76abf9210207bb74d19c758e898090f5b4c45fdf1c9a9e8613431c7d9455d904` |
| `openssl/x86/libcrypto.a` | 7,860,308 | `88699686084a5cf6068be378b86b7e90f9ea646f6aca09b17303d8cdbe933aff` |
| `openssl/x86_64/libcrypto.a` | 11,403,206 | `8502529fe9992e4f67962a5c5d98139ec37cd9ccacac341622500bf399ad52ca` |
| `opus/arm64-v8a/libopus.a` | 2,933,886 | `d6c39b9135017ab76a42e364569f18622e6e3df691ac2d1d9d36ee4a02eb2e13` |
| `opus/armeabi-v7a/libopus.a` | 2,130,614 | `3925fecb62e4a75095d658bcc1c4e309712a330699825b7790880cf21497719e` |
| `opus/x86/libopus.a` | 2,170,094 | `ed39b86eee0868d911516c4034e8395af31aaa7bfb8ac7e4415846b59ee28d1f` |
| `opus/x86_64/libopus.a` | 3,142,594 | `7d8214860fdab5a8bf0d15125a2ae7baf80169dc4e6ee1ffa2633bb2e7de7a8b` |

| | OpenSSL (libcrypto only) | opus |
|---|---|---|
| Version | 4.0.2, tag `openssl-4.0.2` = `f089acdf4bc7ba94a79f4bf6eb7362c3e7d14aa9` | 1.6.1, tag `v1.6.1` = `22244de5a79bd1d6d623c32e72bf1954b56235be` |
| Licence | Apache-2.0 (`OpenSSL-Apache-2.0.txt`) | BSD-3-Clause (`opus-BSD-3-Clause.txt`) |
| Used for | AES-128-GCM, AES-128-CBC and `RAND_bytes` in `moonlight-common-c/src/PlatformCrypto.c` | multistream decoder in `moonlight-core/callbacks.c` |

### Where they come from

- Delos inherited them from Moonlight Android: upstream commit `31b700304a9eb2827516f52c33d9da0845fc1ece`
  ("Update to libopus 1.6.1 and openssl 4.0.2", Cameron Gutman, 2026-09-02). Its message names the source.
- Source repository: [cgutman/moonlight-mobile-deps](https://github.com/cgutman/moonlight-mobile-deps) at
  commit `419349aa689e8749a9fb91a22dc45531ddd3df45`, which pins `openssl` and `opus` as submodules at the
  two tags above.
- Built by GitHub Actions: [run 33597171234](https://github.com/cgutman/moonlight-mobile-deps/actions/runs/33597171234)
  (push, success, 2026-09-02T06:02:57Z), job "Build Android Dependencies" on `ubuntu-24.04`. It uploaded
  artefact `9833932661`, `moonlight-android-deps.zip`, 15,962,604 bytes,
  SHA-256 `354ad373cd160cb18a863e04222a16d13cfa49647c6c1fe3f3d5665ba03efbd8`.

### The recipe that produced them

The scripts of `cgutman/moonlight-mobile-deps@419349aa` (`build-android.sh` calls the first two):

- `openssl-android.sh`: for each of `android-arm`, `android-arm64`, `android-x86`, `android-x86_64`:
  `./Configure <target> no-shared no-tests no-apps no-legacy no-dso -D__ANDROID_API__=21`, `make clean`,
  `make build_libs`. The libraries carry `-O3` and "built on: Wed Sep 2 06:07:12 2026 UTC" (arm64).
- `opus-android.sh`: `./autogen.sh`, then
  `./configure --disable-shared --enable-static --with-pic --disable-extra-programs --disable-doc` with the
  NDK clang for API 21, `make`, `make install`, per ABI.
- `archive-android.sh`: copies `libcrypto.a` (not `libssl.a`), `libopus.a` and the two header trees into
  the zip.
- The workflow installs NDK `29.0.14206865`. The compiler strings in the binaries are clang 21.0.0 for
  opus (NDK r29) and clang 18.0.4 for OpenSSL: `openssl-android.sh` prefers a preset `ANDROID_NDK_ROOT`,
  which on the runner image points to an older NDK.

### What is proven

- **The eight libraries are byte-identical to upstream's CI artefact.** The artefact was downloaded on
  2026-11-15, its SHA-256 equals the digest GitHub reports, and each `.a` file in it equals the tracked
  file (`cmp`). The opus headers and all 146 tracked OpenSSL headers are identical too.
- **They are byte-identical to Moonlight Android.** The git blob ids equal those of
  `moonlight-stream/moonlight-android` `master` (`b48494cb`).
- The run's `head_sha` is the commit named above, and that commit pins the two source tags.
- `libcrypto.a` reports `OpenSSL 4.0.2 25 Aug 2026`; its build time matches the run.

### What is not proven

- **Nobody rebuilt the libraries from source here.** The proof stops at "identical to what upstream's
  public CI produced from a recipe that pins these tags". OpenSSL embeds a build timestamp, so a rebuild
  would not be bit-identical without `SOURCE_DATE_EPOCH`. The artefact carries no build attestation; trust
  rests on GitHub Actions and on the upstream maintainer's account.
- **`libopus.a` reports its version as `libopus unknown`** (the submodule checkout has no tag for the
  build to read). That it is 1.6.1 follows from the recipe, not from the binary.
- **The upstream artefact expires on 2026-12-01.** After that date nobody can repeat the comparison from
  GitHub. The project owner keeps a local copy of `moonlight-android-deps.zip` with the SHA-256 above.
  The 16 MB zip is deliberately not committed.
- Delos has no build recipe of its own for these libraries yet. A security update depends on upstream
  publishing new binaries, or on writing that recipe.

### Notes

- The `x86` and `x86_64` libraries are tracked but not built: `app/build.gradle` builds `armeabi-v7a` and
  `arm64-v8a` only. They are kept because they are identical to upstream.
- `openssl/include/openssl/configuration.h` is one generated header (the x86_64 variant) used for all
  ABIs, as upstream ships it. Delos calls only the EVP and RAND functions listed above, which do not
  depend on the affected `BN_ULONG` width.

## 2. Native source code

| Component | Version | Form | Origin | Licence | Licence file |
|---|---|---|---|---|---|
| delos-common-c (fork of moonlight-common-c) | `8c208139e841c9f5fffe6e4f1606bfdf922c9707` (updated with the changes of moonlight-common-c `94c65fd3`) | git submodule `app/src/main/jni/moonlight-core/moonlight-common-c` | [DELOS-STREAM/Delos-common-c](https://github.com/DELOS-STREAM/Delos-common-c): a fork of [ClassicOldSong/moonlight-common-c](https://github.com/ClassicOldSong/moonlight-common-c), which is a fork of [moonlight-stream/moonlight-common-c](https://github.com/moonlight-stream/moonlight-common-c); carries the PyroWave protocol commits of Nonary | GPL-3.0 | `LICENSE.txt` in the submodule |
| ENet | `98dbb897ac4a7208038e0782cddedf89dcf0723a` | nested submodule `moonlight-common-c/enet` | [cgutman/enet](https://github.com/cgutman/enet), pinned by moonlight-common-c | MIT | `ENet-MIT.txt` |
| nanors (Reed-Solomon, with `deps/obl`) | `a9b1636f9c98dbfdd0daeaf6982ccd24d1c30f29` | nested submodule `moonlight-common-c/nanors` | [sleepybishop/nanors](https://github.com/sleepybishop/nanors), pinned by moonlight-common-c | MIT | `nanors-MIT.txt` |
| PyroWave | `186f0393b77f7755953b5ecde994bb1cec2e4155` plus Nonary's patches | vendored source in `app/src/main/jni/pyrowave/` | [Nonary/moonlight-qt](https://github.com/Nonary/moonlight-qt) `e4693208c9c1abefdd8c1cd44600e613b3fc14fb`, directory `pyrowave/`; original: [Themaister/pyrowave](https://github.com/Themaister/pyrowave) | MIT | `PyroWave-MIT.txt` |
| Granite (subset) | `b6cffd5ce81f540f0855e6778428483e14763d9b` | vendored with PyroWave | same; original: [Themaister/Granite](https://github.com/Themaister/Granite) | MIT | `Granite-MIT.txt` |
| volk | `47cddf7ed97b94118a08aacb548a411188e016cc` | vendored with PyroWave | same; original: [zeux/volk](https://github.com/zeux/volk) | MIT | `volk-MIT.txt` |
| Vulkan-Headers | `6802bb4733b63ed5efd3adb308a6c885ef180ea1` | vendored with PyroWave | same; original: [KhronosGroup/Vulkan-Headers](https://github.com/KhronosGroup/Vulkan-Headers) | Apache-2.0 OR MIT | `Vulkan-Headers.txt` |
| Snapdragon Game Super Resolution shaders | `d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e` | adapted GLSL in `app/src/main/assets/sgsr/` and `pyrowave-renderer/shaders/` | [SnapdragonGameStudios/snapdragon-gsr](https://github.com/SnapdragonGameStudios/snapdragon-gsr), see `docs/SGSR2_VIDEO.md` | BSD-3-Clause | `sgsr.txt` |
| HowLongToBeat search flow | howlongtobeatpy 1.0.24, `4275a452b9ccd230b7c2a82b8d1a5d851285eb14` | adapted code | [ScrappyCocco/HowLongToBeat-PythonAPI](https://github.com/ScrappyCocco/HowLongToBeat-PythonAPI) | MIT | `howlongtobeatpy.txt` |

All of it is compiled from source by `ndk-build` (NDK `29.0.14206865`, pinned in `app/build.gradle`).

### PyroWave vendoring

- `tools/pyrowave/vendor.ps1` (PowerShell) makes a sparse clone of `Nonary/moonlight-qt` at the commit
  above, copies `pyrowave/` without tests, benchmarks and desktop tools, and applies the patches in
  `tools/pyrowave/patches/`. Delos has one patch: `0001-granite-query-pool-without-wait.patch`.
- `app/src/main/jni/pyrowave/VENDOR.txt` records the moonlight-qt commit; `VENDOR-moonlight-qt.txt` is
  Nonary's own record of the PyroWave, Granite, volk and Vulkan-Headers commits and of his patches.
- Checked on 2026-11-15: the tracked tree differs from `Nonary/moonlight-qt@e4693208` `pyrowave/` only in
  `Granite/vulkan/query_pool.cpp` (exactly the Delos patch), in the files the script leaves out, and in
  Delos' own `Android.mk` and `VENDOR.txt`.
- Do not edit the vendored tree by hand: add a patch and rerun the script. Nothing enforces this yet.

### Generated SPIR-V that cannot be rebuilt here: `slangmosh.hpp`

`app/src/main/jni/pyrowave/pyrowave/shaders/slangmosh.hpp`, `slangmosh_scaler.hpp` and `slangmosh_test.hpp`
hold precompiled SPIR-V of the PyroWave codec as C++ arrays. They run on the device GPU.

- They are generated upstream by Granite's `slangmosh` tool (`pyrowave/slangmosh.sh`) from the `.comp`,
  `.frag` and `.vert` files next to them. **The generator is not in this repository, so Delos cannot
  regenerate them.**
- They are taken unmodified from `Nonary/moonlight-qt@e4693208` (byte-identical, checked 2026-11-15). They
  differ from the original author's files at `Themaister/pyrowave@186f0393`, because Nonary's patches
  changed two shaders and regenerated the bank.
- Not verified: that the arrays match the GLSL sources beside them.

### Generated SPIR-V that can be rebuilt: `shaders_spv.h`

`app/src/main/jni/pyrowave-renderer/shaders_spv.h` holds the 13 SPIR-V modules of Delos' own renderer.

- Sources: `app/src/main/jni/pyrowave-renderer/shaders/`. Regenerate with
  `GLSLC=$ANDROID_HOME/ndk/29.0.14206865/shader-tools/<host>/glslc ./build.sh` in that directory
  (`glslc -O --target-env=vulkan1.1`). The script needs GNU `od` (`-w16`), so it does not run with the
  stock macOS `od`.
- The review of 2026-11-15 rebuilt all 13 modules with the NDK r29 `glslc` and found them identical to
  the tracked header. No automated check repeats this yet: a shader change must regenerate the header in
  the same commit.

## 3. Fonts, icons and sounds

| File | Source | Version | Licence | Licence file |
|---|---|---|---|---|
| `app/src/main/res/font/unbounded.ttf` | [google/fonts](https://github.com/google/fonts) `ofl/unbounded` | commit `e7db6ca603fd4862010383ff17ffd6cc4ffac1c0` | OFL-1.1 | `Unbounded-OFL.txt` |
| `app/src/main/res/font/figtree.ttf` | google/fonts `ofl/figtree` | same commit | OFL-1.1 | `Figtree-OFL.txt` |
| `app/src/main/res/font/phosphor_regular.ttf`, `phosphor_fill.ttf` | [phosphor-icons/web](https://github.com/phosphor-icons/web) plus approved input artwork below | `v2.1.2` plus local SVGs | MIT base; OFL-1.1 lettering; CC0-1.0 brand paths | `Phosphor-LICENSE.txt`, `Figtree-OFL.txt`, `Simple-Icons-CC0.txt` |
| `app/src/main/res/raw/ui_{back,error,move,open,select,step,tab,toggle,undo,screenshot}.wav` | [Open Sounds](https://www.romainraynal.fr/en/opensounds) by Romain Raynal, chosen and named by the owner | downloaded 2026-11-24 and 2026-11-27 | royalty-free, free for commercial use, credit appreciated (credited in `docs/CREDITS.md`) | n/a |
| `app/src/main/res/raw/intro_fx.wav` | made for Delos by the owner (Meitoncz) | 2026-11-24 | original to Delos, project licence | n/a |

All `ui_*.wav` files were gain-matched on 2026-11-27 (owner request): each scaled, with no other processing, so its loudest 100 ms reach −28 dBFS RMS with peaks at or below −1 dBFS (`ui_step.wav` is peak-limited at −30.7 dBFS RMS). The owner's unscaled originals stay outside the repository.

The volume dial adds the upstream `headphones` and `bluetooth` glyphs as `VOLUME_HEADPHONES` and `VOLUME_BLUETOOTH`.
They use the same pinned Phosphor sources and MIT notice; both generated font digests are in `third_party/CHECKSUMS.sha256`.

- Fonts: `tools/fonts/subset_fonts.py` downloads the eight source files, checks each against a SHA-256
  digest in the script, and writes the four subset TTFs, the licence files, `Symbols.kt` and
  `docs/prototype/phosphor-icons.css`. See `docs/ICONS.md`. The fontTools version is not pinned, so the
  inputs are verified but the byte identity of a fresh subset is not.
- Sounds: the files are used as delivered (WAV, 48 kHz, 24-bit, stereo); their bytes are recorded in
  `third_party/CHECKSUMS.sha256`.
- Avatars (`app/src/main/assets/avatars/`) and the demo artwork (`app/src/main/assets/artwork/`) are
  images made for Delos; see `docs/GUI.md` and `docs/CREDITS.md`.

### Controller and Mac input artwork in the icon fonts

`tools/fonts/controller/` contains 308 approved original hardware-informed SVGs (Xbox,
DualSense, DualShock 4 and Joy-Con); `tools/fonts/mac/` contains 366 original keyboard,
mouse and trackpad SVGs. Both styles retain their native 256-unit artwork without rescaling.
The geometry is original to Delos except for the following components; no SF Symbols or
commercial controller icon pack is included.

| Component | Source | Licence | Notice |
|---|---|---|---|
| Figtree Medium lettering converted to contours | [google/fonts, pinned Figtree source](https://github.com/google/fonts/tree/e7db6ca603fd4862010383ff17ffd6cc4ffac1c0/ofl/figtree) | OFL-1.1, retained for the derived lettering | `Figtree-OFL.txt`, also beside both SVG sets |
| Xbox and PlayStation Guide marks | [Simple Icons 11.0.0](https://github.com/simple-icons/simple-icons/tree/11.0.0) | CC0-1.0; trademark rights are not waived | `Simple-Icons-CC0.txt`, also beside controller sources |

Figtree's pinned variable source SHA-256 is
`26ad3db9b31ff7dde67a91ff515d022d2f495cd506590699cf264f0bfe6fb714`;
its unchanged OFL notice SHA-256 is
`140d37233e7f3ce7313798befa9600893bcceaf41a55fa0fa5ad52f7f657a268`.
The Figtree component is not relicensed as MIT.

`tools/fonts/controller/brand-marks.json` preserves the original Simple Icons path data and
these original-file identities:

| Mark | Versioned source URL | SHA-256 |
|---|---|---|
| Xbox | https://raw.githubusercontent.com/simple-icons/simple-icons/11.0.0/icons/xbox.svg | `c5ec61839e921542b3581d9aab7d72b822200975c4b508ea4f9bc02ce4c6f025` |
| PlayStation | https://raw.githubusercontent.com/simple-icons/simple-icons/11.0.0/icons/playstation.svg | `b68b4d7b63443759b9c4d77a5501c5758eeae06a6d594d278d5eb6cb4d3dbbe4` |

The marks identify controller controls, not sponsorship or endorsement. Canonical SVGs,
their notices and brand metadata have individual SHA-256 entries in
`third_party/CHECKSUMS.sha256`. `custom.json` and `icons.txt` connect all 337 pairs to
stable, collision-checked U+F001–U+F151 mappings, preserving Logo at U+F000.
Generation uses the existing pipeline described in `docs/ICONS.md`.

## 4. Build tools

### Gradle wrapper

| Item | Value |
|---|---|
| Gradle version | 9.9.1 (`gradle/wrapper/gradle-wrapper.properties`) |
| Distribution | `https://services.gradle.org/distributions/gradle-9.9.1-bin.zip` |
| `distributionSha256Sum` | `d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228` (equals `gradle-9.9.1-bin.zip.sha256` on services.gradle.org) |
| `gradle/wrapper/gradle-wrapper.jar` SHA-256 | `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8` (equals `gradle-9.9.1-wrapper.jar.sha256` on services.gradle.org) |
| Licence | Apache-2.0 (build tool, not shipped) |

The wrapper downloads the distribution and refuses it when the checksum differs. To move to another
Gradle version, take the checksum from `https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256`
and run this command twice (the first run updates the properties, the second the jar and the scripts):

```sh
./gradlew wrapper --gradle-version <version> --distribution-type bin --gradle-distribution-sha256-sum <checksum>
```

Then compare `shasum -a 256 gradle/wrapper/gradle-wrapper.jar` with
`https://services.gradle.org/distributions/gradle-<version>-wrapper.jar.sha256`, and update the jar's line
in `third_party/CHECKSUMS.sha256` and the table above.

### Repositories

Maven Central and Google's Maven repository serve everything, with one exception:
[JitPack](https://jitpack.io) serves exactly `com.github.cgutman:ShieldControllerExtensions`, which is
published nowhere else. The root `build.gradle` declares JitPack with `exclusiveContent`, so no other
module is ever looked up there and that module is never looked up elsewhere. JitPack builds artefacts on
demand; the checksum in `gradle/verification-metadata.xml` pins the one Delos uses
(`ShieldControllerExtensions-1.0.1.aar`, SHA-256 `f53ddeedfa584db60d66e11ba675d322813b60706f9b213976bf95994fcb7384`).

### Dependency verification

`gradle/verification-metadata.xml` lists the SHA-256 of every artefact Gradle resolves: plugins, the
Android Gradle plugin's tools, libraries, their POM and module files. Gradle checks each download and each
cached file against it and fails the build on a mismatch or an unlisted artefact.

- The checksums were recorded from the artefacts as downloaded on 2026-11-15 (trust on first use). They
  are not compared with signatures (`verify-signatures` is off).
- `aapt2` is a per-host binary: the macOS checksum was recorded by Gradle, the Linux and Windows ones were
  added by downloading the two jars from Google's Maven repository.
- `*-sources.jar` and `*-javadoc.jar` are trusted without a checksum: only an IDE fetches them and they
  never reach a build classpath.
- Robolectric downloads its `android-all` runtime jars itself during unit tests. They do not pass through
  Gradle's resolution and are **not** covered.
- The file also lists JaCoCo 0.8.13 (`org.jacoco`, `org.ow2.asm` 9.8) for the unit-test coverage report.
- Verified on 2026-11-15 with an empty Gradle user home: the full build (unit tests, debug and release
  APK, lint) and the coverage report pass with verification on.

When a dependency or plugin is added or its version changes, regenerate the entries in the same commit.
Use an empty Gradle user home, because a warm cache hides files that a fresh machine downloads:

```sh
export GRADLE_USER_HOME=$(mktemp -d)
./gradlew --write-verification-metadata sha256 :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
./gradlew --write-verification-metadata sha256 :app:jacocoDebugUnitTestReport
rm -rf "$GRADLE_USER_HOME"; export GRADLE_USER_HOME=$(mktemp -d)
./gradlew --no-daemon clean :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

- Gradle adds the new entries and keeps the old ones. Review the diff: it must contain only the artefacts
  you meant to change. Remove entries of versions that are no longer used.
- The last command is the proof. Gradle's generator misses a few BOM and parent-POM files that only a
  cold build fetches (four on 2026-11-15: `guava-parent-33.4.0-jre.pom`, two `junit-bom` module files,
  `kotlinx-coroutines-bom-1.8.0.pom`). When the cold build reports "Dependency verification failed" for
  such a file, compare its SHA-256 with the `.sha256` file next to it in the repository and add the entry
  by hand, with an `origin` that says so.
- Re-add the Linux and Windows `aapt2` lines when the Android Gradle plugin version changes.
- If a build fails with "Dependency verification failed" for a version nobody changed, do not regenerate:
  a changed checksum for an unchanged version is the event this file exists to catch.

### Build plugins

| Plugin | Version | Licence |
|---|---|---|
| Android Gradle plugin (`com.android.tools.build:gradle`) | 9.4.0 | Apache-2.0 |
| Kotlin Gradle plugin, Compose compiler plugin | 2.4.21 | Apache-2.0 |
| Android NDK | 29.0.14206865 | Android SDK licence (downloaded by the Android Gradle plugin, not through Maven) |

## 5. Java and Kotlin libraries

All versions are exact; there are no dynamic versions. Direct dependencies in `app/build.gradle`:

| Library | Version | Repository | Licence | Licence file |
|---|---|---|---|---|
| `org.bouncycastle:bcprov-jdk18on` | 1.87 | Maven Central | MIT | `BouncyCastle-MIT.txt` |
| `org.bouncycastle:bcpkix-jdk18on` (brings `bcutil-jdk18on` 1.87) | 1.87 | Maven Central | MIT | `BouncyCastle-MIT.txt` |
| `org.jcodec:jcodec` | 0.2.5 | Maven Central | BSD-2-Clause | `JCodec-BSD-2-Clause.txt` |
| `com.squareup.okhttp3:okhttp` (brings Okio 3.18.1) | 5.5.1 | Maven Central | Apache-2.0 | `OkHttp-Apache-2.0.txt`, `Okio-Apache-2.0.txt` |
| `org.jmdns:jmdns` (brings SLF4J API 2.0.7, MIT) | 3.6.5 | Maven Central | Apache-2.0 | `JmDNS-Apache-2.0.txt`, `SLF4J-MIT.txt` |
| `com.github.cgutman:ShieldControllerExtensions` | 1.0.1 | JitPack | MIT | `ShieldControllerExtensions-MIT.txt` |
| `com.google.code.gson:gson` (brings Error Prone annotations 2.50.0) | 2.15.0 | Maven Central | Apache-2.0 | `Gson-Apache-2.0.txt`, `ErrorProne-Annotations-Apache-2.0.txt` |
| `androidx.annotation:annotation` | 1.11.0 | Google | Apache-2.0 | `AndroidX-Apache-2.0.txt` |
| `androidx.appcompat:appcompat` | 1.8.0 | Google | Apache-2.0 | `AndroidX-Apache-2.0.txt` |
| `androidx.preference:preference` | 1.2.1 | Google | Apache-2.0 | `AndroidX-Apache-2.0.txt` |
| `com.google.android.material:material` | 1.14.0 | Google | Apache-2.0 | `Material-Components-Apache-2.0.txt` |
| `androidx.compose:compose-bom` (foundation, ui, animation 1.12.1) | 2026.09.00 | Google | Apache-2.0 | `AndroidX-Apache-2.0.txt` |
| `androidx.activity:activity-compose` | 1.13.0 | Google | Apache-2.0 | `AndroidX-Apache-2.0.txt` |
| `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | 2.11.0 | Google | Apache-2.0 | `AndroidX-Apache-2.0.txt` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Maven Central | Apache-2.0 | `kotlinx-coroutines-Apache-2.0.txt` |
| `dev.chrisbanes.haze:haze-blur` (brings JetBrains Compose Multiplatform 1.12.2) | 2.1.0 | Maven Central | Apache-2.0 | `Haze-Apache-2.0.txt`, `JetBrains-Compose-Apache-2.0.txt` |
| `androidx.media3:media3-exoplayer`, `media3-exoplayer-hls` (bring the other Media3 modules 1.11.1, Guava 33.3.1-android and Guava failureaccess 1.0.2) | 1.11.1 | Google | Apache-2.0 | `AndroidX-Apache-2.0.txt`, `Guava-ListenableFuture-Apache-2.0.txt` |
| `com.android.tools:desugar_jdk_libs` (core library desugaring) | 2.1.5 | Google | GPL-2.0 with Classpath exception | `desugar_jdk_libs-GPL-2.0-with-Classpath-exception.txt` |

Transitive runtime libraries with their own licence file: Kotlin standard library 2.4.21, kotlinx.serialization
1.7.3, JetBrains annotations 23.0.0, JSpecify 1.0.0, Guava ListenableFuture 1.0, Guava 33.3.1-android and failureaccess 1.0.2 (all Apache-2.0; Guava's licence file covers them). The
complete resolved set, with checksums, is in `gradle/verification-metadata.xml`;
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath` prints the tree.

Test-only, not shipped: JUnit 4.13.2 (EPL-1.0), `androidx.test:core` 1.7.0 (Apache-2.0), Robolectric 4.17
(MIT), Mockito 5.19.0 (MIT), JaCoCo 0.8.13 (EPL-2.0).

### Licence texts in the app

`app/src/main/assets/licenses/` holds one file per project and `INDEX.txt`, which maps component and
version to its file. Each file added in the 2026-11-15 review starts with one line naming the component,
its version and the URL the text was read from, followed by the text exactly as the project publishes it.
`Delos-GPL-3.0.txt` is the license of the app itself, a byte-identical copy of `LICENSE`. `LicenseAssetsTest`
checks that copy and that `INDEX.txt` and the folder name the same files.
The files are packaged into the APK. The app does not show them on a screen yet.

## 6. Known gaps

Stated plainly, so nobody has to find them:

1. **OpenSSL is one security release behind.** 4.0.3 (2026-09-29) fixes 14 CVEs, the highest rated High
   (CVE-2026-84782, DTLS). They are in DTLS/TLS/QUIC (`libssl`, which Delos does not ship), X.509
   verification, EC/SM2 signing and the CMP client. Delos calls only `EVP_aes_128_gcm`, `EVP_aes_128_cbc`,
   the `EVP_Encrypt*`/`EVP_Decrypt*`/`EVP_CIPHER_CTX_*` functions and `RAND_bytes`, so **none of the 14
   looks reachable**. That assessment comes from a search of the source for OpenSSL calls, not from a
   linker map of the shipped library. OpenSSL 4.1.0 also exists. Upstream `moonlight-mobile-deps` has not
   published newer binaries (checked 2026-11-15).
2. **The prebuilt libraries were never rebuilt from source by this project**, and the upstream artefact
   that proves their origin expires on 2026-12-01 (section 1).
3. **The delos-common-c update was tested on one device and one host only.** On 2026-11-15 delos-common-c
   (pin `8c208139`) was updated with the changes of `moonlight-stream/moonlight-common-c` `master` (`94c65fd3`,
   65 commits: fixes to the RTSP parser, STUN validation, replay detection, video frame reassembly bounds and a
   use-after-free of queued controller packets); the 12 Delos and Artemis commits of the fork are kept.
   Evidence (2026-11-15), AYN Odin 2 Portal Max with Vibepollo on Windows: H.264, HEVC, AV1, PyroWave 4:4:4 and
   PyroWave HDR start and end; audio, controller and keyboard input, rumble, reconnect after a Wi-Fi drop and
   the link test work. Vibepollo crashed once at the end of an HEVC HDR stream; two repeats of the same case
   did not crash, and the cause is not known. Not tested: Apollo, Sunshine, a Linux host, an HDR switch during
   a stream, STUN.
4. **`slangmosh.hpp` cannot be regenerated here** (section 2).
5. **No automated check** yet compares the vendored PyroWave tree with its pinned commit, or
   `shaders_spv.h` with its sources.
6. **Robolectric's runtime jars and the NDK** are downloaded outside Gradle's dependency verification.
7. **Vulnerability databases were not queried** for opus, the Maven dependencies or the npm dependencies
   of `tools/metadata-worker` and `tools/report-worker` (which are separate services with their own
   `package-lock.json`, not part of the app).
8. **History:** the git history still contains about 350 MB of `.tflite` depth-model files that an
   upstream fork added in 2025 and Delos removed. They are not in the current tree and are not shipped.

## 7. PlayStation Remote Play: trademarks and origin

**No affiliation.** The PlayStation Remote Play support is an unofficial, community-made feature. Delos and its authors are
not affiliated with, endorsed, sponsored or approved by Sony Interactive Entertainment Inc. or any of its affiliates. Sony
does not support this feature and may change its consoles or services so that it stops working.

**Trademarks.** "PlayStation", the "PS" family logo, "PS5", "PS4", "DualSense", "DUALSHOCK" and "Remote Play" are registered
trademarks or trademarks of Sony Interactive Entertainment Inc. Delos uses these names only to describe compatibility
(nominative use). The origin of the controller and console symbols in the icon fonts is described in section 3.

**Origin of the implementation.**

| Item | State |
|---|---|
| Protocol code (`app/src/main/java/com/limelight/ps/`) | Written for Delos from the description in `docs/research/ps-remote-play-protocol.md`. It contains no Sony source code and no source code of another Remote Play client |
| Sony SDKs, libraries or binaries | None used, none shipped |
| Protocol constants (`PsConstants.kt`, generated by `tools/ps/gen_constants.py`) | Shipped. These are key tables and client identifiers that the protocol requires for interoperability. They originate from Sony's Remote Play client and were published by third parties; `docs/research/ps-remote-play-constants.md` lists each one |
| PlayStation Network sign-in | Done on Sony's own web page inside the app. Delos keeps the account ID that links a console; it never sees the password |
| Use | Needs the user's own console and PlayStation Network account. Whether this use fits Sony's terms of service is for the user to judge; this document is not legal advice |
