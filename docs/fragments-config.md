# Fragments Configuration Reference

Last updated: 2025-09-07

This is a concise, reference-first guide to fragments-related configuration.
Defaults are shown; see reference.conf for inline comments.

## Server flags (gates)
- `server.enableFragmentsHttp` (bool, default `false`)
  - Enables `/fragments/*` (auth required). Dev/proto only.
- `server.enableFetchFragmentsRpc` (bool, default `false`)
  - Enables RPC fragments emission and handler wiring.
- `server.preferSegmentState` (bool, default `false`)
  - Filters emitted ranges to known segments when state exists; compat fallback.

## Server caches/state
- `server.segmentStateRegistry.maxEntries` (int, default `1024`)
- `server.segmentStateRegistry.ttlMs` (long, default `300000`)
  - Size/TTL for in-memory SegmentWaveletState registry (LRU + TTL).

### Sizing Guidance and Examples (segment state registry)

What it is
- One entry per wavelet (WaveletName) holding a SegmentWaveletState.
- LRU on capacity; TTL is enforced from insertion time (a fresh `put` sets the timestamp; `get` does not refresh TTL).

What to watch (Statusz → Fragments Caches)
- `segmentStateRegistry: hits/misses/evictions/expirations`.
- Healthy steady state: primarily hits; evictions/expirations should be non-zero but not dominate.

Heuristics
- Start with `maxEntries` >= peak concurrently active wavelets per node.
- Choose `ttlMs` to cover the typical “interaction session” length (how long you want a wavelet’s state to remain warm after last build).
- If memory is tight, bias toward smaller `maxEntries` and shorter `ttlMs` but accept more misses and rebuilds.

Back-of-the-envelope
- If a node sees ~A active wavelets concurrently, set `maxEntries ≈ 1.5 × A` to absorb short spikes without constant churn.
- If users typically bounce between wavelets within T minutes, set `ttlMs ≈ T × 60_000` so recently used state isn’t discarded too soon.

Scenario examples
- Local/dev (low traffic)
  - Goal: keep things simple, avoid noisy churn.
  - Suggested: `maxEntries = 128`, `ttlMs = 300_000` (5 minutes).
  - Expectation: very few evictions; occasional expirations between runs.

- Staging / small team (≤ 200 concurrent wavelets/node)
  - Suggested: `maxEntries = 512–1024`, `ttlMs = 300_000–600_000` (5–10 minutes).
  - Watch: if `misses` grow and `evictions` spike during load tests, increase `maxEntries`.

- Production (read‑heavy, moderate churn; ~1k concurrent wavelets/node)
  - Suggested starting point: `maxEntries = 2048`, `ttlMs = 600_000` (10 minutes).
  - If `expirations` dominate and users revisit the same waves within 10 minutes, increase `ttlMs` to 15–20 minutes.
  - If GC/memory pressure increases, reduce `maxEntries` by 25–30% and re‑evaluate hit/miss ratio.

- Production (write/churn heavy or memory‑constrained)
  - Suggested: `maxEntries = 1024–1536`, `ttlMs = 120_000–300_000` (2–5 minutes).
  - Trade‑off: more rebuilds (higher `misses`), but lower memory footprint and shorter retention.

Sample configs
```
# Dev/local
server.segmentStateRegistry.maxEntries = 128
server.segmentStateRegistry.ttlMs = 300000  # 5m

# Staging (burstier than dev, still modest)
server.segmentStateRegistry.maxEntries = 1024
server.segmentStateRegistry.ttlMs = 600000  # 10m

# Production read‑heavy
server.segmentStateRegistry.maxEntries = 2048
server.segmentStateRegistry.ttlMs = 900000  # 15m

# Production churn‑heavy / constrained memory
server.segmentStateRegistry.maxEntries = 1280
server.segmentStateRegistry.ttlMs = 180000  # 3m
```

Tuning loop
- Increase `maxEntries` if `evictions` correlate with traffic spikes and hit ratio drops.
- Increase `ttlMs` if users frequently revisit the same wavelets within the TTL and you observe `expirations` immediately followed by rebuilds (misses).
- Decrease `maxEntries`/`ttlMs` if memory pressure rises or registry holds data longer than needed.


## Viewport and manifest order
- `wave.fragments.defaultViewportLimit` (int, default `5`)
- `wave.fragments.maxViewportLimit` (int, default `50`)
- `wave.fragments.manifestOrderCache.maxEntries` (int, default `1024`)
- `wave.fragments.manifestOrderCache.ttlMs` (long, default `120000`)

## Client applier
- `wave.fragments.applier.impl` (string, default `skeleton`)
  - Which client-side applier to wire when `client.flags.defaults.enableFragmentsApplier=true`.
  - Supported: `skeleton` (records windows + history), `real` (merges coverage ranges), `noop` (disabled).

## Client flags (merged into client.flags.defaults)
- `client.flags.defaults.enableFragmentsApplier` (bool, default `false`)
  - Enables client-side RawFragmentsApplier hook in ViewChannelImpl.

## Observability (Statusz → Fragments Metrics)
- Emission: `emissionCount`, `emissionRanges`, `emissionErrors`, `emissionFallbacks`
- Compute: `computeFallbacks`, `viewportAmbiguity`
- HTTP: `httpRequests`, `httpOk`, `httpErrors`
- Applier: `applierEvents`, `applierDurationsMs`, `applierRejected`
  - `applierRejected`: count of invalid fragments dropped by the client applier
    (null segment id, negative bounds, or `from > to`). Healthy systems should
    keep this near zero; spikes suggest malformed payloads or an ongoing
    migration/canary.

## Startup Validation
- On startup, the server validates key cache/registry settings and aborts with a clear error if invalid values are provided:
  - `server.segmentStateRegistry.maxEntries` must be > 0
  - `server.segmentStateRegistry.ttlMs` must be >= 0 (0 disables TTL)
  - `wave.fragments.manifestOrderCache.maxEntries` must be > 0
  - `wave.fragments.manifestOrderCache.ttlMs` must be >= 0 (0 disables TTL)
- Invalid values throw `ConfigurationInitializationException` during initialization; see `ServerMain.applyFragmentsConfig`.

## Cache Metrics (Statusz → Fragments Caches)
- Manifest order cache: `hits`, `misses`, `evictions`, `expirations`.
- Segment state registry: `hits`, `misses`, `evictions`, `expirations`.
- View at `/statusz?show=fragments` under “Fragments Caches”.
