# F-2 slice 4: Floating controls + version-history overlay + profile overlay scaffolding

Status: Ready for implementation
Owner: codex/issue-1048-floating-version-history worktree
Issue: [#1048](https://github.com/vega113/supawave/issues/1048)
Parent umbrella: [#1037](https://github.com/vega113/supawave/issues/1037) (slice 4 of 6)
Tracker: [#904](https://github.com/vega113/supawave/issues/904)

Foundation (all merged):
- F-0 (#1035, PR #1043, sha `af7072f9`) — wavy design tokens + plugin slot contracts.
- F-1 (#1036, PR #1040, sha `86ea6b44`) — viewport-scoped data path; depth-axis fragment contract.
- F-2.S1 (#1037, PR #1045, sha `f4a6cd6f`) — StageOne read surface: `<wave-blip>`, `<wave-blip-toolbar>`, per-blip metadata, server-side parity wire.

Inventory affordances claimed (12):
- **J.2** Scroll-to-new-messages floating pill
- **J.3** Hide / Show wave controls toggle
- **J.4** Open / close navigation drawer
- **J.5** Back to inbox
- **K.1** Version-history overlay (full-bleed, dark wash)
- **K.2** Time slider with playhead handle (signal-cyan)
- **K.3** "Show changes" toggle
- **K.4** "Text only" toggle
- **K.5** "Restore" action (primary destructive — confirm dialog)
- **K.6** "Exit" version history
- **L.1** Open user profile from blip avatar (modal)
- **L.5** Previous / Next participant nav within profile modal

Out of scope (other slices / features own them):
- L.2 "Send Message" → 1:1 wave (F-3)
- L.3 "Edit Profile" (F-0)
- L.4 Close profile modal — covered by F-0 modal close behavior; this slice piggybacks on it.
- Search rail / search-help (S3, #1047)
- Wave-chrome wave-nav-row / focus-frame / depth-nav-bar (S2, #1046)
- URL state + read state (S5)
- Demo route (S6)

Quality bar: **No "practical parity" escape hatch.** Every cited inventory affordance is reachable on `?view=j2cl-root` and asserted by a Lit unit test for its element + a per-row server-side parity assertion for the markup contract.

## 1. Why this slice exists

S1 shipped `<wave-blip>` (the per-blip read surface) and the `wave-blip-profile-requested` CustomEvent fired by an avatar click. S2 / S3 cover the always-on chrome (nav-row, focus-frame, depth-nav-bar, search rail). S4 picks up the **transient and overlay surfaces**:

1. **Floating + accessory controls (J.2–J.5)** — the wave-level transient affordances that float above the surface. They are pure chrome, but every one of them is a regression risk on mobile breakpoints today because the GWT path renders them via inline styles in the legacy `WavePanelResourceLoader` CSS.
2. **`<wavy-version-history>` overlay (K.1–K.6)** — a full-bleed modal with a time-slider playhead, two toggles ("Show changes", "Text only"), a destructive "Restore" action, and an "Exit" close. This is the riskiest element in the slice because the GWT version-history seam (`PlaybackController` / `wavelet snapshot at version`) must be **consumed unchanged** — we do not refactor the seam.
3. **`<wavy-profile-overlay>` scaffolding (L.1, L.5)** — mounts a modal positioned by the `wave-blip-profile-requested` CustomEvent that S1 already emits. L.2 (Send Message) is owned by F-3; L.3 (Edit Profile) is owned by F-0; L.4 (close) reuses the F-0 modal-close behavior. S4 only mounts the modal scaffolding, wires the prev/next participant nav (L.5), and proves the open path (L.1). The body of the modal is delegated to F-0 / F-3 via named slots.

The S4 deliverables touch **different files** than S2 (#1046) and S3 (#1047) so the three slices can land in any order without merge conflicts. Per the brief:
- **S4** owns `j2cl/lit/src/elements/wavy-version-history.js`, `wavy-profile-overlay.js`, and four floating-control elements (`wavy-floating-scroll-to-new.js`, `wavy-wave-controls-toggle.js`, `wavy-nav-drawer-toggle.js`, `wavy-back-to-inbox.js`); plus the per-row sibling parity test.
- **S2** owns `wavy-wave-nav-row`, `wavy-focus-frame`, `wavy-depth-nav-bar`.
- **S3** owns `wavy-search-rail`, `wavy-search-help-modal`, `wavy-wave-panel-header`.

The version-history slider needs server-rendered shell markup (an empty `<wavy-version-history hidden>` mount point + the version-list link) so Lit upgrade has somewhere to attach. We add that to `renderJ2clRootShellPage`. Slider data wiring (calling the existing `getHistory` seam) is a **client-side pull** and does not refactor the seam — the slider element exposes a `loadVersions(loaderFn)` hook the renderer wires up, with an inert no-op loader by default for safe rollout.

## 2. Verification ground truth (re-derived in worktree)

Citations re-grepped on 2026-04-26 against `origin/main` post-#1045 (sha `f4a6cd6f`).

### Server-side (J2CL root shell)

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3321-3461` — `renderJ2clRootShellPage` already links `wavy-tokens.css` and the shell bundle. **S4 adds**: a `<wavy-version-history hidden>` mount node, a `<wavy-profile-overlay hidden>` mount node, a floating-control `<wavy-floating-scroll-to-new hidden>` pill mount, a `<wavy-wave-controls-toggle>` button stub in the shell header context, and a `<wavy-back-to-inbox>` button + `<wavy-nav-drawer-toggle>` button in the shell-header chrome region (these last two are mobile-only via CSS).
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/J2clSelectedWaveSnapshotRenderer.java` — the per-wave server snapshot renderer; we do **not** modify it.
- The legacy GWT path (`?view=gwt`) must continue to NOT load any of the new S4 elements — the parity reciprocal asserts none of `wavy-version-history`, `wavy-profile-overlay`, or `wavy-floating-scroll-to-new` strings appear.

### Server-side (version-history seam — consume only)

- `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveletProvider.java:getHistory` — the existing wire seam GWT version playback already consumes; S4 **does not** modify the interface. Wiring the slider to actually fetch history is left to a follow-up (filed if seam needs adjustment); for now the slider element accepts a JS `versionLoader` callback (a `(rangeStart, rangeEnd) => Promise<Version[]>`) with a no-op default. The mount point + the slider chrome + the toggles + the Restore confirm dialog all ship in S4 and are asserted; the data fetch is gated behind `versionLoader` being supplied.

### J2CL/Lit client-side

- `j2cl/lit/src/elements/wave-blip.js:225` — already emits `wave-blip-profile-requested` on avatar click with `{detail: {blipId, authorId}}`. S4's `<wavy-profile-overlay>` listens for this event on `document` (bubbles + composed) and opens itself.
- `j2cl/lit/src/index.js` — entry point; S4 imports the new elements here so the shell bundle picks them up.
- `j2cl/lit/src/design/wavy-tokens.css` — the F-0 token CSS providing `--wavy-signal-cyan`, `--wavy-bg-base`, `--wavy-text-body`, `--wavy-radius-pill`, `--wavy-radius-card`, `--wavy-spacing-2..5`, `--wavy-motion-focus-duration`, `--wavy-easing-focus`, `--wavy-focus-ring`. The S4 elements consume these names verbatim.
- `j2cl/lit/src/design/wavy-edit-toolbar.js`, `wavy-rail-panel.js`, `wavy-blip-card.js`, `wavy-pulse-stage.js`, `wavy-depth-nav.js`, `wavy-compose-card.js` — F-0 elements; S4 does not modify them. The new S4 elements live alongside.
- `j2cl/lit/src/elements/shell-header.js`, `shell-nav-rail.js`, `shell-status-strip.js`, `shell-skip-link.js`, `shell-main-region.js` — existing shell chrome elements; S4 does **not** modify them. The floating-controls live in their own elements that the renderer mounts in the shell-main-region's named slot.
- `j2cl/lit/test/wave-blip.test.js:157` — S1 already asserts the avatar profile event. S4's `<wavy-profile-overlay>` test will simulate dispatching the same event on `document` and assert the overlay opens.

### Per-row parity fixture

- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageOneReadSurfaceParityTest.java` is the S1 fixture. S4 adds a sibling test class `J2clStageOneFloatingOverlaysParityTest` in the same package so the umbrella `?view=j2cl-root` markup is asserted for the new mount points without bloating the S1 file.

### GWT side (reference only — for shape sanity)

- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/NewBlipIndicatorPresenter.java:38-200` — the GWT scroll-to-new-blips floating pill: appends a `<div class="pill">` with `role="button"`, `aria-label="Scroll to new messages"`, `tabindex` toggling on visibility, scroll-listener dismissal when near bottom (50px). The S4 Lit element mirrors the same a11y contract: `role="button"`, `aria-label`, `tabindex` toggled with the `hidden` attribute, click → emits `wavy-scroll-to-new-clicked` event the renderer wires to its scroller.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/ParticipantController.java`, `ParticipantSelectorWidget.java` — show that GWT renders participant profiles via a separate widget. The S4 profile overlay mirrors the **read-only** view (avatar, display name, participant id) and emits events for L.2 / L.3 the future slices fill in.

## 3. Acceptance contract (row-level)

Every claimed affordance maps to a Lit unit test assertion + a per-row server-side parity assertion (where the server-side mount/marker is meaningful) + an a11y assertion (role, aria-label, focusable when visible).

### 3.1 J.2 — Scroll-to-new-messages floating pill (`<wavy-floating-scroll-to-new>`)

**Element:** `j2cl/lit/src/elements/wavy-floating-scroll-to-new.js`

**Properties:**
- `count: number` (attribute `count`, reflects) — number of new messages below the fold.
- `hidden: boolean` (HTML standard) — pill is hidden when no unread.

**Visual:**
- `position: fixed; bottom: var(--wavy-spacing-5); right: var(--wavy-spacing-5);`
- Background `var(--wavy-signal-cyan)`, color `var(--wavy-bg-base)`, `border-radius: var(--wavy-radius-pill)`.
- Focus ring: `var(--wavy-focus-ring)` on `:focus-visible`.

**A11y:**
- `role="button"`, `tabindex="0"` when visible, `tabindex="-1"` when hidden, `aria-label="Scroll to new messages"`.

**Interaction:**
- Click + Enter + Space → emits `wavy-scroll-to-new-clicked` (`{detail: {count}}`) bubbles + composed. Renderer / shell consumer wires it to the scroller.
- When `count === 0` and not explicitly visible-overridden → adds `hidden` and removes from tab order.
- When `count > 0` → removes `hidden`, sets `tabindex="0"`, label content "↓ {count} new" (with screen-reader-only "Scroll to new messages" description).

**Lit unit tests (`j2cl/lit/test/wavy-floating-scroll-to-new.test.js`):**
1. Defines the `wavy-floating-scroll-to-new` custom element.
2. Hidden by default when `count === 0`.
3. Visible (no `hidden`) when `count > 0`; renders the count.
4. Click → fires `wavy-scroll-to-new-clicked` with detail `{count}`.
5. Enter key → fires the same event.
6. Space key → fires the same event.
7. `role="button"` + `aria-label="Scroll to new messages"` always present.
8. When hidden, `tabindex === "-1"`.
9. When visible, `tabindex === "0"`.

**Server-side parity (sibling fixture):**
- `?view=j2cl-root` HTML contains `<wavy-floating-scroll-to-new` with `hidden` attribute (initial state — no unread).
- `?view=gwt` HTML does NOT contain the string `wavy-floating-scroll-to-new`.

### 3.2 J.3 — Hide/Show wave controls toggle (`<wavy-wave-controls-toggle>`)

**Element:** `j2cl/lit/src/elements/wavy-wave-controls-toggle.js`

**Properties:**
- `pressed: boolean` (attribute `pressed`, reflects) — when `true`, controls are hidden ("compact mode"). Maps to the inventory's "Hide wave controls" / "Show wave controls" pair.

**Visual:** small icon button using `--wavy-text-muted` → `--wavy-signal-cyan` on `pressed` state.

**A11y:**
- `role="button"`, `aria-pressed="true|false"`, `aria-label` flips between `"Hide wave controls"` and `"Show wave controls"` based on `pressed`.
- Keyboard: Enter + Space toggle.

**Interaction:**
- Click / Enter / Space → toggles `pressed` and emits `wavy-wave-controls-toggled` (`{detail: {pressed}}`) bubbles + composed.

**Lit unit tests:**
1. Defines the element.
2. Default `pressed === false`, `aria-pressed === "false"`, label = "Hide wave controls".
3. Click toggles to `pressed === true`, `aria-pressed === "true"`, label = "Show wave controls".
4. Enter key toggles.
5. Space key toggles.
6. Toggle emits `wavy-wave-controls-toggled` with the new `pressed` value.

**Server-side parity:**
- `?view=j2cl-root` HTML contains `<wavy-wave-controls-toggle`.
- `?view=gwt` does not.

### 3.3 J.4 — Open / close navigation drawer (`<wavy-nav-drawer-toggle>`)

**Element:** `j2cl/lit/src/elements/wavy-nav-drawer-toggle.js`

**Properties:**
- `open: boolean` (attribute `open`, reflects). Default `false`.

**Visual:** hamburger / close icon depending on `open`. Mobile-only visibility (CSS `@media (max-width: 860px)`).

**A11y:**
- `role="button"`, `aria-expanded="true|false"`, `aria-controls` (string id of the drawer; renderer-supplied via `aria-controls` attribute).
- `aria-label` flips: `"Open navigation drawer"` ↔ `"Close navigation drawer"`.
- Keyboard: Enter + Space toggle.

**Interaction:**
- Click / Enter / Space → toggles `open`, emits `wavy-nav-drawer-toggled` (`{detail: {open}}`).

**Lit unit tests:**
1. Defines element.
2. Default `open === false`, `aria-expanded === "false"`, label = "Open navigation drawer".
3. Click toggles → `open === true`, `aria-expanded === "true"`, label = "Close navigation drawer".
4. Enter / Space toggle.
5. Emits `wavy-nav-drawer-toggled` with `open` value.

**Server-side parity:**
- `?view=j2cl-root` HTML contains `<wavy-nav-drawer-toggle`.
- `?view=gwt` does not.

### 3.4 J.5 — Back to inbox (`<wavy-back-to-inbox>`)

**Element:** `j2cl/lit/src/elements/wavy-back-to-inbox.js`

**Properties:**
- `href: string` (attribute `href`) — the URL to navigate to. Default `"#inbox"`.

**Visual:** anchor styled as a header back button; mobile-only visibility (CSS `@media (max-width: 860px)`); on desktop, the inventory says it does not appear.

**A11y:**
- Renders an `<a>` (or `role="link"` button if `href` not set) with `aria-label="Back to inbox"`.
- Keyboard navigates via the standard anchor behavior.

**Interaction:**
- Click → also emits `wavy-back-to-inbox-clicked` (no detail) bubbles + composed so the renderer can intercept (e.g. for client-side routing) **before** the link follows. (Non-cancelling intent: emit, then default link traversal.)

**Lit unit tests:**
1. Defines element.
2. Renders a single `<a>` with `aria-label="Back to inbox"` + the supplied `href` (default `#inbox`).
3. Click fires `wavy-back-to-inbox-clicked`.
4. When `href` is unset, falls back to `#inbox`.

**Server-side parity:**
- `?view=j2cl-root` HTML contains `<wavy-back-to-inbox`.
- `?view=gwt` does not.

### 3.5 K.1–K.6 — Version-history overlay (`<wavy-version-history>`)

**Element:** `j2cl/lit/src/elements/wavy-version-history.js`

This is the heaviest element in S4. It owns 6 affordances under one custom element to keep the modal a single mount point. Every affordance has its own assertion.

**Properties:**
- `open: boolean` (attribute `open`, reflects) — overlay visibility.
- `versions: Array<{ index: number, label: string, timestamp: string }>` (property; default `[]`) — the time-slider rail's points.
- `value: number` (attribute `value`, reflects) — current playhead index 0..N. Default `0`.
- `showChanges: boolean` (attribute `show-changes`, reflects) — K.3.
- `textOnly: boolean` (attribute `text-only`, reflects) — K.4.
- `restoreEnabled: boolean` (attribute `restore-enabled`, reflects) — K.5 gate. Default `false`. Renderer flips to `true` only after wiring a real `versionLoader`.
- `versionLoader: (rangeStart, rangeEnd) => Promise<Version[]>` (property; default no-op) — async hook the renderer wires up. The slider calls this in `firstUpdated` if set; otherwise stays inert.

**Visual (K.1):**
- Full-bleed dark wash. `position: fixed; inset: 0; background: rgba(11, 19, 32, 0.84);` (dark base with alpha — same shade as `--wavy-bg-base` token).
- Modal panel centered with `--wavy-radius-card`, padding `--wavy-spacing-5`, border `var(--wavy-border-hairline)`.
- Hidden when `open === false` (via the standard `hidden` attribute on the host's outer wrapper, NOT on the host itself, so reattach is fast).

**Time slider (K.2):**
- `<input type="range" min="0" max="N">` styled with the wavy signal-cyan playhead handle. Uses CSS `accent-color: var(--wavy-signal-cyan)` and a styled track via `::-webkit-slider-runnable-track` / `::-moz-range-track`.
- Emits `wavy-version-changed` (`{detail: {index, version}}`) on slider input.
- `aria-label="Version history time slider"`, `aria-valuemin/max/now` reflect.

**Show changes (K.3):**
- `<button type="button" aria-pressed=…>Show changes</button>` toggles `showChanges`. Emits `wavy-show-changes-toggled` (`{detail: {showChanges}}`).

**Text only (K.4):**
- `<button type="button" aria-pressed=…>Text only</button>` toggles `textOnly`. Emits `wavy-text-only-toggled` (`{detail: {textOnly}}`).

**Restore (K.5):**
- `<button class="restore">Restore</button>` styled red/destructive (color `var(--wavy-signal-red, #ef4444)`).
- Click → opens an inline confirm dialog (`<dialog>` element) with "Restore version {label}? This rewrites the wave to this point." and Cancel / Restore buttons. The Restore action emits `wavy-version-restore-confirmed` (`{detail: {index, version}}`); cancel just closes the inline dialog. The inner confirm dialog uses the native `<dialog>` element (closest GWT parity).
- The inline confirm dialog is keyboard-trapped via `<dialog>`'s native `showModal()` semantics (no extra focus-trap code).
- **Restore is gated by `restoreEnabled` (boolean property, default `false`).** When `restoreEnabled === false`, the Restore button reflects `aria-disabled="true"` + the standard `disabled` attribute, click is a no-op, and a small `"Preview only — restore not available"` hint renders next to it. The renderer flips `restoreEnabled = true` only when it has a real `versionLoader` wired (see §4 T5 + the §5 risk on version-history wiring). This prevents the destructive action from emitting into a void in the no-op rollout state.

**Exit (K.6):**
- `<button class="exit" aria-label="Exit version history">×</button>` in the modal's top-right corner.
- Click + Escape key (when `open`) → sets `open = false`, emits `wavy-version-history-exited`.
- Escape only fires when the host itself or a descendant is focused (overlay traps focus into itself when `open === true` via `inert` on `body > *:not(this)` is **explicitly NOT done** — too invasive for S4; a follow-up can wire it. The `<dialog>` element used for the confirm is the only modal-trapped surface in S4).

**A11y:**
- `role="dialog"`, `aria-modal="true"`, `aria-label="Version history"` on the host when `open`.
- When `open === false`, the host carries `aria-hidden="true"` (and the `hidden` attribute, suppressing rendering and tab stops).

**Public method:** `open()` and `close()` (also reflected via the `open` attribute) so the renderer / a future S5 controller can drive it.

**Lit unit tests (`j2cl/lit/test/wavy-version-history.test.js`):**
1. Defines element.
2. Default `open === false`; host carries `hidden`.
3. `open()` method → host loses `hidden`, gains `aria-modal="true"`, `role="dialog"`.
4. Renders a slider `<input type="range">` with `aria-label="Version history time slider"`.
5. Slider input updates `value` and emits `wavy-version-changed` with `{index, version}` derived from `versions[index]`.
6. Default `aria-valuemin === "0"`, `aria-valuemax` reflects the configured `versions.length - 1` (when `versions` is set).
7. "Show changes" toggle button: default `aria-pressed === "false"`; click → `showChanges === true`, `aria-pressed === "true"`, emits `wavy-show-changes-toggled`.
8. "Text only" toggle button: default `aria-pressed === "false"`; click → emits `wavy-text-only-toggled`.
9. **Restore gate (default).** With default `restoreEnabled === false`, the Restore button carries `aria-disabled="true"` + `disabled`; click is a no-op (asserts no `wavy-version-restore-confirmed` event fires); the "Preview only — restore not available" hint renders.
10. **Restore enabled.** Setting `restoreEnabled = true` removes `aria-disabled` + `disabled`, removes the hint, and click → opens the inline confirm `<dialog>` (asserts `dialog[open]` querySelector exists on the rendered shadow tree).
11. Confirm dialog "Restore" inner button → emits `wavy-version-restore-confirmed` with `{index, version}` and closes the dialog.
12. Confirm dialog "Cancel" inner button → closes the dialog without emitting `wavy-version-restore-confirmed`.
13. Exit "×" button (K.6) → sets `open === false`, emits `wavy-version-history-exited`.
14. Escape key on host while `open` → exits.
15. `versionLoader` callback invoked once on first open (asserts callback fires with `(0, ∞)` shaped args).

**Server-side parity (sibling fixture):**
- `?view=j2cl-root` HTML contains `<wavy-version-history` with `hidden` attribute (initial state — closed).
- `?view=gwt` does not.

### 3.6 L.1 + L.5 — Profile overlay (`<wavy-profile-overlay>`)

**Element:** `j2cl/lit/src/elements/wavy-profile-overlay.js`

S4 mounts the modal scaffolding only. The body content is delegated to slots so F-0 (L.3 Edit Profile) and F-3 (L.2 Send Message) can fill them later.

**Properties:**
- `open: boolean` (attribute `open`, reflects).
- `participants: Array<{ id: string, displayName: string, avatarUrl?: string }>` (property; default `[]`).
- `index: number` (attribute `index`, reflects) — currently shown participant. Default `0`.

**Visual:**
- Modal centered, `--wavy-radius-card`, dark wash backdrop.
- Avatar (large), display name, participant id below.
- Prev / Next nav buttons with `←` / `→` glyphs at left/right edges of the modal.
- Slot named `actions` for L.2 + L.3 to mount their buttons in a future slice; S4 leaves the slot empty.

**A11y:**
- `role="dialog"`, `aria-modal="true"`, `aria-labelledby` pointing to the display-name `<h2 id>` so screen readers announce the participant.
- Hidden via `hidden` attribute when `open === false`.

**Open path (L.1):**
- Listens on `document` for `wave-blip-profile-requested` (the S1 event from the avatar click). Detail: `{blipId, authorId}`.
- On receipt: sets `open = true`, sets `index` to the matching participant if `participants` includes a match for `authorId`, else falls back to index `0` and lets the renderer hydrate `participants` later.
- Re-fires `wavy-profile-overlay-opened` (`{detail: {authorId}}`) so consumers can hook in.

**Prev / Next (L.5):**
- `<button aria-label="Previous participant">` + `<button aria-label="Next participant">` cycle `index` through `participants` (clamped, no wrap on the first/last — Prev is disabled at 0; Next is disabled at length-1).
- Each click emits `wavy-profile-participant-changed` (`{detail: {index, participant}}`).
- Keyboard: Left / Right arrows when overlay focused fire the same events.

**Public methods:** `open(index)` and `close()`.

**Close (L.4 — reuses F-0 modal close behavior):**
- Escape key + an Exit "×" button (`aria-label="Close profile"`) close the overlay.
- The inventory marks L.4 as F-0-owned. S4 still renders the close affordance (cannot ship a modal you can't dismiss); the F-0 contribution is the *style* of the close button, which we draw from the same wavy tokens. No event is emitted on close beyond `wavy-profile-overlay-closed`.

**Lit unit tests (`j2cl/lit/test/wavy-profile-overlay.test.js`):**
1. Defines element.
2. Default `open === false`; host carries `hidden`.
3. Receiving a `wave-blip-profile-requested` CustomEvent on `document` opens the overlay (`open === true`, `hidden` removed).
4. With `participants` populated, opening with a matching `authorId` sets `index` to that participant.
5. Opening with an unknown `authorId` falls back to `index === 0`.
6. Opens fires `wavy-profile-overlay-opened` with the `{authorId}` from the trigger event.
7. Prev button disabled at `index === 0`, enabled otherwise.
8. Next button disabled at `index === participants.length - 1`, enabled otherwise.
9. Click Next → `index += 1`, emits `wavy-profile-participant-changed`.
10. Click Prev → `index -= 1`, emits `wavy-profile-participant-changed`.
11. ArrowRight key on host → Next (when not at end).
12. ArrowLeft key on host → Prev (when not at start).
13. Close "×" button → `open === false`, emits `wavy-profile-overlay-closed`.
14. Escape key while open → close.
15. Renders the named slot `actions` (asserts `slot[name="actions"]` exists in shadow root).

**Server-side parity (sibling fixture):**
- `?view=j2cl-root` HTML contains `<wavy-profile-overlay` with `hidden` attribute.
- `?view=gwt` does not.

## 4. Tasks (T1–T6)

Each task lands as one logical unit (≤ ~250 LOC of production code + paired tests). Verification runs after each task.

### T1 — `<wavy-floating-scroll-to-new>` (J.2)

**Files:**
- `j2cl/lit/src/elements/wavy-floating-scroll-to-new.js` (new, ~110 LOC)
- `j2cl/lit/test/wavy-floating-scroll-to-new.test.js` (new, ~120 LOC)
- `j2cl/lit/src/index.js` (add import line, +1 LOC)

**Verify:** `sbt -batch j2clLitTest`.

### T2 — Wave-controls + nav-drawer + back-to-inbox accessory triplet (J.3, J.4, J.5)

The three accessory toggles share an identical structural pattern (boolean-state button + a11y reflection + Enter/Space/click → emit + visual flip), so they ship as one task to keep the diff coherent. Total task LOC (~520 prod + tests) is above the soft "~250 LOC per task" guideline; this is a deliberate exception because splitting the triplet across three trivial PRs creates more review overhead than it saves.

**Files:**
- `j2cl/lit/src/elements/wavy-wave-controls-toggle.js` (new, ~90 LOC)
- `j2cl/lit/src/elements/wavy-nav-drawer-toggle.js` (new, ~90 LOC)
- `j2cl/lit/src/elements/wavy-back-to-inbox.js` (new, ~80 LOC)
- `j2cl/lit/test/wavy-wave-controls-toggle.test.js` (new, ~90 LOC)
- `j2cl/lit/test/wavy-nav-drawer-toggle.test.js` (new, ~90 LOC)
- `j2cl/lit/test/wavy-back-to-inbox.test.js` (new, ~80 LOC)
- `j2cl/lit/src/index.js` (add 3 import lines)

**Verify:** `sbt -batch j2clLitTest`.

### T3 — `<wavy-version-history>` overlay (K.1–K.6)

**Files:**
- `j2cl/lit/src/elements/wavy-version-history.js` (new, ~250 LOC)
- `j2cl/lit/test/wavy-version-history.test.js` (new, ~250 LOC)
- `j2cl/lit/src/index.js` (add import line)

**Verify:** `sbt -batch j2clLitTest`.

### T4 — `<wavy-profile-overlay>` scaffolding (L.1, L.5)

**Files:**
- `j2cl/lit/src/elements/wavy-profile-overlay.js` (new, ~220 LOC)
- `j2cl/lit/test/wavy-profile-overlay.test.js` (new, ~250 LOC)
- `j2cl/lit/src/index.js` (add import line)

**Verify:** `sbt -batch j2clLitTest`.

### T5 — Server-rendered mount points in `renderJ2clRootShellPage`

**Files:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` — append six mount points (J.2 pill, J.3 toggle, J.4 drawer toggle, J.5 back link, K version-history host, L profile-overlay host) inside the J2CL root shell HTML, immediately AFTER `</shell-root>` and BEFORE `<script src=… j2cl-sidecar.js>` so they live as siblings of `<shell-root>` (their `position: fixed` styling means DOM order does not affect visual placement; siblinghood keeps them out of `<shell-root>`'s CSS Grid layout). The same six mount points are NOT added to the signed-out branch (`shell-root-signed-out`) — these affordances only matter inside an open wave.

The new HTML chunk added is roughly:

```html
<wavy-back-to-inbox href="#inbox"></wavy-back-to-inbox>
<wavy-nav-drawer-toggle aria-controls="shell-nav-drawer"></wavy-nav-drawer-toggle>
<wavy-wave-controls-toggle></wavy-wave-controls-toggle>
<wavy-floating-scroll-to-new hidden></wavy-floating-scroll-to-new>
<wavy-version-history hidden></wavy-version-history>
<wavy-profile-overlay hidden></wavy-profile-overlay>
```

All carry `data-j2cl-floating-mount="true"` so the parity fixture has a single attribute to count for the "all 6 mount points present" assertion.

**Verify:** `sbt -batch j2clProductionBuild` (sanity that the shell bundle still builds since index.js now imports six new modules) and `sbt -batch j2clLitTest`.

### T6 — Per-row parity sibling fixture

**Files:**
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageOneFloatingOverlaysParityTest.java` (new, ~200 LOC). Mirrors the S1 fixture's helpers (mock servlet + provider) but asserts **only** the S4 mount points on `?view=j2cl-root` and the reciprocal absence on `?view=gwt`.

**Test methods (one per affordance + reciprocal):**
1. `j2clRootShellMountsScrollToNewFloatingPill` — asserts `<wavy-floating-scroll-to-new` + `hidden` attribute.
2. `j2clRootShellMountsWaveControlsToggle` — asserts `<wavy-wave-controls-toggle`.
3. `j2clRootShellMountsNavDrawerToggle` — asserts `<wavy-nav-drawer-toggle` + `aria-controls=`.
4. `j2clRootShellMountsBackToInbox` — asserts `<wavy-back-to-inbox`.
5. `j2clRootShellMountsVersionHistoryOverlay` — asserts `<wavy-version-history` + `hidden`.
6. `j2clRootShellMountsProfileOverlay` — asserts `<wavy-profile-overlay` + `hidden`.
7. `j2clRootShellMountsAllSixFloatingControls` — asserts `data-j2cl-floating-mount="true"` count == 6.
8. `legacyGwtRouteOmitsAllSixFloatingControls` — asserts `?view=gwt` HTML contains none of the new strings.

**Verify:** `sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild`.

## 4a. Inventory cross-walk (sections J / K / L)

Confirms the S4 affordance list is exhaustive for the F-2-owned subset of these sections.

| Inv. row | Affordance | Owner | Slice |
| --- | --- | --- | --- |
| J.1 | "Click here to reply" inline composer | F-3 | n/a |
| J.2 | Scroll to new messages floating pill | F-2 | **S4** |
| J.3 | Hide / Show wave controls | F-2 | **S4** |
| J.4 | Open / close navigation drawer | F-2 | **S4** |
| J.5 | Back to inbox | F-2 | **S4** |
| K.1 | Version history overlay | F-2 | **S4** |
| K.2 | Time slider with playhead | F-2 | **S4** |
| K.3 | Show changes toggle | F-2 | **S4** |
| K.4 | Text only toggle | F-2 | **S4** |
| K.5 | Restore action | F-2 | **S4** |
| K.6 | Exit version history | F-2 | **S4** |
| L.1 | Open user profile from blip avatar | F-2 | **S4** |
| L.2 | Send Message → 1:1 wave | F-3 | n/a (S4 mounts the slot) |
| L.3 | Edit Profile (own avatar) | F-0 | n/a (S4 mounts the slot) |
| L.4 | Close profile modal | F-0 | n/a (S4 ships tactical close pending F-0 restyle) |
| L.5 | Previous / Next participant | F-2 | **S4** |

The inventory has no rows after J.5, K.6, or L.5 (file at `docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md` lines 199–229). S4 claims **all 12 F-2-owned affordances** in these sections, which is the complete F-2 set under sections J / K / L.

## 4b. Shared-file merge strategy with S2 / S3

Two files are touched by all three of S2 / S3 / S4:

1. `j2cl/lit/src/index.js` — each slice appends import lines.
2. `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` — each slice appends mount points to the signed-in branch of `renderJ2clRootShellPage`.

**Strategy:**
- Each slice's imports + mounts append at the **end** of their respective sections in lexical-by-element-name order, so diffs stay clean.
- The last-to-merge slice rebases on `origin/main` and resolves the trivial append conflict by taking both sides.
- S4 imports are placed alphabetically among the other `wavy-*` imports inside the F-2 block; S4 mounts go in a single new line block delimited by `// F-2.S4 (#1048): floating + overlay mount points` so future slices (S5/S6) can append without scanning.

## 5. Risks + mitigations

- **Version-history slider data wiring** — the inventory says K.5 Restore is destructive; we cannot ship Restore wired to a real loader without the seam being audit-clean. **Mitigation:** the `versionLoader` callback defaults to a no-op AND `restoreEnabled` defaults to `false` so the renderer ships with the slider chrome, the toggles, and the Exit affordance but the destructive Restore is gated. The slider element is fully tested in isolation; the wiring is a single line in the renderer. **A follow-up issue WILL be filed at PR-open time** (not "if needed") to wire `WaveletProvider.getHistory` consumption + flip `restoreEnabled = true` once the seam audit is complete; the changelog references the follow-up issue number so the inert state has a tracked exit.
- **Profile overlay / blip-avatar event coupling** — the S1 element emits the event with `bubbles: true, composed: true`. The S4 overlay listens on `document` so the cross-shadow-root crossing works. **Mitigation:** unit tests dispatch the event on `document` and assert the overlay opens; the contract is asserted both sides.
- **Floating element CSS layering** — pill + version-history + profile-overlay all use `position: fixed`. Z-index ordering: pill = 100, profile-overlay = 200, version-history = 300 (highest). Documented in each element's `static styles`.
- **Mobile-only visibility** — J.4 + J.5 are mobile-only per the inventory. We use a `@media (max-width: 860px)` rule on the host so the desktop layout is unchanged. Tests assert the elements always render in the shadow tree (visibility is a CSS concern, not a structural one).
- **Inventory L.4 / L.2 / L.3 ownership** — L.4 (close) is F-0-owned but a modal must close; we ship a tactical close on the overlay using the wavy tokens and a `wavy-profile-overlay-closed` event. L.2 + L.3 are slotted (`<slot name="actions">`) so future slices can fill them without a re-rev.
- **Bundle size** — six new elements total ~840 LOC of production code. esbuild minifies + tree-shakes; sanity-check with `j2clProductionBuild` that the shell bundle still loads.
- **chatgpt-codex / CodeRabbit cycle** — budget for 2-3 fix passes per the brief.

## 6. Verification (must pass before opening PR)

1. `sbt -batch j2clLitTest` — all new tests pass.
2. `sbt -batch j2clSearchTest` — search sidecar suite still green.
3. `sbt -batch j2clProductionBuild` — production J2CL sidecar build emits the shell bundle including the six new modules.
4. `sbt -batch "jakartaTest:testOnly *J2clStageOneFloatingOverlaysParityTest"` — sibling parity fixture green.
5. The S1 `J2clStageOneReadSurfaceParityTest` still passes (we only ADD to the shell HTML, never remove).

## 7. Out of scope (for later slices / follow-ups)

- Wiring the version-history `versionLoader` to a real `WaveletProvider.getHistory` call (filed as a follow-up if the seam needs work).
- L.2 "Send Message" (F-3 fills the `actions` slot).
- L.3 "Edit Profile" (F-0 fills the `actions` slot).
- Server-driven `count` for the scroll-to-new pill (S5 wires unread state to the pill's `count` attribute).
- URL-state for the version-history overlay (S5 owns `&v=<version>`).
- Demo route updates (S6).

## 8. Definition of done

- All six new Lit elements registered in `j2cl/lit/src/index.js` and bundled by esbuild.
- Six server-rendered mount points present in `?view=j2cl-root` and absent from `?view=gwt`.
- Lit unit-test counts (per §3): J.2 = 9, J.3 = 6, J.4 = 6, J.5 = 5, K = 15, L = 15. Total = **56** Lit assertions across the six elements.
- 8 server-side parity assertions in the sibling fixture.
- `sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild` exits 0.
- Changelog entry committed to `wave/config/changelog.d/2026-04-26-issue-1048-floating-version-history.json`. The changelog notes the version-history wiring follow-up issue (filed at PR time, see §5 risks) so the inert state has a tracked exit.
- PR opened with the title and body specified in the brief; auto-merge squash; monitored to merge.
