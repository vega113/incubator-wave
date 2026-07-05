import { playwrightLauncher } from "@web/test-runner-playwright";

// #1275: coverage is measured (v8 -> istanbul) so regressions in the Lit
// custom elements / shell controllers are visible in CI. It is turned on by
// the `--coverage` flag (see the `test:coverage` npm script and the
// j2clLitTest sbt task); the plain `npm test` dev loop stays fast without it.
export default {
  files: "test/**/*.test.js",
  nodeResolve: true,
  browsers: [playwrightLauncher({ product: "chromium" })],
  testFramework: {
    config: { ui: "bdd", timeout: 5000 }
  },
  coverageConfig: {
    // Only measure the shipped source; test helpers and fixtures don't count.
    include: ["src/**/*.js"],
    exclude: ["test/**", "node_modules/**", "**/*.test.js"],
    reportDir: "coverage",
    reporters: ["lcov", "text-summary"],
    // Floor thresholds pinned a few points below the current measured baseline
    // (statements 95.8 / branches 90.9 / functions 96.6 / lines 95.8) so a
    // meaningful drop fails CI, without churning on every fractional wobble.
    // Raise these as coverage climbs; never lower without justification.
    threshold: {
      statements: 92,
      branches: 87,
      functions: 92,
      lines: 92
    }
  }
};
