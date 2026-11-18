# Optical flow for MetalFX and SGSR2

## Purpose

Evaluate Gaussian pyramids, Lucas–Kanade, Horn–Schunck and newer optical-flow methods for temporal upscaling of
received video in Delos. This is dated research, not a shipped algorithm change or a performance result.

## Scope

The estimator contract covers both MetalFX Temporal and SGSR2-DV. Metal, GLES and Vulkan need separate GPU
implementations, but can share the mathematics, motion convention, confidence semantics and test vectors.
Spatial upscalers do not consume this flow. No output resolution, stream frame rate, bitrate, HDR precision or
asset quality is reduced by this recommendation. An analysis pyramid is additional data, not a smaller output.

The current implementation is specified in [SGSR2_VIDEO.md](SGSR2_VIDEO.md). The earlier feasibility evidence
remains in [VIDEO_OPTICAL_FLOW_RESEARCH.md](VIDEO_OPTICAL_FLOW_RESEARCH.md); its implementation milestones
are historical, not the current work queue. Live implementation status belongs in GitHub issues.

## Findings

1. Gaussian pyramids can be built directly from decoded video textures, one spatial pyramid per frame. They
   need neither game-engine access nor a future frame. Current/previous pyramids are retained in a ping-pong pair.
2. Pyramids are not an alternative to Lucas–Kanade or Horn–Schunck. They make a solver coarse-to-fine so that
   large full-resolution displacements become small displacements at coarse levels [S1, S2].
3. The inspected GLES implementation already has four-level pyramidal Lucas–Kanade. The inspected Metal
   implementation also has four levels and already uses inverse-compositional updates. Therefore adding a
   pyramid or caching the template Hessian is not a new feature for every backend.
4. Existing mip generation and box reduction are not an explicitly controlled recursive Gaussian pyramid.
   Anti-aliasing, correspondence quality, densification, confidence and temporal acceptance remain separate concerns.
5. The user's report that the current result is insufficient is the visual problem to address. Source inspection
   alone cannot attribute the artifacts to Lucas–Kanade rather than sampling, rejection, cadence or reconstruction.
   There is no new measured quality, latency or energy comparison in this research.

## Current implementation evidence

Snapshot inspected on 2026-11-18: Delos `e3241ab85486f165b369e34c3a95254305d45acc`; Metal implementation
`2f01b5599b6264d8a73ad3a9e4e659995376f61f`. These identify source snapshots, not device acceptance.

| Path | Observed design | Consequence for an experiment |
|---|---|---|
| `app/src/main/java/com/limelight/binding/video/SgsrTemporal.java` and `app/src/main/assets/sgsr/video_flow.comp` | Four luma levels via `glGenerateMipmap`; 8-pixel flow grid; up to six iterations per level; gradients and tensor recomputed from the warped previous frame | Existing coarse-to-fine baseline. Compare an explicit Gaussian construction and inverse-compositional solve against it, rather than calling the baseline single-scale |
| `app/src/main/jni/pyrowave-renderer/shaders/sgsr_flow.comp` | Shader samples LOD 0, initializes motion to zero and performs up to five iterations; no coarse-flow input in that shader | The checked kernel does not have the GLES coarse-to-fine interface. Native integration and generated SPIR-V must be checked before claiming equivalent pyramids |
| `Packages/DelosKit/Sources/DelosMedia/UpscalerShaders.metal`, `videoLumaPyramid` | Initial 3×3 binomial smoothing; subsequent 2×2 box reductions in an 8×8 threadgroup | Smoothing exists, but not recursive Gaussian filtering at every reduction. Wider filters need halos across threadgroup boundaries |
| `Packages/DelosKit/Sources/DelosMedia/UpscalerShaders.metal`, `videoFlow`; `MetalUpscaler.swift`, `VideoMotion` | Four levels, 8-pixel grid, inverse-compositional template gradients/tensor, independent backward/forward solves, reactive mask and scene heuristic | Preserve these existing optimizations. Evaluate matching and reconstruction separately before replacing the solver |

The Metal paths in this table are relative to the platform source repository. This document lives in Delos and
specifies a shared technical contract, not a cross-platform product comparison.

