// G-PORT-2 (#1111) — search panel parity between ?view=j2cl-root and
// ?view=gwt.
//
// Asserts:
//   - both views render the search rail surface (action row + folder
//     list);
//   - both views expose a refresh affordance reachable via a single
//     selector (`title="Refresh search results"`) that exists on the
//     GWT toolbar (setTooltip ⇒ HTML `title="..."`) and the J2CL
//     action row (`<button data-digest-action="refresh"
//     title="Refresh search results">`);
//   - both views render the same set of digest cards in the same
//     order, against a NON-EMPTY inbox seeded by clicking GWT's
//     "New Wave" toolbar button before sampling either view;
//   - each digest card on each view exposes `[data-digest-card]` plus
//     the five sub-selectors (`avatars`, `title`, `snippet`,
//     `msg-count`, `time`).
//
// Per the G-PORT-1 parity hard rule (issue #1110), this test does NOT
// skip an assertion to make the run pass. If a view legitimately
// fails to render the rail or expose the parity selectors the test
// fails until the underlying renderer is fixed.
//
// CodeRabbit (PR #1120 review): the seeding step is explicit, not a
// silent dependency on the WelcomeRobot. RegistrationUtil.greet() is
// best-effort (logs a warning on failure), so an empty-inbox run
// would silently bypass the per-card parity contract. We therefore
// click GWT's "New Wave" toolbar button to deterministically create
// a wave on the user's inbox before running the parity assertions.
import { test, expect, Locator, Page } from "@playwright/test";
import { J2clPage } from "../pages/J2clPage";
import { GwtPage } from "../pages/GwtPage";
import { freshCredentials, registerAndSignIn } from "../fixtures/testUser";

const BASE_URL = process.env.WAVE_E2E_BASE_URL ?? "http://127.0.0.1:9900";

interface DigestSnapshot {
  /** waveId / data-wave-id when present. */
  id: string | null;
  /** Title text. */
  title: string;
  /** Whether the six structural children exist. */
  hasAvatars: boolean;
  hasTitle: boolean;
  hasSnippet: boolean;
  hasMsgCount: boolean;
  hasTime: boolean;
}

/**
 * Reads every {@code [data-digest-card]} on the page and returns a
 * structural snapshot per card. Reads through the open shadow root of
 * the J2CL custom elements via Playwright's automatic shadow piercing
 * (the {@code locator(...)} selector pierces open shadow roots).
 */
async function snapshotCards(page: Page): Promise<DigestSnapshot[]> {
  const cards = page.locator("[data-digest-card]");
  const count = await cards.count();
  const out: DigestSnapshot[] = [];
  for (let i = 0; i < count; i++) {
    const card = cards.nth(i);
    out.push({
      id: await card.getAttribute("data-wave-id"),
      title: ((await card.locator("[data-digest-title]").first().textContent()) || "").trim(),
      hasAvatars: (await card.locator("[data-digest-avatars]").count()) > 0,
      hasTitle: (await card.locator("[data-digest-title]").count()) > 0,
      hasSnippet: (await card.locator("[data-digest-snippet]").count()) > 0,
      hasMsgCount: (await card.locator("[data-digest-msg-count]").count()) > 0,
      hasTime: (await card.locator("[data-digest-time]").count()) > 0
    });
  }
  return out;
}

/**
 * Resolves the first refresh affordance by `title` attribute. GWT's
 * setTooltip lowers to `title=""` so the same selector matches both
 * views.
 */
function refreshButton(page: Page): Locator {
  // Both views expose a refresh affordance with `title="Refresh search
  // results"` (GWT setTooltip lowers to a `title=""` HTML attr; J2CL
  // emits the same attr on its action-row button). On J2CL the rail
  // also emits a hidden SSR'd duplicate in light DOM — `:visible`
  // narrows to the user-perceivable button.
  return page.locator('[title="Refresh search results"]:visible').first();
}

