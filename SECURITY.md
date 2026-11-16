# Security policy

## Supported versions

| Version | Supported |
|---|---|
| The latest release | Yes |
| `main` before the first release | Yes |
| Older releases | No: update to the latest release |

Delos 1.0.0 is the first release. Reports against the latest release and the current `main` are handled.

## Reporting a vulnerability

Report it privately through GitHub: [Report a vulnerability](https://github.com/Meitoncz/Delos/security/advisories/new)
(repository → Security → Advisories → Report a vulnerability). Do not open a public issue and do not post details in
a discussion or a pull request.

Include the Delos version or commit, the device and Android version, the host software and version, and the steps
that show the problem. Remove addresses, PC names and keys from logs.

Delos is maintained by one person. A report is answered as soon as the maintainer can; there is no guaranteed
response time. A fix is published with a note in `CHANGELOG.md`, and the reporter is credited unless they prefer
not to be.

## Scope

| In scope | Out of scope |
|---|---|
| The Delos Android app: this repository, including the native code under `app/src/main/jni/` | The host software on the PC ([Vibepollo](https://github.com/Nonary/Vibepollo), [Apollo](https://github.com/ClassicOldSong/Apollo), [Sunshine](https://github.com/LizardByte/Sunshine)): report there |
| The streaming core fork [Meitoncz/Delos-common-c](https://github.com/Meitoncz/Delos-common-c) | Problems that need a rooted or already compromised device |
| The metadata service in `tools/metadata-worker/` and the report service in `tools/report-worker/` | The third-party services Delos reads from (SteamGridDB, HowLongToBeat, IGDB, PCGamingWiki, Steam) |
| The GitHub workflow and the build files of this repository | A stream that the host sends without encryption (see "What is encrypted") |

A problem inherited from Moonlight or Artemis is in scope; it is also reported upstream.

## Security model

Delos is a client. It connects to a PC that the user paired, and to a few internet services for artwork and game
facts. The PC is trusted after pairing: it sends video and audio and receives the user's input.

### Pairing and identity

- Delos creates one key pair and certificate on the device at first use.
- Pairing uses the GameStream pairing protocol over the PC's HTTP port. The PIN is never sent: both sides prove
  that they know it, so someone who only listens or relays cannot complete a pairing.
- Pairing stores the PC's certificate. Every later request to the paired PC (app list, launch, the session key,
  runtime bitrate, the link test's token) uses HTTPS with Delos's client certificate, and the PC's certificate
  is pinned to the stored one.
- A pairing link or a launch link opened from outside Delos (`art://`, a shortcut made by another app, an opened
  `.art` file) asks the user before it does anything. These prompts are the only confirmation dialogs in Delos.

### What is encrypted

Delos shows, for the running stream, whether video, audio and control are encrypted.

| Part | Encrypted |
|---|---|
| Requests to a paired PC | Always: HTTPS with the pinned certificate |
| Controller, keyboard and mouse input | Always, with the session key that travels over that HTTPS connection |
| Control stream | When the host supports the encrypted control stream |
| Audio | When the host offers audio encryption; Delos always asks for it |
| Video | When the host offers or requires video encryption; Delos asks for it on devices with AES instructions |
| Pairing and the first, unpaired contact with a PC | No: plain HTTP by protocol design. It carries the PC's name and state, not user data |
| Artwork and game facts from the internet | Always: HTTPS |

**Encryption of video and audio is the host's choice.** A host may leave them in clear on a local network. Delos
cannot switch them on, does not refuse such a stream, and has no setting that forces encryption; it shows the
state so the user can change the host's setting. While video and audio are in clear, anyone on the network path
can watch and listen, and Delos cannot tell where those packets come from. Details:
[docs/STREAM_QUALITY.md](docs/STREAM_QUALITY.md#stream-encryption).

### Network

- **No listening services.** Delos offers nothing to the network and accepts no incoming connections. Its UDP
  sockets receive only what it asked for: the stream it started, the answers to its mDNS search for PCs, and,
  during a link test, the test packets of the PC (counted only when they come from the PC's address and carry the
  token that Delos sent over HTTPS).
- **A LAN address is never trusted by itself.** Whether an address is private, link-local or public decides only
  technical details: which address is stored, packet size, whether the public address is looked up. It never
  replaces pairing, the pinned certificate or encryption.
- Cleartext HTTP is allowed only because the GameStream protocol needs it for the PC. Clients of internet services
  use HTTPS.

Outbound connections:

| To | When | What is sent |
|---|---|---|
| The PC | Discovery (mDNS on the local network), pairing, library, stream | As above |
| SteamGridDB | The user enters their own API key and downloads artwork | App names, the user's key |
| HowLongToBeat | Game facts are loaded | App names |
| The Delos metadata service (see below) | Game facts are loaded, in builds that have its address | App names |
| `stun.moonlight-stream.org` (Moonlight project) | A PC on the local network is added or found, to learn the public address for streaming from elsewhere | A STUN request; the server sees the public IP address |
| `android.conntest.moonlight-stream.org` (Moonlight project) | A stream fails to connect or is cut off, to tell the user which ports are blocked | A connection test; the server sees the public IP address |
| The Delos report service (see below) | The user reports a problem, in builds that have its address | The cleaned diagnostic log, over HTTPS |
| GitHub, in the browser | The user opens a link or reports a problem | What the user submits |
| `api.github.com` | Once a day when Delos starts, and on Settings → About → Check now, unless "Check for updates" is off | A request for the latest release of Meitoncz/Delos with `User-Agent: Delos/<version>`; GitHub sees the public IP address |
| `github.com` and the download host it redirects to | Only when a newer release exists (the checksum list) and after the user presses Update (the APK) | A plain download request |

Delos has no account, no analytics and no advertising code.

### The metadata service

Genres, themes and native-HDR facts come from IGDB, PCGamingWiki and Steam through a small proxy
(`tools/metadata-worker/`, a Cloudflare Worker). It exists so that the provider credentials never ship in the app:
they are secrets of the Worker. The app sends app names and receives public facts. The service is public and
unauthenticated, limited per address; it stores no user data. A build without the service address shows those
facts as unavailable.

### What Delos stores on the device

All of it is in the app's private storage, which other apps cannot read on a device that is not rooted.

| Data | Leaves the device in a backup |
|---|---|
| The pairing key and certificate of this device | No |
| The list of PCs: names, addresses, MAC addresses, pinned certificates | No |
| Settings, profiles, per-app settings, streaming time, the SteamGridDB key | No |
| The diagnostic log and crash file | No |
| Downloaded artwork and cached game facts | Yes, where the system backs up app files |

The pairing key is not protected by a password or by the Android Keystore: it relies on the app's private
storage. After restoring a backup to a new device, each PC must be paired again.

### Diagnostics

- Delos keeps a small rolling log and, after a crash, a crash file on the device. "Keep diagnostic logs" in
  Settings → About switches both off.
- **Nothing is uploaded automatically.** A log leaves the device only when the user shares it from Debug info or
  chooses "Report this problem" (or "Report a problem" in Debug info). In a build that has the report service (below)
  that uploads the cleaned log and opens a GitHub issue draft with its link in the browser for the user to read and
  submit; "Save the log to Downloads instead", and a build without the service, save a file to Downloads for the user to
  attach.
- Everything that is shared is cleaned first: IP and MAC addresses, PC and account names, ids, keys and tokens
  are replaced. Known gaps: app (game) titles are not removed, and names shorter than three characters are not
  recognized. Read a report before submitting it.
- How it works: [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md).

### Updates

- Delos checks the latest GitHub release at most once a day and offers a newer version in the app. "Check for updates" in
  Settings → About is on by default; off means no request at all. Only `api.github.com` is asked; GitHub sees the IP
  address, nothing else is sent.
- Nothing is installed without the user pressing Update and without Android's own installer prompt. The download is HTTPS
  only (also after redirects) and limited to 250 MB; its SHA-256 must match the `SHA256SUMS.txt` of the same release, else
  the file is deleted and not installed. Android installs an update only when it is signed with the same key as the
  installed Delos.
- The checksum list and the APK come from the same GitHub release, so the checksum detects a damaged or substituted
  download, not a release that was published with a wrong file by someone who holds the release account; the signature
  check of Android covers that case only for the key. Not verified on a device.
- How it works: [docs/UPDATES.md](docs/UPDATES.md).

### The report service

The cleaned log of "Report this problem" goes to a small Cloudflare Worker (`tools/report-worker/`, in builds that have
its address) that stores it for 30 days behind a link that nobody can guess and returns the link for the GitHub draft.
The log is cleaned twice: by the app's scrubber on the device, and by the Worker, which repeats the pattern rules
(addresses, e-mail addresses, URL queries, ids, keys and tokens) before it stores the text. Anyone who has the link can
read the log until it expires, and the link in a public issue is public. The Worker holds no credentials, calls no other
service, keeps no address or account, logs no body, and limits uploads per client and per day. It has no endpoint that
lists or deletes reports. Cloudflare, which runs it, sees the sender's IP address like any web host. The upload is
started by the user for each report. The service was exercised on Cloudflare with `curl` only (not from the app): [tools/report-worker/README.md](tools/report-worker/README.md).

### Known limits

- Video and audio in clear when the host does not encrypt them (above).
- The first contact with a PC and its pairing are not encrypted; an attacker on the same network can show a fake
  PC in the list, but cannot pair in the user's place without the PIN and cannot read a paired PC's data.
- The legacy help screen opens web pages inside the app when the device has no browser.

## Rules for contributors

The security rules of the project are in [docs/ENGINEERING_RULES.md](docs/ENGINEERING_RULES.md), section 2. A change
that adds a listener, an exported component, an outbound endpoint or stored secret data updates this document in
the same commit.
