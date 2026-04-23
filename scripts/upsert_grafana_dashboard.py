#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
PERF_DASHBOARD_PATH = REPO_ROOT / "grafana" / "dashboards" / "perf-observability.json"
ANALYTICS_DASHBOARD_PATH = (
    REPO_ROOT / "deploy" / "supawave-host" / "grafana-dashboards" / "supawave-analytics-overview.json"
)
PLACEHOLDER_UIDS = {"__PROMETHEUS_DS_UID__", "${DS_PROMETHEUS}"}
URL_OPEN_TIMEOUT_SECONDS = 30


def default_dashboard_paths() -> list[Path]:
  return [PERF_DASHBOARD_PATH, ANALYTICS_DASHBOARD_PATH]


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
  parser = argparse.ArgumentParser(description="Upsert Grafana dashboards with a concrete datasource UID.")
  parser.add_argument(
      "--dashboard",
      action="append",
      default=[],
      help="Dashboard path to upsert. May be repeated. Defaults to the repo-owned perf and analytics dashboards.",
  )
  parser.add_argument(
      "--strict",
      action="store_true",
      help="Fail on dashboard read or Grafana API errors instead of treating them as warnings.",
  )
  return parser.parse_args(argv)


def load_dashboard(path: Path) -> dict:
  return json.loads(path.read_text(encoding="utf-8"))


def load_dashboard_targets(raw_paths: list[str] | None = None) -> list[Path]:
  candidates = raw_paths or [str(path) for path in default_dashboard_paths()]
  targets = []
  seen = set()

  for raw_path in candidates:
    path = Path(raw_path)
    if not path.is_absolute():
      path = REPO_ROOT / path
    normalized = path.resolve(strict=False)
    key = str(normalized)
    if key in seen:
      continue
    seen.add(key)
    targets.append(normalized)

  return targets


def inject_datasource(node, datasource_uid: str):
  if isinstance(node, dict):
    updated = {}
    for key, value in node.items():
      if key == "__inputs":
        continue
      if key == "uid" and isinstance(value, str) and value in PLACEHOLDER_UIDS:
        updated[key] = datasource_uid
      else:
        updated[key] = inject_datasource(value, datasource_uid)
    return updated
  if isinstance(node, list):
    return [inject_datasource(item, datasource_uid) for item in node]
  return node


def prepare_dashboard(path: Path, datasource_uid: str) -> dict:
  dashboard = inject_datasource(load_dashboard(path), datasource_uid)
  dashboard["id"] = None
  return dashboard


def build_payload(dashboard: dict, folder_uid: str | None = None) -> dict:
  payload = {
      "dashboard": dashboard,
      "overwrite": True,
  }
  if folder_uid:
    payload["folderUid"] = folder_uid
  return payload


def upsert_dashboard(
    path: Path,
    *,
    grafana_url: str,
    api_token: str,
    datasource_uid: str,
    folder_uid: str | None,
    strict: bool,
) -> int:
  if not path.exists():
    print(f"warning: Grafana dashboard file is missing: {path}", file=sys.stderr)
    return 1 if strict else 0

  payload = build_payload(
      dashboard=prepare_dashboard(path, datasource_uid),
      folder_uid=folder_uid,
  )
  request = urllib.request.Request(
      f"{grafana_url.rstrip('/')}/api/dashboards/db",
      data=json.dumps(payload).encode("utf-8"),
      headers={
          "Authorization": f"Bearer {api_token}",
          "Content-Type": "application/json",
      },
      method="POST",
  )

  try:
    with urllib.request.urlopen(request, timeout=URL_OPEN_TIMEOUT_SECONDS) as response:
      if 200 <= response.status < 300:
        return 0
      print(
          f"warning: Grafana dashboard upsert returned HTTP {response.status} for {path}",
          file=sys.stderr,
      )
      return 1 if strict else 0
  except urllib.error.HTTPError as exc:
    print(
        f"warning: Grafana dashboard upsert HTTP error for {path}: {exc.code} {exc.reason}",
        file=sys.stderr,
    )
    return 1 if strict else 0
  except urllib.error.URLError as exc:
    print(
        f"warning: Grafana dashboard upsert URL error for {path}: {exc.reason}",
        file=sys.stderr,
    )
    return 1 if strict else 0


def main(argv: list[str] | None = None) -> int:
  args = parse_args(argv)
  grafana_url = os.getenv("GRAFANA_URL", "")
  api_token = os.getenv("GRAFANA_DASHBOARD_API_TOKEN", "")
  datasource_uid = os.getenv("GRAFANA_PROMETHEUS_DATASOURCE_UID", "")
  folder_uid = os.getenv("GRAFANA_FOLDER_UID", "") or None

  if not grafana_url or not api_token or not datasource_uid:
    print("warning: Grafana dashboard credentials are incomplete; skipping upsert", file=sys.stderr)
    return 1 if args.strict else 0

  exit_code = 0
  for path in load_dashboard_targets(args.dashboard):
    exit_code = max(
        exit_code,
        upsert_dashboard(
            path,
            grafana_url=grafana_url,
            api_token=api_token,
            datasource_uid=datasource_uid,
            folder_uid=folder_uid,
            strict=args.strict,
        ),
    )
  return exit_code


if __name__ == "__main__":
  raise SystemExit(main(sys.argv[1:]))
