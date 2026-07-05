# SupaWave Lit shell (`j2cl/lit`)

Lit custom elements and shell controllers for the J2CL root UI (`/?view=j2cl-root`).
These primitives are composed by the GWT→J2CL parity slices (#964–#971) and are the
visual/interaction contract with the J2CL Java layer.

## Layout

- `src/elements/` — `wavy-*` / `shell-*` custom elements.
- `src/` — controllers, i18n catalog, keyboard-shortcut stack, DOM-contract helpers.
- `test/` — `@web/test-runner` + `@open-wc/testing` unit tests (one or more per element/controller).
- `web-test-runner.config.mjs` — test-runner + coverage config.

## Running tests

```bash
npm test                 # fast dev loop, no coverage
npm run test:coverage    # runs with v8 coverage + threshold gate (what CI runs)
```

`npm run test:coverage` is what the `j2clLitTest` sbt task (and therefore CI) runs.
It writes an lcov report to `coverage/` (gitignored) and a text summary to the
console.

## Coverage gate (#1275)

Coverage is **measured and enforced**. Floor thresholds live in
`web-test-runner.config.mjs` and are pinned a few points below the current
baseline (statements ~95.8% / branches ~90.9% / functions ~96.6% / lines ~95.8%),
so a meaningful regression fails CI while fractional wobbles don't churn.

Raise the thresholds as coverage climbs; **never lower them without justification.**

## The rule: new UI ships with tests

**Every new `wavy-*` / `shell-*` element or controller — and every behavior change
to an existing one — must include a test in the same PR** that exercises its public
API and state transitions using the existing `@web/test-runner` + `@open-wc`
patterns. This keeps the coverage gate green and, more importantly, protects the
parity contract at review time.

Cross-cutting concerns that must stay covered on the critical chrome surfaces:

- **i18n switching** — see `test/i18n.test.js`.
- **Keyboard shortcuts** — see `test/shortcuts/` (keybindings, dialog stack, shell-root keys).
- **Error / loading states** — assert the visible error/empty/loading branches, not just the happy path.

Snapshot + `axe-core` a11y sweeps are encouraged as follow-up once the infra cost is
evaluated; they are not required by this slice.