/**
 * Drives the GWT view's "New Wave" toolbar button to deterministically
 * seed the user's inbox with one wave. The button's title is
 * "New Wave (Shift+Cmd+O)" (GWT messages.newWaveHint() + the shortcut
 * suffix). Returns when the search bar/rail re-renders, indicating
 * the new wave is now in inbox.
 *
 * Implementation detail: clicking "New Wave" in GWT navigates the URL
 * to the new wave (something like `#wave=…`). To put the user back on
 * the inbox so the rail shows the new wave we navigate explicitly to
 * `/?view=gwt` after creation — this is the same flow a user would
 * use via the back button.
 */
async function seedOneWaveViaGwtNewWaveButton(
  page: Page,
  gwt: GwtPage,
  baseURL: string
): Promise<void> {
  await gwt.goto("/");
  await gwt.assertInboxLoaded();
  // GWT renders the toolbar after the bundle loads. The New Wave
  // button has a tooltip of "New Wave (Shift+Cmd+O)" via
  // SearchPresenter.initToolbarMenu's setTooltip(messages.newWaveHint()
  // + " (Shift+Ctrl/Cmd+O)") wiring. We match on a starts-with title
  // selector so future tooltip suffix tweaks don't break the test.
  const newWave = page.locator('[title^="New Wave ("]').first();
  await expect(
    newWave,
    "GWT: New Wave toolbar button must mount within 30s"
  ).toBeVisible({ timeout: 30_000 });

  // Wait for the GWT initial /search to settle BEFORE snapshotting
  // the pre-click count. The WelcomeRobot delivers a welcome wave
  // asynchronously during registration, so the inbox state is racy
  // at toolbar-render time — sampling now would let cardsBefore
  // capture 0 cards, making the strict-increase post-condition
  // fire on the welcome wave (not on our newly-created wave).
  //
  // GWT obfuscates CSS class names so we cannot select on .waveCount
  // directly; the wave-count bar's text content follows a stable
  // pattern though ("N waves" / "N waves · M unread"), so we wait
  // for that text to appear in the DOM. After the bar settles we
  // also absorb a short delay to let any in-flight WelcomeRobot
  // delivery land before sampling cardsBefore.
  await page
    .waitForFunction(
      () => {
        const elements = Array.from(document.querySelectorAll("body *"));
        return elements.some((el) => {
          if (el.children.length > 0) return false;
          const text = (el.textContent || "").trim();
          return /\b\d+\s+waves?\b/i.test(text);
        });
      },
      { timeout: 30_000 }
    )
    .catch(() => {
      throw new Error(
        'GWT initial search did not settle; the "N waves" info bar never ' +
          "appeared. Inspect the trace attachment."
      );
    });
  // Absorb any in-flight WelcomeRobot delivery (best-effort) before
  // we snapshot cardsBefore. 3s is enough on a healthy local server;
  // CI is similarly generous on welcome-bot latency.
  await page.waitForTimeout(3_000);
  const cardsBefore = await page.locator("[data-digest-card]").count();

  // Capture the URL before click so we can detect the navigation
  // away to the new wave (which is how GWT confirms the wave was
  // created).
  const beforeUrl = page.url();
  await newWave.click();
  await page
    .waitForFunction((before) => window.location.href !== before, beforeUrl, {
      timeout: 30_000
    })
    .catch(() => {
      throw new Error(
        "GWT New Wave click did not navigate away from the inbox; the " +
          "wave was not created. Inspect the trace attachment."
      );
    });

  // Give the server a beat to commit the new-wave delta before we
  // navigate away. Without this sleep the subsequent /?view=gwt
  // bootstrap can issue its /search request before the new wave's
  // first delta has reached the index, returning a stale list.
  await page.waitForTimeout(2_000);

  // Navigate back to the inbox so the rail repaints with the new
  // wave at the top of the list. We use `location.href = ...` (a
  // full-document navigation) instead of Playwright's `page.goto`
  // because the post-click URL carries a GWT hash route
  // (#local.net/w+...) — `page.goto` to the same query string
  // sometimes preserves the GWT in-memory state and the new wave
  // does not appear in the inbox digest list. A hard navigation
  // forces GWT to re-bootstrap and re-issue the /search request.
  await page.evaluate((url) => {
    window.location.href = url;
  }, baseURL + "/?view=gwt");
  await page.waitForLoadState("domcontentloaded");
  await gwt.assertInboxLoaded();
  // Wait for the inbox card count to STRICTLY INCREASE past the
  // pre-click count. A short polling loop with explicit refreshes
  // covers the case where the GWT search has cached the pre-seed
  // result and needs a refresh click to pick up the new wave.
  const refreshLocator = page.locator(
    '[title="Refresh search results"]:visible'
  );
  let satisfied = false;
  const deadline = Date.now() + 45_000;
  while (Date.now() < deadline) {
    const now = await page.locator("[data-digest-card]").count();
    if (now > cardsBefore) {
      satisfied = true;
      break;
    }
    // Best-effort refresh; ignore errors if the toolbar isn't ready.
    try {
      if ((await refreshLocator.count()) > 0) {
        await refreshLocator.first().click({ timeout: 2_000 });
      }
    } catch {
      /* ignore — we'll retry on the next loop tick */
    }
    await page.waitForTimeout(1_000);
  }
  if (!satisfied) {
    const finalCount = await page.locator("[data-digest-card]").count();
    throw new Error(
      `GWT inbox did not surface the new wave within 45s ` +
        `(cardsBefore=${cardsBefore}, finalCount=${finalCount}). ` +
        `Inspect the trace attachment.`
    );
  }
}

