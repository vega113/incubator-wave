# E2E Sanity Check — supawave.ai

> Hourly monitoring prompt for `/loop 1h`. Checks client health, server logs, and deploy pipeline.
> Creates GitHub issues automatically when new bugs are found.

## Usage

```
/loop 1h <paste the prompt below>
```

---

## Prompt

E2E Sanity Check for supawave.ai.

PHASE 1 — CLIENT HEALTH CHECK:

Use curl to verify core endpoints:
```
curl -s -o /dev/null -w '%{http_code}' 'https://supawave.ai/healthz'
curl -s -c /tmp/wave-cookies.txt -X POST 'https://supawave.ai/auth/signin' -d 'address=testSanity1&password=testSanity1' -o /dev/null -w '%{http_code}'
curl -s -b /tmp/wave-cookies.txt 'https://supawave.ai/search/?query=in%3Ainbox&index=0&numResults=20' -o /dev/null -w '%{http_code}'
curl -s -o /dev/null -w '%{http_code}' 'https://supawave.ai/webclient/webclient.nocache.js'
```

Expected: healthz 200, login 302, search 200, GWT assets 200.
If any fail, investigate and create GitHub issue.

If browser automation is available (mcp__claude-in-chrome), also verify:
- Navigate to https://supawave.ai/auth/signin?r=/
- Verify app loads: signout link exists, inbox count visible
- Create wave via JS: `document.querySelector('.SWCM2').click()`
- Check console for app errors (ignore chrome-extension errors)
- Reuse a single tab across checks to avoid tab accumulation

PHASE 2 — SERVER LOG CHECK (always):

```
ssh supawave "docker logs supawave-wave-1 --since 1h 2>&1 | tail -300 | grep -iE 'error|exception|fatal|stacktrace|caused.by|OOM|crash'"
ssh supawave "docker logs supawave-caddy-1 --since 1h 2>&1 | tail -300 | grep -iE 'error|exception|fatal'"
ssh supawave "docker logs supawave-mongo-1 --since 1h 2>&1 | tail -300 | grep -iE 'error|exception|fatal'"
```

PHASE 3 — DEPLOY PIPELINE CHECK (always, critical):

```
gh run list --repo vega113/incubator-wave --workflow deploy-contabo.yml --limit 3
ssh supawave "docker ps --filter name=supawave-wave --format '{{.Status}}'"
```

If latest deploy is `failure`:
- Production is running stale code
- Check how many consecutive failures
- If not already tracked, create GitHub issue immediately

PHASE 4 — TRIAGE (if NEW errors found):

1. Check recent commits: `git log --oneline -10`
2. Check recent PRs: `gh pr list --repo vega113/incubator-wave --state all --limit 5`
3. If confirmed NEW bug:
   a. Create GitHub issue: `gh issue create --repo vega113/incubator-wave --title "..." --body "..."`
   b. If fix is straightforward, create a PR
   c. Include issue/PR link in output
4. Known issues to IGNORE (already tracked — update this list as issues are resolved):
   - Caddy 502s during deploy restart windows (transient, ~8s)
   - ResendMailProvider missing API key (#68)
   - RobotCapabilityFetcher missing binding (#66)
   - FragmentsServlet UnsupportedOperationException (#67)
   - VersionHistoryServlet version 0 hash mismatch (#126)

OUTPUT FORMAT:
```
=== SUPAWAVE SANITY CHECK [timestamp] ===
Client: PASS/FAIL/SKIP (details)
Server logs: CLEAN/ISSUES (details)
Deploy pipeline: OK/FAILING (details)
Action taken: NONE / ISSUE_CREATED (link) / PR_CREATED (link)
=======================================
```

---

## Prerequisites

- SSH access: `ssh supawave` must work without password prompt
- GitHub CLI: `gh` authenticated with repo access
- Test users registered on supawave.ai:
  - testSanity1 / testSanity1
  - testSanity2 / testSanity2
- Register if needed: `curl -s -X POST 'https://supawave.ai/auth/register' -d 'address=testSanity1&password=testSanity1'`

## Browser Automation Notes (optional, for deeper checks)

- Disable 1Password for supawave.ai (it crashes the Chrome extension)
- Reuse a single tab across checks — creating new tabs each time causes extension disconnects
- GWT editor typing is flaky via browser automation — wave creation + blip loading is the real signal
- If extension disconnects, curl fallback covers the critical paths
- After signout, navigate explicitly to `/auth/signin?r=/` (signout redirects to landing page, not login)

## What This Catches

Proven detections from real monitoring (2026-03-23 to 2026-03-25):
- Missing Guice bindings after Robot API JWT PR (#66)
- FragmentsServlet UnsupportedOperationException (#67)
- ResendMailProvider config error cascading to break registration (#68)
- 6+ consecutive deploy failures going undetected for 10+ hours (#100)
- GWT assets 404 after SBT migration deploy (#109)
- VersionHistoryServlet hash mismatch at version 0 (#126)
