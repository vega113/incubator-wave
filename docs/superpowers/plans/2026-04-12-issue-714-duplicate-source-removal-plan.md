# Issue 714 Duplicate Source Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish issue `#714` by removing same-path Java duplicates between `wave/src/main/java/` and `wave/src/jakarta-overrides/java/`, leaving one authoritative source file per class and updating build/docs accordingly.

**Architecture:** Keep the currently active Jakarta runtime copies for every duplicate path that SBT resolves to `wave/src/jakarta-overrides/java/`, and delete the shadowed `src/main/java` copies. For the one inverted duplicate (`RobotApiModule.java`), keep the main-tree copy that SBT already compiles and delete the excluded Jakarta copy. Preserve the existing main-only legacy compile skips and directory-level excludes unless verification proves they are obsolete.

**Tech Stack:** SBT build metadata, Python `unittest`, shell audit scripts, Java source tree cleanup, GitHub issue evidence

---

## Task 1: Lock In A Failing Structural Audit

**Files:**
- Create: `scripts/tests/test_issue_714_duplicate_sources.py`
- Verify: `build.sbt`

- [ ] **Step 1: Write the failing audit test**

```python
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
    match = re.search(
        rf"val {re.escape(name)}: Set\\[String\\] = Set\\((.*?)\\n  \\)",
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
```

- [ ] **Step 2: Run the audit test and verify RED**

Run: `python3 -m unittest scripts.tests.test_issue_714_duplicate_sources -v`

Expected:
- `test_no_same_path_java_duplicates_remain` fails with the current duplicate-path list
- `test_exact_duplicate_exclude_sets_are_empty` fails because `mainExactExcludes` and `jakartaExactExcludes` still contain entries

- [ ] **Step 3: Record the duplicate inventory for the implementation commit**

Run:

```bash
node <<'NODE'
const fs = require('fs');
const path = require('path');
function walk(dir, prefix = '') {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const rel = path.join(prefix, entry.name);
    if (entry.isDirectory()) out.push(...walk(path.join(dir, entry.name), rel));
    else if (entry.isFile() && rel.endsWith('.java')) out.push(rel.replace(/\\/g, '/'));
  }
  return out;
}
const mainRoot = 'wave/src/main/java';
const jakartaRoot = 'wave/src/jakarta-overrides/java';
const main = new Set(walk(mainRoot));
const jakarta = new Set(walk(jakartaRoot));
const duplicates = [...main].filter(rel => jakarta.has(rel)).sort();
console.log(JSON.stringify({ duplicates: duplicates.length }, null, 2));
for (const rel of duplicates) console.log(rel);
NODE
```

Expected:
- the command prints `48` duplicate paths
- the list includes the `43` former `mainExactExcludes`, the `4` directory-excluded robot/avatar duplicates, and `org/waveprotocol/box/server/robots/RobotApiModule.java`

## Task 2: Remove The Duplicate Source Files

**Files:**
- Delete: `wave/src/main/java/com/google/wave/api/AbstractRobot.java`
- Delete: `wave/src/main/java/org/apache/wave/box/server/rpc/InitialsAvatarsServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/SearchModule.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/ServerModule.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/StatModule.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManager.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiOperationServiceRegistry.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/agent/AbstractBaseRobotAgent.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/agent/AbstractCliRobotAgent.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/agent/RobotAgentUtil.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/agent/registration/RegistrationRobot.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiOperationServiceRegistry.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/operations/NotifyOperationService.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentInfoServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/FetchProfilesServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/FetchServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/FolderServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/HealthServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/LocaleServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/NotificationServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchesServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/SignOutServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/VersionedFetchServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/WaveRefServlet.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/WebSocketChannelImpl.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/rpc/WebSocketClientRpcChannel.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/security/NoCacheFilter.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/security/SecurityHeadersFilter.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/security/StaticCacheFilter.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/stat/RequestScopeFilter.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/server/stat/TimingFilter.java`
- Delete: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiModule.java`

- [ ] **Step 1: Remove main-tree shadow copies for Jakarta-owned runtime classes**

Run:

```bash
rm \
  wave/src/main/java/com/google/wave/api/AbstractRobot.java \
  wave/src/main/java/org/apache/wave/box/server/rpc/InitialsAvatarsServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/SearchModule.java \
  wave/src/main/java/org/waveprotocol/box/server/ServerMain.java \
  wave/src/main/java/org/waveprotocol/box/server/ServerModule.java \
  wave/src/main/java/org/waveprotocol/box/server/StatModule.java \
  wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManager.java \
  wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiOperationServiceRegistry.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/agent/AbstractBaseRobotAgent.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/agent/AbstractCliRobotAgent.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/agent/RobotAgentUtil.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/agent/registration/RegistrationRobot.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiOperationServiceRegistry.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/operations/NotifyOperationService.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentInfoServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/FetchProfilesServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/FetchServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/FolderServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/HealthServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/LocaleServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/NotificationServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/PublicWaveFetchServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/SearchesServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/SignOutServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/UserRegistrationServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/VersionedFetchServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/WaveRefServlet.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/WebSocketChannelImpl.java \
  wave/src/main/java/org/waveprotocol/box/server/rpc/WebSocketClientRpcChannel.java \
  wave/src/main/java/org/waveprotocol/box/server/security/NoCacheFilter.java \
  wave/src/main/java/org/waveprotocol/box/server/security/SecurityHeadersFilter.java \
  wave/src/main/java/org/waveprotocol/box/server/security/StaticCacheFilter.java \
  wave/src/main/java/org/waveprotocol/box/server/stat/RequestScopeFilter.java \
  wave/src/main/java/org/waveprotocol/box/server/stat/TimingFilter.java
