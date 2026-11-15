# Contributing to Delos

Thanks for your interest. Delos is in active development, has no releases yet, and is maintained by one person ([@Meitoncz](https://github.com/Meitoncz)), so things move at a human pace.

The permanent rules of the project (tests, security, history, binaries, claims, documentation, releases, code) are in [docs/ENGINEERING_RULES.md](docs/ENGINEERING_RULES.md). This page says how to take part; it does not repeat them.

## Reporting bugs and ideas

Use the issue forms: pick "Bug report" or "Feature request" when opening a [new issue](https://github.com/Meitoncz/Delos/issues/new/choose). Search existing issues first. For bugs, the form asks for your device, host software and Delos build (shown on the About page), and explains how to collect logs.

Please remove personal data (IP addresses, PC and account names) from logs and screenshots before posting. The diagnostic log that Delos shares from Settings → About → Debug info, or saves for you after a crash ("Report this problem"), is cleaned automatically (addresses, PC and account names, ids and keys are removed); attach that file to the issue. How it works: [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md).

Problems on the PC side belong to [Vibepollo](https://github.com/Nonary/Vibepollo), [Apollo](https://github.com/ClassicOldSong/Apollo) or [Sunshine](https://github.com/LizardByte/Sunshine).

## Security problems

Do not open a public issue for a vulnerability. Report it privately as described in [SECURITY.md](SECURITY.md), which also describes what Delos encrypts, stores and sends.

## Building

[docs/BUILDING.md](docs/BUILDING.md) has the requirements for macOS, Linux and Windows, the submodule, `local.properties` and every command. In short: JDK 21, the Android SDK, a checkout path without spaces, then

```sh
git clone --recurse-submodules https://github.com/Meitoncz/Delos.git
cd Delos
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

## Tests and lint

| Command | What it checks |
|---|---|
| `./gradlew :app:testDebugUnitTest` | The unit tests (JVM and Robolectric) |
| `./gradlew :app:testDebugUnitTest --tests 'com.limelight.diag.*'` | One package or class of them |
| `./gradlew :app:lintDebug` | Android lint with `app/lint.xml` |
| `./gradlew :app:assembleDebug` | That the app builds |
| `python3 tools/docs/check_links.py` | Relative links and anchors of the Markdown files |
| `python3 tools/l10n/find_literals.py` | User-visible text that is not a string resource |

Run the first three before you open a pull request: CI runs only when the maintainer starts it, never by itself on a push or a pull request.

A bug fix comes with a regression test that fails without the fix. New logic comes with tests. Where a unit test cannot reach the code (native code, behavior that needs a device), say so in the commit body with a line `No test: <reason>`.

## Commit messages

Commits follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/). The types, the scopes and every rule are in [docs/ENGINEERING_RULES.md](docs/ENGINEERING_RULES.md#9-commit-messages-and-branch-names); this is the short form.

```
fix(stream): keep the bitrate after a failed request

The page reset its slider to the profile value when the host answered
with an error. It now keeps the value the user chose and shows the
host's message.

Refs: #123

[skip ci]
```

- **Subject:** `<type>[(scope)][!]: <description>`, at most 72 characters. The description says what the commit does, in the imperative, without a period at the end.
- **Types:** `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `style`, `revert`.
- **Scopes** (optional): `ui`, `stream`, `pyrowave`, `input`, `host`, `l10n`, `deps`, `docs`, `ci`, `tools`, `release`.
- **Breaking change:** `!` before the colon (`feat(host)!: ...`) or a footer `BREAKING CHANGE: <description>`. Breaking means an incompatible change of the public API declared in [docs/VERSIONING.md](docs/VERSIONING.md#public-api).
- **Body:** after an empty line. What changed and why, measurements with device and date, and what was not verified.
- **Footers:** after an empty line after the body. An issue is named as `Refs: #123`, or `Closes: #123` when the commit closes it.
- **`[skip ci]`** is the last line of every commit to `main`, after the footers and an empty line. GitHub Actions does not start workflows for a pushed commit whose message contains it. The Delos workflow starts only by hand today, so the line has nothing to skip yet; the maintainer keeps it so that no push starts a run if an automatic trigger is ever added. A contributor's pull request does not need it.
- **One topic per commit.** No `WIP`, `fixup!` or `squash!` commits on `main`: tidy them up before the merge.
- The convention applies from 2026-11-15. The history of `main` is never rewritten, so older commits keep their subjects; a mistake is fixed by a new commit.

## Branch names

Branches follow [Conventional Branch](https://conventionalbranch.org): `<type>/<description>`, with the type `feature`, `bugfix`, `hotfix`, `release` or `chore` and a description in lower-case letters, digits and single hyphens. The issue number comes first when there is one: `feature/87-review-rules`, `bugfix/123-bitrate-reset`, `release/v1.0.0`.

## Hooks

Two hooks check the commit message and, on a push, the name of a new branch. Enable them once per clone:

```sh
git config core.hooksPath tools/hooks
```

`sh tools/hooks/check-branch-name.sh` checks the current branch by hand.

## Principles

- Controller first. Every screen must be usable with the D-pad or stick, A/B/X/Y and the two menu buttons. Touch is supported but is not the design target.
- No confirmation dialogs in normal flows. A security prompt for something that comes from outside the app (a link, a shortcut made by another app) is the exception.
- Visual changes start as a prototype or a discussion in an issue, before code.
- Keep the Java package `com.limelight`.
- The app is called Delos in every language: the name is never translated or inflected.
- Delos is GPL-3.0. By contributing you agree your work is released under the same license.

## Pull requests

Welcome without prior discussion:

- Bug fixes
- Device compatibility reports and fixes
- Corrections of translations by native speakers: ten languages ship, and they were not audited by a native speaker of each language yet ([docs/LOCALIZATION.md](docs/LOCALIZATION.md))

Please discuss in an issue first:

- New features
- UI changes
- Anything touching moonlight-common-c, which lives in a fork submodule
- A new dependency or binary: each needs its origin, version, license and checksum recorded ([docs/THIRD_PARTY.md](docs/THIRD_PARTY.md))

Keep PRs small and focused, and fill in the PR template. A change of behavior updates its document in `docs/` in the same pull request, and user-visible text exists in all ten languages.

## Code style

Follow the patterns already in the code. The shell UI is Kotlin with Compose; the streaming core is Java. Do not reformat code you are not changing: files that come from Moonlight and Artemis must stay mergeable.

## Documentation

All documents are listed in [docs/README.md](docs/README.md). They follow one style, precise, direct and structured, defined in [docs/ENGINEERING_RULES.md](docs/ENGINEERING_RULES.md#documentation-style).

## Claims

README, changelog, release notes and the texts of the app state only what was tested, or say that it was not. A statement about speed, quality, latency or compatibility in a pull request names the device, the date and the number or observation behind it.

## AI tools

The maintainer works with AI coding agents and reviews all changes. If you use AI tools for a PR, you are still responsible for understanding and testing what you submit.

## Versions

Delos follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html); the public API it is measured against and the history of the reconstructed 0.x versions are in [docs/VERSIONING.md](docs/VERSIONING.md). The changes per version are in [CHANGELOG.md](CHANGELOG.md), which follows [Keep a Changelog 1.0.0](https://keepachangelog.com/en/1.0.0/). Add user-facing changes under "Unreleased" with one of its six headings (Added, Changed, Deprecated, Removed, Fixed, Security): a `feat` goes to Added or Changed, a `fix` to Fixed or Security; version bumps are made only by the maintainer when a release is announced.
