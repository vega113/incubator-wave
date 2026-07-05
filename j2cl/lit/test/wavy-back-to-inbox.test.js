import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-back-to-inbox.js";

describe("<wavy-back-to-inbox>", () => {
  it("defines the wavy-back-to-inbox custom element", () => {
    expect(customElements.get("wavy-back-to-inbox")).to.exist;
  });

  it("renders a single anchor with aria-label=Back to inbox", async () => {
    const el = await fixture(html`<wavy-back-to-inbox></wavy-back-to-inbox>`);
    const anchors = el.renderRoot.querySelectorAll("a");
    expect(anchors.length).to.equal(1);
    expect(anchors[0].getAttribute("aria-label")).to.equal("Back to inbox");
  });

  it("default href falls back to #inbox", async () => {
    const el = await fixture(html`<wavy-back-to-inbox></wavy-back-to-inbox>`);
    expect(el.renderRoot.querySelector("a").getAttribute("href")).to.equal(
      "#inbox"
    );
  });

  it("respects a caller-provided href", async () => {
    const el = await fixture(
      html`<wavy-back-to-inbox href="/inbox/all"></wavy-back-to-inbox>`
    );
    expect(el.renderRoot.querySelector("a").getAttribute("href")).to.equal(
      "/inbox/all"
    );
  });

  it("click fires wavy-back-to-inbox-clicked", async () => {
    const el = await fixture(html`<wavy-back-to-inbox></wavy-back-to-inbox>`);
    const a = el.renderRoot.querySelector("a");
    setTimeout(() =>
      a.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }))
    );
    const ev = await oneEvent(el, "wavy-back-to-inbox-clicked");
    expect(ev.bubbles).to.equal(true);
    expect(ev.composed).to.equal(true);
  });

  it("preventDefault on wavy-back-to-inbox-clicked cancels anchor navigation", async () => {
    const el = await fixture(html`<wavy-back-to-inbox></wavy-back-to-inbox>`);
    el.addEventListener("wavy-back-to-inbox-clicked", (ev) => ev.preventDefault());
    const a = el.renderRoot.querySelector("a");
    const click = new MouseEvent("click", { bubbles: true, cancelable: true });
    a.dispatchEvent(click);
    expect(click.defaultPrevented).to.equal(true);
  });

  it("modified clicks keep native anchor semantics even when intercepted", async () => {
    const el = await fixture(html`<wavy-back-to-inbox></wavy-back-to-inbox>`);
    el.addEventListener("wavy-back-to-inbox-clicked", (ev) => ev.preventDefault());
    const a = el.renderRoot.querySelector("a");
    for (const init of [{ metaKey: true }, { ctrlKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 }]) {
      let routed = 0;
      const count = () => routed++;
      el.addEventListener("wavy-back-to-inbox-clicked", count);
      const click = new MouseEvent("click", { bubbles: true, cancelable: true, ...init });
      a.dispatchEvent(click);
      el.removeEventListener("wavy-back-to-inbox-clicked", count);
      expect(click.defaultPrevented, JSON.stringify(init)).to.equal(false);
      expect(routed, "router event for " + JSON.stringify(init)).to.equal(0);
    }
  });

  it("anchor navigation proceeds when no listener intercepts", async () => {
    const el = await fixture(html`<wavy-back-to-inbox></wavy-back-to-inbox>`);
    const a = el.renderRoot.querySelector("a");
    const click = new MouseEvent("click", { bubbles: true, cancelable: true });
    a.dispatchEvent(click);
    expect(click.defaultPrevented).to.equal(false);
  });

  it("clearing href falls back to #inbox in the rendered output", async () => {
    const el = await fixture(html`<wavy-back-to-inbox></wavy-back-to-inbox>`);
    el.href = "";
    await el.updateComplete;
    expect(el.renderRoot.querySelector("a").getAttribute("href")).to.equal(
      "#inbox"
    );
  });
});