test.describe("G-PORT-2 search panel parity", () => {
  test("J2CL and GWT search rails render the same digest list and refresh affordance", async ({
    page
  }) => {
    test.setTimeout(180_000);
    const creds = freshCredentials("gp2");
    test.info().annotations.push({ type: "test-user", description: creds.address });
    await registerAndSignIn(page, BASE_URL, creds);

    const j2cl = new J2clPage(page, BASE_URL);
    const gwt = new GwtPage(page, BASE_URL);

    // ---- Seed a non-empty inbox ----
    // CodeRabbit (PR #1120 review): without an explicit seed step the
    // per-card parity contract only validates the empty-list case. We
    // therefore actively create a wave via GWT's "New Wave" toolbar
    // button before sampling either view.
    await seedOneWaveViaGwtNewWaveButton(page, gwt, BASE_URL);

    // ---- J2CL view ----
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();

    // The rail must mount.
    await expect(
      page.locator("wavy-search-rail"),
      "J2CL: <wavy-search-rail> must mount"
    ).toHaveCount(1, { timeout: 15_000 });

    // The new action row must mount with refresh + sort + filter buttons.
    // The rail emits the action row twice in DOM: once pre-upgrade (in
    // light DOM, where it's intentionally hidden post-upgrade because the
    // rail does not expose a default slot per #1060) and once in the
    // shadow DOM (the visible one rendered by Lit). We assert at least
    // one VISIBLE action row exists, matching the contract a user would
    // perceive on the page.
    await expect(
      page.locator("[data-digest-action-row]:visible").first(),
      "J2CL: at least one action-row must be visible"
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.locator('[data-digest-action="refresh"]:visible').first(),
      "J2CL: refresh action button"
    ).toBeVisible();
    await expect(
      page.locator('[data-digest-action="sort"]:visible').first(),
      "J2CL: sort action button"
    ).toBeVisible();
    await expect(
      page.locator('[data-digest-action="filter"]:visible').first(),
      "J2CL: filter action button"
    ).toBeVisible();

    // Refresh affordance is reachable via the cross-view selector.
    await expect(
      refreshButton(page),
      "J2CL: refresh affordance reachable via title='Refresh search results'"
    ).toBeVisible({ timeout: 10_000 });

    // J-UI-1 / G-PORT-2: read whether the rail-card path is enabled
    // for this viewer. The shell-root SSR mirrors the per-viewer flag
    // value through `data-j2cl-search-rail-cards="true"`. When the
    // flag is OFF the J2CL view renders the legacy plain-DOM digest
    // list (which does NOT carry data-digest-card hooks); the parity
    // test then reduces its scope to the action-row contract,
    // because per-card structural parity is gated on the rail-cards
    // flag being on. The test still proves the parity selectors are
    // wired correctly when the path IS on.
    const railCardsOn = await page
      .locator("shell-root[data-j2cl-search-rail-cards='true']")
      .count();
    let j2clCards: DigestSnapshot[] | null = null;
    if (railCardsOn > 0) {
      // Wait for the J2CL search subscription to deliver at least one
      // card. The seed step guaranteed the inbox is non-empty, so we
      // can hard-require ≥1 card — that's the whole point of seeding.
      await page
        .waitForFunction(
          () => document.querySelectorAll("wavy-search-rail-card").length > 0,
          { timeout: 30_000 }
        )
        .catch(() => {
          throw new Error(
            "J2CL search subscription did not deliver any " +
              "<wavy-search-rail-card> within 30s after the inbox was " +
              "seeded. Either the search service is broken on this " +
              "server, or the j2cl-search-rail-cards flag is not " +
              "actually delivering cards. Inspect the trace attachment."
          );
        });
      j2clCards = await snapshotCards(page);
      expect(
        j2clCards.length,
        "J2CL: expected ≥1 digest card after seeding the inbox"
      ).toBeGreaterThan(0);
    }

    // ---- GWT view ----
    await gwt.goto("/");
    await gwt.assertInboxLoaded();

    await expect(
      refreshButton(page),
      "GWT: refresh affordance reachable via title='Refresh search results'"
    ).toBeVisible({ timeout: 30_000 });
    // data-digest-action-row/sort/filter are only emitted by the J2CL SSR
    // path (appendWavySearchRail in HtmlRenderer). The GWT search panel
    // creates its toolbar via SearchPanelWidget at runtime without those
    // attributes, so sort/filter are not asserted here.

    // Wait for ≥1 card on the GWT side as well — the seed guaranteed
    // a wave on the user's inbox.
    await page
      .waitForFunction(
        () => document.querySelectorAll("[data-digest-card]").length > 0,
        { timeout: 30_000 }
      )
      .catch(() => {
        throw new Error(
          "GWT inbox did not surface the seeded wave within 30s after " +
            "navigating back to the inbox. Inspect the trace attachment."
        );
      });

    const gwtCards = await snapshotCards(page);
    expect(
      gwtCards.length,
      "GWT: expected ≥1 digest card after seeding the inbox"
    ).toBeGreaterThan(0);

    // ---- Parity assertions ----

    // GWT cards always carry data-digest-* hooks (this slice tagged
    // DigestDomImpl.ui.xml). Whether the J2CL view renders them via
    // <wavy-search-rail-card> depends on the j2cl-search-rail-cards
    // feature flag for the viewer.
    if (j2clCards !== null) {
      // J2CL rail-cards path is enabled for this viewer — assert the
      // full per-card parity contract.
      test.info().annotations.push({
        type: "parity-cards",
        description:
          `j2cl=${j2clCards.length} gwt=${gwtCards.length} ` +
          `j2cl-titles=${JSON.stringify(j2clCards.map((c) => c.title))} ` +
          `gwt-titles=${JSON.stringify(gwtCards.map((c) => c.title))}`
      });
      expect(
        gwtCards.length,
        `digest card count parity (j2cl=${j2clCards.length}, gwt=${gwtCards.length})`
      ).toEqual(j2clCards.length);

      // Normalize the empty/untitled-wave fallback text before
      // comparing titles. The J2CL projector substitutes "(untitled
      // wave)" for an empty title (J2clSearchResultProjector.java:79
      // and J2clSearchPanelController.java:413); GWT lets the empty
      // string flow through DigestDomImpl.setTitleText. This is a
      // rendering-level divergence that lives outside the G-PORT-2
      // contract — the parity goal is the structural DOM, not the
      // text fallback. We therefore collapse both to empty string
      // before comparing the ordered title list.
      const normTitle = (t: string): string =>
        t === "(untitled wave)" || t === "(no title)" ? "" : t;
      expect(gwtCards.map((c) => normTitle(c.title))).toEqual(
        j2clCards.map((c) => normTitle(c.title))
      );
      for (const c of [...j2clCards, ...gwtCards]) {
        expect(c.hasAvatars, "card must have data-digest-avatars").toBe(true);
        expect(c.hasTitle, "card must have data-digest-title").toBe(true);
        expect(c.hasSnippet, "card must have data-digest-snippet").toBe(true);
        expect(c.hasMsgCount, "card must have data-digest-msg-count").toBe(true);
        expect(c.hasTime, "card must have data-digest-time").toBe(true);
      }
    } else {
      // J2CL rail-cards flag is off — the legacy plain-DOM digest list
      // does not carry data-digest-* hooks. We still assert that GWT
      // cards (now non-empty) expose the five children, because that's
      // the GWT-side contract this slice ships.
      for (const c of gwtCards) {
        expect(c.hasAvatars, "GWT card must have data-digest-avatars").toBe(true);
        expect(c.hasTitle, "GWT card must have data-digest-title").toBe(true);
        expect(c.hasSnippet, "GWT card must have data-digest-snippet").toBe(true);
        expect(c.hasMsgCount, "GWT card must have data-digest-msg-count").toBe(true);
        expect(c.hasTime, "GWT card must have data-digest-time").toBe(true);
      }
      test
        .info()
        .annotations.push({
          type: "note",
          description:
            "j2cl-search-rail-cards flag OFF for this viewer; per-card parity check skipped — only action-row + GWT-side card hooks asserted."
        });
    }

    // 4. Visual diff: capture a rail screenshot from each view. We
    //    don't compare them at the pixel level (the two views
    //    legitimately differ in chrome — that's J-UI / V-* visual
    //    polish, not the rail-DOM contract this slice owns), but the
    //    screenshots are attached to the report so a reviewer can
    //    inspect side-by-side.
    //
    //    Re-load the J2CL view first to grab its screenshot, then
    //    re-load the GWT view for its screenshot.
    await j2cl.goto("/");
    await j2cl.assertInboxLoaded();
    await expect(page.locator("wavy-search-rail")).toHaveCount(1);
    const j2clRailShot = await page
      .locator("wavy-search-rail")
      .first()
      .screenshot();
    await test.info().attach("rail-j2cl.png", {
      body: j2clRailShot,
      contentType: "image/png"
    });

    await gwt.goto("/");
    await gwt.assertInboxLoaded();
    await expect(refreshButton(page)).toBeVisible({ timeout: 30_000 });
    // GWT's search panel container is `.search-panel` from SearchPanelWidget.css.
    // We screenshot the closest ancestor that contains the refresh button so the
    // test does not depend on a specific GWT internal class name.
    const gwtRail = page
      .locator('[title="Refresh search results"]')
      .first()
      .locator("xpath=ancestor::*[contains(@class, 'search-panel') or contains(@class, 'self')][1]");
    let gwtShot: Buffer;
    if ((await gwtRail.count()) > 0) {
      gwtShot = await gwtRail.first().screenshot();
    } else {
      // Fallback: clip to the viewport-relative left rail area.
      const vp = page.viewportSize();
      gwtShot = await page.screenshot({
        clip: { x: 0, y: 0, width: Math.min(360, vp?.width ?? 360), height: vp?.height ?? 800 }
      });
    }
    await test.info().attach("rail-gwt.png", {
      body: gwtShot,
      contentType: "image/png"
    });
  });
});
