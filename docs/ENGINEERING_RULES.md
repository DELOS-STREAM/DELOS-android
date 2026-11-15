# Engineering rules

## Purpose

The permanent rules of the Delos project. Each rule states what must hold, why, and how it is checked. How to
build, report and contribute is in [CONTRIBUTING.md](../CONTRIBUTING.md); this document does not repeat it.

## Scope

The rules apply to every change of this repository: code, tests, binaries, documents, GitHub files, release notes
and the texts of the app. A rule changes only by a change of this file that the owner approves.

"Check" names what catches a violation. "CI" is a step of `.github/workflows/build.yml`, which runs on request.
"PR" is a line of `.github/pull_request_template.md`. "Review" means a person reads for it: no script exists.

## 1. Tests

| # | Rule | Why | Check |
|---|---|---|---|
| T1 | Every bug fix adds a regression test that fails without the fix. Where a unit test cannot reach the code (native, device only), the commit body says `No test: <reason>`. | A fixed bug without a test returns unnoticed. | PR "Tests"; review |
| T2 | A new logic class comes with tests. Logic inside an Activity, a Service or a renderer is first moved into a plain class. | Code in Android components is not reachable by JVM tests. | PR "Tests"; review |
| T3 | Coverage of the unit tests is measured with JaCoCo. The floor of a package only goes up. | Coverage that can fall silently protects nothing. | CI "Coverage floors": `tools/coverage/check_coverage.py` against `tools/coverage/baseline.json` |
| T4 | The unit tests pass before every merge to `main` and on the commit that gets a release tag. | `main` is what users build and what a release is cut from. | `./gradlew :app:testDebugUnitTest`; CI "Unit tests"; release checklist |
| T5 | No disabled tests: no `@Ignore`, no commented-out test. | A skipped test reads as a passing one. | CI "No disabled tests" |
| T6 | Tests are deterministic: no network beyond loopback, no dependence on the default locale or time zone, no sleep as the assertion. | A test that fails at random is ignored when it fails for a reason. | Review |

## 2. Security

| # | Rule | Why | Check |
|---|---|---|---|
| S1 | Anything that listens, accepts input from outside the app or leaves the device is authenticated and, where it carries user data, encrypted. | An open port or an unauthenticated message is reachable by everyone on the network. | PR "Security"; [SECURITY.md](../SECURITY.md) lists every listener and outbound endpoint and changes in the same commit |
| S2 | A private, link-local or LAN address is never a trust decision. | A home network contains devices the user does not control. | Review |
| S3 | Exported components validate every input and ask before they act on input from outside the app. | Any installed app or opened link can call them. | Unit tests of the parsers of exported entry points; review |
| S4 | A string from the host, a link or an intent becomes a file path only after validation. | Path traversal out of the intended directory. | Unit tests with traversal input |
| S5 | Connections to the internet use HTTPS. Cleartext is allowed only for the GameStream HTTP port of the PC. | The app-wide network configuration must allow cleartext for that port, so each client enforces HTTPS itself. | Unit tests of the download helpers; review |
| S6 | Secrets and personal data never enter git, logs or backups. Text leaves the device only through `LogScrubber`; a new log field that identifies a person or a PC gets a `LogScrubberTest` case. | A leaked key cannot be taken back. | `LogScrubberTest`; PR "No personal data or secrets"; GitHub secret scanning with push protection |
| S7 | A vulnerability is reported and handled privately. Something private that was pushed is rotated and reported to GitHub Support; it is not "removed" by rewriting history. | A rewrite does not delete what GitHub and clones already have, and it breaks rule H3. | [SECURITY.md](../SECURITY.md); issue form contact link |

## 3. History

