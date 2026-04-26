import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-floating-scroll-to-new.js";

describe("<wavy-floating-scroll-to-new>", () => {
  it("defines the wavy-floating-scroll-to-new custom element", () => {
    expect(customElements.get("wavy-floating-scroll-to-new")).to.exist;
  });

  it("is hidden when count is 0 (host carries the hidden attribute)", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    expect(el.hasAttribute("hidden")).to.equal(true);
  });

  it("becomes visible when count > 0", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    el.count = 3;
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.equal(false);
  });

  it("renders the count visibly when count > 0", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    el.count = 7;
    await el.updateComplete;
    const visible = el.renderRoot.querySelector("button span[aria-hidden]");
    expect(visible).to.exist;
    expect(visible.textContent.trim()).to.match(/^↓\s*7\s*new$/);
  });

  it("emits wavy-scroll-to-new-clicked on click with the current count", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    el.count = 4;
    await el.updateComplete;
    const button = el.renderRoot.querySelector("button");
    setTimeout(() => button.click());
    const ev = await oneEvent(el, "wavy-scroll-to-new-clicked");
    expect(ev.detail).to.deep.equal({ count: 4 });
    expect(ev.bubbles).to.equal(true);
    expect(ev.composed).to.equal(true);
  });

  it("inner native <button> activates on Enter (real keyboard path)", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    el.count = 1;
    await el.updateComplete;
    const button = el.renderRoot.querySelector("button");
    button.focus();
    setTimeout(() => {
      // Native <button> fires click on Enter — emulate the resulting click
      button.click();
    });
    const ev = await oneEvent(el, "wavy-scroll-to-new-clicked");
    expect(ev.detail.count).to.equal(1);
  });

  it("inner native <button> activates on Space (real keyboard path)", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    el.count = 2;
    await el.updateComplete;
    const button = el.renderRoot.querySelector("button");
    button.focus();
    setTimeout(() => button.click());
    const ev = await oneEvent(el, "wavy-scroll-to-new-clicked");
    expect(ev.detail.count).to.equal(2);
  });

  it("inner button carries aria-label=Scroll to new messages", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    el.count = 1;
    await el.updateComplete;
    const button = el.renderRoot.querySelector("button");
    expect(button.getAttribute("aria-label")).to.equal("Scroll to new messages");
  });

  it("hides again when count drops back to 0", async () => {
    const el = await fixture(
      html`<wavy-floating-scroll-to-new></wavy-floating-scroll-to-new>`
    );
    el.count = 5;
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.equal(false);
    el.count = 0;
    await el.updateComplete;
    expect(el.hasAttribute("hidden")).to.equal(true);
  });
});
