# Issue 982 Grafana Analytics Dashboard Provisioning Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the repo-owned Wave usage analytics dashboard to Grafana with the existing GitHub Actions Grafana credentials so the dashboard no longer depends on manual import.

**Architecture:** Keep the live analytics emission and Alloy shipping path unchanged because host verification already shows `/metrics` exports the `wave_analytics_*` counters and Alloy remote-write is healthy. Fix the provisioning gap by extending the Grafana dashboard upsert helper to handle both dashboard placeholder styles already present in the repo, including the analytics dashboard `__inputs` export block, then add a lightweight GitHub Actions workflow that validates and upserts the dashboard with the existing `GRAFANA_*` vars and secret. In CI, the workflow should treat Grafana API failures as real failures; best-effort behavior stays available only outside the strict workflow path.

**Tech Stack:** Python 3, GitHub Actions workflow YAML, Grafana dashboards API, existing Prometheus-backed dashboard JSON.

---

### Task 1: Lock the provisioning gap down with tests

**Files:**
- Modify: `scripts/tests/test_grafana_dashboard_upsert.py`
- Add: `scripts/tests/test_grafana_dashboards_workflow.py`
- Read: `deploy/supawave-host/grafana-dashboards/supawave-analytics-overview.json`
- Read: `grafana/dashboards/perf-observability.json`

- [ ] **Step 1: Add failing helper tests for the analytics dashboard path**

Cover:
- the analytics dashboard file is included in the upsert target set
- the helper rewrites `${DS_PROMETHEUS}` datasource placeholders to the real datasource UID
- the helper strips or neutralizes the analytics dashboard `__inputs` placeholder block so the Grafana API payload is import-ready
- the helper still rewrites `__PROMETHEUS_DS_UID__` for the perf dashboard
- the helper payload remains `overwrite: true`

- [ ] **Step 2: Add a failing workflow-shape test**

Assert the new workflow:
- exists at `.github/workflows/grafana-dashboards.yml`
- reuses `GRAFANA_URL`, `GRAFANA_PROMETHEUS_DATASOURCE_UID`, `GRAFANA_FOLDER_UID`, and `GRAFANA_DASHBOARD_API_TOKEN`
- runs dashboard tests on PRs
- only performs the live Grafana upsert on `push` to `main` or `workflow_dispatch`
- explicitly scopes `paths:` to the dashboard helper, dashboard JSON files, and the workflow file itself

- [ ] **Step 3: Run the narrow failing test target**

Run:
`python3 -m unittest scripts.tests.test_grafana_dashboard_upsert scripts.tests.test_grafana_dashboards_workflow`

Expected:
- FAIL because the helper only targets the perf dashboard today and the workflow does not exist yet.

### Task 2: Generalize the Grafana dashboard upsert helper

**Files:**
- Modify: `scripts/upsert_grafana_dashboard.py`
- Modify: `scripts/tests/test_grafana_dashboard_upsert.py`

- [ ] **Step 1: Extend the helper to upsert explicit dashboard paths**

Implement:
- a small dashboard target list that includes both `grafana/dashboards/perf-observability.json` and `deploy/supawave-host/grafana-dashboards/supawave-analytics-overview.json`
- CLI support for repeated `--dashboard <path>` overrides so the workflow can be explicit
- recursive datasource placeholder normalization for both `__PROMETHEUS_DS_UID__` and `${DS_PROMETHEUS}`
- normalization that removes the analytics dashboard `__inputs` block once `${DS_PROMETHEUS}` has been resolved
- deduplication of repeated dashboard paths before upsert

- [ ] **Step 2: Keep the helper best-effort**

Preserve current behavior:
- missing credentials still exit `0` with a warning
- HTTP and URL errors still exit `0` with a warning
- folder UID remains optional

- [ ] **Step 3: Add a strict mode for CI provisioning**

Implement:
- a `--strict` CLI flag that turns Grafana HTTP/URL failures into non-zero exits
- workflow usage of `--strict` so a green run means the dashboard really upserted

- [ ] **Step 4: Re-run the helper tests**

Run:
`python3 -m unittest scripts.tests.test_grafana_dashboard_upsert`

Expected:
- PASS with both dashboard formats covered.

### Task 3: Add the lightweight dashboard provisioning workflow

**Files:**
- Add: `.github/workflows/grafana-dashboards.yml`
- Add: `scripts/tests/test_grafana_dashboards_workflow.py`

- [ ] **Step 1: Add the workflow**

Workflow requirements:
- trigger on `push` to `main` when dashboard helper/dashboard files change
- trigger on `pull_request` for the same paths to validate tests without live upsert
- trigger on `workflow_dispatch` for manual provisioning on a branch; this is intentional so the branch can prove Grafana creation before merge
- run `python3 -m unittest scripts.tests.test_grafana_dashboard_upsert scripts.tests.test_grafana_dashboards_workflow`
- run `python3 scripts/upsert_grafana_dashboard.py --strict --dashboard grafana/dashboards/perf-observability.json --dashboard deploy/supawave-host/grafana-dashboards/supawave-analytics-overview.json` only when the event allows live provisioning and Grafana credentials are present

- [ ] **Step 2: Re-run the workflow-shape tests**

Run:
`python3 -m unittest scripts.tests.test_grafana_dashboards_workflow`

Expected:
- PASS with the new workflow committed to the repo.

### Task 4: Verify end to end and capture traceability

**Files:**
- Modify: `journal/local-verification/2026-04-23-issue-982-grafana-analytics-dashboard.md`

- [ ] **Step 1: Run the full narrow verification set**

Run:
`python3 -m unittest scripts.tests.test_grafana_dashboard_upsert scripts.tests.test_grafana_dashboards_workflow`

Expected:
- PASS

- [ ] **Step 2: Prove the patch stayed out of the shipping path**

Run:
`git diff --name-only -- scripts/upsert_grafana_dashboard.py scripts/tests/test_grafana_dashboard_upsert.py scripts/tests/test_grafana_dashboards_workflow.py .github/workflows/grafana-dashboards.yml deploy/supawave-host/grafana-dashboards/supawave-analytics-overview.json grafana/dashboards/perf-observability.json docs/superpowers/plans/2026-04-23-issue-982-grafana-analytics-dashboard-provisioning.md`

Expected:
- Only the helper, tests, workflow, and dashboard files listed above changed; no `/metrics` or Alloy shipping files were touched.

- [ ] **Step 3: Trigger the dashboard workflow on the branch**

Run:
`gh workflow run grafana-dashboards.yml --ref codex/issue-982-grafana-analytics-dashboard`

Then record the workflow run URL and final status in:
- `journal/local-verification/2026-04-23-issue-982-grafana-analytics-dashboard.md`
- GitHub issue `#982`

- [ ] **Step 4: Open the PR and monitor it through merge**

After review-ready verification:
- open the PR to `main`
- add issue comment with plan path, verification commands, workflow run evidence, and PR URL
- create a thread heartbeat monitor so this thread keeps checking the PR until it is merged