| # | Rule | Why | Check |
|---|---|---|---|
| H1 | A commit has one subject line of at most 72 characters that says what changed. Details go to the body. | Lists and release comparisons cut subjects at about 72 characters. | `tools/hooks/commit-msg` |
| H2 | No WIP, fixup or squash commits on `main`. | Each commit on `main` must build and mean something on its own. | `tools/hooks/commit-msg` |
| H3 | The history of `main` and of the `v*` tags is never rewritten without the owner's written approval. A mistake is fixed by a new commit. | Every clone, fork and link depends on the published hashes. | Repository ruleset on `main` (no force push, no deletion); review |
| H4 | Upstream stays mergeable: Moonlight and Artemis commits are never rewritten, upstream is merged with merge commits, and upstream-derived files are not reformatted or restructured. | Protocol and security fixes arrive from upstream. | Review |

## 4. Binaries and third-party code

| # | Rule | Why | Check |
|---|---|---|---|
| B1 | No binary without a row in [THIRD_PARTY.md](THIRD_PARTY.md) (source, version, license, how it was built) and a recorded checksum. | A binary of unknown origin cannot be audited or rebuilt. | CI "Verify tracked binaries": `tools/verify-binaries.sh` |
| B2 | A binary changes only in a commit that also changes its checksum and names the source of the new file. | The record must describe the file that ships. | `tools/verify-binaries.sh` (a mismatch fails) |
| B3 | Dependencies have fixed versions and are verified. A repository other than Maven Central and Google is limited to the groups it is needed for. | A moved version or a substituted artifact changes what ships. | Gradle dependency verification; review |
| B4 | Vendored source records its origin commit; local changes exist as patch files. | The difference from upstream must be reviewable. | Review |
| B5 | Workflow actions are pinned by full commit SHA and workflows declare `permissions`. | A moved tag would run new code with the job's token. | CI "Actions are pinned by commit SHA" |
| B6 | The license of every shipped component is in `app/src/main/assets/licenses/`. | The licenses require it. | Review against [THIRD_PARTY.md](THIRD_PARTY.md) |

## 5. Claims

| # | Rule | Why | Check |
|---|---|---|---|
| C1 | README, changelog, release notes, app texts and the website state only what was tested. The evidence is in `docs/` with device, Android version, host and version, date, and the number or observation. Anything else says "not tested" in the same sentence. | A claim that turns out untrue costs more trust than a feature gains. | PR "Claims"; release checklist in [VERSIONING.md](VERSIONING.md) |
| C2 | No performance number without a recorded measurement. | A number without a measurement is a guess. | PR "Claims"; review |
| C3 | A comparison with other apps names the date of the check and is repeated before each release. | Other projects change weekly. | Release checklist |
| C4 | The in-app text of a feature that was not tested on real hardware says so. | The app is where the user switches the feature on. | Review; release checklist |

## 6. Documentation

| # | Rule | Why | Check |
|---|---|---|---|
| D1 | A change of behavior updates its document in the same commit, and `CHANGELOG.md` "Unreleased" when users can see it. | A specification that lags the code misleads. | PR "Docs" |
| D2 | Living specifications and dated evidence are separate: evidence sits in sections named `Evidence (YYYY-MM-DD)` or in an evidence document, and is not edited later. | A reader must know whether a sentence is true now or was true on one day. | `tools/docs/check_links.py` (structure warnings); review |
| D3 | No internal notes, local paths, task queues or work logs in tracked documents. Open work is a GitHub issue. | They are noise for readers and leak private context. | PR "No personal data or secrets"; review |
| D4 | Every relative link and anchor of the tracked Markdown files resolves, and none points to an untracked file. | A broken link is a broken promise. | CI "Markdown links and anchors": `tools/docs/check_links.py` |
| D5 | A document states a number that the code owns (counts, sizes, timings) only together with the constant or test that enforces it. | Bare numbers go stale first. | Review |
| D6 | Every document follows the documentation style below. | One style makes every document predictable. | `tools/docs/check_links.py` (structure warnings); review |

## 7. Releases

| # | Rule | Why | Check |
|---|---|---|---|
| R1 | A release, a version bump and a tag happen only when the owner announces a release. | The version number is a public statement. | Review |
| R2 | The release page shows the complete changelog section of its version. | Users read the release page, not the repository. | Release checklist in [VERSIONING.md](VERSIONING.md) |
| R3 | The release build is built, linted and smoke-tested on a device before the tag. | Minification and signing only run in the release variant. | CI "Build release APKs"; release checklist |

