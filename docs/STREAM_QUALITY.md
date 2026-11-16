# Stream quality

## Purpose

What Delos does to the quality, smoothness and latency of a stream on the device: bitrate changes during a stream,
frame pacing and decoding, encryption, the link test and bitrate recommendation, the SGSR upscalers, variable
refresh rate, gyro smoothing and the latency work. Each section specifies the current behavior.

## Scope

| Covered here | Specified elsewhere |
|---|---|
| Runtime bitrate, Fast V-Sync, asynchronous decoding, thread scheduling | SGSR2-DV in depth: [SGSR2_VIDEO.md](SGSR2_VIDEO.md) |
| Stream encryption as the stream negotiates it | The security model as a whole: [SECURITY.md](../SECURITY.md) |
| PyroWave diagnostics, link guidance and the link test | The rows and pages that show these settings: [GUI.md](GUI.md), [SETTINGS.md](SETTINGS.md) |
| SGSR 1, Sharpen only, supersampling, variable refresh rate, gyro smoothing, latency | Rumble: [RUMBLE.md](RUMBLE.md) |

What was checked on a device, and when, is in the "Evidence" sections at the end. Ideas taken from other projects
are credited in [CREDITS.md](CREDITS.md).

## Runtime bitrate

The stream menu's **Bitrate** page (a popover above the menu bar) previews 1–1000 Mbps and sends **Apply** over the existing pinned,
client-certificate HTTPS connection to Vibepollo's `/bitrate?bitrate=<kbps>`. No common-c protocol
extension or host modification is needed. Requests run off the UI thread and only one runs at a time.
The host's XML status must indicate success, and its returned bitrate is the authoritative value.
The installed Vibepollo 2.0.1 implementation caps this command at 500 Mbps (and its configured ceiling).
The menu reports the applied value rather than pretending that an unclamped request was accepted.

Changes affect the current stream only; profile/per-app defaults are not overwritten. Unsupported
hosts, missing sessions, permission errors and connection failures report a message without claiming
success. The stream stays connected. Source: Vibepollo `src/nvhttp.cpp::setBitrate` at installed host
commit `66e169c325dab73e4e780fe34ac3c613af4cdf52`.

## Stream encryption

Delos shows whether video, audio and control are encrypted; encryption of video and audio is decided by the host.

What Delos asks for and what decides the result (`moonlight-core/callbacks.c`, `SdpGenerator.c` in
moonlight-common-c):

| Part of the connection | Delos asks for encryption | It is encrypted when |
|---|---|---|
| App list, launch, the session key, runtime bitrate (HTTPS) | always | always: TLS with the PC's certificate pinned at pairing |
| Controller, keyboard and mouse input | always | always, with the session key |
| Control stream | always | the host supports the encrypted control stream |
| Audio | always | the host offers audio encryption |
| Video | on devices whose CPU has AES instructions | the host offers or requires video encryption |
| Stream setup (RTSP) | not chosen by Delos | the host hands out an encrypted session address (`rtspenc://`) |

Delos cannot switch video or audio encryption on by itself: the host decides, and a host may leave them in clear
on a local network. The state is information for the user, who can then change the host's setting. Delos does
not refuse a stream whose video or audio is in clear and has no setting that forces encryption. The security
model as a whole is in [SECURITY.md](../SECURITY.md).

## Fast V-Sync and asynchronous decoding

**Fast V-Sync (Nano Pacer)** is a separate `frame_pacing` choice; the existing default remains Latency.
It estimates a stable cadence at the lower of stream/display rate, handles fractional display rates,
maps PTS to monotonic time and drains stale output while waiting. Delos bounds the final spin to
100 microseconds and makes the wait interruptible. Existing Balanced/LFR/Cap FPS/Smoothness/Warp paths
remain separate; Fast V-Sync takes precedence over the optional Balanced LFR switch. A frame that
arrives a full period or more late is presented at once instead of waiting another period.

Decode time and total latency in the statistics are stamped when a frame leaves the codec, before any
pacer wait, so they do not include time spent pacing. The 1% low FPS is measured when frames are handed
to the display (after `releaseOutputBuffer`), so it reflects submission cadence, not the panel's
presentation times.

**Asynchronous decoding** is an opt-in Stream setting. MediaCodec callbacks feed bounded input/output
queues instead of polling the codec. The existing submit/render/recovery threads consume those queues.
Generation guards and a closed acceptance gate prevent old indices crossing flush/reset/restart.
Flush resumes callbacks with `start()`, as required by MediaCodec; stop/reset/release detach callbacks.
Callback errors are handed to the render loop's recovery handling rather than swallowed or handled
by blocking the callback thread. Decode timestamp bookkeeping is synchronized between submit/output.
The output queue holds 32 frames, above any codec's own buffer count, so the codec's buffer pool is the
backpressure as in synchronous decoding. If the renderer stalls long enough to fill it (or the input
queue), the dropped output frame or left-out input index is counted and logged, not silent.

Both are MediaCodec features (H.264, HEVC, AV1); PyroWave remains on its separate native Vulkan path.
These settings also participate in the per-app profile/launch snapshot mechanism.

The callback adapter and pacer are adapted from SicarioGaming/Starwire at
`87624983e78782ac6346c0f7369f68f39dfe1082` (GPL-3.0), which credits **derflacco / Artemide** for the
original async decoding and Nano Pacer. Reference flush-order regression tests retain that provenance.

## PyroWave diagnostics

PyroWave decodes and presents on moonlight-common-c's video receive thread, so a slow GPU frame also
stops the UDP socket from being read. The overlay's "Latency" is only the control-channel round trip and
"Lost frames" counts whole frames, so neither shows this. The full statistics panel (not the compact line)
therefore has a **Backlog** section for PyroWave, averaged over one-second windows:

- **Stall (recv thread) avg/max**: time spent inside one frame's submit call, i.e. how long the socket
  was not read. Several times the frame interval (8 ms at 120 FPS) means the kernel buffer can overflow.
- **Frame age at submit avg/max**: time since the frame's first packet arrived when the renderer starts on
  it. Growing values mean frames queue up behind a slow previous frame.
- **Receive to present avg/max**: the same age once the frame has been handed to the presentation queue.
- **Partial frames /s**: frames that arrived with missing packets (zero-filled). **Skipped /s**: frames
  the renderer dropped.

