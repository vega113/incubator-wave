import { expect } from "@open-wc/testing";
import { createJsonShellInput } from "../src/input/json-shell-input.js";

describe("json-shell-input", () => {
  beforeEach(() => {
    window.__bootstrap = undefined;
  });

  it("reads the bootstrap contract payload from window.__bootstrap", () => {
    window.__bootstrap = {
      session: {
        address: "a@b.c",
        role: "owner",
        domain: "b.c",
        features: []
      },
      socket: {
        address: "ws.example:443"
      }
    };
    const snap = createJsonShellInput(window).read();
    expect(snap.signedIn).to.equal(true);
    expect(snap.role).to.equal("owner");
    expect(snap.idSeed).to.equal("");
    expect(snap.websocketAddress).to.equal("ws.example:443");
  });

  it("ignores legacy session.id from bootstrap JSON", () => {
    window.__bootstrap = {
      session: {
        address: "a@b.c",
        role: "owner",
        domain: "b.c",
        id: "legacy-seed",
        features: []
      },
      socket: {
        address: "ws.example:443"
      }
    };

    expect(createJsonShellInput(window).read().idSeed).to.equal("");
  });
});
