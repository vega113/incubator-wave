# Local Verification

- Branch: codex/mobile-bottom-actions-20260425
- Worktree: /Users/vega/devroot/worktrees/mobile-bottom-actions-20260425
- Date: 2026-04-25

## Commands

- `bash scripts/worktree-boot.sh --port 9925`
- `sbt "Test/runMain org.junit.runner.JUnitCore org.waveprotocol.box.server.util.WavePanelMobileChromeContractTest org.waveprotocol.box.server.util.MobileChromeControlsContractTest org.waveprotocol.box.server.util.WavePanelTagsLayoutTest"`
- `PORT=9925 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/mobile-bottom-actions-20260425/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/mobile-bottom-actions-20260425/wave/config/jaas.config' bash scripts/wave-smoke.sh start && PORT=9925 bash scripts/wave-smoke.sh check`
- Mobile Playwright check against `http://127.0.0.1:9925/#local/foo`
- `rg` check over `target/universal/stage/war` for removed pin controls and hidden tags CSS

## Results

- `worktree-boot.sh` passed; GWT assets compiled and staged for branch `codex/mobile-bottom-actions-20260425`.
- Focused JUnit contract tests passed: `OK (16 tests)`.
- Smoke check passed: `ROOT_STATUS=200`, `HEALTH_STATUS=200`, `LANDING_STATUS=200`, `J2CL_ROOT_STATUS=200`, `SIDECAR_STATUS=200`, `WEBCLIENT_STATUS=200`.
- Mobile Playwright check passed:
  - `mobileWaveChromePin` absent.
  - `mobileTagsPin` absent.
  - Tags button displays on mobile wave view with label `Show tags tray`.
  - Clicking Tags sets `body.mobile-tags-open` and updates the label to `Hide tags tray`.
  - Clicking Tags again clears `body.mobile-tags-open` and restores `Show tags tray`.
- Compiled staged assets passed:
  - `stale-mobile-pin-surface-absent`
  - `compiled-tags-hidden-contract-present`
- Claude review completed after the bridge-removal fix with no blockers. Output: `/tmp/claude-review-mobile-bottom-actions.out`.

## Follow-up

- PR: pending
- Issue: none supplied
