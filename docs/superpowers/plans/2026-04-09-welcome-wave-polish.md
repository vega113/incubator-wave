# Welcome Wave Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the onboarding welcome wave so it reads more clearly, feels more SupaWave-native, explains `gpt-ts-bot@supawave.ai` and public-wave discovery better, and includes direct internal wave links to the onboarding/public support set.

**Architecture:** Keep the change localized to the existing server-side welcome authoring seam in `WelcomeWaveContentBuilder`. Extend the existing structural test to lock in the new copy and actual `wave://...` link annotations, then update the builder with richer paragraph/list structure and a small helper for internal wave links. Finish with a narrow local verification pass against a real worktree server using shared file-store state so the rendered content and in-app wave navigation are both exercised.

**Tech Stack:** Jakarta server overrides, Wave conversation/document annotations, JUnit 3 `TestCase`, SBT, changelog assembly/validation.

---

## File Structure

**Modify:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveContentBuilder.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java`
- `wave/config/changelog.d/2026-04-09-welcome-wave-polish.json`

**Regenerate:**
- `wave/config/changelog.json`

**Verification targets:**
- `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest org.waveprotocol.box.server.rpc.render.ServerHtmlRendererTest org.waveprotocol.wave.client.doodad.link.LinkTest"`
- `python3 scripts/assemble-changelog.py`
- `python3 scripts/validate-changelog.py --changelog wave/config/changelog.json`
- `bash scripts/worktree-boot.sh --shared-file-store --port 9899`
- local server smoke using the shared file-store state from `/Users/vega/devroot/incubator-wave`

## Task 1: Lock the new onboarding copy and wave-link requirements in tests

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/WelcomeWaveCreatorTest.java`

- [ ] **Step 1: Expand the structural assertions for the new copy**

```java
assertTrue(rootText.contains("gpt-ts-bot@supawave.ai"));
assertTrue(rootText.contains("left search panel"));
assertTrue(rootText.contains("@ icon"));
assertTrue(rootText.contains("Talk to the bot"));
assertTrue(rootText.contains("Onboarding waves"));
```

- [ ] **Step 2: Add assertions for the six internal onboarding/support wave links**

```java
assertManualLink(rootBlip.getContent(),
    "wave://supawave.ai/w+PSWhwKguwjA/~/conv+root/b+PSWhwKguwjB");
assertManualLink(rootBlip.getContent(),
    "wave://supawave.ai/w+IaeaidlHtXA/~/conv+root/b+IaeaidlHtXB");
```

- [ ] **Step 3: Keep the existing external-link and collapsed-thread assertions intact**

```java
assertEquals(5, detailTexts.size());
assertEquals(5, result.getCollapsedThreadIds().size());
```

- [ ] **Step 4: Run the focused welcome-wave test**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest"`
Expected: FAIL because the current content still uses the older copy, lacks the `@`-icon guidance, and does not include the six internal onboarding wave links.

## Task 2: Update the authored welcome-wave content and wave-link helpers

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WelcomeWaveContentBuilder.java`

- [ ] **Step 1: Introduce constants/helpers for the six onboarding/support wave refs**

```java
private static final String WAVE_0_URI =
    "wave://supawave.ai/w+PSWhwKguwjA/~/conv+root/b+PSWhwKguwjB";
```

- [ ] **Step 2: Re-author the root blip with clearer section spacing and list-shaped guidance**

```java
appendLine(doc, "Start here");
appendLine(doc, "Wave keeps the conversation and the document in the same place.");
appendLine(doc, "");
appendLine(doc, "Try this next");
appendLine(doc, "- Start a new wave.");
appendLine(doc, "- Add people or bots from the participant bar.");
appendLine(doc, "- Use @mention where the context actually lives.");
```

- [ ] **Step 3: Replace the old robot copy with clearer `gpt-ts-bot@supawave.ai` guidance**

```java
appendLine(doc, "Talk to the bot");
appendLine(doc,
    "Invite gpt-ts-bot@supawave.ai into a wave, then talk to it where the relevant text already is.");
appendLine(doc,
    "It works best when you @mention it in the right reply thread and ask for something concrete: summarize, draft, explain, or continue the work in context.");
```

- [ ] **Step 4: Add the public-wave discovery instruction using the left-panel `@` icon wording from the issue**

```java
appendLine(doc,
    "Public waves are discoverable from the @ icon in the left search panel, or from the public directory when you want the open web view.");
```

- [ ] **Step 5: Add a dedicated onboarding/public support section with actual internal wave links**

```java
appendLine(doc, "Onboarding waves");
appendLinkedLine(doc, "Wave 0", WAVE_0_URI);
appendLinkedLine(doc, "Wave 1", WAVE_1_URI);
```

- [ ] **Step 6: Re-run the focused welcome-wave test**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest"`
Expected: PASS with the new copy, real `wave://...` annotations, and unchanged collapsed inline-detail behavior.

## Task 3: Record the user-facing change and verify locally

**Files:**
- Create: `wave/config/changelog.d/2026-04-09-welcome-wave-polish.json`

- [ ] **Step 1: Add a changelog fragment for the onboarding polish**

```json
{
  "releaseId": "2026-04-09-welcome-wave-polish",
  "version": "PR #000",
  "date": "2026-04-09",
  "title": "Welcome wave polish",
  "summary": "The welcome wave now has clearer navigation guidance, stronger bot coaching, and direct internal links across the onboarding/public support waves."
}
```

- [ ] **Step 2: Assemble and validate the changelog**

Run: `python3 scripts/assemble-changelog.py`
Expected: `wave/config/changelog.json` is regenerated successfully.

Run: `python3 scripts/validate-changelog.py --changelog wave/config/changelog.json`
Expected: validation passes.

- [ ] **Step 3: Run the focused automated verification set**

Run: `sbt "testOnly org.waveprotocol.box.server.rpc.WelcomeWaveCreatorTest org.waveprotocol.box.server.rpc.render.ServerHtmlRendererTest org.waveprotocol.wave.client.doodad.link.LinkTest"`
Expected: PASS.

- [ ] **Step 4: Use the standard worktree boot helper so the local verification record is created in the expected place**

Run: `bash scripts/worktree-boot.sh --shared-file-store --port 9899`
Expected: the app is staged, the shared file-store is linked into `wave/_accounts`, `wave/_attachments`, and `wave/_deltas`, and a local-verification record is created under `journal/local-verification/`.

- [ ] **Step 5: Start the staged server with the printed command and run the base smoke check**

Run: the exact `start`, `check`, and `stop` commands printed by `scripts/worktree-boot.sh`
Expected: the server starts cleanly on port `9899`, the helper smoke check passes, and shutdown completes cleanly.

- [ ] **Step 6: Exercise the welcome-wave render and the inserted internal wave links in a browser**

Run: sign in against the local worktree server, open the welcome wave, confirm the richer formatting reads cleanly, and click at least one inserted onboarding wave link to verify in-app navigation resolves to the linked wave.

Expected: the welcome wave renders with readable section spacing and list structure, the `@`-icon/public-wave guidance is visible, and the inserted internal wave link opens the expected target wave instead of a dead external URL.