## 8. Code

| # | Rule | Why | Check |
|---|---|---|---|
| K1 | Lint passes without new errors. | Lint finds leaks and unsafe intent and network use before users do. | `./gradlew :app:lintDebug`; CI "Lint" |
| K2 | No silent catch: an exception is logged through `LimeLog` or `DiagLog` or rethrown; coroutine cancellation is rethrown. | A swallowed exception never reaches the problem report. | Review |
| K3 | Everything registered, bound or acquired is released in the paired lifecycle callback. A native handle has one owner. No static reference keeps an Activity or a View. | Leaks and use-after-free crashes. | Lint `StaticFieldLeak`; review |
| K4 | User-visible text is a string resource and exists in all ten languages. | Text in code cannot be translated. | `tools/l10n/find_literals.py`; `StringResourcesTest` |
| K5 | Code that runs per frame or per input event does not allocate, format strings or build log lines. | Garbage collection pauses are visible as stutter. | Review |
| K6 | Dead code is deleted, not commented out. A `TODO` names its issue. | Commented code is never maintained. | Review |
| K7 | JNI code checks every lookup and allocation result, deletes local references on attached threads and keeps debug-only code behind the debug macro. | A failed lookup crashes the process without a Java stack trace. | Review |

## Documentation style

One style for every document: precise, direct, technical where needed, structured.

### Kinds of document

| Kind | What it is | Where |
|---|---|---|
| Living specification | Describes how a part of Delos behaves now. It must match the code. | `docs/*.md`, listed under "Living specifications" in [README.md](README.md) |
| Dated evidence | Records what was measured, researched or decided on one day. It is not edited afterwards, except to add "superseded by". | `Evidence (YYYY-MM-DD)` sections at the end of a specification, or a document listed under "Dated evidence" |
| Guide or policy | Tells a person what to do: building, contributing, rules, security, versions. | Root documents and `docs/` |

### Skeleton of a living specification

The headings appear in this order. A part that has nothing to say is left out, except Purpose and Verification.

| Section | Content |
|---|---|
| Title (`#`) | The name of the feature or area. |
| `## Purpose` | One paragraph: what the feature is for and what the document specifies. |
| `## Scope` | What is covered and what is specified elsewhere, with links. |
| `## Behavior` or `## Design` | The facts, as numbered or bulleted statements. A large specification uses several topical sections in place of this one. |
| `## Settings` | Preference keys, values, defaults and units, in tables. |
| `## Code map` | The files and classes that implement it, as a table of path and role. |
| `## Verification` | The tests that enforce the specification and how to check it on a device. It ends with `### Not verified`: what was never checked. |
| `## Related` | Other documents and issues. |
| `## Evidence (YYYY-MM-DD)` | One section per day: device, Android version, host, what was checked, the result. |

### Sentences

- Short declarative sentences in the present tense. One fact is stated once, in one place; other places link to it.
- Exact names in backticks: file paths, classes, constants, preference keys, values with their units.
- No account of how the work went. No "we", "I", "the lead", "the user asked" or the name of a tool that wrote the
  code. The person who uses the app is "the user"; the person who decides is "the owner".
- No marketing words (seamless, powerful, blazing) and no filler (simply, basically, of course, it should be noted).
- A statement that is not verified says so with "not verified" or "not measured"; it is not softened with "should".
- No emojis. Headings in sentence case. Tables for anything that can be enumerated. Dates as `YYYY-MM-DD`.
- American spelling (color, behavior, center, license), which is what the app texts and most documents already
  use. Changelog entries of past versions stay as written.
- English. The name Delos is never inflected or translated.

### Exceptions

- `README.md` is the project's front page and keeps its own layout.
- `CHANGELOG.md` follows Keep a Changelog.
- [DECODER_ERRATA.md](DECODER_ERRATA.md) and the vendored third-party documents are upstream text and stay as they are.
