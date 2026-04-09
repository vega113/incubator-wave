# Issue #781 Local Verification

Date: 2026-04-09
Issue: #781 fix attachment metadata lookup for ids containing `+`
Worktree: `/Users/vega/devroot/worktrees/issue-781-attachment-id-encoding-20260409`
Branch: `issue-781-attachment-id-encoding-20260409`
Plan: `docs/superpowers/plans/2026-04-09-issue-781-attachment-id-encoding.md`
Commit: `6f2380ecb fix(attachment): encode metadata lookup ids`

## Verification Commands

1. Red test before implementation:

```bash
sbt "testOnly org.waveprotocol.wave.media.model.AttachmentInfoRequestBuilderTest"
```

Result: FAIL as expected before the helper existed (`AttachmentInfoRequestBuilder` missing at compile time).

2. Focused regression after implementation:

```bash
sbt "testOnly org.waveprotocol.wave.media.model.AttachmentInfoRequestBuilderTest"
```

Result: PASS (`2` tests, `0` failed).

3. Existing servlet validation:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.AttachmentInfoServletValidationTest"
```

Result: PASS (`2` tests, `0` failed).

4. Changelog assembly:

```bash
python3 scripts/assemble-changelog.py --fragments wave/config/changelog.d --output wave/config/changelog.json
```

Result: PASS (`assembled 120 entries -> wave/config/changelog.json`).

5. Changelog validation:

```bash
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Result: PASS (`changelog validation passed`).

6. Client compile gate:

```bash
sbt compileGwt
```

Result: PASS (`Compilation succeeded -- 100.695s`, `Link succeeded`).

7. Local UI/server sanity using the already-built webclient bundle:

```bash
rm -f /tmp/issue-781-ui-sanity.out
sbt -DskipGwt=true run > /tmp/issue-781-ui-sanity.out 2>&1 & pid=$!
cleanup() { kill "$pid" >/dev/null 2>&1 || true; pkill -f "org.waveprotocol.box.server.ServerMain" >/dev/null 2>&1 || true; }
trap cleanup EXIT
root_status=000
webclient_status=000
for i in {1..90}; do
  root_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:9898/ || true)
  webclient_status=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:9898/webclient/webclient.nocache.js || true)
  if [[ ( "$root_status" == "200" || "$root_status" == "302" ) && "$webclient_status" == "200" ]]; then
    echo "ROOT=$root_status WEBCLIENT=$webclient_status"
    exit 0
  fi
  sleep 1
done
echo "ROOT=$root_status WEBCLIENT=$webclient_status"
tail -n 120 /tmp/issue-781-ui-sanity.out
exit 1
```

Result: PASS (`ROOT=200 WEBCLIENT=200`).

## Review

- Direct review: no blockers found in the scoped diff.
- Claude review: PASS. Notes addressed:
  - Aligned the JVM test encoder with the production GWT query encoder for space handling by converting `+` to `%20` in the test helper.
  - Remaining note: changelog fragment `version` should be reconciled with the eventual PR number if repository practice changes.
