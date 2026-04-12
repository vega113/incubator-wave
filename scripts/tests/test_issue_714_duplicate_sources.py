import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
MAIN_ROOT = REPO_ROOT / "wave" / "src" / "main" / "java"
JAKARTA_ROOT = REPO_ROOT / "wave" / "src" / "jakarta-overrides" / "java"
BUILD_FILE = REPO_ROOT / "build.sbt"


def java_paths(root: Path) -> set[str]:
  return {
      str(path.relative_to(root)).replace("\\", "/")
      for path in root.rglob("*.java")
  }


def build_set_entries(name: str) -> list[str]:
  text = BUILD_FILE.read_text(encoding="utf-8")
  empty_match = re.search(
      rf"val {re.escape(name)}: Set\[String\] = Set\(\)",
      text,
  )
  if empty_match:
    return []
  match = re.search(
      rf"val {re.escape(name)}: Set\[String\] = Set\((.*?)\n  \)",
      text,
      re.S,
  )
  if not match:
    return []
  return re.findall(r'"([^"]+)"', match.group(1))


class Issue714DuplicateSourcesTest(unittest.TestCase):

  def test_no_same_path_java_duplicates_remain(self) -> None:
    duplicates = sorted(java_paths(MAIN_ROOT) & java_paths(JAKARTA_ROOT))
    self.assertEqual([], duplicates)

  def test_exact_duplicate_exclude_sets_are_empty(self) -> None:
    self.assertEqual([], build_set_entries("mainExactExcludes"))
    self.assertEqual([], build_set_entries("jakartaExactExcludes"))


if __name__ == "__main__":
  unittest.main()
