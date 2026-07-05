import { expect } from "@open-wc/testing";
import { installI18nBridge, J2CL_I18N_GLOBAL } from "../src/i18n/j2cl-bridge.js";
import { en } from "../src/i18n/catalogs/en.js";
import { de } from "../src/i18n/catalogs/de.js";
import { setLocale, _resetLocaleForTesting } from "../src/i18n/locale.js";

describe("#1277 J2CL i18n bridge", () => {
  afterEach(() => {
    _resetLocaleForTesting();
    delete window.__bootstrap;
    delete window[J2CL_I18N_GLOBAL];
  });

  it("installs t/getLocale/setLocale/subscribe on the target", () => {
    const target = {};
    const bridge = installI18nBridge(target);
    expect(target[J2CL_I18N_GLOBAL]).to.equal(bridge);
    expect(bridge.t).to.be.a("function");
    expect(bridge.getLocale).to.be.a("function");
    expect(bridge.setLocale).to.be.a("function");
    expect(bridge.subscribe).to.be.a("function");
  });

  it("defaults to window when no target is given", () => {
    const bridge = installI18nBridge();
    expect(window[J2CL_I18N_GLOBAL]).to.equal(bridge);
  });

  it("resolves the Java-surface keys through the English catalog by default", () => {
    const { t } = installI18nBridge({});
    expect(t("rootStatus.ready", "FB")).to.equal("Workspace is ready.");
    expect(t("selectedWave.errorDisconnected", "FB")).to.equal("Selected wave disconnected.");
  });

  it("returns German strings once the locale switches", () => {
    const { t, setLocale: bridgeSetLocale } = installI18nBridge({});
    bridgeSetLocale("de");
    expect(t("rootStatus.ready", "FB")).to.equal("Arbeitsbereich ist bereit.");
    expect(t("selectedWave.errorOpen", "FB")).to.equal(
      "Ausgewählte Wave kann nicht geöffnet werden."
    );
  });

  it("keeps the supplied English fallback for unknown keys", () => {
    const { t } = installI18nBridge({});
    expect(t("does.not.exist", "the fallback")).to.equal("the fallback");
  });
});

describe("#1277 Java-surface catalog keys", () => {
  // The catalog intentionally allows en-only keys (the en-fallback path), so we
  // do not assert full parity — only that the Java-surface keys added for #1277
  // are translated in both catalogs.
  const JAVA_SURFACE_KEYS = Object.keys(en).filter(
    (key) => key.startsWith("rootStatus.") || key.startsWith("selectedWave.")
  );

  it("has at least the root + selected-wave namespaces", () => {
    expect(JAVA_SURFACE_KEYS.length).to.be.greaterThan(15);
  });

  it("defines every Java-surface key in both en and de", () => {
    const missingDe = JAVA_SURFACE_KEYS.filter((key) => !(key in de));
    expect(missingDe, `de.js missing Java-surface keys: ${missingDe.join(", ")}`).to.have.lengthOf(
      0
    );
  });

  it("gives every Java-surface key a non-empty German translation distinct from English", () => {
    const untranslated = JAVA_SURFACE_KEYS.filter(
      (key) => typeof de[key] !== "string" || de[key].trim().length === 0
    );
    expect(
      untranslated,
      `de.js has empty Java-surface translations: ${untranslated.join(", ")}`
    ).to.have.lengthOf(0);
  });
});
