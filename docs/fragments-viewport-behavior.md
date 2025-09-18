# Fragments Viewport Behavior — Semantics, Rules, and Examples

Last updated: 2025-09-07

This note describes how the server interprets viewport hints for fragments
emission and selection, and which fallbacks/metrics apply.

## Inputs
- `viewport_start_blip_id` (string): anchor blip id, e.g. `b+123`.
- `viewport_direction` (string): `forward` | `backward`.
- `viewport_limit` (int): desired number of blip segments, > 0.

## Clamping and Validation
- `viewport_limit` is clamped to `[wave.fragments.defaultViewportLimit, wave.fragments.maxViewportLimit]`.
- If only `viewport_direction` is present (no start/limit), the request is treated as ambiguous:
  - Server records the `viewportAmbiguity` metric.
  - Defaults are applied (direction respected, limit defaults, anchor may be heuristics).

## Selection Order
1. Include `INDEX` and `MANIFEST` segments first.
2. Preferred: manifest order (conversation tree) near the anchor.
3. Fallback: time-based blip ordering (mtime/id) if manifest order isn’t available.
4. Final fallback: `INDEX` and `MANIFEST` only when selection fails.

## Fallbacks and Metrics
- `computeFallbacks`: manifest/time-based computation failed; selection fell back to safe defaults.
- `emissionFallbacks`: only `INDEX`/`MANIFEST` emitted in the update.
- `viewportAmbiguity`: ambiguous hints seen; defaults applied.
- HTTP counters (if `/fragments` is enabled): `httpRequests`, `httpOk`, `httpErrors`.

View under `/statusz?show=fragments`.

## Text Diagrams

```
Manifest order (root → leaves):

  [INDEX]   [MANIFEST]
      |         |
  b+1 → b+2 → b+3 → b+4 → ...

Anchor = b+2, direction=forward, limit=2
→ Visible: [INDEX, MANIFEST, b+2, b+3]

Anchor = b+4, direction=backward, limit=3
→ Visible: [INDEX, MANIFEST, b+2, b+3, b+4]
```

## Worked Examples and Pseudo‑Cases

The server combines the three hints to choose a window around an anchor. When the
inputs are incomplete or cannot be resolved deterministically, the server applies
defaults and increments the `viewportAmbiguity` metric.

Assume manifest order: [b+1, b+2, b+3, b+4, b+5, …]. In all cases INDEX and
MANIFEST are included first.

- Example A — Full hints (no ambiguity)
  - Input: start=b+10, direction=forward, limit=5
  - Action: select [b+10 … b+14] (clamped to end)
  - Metrics: no `viewportAmbiguity` increment

- Example B — Backward window (no ambiguity)
  - Input: start=b+4, direction=backward, limit=3
  - Action: select [b+2, b+3, b+4]
  - Metrics: none

- Example C — Missing start (ambiguous)
  - Input: start=null, direction=forward, limit=4
  - Action: choose a heuristic anchor (e.g., most recently visible blip or
    manifest head), then select [anchor …] with limit=4
  - Metrics: `viewportAmbiguity`++

- Example D — Missing start and limit (ambiguous)
  - Input: start=null, direction=backward, limit=null
  - Action: choose heuristic anchor; apply default limit; move backward
  - Metrics: `viewportAmbiguity`++

- Example E — Start not found (ambiguous)
  - Input: start=b+999 (absent), direction=forward, limit=3
  - Action: treat as ambiguous; choose nearest valid anchor (e.g., head)
  - Metrics: `viewportAmbiguity`++

- Example F — Invalid/edge limit (no ambiguity)
  - Input: start=b+2, direction=forward, limit=0
  - Action: clamp limit to default; select forward
  - Metrics: none (clamping alone does not imply ambiguity)

- Example G — Oversized limit (no ambiguity)
  - Input: start=b+2, direction=forward, limit=10000
  - Action: clamp to max; select forward
  - Metrics: none

- Example H — Prefer‑state filter (independent of ambiguity)
  - Input: any window; preferSegmentState=true; state has only INDEX/MANIFEST
  - Action: filter emitted ranges to known segments (INDEX/MANIFEST)
  - Metrics: no `viewportAmbiguity`; may see `emissionFallbacks` if only
    INDEX/MANIFEST remain after filtering.

### Quick Reference: When does `viewportAmbiguity` increment?
- Yes:
  - Missing anchor with no deterministic default (C, D)
  - Anchor cannot be resolved (E)
- No:
  - Limit clamping (F, G)
  - Any fully specified triplet (A, B)
  - Prefer‑state filtering (H)

### Pseudo‑API Examples
- HTTP (conceptual):
  - GET /fragments?wavelet=example.com/w+1/conv+root&start=b+10&dir=forward&limit=5
- Multiplexer open (conceptual):
  - open(waveId, filter, stream, startBlipId="b+10", direction="forward", limit=5)


## Operational Notes
- Manifest order is cached with TTL to reduce recomputation on hot wavelets:
  - Config: `wave.fragments.manifestOrderCache.{maxEntries,ttlMs}`
  - Cache is LRU + TTL bounded.
- Prefer segment state when available (experimental):
  - Config: `server.preferSegmentState`
  - When enabled, ranges are filtered to known segments from the state; compat remains the fallback.

## Examples of Clamping
- `viewport_limit=0` → clamped to default; log fine-level note.
- `viewport_limit=10_000` → clamped to max; log fine-level note.

## Failure Transparency
- Fallbacks log at WARN with the wavelet name to ease debugging.
- Config read failures log at INFO with defaults applied.

## Current Limitations (2025-09-18)
- When `forceClientFragments=true` the server still sends a full `WaveletSnapshot` on the initial `ViewChannel` update. The fragments window is additive, so the browser renders all blips immediately despite the clamp.
- `fragmentsApplierMaxRanges` only trims how many ranges the client applier processes per batch; it does not reduce the payload size the server sends.
- The current `ViewChannelFragmentRequester` in stream mode is a no-op, so scrolling does not trigger additional fragment fetches. Wiring it to `ViewChannel.fetchFragments` remains pending.
- Observability via the fragments badge (`FragmentsDebugIndicator`) shows paged-in counts (e.g., `Blips 3/5`) even when all blips have been fetched; treat it as a UI virtualization indicator rather than evidence of deferred data load.