Reading it: high or growing stall and frame age = receiver/GPU backlog (lower bitrate or chroma
resolution, or a faster renderer); partial frames with a low stall = packets lost on the air or in the
network. In debug builds the same numbers, plus received KB/s, bitrate, chroma mode and the per-stage
waits (previous frame, swapchain image, submit+present), are logged once a second under the logcat tag
`DelosPyroDiag`, with a `start:` line (bitrate, chroma requested/negotiated, directSubmit, capability) and
the swapchain image count, present mode and pre-transform when it is created (with the present modes the surface
offers), and in the `present=` / `images=` / `preTransform=` part of the per-second line. The UDP receive buffer is set and
checked inside moonlight-common-c, which logs a warning only when the kernel clamps it; Delos reads the
grant back itself (see [Receive buffer and UDP drops](#receive-buffer-and-udp-drops)). Suggested A/B: 4:2:0 at
225 Mbps, 4:4:4 at 225, 4:4:4 at 270, and 4:4:4 at 150.

## Stream thread scheduling and performance hints (#75)

Thread scheduling is always on while a stream runs and has no setting; the decision is recorded in
[Evidence (2026-11-07): decisions](#evidence-2026-11-07-decisions). It changes where and how urgently the stream's
threads run, not what they do. Nothing in moonlight-common-c is modified. The code is `LatencyThreads.java` and
`latency_threads.c`.

### Thread priority

| Thread | Role | How it is found | Priority |
|---|---|---|---|
| `VideoRecv` | The video receive thread; for PyroWave it also decodes and presents | It is raised from its first decode-unit callback. For decoders that are not direct-submit, `VideoDec` is the thread that calls back, so `VideoRecv` is found by its thread name | nice -8 (`THREAD_PRIORITY_URGENT_DISPLAY`), the priority the MediaCodec render thread uses |
| `InputSend`, `ControlRecv` | Threads that never call into Delos; without the raise they run at nice 0 | By their thread names (`/proc/self/task/*/comm`) when the connection has started | Raised the same way, to nice -8 |

### Big cores

1. Threads announce themselves with `LatencyThreads.register(tid)`: the receive thread, the MediaCodec
   `Video - Renderer` thread, the `CodecAsync` callback thread and the `Video - SGSR` GL thread.
2. Each registered thread gets a `sched_setaffinity` mask of the high-capacity cores: every core with at least 60 %
   of the biggest core's `cpu_capacity` (the gold and prime cores of a Snapdragon 8 Gen 2).
3. The kernel still picks the core inside the mask. Nothing is pinned to the little cores.
4. Placement is skipped when any capacity value is unreadable, all cores are alike, or fewer than two cores qualify.
5. The capacities and the mask are logged once per process (`Delos latency threads: cpu_capacity [...]`).

### Performance hint session (ADPF, Android 12+)

| Property | Value |
|---|---|
| Session | One `PerformanceHintManager` session covers the registered threads |
| Target work duration | 0.6 x the frame period (5.0 ms at 120 FPS) |
| Lifetime | Only from the decoder's start to its stop or pause |
| Reported work | The receive thread's time per decode unit, as the mean of four frames. It is measured natively around the submit callback, so MediaCodec and PyroWave are both covered |
| Late registration | A session takes its threads at creation, so it is re-created when a thread registers later (a codec reset starting a new `CodecAsync` or SGSR thread) |
| No support or an API failure | Logged once; the stream goes on without the session |
| Frame rate | The stream's frame rate is fixed for a connection. `LatencyThreads.setFrameRate` moves the target if a live change is ever added |

`setSustainedPerformanceMode` is deliberately not used: it caps clocks.

### Receive buffer and UDP drops

This part is evidence only: it changes nothing in the stream.

1. On the first frame the receive thread logs the receive buffer of every UDP socket of the process to
   `DelosPyroDiag`. The video socket is the largest.
2. The kernel reports twice the granted size, so about 5800 KB (23000 KB for PyroWave) is a full grant. Less means
   the kernel clamped the request.
3. moonlight-common-c does not expose the socket's descriptor, hence the scan of every socket.
4. Debug builds also read `/proc/net/snmp` once a second and log increases of `RcvbufErrors` (kernel buffer full)
   and `InErrors` to `DelosPyroDiag`. The counters cover the whole system, not only Delos.
5. Android may deny apps that file; one log line then says so.

### Cost

Higher and steadier CPU clocks for the stream's threads: more battery use and heat than without the scheduling,
most visible on a long 120 FPS session. The whole path is per stream, so nothing runs in the menus. If the device
throttles, the hint is a request, not a guarantee. The device procedure is in
[Thread scheduling device procedure](#thread-scheduling-device-procedure).

### Not implemented

| Candidate | Reason |
|---|---|
| `recvmmsg` batching of the UDP reads | It needs changes inside moonlight-common-c's receive loop (the fork). The saving (0.2-0.7 ms per frame) is only a guess until a trace shows per-packet syscall cost |
| Splitting receive from decode for PyroWave | A structural change to the single-threaded reassembly queue. It only avoids loss when a decode exceeds a frame interval, which the stall numbers must show first |
| The input path (1 ms mouse batching, reliable controller packets) | Protocol behavior, host-gated. For a handheld with its own controller the path is already short. Only the priority of `InputSend` and `ControlRecv` is raised |
| Socket marking (DSCP on the video socket) | It lives inside moonlight-common-c, and the downlink class is decided by the host's marking. Only the receive buffer and the drop counters are observable |

## PyroWave link guidance (#18)

PyroWave needs hundreds of Mbps and degrades into blur, not stalls, when the link falls short, so Delos tells the
user what the link carries instead of guessing.

| Part | Codecs |
|---|---|
| **Test link** and the recommendation | **Every codec** (H.264, HEVC, AV1, automatic and PyroWave): the probe measures the network, not the codec |
| The passive in-stream hint and the Link statistics block | PyroWave only (see [Passive hint (PyroWave only)](#passive-hint-pyrowave-only)) |

Everything is recommendation only. Delos never changes the bitrate setting by itself and never sends
`pyrowavePaceMbps` or `pyrowaveLinkMbps` to the host (no JNI plumbing exists for them). The code is
`app/src/main/java/com/limelight/shell/link/` (pure logic and the probe client) and the rows in
`shell/screens/LinkGuidanceRows.kt`.

### Link guidance shown to the user

| No. | Part | Where | Behavior |
|---|---|---|---|
| 1 | **Test link** | Settings, Stream tab, under the bitrate row | An active UDP probe against the PC. It runs only when the user presses it, never automatically and never during a stream: the host refuses probes while a stream session exists, answering HTTP 400 "Stop streaming before testing bandwidth". A cancellable portal says what is being tested ("Testing 250 Mbps · stay on Wi-Fi · B cancels") |
| 2 | **Recommended bitrate with a one-line reason** | Settings bitrate row, per-app bitrate row, stream menu Bitrate page | `Recommended: 225 Mbps · link carries up to 665 Mbps` (both numbers when the link has headroom). The help panel says it in one sentence: "Tested yesterday on Wi-Fi: carries up to 665 Mbps; 225 Mbps is enough for 1080p60 PyroWave 4:2:0.". Settings also offers **Use recommended bitrate**, which fills the bitrate on the user's press. The stream menu's row fills the slider on A and still needs Apply |
| 3 | **Passive in-stream hint** | The same row as the recommendation | A rolling ~5 s window of the renderer's own counters. When the link drops frames the recommendation becomes `Recommended: 135 Mbps · drops frames at 225 Mbps` and the help line says so |
| 4 | **Link block** | The full statistics panel | Received rate against the target bitrate, partial and lost frames per second, and a one-line verdict |

### The probe

The source of truth is Vibepollo `docs/pyrowave-protocol.md` ("Negotiation", "FEC-inclusive recommendations and UDP
calibration") and the host source (`src/pyrowave_udp_probe.h`, `nvhttp.cpp::getPyroWaveUdpProbe`). The conversion
is a port of `pyrowavebandwidth.h` (Nonary/moonlight-qt, identical in Vibepollo).

#### Host support

A paired HTTPS `/serverinfo` carries the fields below. `PyroWaveHostInfo` parses them on every poll into
`ComputerDetails.pyroWave`; the test re-reads `/serverinfo` fresh.

| Field | Status | Note |
|---|---|---|
| `PyroWaveUdpProbeVersion=1` | required | |
| `PyroWaveWireBudgetVersion=1` | required | |
| `PyroWaveCriticalFecPercentage` | required | 0..255 |
| `PyroWaveMinParityShards` | required | |
| `PyroWaveUdpHandshakeVersion=1` | optional | Selects the handshake |
| `PyroWaveHostLinkMbps` | read | The host's routed wired transmit speed, 0 for Wi-Fi/unknown. It is read as a ceiling |
| `PyroWaveBandwidthProbeBytes` | parsed | Only the legacy HTTPS download probe uses it, which Delos does not run |

A PC without the fields, or with a newer protocol version than this Delos knows, gets a sentence instead of a test
("This PC's Vibepollo cannot test the link yet. Update it."). The static recommendation is still shown.

#### Request

`GET /pyrowave-udp-probe?kbps=<5000..3000000>&port=<receiver port>&packetsize=1392&token=<32 lowercase
hex>&handshake=1` goes over the existing pinned, client-certificate HTTPS connection to the PC's address.

| Element | Value |
|---|---|
| Address | Resolved once, IPv4 preferred. The UDP receiver and the HTTPS peer use the same address |
| Token | Fresh per probe (16 random bytes) |
| Packet size | 1392, the streaming packet size |

#### Handshake

1. The host binds its UDP sender, sends a 32-byte token warm-up, flushes the HTTP headers with
   `X-PyroWave-Udp-Port`, and keeps the XML body pending.
2. Delos sends the 32-byte token from its receiving socket to that port every 100 ms until the first measured packet
   arrives. The host waits at most 1500 ms.
3. A host failure after the headers arrives as XML with `status_code=500`. A message containing "handshake" is
   reported as blocked UDP.
4. Hosts without handshake support get the legacy probe: no `handshake` parameter, and the source is checked by
   address only.

#### Probe packets

| Element | Value |
|---|---|
| Duration | Two seconds, paced in 1 ms groups |
| Wire charge of every packet | `packetsize + 134` wire bytes |
| UDP payload | `packetsize + 48`: ASCII token (bytes 0-31), big-endian sequence (32-35), filler |
| Response XML | `expected`, `sent`, `elapsedMs` |
| Accepted answer | `expected` must equal `kbps * 2000 / (8 * (packetsize + 134))` (integer division); otherwise the answer is rejected |
| Datagrams that count | Only exact-size datagrams from the PC (and, with the handshake, from the announced port) that start with the token. Duplicates and foreign packets never raise delivery |
| Wait after the response | 100 ms for late packets |

#### Scoring

`scoreProbe` scores one probe.

1. Arrival times come from `System.nanoTime()`, stamped right after `receive()` returns on a dedicated thread at
   `THREAD_PRIORITY_URGENT_AUDIO`. The Android socket has no kernel timestamps.
2. Delay is relative: transit = arrival less the packet's slot `ceil((seq + 1) * 2000 / expected) - 1` ms, so the
   clocks' offset cancels.
3. The rules are the **reference client's** (`pyrowavelinkpolicy.h`, `capacityQualified()`), not the stricter
   numbers in the host document (see
   [Where Delos follows the reference client, not the host document](#where-delos-follows-the-reference-client-not-the-host-document)).
4. Unsent packets count as lost.

A probe passes when every rule of the table holds.

| Rule | Limit |
|---|---|
| Packets the host sent | At least 98 % of the planned packets, and not more |
| Aggregate loss | < 2 % |
| Loss in each of the 20 sequence windows (100 ms) | < 5 % |
| Delay growth (mean transit of the last window less the first) | <= 2 ms |
| The host's `elapsedMs` | Within 2 % of 2000 |

**p99 transit variation above 4 ms is only a warning** ("Stable, a little jitter", shown in the success toast); it
never fails a probe.

#### Receiver

1. The receive loop allocates nothing, logs nothing and takes no locks: buffer, packet and the score array exist
   before the first datagram.
2. The receive buffer is requested at 8 MiB. The granted size is recorded, and logged in debug builds.
3. Scoring runs after the loop.
4. The loop's busiest datagram handling time and the granted buffer let a loss failure be attributed to this device
   ("This device could not receive 450 Mbps that fast"). That happens when the granted buffer is under 2 MiB or
   one datagram kept the loop busy for over 5 ms.

#### Debug log

Debug builds log one line per probe under the tag `DelosLinkTest`: rate, expected, sent, received, granted rcvbuf,
loss, worst 100 ms window, p99, growth, host elapsed, longest loop stall, authenticated packets, and PASS or FAIL
with the rule.

#### Search

`searchLink` is pure and tested with fake paths.

| Step | Rule |
|---|---|
| Start | The selected bitrate: the current bitrate setting, rounded down to 5 Mbps |
| Bounds | `PyroWaveHostLinkMbps` and 3 Gbps |
| Growth | x1.25 (rounded down to 5 Mbps) while probes pass |
| Failure | Bisect the passing/failing bracket down to 5 Mbps. A failing start bisects downwards to the 5 Mbps floor |
| Confirmation | Take 95 % of the best pass (rounded down to 5 Mbps) and measure it twice afresh |
| Failed confirmation | If either run fails, lower the rate by 20 % and test again (at most 32 attempts, down to 5 Mbps) |
| No confirmed rate | No result. The message names the last probe's real reason ("Lost 3.0% of the test packets at 450 Mbps", "This device could not receive 450 Mbps that fast", ...) and whether any rate passed once |
| Cancel (B) | Abandons everything measured |

The confirmed rate is a **total wire allowance**, the same unit as the bitrate setting.

#### Failure reasons

Every reason is readable text; no raw error is shown.

| Level | Reasons |
|---|---|
| Probe | Host too old or newer protocol, stream active, pairing denied (403), UDP blocked (no packets / handshake timeout), network error, timeout, host error, invalid result |
| Search | Host could not send fast enough, link lost packets, link too unsteady |

#### Stored result

1. The last confirmed test is stored per PC and kind of network (Wi-Fi / Ethernet / other) in the
   `delos_link_tests` preferences, with its date. An SSID needs a location permission, so the transport is the
   cheap stand-in.
2. It is shown with its age ("Tested yesterday on Wi-Fi") and only feeds the recommendation. It is never silently
   re-applied.
3. A network that was not tested shows "Not tested on Wi-Fi yet".

### Wire budget and recommendation

The host reserves the following from every wire allowance: one maximum feasible critical-FEC block (the largest
critical count with `critical + max(ceil(critical * fec / 100), minParity) <= 255` shards), packet rounding, IPv6,
encryption, Ethernet overhead and 3572 kbps for audio and control. `WireBudget` ports `pyrowavebandwidth.h`
(`maxCriticalParity`, `imageBytes`, `wireBytes`, `imageKbps`, `totalKbps`). It is only used to say how much
picture a tested budget carries ("about 102 Mbps of picture after FEC and overhead").

`recommend(...)` = `min(PyroWaveBitrate.recommendedKbps, tested wire budget, PyroWaveHostLinkMbps)`, rounded
down to 5 Mbps, never under 5 Mbps. The live drop hint can only lower it further. The bitrate setting is a total
wire allowance (Delos sends it as `configuredBitrateKbps`), as is the probe's rate, so they compare directly.

| Reason text | When |
|---|---|
| `not tested on this link` | No test on this link |
| `link carries up to N Mbps` | The link has headroom |
| `link carries N Mbps` | The link does not carry enough |
| `the PC's link is N Mbps` | The PC's link is the limit |
| `drops frames at N Mbps` | The live drop hint |

### Link guidance for ordinary codecs (H.264, HEVC, AV1)

Host support is the same check as for PyroWave: the paired `/serverinfo` must advertise
`PyroWaveUdpProbeVersion=1`.

1. The host source shows that the probe handler itself (`getPyroWaveUdpProbe`) asks for nothing but a paired client
   and no running stream, and for no PyroWave setting.
2. The `/serverinfo` fields are only written when the host's PyroWave encoder works on its capture adapter. A host
   without PyroWave support therefore does not advertise the probe, and Delos does not try it.
3. A host that does not advertise gets "cannot test the link yet" and, for an ordinary codec, no extra row at all.
4. The stored result is a wire budget, independent of the codec, so one test serves every codec.

The recommendation for an ordinary codec is `min(useful maximum, 65 % of the tested budget, 65 % of the PC's link)`,
never below 5 Mbps. With no test it is the app's own default bitrate (Moonlight's table: 20 Mbps at 1080p60, 40 at
1440p60, 80 at 4K60, shown as before). The constants are in `LinkPolicy` (**judgment, not measurement; verify on
the Odin**):

| Constant | Value | Reasoning |
|---|---|---|
| `ORDINARY_BUDGET_SHARE` | 0.65 | The test is a clean 2 s burst. A live stream adds FEC (Sunshine default 20 %, configurable higher), audio and control, and its rate control overshoots on scene cuts and keyframes; the 5 % confirmation margin is already in the tested budget. |
| `H264_CAP_FACTOR` | 3.0 x default | 60 Mbps at 1080p60: H.264 needs about half as much again as HEVC/AV1 for the same picture; the quality knee is around 40-60 Mbps. |
| `HEVC_CAP_FACTOR`, `AV1_CAP_FACTOR` | 2.0 x default | 40 Mbps at 1080p60, 80 at 1440p60. Automatic assumes this middle value. |
| `ORDINARY_MAX_KBPS` | 150 Mbps | The knee at 4K60 is about 100-160 Mbps, and hardware decoders (H.264 level 5.2, common HEVC profiles) top out near it. |

| Example | Recommended | Why | Reason text |
|---|---|---|---|
| HEVC 1080p60 on a link that carries up to 665 Mbps | 40 Mbps | The knee, not 65 % of the link | `link carries up to 665 Mbps` |
| H.264 on a link that carries 80 Mbps | 50 Mbps | 65 % of 80, below the 60 Mbps useful maximum | `link carries 80 Mbps` |

The stream menu shows the row for an ordinary codec only when a test exists (testing is done in Settings, before
connecting). Settings shows it when the PC can test or a result exists.

### Passive hint (PyroWave only)

Ordinary codecs show neither this hint nor the Link block: their loss counters are not equivalent.

| Codec | What its loss counters count |
|---|---|
| Ordinary codecs | The RTP queue repairs loss with FEC and recovers with IDR or reference invalidation. The frame-loss figure counts only frames dropped after all that |
| PyroWave | Partial and lost frames count every frame that arrived damaged |

A threshold calibrated on one would be meaningless on the other, so the hint stays PyroWave-only until it is
measured for the others.

Every second the PyroWave stats thread feeds `LinkMonitor` (also with the overlay off): frames, partial, lost
and skipped frames and the app's received rate, with the bitrate in force. The verdict covers the trailing
samples at the current bitrate (a bitrate change starts a fresh reading), at least 3 s and about 5 s:

| Reading | Verdict |
|---|---|
| (partial + lost) / frames >= 2 % | **Link drops frames at N Mbps, try M** (M = N less `clamp(4 x share, 10 %, 40 %)`, rounded down to 5 Mbps) |
| skipped / frames >= 2 % without the above | Device skips frames; the link is fine (no bitrate advice: it is not the network) |
| otherwise | Link keeps up |

The thresholds are named constants in `LinkPolicy` and are **not yet calibrated**; they are Delos's own
(`HINT_DROP_SHARE`, `HINT_CUT_PER_SHARE`, `HINT_MIN_CUT`, `HINT_MAX_CUT`, window and minimum seconds). The
received rate is `TrafficStats` of the whole app (video, audio, control and HTTP together), the same source as
the Bandwidth row but rx only; there is no video-only byte counter in Java. Expect it to read a few Mbps high.

### Reading the link guidance

| Observation | Meaning or action |
|---|---|
| Tested well above the format's need and no drops | Leave the bitrate alone |
| "link tested at 180 Mbps" under a 225 Mbps guide | The format will lose detail on this link. Use recommended bitrate, or lower the resolution or FPS for the full picture |
| Drops in the stream but a good test | The radio changed (distance, interference, roaming) or the device is the limit. Look at the Backlog block (stall and frame age) and at "Device skips frames" |
| Any test result | It is a snapshot of a 2-second burst on a Wi-Fi that swings with RSSI, roaming and power save. Repeat it before concluding anything, and expect variance of a few tens of Mbps |
| What the test proves | It measures UDP delivery only. It does not prove sustained smoothness, host encoding speed, frame-burst delivery or decoder load |

### Where Delos follows the reference client, not the host document

The reference client is Nonary's moonlight-qt (`pyrowavelinkpolicy.h`).

| Rule | Host document | Reference client | Delos |
|---|---|---|---|
| Aggregate loss | 0.1 %, a failure | loss < 2 % | The reference |
| Loss per window | 1 % per window, a failure | any 100 ms window < 5 % | The reference |
| Planned packets sent | all | at least 98 % of planned packets sent | The reference |
| p99 | p99 <= 4 ms, a failure | p99 > 4 ms is a warning only | The reference |
| Delay growth | delay growth <= 2 ms, a failure | delay growth <= 2 ms | The reference, kept as written: delay growth stays a failure (it detects a queue building up) |
| Start of the search | - | A ceiling bounded by the quality need | The selected bitrate (the bitrate setting) |

The device observation behind the choice of the reference rules is in
[Device checks (date not recorded)](#device-checks-date-not-recorded).

### Points the host documents leave open

| Point | What Delos does |
|---|---|
| The documents do not say how the wire allowance relates to the author's *image* recommendation in the bitrate setting | Delos treats the setting and the probe as the same wire unit and compares `recommendedKbps` directly (as the app's default bitrate already does). The FEC-inclusive conversion is therefore informational |
| "All planned packets sent": the host document says all, the reference accepts 98 % | Delos uses 98 % |
| The host's stream-active refusal is an HTTP 400 with the text "Stop streaming before testing bandwidth" (from the host source, not the document) | Delos matches the word "streaming". A probe running concurrently from another client gets the same answer |
| `PyroWaveCriticalFecPercentage` is documented 0..255 (the reference client accepts 0..100) | Delos accepts 0..255 |
| The client's routed wired link speed (`pyrowaveLinkMbps`) | Deliberately not measured or sent |

No measurement run of the link test is recorded; see
[Evidence (2026-11-07): link test on the Odin](#evidence-2026-11-07-link-test-on-the-odin) and
[Not verified](#not-verified).

## Snapdragon Game Super Resolution 1

The official Qualcomm SGSR 1 spatial shader works with the final streamed image; temporal SGSR 2
requires game-engine inputs that a streaming client does not receive. The shader is vendored from
SnapdragonGameStudios/snapdragon-gsr at `d926f074bcb9d714e179f1ce0fcb9ee2eeb5074e`, under BSD-3-Clause.
Its copyright remains in the shader and the full license is bundled as `assets/licenses/sgsr.txt`.
Delos enables edge-direction filtering, exposes sharpening strength and bypasses reconstruction when
downscaling. The optional setting requires OpenGL ES 3.1.

The **Quality** choice offers Standard (the original mobile edge-direction shader) and **High Quality**.
The latter ports `SGSR_TARGET_HIGH_QUALITY` from Qualcomm's official Unity package at
`ab685c993ebd0f65a762c6e28f64f124c7f12532`, `Shaders/v1/sgsr.hlsl` (BSD-3-Clause). It uses RGB-derived
luma, a 4/255 edge threshold and two weighted estimates blended 60% edge / 40% smooth. This is a
different spatial filter, not just a higher sharpening value. Delos uses full precision for this
shader and guards zero variance. It shares HDR/P010 conversion, output size and geometry with
Standard. Both programs compile at initialization; live switching selects the next frame's program
without resetting the decoder. Sharpness remains independent. GPU cost/visual gain require Odin
acceptance. The choice is available globally, per game and session-only in Stream menu → Upscaler.

SGSR 2 is not integrated: its color/depth/velocity, camera transforms and jitter inputs are unavailable
from the decoded stream. Substituting zero motion is not correct for moving video; deriving optical
flow and replacing its other assumptions would be separate video reconstruction work.

`SgsrRenderer` owns its EGL context on a dedicated thread. MediaCodec renders into a SurfaceTexture;
a first pass resolves its transform/crop to a 2D image, then SGSR reconstructs at the display size.
SDR uses an RGBA8 intermediate and the final SurfaceView. The SurfaceView retains aspect-ratio and
input geometry, while interface/statistics/virtual-controls overlays remain outside the filter.
The surface is sized when the stream starts from the requested HDR; if the host then settles on 8-bit
video, the decoder asks `DelosStreamActivity` to give the surface its layout size before creating the SGSR stage.

Ten-bit video uses an RGBA16F intermediate and **P010 AHardwareBuffer** output, via a child
SurfaceControl. This is required on the Odin: labelling an RGB layer as HDR is insufficient on its
display stack. The SGSR fragment output converts encoded RGB into limited-range BT.2020 YCbCr for
HDR (BT.709 when the host switches to SDR). Surface transactions carry BT.2020 PQ dataspace and the
host's SMPTE 2086 / CTA-861.3 metadata. The four-buffer pool honors SurfaceFlinger release fences;
when none is available it drops a frame rather than overwriting a held buffer. GPU completion is
currently synchronized with `glFinish()` before submitting the buffer; there is no CPU pixel copy.
This extra GPU synchronization must be measured on the device, not assumed free.

**Stream menu → Upscaler** contains live On/Off, mode and sharpness controls. MediaCodec toggling
requests a local decoder reset/IDR to change its output surface, preserving the network session.
PyroWave applies changes at a frame boundary without resetting its decoder. Sharpness updates
presentation parameters without restarting either decoder. Live changes are session-only; initial
defaults remain in Display settings and may be overridden per game. Initialization/runtime failures
fall back to direct rendering and are exposed in the menu/logs; HDR is not silently converted to SDR.

Every change on this page is also kept for the running app, automatically: it applies live and, about 500 ms after the
last change (a slider drag or held D-pad writes once; the menu closing or the stream ending writes at once), the current
upscaler on/off, mode, sharpness and supersampling level are written as that app's per-app overrides (see
[PER_APP_SETTINGS.md](PER_APP_SETTINGS.md#the-menu-keeps-the-live-upscaler-and-gyro-smoothing-for-the-app)) and apply from its next
launch. Nothing else is written (no bitrate, no other display setting) and neither the profile nor other apps change; the
running stream keeps its live values. A stream started without its launch identity stays session-only.

Both renderers implement SGSR 1 (Normal), SGSR 1 (Quality), SGSR2-DV and SGSR2-DV (Quality), and
the [Sharpen only mode](#sharpen-only-mode).
PyroWave uses native Vulkan shaders rather than the GLES stage. Spatial modes use distinct Qualcomm
Standard/HQ kernels; temporal Quality adds a separate reactive activation pass and tighter YCoCg
history clipping. All modes retain FP16 intermediates and use the existing HDR/P010 bridge.
Independent bounded sharpening runs only during presentation; zero bypasses the sharpening filter,
and Off disables all SGSR processing. Actual applied native mode/size are reported separately from
the requested setting. Unsupported GPU-P010 processing reports direct rendering, not a false active mode.

The statistics overlay includes the measured post-decoding processing time for every SGSR variant in
both renderers and adds it to total latency. MediaCodec uses smoothed wall time around a successful
SGSR draw; PyroWave uses its Vulkan draw timestamps. This is processing cost, not a claim of complete
input-to-photon latency.

### SGSR 1 reconstruction gain and Sharpness

SGSR 1 **Standard and High Quality use the official fixed reconstruction gain of 2.0** in both
MediaCodec/GLES and PyroWave/Vulkan. The gains used before, and the audit, are in
[Evidence (2026-11-07): SGSR 1 reconstruction gain and Sharpness](#evidence-2026-11-07-sgsr-1-reconstruction-gain-and-sharpness).

Sharpness controls only an independent final bounded cross filter in both SGSR 1 pipelines.

| Quantity | Value |
|---|---|
| Normalized slider `s` | `0..1`, default `0.5` |
| Effective strength | `0.5*s` |
| Detail gain | `0.75` |
| Correction coefficient | `0.375*s` (`0.1875` at 50%, `0.375` at 100%) |
| Correction limit of each channel | `min(0.06, 0.25*localRange)`, with the local-range and output clamps |

**0 bypasses only the final sharpener: fixed-gain SGSR 1 reconstruction remains active.** Upscaler Off bypasses
both. The milder final-filter mapping is intended to add gentle sharpening to full-gain reconstruction
**[INFERENCE]**; it is not RCAS equivalence or device acceptance. The mapping does not change SGSR2-DV/Video
Quality or Sharpen only's CAS mapping.

GLES reuses the supersampling RGB target for one reconstruction draw and a separate cross-filter presentation
draw, avoiding five complete reconstruction evaluations per output pixel. Ordinary spatial upscaling therefore
also needs the intermediate when final filtering is active, although no additional target type exists. HDR
reconstruction remains encoded RGB/PQ until the final P010 conversion. Native uses its compute intermediate and
final presentation/pack pass. Extra GLES storage, bandwidth and GPU cost are not measured. The comparison that
checks this mapping is in
[SGSR 1 gain and Sharpness device procedure (not run)](#sgsr-1-gain-and-sharpness-device-procedure-not-run).

### Sharpen only mode

Issue [#67](https://github.com/DELOS-STREAM/DELOS-android/issues/67). **Sharpen only** is a fifth Upscaler mode (setting value
`sharpen`, Mode row position 5) for sharpness without scaling: the internal size is the stream's resolution whatever
the supersampling level, and the only picture change is one contrast-adaptive sharpen pass using the existing
sharpness value (0 leaves the picture unchanged). The Sharpness slider keeps working in every other mode as before.
The Enabled toggle, the Mode row and Sharpness work live; the supersampling quality has no meaning in this mode, so
the stream menu hides that row and Settings disables it with "Not used by Sharpen only.". The mode is saved and
restored globally and per game like the others (`list_sgsr_quality=sharpen`); older builds read the value as Standard.

**Algorithm.** SGSR 1 at a scale factor of 1 is not a plain sharpen (it is an edge-directed resample of the luma with
a Lanczos kernel), so the mode has its own pass: FidelityFX CAS (contrast adaptive sharpening, AMD, MIT) on the
five-sample cross, per color channel, in the encoded domain (SDR gamma or HDR PQ code values, never linearised):

```
mn, mx = min / max of centre e and its up b, left d, right f, down h
amp    = sqrt(clamp(min(mn, 1 - mx) / max(mx, 1e-4), 0, 1))   // less where the signal is near black or white
w      = -amp * 0.2 * sqrt(sharpness)                          // sharpness 0..1; 0 = off, 1 = CAS's strongest 1/5
out    = clamp((b + d + f + h) * w + e) / (1 + 4 w)           // the weights sum to 1, flat areas never change
```

CAS's own default peak is 1/6.5; the square root puts the middle of the slider (50 %) at 0.141 instead of the weak
0.1 a linear ramp would give. `SharpenOnly.java` is the CPU reference with unit tests of documented values; the
shaders evaluate the same steps and must be kept in step with it.

| Pipeline | Where | Passes per frame |
| --- | --- | --- |
| OpenGL ES (MediaCodec) | `assets/sgsr/sharpen_only.frag`, drawn by `SgsrRenderer` | the existing OES to 2D copy, then the sharpen pass straight into the window or the P010 layer |
| Vulkan (PyroWave) | `shaders/sgsr_sharpen.glsl` (`contrastAdaptive`) in `sgsr_present.frag` / `sgsr_pack.frag`; mode 5 of `sgsr_spatial.comp` | a YCbCr to RGB compute pass into the existing FP16 image at stream size, then the sharpen present (or P010 pack) pass |

Both are GPU-only and keep FP16 intermediates, the P010/PQ HDR output and the launch arrival pass of the SGSR modes
(the arrival renders the sharpened frame). The Vulkan side supports 4:2:0 and 4:4:4 planes (the chroma planes are
sampled at their own size) and SDR, HDR10 swapchain and the HDR P010 bridge.

**Surface size.** Sharpen only must not enlarge anything, so the cost stays that of the stream's own pixels. SDR
MediaCodec output keeps the stream-sized SurfaceView buffers (`StreamSurfaceController.sizeStreamSurfaceForUpscaler`: a scaling mode
asks for the layout size, Sharpen only and turning the upscaler off for the stream's fixed size) and the HDR child layer is created at the
stream's size, not the display's; SurfaceFlinger scales the result to the view as it does for direct output.
PyroWave presents at the stream's size in this mode (`sgsrPresentationWidth/Height` = stream size, no supersampling
candidate). Because these sizes differ from the scaling modes', switching Mode to or from Sharpen only (or enabling
with a stored Sharpen only) rebuilds the output stage: a MediaCodec decoder reset like the Enabled toggle, and for
PyroWave the existing upscaler recreate at a frame boundary. Switching among the other four modes is as before.

**Measuring the cost.** Both pipelines publish what the mode costs, for the next stream test:

- OpenGL ES: once a second, while the mode runs, one debug line under the log tag `DelosSharpen`
  (`adb logcat -s DelosSharpen`), for example
  `sharpen-only 1920x1080->1920x1080 sharpness=0.5 p010=true hdr=true timer=gpu | pass gpu avg=0.310 max=0.620 ms (n=119) | copy gpu avg=0.180 max=0.400 ms | cpu submit pass avg=0.040 max=0.200 ms copy avg=0.030 max=0.100 ms`.
  GPU time is an `EXT_disjoint_timer_query` time-elapsed query around the sharpen draw and around the OES copy that
  the direct output does not have; if the extension is missing (or a probe query errors) the line says
  `timer=cpu-submit` and only the CPU time of submitting the draws is reported. Disjoint GPU events discard the
  affected samples. The full statistics panel shows the row **Sharpen pass** (`0.31 ms avg / 0.62 max · copy 0.18 ms`,
  with `(CPU submit)` when no GPU timer exists).
- Vulkan: the existing timestamp queries gain a fourth stamp after the reconstruction stage, so the draw time is
  split into the YCbCr to RGB conversion and the sharpen/pack pass. `DelosPyroDiag` (debug builds) ends with
  `sharpen pass gpu avg/max .../... ms, conversion avg/max .../... ms (n=...)` (or `sharpen off`), the native 5-second
  log line `Sharpen only: GPU sharpen pass ... ms, colour conversion ... ms`, and the full panel's **Backlog**
  section gains **Sharpen pass** (`0.50 ms avg / 0.90 max · conversion 0.25 ms`). The existing "GPU draw" number
  already covers the whole draw stage in every mode, so the cost against the direct path is the difference in it.

The numbers are what the pass costs; they are not an input-to-photon measurement. Cost on the Odin at 1080p120,
the visual result, and the shader compile on the Adreno 740 are verified only by a stream test (issue #67).

### Native-resolution supersampling

For all four SGSR modes, a stream already matching the display uses supersampling instead of
same-resolution reconstruction. The live stream menu calls this mode **Supersampling** and offers two
levels, session-only unless saved: `1.33x` (stored value 1; the earlier label 1x is recorded in
[Evidence (2026-11-07): decisions](#evidence-2026-11-07-decisions)) reconstructs 1920x1080 into 2560x1440, while
`2x` reconstructs it into 3840x2160. A separate presentation pass reduces the internal image to the display
resolution.
The HDR path performs RGB-to-P010 conversion only after that reduction; intermediate RGB remains
FP16 and PQ-encoded, not linear-light or eight-bit SDR.

The level a stream starts with is the Display setting **Supersampling quality**
(`list_sgsr_supersampling_level`, 1 or 2, default 1; profile value with a per-app override). An unknown
stored value reads as 1.33x, and a 2x that does not fit falls back to 1.33x exactly as for the live choice.

The sizing policy tolerates a two-pixel Surface aspect-fit difference, checks the GPU texture limit,
and caps the internal target at 3840x2160. Native 4K therefore never attempts an unsafe 8K target.
Changing the level live resets only temporal history and size-dependent SGSR resources, not the
network stream or decoder. PyroWave recreates those Vulkan resources after its current frame fence and
falls back from `2x` to `1x` if the larger allocation fails. The stream menu row shows the level in
effect, which is the request at once and `1x` after such a fallback, so one D-pad press moves it; it
does not wait for the renderer's next frame to apply the request. In HDR the P010 output is rebuilt
with the upscaler: the previous surface control stays on screen with its last picture until the rebuilt
output has shown its first frame, so a level or mode change holds the picture for the rebuild instead
of flashing black.

The full statistics panel reports the actual active target directly below the source resolution, for
example `Supersampled to: 2560 x 1440` or `Upscaled to: 1920 x 1080`. It reads the renderer's applied
dimensions, so a pending change or fallback is not reported as completed. On the Odin's Adreno 740,
the headless SGSR 2 temporal stage measured about 6.85 ms median / 7.39 ms p95 for 1080p-to-1440p and
10.23 ms median / 16.76 ms p95 for 1080p-to-4K. The first level is the default; the second is primarily
suited to 60 FPS streams.

The two-pass SurfaceTexture/2D approach was also reviewed against linckosz's Moonlight Android
PR #1567 (`37e7a00ce8cde55b936c9239d8e717336f55da63`); the lifecycle, live controls and P010 HDR output
here are implemented for Delos rather than copying that bridge's synchronous frame wait.

## Variable refresh rate (VRR / ARR)

Setting: Settings > Display > Variable refresh rate (`list_vrr`: Auto, On, Off; default Auto; also a per-app setting). The row
is hidden below Android 11 (API 30), where `Surface.setFrameRate()` does not exist. Most handhelds, including the AYN Odin 2,
have a fixed-refresh panel; nothing changes for them.

**What Android offers.** `Display.hasArrSupport()` (API 36, Android 16) says whether the display supports adaptive refresh
rate: the panel changes its refresh rate continuously (not in the steps of the display modes) to follow the content.
`Surface.setFrameRate(rate, FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, CHANGE_FRAME_RATE_ALWAYS)` tells the system the frame rate
of the content, which the system uses to pick the refresh rate (on an adaptive panel, one that matches the content).

**What Delos does.** `DisplayVrr.detect` reads `hasArrSupport()` (false below API 36 and when the call throws);
`VrrMode.resolve(setting, reported)` makes VRR active (Auto: as reported; On: always; Off: never; always inactive below API 30).
When VRR is **not** active, `StreamDisplayPlanner` returns exactly the plan it returned before VRR existed (the planner tests
are the regression suite; `vrrInactiveEqualsThePlanBeforeVrr` runs a table). When it is active:

- `Plan.displayRefreshRate` (reported to the host as the client refresh rate, and the number Cap FPS pacing compares with) is
  the **peak** refresh rate of the supported modes at the chosen mode's resolution, not `min(current rate, chosen rate)`: an
  adaptive display's instantaneous rate can be low when the stream starts and rises with the content.
- `Plan.surfaceFrameRate` is the **true stream frame rate** (`Plan.pacedFrameRate`, before the warp factor) instead of the
  refresh rate of the best mode, so the system can match the content without pull-down.
- The display mode choice (`chooseMode`) is unchanged: the mode switch, the frame-rate compatibility and the change strategy are
  the same.

**What it does not do.** No change to the decoder, the pacing modes (Fast V-Sync, Balanced, Cap FPS, ...) or the host stream. The
Balanced pacing's Choreographer still ticks at the rate the system reports.

**Diagnostics.** The log line at stream start: `VRR: setting=auto, reported=true, active=true, surface frame rate=90.0,
planning rate=120.0`. Settings > About > Debug info > Displays: `Adaptive refresh rate: reported yes/no (...)` per display. The
statistics panel (compact line and full panel header) appends `VRR` after the FPS figure only while VRR is active.

**Unverified.** The test device has no VRR display, so this is proven by unit tests (planner, `VrrMode`, detection with a fake that
throws, settings plumbing) and by "nothing changes on a fixed-refresh device", not on real hardware. A measurement on a VRR
device (Android 16, panel reporting ARR) should check: (1) the log line shows `reported=true active=true` in Auto; (2) the
statistics overlay shows the `VRR` marker; (3) the frame delivery intervals (Perfetto frame timeline / `dumpsys SurfaceFlinger
--latency`) of a 90 and a 72 FPS stream are even, with no 3:2 pull-down pattern and no refresh above the stream rate it does not
need, compared with Off on the same stream; (4) a 60 FPS stream on a 120 Hz panel still reaches the panel as intended.

## Gyro smoothing (#77)

**Controls → Gamepad → Gyro smoothing** (`seekbar_gyro_smoothing`, 0..100, default 0) steadies the gyroscope before it is
sent to the host. 0 is off and the sensor path is then unchanged (the filter is never created). It acts on the gyroscope
only, never on the accelerometer, for both the external gamepad's sensors and the built-in device sensors
(`ControllerHandler.createSensorListener`). The stream menu (More → Gyro smoothing, steps of 5) changes it live and keeps it for this app as its per-app override (the profile is not written; the Settings slider sets the profile value and applies to the next stream, because Settings is not open during a stream).

**Live change and threads**: the menu writes the new strength into `PreferenceConfiguration.gyroSmoothing` (`volatile`) and
nothing else (no profile write). Each gyro sensor listener reads that field once per sample (a volatile read, never
`SharedPreferences`) and, when it differs from the filter's, calls `GyroSmoother.setStrength` on the sensor thread, which is the
only thread that ever touches the filter: no lock and no state hand-over. 0 to positive starts a fresh filter, positive to 0 is
an exact bypass (`filter` returns false and the original unfiltered code path sends the sample) and forgets the state, and a
change between positive strengths keeps the state, so there is no jump. Listeners created later (a controller that connects
mid-stream) start from the current value.

**Filter** (`binding/input/GyroSmoother.java`, pure and unit-tested): a One Euro filter per axis, one instance per sensor
listener (so per controller and per report rate; a new report rate starts a fresh filter), no allocation per sample. The
cut-off is `minCutoff + beta * |d|`, with `d` the rate of change of the angular velocity, itself low-passed at a fixed 1 Hz.
At rest and in slow aiming the cut-off stays near `minCutoff` and removes jitter; in a flick `|d|` grows, the cut-off opens
and the lag almost vanishes. It runs on the raw reading converted to deg/s (the unit the host receives, so `beta` is in Hz
per deg/s per second), before the display-rotation axis remapping, so a rotation never mixes the state of two axes. Samples
are timed with the sensor's own timestamps; the same timestamp repeats the last output, a gap above 250 ms or a timestamp
going backwards restarts the filter from the new sample.

**Mapping** (`t = slider / 100`): `minCutoff = 8 Hz * 0.025^t`, `beta = 0.02 * 0.25^t`.

| Slider | minCutoff | beta | Group delay at rest | Noise variance (rest, vs raw) | Lag of a slow 0.5 Hz, 30 deg/s sway | Lag of a 3 Hz, 300 deg/s flick | 0 to 300 deg/s in 100 ms reaches |
|---|---|---|---|---|---|---|---|
| 0 | off | off | 0 | 100 % | 0 | 0 | 300 |
| 1 | 7.71 Hz | 0.0197 | 21 ms | 10.9 % | 15 ms | 5 ms | 292 |
| 10 | 5.53 Hz | 0.0174 | 29 ms | 8.1 % | 15 ms | 5 ms | 291 |
| 25 | 3.18 Hz | 0.0141 | 50 ms | 4.9 % | 20 ms | 5 ms | 289 |
| 50 | 1.26 Hz | 0.0100 | 126 ms | 2.1 % | 30 ms | 10 ms | 286 |
| 75 | 0.50 Hz | 0.0071 | 316 ms | 1.0 % | 35 ms | 10 ms | 283 |
| 100 | 0.20 Hz | 0.0050 | 796 ms | 0.47 % | 45 ms | 10 ms | 278 |

Numbers come from `GyroSmootherTest` (200 Hz samples, white noise of 0.5 deg/s sigma, lag resolution 5 ms): the group delay
at rest is `1 / (2 pi minCutoff)`, which only matters for the first moments after a still hand starts to move; the
latency a player feels is the sway and flick columns. These are filter numbers, not measured input-to-photon latency.
Unverified without a real game on a handheld: how it feels in a game, and whether the host (Steam Input, emulators) already
smooths the data, which would make 0 the right choice.

## Latency (#75)

The mechanisms below are implemented. **Their effect on latency is not measured on the device** (see
[Not verified](#not-verified) and [Evidence (2026-11-07): decisions](#evidence-2026-11-07-decisions)). Everything
marked [INFERENCE] is derived from the source and from Android's standard behavior, not observed on the Odin. The
input-to-photon number can only come from the camera test in
[Latency measurement procedure (not run)](#latency-measurement-procedure-not-run). The in-app stage timestamps never
include the controller, network, host or decoding of the host side.

### Latency budget

Typical values in milliseconds, a handheld at 120 FPS on a 120 Hz panel. (doc) = from the SGSR measurements in this
document; everything else is [INFERENCE].

| Stage | Owner | ms |
|---|---|---|
| Controller poll and send | Android input | 1-4 |
| Host capture and encode | Vibepollo | 2-6 (shown as "PC processing") |
| Host capture and encode, PyroWave | Vibepollo | about 1-2 |
| Wi-Fi transit and jitter | network | 1-4 (round trip / 2) |
| Reassembly and FEC, MediaCodec | moonlight-common-c | below 1 |
| Decode | MediaCodec, low-latency Qualcomm decoder | 2-5 |
| Pacing wait | Latency: about 0; Fast V-Sync / Balanced: 0-8.3 by design | |
| Output stage, MediaCodec direct | codec to SurfaceView | none |
| Output stage, Sharpen only | listener thread hop, `updateTexImage`, OES copy 0.18 (doc) + sharpen 0.30 (doc) | about 0.7-1 |
| Output stage, SGSR 2 | 3.3-3.9 ms at 480p/720p to 1080p (doc, headless); 3.9-4.9 in play (doc) | |
| Output stage, supersampling | spatial about 1.3-2 at 1x (4K target 2.5-4 at 2x); temporal 6.85 at 1x, 10.23 at 2x (doc, headless, over the 8.3 ms frame at 2x) | |
| PyroWave | Vulkan decode about 3 (doc); receive to present 4-9 (doc) | |
| Compositor tail | SurfaceFlinger latch plus scan-out | 8-12 on average, up to one frame more when the latch is missed |

The compositor tail is the one a client cannot shorten by much:

1. A buffer queued at time t is latched at the next SurfaceFlinger latch point and scanned out at the following
   panel refresh: 1 to 1.5 frame periods plus half a scan-out for the screen center.
2. If the stream FPS equals the display rate, the phase between frame-ready and refresh is fixed for stretches of a
   session and drifts slowly (host and panel clocks differ by tens of ppm). A measured latency therefore wanders
   over a window of about one frame period across minutes.
3. Camera runs must be long enough or sample the trigger phase randomly.

### Render-path mechanisms

None of the mechanisms changes the picture, adds a setting or touches moonlight-common-c.

| No. | Mechanism | Where | Expected gain [INFERENCE] |
|---|---|---|---|
| 1 | Newest frame only in the SGSR GL thread | `SgsrRenderer.draw`, `FrameQueue` | About 0 ms while the output keeps up. When the GPU work per frame exceeds the frame interval (SGSR 2 at 1x or 2x at 120 FPS, 4K supersampling) it removes the 1-3 queued frames, 8-25 ms. The depth depends on the codec's buffer count, which was not read. It costs frames per second only in that overloaded state |
| 2 | Fast V-Sync aware of the output stage | `NanoPacer.waitAndDrainLatest`, `SgsrPacing` | 0 to a few ms: it avoids a missed latch for a share of the frames (up to 8.3 ms each) with Fast V-Sync and SGSR. On the SDR path the estimate is CPU submit time and underestimates GPU completion; the cap bounds the damage of a wrong estimate |
| 3 | HDR output without `glFinish` | `sgsr_hdr_output.cpp` | 0.2-0.5 ms (one wake after the GPU is idle), and the next frame's `updateTexImage` and command recording overlap the previous frame's GPU work |
| 4 | GPU work in flight bounded to two frames | `GpuInFlight`, `SgsrRenderer.draw`, `sgsr_frame_timestamps.cpp` | Bounds the 70-140 ms measured with SGSR 2 at 4K to about 20 ms |
| 5 | Honest GPU time | `GpuInFlight`, `SgsrRenderer` | None; the numbers mean what they say |
| 6 | Overload notice and load governor | `OverloadDetector`, `FlowGovernor`, `SgsrTemporal.render` | Under load: about 10-15 % less GPU time for SGSR 2. [INFERENCE] |
| 7 | GL thread priority and EGL context priority | `SgsrRenderer` | None |
| 8 | PyroWave present observability | `pyrowave_renderer.cpp` | None by itself; it shows whether MAILBOX is used and whether the compositor rotates |
| 9 | Stage timestamps | `LatencyStages`, `SgsrRenderer`, `sgsr_frame_timestamps.cpp` | None; measurement only |

#### 1. Newest frame only in the SGSR GL thread

1. The frame-available callback runs on its own thread and counts queued frames (`FrameQueue`).
2. A draw consumes every waiting frame with one `updateTexImage()` each and draws only the newest, so a stale frame
   is never shown while a newer one waits.
3. SGSR 2 sees the newest frame's timestamp, so one skipped frame does not reset its history. The history resets
   only when the gap exceeds its 50 ms limit, about 6 frames at 120 FPS.
4. The callback has to count on its own thread: on the GL thread the callbacks of frames queued behind a long draw
   are not delivered until that draw is over.

#### 2. Fast V-Sync aware of the output stage

1. The pacer releases a frame earlier by the smoothed frame-available-to-swap time of the SGSR GL thread plus
   0.5 ms, capped at half a frame period and never before now.
2. The phase bookkeeping keeps the display target (`SgsrPacing`, `NanoPacer`).
3. Latency mode is untouched.
4. Balanced is not adjusted: its release time is the Choreographer callback, and the SGSR consumer ignores the
   release timestamp, so there is nothing to move.

#### 3. HDR output without `glFinish`

1. The P010 buffer goes to the SurfaceControl transaction with an `EGL_ANDROID_native_fence_sync` fence, so the GL
   thread does not wait for the GPU.
2. Reuse stays protected by the transaction's release fences.
3. Without the extension the code falls back to `glFinish`.
4. Every fence descriptor is owned by exactly one place:

| Descriptor | Owner |
|---|---|
| The one passed to `setBuffer` | The transaction takes it |
| A `dup` per frame | The output keeps it and closes it when it signals, when its slot is reused or at teardown |
| The present fence of the transaction statistics | Closed the same way |

#### 4. GPU work in flight bounded to two frames

The device finding behind this bound is in
[Evidence (2026-11-07): GPU saturation by the temporal modes](#evidence-2026-11-07-gpu-saturation-by-the-temporal-modes).

1. Every frame the GL thread submits gets a native fence.

| Path | Fence |
|---|---|
| SDR | `eglCreateSyncKHR` with `EGL_SYNC_NATIVE_FENCE_ANDROID` plus a flush, polled with a zero timeout |
| HDR | The fence of the P010 hand-over |

2. With two frames still running, the draw is not queued. The newest frame stays in the texture and is drawn as soon
   as the GPU is free (retried every millisecond) or replaced by a newer one.
3. Latency therefore stays at about one to two GPU frames however slow the GPU is, at the cost of frames per second.
4. The bound is two, not one, because the CPU needs 4-6 ms per frame to record while the GPU needs about 10: with
   one the GPU would idle during every recording.
5. A fence that has not signalled after 250 ms is given up so a hung GPU cannot freeze the picture.
6. Frames dropped this way are the `GPU busy /s` figure of the Frame queue row.
7. Without native fences the GPU is waited for after every frame.
8. SGSR 2 history is unaffected: a dropped frame never reaches it.

#### 5. Honest GPU time

1. The Upscaling figure and the Fast V-Sync input are the GPU execution time of a frame from its fences: from the
   later of its submission and the end of the previous frame to its signal time. Queueing behind the previous frame
   is not counted.
2. The figure is not CPU submit time and not a backlog-dominated wall time.
3. Until the first fence is read, or without fences, it is the CPU submit time.

#### 6. Overload notice and load governor

| Part | Condition | Action |
|---|---|---|
| Overload notice | Two consecutive seconds with the GPU time above 90 % of the frame period, or with more than 20 % of the frames dropped for a busy GPU | One toast per stream and mode says "Upscaler is too heavy for this frame rate: lower the supersampling or the frame rate" (logged under `DelosSharpen`). No setting is changed |
| Load governor, on | In the SGSR 2 modes, while the GPU time is above 70 % of the frame period | Every second frame reuses the previous frame's optical flow instead of recomputing it. This saves the flow passes (a quarter to a third of the temporal cost) on half the frames |
| Load governor, off | The GPU time is below 45 % of the frame period | The flow is recomputed every frame |

With the governor on, only changing motion can look slightly less sharp, and only under load.

#### 7. GL thread priority and EGL context priority

1. The GL thread stays at `THREAD_PRIORITY_DISPLAY`.
2. `EGL_CONTEXT_PRIORITY_HIGH_IMG` is not used for the stream. Do not use it.
3. Reason: a high GPU priority lets the SGSR context pre-empt the compositor's and the system UI's GPU work, and
   with the GPU saturated the whole system stuttered. The GL thread at urgent display priority and a high-priority
   EGL context were tried and **reverted**; see
   [Evidence (2026-11-07): GPU saturation by the temporal modes](#evidence-2026-11-07-gpu-saturation-by-the-temporal-modes).

#### 8. PyroWave present observability

Logging only: the present modes the surface offers and the chosen one, the swapchain image count and the
pre-transform. They are logged once when the swapchain is created and in the `DelosPyroDiag` line.

#### 9. Stage timestamps

The stages and the log lines are listed in
[Latency measurement procedure (not run)](#latency-measurement-procedure-not-run), step 2.

### Temporal modes at a 4K target

**A temporal mode at a 4K target (2x supersampling) needs about 10 ms of GPU time per frame, so on the Odin it can
only hold 60-90 FPS; use 1x, or a 60 FPS stream, for SGSR2-DV at 4K.** The in-flight bound cannot create GPU time:
a saturated GPU stays saturated, and the dropped frames are the price. The measurement and the memory-traffic
estimate are in
[Evidence (2026-11-07): GPU saturation by the temporal modes](#evidence-2026-11-07-gpu-saturation-by-the-temporal-modes).

### Candidates not implemented

| Candidate | Reason |
|---|---|
| The reactive-mask texture as a single channel | OpenGL ES 3.1 allows only `rgba16f`, `r32f`, `rgba8` and a few integer formats for image stores. `r16f` and `r8` need an extension, `r32f` is not filterable without `OES_texture_float_linear`, and `rgba8` would quantise the mask and so change the picture. The gain is about 0.1 ms in the Quality mode only |
| The OES-copy fusion | No reason recorded |
| Cheaper SGSR 2 flow and a 2x cap | They change the picture (issue #74) |
| Front-buffer rendering | Risk; only after these mechanisms are measured |
| A different history format or a single-channel mask, to halve the memory traffic of the temporal path | An 8-bit history changes the picture and `R11G11B10F` has no alpha. It is left for a decision with a before/after picture comparison |

ADPF hint sessions are implemented: see
[Performance hint session (ADPF, Android 12+)](#performance-hint-session-adpf-android-12).

## Code map

| Path | Role |
|---|---|
| `app/src/main/java/com/limelight/shell/link/` | Link test: `LinkProbeClient`, `LinkPolicy`, `LinkSearch`, `LinkRecommendation`, `WireBudget`; the passive hint: `LinkMonitor` |
| `app/src/main/java/com/limelight/shell/screens/LinkGuidanceRows.kt` | The Test link and recommendation rows |
| `app/src/main/java/com/limelight/nvstream/http/PyroWaveHostInfo.java` | Parses the PyroWave `/serverinfo` fields into `ComputerDetails.pyroWave` |
| `app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java` | MediaCodec decoding, pacing, asynchronous mode |
| `app/src/main/java/com/limelight/binding/video/NanoPacer.java`, `SgsrPacing.java` | Fast V-Sync; the earlier release for the SGSR output stage |
| `app/src/main/java/com/limelight/binding/video/SgsrRenderer.java`, `SgsrTemporal.java`, `SgsrDimensions.java` | The GLES upscalers and their sizes |
| `app/src/main/java/com/limelight/binding/video/FrameQueue.java`, `GpuInFlight.java` | The count of queued frames; the bound of GPU frames in flight and the GPU execution time |
| `app/src/main/java/com/limelight/binding/video/OverloadDetector.java`, `FlowGovernor.java` | The overload notice; the load governor of the SGSR 2 modes |
| `app/src/main/java/com/limelight/binding/video/LatencyStages.java` | Stage timestamps |
| `app/src/main/java/com/limelight/binding/video/LatencyThreads.java` | Thread registration, big-core placement, the performance hint session |
| `app/src/main/java/com/limelight/binding/video/SharpenOnly.java` | The CPU reference of Sharpen only |
| `app/src/main/java/com/limelight/preferences/UpscalerMode.java`, `PyroWaveBitrate.java` | Upscaler modes; the PyroWave bitrate recommendation |
| `app/src/main/java/com/limelight/binding/video/VrrMode.java`, `DisplayVrr.java` | Variable refresh rate |
| `app/src/main/java/com/limelight/binding/input/GyroSmoother.java` | Gyro smoothing |
| `app/src/main/jni/moonlight-core/latency_threads.c`, `callbacks.c` | Thread priorities; the stream configuration, including the encryption request |
| `app/src/main/jni/pyrowave-renderer/` | The PyroWave Vulkan renderer and its upscalers; `sgsr_hdr_output.cpp` (the HDR P010 output and its fences), `sgsr_frame_timestamps.cpp` (frame fences and stage timestamps), `pyrowave_renderer.cpp` (present logging) |

## Verification

Unit tests cover applied bitrate parsing and XML failures, Nano Pacer lock/reset/rate handling and
MediaCodec flush/restart generation ordering. Runtime bitrate is host/encoder-dependent; an HTTP
acknowledgement alone is not proof that every codec's measured traffic changes as requested.

A device check of this area: start HEVC at 720p with HDR, Fast V-Sync and async decoding, change the runtime
bitrate, and toggle SGSR/sharpness live. Check image orientation, HDR color/luminance, stable
frames, connection continuity and logs confirming the GPU/P010 path rather than fallback. Repeat with
SDR, the other pacing modes, async off, repeated toggles and stream reconnect/teardown.

### Thread scheduling device procedure

The procedure needs the device. Compare a session with and without the scheduling on the same stream.

| Tool | What to look at |
|---|---|
| Perfetto | `sched` and CPU frequency counters: the receive thread on a gold or prime core, no little-core residency, no long runnable gaps. GPU frequency counters: the hint is expected to keep clocks from dropping between frames |
| `DelosPyroDiag` per second | Stall max, frame age at submit and receive-to-present p95/p99 are expected to fall. The mean may not move: the expected gain is in the tail. The `DelosLatency` lines of the render-side work in #75 give the same view for the MediaCodec path |
| logcat | The `DelosThreads` lines (`raised to nice -8: ...`) and the `Delos latency threads:` lines show what was applied |

### SGSR 1 gain and Sharpness device procedure (not run)

No build checks or device acceptance are claimed for the fixed gain and the Sharpness mapping
([SGSR 1 reconstruction gain and Sharpness](#sgsr-1-reconstruction-gain-and-sharpness)). The procedure:

1. Compare the same scene on a build before the fixed gain (the gains are in
   [Evidence (2026-11-07): SGSR 1 reconstruction gain and Sharpness](#evidence-2026-11-07-sgsr-1-reconstruction-gain-and-sharpness))
   and on the current build, at 50% in Standard/HQ for each renderer.
2. Include HDR, live switching and the launch arrival effect.
3. Compare 0/50/100% and the sustained processing time.

### Latency measurement procedure (not run)

The procedure was not run: no input-to-photon number and no before/after comparison exists
([Evidence (2026-11-07): decisions](#evidence-2026-11-07-decisions)). It stays valid for whoever measures it.

1. **Camera (the only input-to-photon number).**
   1. Point a phone with 240 FPS slow motion at the handheld. At 240 FPS one camera frame is 4.2 ms.
   2. Delos has no built-in "latency flash" action. Press a controller button that makes a white flash on the host
      (a trivial full-screen window that turns white on a key, for example).
   3. Count camera frames from the physical press, or an LED on the controller, to the screen turning white.
   4. Do 30-50 presses at random intervals (so different refresh phases are sampled) and report median and p90.
   5. Measure once without the stream (the flash on the client's own screen) to know the camera and display floor.
   6. Do each configuration in the same session.
2. **In-app stages (no pixel reads).** Debug build, statistics overlay on.
   `adb logcat -s DelosLatency DelosSharpen DelosPyroDiag`:
   - `DelosLatency` once a second: `decode`, `pacing`, `avail->draw`, `draw`, `avail->gpu`, `avail->latch`,
     `avail->present`, each as avg/p95/max in ms with the sample count.

     | Stage or path | Meaning or source |
     |---|---|
     | `decode` | Codec submit to codec output |
     | `pacing` | Codec output to release |
     | The `avail->` stages | Count from the SGSR frame-available callback |
     | `avail->present` | The "Render latency" row |
     | SDR | GPU, latch and present come from `eglGetFrameTimestampsANDROID` a few frames after the swap |
     | HDR | GPU from the fence signal time, latch and present from the SurfaceControl transaction statistics |
     | A stage the platform does not give | Has no entry |
     | Direct MediaCodec | Has `decode` and `pacing` only |

   - `DelosSharpen` once a second:

     | Field | Meaning |
     |---|---|
     | `queue pending avg/max` | - |
     | `age avg/max` | - |
     | `gpu avg/max` | GPU execution time per frame from the frame fences |
     | `in=` | Frames per second from the decoder |
     | `drawn` | - |
     | `skipped` | Frames skipped because a newer one was waiting |
     | `gpuBusySkipped` | Frames that waited for a busy GPU and were replaced |

     Pending above 1 on average, or skipped above 0, means the CPU side cannot keep up. `gpu avg` above the frame
     period (8.3 ms at 120 FPS) or `gpuBusySkipped` above 0 means the GPU cannot. Two lines under the same tag say
     when the overload governor acted (`overload: ...`, `SGSR 2 under load: ...`).
   - `DelosPyroDiag` (PyroWave): the existing numbers plus `present=` (MAILBOX or FIFO), `images=` and
     `preTransform=`.
3. **No code needed.**
   - `adb shell dumpsys SurfaceFlinger --latency "<layer>"` for per-frame desired and actual present times. Layer
     names come from `dumpsys SurfaceFlinger --list`; the HDR child layer is `Delos SGSR HDR`.
   - A Perfetto trace (`sched freq gfx view` categories, with the frame timeline) for GPU and CPU frequencies and
     missed refreshes.
   - `dumpsys SurfaceFlinger` for the composition type of the stream layer (device/overlay or client/GPU).

Verify each mechanism with the same measurement with and without it, one configuration per row, no change of the
stream between runs. Sub-millisecond changes cannot be seen by the camera; judge them with
`DelosLatency`/`DelosSharpen` and the p95. The in-app stage timestamps (`DelosLatency`, the "Frame queue" and
"Render latency" statistics rows) remain available for spot checks.

### Device checks (date not recorded)

With the host document's numbers as the pass rules of the link test
([Where Delos follows the reference client, not the host document](#where-delos-follows-the-reference-client-not-the-host-document)),
a clean 500+ Mbps Wi-Fi 7 link on the Odin ended with "too unsteady" (the message for a search whose last probe
failed on delay). The most likely cause [INFERENCE, no log was captured] is that Android's read-time arrival stamps
push p99 over 4 ms (and 0.1 % loss is tight for a phone's receive path), so no step passed.

### Not verified

- The effect of the latency work, and any input-to-photon latency: not measured
  ([Latency (#75)](#latency-75), [Evidence (2026-11-07): decisions](#evidence-2026-11-07-decisions)).
- The link test beyond one network: no variance between runs, no second network, no comparison of the
  recommendation with a real stream. The thresholds of the passive hint are not calibrated.
- The constants of the link guidance for ordinary codecs: judgment, not measurement.
- SGSR 1 fixed gain and the Sharpness mapping: no build checks or device acceptance; extra GLES storage, bandwidth
  and GPU cost are not measured.
- Memory traffic of the temporal modes: not measured.
- Variable refresh rate on a display that reports adaptive refresh: no such device was available.
- Gyro smoothing in a game: the numbers are filter numbers from `GyroSmootherTest`.
- Sustained performance and heat in long sessions, for every upscaler mode.
- Any host other than Vibepollo on Windows.

## Related

- [SGSR2_VIDEO.md](SGSR2_VIDEO.md), [PER_APP_SETTINGS.md](PER_APP_SETTINGS.md), [SETTINGS.md](SETTINGS.md), [GUI.md](GUI.md), [SECURITY.md](../SECURITY.md).
- Issues [#18](https://github.com/DELOS-STREAM/DELOS-android/issues/18) (link guidance), [#19](https://github.com/DELOS-STREAM/DELOS-android/issues/19) (PyroWave quality and performance), [#67](https://github.com/DELOS-STREAM/DELOS-android/issues/67) (Sharpen only),
  [#74](https://github.com/DELOS-STREAM/DELOS-android/issues/74) (SGSR reference check), [#75](https://github.com/DELOS-STREAM/DELOS-android/issues/75) (latency), [#77](https://github.com/DELOS-STREAM/DELOS-android/issues/77) (gyro smoothing), [#24](https://github.com/DELOS-STREAM/DELOS-android/issues/24) (variable refresh rate).

## Evidence (2026-10-26): first device feedback

AYN Odin 2 Portal Max, Vibepollo on Windows. The owner confirmed that Fast V-Sync, asynchronous decoding and
runtime bitrate changes work. SGSR's first
HDR test exposed a vertically inverted image and an enlarged/cropped viewport. The correction keeps the HDR
parent SurfaceView at its original stream buffer size,
maps the display-sized P010 child into that parent's coordinate space, and explicitly flips the raw
GL framebuffer vertically at presentation. Unlike an EGL window swap, raw SurfaceControl buffer
submission does not supply the GL-to-Android orientation conversion.

After the correction the owner confirmed that the HDR colors are good (judged by eye, not measured) and
reported little visual change
at 480p. Device logs show actual SGSR output from 854x480 to 1920x1079 (the SurfaceView's aspect-fit
size), with sharpness 100%; scaling is directly to the view, not a fixed 2x stage. The High Quality
variant was then added for comparison.

The upscaler comparison used 480p HDR as a deliberately difficult source. The owner preferred
`SGSR2-DV (Quality)` over the other three modes while asking to retain all four choices. The
improvement was modest and static source aliasing remains visible. A 1080p-to-4K comparison
would be a more representative upscale ratio; it was not done.

The High Quality build passed assembly, the unit tests of that day (62) and lint. Later device logs confirm
`SGSR quality: High Quality` actually rendered on the GPU/P010 path. This confirms shader
compilation/selection; no visual or sustained-performance acceptance of that mode is recorded.

## Evidence (2026-10-27): PyroWave SGSR 2 and AV1 recovery

PyroWave's Vulkan renderer runs its video-specific SGSR 2 reconstruction for SDR and HDR/P010.
The first full-resolution version added about 6.5-6.8 ms of GPU draw work and was visibly too latent.
Combining neighborhood reconstruction and YCoCg clipping removed the duplicate nine-sample pass;
the Odin then measured mostly 3.9-4.9 ms of draw work and sustained about 118-119 fps in the tested
720p-to-1920x1080 HDR stream. The owner confirmed substantially better latency (by feel, not measured),
correct HDR color and working live On/Off. The native status also keeps the menu toggle synchronized.

Adding post-processing timing initially exposed an unrelated lifetime hazard in asynchronous
MediaCodec input. A decoder recovery could invalidate a retained direct input buffer and the next
`put()` crashed inside `GetByteArrayRegion`/`memcpy`. Async mode now fetches the callback-owned buffer
for each frame instead of retaining it across a possible flush/reset. GL timer queries that triggered
the recovery were removed in favor of safe wall timing. On the corrected build, the owner started
AV1 with SGSR twice; both sessions kept the upscaler available and neither crashed. Device logs also
confirmed MediaCodec output at 1920x1080 for a 1280x720 source.

## Evidence (2026-10-28): PyroWave upscaler parity

Implemented: genuine SGSR 1 (Normal)/HQ, distinct temporal Quality activation/reconstruction,
presentation sharpening and matching saved/live controls/help. The native mode replaces the old
temporal-enabled boolean; requested settings remain separate from the actually applied mode.
Shaders are generated with `jni/pyrowave-renderer/shaders/build.sh`; no runtime shader compiler
or network dependency is introduced. GPU draw timestamps include all new post-decoding passes.

The ARM64/ARMv7 debug build, the unit tests of that day (80) and lint (0 errors) passed.
`PyroWaveGpuInstrumentation` passed real Vulkan shader readbacks on the Odin Adreno 740:
spatial constants/edges, bidirectional motion, Quality activation and distinct reconstruction,
history resets/scene changes, ten-bit BT.2020 preservation, zero/nonzero presentation sharpening
and HDR P010 packing. A second probe created the actual renderer, allocated/dispatched each mode
at both supersampling levels, checked applied state/Off teardown and rebuilt SDR → HDR → SDR.
No game, emulator or streaming connection was used.

These checks establish implementation and GPU correctness, not visual acceptance or sustained
game performance. The 118–119 FPS result above is specifically the first SGSR2-DV path,
not a benchmark for the HQ/Quality modes; no later acceptance of those modes is recorded.

## Evidence (2026-11-07): SGSR 1 reconstruction gain and Sharpness

Before the fixed reconstruction gain of 2.0, GLES used `0.5 + 1.5*s` (`1.25` at the default 50%), while native
used `0.5` plus a separate presentation sharpener. The historical audit is preserved in
[SGSR2_VIDEO.md](SGSR2_VIDEO.md#evidence-2026-11-07-sgsr-1-fixed-gain-follow-up). From this follow-up on, Sharpness
controls only the final bounded cross filter; SGSR2-DV/Video Quality and Sharpen only's CAS mapping are unchanged
by it. No build checks or device acceptance are claimed for this follow-up. The comparison to run is in
[SGSR 1 gain and Sharpness device procedure (not run)](#sgsr-1-gain-and-sharpness-device-procedure-not-run): the
same scene on main and this change.

## Evidence (2026-11-07): link test on the Odin

Not measured: no recorded run. The table that was meant to hold the results (three runs per place and band, with
a stream check at the recommended bitrate) was never filled, and issue
[#18](https://github.com/DELOS-STREAM/DELOS-android/issues/18) was closed without it. What is recorded: the test was used on
the Odin on one Wi-Fi 7 network on 2026-11-07 and reported about 700 Mbps (closing note of the issue), after the
pass rules had been changed as described under
[Where Delos follows the reference client, not the host document](#where-delos-follows-the-reference-client-not-the-host-document).
No variance between runs, no second network and no comparison of the recommendation with a real stream were
recorded. The constants for ordinary codecs and the thresholds of the passive hint are therefore judgment, not
calibration.

## Evidence (2026-11-07): GPU saturation by the temporal modes

Device finding, measured on the Odin with the first version of the latency work ([Latency (#75)](#latency-75)),
SGSR2-DV (Normal) at 2x supersampling (a 4K target), 120 FPS. The whole app and the system stuttered.

| Source | Reading |
|---|---|
| Upscaling figure | Peaked above 150 ms |
| `DelosSharpen` | `queue pending avg=1.0 max=1, skipped=0` (the SurfaceTexture queue was not the problem) |
| `DelosSharpen`, `draw` | 4-6 ms on average (maximum 41-50) |
| `DelosLatency`, `avail->gpu` | 60-130 ms |
| `DelosLatency`, `avail->latch` | 20-60 ms |
| `DelosLatency`, `avail->present` | 70-140 ms (maximum 290) |

Reading: the CPU submitted much faster than the Adreno 740 could execute (SGSR2-DV at 4K costs about 10 ms of GPU
time headless against an 8.3 ms frame), so 8-15 frames of work piled up inside the driver's command queue and the
swap chain, where neither the SurfaceTexture coalescing nor the pacer could see them. With the GPU saturated, the
compositor's and the system UI's GPU work waited behind it, which is the system-wide stutter [INFERENCE: the
stutter itself was not profiled]. A high EGL context priority made this worse by design and is gone: the GL thread
at urgent display priority and a high-priority EGL context were tried and reverted.

Consequences, in the code: the GPU frames in flight are bounded, the Upscaling figure is real GPU time, and the
user is told when the upscaler is too heavy
([Render-path mechanisms](#render-path-mechanisms), mechanisms 4, 5 and 6). A temporal mode at a 4K target (2x
supersampling) needs about 10 ms of GPU time per frame, so on the Odin it can only hold 60-90 FPS. What the bound
cannot do is create GPU time: a saturated GPU stays saturated, and the dropped frames are the price.

Memory traffic is the other candidate for a system-wide slowdown that a longer GPU queue does not explain (the GUI
was also slow with SGSR2-DV (Quality) at 1x). The estimate is [INFERENCE from the texture formats in
`SgsrTemporal`, not measured]:

| Per frame, temporal path | Size |
|---|---|
| Writes the RGBA16F history at the output size (8 bytes per pixel) | 71 MB at 4K, 16 MB at 1080p |
| Reads the previous history, and reads it again in the present pass | the same size each |
| The stream-sized RGBA16F reactive mask in Quality mode | plus 16 MB |
| Total at 4K | about 210 MB per frame, 25 GB/s at 120 FPS |
| Total at 1080p with Quality | about 50 MB (6 GB/s) |

The traffic is on a memory bus the CPU, display controller and decoder share. Halving it needs a different history
format (an 8-bit history changes the picture; `R11G11B10F` has no alpha) or a single-channel mask, so it is left
for a decision with a before/after picture comparison. The in-flight bound also keeps the GPU from running at
100 % when frames are being dropped anyway, but it does not leave headroom on its own; the load governor lowers
the flow work from 70 % of the frame period.

## Evidence (2026-11-07): decisions

| Subject | Record |
|---|---|
| Stream thread scheduling and performance hints | Decided 2026-11-07: always on while a stream runs, with no setting |
| Supersampling level label | The first level (stored value 1) was labeled 1x until 2026-11-07; its label is `1.33x` |
| Latency results (#75) | Not measured: no recorded run. Issue [#75](https://github.com/DELOS-STREAM/DELOS-android/issues/75) was closed on 2026-11-07 with the optimizations and the in-app instrumentation done and with the decision not to do the camera measurement. No input-to-photon number and no before/after comparison of any change of the latency work exists |
