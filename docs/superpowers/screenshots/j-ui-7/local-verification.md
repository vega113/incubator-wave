# J-UI-7 local-server verification

Date: 2026-04-28
Branch: `claude/j2cl-ui-7`
Server: dev mode on `127.0.0.1:9899` via `bash scripts/worktree-boot.sh --port 9899` + `bash scripts/wave-smoke.sh start`.
User: fresh registration `qajui71777358186@local.net` (admin by virtue of being the first registered account on a clean memory store).

## Steps performed

1. Built `j2clProductionBuild`, `j2clLitBuild`, and the GWT shell via the
   worktree-boot helper.
2. Started the server with the staged build.
3. Registered fresh user via the `/auth/register` page (the user is not
   `vega` — per the `feedback_local_registration_before_login_testing`
   memory).
4. Signed in via `/auth/signin` (browser flow — curl gets caught by the
   redirect chain auth check).
5. Enabled the `j2cl-search-rail-cards` flag globally via `POST
   /admin/flags`.
6. Navigated to `/?view=j2cl-root&q=in:inbox`. Confirmed the welcome wave
   renders as a `<wavy-search-rail-card>` with the expected attributes.
7. Verified the live unread mutation path end-to-end by driving the same
   attribute mutations the Java side performs in
   `J2clSearchRailCardView.setUnreadCount(...)` and observing the
   element's reactive behaviour.

## Live-server evidence

Every transition below was driven by `card.setAttribute('unread-count',
N)` — the exact call `J2clSearchRailCardView.setUnreadCount(N)` issues
from the Java side when `J2clSearchPanelController.onReadStateChanged`
fires.

| Stage     | unread-count | data-pulse | badge        | aria-label                           | --wavy-rail-card-read |
|-----------|--------------|------------|--------------|--------------------------------------|-----------------------|
| Initial   | `"6"`        | `null`     | `"6"`        | `Welcome to SupaWave. 6 unread.`     | (unset)               |
| 6 → 3     | `"3"`        | `"ring"`   | `"3"`        | `Welcome to SupaWave. 3 unread.`     | (unset)               |
| 3 → 0     | `"0"`        | `"ring"`   | (hidden)     | `Welcome to SupaWave. Read.`         | `"1"`                 |

Highlights:

- The pulse fires on **every** post-initial transition, including the
  `1 → 0` zero-out where the badge disappears entirely (this was the
  case the v1 plan missed; the pulse box-shadow is now on the host, not
  on `.badge.unread`).
- Initial render did **not** fire a pulse — verified via the `before`
  snapshot above and via the dedicated unit case in
  `wavy-search-rail-card.test.js`.
- `:host([unread-count="0"])` exposes a CSS read-state hook
  (`--wavy-rail-card-read: 1`) so styling and parity probes can hook
  the read state without scraping the badge DOM.
- The `<article>` `aria-label` tracks the count live: 6 unread → 3
  unread → "Read." — exactly the text AT consumers will hear when they
  navigate back to the card.

## SSR contract (flag on)

```html
<shell-root data-j2cl-root-shell="true" ... data-j2cl-search-rail-cards="true">
  <wavy-search-rail query="in:inbox" data-active-folder="inbox"
                    result-count="" data-rail-cards-enabled="true">
  </wavy-search-rail>
</shell-root>
```

The `data-j2cl-search-rail-cards="true"` SSR attribute and the
`data-rail-cards-enabled="true"` rail mirror are both unchanged from
J-UI-1 (#1079); J-UI-7 does not introduce new SSR attributes.

## GWT path unaffected

`/?view=gwt` was not touched by this slice. The legacy DOM digest path
(used when the `j2cl-search-rail-cards` flag is off) keeps the same
listener wiring; the new contributions there (`data-read` on the
focusable button, `aria-label` refresh in `setUnreadCount`) are
backwards-compatible — the existing search-panel controller already
calls `setUnreadCount` whenever the listener fires.

## Out-of-scope gap surfaced during QA

While exercising the issue's local-server step 5 ("click the wave to
open it; confirm the rail digest's unread count immediately drops to
zero"), the per-blip read state was found to never decrement
end-to-end on the local-server config. Investigation showed:

- The `/read-state` endpoint correctly returns `unreadCount=6` for the
  fresh user's welcome wave.
- The J2CL read renderer does not stamp the `unread` attribute on
  per-blip elements because per-blip unread is not on the wire
  (`SidecarSelectedWaveDocument` carries no unread bit).
- The viewport-dwell `markBlipRead` path therefore never fires.
- The existing `/j2cl/mark-blip-read` endpoint (#1056) hits a separate
  "user-data wavelet unavailable" failure under
  `OperationContextImpl#openWavelet`, because the UDW-owner is not a
  participant on their own UDW and the standard
  `checkAccessPermission` denies the open.

These are **pre-existing F-4 gaps**, not introduced by J-UI-7. They
will be filed as follow-up issues. This slice ships the visible-cue
plumbing so the moment those upstream paths converge, the rail card's
live decrement is already pixel-correct and announceable.

## Files touched (summary)

- `j2cl/lit/src/elements/wavy-search-rail-card.js` — reflect
  unread-count, host-level pulse, `:host([unread-count="0"])` hook,
  composed `aria-label`, debounced pulse-clear timer.
- `j2cl/lit/test/wavy-search-rail-card.test.js` — 8 new cases.
- `j2cl/src/main/java/.../search/J2clDigestView.java` — `data-read`
  toggle and `aria-label` refresh on the focusable button root.
- `j2cl/src/test/java/.../search/J2clDigestViewTest.java` (new) — DOM
  attribute round-trip.
- `j2cl/src/test/java/.../search/J2clSearchRailCardViewTest.java`
  (new) — DOM attribute round-trip.
- `wave/config/changelog.d/2026-04-28-issue-1085-j-ui-7.json` (new) —
  release-note fragment.
- `docs/superpowers/plans/2026-04-28-j-ui-7-plan.md` (new) — slice
  plan + copilot revision.
