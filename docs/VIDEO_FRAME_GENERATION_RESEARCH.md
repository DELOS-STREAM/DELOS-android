# Video frame generation for DELOS (Android)

## Purpose

This is a research and design basis for generating extra frames from decoded stream video on Android, so that a
60 fps stream fills a 120 Hz display (and 30 fps fills 60 Hz). It applies to every DELOS stream source.

- Phase 1 is implemented: `binding/video/FrameGenerator.java` (with
  `jni/pyrowave-renderer/sgsr_frame_generation.cpp`), run by `SgsrRenderer`. Setting `list_frame_generation` (off, 2×, 3×,
  4×; SDR and HDR) and, live for one stream, More → Frame generation in the stream menu. With the upscaler off the SGSR
  stage runs as a sharpen-only pass-through at strength 0.
  - **Pace:** the game's. A decoded frame that repeats the previous picture (64 × 36 grid of differences, at most 0.5 % of
    cells changed), or refines it right after a moving picture (under 35 % of that picture's changed cells), is not shown
    as a new picture; one with changed details replaces the newest picture and is shown when no step takes its refresh.
    The plan uses the smoothed game interval.
  - **Prediction:** the driver's extrapolation or an own warp, whichever predicted the game's next picture better (every
    other moving picture, error relative to holding). The warp moves the newest picture along `GL_QCOM_motion_estimation`
    vectors (half-size luma, 8 × 8 blocks), sampled at the destination pixel.
  - **Display refresh:** measured from consecutive frame callbacks (the rate read when the stage is rebuilt can be a passing
    one during a rate switch).
  - **Presentation (HDR):** both latencies are measured from the present fences (picture about 7.5 ms from its first
    callback, generated image about 16 ms from its callback on the Odin); steps are presented on the callback that shows
    them on their refresh, and a picture that would show a refresh early waits for its slot.
- Observed on the Odin (2026-11-22, PS5, GTA at 30 fps in a 60 fps HDR stream, 120 Hz), by the owner: smoother than
  without generation, reads as 60 most of the time with motion blur off; fast camera turns show warping.
- Tried and reverted (2026-11-22): a warp with motion at half and quarter size, a 3 × 3 median, a fitted affine camera
  model, per-pixel choice among the candidates and damping on camera acceleration. On the Odin its fields read back almost
  no motion (longest vector 3 px while the game moved), it lost the comparison to the driver, and the owner saw pixel
  noise over the moving picture and less smoothness than the simpler warp.
- Interpolation (one game frame of added latency) is not planned now (owner, 2026-11-22: +33 ms at 30 fps is too much);
  at most later as an experimental option.
- The reference device is the AYN Odin 2 Portal Max (QCS8550, Adreno 740, Android 13, 1920 × 1080 at 120 Hz).
- Facts carry a source; everything else is marked **[INFERENCE]** or **[UNVERIFIED]**.

Related:
- [VIDEO_OPTICAL_FLOW_RESEARCH.md](VIDEO_OPTICAL_FLOW_RESEARCH.md);
- [OPTICAL_FLOW_PYRAMIDS_2026-11-18.md](OPTICAL_FLOW_PYRAMIDS_2026-11-18.md);
- [SGSR2_VIDEO.md](SGSR2_VIDEO.md);
- [STREAM_QUALITY.md](STREAM_QUALITY.md).

## Findings

1. **The Odin's GLES driver exposes two Qualcomm extensions that fit this exactly:**
   - `GL_QCOM_frame_extrapolation` (`glExtrapolateTex2DQCOM`): a driver-implemented frame extrapolator, the "Adreno Frame Motion Engine".
   - `GL_QCOM_motion_estimation` (`glTexEstimateMotionQCOM`): block motion estimation on an R8 luma texture or directly on a YUV EGLImage.
   - Evidence: a read-only `adb shell dumpsys SurfaceFlinger` on the Odin, 2026-11-21. Driver `V@0676.53`. Both extension strings are listed.
   - Specifications: [QCOM_frame_extrapolation](https://registry.khronos.org/OpenGL/extensions/QCOM/QCOM_frame_extrapolation.txt), [QCOM_motion_estimation](https://registry.khronos.org/OpenGL/extensions/QCOM/QCOM_motion_estimation.txt).
   - The listing is SurfaceFlinger's context; the app's own EGL context must be queried too [UNVERIFIED].
2. **Interpolation versus extrapolation is a latency choice.**
   - Interpolating between decoded frames N and N+1 cannot start before N+1 has been decoded. It adds about one source frame interval of display delay: about 16.7 ms at 60 fps.
   - Extrapolating from N−1 and N adds no waiting, only compute. It guesses: it overshoots at motion changes and cannot reveal disoccluded areas.
   - DELOS optimises for latency, so the extrapolation mode is the natural default for games; interpolation is an opt-in "smoothness" mode.
3. **No motion vectors come from the decoder.** Android `MediaCodec` exposes none. Parsing them from H.264, HEVC or AV1 needs full entropy decoding (CABAC), close to a software decoder. That is not cheap on the CPU and not worth it [INFERENCE].
   - PyroWave is a wavelet codec without motion vectors.
   - Motion must be estimated from pixels: with the QCOM extension, the existing SGSR2-DV flow, or the host.
4. **The existing SGSR2-DV pipeline already does most of the work.** It already holds:
   - current and previous luma pyramids;
   - bidirectional pyramidal Lucas–Kanade flow on an 8-pixel grid with confidence;
   - scene-cut detection;
   - FP16 RGB targets on both the GLES path (`SgsrTemporal`) and the Vulkan path (PyroWave).

   A warp pass can reuse that flow [source: SGSR2_VIDEO.md, Pipeline].
5. **No permissively licensed, ready-made video frame interpolator fits a phone GPU budget.**
   - FSR 3 frame interpolation (MIT) needs engine depth and motion vectors and SM 6.2 / Vulkan GLSL 4.50 features.
   - Neural interpolators (RIFE, MIT) cost far more than the budget on mobile [UNVERIFIED on the Odin].
   - The practical options are the Qualcomm driver extension or our own flow-based warp.

## Approaches

| Approach | Added latency | Quality risks | Cost on the Odin | Fit |
|---|---|---|---|---|
| A. Driver extrapolation `glExtrapolateTex2DQCOM(prev, cur, out, 0.5)` | none beyond compute | Overshoot at motion changes, HUD/text smear, disocclusion holes; quality not defined by the spec | Unknown; the driver uses its motion engine [UNVERIFIED] | Fastest to build; GLES path only |
| B. Own extrapolation: flow (QCOM motion estimation or SGSR2-DV flow) + forward warp by +0.5 | none beyond compute | As A, but tunable: confidence gating, HUD handling, fallback to repeat | flow 0.5–1 ms per direction at 720p (measured fixture) + warp ~1 ms at 1080p [INFERENCE] | Good; reuses SGSR2-DV |
| C. Own interpolation: bidirectional flow + backward warp from both frames at t = 0.5 + occlusion blend | +1 source frame (16.7 ms at 60 fps) | Fewest artifacts; still struggles with transparency, particles, compressed noise | about 2× flow + 1–2 ms blend [INFERENCE] | Opt-in "smooth" mode |
| D. Neural interpolation (RIFE family, MIT) | +1 frame + inference | Best visual quality offline | Tens of ms per 1080p frame on mobile GPUs reported by ports [UNVERIFIED]; NPU round trip as in the upscaler research | Not within budget at 120 Hz |
| E. Host-side generation (Vibepollo encodes 120 fps from 60) | network only | Host GPU does the work; doubles bitrate | Not a client feature | Out of scope; note for Vibepollo |
| F. Blend / frame repeat | none | Ghosting (blend) or judder (repeat) | negligible | Fallback only |

**Detail per approach:**

- **Bidirectional versus forward flow:**
  - Interpolation (C) needs flow N→N+1 and N+1→N. SGSR2-DV already computes both directions; that is used for the consistency check.
  - Extrapolation (B) needs flow N−1→N: the "backward" flow of the previous pair, inverted, or forward flow directly. Warp the current frame forward by +0.5 × flow.
  - Forward warping needs splatting (scatter) or an inverse-field approximation. On GLES 3.1 the cheap form is to sample the current frame at `p − 0.5·v(p)`, with v taken from a dilated flow field [INFERENCE].
- **HUD and text:**
  - Static overlays are the visible failure mode.
  - Mitigations:
    - per-block zero-motion snapping where the flow residual against the static assumption is small;
    - confidence-weighted blend back to the unwarped current frame (repeat) for low-confidence blocks.
  - DELOS cannot get a HUD-less buffer (FSR 3 can).
- **Scene cuts and pauses:**
  - Reuse `scene_change.comp`. On a cut, an out-of-order timestamp or a gap over 50 ms, output a repeat of the current frame instead of a generated one.
- **Compression artifacts:**
  - Low-bitrate block noise yields noisy flow.
  - Smooth the flow (pyramid), and require a minimum structure-tensor confidence; low confidence falls back to a repeat [INFERENCE].
- **Variable host frame rate:**
  - Generate only when the decoded cadence is steadily about half the display rate.
  - Otherwise present decoded frames as today. The phase-lock statistics already measure the cadence.

## Hardware and OS features

| Feature | State on the Odin | Use |
|---|---|---|
| `GL_QCOM_frame_extrapolation` | Listed (2026-11-21) | Approach A. Formats RGBA8, RGB8, R8, RGBA16F, RGB16F, RGBA32F, RGB32F; all three textures identical size and format; `scaleFactor` 0.5 for alternating real and extrapolated frames (Qualcomm's [AFME sample](https://github.com/SnapdragonGameStudios/adreno-gpu-opengl-es-code-sample-framework/tree/main/samples/amfe_power_saving), BSD-3-Clause repository) |
| `GL_QCOM_motion_estimation` | Listed | Block motion vectors (RGBA16F, one per block; the block size comes from `GL_MOTION_ESTIMATION_SEARCH_BLOCK_X/Y_QCOM`). Accepts the decoder's YUV EGLImage directly, so no luma copy is needed. Input dimensions must be multiples of the block size |
| `VK_QCOM_image_processing` (`textureBlockMatch*`) | Present (earlier probe, [VIDEO_OPTICAL_FLOW_RESEARCH.md](VIDEO_OPTICAL_FLOW_RESEARCH.md)) | Block matching for the PyroWave Vulkan path |
| `VK_QCOM_image_processing2` | Absent | — |
| "Snapdragon Frame Motion Engine" / AFME as a product | Marketing name for the driver feature above; no separate public SDK found ([Qualcomm samples](https://www.qualcomm.com/developer/snapdragon-game-toolkit/samples)) | — |
| Android frame timelines: `Choreographer.FrameData` / `FrameTimeline` (API 33), `SurfaceControl.Transaction.setFrameTimeline` (API 33), `ASurfaceTransaction_setDesiredPresentTime` (NDK, API 29) | Android 13 = API 33 | Present generated and real frames on alternate vsyncs; DELOS's Phase lock already uses display-link deadlines ([Choreographer.FrameTimeline](https://developer.android.com/reference/android/view/Choreographer.FrameTimeline), [SurfaceControl.Transaction](https://developer.android.com/reference/android/view/SurfaceControl.Transaction)) |
| MediaCodec motion vectors | Not exposed by any public API | — |

## Known implementations and licences

| Implementation | Licence | Reusable in DELOS | Notes |
|---|---|---|---|
| AMD FidelityFX Frame Interpolation / FSR 3 ([manual](https://gpuopen.com/manuals/fidelityfx_sdk/techniques/frame-interpolation)) | MIT | Ideas and code (with notice) | Needs game depth and motion vectors plus its own optical flow; disocclusion masks, inpainting pyramid and HUD-less input. Shaders are HLSL CS 6.2 or GLSL 4.50 (Vulkan). For video: replace the game vectors with optical flow only, drop the depth priority. A port to GLES 3.1 is substantial work |
| AMD FidelityFX Optical Flow | MIT | Yes | Block-match pyramid with scene-change detection; needs wave operations and `msad4` |
| AMD Fluid Motion Frames | Closed, driver | No | — |
| Lossless Scaling LSFG | Closed, commercial | No | — |
| NVIDIA Optical Flow SDK FRUC | Proprietary, NVIDIA hardware only | No | — |
| RIFE / Practical-RIFE ([ECCV2022-RIFE](https://github.com/MegEngine/ECCV2022-RIFE)) | MIT | Yes (models MIT) | Neural; too slow for 120 Hz on a phone GPU [UNVERIFIED] |
| IFRNet, FILM | MIT / Apache-2.0 [UNVERIFIED per repository] | Yes | Heavier than RIFE |
| Qualcomm AFME sample | BSD-3-Clause (repository) | Yes | Shows the extension call sequence |
| VipleStream (Sunshine + Moonlight fork with "AI frame interpolation" on Android and Windows) ([repository](https://github.com/finaltwinsen/VipleStream)) | GPL-3.0 | Compatible with DELOS (GPL-3.0) but not permissive; reading for ideas is fine | Shows demand; its method and latency are not documented [UNVERIFIED] |
| moonlight-macos-framegen ([repository](https://github.com/AyberkA0/moonlight-macos-framegen)) | GPL-3.0 | As above | MetalFX-based frame generation on macOS |
| mpv `interpolation` | LGPL/GPL | — | Temporal resampling (blending), not motion-compensated |
| SVP | Closed (its MVTools backend is GPL) | No | CPU/GPU motion-compensated video interpolation for players |

Only MIT/BSD/Apache code may be copied into DELOS under the task's rule. GPL code is licence-compatible but is excluded by that rule.

## Latency budget at 60 → 120 Hz (Odin)

| Stage | Extrapolation (A/B) | Interpolation (C) |
|---|---|---|
| Wait for the next decoded frame | 0 | +16.7 ms (one source interval) |
| Flow | A: driver-internal [UNVERIFIED]; B: 0.5–1 ms one direction at 720p input (measured synthetic fixture, SGSR2_VIDEO.md) | 2 directions: about 1–2 ms |
| Warp / blend to 1920 × 1080 FP16 | about 0.5–1 ms [INFERENCE] | about 1–2 ms [INFERENCE] |
| Existing upscaler (if on) | runs once per decoded frame; the generated frame is produced **after** upscaling at output resolution, or before upscaling at stream resolution (cheaper, but needs a second upscale) | same |
| Frame period at 120 Hz | 8.33 ms; generated work must finish well inside it next to SGSR (2–4 ms measured) | same |

[INFERENCE] Extrapolation adds roughly 1–3 ms GPU work per generated frame, with no added display latency. Interpolation adds about 17 ms plus compute. Both must be measured.

**Interaction with pacing:**
- Phase lock (`PhaseLockDriver`/`PhaseLockPacer`) releases each decoded frame at its target refresh.
- With generation the schedule per 2 refreshes is: refresh k shows the real frame N; refresh k+1 shows G(N−1, N), extrapolated, or G(N, N+1), interpolated.
- The GL thread therefore draws on every refresh instead of once per decoded frame.
- The `FrameQueue` "skip to newest" logic must not skip a frame needed as a generation input.
- `SgsrPacing` has to account for the extra stage time.

**Render paths:**
- **GLES (MediaCodec + SGSR):**
  - Both QCOM extensions are available here.
  - Generated frames go through the same presentation (and P010 HDR) path.
  - First target.
- **Vulkan (PyroWave):**
  - No QCOM GLES extensions.
  - Use `VK_QCOM_image_processing` block match or port the SGSR2-DV flow (already in Vulkan) plus a warp compute shader.
  - Second target.

## Recommended design

### Phase 1: driver extrapolation probe (GLES, low effort)

1. In the app's EGL context, check `GL_QCOM_frame_extrapolation` and load `glExtrapolateTex2DQCOM` with `eglGetProcAddress`.
2. Keep the last two presented RGBA16F (HDR) or RGBA8 frames at output resolution. SGSR already ping-pongs history targets.
3. When the decoded cadence is half the display rate:
   - present the real frame at refresh k;
   - at refresh k+1 call `glExtrapolateTex2DQCOM(prev, cur, gen, 0.5)` and present `gen`.
4. Repeat instead of extrapolating on: a scene cut, a timestamp gap, the first frame after reset, or an HDR switch.
5. Measure the GPU time with the existing GPU timer queries.
6. Look at the quality: HUD, text and fast pans.

### Phase 2: own flow-based extrapolation (GLES, then Vulkan)

1. **Flow:**
   - `glTexEstimateMotionQCOM` on the decoder EGLImage pair; or
   - the SGSR2-DV LK flow already computed for the temporal upscaler, reused at no extra cost when SGSR2-DV is on.
2. **Confidence:** forward/backward consistency (as SGSR2-DV), structure tensor, and a residual check.
3. **Warp (compute):** sample the current frame at `p − 0.5·v(p)`.
   - The flow field is dilated (max-magnitude in a 3 × 3 block neighbourhood) to reduce holes at moving edges.
   - Blend toward the unwarped frame where the confidence is low.
   - Snap near-zero vectors to zero, which protects HUD and text.
4. **Output:** the same presentation and P010 pass.
5. **Vulkan:** port the warp shader. The flow exists in `sgsr_flow.comp`, which needs a coarse-to-fine interface first (SGSR2_VIDEO.md / OPTICAL_FLOW_PYRAMIDS note).

### Phase 3 (optional): interpolation mode

Bidirectional warp from N and N+1 at t = 0.5 with an occlusion mask (forward/backward disagreement), then fill holes from the nearer frame. Present it with one frame of delay. Off by default.

### Settings (stream menu and settings, per app)

| Setting | Values | Default |
|---|---|---|
| Frame generation | Off / Extrapolate (no added latency) / Interpolate (smoother, +1 frame latency) | Off until accepted |
| Target | Auto (display rate) / 2× stream rate | Auto |
| Show statistics | Generated frames per second, generation GPU time, repeats | With the existing statistics |

**Automatic fallback:**
- If generation plus the existing GPU work exceeds about 80 % of the refresh interval over a window, or frames start to miss their refresh, switch to repeat (no generation).
- Report it once via the existing overload notice (`FlowGovernor` / `OverloadDetector` pattern).

## 30 fps source with 1–3 generated frames (owner requirement, 2026-11-21)

Target: a 30 fps stream shown at 60, 90 or 120 Hz on the 120 Hz Odin, with the lowest latency and the fewest artifacts.

### Rates and time steps

| Output | Generated per source frame | Steps t (fraction of the 33.3 ms interval) | Display |
|---|---|---|---|
| 30 → 60 | 1 | 1/2 | 60 Hz mode, or 120 Hz with each frame held 2 refreshes |
| 30 → 90 | 2 | 1/3, 2/3 | 90 Hz mode only. At 120 Hz the 4-refresh interval cannot be split into 3 even steps, so 90 needs a 90 Hz display mode (the Odin offers it [UNVERIFIED]) |
| 30 → 120 | 3 | 1/4, 2/4, 3/4 | 120 Hz, one frame per refresh |

Rule:
- Request a display mode that is an integer multiple of the source rate.
- Otherwise generated frames land unevenly and judder returns.
- 30 → 120 and 30 → 60 work at the native 120 Hz; 90 needs a display mode switch.

### Multi-step generation

**Always generate from the same pair, never chain.**
- Each step t_k is computed directly from the two source frames and one flow field, scaled by t_k.
- Feeding a generated frame back as an input compounds warp error and blur at every step [INFERENCE; standard VFI practice].
- One flow estimation per source frame serves all 1–3 generated frames. Only the warp/blend pass runs per output frame. Per-frame cost therefore grows slowly with the multiplier.

**Extrapolation (no waiting):**
- Output frames at t = 1/4, 2/4, 3/4 *beyond* the newest frame, using the velocity of the last pair (N−1 → N).
- **Error growth:**
  - The position error is about acceleration × t² / 2. At 33 ms intervals the last step reaches 25 ms into the future.
  - The error at t = 3/4 is about 9× the error at t = 1/4.
  - Overshoot at stops and turns becomes visible as a "rubber band" when the next real frame snaps back.
  - Disoccluded regions (behind a moving object) have no source pixels at all.
- **Mitigation:** damp the scale for low-confidence or accelerating blocks. Use t_eff = t × c, with c from the flow consistency between pairs (N−2 → N−1) and (N−1 → N): blocks whose velocity changed strongly get c → 0, a repeat.

**Interpolation (one source frame of delay):**
- Output frames between N−1 and N at t = 1/4..3/4.
- No guessing about the future, and occlusions can be filled from the other frame.
- Cost: +33.3 ms display delay at 30 fps (one source interval), plus compute. That is twice the cost at 60 fps.

**Owner's requirement "lowest latency AND fewest artifacts" cannot be fully met by one mode at 30 fps. Proposal:**

| Mode | Latency added | Artifacts |
|---|---|---|
| Extrapolate (default for fast games) | ≈ 0 (+ about 1–3 ms compute) | Overshoot at sudden motion changes, holes at disocclusions (filled by a repeat) |
| Interpolate (default for slow/cinematic games) | +33 ms + compute | Lowest; occlusion handled from both frames |
| Hybrid (later) | ≈ 0 | Extrapolate, then correct the next extrapolated steps with the newly arrived frame as soon as it is decoded. Smaller error than pure extrapolation for 30 → 120 [INFERENCE] |

### `GL_QCOM_frame_extrapolation` for 30 → 120

- The spec allows any non-zero `scaleFactor`: values > 0 target the future, values < 0 the past. Three calls per source frame with 0.25, 0.5 and 0.75 on the same `src1`/`src2` are allowed by the spec ([Khronos text](https://registry.khronos.org/OpenGL/extensions/QCOM/QCOM_frame_extrapolation.txt)).
- Negative factors (−0.75 … −0.25 from N towards N−1) would give an *interpolation* between the two inputs with the driver's estimator. That is not the documented use case [UNVERIFIED].
- The spec says nothing about:
  - whether the motion estimation is reused between calls on the same pair, or recomputed (cost × 3) [UNVERIFIED];
  - the motion search range at 33 ms gaps [UNVERIFIED];
  - occlusion fill or HUD handling. Quality is "not defined".
- Chained steps (feeding the output back as an input) are possible by the API but not recommended (see above).
- **Decision:** Phase 1 measures cost and quality of 1, 2 and 3 calls per pair at 720p/1080p. If quality at t = 0.75 is poor, the driver path is used only for 30 → 60 and 60 → 120, and Phase 2 (own warp) handles 30 → 90/120.

### Flow quality at 33 ms motion

**Motion per source frame doubles versus 60 fps.** A fast pan of 1 screen width per second is about 64 px per frame at 1920 px; flicks can reach 150–300 px [INFERENCE].

**Requirements for the own flow (Phase 2):**

| Parameter | 60 fps (today, SGSR2-DV) | 30 fps requirement |
|---|---|---|
| Pyramid levels | 4 (8-px grid at level 0) | 5–6, so the coarsest level sees ≤ 8 px motion for 256 px displacement |
| Coarsest search | LK only | Block-match search ±8 at the coarsest level (QCOM `textureBlockMatch` on Vulkan, `glTexEstimateMotionQCOM` on GLES, or a SAD compute pass), then LK refinement down the pyramid. LK alone fails beyond about 1 pixel per level |
| Grid | 8 px | 8 px, plus a dilated 4-px field near motion edges |
| Temporal prior | none | Initialize with the previous pair's flow (motion is coherent at 30 fps); validate by the photometric residual |
| Confidence | forward/backward + residual | Same, plus a velocity-change term (N−2→N−1 vs N−1→N) for extrapolation damping |

`glTexEstimateMotionQCOM`:
- block size per implementation (query at runtime); search range unspecified [UNVERIFIED];
- the cost is in the motion hardware, not the shader cores [UNVERIFIED].

Measure its endpoint error on synthetic 30 fps translations of 16, 64, 128 and 256 px with the existing GPU fixture before relying on it.

### Occlusion, disocclusion and holes

1. **Occlusion mask:** forward/backward inconsistency > 1 px, or a target pixel claimed by two vectors (a splat count > 1).
2. **Interpolation:** in disoccluded areas take the colour only from the frame where the pixel is visible: the newer frame for newly revealed background, the older frame for covered areas.
3. **Extrapolation:** disoccluded pixels have no source.
   - Fill them from the current frame unwarped (a repeat), or from the background flow.
   - Inpainting from a downsampled pyramid (as FSR 3's inpainting pass) blurs. It is acceptable only for small holes ≤ 8 px [INFERENCE].
4. **Edges:** dilate the foreground vectors by one block so object borders are not torn.

### HUD and static pixels

1. **Static mask:** a pixel block whose luma residual between N−1 and N, and N−2 and N−1, is below a threshold (codec noise level, about 2/255) is static. Force v = 0 there; this holds menus, crosshairs and text still.
2. **Moving-background HUD:** blocks where the flow is non-zero but the residual against zero motion is also low (overlay on motion) get v = 0. Blend the warped background around them with a 1-block feather.
3. **Text over motion:** the confidence threshold blends towards the source frame. Blur is better than smearing letters.

All three masks come from data already computed for the flow; no extra pass is needed except a 3 × 3 max-filter [INFERENCE].

### Pacing at 120 Hz with 30 fps arrival jitter

**Schedule:**
- Real frame N on refresh r, then generated frames at r+1, r+2, r+3 (t = 1/4, 2/4, 3/4), then N+1 at r+4.
- The generated frames use **their own scheduled display time**, not their compute time. t_k = (display_time_k − display_time_N) / (expected interval 33.3 ms).

**Jitter:**
- Phase lock already estimates the arrival phase and margin.
- With 30 fps input it locks to every 4th refresh.
- If N+1 arrives late, extrapolation continues with t = 1.0 (or holds the t = 3/4 frame). It never jumps back.
- If N+1 arrives early, it is shown on its slot and the remaining generated frames are dropped.

**Extrapolation needs no queue.** Interpolation needs a 1-frame queue: the Phase-lock target becomes N−1 + 1 interval.

**Host cadence:**
- The real source rate is measured over a window.
- Generation is enabled only if the rate is stable at 30 (or 60) ±2 % with < 5 % late frames.
- Otherwise frames are shown as they arrive (today's behaviour).

**Implementation:**
- The GL thread draws on every refresh (Choreographer frame timelines, API 33), selecting either the newest real frame or a generated step.
- `FrameQueue`'s skip-to-newest must keep the two frames used as generation inputs.

### GPU cost per generated frame (Adreno 740, estimates)

| Work | When | Estimate | Basis |
|---|---|---|---|
| Flow (both directions, 5–6 levels, 720p input) | once per source frame | 1.5–3 ms | 0.5–1 ms per direction measured at 4 levels (SGSR2_VIDEO.md fixtures); extra levels and a coarse search [INFERENCE] |
| Masks (static, occlusion, dilation) | once per source frame | 0.3–0.6 ms | [INFERENCE] |
| Warp + blend at 1920 × 1080 FP16 | per generated frame | 0.8–1.5 ms | Two 1080p texture fetches + masks [INFERENCE] |
| SGSR upscale (if on) | once per source frame if generation happens after the upscale; per generated frame if before | 2–4 ms | Measured fixtures |

30 → 120: per 33.3 ms (4 refreshes) ≈ flow 3 ms + 3 × 1.5 ms warp + upscale 4 ms ≈ 11.5 ms GPU, about 35 % of the interval. This fits on paper. Generate **after** the upscale, at the output resolution: the upscaler then runs once per source frame. Every number here is unmeasured.

### Acceptance test plan (30 fps content)

**Test sources:**
- The host renders a fixed 30 fps test pattern, so results are repeatable. Vibepollo Desktop runs a looping 30 fps video:
  - a horizontal pan at 0.25, 1 and 3 screen widths per second;
  - a vertical scroll of text;
  - a moving object over a textured background;
  - a static HUD overlay over panning content;
  - particles;
  - a hard cut;
  - a fade.
- The same in one real game with a 30 fps cap (the owner starts it), plus a 30 fps PlayStation stream.

| Metric | Target | How |
|---|---|---|
| Output cadence | 120 frames/s with ≤ 1 % repeats or drops over 60 s on a steady source | Phase-lock counters |
| Even spacing | Present-time deltas 8.33 ms ± 1 ms p99 | `presentedTime` per frame |
| Added latency | Extrapolate ≤ 3 ms vs. off; interpolate ≈ 33 ms + ≤ 3 ms | 240 fps camera, controller LED vs. on-screen reaction |
| GPU | ≤ 60 % of the 33.3 ms window incl. upscaler; p99 per generated frame ≤ 2.5 ms | GL timer queries |
| Quality, synthetic | Endpoint error of flow < 1 px for 16–256 px translations; PSNR of the generated t = 1/2 frame vs. a ground-truth 60 fps render ≥ 30 dB on the pan clips [proposal] | Offline capture of the GPU output (debug readback) against a 120 fps master of the same pattern |
| Quality, eye (owner) | No HUD wobble, no text smear, no visible tearing at object edges, cut without a ghost frame, "rubber band" at stops acceptable | Side by side: off / extrapolate / interpolate |
| Thermal | 20 min at 30 → 120 without throttling below 120 output | Clock and temperature log |

## Implemented: vector median of the warp (`FrameGenerator`, 2026-11-23)

**What it does:** the block vectors of `glTexEstimateMotionQCOM` pass through a 3 × 3 **vector median** before the warp
samples them. Each block takes, of its own and its eight neighbours' vectors, the one with the smallest summed distance to
the others (its own on a tie).
- The result is always a vector the driver measured nearby: an outlier is replaced by a neighbour's vector, never by a blend
  of components, by "no motion" or by the newest picture.
- A component-wise median was not used because it can produce a vector no block had (x from one neighbour, y from another).
- The orientation test, the driver-versus-warp evaluation and the generated steps all use the filtered field.
- A device that cannot run the pass keeps the driver's vectors; the log then says `vector median off`.

**Log:** the periodic `Frame generation:` line (every 600 decoded frames) ends with these fields; the counts cover the time
since the previous line.

| Field | Meaning |
|---|---|
| `driver` / `warp` (first word) | The way that generates now |
| `errors vs hold: driver x, warp y` | Each way's smoothed error against the game's real next picture, relative to holding the picture; lower is better. The warp's figure is the measure for this change |
| `steps driver N warp M` | Generated steps by each way since the previous line: attributes what the owner saw to a way |
| `vectors mean A px max B px` | Length of the filtered vectors in picture pixels, over the measured pairs. The scale ends at 64 luma px (about 128 picture px): a `max` at that value means the vectors reached it or more |
| `median changed C % (P pairs)` | Share of blocks the median moved by more than 2 luma px (about 4 picture px), and how many pairs were measured (every 8th estimated pair, and every evaluated pair) |
| `at cap C %` | Share of blocks whose driver vector is at the edge of the search window: at least 30 luma px sideways or 25 up and down. The window is not documented; the constant largest length of 41.9 luma px fits ±32 × ±27 [INFERENCE] |
| `max x A y B luma px` | The driver's largest vector on each axis: confirms the window |
| `estimation T ms` | Wall time of one `glTexEstimateMotionQCOM` call, with `glFinish` before and after, on every 8th pair. An upper bound of its GPU time |
| `fast pairs N: driver x warp y` | Evaluated pairs whose mean vector is at least 20 luma px (about 40 picture px per game frame), and each way's mean error against the real next picture on them (relative to holding; lower is better) |
| `vectors not measured` | No motion was estimated since the previous line (the driver's way ran without an evaluation) |

**Observed (Odin, PS5 game at 30 fps, 2026-11-23):** `steps driver 0 warp 297, vectors mean 16.1 px max 83.8 px, median
changed 8.4 % (40 pairs)`; the maximum was 83.8 px in every line. The owner judged the picture clearly better with the median.

## Implemented: pre-shift of the motion estimation (`FrameGenerator`, 2026-11-23)

**Why:** `GL_QCOM_motion_estimation` takes no search range and no starting vector. Its window (about ±64 × ±54 picture px per
pair at half-size luma [INFERENCE]) is smaller than a fast camera turn at 30 fps.

**What it does:** a camera turn is nearly one shift of the whole picture and changes little from pair to pair.
1. The mean vector of the previous pair's filtered field is taken on the GPU (one texel) and held within ±30 × ±25 luma px.
   With that limit "no motion" always stays inside the window, so a camera that stops is measured correctly on the next pair.
2. The older picture's luma is drawn moved by that shift; the driver estimates what is left.
3. The median pass adds the shift back. The warp, the evaluation and the statistics use the sum.

**Reach:** up to about twice the window for a steady turn, with the half-size precision and one estimation per pair.

**When there is no shift:**
- before the vectors' orientation is known, and without the vector median;
- when the previous field is not that of this pair or of the one right before it (the driver's way ran, a restart, a new size);
- after a cut or a sudden start: a pair whose difference is over three times the one before.
- A device that cannot run a pass logs `pre-shift off` once and estimates unshifted as before.

**Limits:** the first pair of a sudden fast turn still exceeds the window; fast objects in front of a still camera gain nothing;
a strip as wide as the shift at the picture's border has no true match.

**Log** (same line): `pre-shift on S of E pairs` (estimated pairs that started from a shift) and `shift mean G px` (its mean
length on the measured shifted pairs, picture px). With the shift, `at cap` and `max x … y …` describe what the driver found
**after** the shift: `at cap` should fall on steady fast turns while `vectors mean` and `max` rise above the old 83.8 px.

**Not verified:** not built or run.

## Verification plan

| Check | Method | Owner judges by eye |
|---|---|---|
| Extension availability in the app context | Log `GL_EXTENSIONS` at SGSR start | — |
| GPU time per generated frame | GL timer queries, p50/p95/p99 in the stream statistics at 720p and 1080p input, SDR and HDR | — |
| Display cadence | Phase lock counters: real vs. generated vs. repeated per second; missed refreshes | Smoothness of a slow camera pan |
| Latency | Camera (240 fps) input-to-photon test: off vs. extrapolate vs. interpolate, same scene | — |
| Artifacts | Test clips: slow pan, fast pan, HUD over motion, scrolling text, particles, scene cut, menu fade | Smear, ghosting, HUD wobble, edge tearing |
| Thermals | 20-minute session at 120 Hz, clocks and temperature | — |

### Not verified

- No extension was called.
- No GPU timing of generation exists.
- No latency was measured.
- The extension list came from SurfaceFlinger's context, not from DELOS's EGL context.
- The neural-interpolation costs are reports, not measurements.
- The quality of the driver extrapolator on decoded video is unknown.

## Sources

- Khronos: [QCOM_frame_extrapolation](https://registry.khronos.org/OpenGL/extensions/QCOM/QCOM_frame_extrapolation.txt), [QCOM_motion_estimation](https://registry.khronos.org/OpenGL/extensions/QCOM/QCOM_motion_estimation.txt)
- Qualcomm: [AFME power-saving sample](https://github.com/SnapdragonGameStudios/adreno-gpu-opengl-es-code-sample-framework/tree/main/samples/amfe_power_saving); [GPU motion estimation blog, 2024-03](https://www.qualcomm.com/developer/blog/2024/03/gpu-motion-estimation-improved-handling-repeating-patterns)
- AMD: [FidelityFX Frame Interpolation manual](https://gpuopen.com/manuals/fidelityfx_sdk/techniques/frame-interpolation)
- Android: [Choreographer.FrameTimeline](https://developer.android.com/reference/android/view/Choreographer.FrameTimeline), [SurfaceControl.Transaction](https://developer.android.com/reference/android/view/SurfaceControl.Transaction), [surface_control.h](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/master/include/android/surface_control.h)
- RIFE: [ECCV2022-RIFE](https://github.com/MegEngine/ECCV2022-RIFE)
- Streaming forks: [VipleStream](https://github.com/finaltwinsen/VipleStream), [moonlight-macos-framegen](https://github.com/AyberkA0/moonlight-macos-framegen)
- Device query: `adb shell dumpsys SurfaceFlinger` on the Odin, 2026-11-21 (read-only)
