# F-2 Slice 2 — Wave chrome (`<wavy-wave-nav-row>` + `<wavy-focus-frame>` + `<wavy-depth-nav-bar>`)

Issue: [#1046](https://github.com/apache/incubator-wave/issues/1046).
Parent umbrella: [#1037](https://github.com/apache/incubator-wave/issues/1037) (F-2 StageOne read surface) — slice 2 of 6, **does NOT close umbrella**.
Tracker: [#904](https://github.com/apache/incubator-wave/issues/904).
Foundation: F-0 (#1035 / sha `af7072f9`), F-1 (#1036 / sha `86ea6b44`), F-2.S1 (#1045 / sha `f4a6cd6f`).

Parity-matrix rows claimed: **R-3.2** (focus framing), **R-3.3** (collapse motion wiring), **R-3.4** (E.1–E.10 wave nav row), **R-3.7-chrome** (G.2 + G.3 depth-nav-bar shell, no URL state — that's S5).

## 1. Why this plan exists

F-2.S1 (#1045) re-executed the read surface — it ships `<wave-blip>` + `<wave-blip-toolbar>` and threads them through `J2clReadSurfaceDomRenderer`. R-3.2 / R-3.3 / R-3.4 / R-3.7 are **partially** met (focus is tracked on the renderer but not bracketed by a `<wavy-focus-frame>` landmark; collapse animates with hard-coded values; nav buttons E.1–E.10 don't render at all; depth-nav scaffold exists at the F-0 recipe level but G.2/G.3 chrome controls don't).

This slice closes those gaps with **three new Lit elements** + a renderer extension that mounts them inside the selected-wave card. The plan is self-contained: it follows the audit (`docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md`) row-by-row, doesn't redefine S1's contract, and emits forward-only events that S5 will consume for URL state + telemetry counters.

## 2. Verification ground truth (re-derived in worktree)

Read freshly inside the worktree to avoid stale assumptions:

- `j2cl/lit/src/design/wavy-tokens.css` — F-0 token contract. Verified all named tokens in the issue brief exist (`--wavy-bg-base`, `--wavy-bg-surface`, `--wavy-border-hairline`, `--wavy-text-body`, `--wavy-text-muted`, `--wavy-signal-cyan`, `--wavy-signal-cyan-soft`, `--wavy-signal-violet`, `--wavy-signal-violet-soft`, `--wavy-signal-amber`, `--wavy-signal-amber-soft`, `--wavy-focus-ring`, `--wavy-pulse-ring`, `--wavy-motion-focus-duration: 180ms`, `--wavy-motion-collapse-duration: 240ms`, `--wavy-easing-focus`, `--wavy-easing-collapse`, `--wavy-spacing-1..8`, `--wavy-radius-card: 12px`, `--wavy-radius-pill: 9999px`).
- `j2cl/lit/src/elements/wave-blip.js` (374 LOC) — F-2.S1 wrapper. Reflects `data-blip-id`, `data-wave-id`, `unread`, `has-mention`, `focused`, `reply-count`. Already emits `wave-blip-reply-requested`, `wave-blip-edit-requested`, `wave-blip-link-copied`, `wave-blip-profile-requested`, `wave-blip-drill-in-requested`. **Notably absent**: `wave-blip-pin-requested` and `wave-blip-archive-requested` events — the S2 nav-row will dispatch these from the host; no change needed in `wave-blip` itself.
- `j2cl/lit/src/elements/wave-blip-toolbar.js` (108 LOC) — per-blip Reply / Edit / Link / overflow.
- `j2cl/lit/src/design/wavy-depth-nav.js` (80 LOC) — F-0 breadcrumb recipe (`crumbs: Array<{label, href?, current?}>`). The S2 `<wavy-depth-nav-bar>` will compose this recipe + add the G.2/G.3 buttons.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java` (1181 LOC) — owns `enhanceSurface`, `enhanceBlips`, `toggleThread`, `onBlipKeyDown` (currently handles ArrowUp/Down/Home/End). **S2 extension surface**: add `j`/`k` aliases for ArrowDown/ArrowUp; add a focus listener hook that toggles a class on a sibling `<wavy-focus-frame>` element when one is mounted; thread the `--wavy-motion-collapse-duration` CSS variable into the collapse class so collapse animates with the F-0 token.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java` (372 LOC) — owns the selected-wave card structure. **S2 extension surface**: insert a `<wavy-wave-nav-row>` element into the card header above `contentList`; insert a `<wavy-depth-nav-bar>` element above the title; write `data-j2cl-selected-wave-host=""` on the card root. The `<wavy-focus-frame>` is mounted by the renderer (not the view) inside its own `host` (`.j2cl-read-surface`). The depth indicator is read from `data-current-depth-blip-id` on the renderer's `host` (S5 will write that; S2 ships the reader path).
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageOneReadSurfaceParityTest.java` (485 LOC) — server-side parity fixture. **S2 extension surface**: add per-row assertions for E.1–E.10 (each button slug + ARIA label + correct token reference appears in the rendered surface), G.2 + G.3 (depth-nav-bar buttons with parent-author-label ARIA), and the focus-frame mount.
- `j2cl/lit/test/wave-blip.test.js` — pattern for new Lit element tests. `before(...)` ensures wavy-tokens.css is loaded; tests use `@open-wc/testing` `fixture(html\`...\`)` + `oneEvent(...)` + `aTimeout(...)`.
- `j2cl/lit/src/index.js` — registers Lit elements into the bundle. **S2 extension**: add three new imports for the new elements.

The deferred-work cross-cuts in S5 / S6:
- S5 owns: URL `&depth=` reader, depth-nav data binding (parent-author labels, breadcrumb stack), live-update awareness pill (G.6).
- S6 owns: `/_/j2cl-design` demo route mount + per-row fixture artifact.

## 3. Acceptance contract (row-level)

### R-3.2 — Focus framing (`<wavy-focus-frame>`)

**Hard acceptance**:
1. A new `<wavy-focus-frame>` Lit element is registered with `customElements.define("wavy-focus-frame", ...)`. It hosts no slotted children — it is a **landmark + visual frame** that paints a 2px cyan ring (`--wavy-focus-ring`) around the currently focused blip via an absolutely-positioned overlay element controlled by reactive `bounds` and `focusedBlipId` properties.
2. The renderer's `enhanceBlips` pass extends keyboard handling: `j` aliases `ArrowDown`, `k` aliases `ArrowUp`. (Issue #1046 R-3.2 cites only `j`/`k` as new shortcuts; `g`/`G` are explicitly **deferred to S5** alongside `[`/`]`. The existing `aria-keyshortcuts` attribute remains `"ArrowUp ArrowDown Home End"` — `j`/`k` are documented as Wavy-specific aliases in code comments only, not announced via ARIA, to avoid screen-reader collisions with global shortcuts.)
3. The renderer dispatches a `wavy-focus-changed` CustomEvent (bubbles, composed) on the renderer's `host` (= the `.j2cl-read-surface` element) with `detail: {blipId, bounds: {top, left, width, height}, key}` whenever `focusBlip(...)` runs. The `key` field is `""` when the change came from a non-keyboard source (e.g. `restoreFocusedBlipById`). `bounds` is always in **`host`-local coordinate space** (i.e. relative to the `.j2cl-read-surface` element, NOT contentList). The frame's positioning ancestor and the bounds-measurement element are the same node — `host` — so the math is `top = blip.getBoundingClientRect().top - host.getBoundingClientRect().top + host.scrollTop` (and analogous for `left`/`width`/`height`). The `<wavy-focus-frame>` element listens on its **renderer host** (the `.j2cl-read-surface` element) for `wavy-focus-changed`; it must therefore be a child of that element, NOT a sibling. (See T5 for the concrete DOM placement.)
4. Frame transition uses `transition: all var(--wavy-motion-focus-duration) var(--wavy-easing-focus)` (180ms ease-out per F-0). On `prefers-reduced-motion: reduce` the transition is set to `none`.
5. **Survives incremental updates**: when the renderer calls `restoreFocusedBlipById(...)` after a window swap, the existing focus-frame element is reused (not destroyed) and its `bounds` re-synced via the same `wavy-focus-changed` event. A new test `restores focus across window-swap rebuilds` mounts a 3-blip window, swaps to a 6-blip window with the same focused blip, and asserts the frame's `bounds` re-converge on the same blip without a destroy/recreate cycle (assert via `instanceof` identity check on the frame element).
6. Visible cyan ring contrast — covered by token: `--wavy-focus-ring` is `0 0 0 2px var(--wavy-signal-cyan)` which is `#22d3ee` on `#0b1320` (≥4.5:1 luminance ratio). Test asserts the computed `box-shadow` resolves to the **expected RGB string** `rgb(34, 211, 238) 0px 0px 0px 2px` (chromium resolves `var(--wavy-focus-ring)` to its concrete computed value; the test computes the expected RGB up front from the hex literal in `wavy-tokens.css`). A literal `.to.include('--wavy-focus-ring')` assertion would not work and is explicitly avoided.

**Telemetry**: emit `wave_chrome.focus_frame.transition` event with field `direction` (forward/backward/jump) and `key` (ArrowDown/j/...) per transition. Counter wired through `J2clClientTelemetry.Sink`.

### R-3.3 — Collapse motion wiring

**Hard acceptance**:
1. `J2clReadSurfaceDomRenderer.toggleThread` already toggles the `j2cl-read-thread-collapsed` class. **The class itself does not animate** today — verified: `enhanceThreads` adds `j2cl-read-thread` class but the project's CSS for `.j2cl-read-thread-collapsed` lives in the GWT-side stylesheet and is a binary `display:none`. The fix is to add token-driven CSS via the J2CL bundle.
2. Add a new design CSS file `j2cl/lit/src/design/wavy-thread-collapse.css` (NEW, ~+30 LOC) that defines `.j2cl-read-thread` + `.j2cl-read-thread-collapsed` height + opacity transitions per `--wavy-motion-collapse-duration` (240ms ease-in-out). The CSS file is wired into the esbuild config as a sibling asset (`wavy-thread-collapse.css`) and linked from the J2CL root shell page next to `wavy-tokens.css`.
3. Space/Enter on the collapse toggle button works (already does via the native `<button>` element + `addEventListener("click", ...)`). Add a focused test: `space and enter toggle the collapse button` in the renderer test that synthesizes both keys and asserts the `aria-expanded` attribute flips.
4. **Focus does not jump on expand**: extend `toggleThread` so when a thread is **expanded** (collapsed → expanded transition), the previously focused blip (if it was hidden by the collapse) is re-focused; if no previously focused blip was hidden, no focus change occurs. Today `focusNearestVisibleFrom` only runs on collapse — the symmetric path on expand is guarded by `isHiddenByCollapsedThread`. Add a new test `focus_does_not_jump_on_expand`.
5. **Read state preserved**: collapse is a pure DOM/CSS class flip — it does not alter `unread` markers or the supplement state. Existing test `restores collapsed thread state across re-render` already covers this; extend with an additional assertion that `data-blip-id` unread attribute survives.
6. Reduced-motion: collapse CSS includes a `@media (prefers-reduced-motion: reduce)` block setting transition to `none`.

**Telemetry**: emit `wave_chrome.thread_collapse.toggle` event with field `state` (collapsed/expanded) per click.

### R-3.4 — Wave nav row (`<wavy-wave-nav-row>`)

**Hard acceptance**: A new `<wavy-wave-nav-row>` Lit element renders **10 buttons** matching audit rows E.1–E.10. The element accepts these reactive properties:
- `unreadCount: Number` (drives E.2 cyan emphasis)
- `mentionCount: Number` (drives E.6/E.7 enabled state)
- `pinned: Boolean` (drives E.9 cyan glyph)
- `archived: Boolean` (drives E.8 enabled state)
- `selectedBlipId: String` (passed through in dispatched events as the focus anchor)

Per-button hard rows:

| Row | Button | ARIA label | Event emitted | Token-driven style |
| --- | --- | --- | --- | --- |
| **E.1** | "Recent" jump (history-back-1) | `Jump to recent activity` | `wave-nav-recent-requested` | `--wavy-text-muted` default |
| **E.2** | "Next Unread" | `Jump to next unread blip` | `wave-nav-next-unread-requested` | `--wavy-signal-cyan` when `unreadCount > 0`, muted otherwise |
| **E.3** | "Previous" | `Jump to previous blip` | `wave-nav-previous-requested` | `--wavy-text-muted` |
| **E.4** | "Next" | `Jump to next blip` | `wave-nav-next-requested` | `--wavy-text-muted` |
| **E.5** | "End" | `Jump to last blip` | `wave-nav-end-requested` | `--wavy-text-muted` |
| **E.6** | "Prev @" | `Jump to previous mention` | `wave-nav-prev-mention-requested` | `--wavy-signal-violet` when `mentionCount > 0` |
| **E.7** | "Next @" | `Jump to next mention` | `wave-nav-next-mention-requested` | `--wavy-signal-violet` when `mentionCount > 0` |
| **E.8** | "Archive" | `Move wave to archive` (or `Restore from archive` when `archived`) | `wave-nav-archive-toggle-requested` | `--wavy-text-muted` |
| **E.9** | "Pin" | `Pin wave` (or `Unpin wave` when `pinned`) | `wave-nav-pin-toggle-requested` | `--wavy-signal-cyan` when `pinned`, muted otherwise |
| **E.10** | "Version History" | `Open version history (H)` — keyboard `H` | `wave-nav-version-history-requested` | `--wavy-text-muted` |

- The element binds a `keydown` listener on the **selected-wave card root ancestor** (located via `closest('[data-j2cl-selected-wave-host]')` and falling back to `document` only if no ancestor matches) for the `H` shortcut (E.10). Binding to the card root (not `document`) prevents multi-fire when more than one nav-row mounts (e.g. demo route in S6, server-first card during the pre-swap window). The handler also bails when target is `INPUT`/`TEXTAREA`/`[contenteditable]` or any modifier (`ctrlKey`/`metaKey`/`altKey`) is pressed. Other keyboard shortcuts (`p`/`n`, etc.) are S5 — the row itself dispatches the event when its button is clicked, but the keyboard binding for those is the renderer's job. The `data-j2cl-selected-wave-host` attribute is written by T5/T5b on the `.sidecar-selected-card` root (see those tasks).
- The event detail object always includes `{selectedBlipId, sourceWaveId}` so S5 can wire to the model state.
- Layout uses `--wavy-spacing-2` gaps between buttons, `--wavy-radius-pill` border, `--wavy-bg-surface` background. Focus uses `--wavy-focus-ring`. Hover uses `--wavy-signal-cyan-soft`.
- Mobile collapse: the `:host` declares `container-type: inline-size; container-name: wave-nav-row;` so a `@container wave-nav-row (max-width: 480px)` query collapses E.6/E.7/E.10 into an overflow menu (`<button data-action="overflow">`). The overflow rendering itself is shipped (button + menu), even if the menu only has the 3 collapsed items — this preserves the contract. (The container declaration is critical: without `container-type: inline-size` the query never matches and the overflow path becomes dead code.)

**Per-blip wiring**: the nav row is mounted **once per wave panel** (not per blip), but dispatches walks against the per-blip rendered DOM via `selectedBlipId`. The audit explicitly called out that S1's wiring at the wave-list level was the bug — S2's nav row receives the focus anchor from the renderer's `wavy-focus-changed` event so it always operates on the current focused blip.

### R-3.7-chrome — Depth-nav bar (`<wavy-depth-nav-bar>`) — G.2 + G.3 only

**Hard acceptance**: A new `<wavy-depth-nav-bar>` Lit element composes the F-0 `<wavy-depth-nav>` recipe (for the breadcrumb crumbs) + adds two new chrome buttons (G.2 + G.3) and a placeholder host for G.6 (the live-update awareness pill, owned by S5).

| Row | Button | ARIA label | Event emitted | Visible when |
| --- | --- | --- | --- | --- |
| **G.2** | "Up one level" (chevron-up + parent author label) | `Up one level to {parentAuthorName}'s thread` (or `Up one level` when `parentAuthorName` is empty) | `wavy-depth-up` | `currentDepthBlipId !== ""` |
| **G.3** | "Up to wave" (top arrow) | `Back to top of wave` | `wavy-depth-root` | `currentDepthBlipId !== ""` |

- Properties: `currentDepthBlipId: String`, `parentDepthBlipId: String`, `parentAuthorName: String`, `crumbs: Array<{label, href?, current?, blipId?}>` (passed through to inner `<wavy-depth-nav>`), `unreadAboveCount: Number` (S5 will drive this; S2 reserves the property and always renders 0 when no value).
- The element is hidden via `hidden` attribute when `currentDepthBlipId === ""` AND `crumbs.length === 0` — at top-level the depth-nav bar adds no chrome.
- Layout: chevron-up button + author label on the left, breadcrumb in the middle, "Up to wave" (text + ⌃) on the right. Tokens: `--wavy-bg-surface` background, `--wavy-border-hairline` bottom border, `--wavy-spacing-3` padding.
- **Crumb click**: when a crumb in the inner `<wavy-depth-nav>` is clicked AND the crumb has a `blipId` field, the bar emits `wavy-depth-jump-to-crumb` with `detail: {blipId}`. (S5 consumes — same emit-now / wire-later pattern as the rest. The inner `<wavy-depth-nav>` recipe doesn't natively emit a click event today, so the bar binds a delegated `click` listener on its inner `<wavy-depth-nav>` element and walks back to the matching crumb by index.)
- Test: a fixture mounted with `currentDepthBlipId="b3"`, `parentAuthorName="Alice"` asserts `aria-label` includes `"Alice's thread"`, click on the up-one-level button dispatches `wavy-depth-up` with `detail: {fromBlipId: "b3", toBlipId: "<parentDepthBlipId>"}`.

**Notes for S5** (events S2 emits that S5 must wire):
- `wave-nav-recent-requested`, `wave-nav-next-unread-requested`, `wave-nav-previous-requested`, `wave-nav-next-requested`, `wave-nav-end-requested`, `wave-nav-prev-mention-requested`, `wave-nav-next-mention-requested`, `wave-nav-archive-toggle-requested`, `wave-nav-pin-toggle-requested`, `wave-nav-version-history-requested`
- `wavy-depth-up` (with `detail: {fromBlipId, toBlipId}`)
- `wavy-depth-root` (with `detail: {fromBlipId}`)
- `wavy-depth-jump-to-crumb` (with `detail: {blipId}`)
- `wavy-focus-changed` (with `detail: {blipId, bounds, key}`) — S5 may consume for URL anchor updates and `g`/`G`/`[`/`]` keyboard handlers

## 4. Implementation tasks

### T1 — `<wavy-focus-frame>` Lit element

**Files**:
- `j2cl/lit/src/elements/wavy-focus-frame.js` (NEW, ~+120 LOC)
- `j2cl/lit/test/wavy-focus-frame.test.js` (NEW, ~+150 LOC)
- `j2cl/lit/src/index.js` (~+1 LOC import)

**Behavior**:
- Single child element `<div class="frame" part="frame">` that's absolutely positioned via inline style from the `bounds` reactive prop (`top`, `left`, `width`, `height` all in `px`).
- `:host { display: contents; }` so the element doesn't take layout space; the inner frame is positioned relative to the nearest positioned ancestor (the read-surface root will be `position: relative`).
- Listens for `wavy-focus-changed` on the parent surface in `connectedCallback`; updates `focusedBlipId` + `bounds`.
- `prefers-reduced-motion: reduce` → `transition: none`.
- Hidden (via `hidden` attribute) when `focusedBlipId === ""`.
- Frame style: `box-shadow: var(--wavy-focus-ring)`, `border-radius: var(--wavy-radius-card)`, `pointer-events: none`.

### T2 — `<wavy-wave-nav-row>` Lit element

**Files**:
- `j2cl/lit/src/elements/wavy-wave-nav-row.js` (NEW, ~+260 LOC)
- `j2cl/lit/test/wavy-wave-nav-row.test.js` (NEW, ~+260 LOC)
- `j2cl/lit/src/index.js` (~+1 LOC import)

**Behavior**:
- 10 buttons rendered in fixed order E.1 → E.10. Each carries `data-action="recent|next-unread|previous|next|end|prev-mention|next-mention|archive|pin|version-history"`.
- `_emit(action)` helper dispatches a CustomEvent (bubbles + composed) with the corresponding event name (`wave-nav-${action}-requested`) and detail `{selectedBlipId, sourceWaveId}`.
- `H` keyboard handler (only): `connectedCallback` resolves the binding target via `this.closest('[data-j2cl-selected-wave-host]')` and falls back to `document` only when no ancestor is found (test-only / pre-mount). Adds `keydown` on the resolved target; `disconnectedCallback` removes. Handler ignores when target is `INPUT`/`TEXTAREA`/`[contenteditable]` or any modifier (`ctrlKey`/`metaKey`/`altKey`) is pressed. (See §3.R-3.4 for the rationale on scoped binding to prevent multi-fire.)
- `:host` declares `container-type: inline-size; container-name: wave-nav-row;` so the `@container wave-nav-row (max-width: 480px)` mobile/narrow collapse query actually matches: hides `[data-action="prev-mention"]`, `[data-action="next-mention"]`, `[data-action="version-history"]` and shows the overflow `<button data-action="overflow">`. The overflow menu (a `<menu>` element) renders the 3 collapsed items as `<button>` children that emit the same events as their full-width counterparts.

### T3 — `<wavy-depth-nav-bar>` Lit element

**Files**:
- `j2cl/lit/src/elements/wavy-depth-nav-bar.js` (NEW, ~+180 LOC)
- `j2cl/lit/test/wavy-depth-nav-bar.test.js` (NEW, ~+180 LOC)
- `j2cl/lit/src/index.js` (~+1 LOC import)

**Behavior**:
- Composes `<wavy-depth-nav>` for the breadcrumb path (passes `crumbs` through).
- Renders G.2 chevron-up button (left) and G.3 "Up to wave" button (right).
- Reserves a `<slot name="awareness-pill">` for the G.6 live-update pill (S5 will fill this slot from `J2clSelectedWaveView`).
- Emits `wavy-depth-up` and `wavy-depth-root` (both bubbles + composed).
- Reflects `data-current-depth-blip-id` on the host so external CSS / S5 can target.

### T4 — Renderer extension: `j`/`k` keys + focus-changed events + collapse motion CSS

**Files**:
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java` (~+90 LOC)
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java` (~+200 LOC)
- `j2cl/lit/src/design/wavy-thread-collapse.css` (NEW, ~+45 LOC)
- `j2cl/lit/esbuild.config.mjs` (~+1 LOC entry)
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` (~+4 LOC, two `<link>` insertions for `wavy-thread-collapse.css` next to the existing `wavy-tokens.css` links at lines 3383 and 3492)

**Renderer changes**:
1. `onBlipKeyDown` adds aliases: `j` → `ArrowDown`, `k` → `ArrowUp` only (per Issue #1046 R-3.2 scope). `g`/`G`/`[`/`]` are S5 territory. The `aria-keyshortcuts` attribute is **unchanged** (`"ArrowUp ArrowDown Home End"`) — `j`/`k` are documented as Wavy-specific aliases via code comments only, not announced to screen readers.
2. New private method `dispatchFocusChanged(HTMLElement blip, String key)` that creates a `CustomEvent("wavy-focus-changed", { detail: {blipId, bounds, key}, bubbles: true, composed: true })` on `host` and records a telemetry event. `bounds` is computed in **`host`-local coordinate space**: `top = blip.getBoundingClientRect().top - host.getBoundingClientRect().top + host.scrollTop`, etc. so the frame stays anchored under scroll. (Equivalent to walking `offsetParent` to `host`; the `getBoundingClientRect` arithmetic is simpler and consistent across collapsed/expanded states.)
3. `focusBlip(...)` calls `dispatchFocusChanged` whenever the focused blip changes (not on no-op `focusedBlip == next`).
4. New public method `setDepthFocus(String currentDepthBlipId, String parentDepthBlipId, String parentAuthorName)` — writes the trio to data-attributes on `host` so the depth-nav-bar can read them. (S5 calls this from `J2clSelectedWaveView`; S2 ships the writer.)
5. `toggleThread(...)` symmetric expand path: when expanding (`collapsed == false` branch), if the previously focused blip was hidden, re-focus it; if focus was outside the collapsed thread, no change.

**HtmlRenderer changes** (server-side; required so the new CSS bundle actually loads):
- After each existing `<link rel="stylesheet" href="...j2cl/assets/wavy-tokens.css">` write at lines 3383 and 3492, insert a sibling `<link rel="stylesheet" href="...j2cl/assets/wavy-thread-collapse.css">`. Both J2CL shell paths get the addition.

**CSS changes** (`wavy-thread-collapse.css`):
```css
.j2cl-read-thread {
  transition: max-height var(--wavy-motion-collapse-duration, 240ms)
      var(--wavy-easing-collapse, cubic-bezier(0.4, 0, 0.2, 1)),
    opacity var(--wavy-motion-collapse-duration, 240ms)
      var(--wavy-easing-collapse, cubic-bezier(0.4, 0, 0.2, 1));
  overflow: hidden;
  max-height: 10000px;
}
.j2cl-read-thread-collapsed {
  max-height: 0;
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .j2cl-read-thread,
  .j2cl-read-thread-collapsed {
    transition: none;
  }
}
```

**esbuild change**: register `wavy-thread-collapse.css` as a sibling asset, the same way `wavy-tokens.css` is registered.

### T5 — `J2clSelectedWaveView` mounts the chrome elements + model exposes `unreadCount`/`pinned`

**Files**:
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java` (~+110 LOC)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java` (~+30 LOC — expose `getUnreadCount()` accessor publicly + thread `pinned` from `J2clSearchDigestItem.isPinned()` through the model constructors with a `pinned` field; the existing `unreadCount` field at line 28 already has `getUnreadCount()` at line 485, so this is a confirmation pass and a new `getPinned()` accessor).
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModelCopyTest.java` (~+30 LOC for the `pinned` round-trip)
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewChromeTest.java` (NEW, ~+220 LOC) — boots the view against a synthetic host, asserts the three chrome elements are mounted, asserts each event reaches the host, asserts `pinned`/`unreadCount` props update when the model changes.

**Model changes**:
- `J2clSelectedWaveModel`: ensure `getUnreadCount()` is public (verified — it's already public at line 485 returning `unreadCount`). Add a new field `private final boolean pinned;` mirroring the existing `unreadCount` plumbing — populated from `digestItem.isPinned()` in the existing builder paths at lines 249/281. Add `public boolean getPinned() { return pinned; }`. Update `J2clSelectedWaveModelCopyTest` to round-trip the field.
- `archived` is **NOT** added to the model in S2 (no source of truth exists in `j2cl/src/main/java`; the inbox-folder state is an S5 concern). The nav-row's `archived` prop defaults to `false` in S2 with a TODO referencing S5.

**View changes**:
- Both paths (cold-mount and server-first): write `data-j2cl-selected-wave-host=""` on the `card` element (the `.sidecar-selected-card`). This is the binding target the `<wavy-wave-nav-row>` `H` handler resolves via `closest(...)`. T5b also writes the same attribute on the server-rendered card so the server-first path is consistent before client boot.
- Cold-mount path: in the constructor where `card` is built, insert (in DOM order):
  - `<wavy-depth-nav-bar hidden>` directly **after** the `eyebrow` and **before** the `title` — hidden by default until S5 writes `currentDepthBlipId`.
  - `<wavy-wave-nav-row>` directly **after** `participantSummary` and **before** `snippet` — bound reactively to model `unreadCount` (now via real `model.getUnreadCount()`) and `pinned` (via new `model.getPinned()`).
  - `<wavy-focus-frame>` is appended **inside the renderer's `host`** (the `.j2cl-read-surface` element) by `J2clReadSurfaceDomRenderer.enhanceSurface(...)` itself — NOT by the view. The renderer owns the frame placement because the frame and the focused blip both live inside the renderer's `host`. The `host` element gets `position: relative` (added in T4 via the new CSS file or directly via attribute style) so the frame's absolute positioning anchors correctly. (Reconciliation: T1 spec, T4 bounds math, and T5 DOM placement all converge on the same node = `host` = `.j2cl-read-surface`.)
- Server-first path (`existingCard` branch): the chrome elements are **already present** server-side (see T5b below). The view locates them via `existingCard.querySelector("wavy-wave-nav-row")` etc., re-binds them as the live nodes (custom-element upgrade happens automatically when `customElements.define` runs after the element is in the DOM — re-bind is property-set only, NEVER `replaceChild` or the upgraded state would reset).
- Wire `setDepthFocus(...)` writer on the renderer; the URL reader is S5.
- The view also installs **delegated event listeners** on the card for `wave-nav-*-requested` and `wavy-depth-up`/`wavy-depth-root`/`wavy-depth-jump-to-crumb` — each handler emits a `wave_chrome.nav_row.click` (or `wave_chrome.depth_nav.click`) telemetry event with the action name. The handlers do **not** route to the controller — that's S5. Telemetry observation is enough for S2 to demonstrate the wiring works without coupling to controller changes.

### T5b — Server-side chrome landmarks in `HtmlRenderer`

The J2CL view above mounts the chrome on the cold-mount path AND re-binds it on the server-first path. For the server-first path to work AND for the parity fixture (T6) to be able to assert chrome presence in the rendered HTML, the server template MUST emit the three chrome elements as **hidden landmarks** inside `appendRootShellSelectedWaveCard`.

**Files**:
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` (~+50 LOC)

**Insertions** in `appendRootShellSelectedWaveCard` (around line 3672):
- Add `data-j2cl-selected-wave-host=""` as an additional attribute on the `<section class="sidecar-selected-card" ...>` opening tag. This is the binding target for the `<wavy-wave-nav-row>` `H` keyboard handler (see T2).
- Inside the `<section class="sidecar-selected-card">`, immediately after the eyebrow paragraph and before the title `<h2>`, append a `<wavy-depth-nav-bar hidden data-j2cl-server-first-chrome="true"></wavy-depth-nav-bar>`.
- After the participant summary `<p class="sidecar-selected-participants">` and before the snippet `<p class="sidecar-selected-snippet">`, append a `<wavy-wave-nav-row data-j2cl-server-first-chrome="true"></wavy-wave-nav-row>`. The element appears whether or not the user is signed in — pre-boot AT users see the nav-row landmark.
- Inside the J2CL-rendered surface (the `<section data-j2cl-read-surface="true">` that lives inside `.sidecar-selected-content`), after the root thread, append a `<wavy-focus-frame data-j2cl-server-first-chrome="true" hidden></wavy-focus-frame>`. The frame sits inside the renderer's `host`, matching where the cold-mount renderer places it.
- This mirrors the existing F-1 pattern of pre-rendering the `.sidecar-selected-card` shell so the J2CL client upgrade swap is a no-op. The `data-j2cl-server-first-chrome` marker lets `J2clSelectedWaveView.enhanceExistingSurface` distinguish "already mounted" from "needs to be created".

### T6 — Server-side parity assertions

**Files**:
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageOneReadSurfaceParityTest.java` (~+220 LOC)

These assertions are valid because T5b pre-renders each chrome element as a hidden landmark inside `appendRootShellSelectedWaveCard`. The HTML returned by the servlet contains the literal `<wavy-wave-nav-row …>` etc. tags before any JS executes.

**New tests** (each is a separate `@Test` method so failures are individually addressable):
- `j2clRootRendersWavyWaveNavRowLandmark` — assert `<wavy-wave-nav-row ` appears in HTML and carries `data-j2cl-server-first-chrome="true"`. The 10 button `data-action="..."` markers do NOT appear server-side (the buttons are inside the Lit element's shadow DOM, populated client-side); the test asserts the landmark host element is present so the client can upgrade it without a re-mount.
- `j2clRootRendersFocusFrameLandmark` — assert `<wavy-focus-frame ` appears inside `.sidecar-selected-content` with `hidden` and `data-j2cl-server-first-chrome="true"` attrs.
- `j2clRootRendersDepthNavBarLandmark` — assert `<wavy-depth-nav-bar ` appears with `hidden` and `data-j2cl-server-first-chrome="true"` attrs at top-level (no depth selected).
- `legacyGwtRouteDoesNotLeakChromeElements` — extends existing `legacyGwtRouteDoesNotLeakF2ClientMarkers` test method (same file, line 318) with three additional `assertFalse(html.contains("<wavy-wave-nav-row"), ...)`, `<wavy-focus-frame`, `<wavy-depth-nav-bar` checks. The new method is added rather than mutating the existing assertions so the original F-2.S1 contract stays evidently intact.
- `j2clRootLoadsWavyThreadCollapseStylesheet` — assert `wavy-thread-collapse.css` is linked in the J2CL root shell HTML (verifies the HtmlRenderer addition in T4).
- `nestedThreadFixtureExposesCollapseTokenContract` — extension that asserts the inline-thread div carries `class="thread"` (renderer adds `j2cl-read-thread` client-side, but the wire contract on the server stays the existing `class="inline-thread thread"`).

**Per-row server-side coverage matrix** — additional `@Test` methods that pin the audit-row contract from the server side (each ARIA / data marker is checked at one and only one place; failures point directly at the broken row):

| Row | Test | Assertion |
| --- | --- | --- |
| **R-3.2** focus-frame landmark | `j2clRootRendersFocusFrameLandmark` | `<wavy-focus-frame ` present + `hidden` attr |
| **R-3.3** collapse stylesheet linked | `j2clRootLoadsWavyThreadCollapseStylesheet` | `wavy-thread-collapse.css` `<link>` present |
| **R-3.3** nested thread contract | `nestedThreadFixtureExposesCollapseTokenContract` | inline-thread `class="thread"` + `data-thread-id` |
| **R-3.4** nav-row landmark | `j2clRootRendersWavyWaveNavRowLandmark` | `<wavy-wave-nav-row ` host present |
| **R-3.4** nav-row not in GWT | `legacyGwtRouteDoesNotLeakChromeElements` | absence on `?view=gwt` |
| **R-3.7-chrome** depth-nav-bar landmark | `j2clRootRendersDepthNavBarLandmark` | `<wavy-depth-nav-bar ` host present + `hidden` |
| **R-3.7-chrome** depth-nav-bar not in GWT | `legacyGwtRouteDoesNotLeakChromeElements` | absence on `?view=gwt` |

**Per-row CLIENT-side coverage** (E.1–E.10 + G.2 + G.3 ARIA / event names) lives in:
- `j2cl/lit/test/wavy-wave-nav-row.test.js` — one `it()` per E.1–E.10 button asserting (a) ARIA label + (b) event name on click + (c) computed-style token cascade for the cyan/violet emphasis cases.
- `j2cl/lit/test/wavy-depth-nav-bar.test.js` — one `it()` per G.2 / G.3 button asserting ARIA label + event dispatch + detail shape.

The split is intentional: server-side fixtures verify the **landmark wire contract** (the elements exist for AT users + URL crawlers + the client can upgrade in place); client-side fixtures verify the **affordance contract** (the right ARIA + the right event firing). Together, every row from the issue brief has at least one executable assertion.

### T7 — Per-row fixture artifact (worktree only)

A `?view=j2cl-root` vs `?view=gwt` side-by-side artifact is **out of scope** for S2 (S6 owns the demo route). However, the Lit-side per-row fixture is in scope:

**File**: `j2cl/lit/test/wavy-wave-nav-row.test.js` includes a `describe("per-row coverage")` block that mounts the row in two scenarios — flat (`unreadCount=0, pinned=false, archived=false, mentionCount=0`) and nested (`unreadCount=3, pinned=true, archived=false, mentionCount=2`) — and asserts the **token cascade** (computed style includes the expected `--wavy-signal-*` reference) for E.2, E.6/E.7, E.9 in both states.

Per-row coverage on the server side is in T6 above (one assertion per E.* / G.* row).

### T8 — Telemetry events

**Files**:
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java` — emit `wave_chrome.focus_frame.transition` and `wave_chrome.thread_collapse.toggle`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java` — listen for the nav-row events and emit `wave_chrome.nav_row.click` with `field: action`.

Existing telemetry sink (`J2clClientTelemetry`) supports per-event field strings; no new infra needed.

## 5. Verification (SBT-only)

Run **inside worktree** with the package script. The SBT bridge is the contract:

```bash
sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild
```

Expected:
- `j2clLitTest` — runs the Lit element tests including all new files (`wavy-focus-frame.test.js`, `wavy-wave-nav-row.test.js`, `wavy-depth-nav-bar.test.js`, plus any updates to `wave-blip.test.js`).
- `j2clSearchTest` — runs `J2clReadSurfaceDomRendererTest` (extended) + `J2clSelectedWaveViewChromeTest` (NEW). 
- `j2clProductionBuild` — builds the J2CL bundle, exercising the `index.js` imports for the three new elements.

Then the parity fixture:
```bash
sbt -batch jakartaTest/test --tests J2clStageOneReadSurfaceParityTest
```
(or the equivalent task that runs the jakarta-test module — the existing fixture already runs there).

## 6. Out of scope (deferred to later F-2 slices)

- **S3** Search rail (B.* affordances) — `<wavy-search-input>`, `<wavy-saved-search-rail>`, `<wavy-search-help-modal>`.
- **S4** Floating + version history overlay (J.*, K.*) — `<wavy-scroll-to-new>`, `<wavy-version-history-overlay>`.
- **S5** Depth-nav data wiring + URL state (G.1, G.4, G.5, G.6) — the URL reader, the parent-author-label data binding, the live-update awareness pill, the `[`/`]` keyboard handlers.
- **S6** Demo route mount (`/_/j2cl-design/sample-wave`) + per-row fixture artifact.

S2 ships the **chrome shells** for these slices: the events are emitted, the data attributes are reserved, the slot for the awareness pill is ready, but no consumer-side wiring lands here.

## 7. Risk register

- **Telemetry name collisions**: `wave_chrome.*` is a new namespace; verify no existing event uses it. Searched — none.
- **`<wavy-depth-nav>` recipe contract**: F-0's `<wavy-depth-nav>` (the F-0 recipe) has properties `crumbs`. The new `<wavy-depth-nav-bar>` (S2) wraps it but does NOT re-implement the breadcrumb. Naming is intentional — the audit calls out the bar as G.2/G.3 controls + breadcrumb, and the recipe alone doesn't have G.2/G.3.
- **Focus frame `position: absolute` over the surface**: the renderer's `host` (`.j2cl-read-surface`) today has no explicit `position`. T4 sets `position: relative` on the surface element when `enhanceSurface(...)` runs (and `wavy-thread-collapse.css` includes a `.j2cl-read-surface { position: relative; }` rule for the server-first path before client boot). Verified no existing CSS expects the surface to be `static` — neither `wavy-tokens.css` nor `shell-tokens.css` positions it.
- **Collapse CSS bundle path**: the new `wavy-thread-collapse.css` is loaded as a separate `<link>` (matching the `wavy-tokens.css` pattern). Verified the server template already supports linking sibling stylesheet assets.
- **Nav-row mobile collapse**: container queries are supported in all Wave-target browsers (Chrome 105+, Firefox 110+, Safari 16+). Falls back gracefully on older browsers (overflow stays hidden, nav stays in single row).
- **Pin prop on the model**: confirmed `J2clSearchDigestItem.isPinned()` exists at line 60. T5 threads it through `J2clSelectedWaveModel` via a new `pinned` field + `getPinned()` accessor (mirroring the existing `unreadCount` plumbing). `unreadCount` is already on the model with a public `getUnreadCount()` at line 485 — T5 consumes the int directly, NOT a parse of `getUnreadText()`.
- **Archive prop on the model**: no `isArchived()` source-of-truth exists in `j2cl/src/main/java`. The archive E.8 button still ships in S2 and dispatches `wave-nav-archive-toggle-requested` on click; the `archived` prop defaults to `false` with a TODO for S5 to wire the inbox-folder state once available.

## 8. Closeout artifact

On merge, write `/tmp/parity-chain/f2-s2-merged.txt` with the merged sha, PR number, and the per-row demonstration matrix.

Comments to post:
- On #1046: plan link, impl summary, PR opened, PR merged.
- On #904: PR opened, PR merged.
- The parity matrix evidence will be appended to `/tmp/parity-chain/`.
