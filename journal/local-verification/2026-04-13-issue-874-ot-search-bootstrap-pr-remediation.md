Worktree: /Users/vega/devroot/worktrees/vega113-incubator-wave-pr-874-monitor
Branch: monitor/vega113-incubator-wave/pr-874
PR: https://github.com/vega113/supawave/pull/874

Verification:
- `sbt 'testOnly org.waveprotocol.box.server.frontend.WaveClientRpcImplTest'`
  - Result: passed, including the new `testOpenRejectsMismatchedSearchQueryWithoutLoggingRawQuery` regression
- `sbt 'testOnly org.waveprotocol.box.server.frontend.WaveClientRpcImplTest org.waveprotocol.box.search.SearchPresenterLoadingStateTest org.waveprotocol.box.webclient.search.SearchPresenterTest'`
  - Result: passed
- `python3 scripts/assemble-changelog.py`
  - Result: `assembled 173 entries -> wave/config/changelog.json`
- `python3 scripts/validate-changelog.py`
  - Result: `changelog validation passed`
- `sbt compile && sbt test`
  - Result: passed (`Total 2272, Failed 0, Errors 0, Passed 2272`)
- `sbt --batch Universal/stage`
  - Result: passed

Review remediation summary:
- Reject OT search bootstrap opens when the supplied `waveId` does not match the computed synthetic search wave for the query.
- Preserve search results after OT bootstrap failure only when they belong to the current query; clear stale results from previous queries.
- Remove the dead `otSearchFallbackEnabled` parameter from `SearchBootstrapUiState.shouldBootstrapViaHttpWhenOtStarts(...)`.
- Stop logging raw user search queries when rejecting mismatched synthetic search-wave opens; the warning now logs only the requested and expected wave IDs, with a regression test covering the rejection path.
- Revalidated the generated changelog workflow with assemble + validate instead of removing the assembled file, which this repo expects to be regenerated.
