Worktree: /Users/vega/devroot/worktrees/vega113-incubator-wave-pr-866-monitor
Branch: codex/tag-undo-manual-close
PR: https://github.com/vega113/supawave/pull/866
Plan: N/A (PR remediation batch)

Changes:
- Added `type="button"` to persistent toast action buttons in `ToastNotification`.
- Added a source-contract regression assertion in `WavePanelTagsLayoutTest`.

Verification:
- `sbt "testOnly org.waveprotocol.box.server.util.WavePanelTagsLayoutTest"`
  - red: failed before the production fix because `actionBtn.setPropertyString("type", "button");` was absent
  - green: passed after the production fix
- `sbt compile`
  - passed
- `sbt test`
  - passed (`2253` tests, `0` failed)

Review remediation:
- Addressed Copilot inline review comment on `wave/src/main/java/org/waveprotocol/wave/client/widget/toast/ToastNotification.java`
  requesting explicit `type="button"` on the new toast action buttons.
