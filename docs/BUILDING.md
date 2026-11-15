# Building Delos

How to build, test and lint Delos from source on macOS, Linux or Windows. The rules for a contribution are in
[CONTRIBUTING.md](../CONTRIBUTING.md); where each bundled library comes from is in [THIRD_PARTY.md](THIRD_PARTY.md).

## What you need

| Tool | Version | Where it is pinned |
|---|---|---|
| JDK (to run Gradle) | 21 | not pinned; any JDK 21 distribution |
| Gradle | 9.7.1, through the wrapper | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle plugin | 9.4.0 | `build.gradle` |
| Android SDK platform | 37 (`platforms;android-37.0`) | `compileSdk` in `app/build.gradle` |
| Android NDK | 29.0.14206865 | `ndkVersion` in `app/build.gradle` |
| Git | any recent version | |

- Always use the wrapper (`./gradlew`, `gradlew.bat` on Windows), never a separately installed Gradle.
- The app's Java source and target level is 17 (`app/build.gradle`); that is the language level, not the JDK that
  runs Gradle.
- The native code is built with `ndk-build` (`app/src/main/jni/Android.mk`), not CMake. The Android Gradle plugin
  installs the pinned NDK itself when the SDK licenses are accepted.
- Delos targets Android 10 or newer (`minSdk 29`, `targetSdk 36`) and is built for ARM only (`arm64-v8a` and
  `armeabi-v7a`).
- **The checkout path must not contain spaces.** `ndk-build` (make) cannot handle them, even when the path is
  quoted; the typical error is `APP_BUILD_SCRIPT points to an unknown file`. The same holds for git worktrees.

### macOS

```sh
brew install openjdk@21 android-platform-tools
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

Install the SDK with Android Studio's SDK Manager, or with `sdkmanager` from the Android command-line tools.

### Linux

Install a JDK 21 package of your distribution (for example `openjdk-21-jdk` on Debian and Ubuntu) and the Android
command-line tools, then:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # the directory of your JDK 21
export ANDROID_HOME="$HOME/Android/Sdk"
```

This is also what the manual CI workflow uses (`.github/workflows/build.yml`, `ubuntu-latest` with Temurin 21).

### Windows

```powershell
winget install Git.Git EclipseAdoptium.Temurin.21.JDK Google.AndroidStudio
```

Set `JAVA_HOME` to the installed JDK 21 directory and use a checkout path such as `C:\src\Delos`. Commands below
are the same with `.\gradlew.bat` in place of `./gradlew`.

### SDK packages

With `sdkmanager` (any system):

```sh
sdkmanager 'platforms;android-37.0' 'platform-tools' 'ndk;29.0.14206865'
```

## Get the source

```sh
git clone --recurse-submodules https://github.com/Meitoncz/Delos.git
cd Delos
```

In an existing clone: `git submodule update --init --recursive`. The submodule is the streaming core,
`app/src/main/jni/moonlight-core/moonlight-common-c` ([Meitoncz/Delos-common-c](https://github.com/Meitoncz/Delos-common-c)),
which brings its own nested submodules; the build fails without it.

Not everything native is built from source in this repository:

- `libcrypto.a` (OpenSSL) and `libopus.a` (Opus) are **prebuilt static libraries**, one per ABI, under
  `app/src/main/jni/moonlight-core/openssl/` and `app/src/main/jni/moonlight-core/opus/`.
- PyroWave, Granite, volk and the Vulkan headers are **vendored source** under `app/src/main/jni/pyrowave/`
  (`VENDOR.txt` names the commits; `tools/pyrowave/` holds the vendor script and the patches).

Origin, version, license and checksum of each of them: [THIRD_PARTY.md](THIRD_PARTY.md).

## local.properties

Create `local.properties` in the checkout root (it is ignored by git) unless `ANDROID_HOME` is set:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

On Windows escape backslashes and the colon: `sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk`. Do not set
`ndk.dir`; the NDK is chosen by `ndkVersion`.

Optional: genres, themes and native-HDR facts come through the Delos metadata service
([tools/metadata-worker/README.md](../tools/metadata-worker/README.md)). Its base URL is not a secret. Set it in
`local.properties` or in the environment variable `DELOS_METADATA_URL` (the environment wins when it is not blank):

```properties
DELOS_METADATA_URL=https://<your-worker>.workers.dev
```

Without it the build works and the app shows those facts as unavailable. Never put keys, tokens or passwords into a
tracked file.

## Build, test and lint

The one command to run before a pull request:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

| Task | What it does | Output |
|---|---|---|
| `:app:assembleDebug` | Debug APKs, application ID `io.github.meitoncz.delos.debug` | `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`, `app-armeabi-v7a-debug.apk` |
| `:app:assembleRelease` | Release APKs (minified), application ID `io.github.meitoncz.delos`; unsigned unless you configure signing | `app/build/outputs/apk/release/` |
| `:app:testDebugUnitTest` | JVM and Robolectric unit tests | `app/build/reports/tests/testDebugUnitTest/index.html` |
| `:app:lintDebug` | Android lint with `app/lint.xml` | `app/build/reports/lint-results-debug.html` |

There are no product flavors. The source namespace stays `com.limelight` so that Moonlight and Artemis can be merged.

One test class or one test:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.limelight.diag.IssueUrlTest'
./gradlew :app:testDebugUnitTest --tests 'com.limelight.diag.IssueUrlTest.<method name>'
```

Coverage of the unit tests (JaCoCo) and the check against the recorded floors:

```sh
./gradlew :app:jacocoDebugUnitTestReport     # runs the unit tests; report in app/build/reports/jacoco/jacocoDebugUnitTestReport/
python3 tools/coverage/check_coverage.py     # fails when a package is below its floor in tools/coverage/baseline.json
```

The tools have their own checks:

```sh
python3 tools/docs/check_links.py                          # relative links and anchors of the tracked Markdown files
python3 -m unittest discover -s tools/docs                 # tests of the link check
python3 -m unittest discover -s tools/coverage             # tests of the coverage check
python3 tools/l10n/find_literals.py                        # user-visible text that is not in a string resource
sh tools/verify-binaries.sh                                # every tracked binary against its recorded checksum
(cd tools/metadata-worker && npm ci && npm test)           # the metadata service
```

## Run it

Install the ARM64 debug APK on a device with USB or wireless debugging enabled:

```sh
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

What can be tested where:

- **A stream needs a real ARM device** and a PC with Vibepollo, Apollo or Sunshine. The emulator cannot run a
  stream.
- **The shell** (library, settings, layouts for other screen shapes, the dual-screen companion) can be checked on an
  Android emulator with the debug previews, which need no PC: `--es preview_screen <name>` on `ShellActivity`
  ([GUI.md](GUI.md), "Screen shapes"; [DUAL_SCREEN.md](DUAL_SCREEN.md), "How to test the companion on the emulator").
  The scripts in `tools/layout/` drive those previews and refuse anything that is not an emulator.

## Continuous integration

The workflow `.github/workflows/build.yml` runs only when it is started by hand (Actions → Build → Run workflow). It
runs the unit tests, lint, the debug and the unsigned release build and the repository checks listed above. Nothing
runs automatically on a push or a pull request, so run the command above yourself before you open one.

## Upstream remotes (optional)

Only needed to compare with or merge the projects Delos grew out of:

```sh
git remote add moonlight https://github.com/moonlight-stream/moonlight-android.git
git remote add artemis   https://github.com/ClassicOldSong/moonlight-android.git
```