## Gaussian pyramid design

For analysis image `Y_t`, define `P_t[0] = Y_t` and:

```text
P_t[l + 1] = decimate_by_2(Gaussian_like_filter(P_t[l]))
```

A reproducible candidate uses separable binomial weights `[1, 4, 6, 4, 1] / 16` in each axis before every
reduction [S1]. This approximates a Gaussian; record the discrete kernel rather than claiming an unspecified
Gaussian sigma. Compare against the smaller `[1, 2, 1] / 4` filter and the existing reduction at equal settings.
Stronger smoothing can remove the thin features needed for matching. It is not automatically better.

### GPU execution and coordinates

- Keep current and previous analysis images on the GPU. Reuse the previous frame's complete pyramid; compute
  only the new frame's pyramid during continuous playback. Reset frames initialize both sides safely.
- Use explicitly ordered compute dispatches and the backend's visibility barriers between producer/consumer
  levels. A threadgroup barrier cannot synchronize different threadgroups. A fused tile implementation needs
  a sufficient halo for every level; it cannot treat the tile's edge as the image's edge.
- Preallocate textures and scratch storage when the stream size changes. Avoid CPU readback, per-frame allocation
  and additional queued frames in production. Include all passes and synchronization in timing.
- Specify odd-dimension rounding, edge extension and pixel-center mapping in one fixture contract. Image sizes
  and flow-grid sizes are different; sampling a coarse grid as if it were an image creates systematic offsets.
- Under the half-pixel convention, `x_coarse = (x_fine + 0.5) / 2 - 0.5`. Propagate displacement in level pixels
  with `u_fine = 2 * interpolate(u_coarse) + delta_u_fine`. Validate the convention against actual mip generation;
  odd sizes require explicit tests rather than assuming a universal exact factor from dimensions alone.
- The theoretical level-area sum approaches `4/3` of one full-resolution image for factor-two reduction.
  This excludes scratch, gradients, both frame pyramids, flow fields, alignment and output resources. It is not
  a memory-use measurement or a prediction of GPU cost.
- Keep the color/HDR pipeline unchanged. Analysis luma needs defined range and transfer handling; encoded PQ,
  HLG and SDR values are not interchangeable brightness measurements. Compare analysis precision and clipping
  without reducing presentation precision or assuming a linear-light solver is automatically superior.

### Causal coarse-to-fine solve

```text
Decode frame t -> GPU analysis pyramid P_t
Retain P_(t-1) from the preceding valid frame
At the coarsest level: initialize and solve correspondences
For each finer level: lift flow, warp, refine, update confidence
Densify with edge-aware correspondence weights
Check independent forward/backward agreement and history validity
Adapt current-to-previous flow and rejection to the temporal consumer
Retain P_t; reset correspondence/history state on discontinuity
```

No future-frame lookahead is required. Processing still adds latency. A previous flow can initialize the new
solve only when transported and validated in the correct coordinates; it is not the new frame pair's final flow.
The existing GLES overload reuse policy must be included in comparisons, not silently assumed absent.

## Method comparison

These are algorithmic tradeoffs, not measured rankings on either target device.

