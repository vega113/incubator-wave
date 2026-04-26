import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-nav-drawer-toggle.js";

describe("<wavy-nav-drawer-toggle>", () => {
  it("defines the wavy-nav-drawer-toggle custom element", () => {
    expect(customElements.get("wavy-nav-drawer-toggle")).to.exist;
  });

  it("default state: open=false, inner button aria-expanded=false, label = Open navigation drawer", async () => {
    const el = await fixture(
      html`<wavy-nav-drawer-toggle></wavy-nav-drawer-toggle>`
    );
    expect(el.open).to.equal(false);
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-expanded")).to.equal("false");
    expect(button.getAttribute("aria-label")).to.equal("Open navigation drawer");
  });

  it("click toggles to open=true, inner button label flips to Close navigation drawer", async () => {
    const el = await fixture(
      html`<wavy-nav-drawer-toggle></wavy-nav-drawer-toggle>`
    );
    el.renderRoot.querySelector("button").click();
    await el.updateComplete;
    expect(el.open).to.equal(true);
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-expanded")).to.equal("true");
    expect(button.getAttribute("aria-label")).to.equal("Close navigation drawer");
  });

  it("inner native <button> activates on real Enter (focus + click cycle)", async () => {
    const el = await fixture(
      html`<wavy-nav-drawer-toggle></wavy-nav-drawer-toggle>`
    );
    const button = el.renderRoot.querySelector("button");
    button.focus();
    button.click();
    await el.updateComplete;
    expect(el.open).to.equal(true);
  });

  it("emits wavy-nav-drawer-toggled with the new open value", async () => {
    const el = await fixture(
      html`<wavy-nav-drawer-toggle></wavy-nav-drawer-toggle>`
    );
    setTimeout(() => el.renderRoot.querySelector("button").click());
    const ev = await oneEvent(el, "wavy-nav-drawer-toggled");
    expect(ev.detail).to.deep.equal({ open: true });
    expect(ev.bubbles).to.equal(true);
    expect(ev.composed).to.equal(true);
  });

  it("forwards caller-provided aria-controls onto the inner button", async () => {
    const el = await fixture(
      html`<wavy-nav-drawer-toggle
        aria-controls="some-drawer"
      ></wavy-nav-drawer-toggle>`
    );
    await el.updateComplete;
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-controls")).to.equal("some-drawer");
  });
});
