// G-PORT-1 (#1110) — page object for the /?view=j2cl-root surface.
// Markers chosen to match the server-side parity contract asserted in
// J2clStageOneReadSurfaceParityTest and the existing screenshot harness
// in scripts/screenshot-v-5.mjs:
//   - <shell-root> present
//   - <shell-root-signed-out> absent
//   - GWT bootstrap (webclient/webclient.nocache.js) absent
//
// G-PORT-6 (#1115): adds task-affordance helpers for the parity test:
//   - blipTaskToggle(blipId)        — locates the per-blip task button
//   - blipHasTaskCompleted(blipId)  — presence check (Lit reflects
//     `taskCompleted: true` as the bare attribute, never `="true"`).
import { expect, Locator } from "@playwright/test";
import { WavePage } from "./WavePage";

export class J2clPage extends WavePage {
  viewQuery(): string {
    return "view=j2cl-root";
  }

  async assertInboxLoaded(): Promise<void> {
    await expect(
      this.page.locator('shell-root[data-j2cl-root-shell="true"]'),
      "J2CL view should render the signed-in J2CL shell"
    ).toHaveCount(1, { timeout: 15_000 });

    await expect(
      this.page.locator("shell-root-signed-out"),
      "J2CL view should not render the signed-out shell after sign-in"
    ).toHaveCount(0);

    const html = await this.page.content();
    expect(
      html.includes("webclient/webclient.nocache.js"),
      "J2CL view should not load the GWT webclient bundle"
    ).toBe(false);
  }

  /**
   * G-PORT-6 (#1115): per-blip task toggle button inside the
   * <wavy-task-affordance> custom element. Playwright pierces shadow
   * DOM automatically so the descendant selector lands on the actual
   * <button data-task-toggle-trigger="true"> rendered in the
   * affordance's renderRoot.
   */
  blipTaskToggle(blipId: string): Locator {
    return this.page.locator(
      `wave-blip[data-blip-id="${blipId}"] wavy-task-affordance ` +
        `button[data-task-toggle-trigger="true"]`
    );
  }

  /**
   * G-PORT-6 (#1115): returns whether the outer <wave-blip> host
   * carries the `data-task-completed` attribute. Lit's Boolean
   * reflection emits the attribute as presence-only (no `="true"`
   * value), so all assertions go through `hasAttribute(...)`.
   */
  async blipHasTaskCompleted(blipId: string): Promise<boolean> {
    return await this.page.evaluate((id: string) => {
      const el = document.querySelector(
        `wave-blip[data-blip-id="${(window as any).CSS.escape(id)}"]`
      );
      return el ? el.hasAttribute("data-task-completed") : false;
    }, blipId);
  }
}