| Method | What it changes | Fit for Delos and main risk |
|---|---|---|
| Pyramidal Lucas–Kanade [S1] | Local patch least squares with coarse-to-fine warping | Low-disruption baseline. Can be applied on a regular grid; it is not restricted to sparse corner tracking. Flat regions, the aperture problem and two motions inside one patch remain ambiguous |
| Inverse-compositional Lucas–Kanade [S1, S3] | Reuses template gradients and tensor within patch iterations | Candidate efficiency improvement for the inspected GLES/Vulkan kernels; already present in the inspected Metal kernel. Runtime improvement is not measured |
| Horn–Schunck [S2] | Brightness data term plus global spatial smoothness | Fills uncertain regions densely, but basic quadratic smoothing can bleed foreground motion into background/HUD edges. Needs warping/pyramids for large displacement and bounded iterations; not a drop-in speed upgrade |
| Robust combined local-global / variational refinement [S2] | Local evidence with a regularized field, optionally robust data/smoothness penalties | Useful refinement comparator. Edge-aware regularization is preferable to unqualified uniform smoothing; extra passes and convergence cost must be measured |
| Dense Inverse Search (DIS) [S3] | Inverse patch search, multiscale densification through overlapping patch aggregation, variational refinement | Recommended stronger classical comparator. Its dense field is not just bilinear enlargement of an 8-pixel grid. Paper CPU timings exclude some preprocessing in key comparisons and are not mobile GPU results |
| RAFT / RAFT Small [S4] | Learned feature matching, multiscale correlation and recurrent dense-flow updates | Useful offline quality reference. Runtime conversion, correlation memory, inference scheduling and image transfers need validation; a desktop result does not establish 120 FPS streaming feasibility |
| SEA-RAFT [S5] | Learned initial flow and revised training/refinement of a RAFT-family estimator | More recent learned comparator, not a proven faster Delos backend. Code, weights, runtime and redistribution terms require separate review |

The Horn–Schunck objective combines a brightness-constraint residual with spatial flow gradients:

```text
E(u,v) = integral[(Ix*u + Iy*v + It)^2 + alpha^2*(|grad u|^2 + |grad v|^2)] dx dy
```

The regularizer produces density by imposing a prior; density does not mean the motion is observed correctly.
A useful robust refinement must still reject disocclusions and avoid smoothing across independently moving edges.
Do not increase temporal accumulation merely because the flow field is smoother.

## Shared consumer contract

| Property | Common rule | Consumer adaptation |
|---|---|---|
| Direction | Backward displacement maps a current pixel to its previous-frame location; camera and object motion are already included | MetalFX uses input-pixel displacement with the configured motion scale. SGSR2-DV converts to its shader's history-sampling convention, including Y orientation |
| Confidence | Separate matching ambiguity, tensor conditioning, photometric residual, bounds and forward/backward inconsistency | MetalFX reactive-mask polarity and SGSR2-DV Activate/history-weight semantics must be adapted explicitly, not assumed identical |
| Invalid history | Reject new exposure/disocclusion, out-of-bounds history, cuts and discontinuous timestamps | Apply the appropriate reset/rejection path without CPU synchronization solely to read a cut decision |
| Depth and jitter | A decoded image pair contains no engine depth buffer or known render jitter | A constant depth plane is not scene geometry. Keep jitter zero unless real source jitter is supplied; fabricated jitter cannot recreate missing samples |
| Freshness | Flow belongs to a named ordered frame pair and timestamp interval | Do not substitute stale vectors after drops, resize, HDR transition or decoder recovery |

Forward flow cannot generally be substituted by negating backward flow at the same pixel. Both fields must be
sampled in their respective coordinate domains. A shared fixture suite needs to test this adapter as carefully as
the estimator. Better flow cannot guarantee recovery of detail absent from a compressed, non-jittered stream.

## Recommendation

The recommended experiment is an explicit recursive Gaussian pyramid with a robust inverse-compositional
patch solver, confidence-weighted densification and a DIS-inspired refinement comparator. It preserves the
existing causal, GPU-resident architecture. This is a recommendation for evaluation, not a claim of improvement
and not authorization to replace all backends without comparison.

For Metal, retain the existing inverse-compositional solve and test pyramid filtering, patch initialization,
edge-aware densification and rejection first. For GLES, cached template derivatives/tensors are an additional
candidate. For the inspected Vulkan kernel, establish a real coarse-to-fine interface before claiming parity.

Horn–Schunck is valuable as a controlled dense-regularization comparator, not the preferred unconditional
replacement. RAFT and SEA-RAFT provide learned references; neither is selected for production without measured
end-to-end benefit on the target hardware. Merely choosing a newer algorithm does not resolve cadence failures
or history artifacts caused outside the estimator.

## Verification

The acceptance experiment compares unchanged baseline, explicit-pyramid baseline, optimized local solver and
DIS/robust-refinement candidate using identical decoded frame pairs and reconstruction settings. It separates
estimator gains from rejection/sharpening changes. Alternative analysis filters are explicit experimental variables,
not silent reductions of stream quality.

