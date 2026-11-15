# Engineering rules

## Purpose

The permanent rules of the Delos project. Each rule states what must hold, why, and how it is checked. How to
build, report and contribute is in [CONTRIBUTING.md](../CONTRIBUTING.md); this document does not repeat it.

## Scope

The rules apply to every change of this repository: code, tests, binaries, documents, GitHub files, release notes
and the texts of the app. A rule changes only by a change of this file that the owner approves.

"Check" names what catches a violation. "CI" is a step of `.github/workflows/build.yml`, which runs with every public release, when its version tag is pushed, and is not started by hand.
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
| H1 | A commit message follows [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) with the types, scopes and footers of section 9. The subject has at most 72 characters. Details go to the body. | The type says what kind of change a commit is, to a reader and to a tool; lists and release comparisons cut subjects at about 72 characters. | `tools/hooks/commit-msg` |
| H2 | No WIP, fixup or squash commits on `main`. One topic per commit. | Each commit on `main` must build and mean something on its own. | `tools/hooks/commit-msg`; review |
| H3 | The history of `main` and of the `v*` tags is never rewritten without the owner's written approval. A mistake is fixed by a new commit. | Every clone, fork and link depends on the published hashes. | Repository ruleset on `main` (no force push, no deletion); review |
| H4 | Upstream stays mergeable: Moonlight and Artemis commits are never rewritten, upstream is merged with merge commits, and upstream-derived files are not reformatted or restructured. | Protocol and security fixes arrive from upstream. | Review |
| H5 | A branch other than `main` is named by [Conventional Branch 1.1.0](https://conventionalbranch.org) with the types of section 9. | The name says what the branch is for before it is opened. | `tools/hooks/pre-push`, `tools/hooks/check-branch-name.sh` |

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
| R4 | Versions follow [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html) against the public API declared in [VERSIONING.md](VERSIONING.md#public-api). | A version number that follows no rule tells the user nothing about an update. | Release checklist |
| R5 | `CHANGELOG.md` follows [Keep a Changelog 1.0.0](https://keepachangelog.com/en/1.0.0/): `[Unreleased]` on top, versions newest first with ISO dates, only the headings Added, Changed, Deprecated, Removed, Fixed and Security, a comparison link per version. | Users read one predictable list per version. | Review; release checklist |

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

## 9. Commit messages and branch names

Adopted on 2026-11-15. The convention applies to commits and branches made from that day on. Earlier history is not
rewritten (rule H3) and keeps its older subjects (`Docs: ...`, `Stream menu: ...`); existing branches keep their
names until they are merged or deleted.

### Commit message

The form is that of [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>[(scope)][!]: <description>

[body]

[footers]

[skip ci]
```

| Part | Rule |
|---|---|
| Subject | The first line: type, optional scope in parentheses, optional `!`, a colon, one space, the description. At most 72 characters. |
| Type | One of the table below, in lower case. |
| Scope | Optional. One of the table below, in lower case. |
| `!` | Directly before the colon: the commit changes the public API incompatibly ([VERSIONING.md](VERSIONING.md#public-api)). |
| Description | Says what the commit does, in the imperative ("add", "keep", "remove"), without a period at the end. |
| Body | Begins after one empty line. Free text: what changed and why, measurements with device and date, what was not verified. `No test: <reason>` (rule T1) is a line of the body. |
| Footers | Begin after one empty line after the body. Each is `Token: value` or `Token #value`; a token uses `-` in place of spaces. |
| `BREAKING CHANGE: <description>` | The footer of an incompatible change; upper case. `BREAKING-CHANGE:` is the same. With `!` in the subject the footer may be left out; the description then describes the break. |
| `Refs: #N` | The footer that names an issue. `Closes: #N` when the commit closes it. |
| `[skip ci]` | The last line of the message of every commit to `main`, after one empty line, **except the commit that gets a release tag**. It is a marker for GitHub Actions, not a footer of the specification: no workflow starts for a pushed commit that contains it. CI runs with every public release, starting with the first (1.0.0): pushing the tag `vX.Y.Z` starts the Build workflow, so the tagged commit must not carry the marker (owner decision, 2026-11-15). A pull request of a contributor never needs it. |
| Merge and revert | The subjects git writes (`Merge ...`, `Revert "..."`) are accepted as they are. A revert written by hand uses the type `revert` and a footer `Refs: <hashes>`. |

| Type | Use | Version ([VERSIONING.md](VERSIONING.md#which-number-increases)) | Changelog section |
|---|---|---|---|
| `feat` | A new feature, or a change of what an existing feature does | MINOR | Added or Changed |
| `fix` | A bug fix | PATCH | Fixed; Security for a vulnerability |
| `perf` | A change that only makes something faster or lighter | PATCH | Changed, with the measurement (rule C2) |
| `revert` | Takes back an earlier commit | as the reverted commit | as the reverted commit |
| `docs` | Documents only | none | none |
| `test` | Tests only | none | none |
| `refactor` | A change of code that changes no behavior | none | none |
| `build` | The build system, Gradle, the NDK build, dependencies | none | none, unless users can see it |
| `ci` | Workflows and repository automation | none | none |
| `chore` | Maintenance that fits no other type | none | none |
| `style` | Formatting only | none | none |
| any type with `!` or `BREAKING CHANGE:` | An incompatible change of the public API | MAJOR | Changed or Removed, with the word "Breaking" |

A removal that users can see is a `feat` or `refactor` with an entry under Removed. A deprecation has an entry
under Deprecated.

| Scope | Area |
|---|---|
| `ui` | The shell: screens, menus, motion, the stream menu, the on-screen keyboard |
| `stream` | The stream activity, decoders, upscalers, statistics, audio |
| `pyrowave` | The PyroWave codec and its renderer |
| `input` | Controllers, touch, mouse, keyboard, rumble, gyro |
| `host` | Pairing, the PC list, host requests, links and shortcuts |
| `l10n` | Strings and translations |
| `deps` | Dependencies and bundled binaries |
| `docs` | Documents, with a type other than `docs` (`fix(docs)`, `chore(docs)`) |
| `ci` | Workflows, with a type other than `ci` (`fix(ci)`) |
| `tools` | Everything in `tools/` |
| `release` | Version numbers, the changelog, release notes |

A new scope is added to this table and to `tools/hooks/commit-msg` in the same commit.

### Branch name

The form is that of [Conventional Branch 1.1.0](https://conventionalbranch.org): `<type>/<description>`.

| Part | Rule |
|---|---|
| Trunk | `main` has no prefix. |
| Type | `feature` (new features), `bugfix` (bug fixes), `hotfix` (urgent fixes), `release` (preparing a release, for example `release/v1.2.0`), `chore` (tasks that are not code, such as dependencies and documents). |
| Description | Lower-case letters `a-z`, digits `0-9` and hyphens between words. No underscores, spaces or other characters. Dots are allowed inside a word, typically in version numbers. |
| Hyphens and dots | Never two in a row, never next to each other, never at the start or the end of the description. |
| Issue number | When the work has an issue, its number is the first word: `feature/87-review-rules`. |
| Length | Descriptive and concise. |

The specification also defines the aliases `feat/` and `fix/`, the agent prefixes `ai/`, `claude/`, `codex/`,
`copilot/` and `cursor/`, and the trunk names `master` and `develop`. Delos does not use them.

### Hooks

`git config core.hooksPath tools/hooks` enables both hooks once per clone.

| Hook | What it does | Tests |
|---|---|---|
| `tools/hooks/commit-msg` | Rejects a message whose subject is not `<type>[(scope)][!]: <description>` with a listed type and scope, is longer than 72 characters, ends with a period, is a WIP, fixup or squash commit, or is not followed by an empty line; a misspelled `BREAKING CHANGE:` footer; `[skip ci]` anywhere but on the last line. | `sh tools/hooks/test-commit-msg.sh` |
| `tools/hooks/pre-push` | Refuses to create a branch with an invalid name on the remote. A branch that already exists there only gets a warning. Tags and deletions are not checked. | `sh tools/hooks/test-check-branch-name.sh` |
| `tools/hooks/check-branch-name.sh [name]` | Checks the current or the named branch; exit 1 with the reason when it is not valid. | the same |

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
