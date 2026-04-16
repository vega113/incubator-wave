Worktree: /Users/vega/devroot/worktrees/vega113-incubator-wave-pr-891-monitor
Branch: monitor/vega113-incubator-wave/pr-891
PR: https://github.com/vega113/supawave/pull/891

Verification:
- `python3 -m unittest scripts.tests.test_perf_workflow_config.PerfWorkflowConfigTest.test_perf_workflow_uses_same_filename_for_alloy_download_and_checksum`
  - Result: failed before the workflow fix because `.github/workflows/perf.yml` downloaded `alloy.zip` but verified `alloy-linux-amd64.zip`
- `python3 -m unittest scripts.tests.test_perf_workflow_config`
  - Result: passed (`Ran 9 tests in 0.000s`)
- `sbt devCompile && sbt compileGwtDev`
  - Result: passed
- `sbt compile`
  - Result: passed
- `sbt test`
  - Result: passed (`Passed: Total 2289, Failed 0, Errors 0, Passed 2287, Skipped 2`)

Review remediation summary:
- Resolved the stale changelog review thread after confirming the branch already carried the requested `\"version\": \"PR #891\"` fix.
- Fixed `.github/workflows/perf.yml` so the Grafana Alloy installer downloads, verifies, and unzips the same filename, which matches the failure seen in `Gatling Performance Tests`.
- Added a workflow regression test to keep the download/checksum filename alignment covered.
- Addressed the remaining CodeRabbit thread by asserting the checksum command uses the same `alloy-linux-amd64.zip` filename as the download and unzip steps.