| Area | Required evidence |
|---|---|
| Known motion | Signed integer/subpixel translations, large displacement, odd sizes, borders and frame gaps; endpoint error and invalid-pixel rejection |
| Independent objects | Foreground/background boundary, stationary HUD over camera pan, thin lines, repeated textures and textureless regions |
| Appearance changes | Compression noise, motion blur, particles/transparency, exposure jumps and hard cuts; calibrated confidence rather than arbitrary density |
| Temporal reconstruction | Text scrolling, trails after disocclusion, flicker, accumulated blur and detail stability in sequences, not only still screenshots |
| Cost | GPU time for analysis, all pyramid levels, both flow directions, densification/refinement, masks and reconstruction; p50/p95/p99 delivery and dropped frames |
| Device behavior | Sustained thermals/energy and SDR/HDR correctness on physical hardware for each backend; no emulator performance verdict |

At 60 FPS a frame interval is approximately 16.67 ms; at 120 FPS it is approximately 8.33 ms. These are arithmetic
intervals, not available GPU budgets. The estimator shares the interval with decoding, color conversion, temporal
reconstruction and presentation. Require no extra queued frame and no regression in actual delivery; a lower mean
flow time does not excuse worse tail latency. The owner starts real streams.

### Not verified

No new algorithm, build, device run, capture, profiling or benchmark was performed for this document. Gaussian
filter choice, iteration counts, patch overlap, precision, energy cost and artifact reduction are not measured.
The source differences above need reproduction against the actual branch before implementation. The old device
capability evidence is not proof of current driver performance.

## Related

- [SGSR2-DV specification](SGSR2_VIDEO.md)
- [Earlier optical-flow feasibility evidence](VIDEO_OPTICAL_FLOW_RESEARCH.md)
- [Stream quality](STREAM_QUALITY.md)
- [Engineering and documentation rules](ENGINEERING_RULES.md)

## Evidence (2026-11-18)

Method: read-only inspection of the source snapshots above and the primary publications below. No hardware or
runtime version is recorded because no hardware experiment was performed. No third-party implementation or
weights were imported. Before importing code or weights, pin their revision and verify licenses and notices.

### Primary sources

- **S1:** Jean-Yves Bouguet, *Pyramidal Implementation of the Lucas Kanade Feature Tracker*.
  [Algorithm, recursive anti-aliasing filters and coarse-to-fine derivation](https://robots.stanford.edu/cs223b04/algo_tracking.pdf).
- **S2:** Bruhn, Weickert and Schnörr, *Lucas/Kanade Meets Horn/Schunck: Combining Local and Global Optic Flow
  Methods*, IJCV 61(3), 2005. [Author-hosted paper](https://www.mia.uni-saarland.de/Publications/bruhn-ijcv05c.pdf).
  Discusses both classical objectives, noise, confidence, discontinuity-preserving extensions and multiresolution.
  Original Horn–Schunck reference: [Determining Optical Flow, 1981](https://doi.org/10.1016/0004-3702(81)90024-2).
- **S3:** Kroeger, Timofte, Dai and Van Gool, *Fast Optical Flow using Dense Inverse Search*, 2016.
  [Paper](https://arxiv.org/abs/1603.03590), especially sections 2 and 3.1 on inverse search, aggregation, refinement
  and the scope of reported timing.
- **S4:** Teed and Deng, *RAFT: Recurrent All-Pairs Field Transforms for Optical Flow*, 2020.
  [Paper](https://arxiv.org/abs/2003.12039).
- **S5:** Wang, Lipson and Deng, *SEA-RAFT: Simple, Efficient, Accurate RAFT for Optical Flow*, 2024.
  [Paper](https://arxiv.org/abs/2405.14793).
- **S6:** Apple, [MTLFXTemporalScaler](https://developer.apple.com/documentation/metalfx/mtlfxtemporalscaler).
  The inspected adapter configures input-pixel motion scale, zero jitter, reactive-mask support and a constant
  depth plane; those local choices do not establish equivalence to engine-provided inputs.
