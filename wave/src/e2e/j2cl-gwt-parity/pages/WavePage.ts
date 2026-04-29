// G-PORT-1 (#1110) — shared page-object base for the J2CL <-> GWT parity
// Playwright harness. Each later G-PORT slice extends the J2cl/Gwt
// subclasses with the surface it actually exercises.
//
// G-PORT-6 (#1115) extends the base with the surface needed by the
// `tasks-parity.spec.ts`:
//   - gotoWave(waveId) — switches the same browser session to a wave URL
//     while preserving the view query.
//   - newWaveAffordance(), composerBody(), composerSubmit(label) —
//     "create/send" hooks the GWT subclass implements (the parity
//     tests author content on GWT and assert on both views, because
//     the J2CL inbox compose surface is gated off in some configs).
import { Locator, Page } from "@playwright/test";

export abstract class WavePage {
  constructor(readonly page: Page, readonly baseURL: string) {}

  /** "view=j2cl-root" or "view=gwt". Subclass-defined. */
  abstract viewQuery(): string;

  /** Asserts the post-login shell rendered for this view. */
  abstract assertInboxLoaded(): Promise<void>;

  /**
   * Navigates to `path` (default "/") with this view's `?view=...` query
   * appended. Uses `domcontentloaded` so we don't depend on networkidle,
   * which is flaky against the live update channel — assertInboxLoaded()
   * does the real readiness check.
   */
  async goto(path: string = "/"): Promise<void> {
    const sep = path.includes("?") ? "&" : "?";
    const target = `${this.baseURL}${path}${sep}${this.viewQuery()}`;
    await this.page.goto(target, { waitUntil: "domcontentloaded" });
  }

  /**
   * G-PORT-3 / G-PORT-6: navigate to a wave-detail URL while preserving
   * the active view. The server reads the {@code wave} query param
   * (see WaveServlet); the {@code view} query is the J2CL/GWT switch.
   */
  async gotoWave(waveId: string): Promise<void> {
    await this.goto(`/?wave=${encodeURIComponent(waveId)}`);
  }

  /**
   * G-PORT-3 / G-PORT-6: the inbox "New Wave" affordance. Implemented
   * by the GWT page (parity tests author waves on GWT for now); the
   * J2CL page raises a not-implemented diagnostic if invoked.
   */
  newWaveAffordance(): Locator {
    throw new Error(
      `${this.constructor.name}.newWaveAffordance() is not implemented. ` +
        `Parity tests author waves on the GWT view today; if you need ` +
        `J2CL compose support, extend this class first.`
    );
  }
}
