import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPO_ROOT / ".github" / "workflows" / "grafana-dashboards.yml"


class GrafanaDashboardsWorkflowTest(unittest.TestCase):
  @staticmethod
  def _workflow() -> str:
    return WORKFLOW_PATH.read_text(encoding="utf-8")

  @staticmethod
  def _step_window(workflow: str, step_name: str) -> str:
    lines = workflow.splitlines()
    marker = f"      - name: {step_name}"
    index = lines.index(marker)
    end_index = len(lines)
    for candidate in range(index + 1, len(lines)):
      if lines[candidate].startswith("      - name: "):
        end_index = candidate
        break
    return "\n".join(lines[index:end_index])

  def test_workflow_exists(self):
    self.assertTrue(WORKFLOW_PATH.exists())

  def test_workflow_reuses_existing_grafana_credentials(self):
    workflow = self._workflow()

    self.assertIn("GRAFANA_URL: ${{ vars.GRAFANA_URL }}", workflow)
    self.assertIn("GRAFANA_FOLDER_UID: ${{ vars.GRAFANA_FOLDER_UID }}", workflow)
    self.assertIn(
        "GRAFANA_PROMETHEUS_DATASOURCE_UID: ${{ vars.GRAFANA_PROMETHEUS_DATASOURCE_UID }}",
        workflow,
    )
    self.assertIn(
        "HAS_GRAFANA_DASHBOARD_API_TOKEN: ${{ secrets.GRAFANA_DASHBOARD_API_TOKEN != '' && 'true' || '' }}",
        workflow,
    )

  def test_workflow_scopes_paths_to_dashboard_assets(self):
    workflow = self._workflow()

    self.assertIn(".github/workflows/grafana-dashboards.yml", workflow)
    self.assertIn("scripts/upsert_grafana_dashboard.py", workflow)
    self.assertIn("scripts/tests/test_grafana_dashboard_upsert.py", workflow)
    self.assertIn("scripts/tests/test_grafana_dashboards_workflow.py", workflow)
    self.assertIn("grafana/dashboards/perf-observability.json", workflow)
    self.assertIn(
        "deploy/supawave-host/grafana-dashboards/supawave-analytics-overview.json",
        workflow,
    )

  def test_workflow_runs_validation_on_pull_requests(self):
    workflow = self._workflow()
    validate_step = self._step_window(workflow, "Run dashboard tests")

    self.assertIn("pull_request:", workflow)
    self.assertIn(
        "python3 -m unittest scripts.tests.test_grafana_dashboard_upsert scripts.tests.test_grafana_dashboards_workflow",
        validate_step,
    )

  def test_workflow_only_upserts_outside_pull_requests(self):
    workflow = self._workflow()
    upsert_step = self._step_window(workflow, "Upsert Grafana dashboards")

    self.assertIn("workflow_dispatch:", workflow)
    self.assertIn("publish_from_current_ref:", workflow)
    self.assertIn("branches: [ main ]", workflow)
    self.assertIn("github.event_name != 'pull_request'", upsert_step)
    self.assertIn("github.ref == 'refs/heads/main'", upsert_step)
    self.assertIn("github.event.inputs.publish_from_current_ref == 'true'", upsert_step)
    self.assertIn("HAS_GRAFANA_DASHBOARD_API_TOKEN == 'true'", upsert_step)

  def test_workflow_upsert_uses_strict_mode_and_both_dashboards(self):
    workflow = self._workflow()
    upsert_step = self._step_window(workflow, "Upsert Grafana dashboards")

    self.assertIn("python3 scripts/upsert_grafana_dashboard.py --strict", upsert_step)
    self.assertIn("--dashboard grafana/dashboards/perf-observability.json", upsert_step)
    self.assertIn(
        "--dashboard deploy/supawave-host/grafana-dashboards/supawave-analytics-overview.json",
        upsert_step,
    )

  def test_workflow_scopes_dashboard_token_to_upsert_step(self):
    workflow = self._workflow()
    job_block = workflow.split("\n    steps:\n", 1)[0]
    validate_step = self._step_window(workflow, "Run dashboard tests")
    upsert_step = self._step_window(workflow, "Upsert Grafana dashboards")

    self.assertNotIn(
        "GRAFANA_DASHBOARD_API_TOKEN: ${{ secrets.GRAFANA_DASHBOARD_API_TOKEN }}",
        job_block,
    )
    self.assertNotIn(
        "GRAFANA_DASHBOARD_API_TOKEN: ${{ secrets.GRAFANA_DASHBOARD_API_TOKEN }}",
        validate_step,
    )
    self.assertIn(
        "GRAFANA_DASHBOARD_API_TOKEN: ${{ secrets.GRAFANA_DASHBOARD_API_TOKEN }}",
        upsert_step,
    )


if __name__ == "__main__":
  unittest.main()
