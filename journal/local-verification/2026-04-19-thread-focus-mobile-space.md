# Local Verification

- Branch: codex/thread-focus-mobile-impl-20260419
- Worktree: /Users/vega/devroot/worktrees/thread-focus-mobile-impl-20260419
- Date: 2026-04-19

## Commands

- `sbt "testOnly org.waveprotocol.wave.model.util.ThreadFocusPolicyTest org.waveprotocol.box.server.util.WavePanelThreadFocusContractTest org.waveprotocol.box.server.util.WavePanelMobileChromeContractTest org.waveprotocol.box.server.util.WavePanelTagsLayoutTest org.waveprotocol.wave.model.supplement.SupplementedWaveImplTest"`
- `sbt compileGwt`
- `sbt -batch Universal/stage`
- `cd target/universal/stage && PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/thread-focus-mobile-impl-20260419/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/thread-focus-mobile-impl-20260419/wave/config/jaas.config' ./bin/wave`
- `PORT=9900 bash scripts/wave-smoke.sh check`
- Browser verification at `http://127.0.0.1:9900/` via Playwright:
  - desktop create-wave flow with nested replies
  - narrow desktop resize flow
  - mobile drawer create-wave flow
  - mobile edit-focus flow
  - mobile pin persistence reload flow

## Results

- Targeted regression suite passed.
- `compileGwt` passed.
- Staged server booted successfully on `127.0.0.1:9900`.
- Smoke checks returned `ROOT_STATUS=200`, `HEALTH_STATUS=200`, and `WEBCLIENT_STATUS=200`.
- Narrow desktop flow now promotes the active deep branch into a focused-thread presentation and emits `#focus=<blipId>&slide-nav=<depth>` in the hash.
- Mobile flow uses the hamburger -> drawer -> new-wave path correctly in compact mode.
- Mobile edit-focus flow sets `body.mobile-wave-chrome-hidden` while editing.
- Mobile wave chrome pin state survives reload through supplement-backed state rather than browser-local storage.

## Notes

- The compact-mode browser automation path needed DOM-dispatched clicks for some reply affordances because Playwright visibility heuristics did not always match the nested GWT editor controls.
- The mobile shell controls are currently exposed by the page shell but were validated primarily through body class changes and rendered screenshots, not through a final polished visual review of every control state.