```

Expected:
- the shadow `src/main/java` copies disappear
- the matching Jakarta files remain intact

- [ ] **Step 2: Remove the excluded Jakarta-only duplicate for `RobotApiModule`**

Run:

```bash
rm wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiModule.java
```

Expected:
- `wave/src/main/java/org/waveprotocol/box/server/robots/RobotApiModule.java` remains as the only copy
- the excluded Jakarta duplicate is gone

## Task 3: Update Build Metadata And Runtime Guidance

**Files:**
- Modify: `build.sbt`
- Modify: `docs/architecture/jakarta-dual-source.md`

- [ ] **Step 1: Empty the exact-duplicate exclusion sets in `build.sbt`**

Update `build.sbt` so these blocks become:

```scala
  // No same-path main/Jakarta duplicate classes remain after issue #714.
  val mainExactExcludes: Set[String] = Set()
```

```scala
  val jakartaExactExcludes: Set[String] = Set()
```

Keep:
- `mainLegacyCompileExcludes`
- `mainDirExcluded`
- the existing `mainFileExcluded` predicate shape so it still consults the empty `mainExactExcludes` plus the legacy list

- [ ] **Step 2: Rewrite the stable architecture doc around the new single-source state**

Update `docs/architecture/jakarta-dual-source.md` so it says:
- the repo still has two source roots, but the duplicate-path cleanup from issue `#714` removed the old same-path shadow copies
- runtime classes that were previously shadowed now live only in `wave/src/jakarta-overrides/java/`
- `wave/src/main/java/` keeps shared code and legacy main-only classes, including the main-only compile skips in `build.sbt`
- `build.sbt` no longer relies on curated same-path exact excludes for active runtime ownership

- [ ] **Step 3: Search for now-stale exact-duplicate guidance and update only the stable docs/comments that would mislead future edits**

Run:

```bash
rg -n "duplicate-path|mainExactExcludes|jakartaExactExcludes|same-path|shadow copies" \
  docs build.sbt wave/src/main/java
```

Expected:
- remaining matches in planning docs can stay as historical artifacts
- stable guidance files and live source comments should no longer claim the runtime relies on same-path duplicate classes

## Task 4: Verify, Review, And Record Completion Evidence

**Files:**
- Create locally only: `journal/local-verification/2026-04-12-issue-714-duplicate-source-removal.md`

- [ ] **Step 1: Re-run the audit test and verify GREEN**

Run: `python3 -m unittest scripts.tests.test_issue_714_duplicate_sources -v`

Expected:
- both tests pass

- [ ] **Step 2: Run the build verification**

Run:

```bash
sbt compile
sbt test
scripts/jakarta-wrong-edit-guard.sh --base-ref origin/main
git diff --check
```

Expected:
- all commands exit `0`
- the guard reports no likely wrong-tree edits

- [ ] **Step 3: Write the local verification note**

Write `journal/local-verification/2026-04-12-issue-714-duplicate-source-removal.md` with:
- pre-change duplicate count (`48`)
- which tree was kept for the runtime-class duplicates
- the `RobotApiModule` exception and why the main copy stayed
- exact verification commands and results

- [ ] **Step 4: Self-review the diff**

Run:

```bash
git status --short
git diff --stat
git diff -- build.sbt docs/architecture/jakarta-dual-source.md scripts/tests/test_issue_714_duplicate_sources.py
```

Expected:
- review confirms the build/doc/test updates match the source deletions
- no stray unrelated edits appear

- [ ] **Step 5: Commit**

```bash
git add build.sbt \
  docs/architecture/jakarta-dual-source.md \
  docs/superpowers/plans/2026-04-12-issue-714-duplicate-source-removal-plan.md \
  scripts/tests/test_issue_714_duplicate_sources.py \
  wave/src/main/java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiModule.java
git commit -m "refactor: remove jakarta duplicate source shadows"
```
