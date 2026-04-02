# J2CL Migration — Phase 0 Agent Prompt

**Use this prompt to start a Codex or Claude Code agent on Phase 0 work.**

---

You are implementing **Phase 0** of the J2CL migration for the Supawave (Apache Wave) GWT client. Work in the repository root at the current working directory.

Read the full migration plan before touching any code:

```
docs/superpowers/plans/j2cl-full-migration-plan.md
```

Phase 0 is pure preparatory cleanup — no J2CL compiler, no new toolchain, no runtime changes. The GWT 2.10 app must still compile and run at the end. Every task here removes legacy surface so Phase 1 (J2CL toolchain scaffold) can start cleanly.

---

## Context

- Build system: SBT. Root build file is `build.sbt`. The GWT client is compiled by a separate `compileGwt` task wired in `build.sbt` (NOT part of the normal SBT compile).
- Java source lives under `wave/src/main/java/`. Resources under `wave/src/main/resources/`. Tests under `wave/src/test/java/` and `wave/src/test/resources/`.
- Jakarta server overrides live under `wave/src/jakarta-overrides/java/`.
- Generated PST message files live under `gen/messages/`.
- SBT excludes the GWT client source trees from the server compile via a path filter in `build.sbt` — changes to client-only files do NOT affect the server build.
- The GWT compile is currently broken by the SBT migration (the `compileGwt` task is wired but not verified as working end-to-end). Phase 0 should not make it worse, but fixing GWT compilation is not a Phase 0 acceptance criterion.
- Verification command for the server: `sbt compile test` (must pass throughout).

---

## Phase 0 Tasks

Work through these tasks **in order**. Each task has a verification step — do not proceed to the next task until verification passes.

---

### Task 1: Delete the gadget client trees (zero-effort deletions)

Delete these entire directories and their contents:

```
wave/src/main/java/org/waveprotocol/wave/client/gadget/
wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/
wave/src/main/java/org/waveprotocol/wave/model/gadget/
wave/src/main/resources/org/waveprotocol/wave/client/gadget/
wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/
wave/src/test/java/org/waveprotocol/wave/client/gadget/
wave/src/test/resources/org/waveprotocol/wave/client/gadget/
wave/src/main/java/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/
wave/src/main/resources/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/
```

Also delete these individual files:

```
wave/src/main/resources/org/waveprotocol/wave/model/gadget/Gadget.gwt.xml
wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/GadgetInfoProviderTest.java
wave/src/test/resources/org/waveprotocol/wave/client/gadget/tests.gwt.xml
```

**Verification:** `sbt compile` still passes. Count remaining `.gwt.xml` files — should be below 130 already or close to it.

---

### Task 2: Fix gadget coupling in client bootstrap and doodad layer

**2a. `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`**
- Remove `import org.waveprotocol.wave.client.gadget.Gadget` (and any other gadget imports)
- Remove the `.use(Gadget.install(...))` call inside `installDoodads(...)`
- Remove any other gadget doodad registration calls

**2b. `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/misc/GadgetCommand.java`**
- Delete this file entirely
- Find all callsites: `grep -r "GadgetCommand" wave/src/main/java/ --include="*.java"` — remove each reference

**2c. `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/plugins/video/VideoLinkPlugin.java`**
- Delete this file entirely
- Find all callsites and remove them

**2d. `wave/src/main/resources/org/waveprotocol/wave/client/doodad/Doodad.gwt.xml`**
- Remove `<inherits name='org.waveprotocol.wave.client.gadget.Gadget'/>`

**2e. `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml`**
- Remove `<inherits name='org.waveprotocol.wave.client.gadget.Gadget'/>` (and any other gadget inherits)

**2f. `wave/src/main/java/org/waveprotocol/wave/client/util/ClientFlagsBase.java`**
- Delete gadget-only flag fields: `profilePictureGadgetUrl`, `showGadgetSetting`, `usePrivateGadgetStates`
- Delete their accessor methods and parsing logic

**2g. `wave/src/main/java/org/waveprotocol/wave/common/bootstrap/FlagConstants.java`**
- Delete gadget-only flag constants: `PROFILE_PICTURE_GADGET_URL`, `SHOW_GADGET_SETTING`, `USE_PRIVATE_GADGET_STATES`

**2h. `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`**
- Remove any stale gadget-related comments or dead gadget code

**Verification:** `sbt compile` passes. `grep -r "gadget\|Gadget" wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/ --include="*.java"` returns nothing.

---

### Task 3: Clean `wave/model/supplement/**` gadget-state APIs

**Delete outright (3 implementation files):**
```
wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetState.java
wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetStateCollection.java
wave/src/main/java/org/waveprotocol/wave/model/supplement/DocumentBasedGadgetState.java
```

**Edit (remove gadget methods from interfaces and implementations) — do these in dependency order:**

1. `ReadableSupplement.java` — delete `getGadgetState(...)` method declaration
2. `WritableSupplement.java` — delete `setGadgetState(...)` method declaration
3. `PrimitiveSupplement.java` — delete gadget read/write method declarations
4. `ObservablePrimitiveSupplement.java` — delete `onGadgetStateChanged(...)` listener declaration
5. `ReadableSupplementedWave.java` — delete `getGadgetStateValue(...)` and `getGadgetState(...)` declarations
6. `WritableSupplementedWave.java` — delete `setGadgetState(...)` declaration
7. `ObservableSupplementedWave.java` — delete `onMaybeGadgetStateChanged(...)` declaration
8. `PrimitiveSupplementImpl.java` — delete gadget accessor/mutator implementations
9. `PartitioningPrimitiveSupplement.java` — delete gadget-state branching (the `usePrivateGadgetStates` path)
10. `SupplementImpl.java` — delete gadget delegation methods
11. `SupplementedWaveImpl.java` — delete gadget delegation methods and `getGadgetStateValue(...)` helper
12. `SupplementedWaveWrapper.java` — delete gadget delegation methods
13. `LiveSupplementedWaveImpl.java` — delete gadget-state forwarding and notification plumbing
14. `WaveletBasedSupplement.java` — remove `GADGETS_DOCUMENT`, `GADGET_TAG`, `gadgetStates` field, gadget constructor wiring, all gadget accessors/mutators, and the gadget-state notification fan-out

Also edit:
- `wave/src/main/java/org/waveprotocol/wave/model/schema/supplement/UserDataSchemas.java` — delete the `WaveletBasedSupplement.GADGETS_DOCUMENT` schema entry
- `wave/src/main/java/org/waveprotocol/wave/model/image/ImageConstants.java` — delete the `GADGET_TAGNAME` constant

**Verification:** `sbt compile` passes. `grep -r "GadgetState\|gadgetState\|GADGET" wave/src/main/java/org/waveprotocol/wave/model/supplement/ --include="*.java"` returns nothing.

---

### Task 4: Server and robot API gadget cleanup

**Delete:**
```
wave/src/main/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java
wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java
```

**Edit:**
- `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java` — remove `/gadget/gadgetlist` and `/gadgets/*` servlet/proxy registrations
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java` — same
- `wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java` — remove the gadget-state event generation branch and stop inspecting `com.google.wave.api.Gadget` elements
- `wave/src/main/java/org/waveprotocol/box/server/robots/operations/DocumentModifyService.java` — remove gadget element handling from document modify operations

**Deprecate in-place (do NOT move, do NOT delete — FQN must stay the same):**
- `wave/src/main/java/com/google/wave/api/Gadget.java` — add `@Deprecated(forRemoval = true)` to the class declaration
- `wave/src/main/java/com/google/wave/api/BlipData.java` — add `@Deprecated(forRemoval = true)` to gadget-element deserialization methods
- `wave/src/main/java/com/google/wave/api/data/ApiView.java` — add `@Deprecated(forRemoval = true)` to gadget element matching code
- `wave/src/main/java/com/google/wave/api/data/ElementSerializer.java` — add `@Deprecated(forRemoval = true)` to gadget serialization code
- `wave/src/main/java/com/google/wave/api/impl/ElementGsonAdaptor.java` — add `@Deprecated(forRemoval = true)` to the class
- `wave/src/main/java/com/google/wave/api/event/EventHandler.java` — add `@Deprecated(forRemoval = true)` to `onGadgetStateChanged(...)`
- `wave/src/main/java/com/google/wave/api/event/EventType.java` — add `@Deprecated(forRemoval = true)` to `GADGET_STATE_CHANGED` enum constant
- `wave/src/main/java/com/google/wave/api/event/GadgetStateChangedEvent.java` — add `@Deprecated(forRemoval = true)` to the class
- `wave/src/main/java/com/google/wave/api/AbstractRobot.java` — add `@Deprecated(forRemoval = true)` to the gadget-state dispatch branch/method

Delete:
- `wave/src/main/java/com/google/wave/api/oauth/impl/PopupLoginFormHandler.java` — not part of the robot event API contract; delete entirely

Also delete these test files:
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/GadgetProviderServletJakartaIT.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/operations/DocumentModifyServiceTest.java` — delete or rewrite once gadget operations are removed

**Verification:** `sbt compile test` passes. `grep -r "gadgetlist\|GadgetProvider\|GadgetState" wave/src/main/java/org/waveprotocol/box/server/ --include="*.java"` returns nothing meaningful.

---

### Task 5: Invert the `WaveContext` → `BlipReadStateMonitor` dependency

Currently `wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java` imports `org.waveprotocol.wave.client.state.BlipReadStateMonitor`. This creates a `wave/model` → `wave/client` compile dependency that blocks Phase 2.

**Steps:**

1. Move `BlipReadStateMonitor` interface to `wave/src/main/java/org/waveprotocol/wave/model/document/BlipReadStateMonitor.java` (or `wave/model/operation/BlipReadStateMonitor.java` if that better fits the package structure — use your judgment)
2. Update `WaveContext.java` to import from the new location
3. Update `wave/src/main/java/org/waveprotocol/wave/client/state/BlipReadStateMonitorImpl.java` to implement the moved interface (add the new import, keep the old `wave/client/state/BlipReadStateMonitor.java` as a deprecated re-export `@Deprecated interface BlipReadStateMonitor extends org.waveprotocol.wave.model.document.BlipReadStateMonitor {}` if removing it breaks compilation)
4. Find all other callsites: `grep -r "BlipReadStateMonitor" wave/src/main/java/ --include="*.java" -l` — update imports as needed

**Verification:** `sbt compile` passes. `grep "wave.client.state.BlipReadStateMonitor" wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java` returns nothing.

---

### Task 6: Replace narrow Guava usage and drop `guava-gwt`

Per `docs/j2cl-preparatory-work.md`, the client Guava surface is very narrow:
- `Preconditions.checkNotNull/checkArgument/checkState` (58 files) → replace with `org.waveprotocol.wave.model.util.Preconditions` (already exists in model layer)
- `@VisibleForTesting` (50 files) → remove the annotation or replace with a local `@VisibleForTesting` in a `wave/common` or `wave/testing` package
- `HashBiMap` / `BiMap` (1 file) → replace with two `HashMap` instances
- `Joiner` (1 file) → replace with `String.join(...)`

Search scope: `wave/src/main/java/org/waveprotocol/wave/client/` and the transitively compiled packages (`wave/model`, `wave/communication`, `wave/concurrencycontrol`).

After replacing all usages:
1. Remove `<inherits name='com.google.common.base.Base'/>` and `<inherits name='com.google.common.collect.Collect'/>` from all `.gwt.xml` files that reference them
2. Remove `guava-gwt` from `build.sbt` (the `compileOnly` and `gwt` configuration entries)

**Verification:** `sbt compile` passes. `grep -r "guava-gwt" build.sbt` returns nothing. `grep -r "com.google.common" wave/src/main/java/org/waveprotocol/wave/client/ --include="*.java"` returns nothing.

---

### Task 7: Remove deferred binding from 6 GWT modules

Replace `<replace-with>` runtime dispatch with explicit runtime detection in each of these modules:

**7a. `Popup.gwt.xml` + `PopupProvider` / `PopupChromeProvider`**
- Find the deferred binding: `grep -r "replace-with\|PopupProvider\|PopupChromeProvider" wave/src/main/resources/ wave/src/main/java/`
- Replace with a factory that detects mobile at runtime (`Navigator.userAgent` check or a SBT-configurable constant)

**7b. `Util.gwt.xml` + `UserAgentStaticProperties`**
- Find the deferred binding and replace with a runtime `userAgent` string check or static initialization

**7c. `useragents.gwt.xml`**
- Remove or flatten the `define-property`/`set-property` for `mobile.user.agent` — use a runtime detection function instead

**7d. `Logger.gwt.xml` + `LogLevel`**
- Replace `loglevel` property and `LogLevel` dispatch with a runtime log level string (from URL params or server config)

**7e. `EditorHarness.gwt.xml`**
- Remove test-only configuration properties that are no longer needed (or convert to plain `.gwt.xml` module without property dispatch)

**7f. `UndercurrentHarness.gwt.xml`**
- Same as above

After these changes, verify: `grep -r "replace-with\|generate-with" wave/src/main/resources/ --include="*.gwt.xml"` returns nothing (or only entries that are explicitly planned for a later phase).

**Verification:** `sbt compile` passes. `.gwt.xml` count is 130 or below.

---

### Task 8: Count `.gwt.xml` files and retire remaining dead modules

After tasks 1-7, count the remaining modules:

```bash
find wave/src/main/resources wave/src/test -name "*.gwt.xml" | wc -l
```

If the count is still above 130, identify and delete or consolidate dead leaf modules (modules that are never inherited by any other module and are not entry points). Check:

```bash
for f in $(find wave/src/main/resources -name "*.gwt.xml"); do
  name=$(grep -m1 'name=' "$f" | sed 's/.*name="\([^"]*\)".*/\1/')
  if ! grep -r "\"$name\"" wave/src/main/resources --include="*.gwt.xml" -q 2>/dev/null; then
    echo "Unused module: $f ($name)"
  fi
done
```

Delete modules that are provably unused.

**Target:** 130 or fewer `.gwt.xml` files.

---

## Acceptance Criteria (all must be true before committing Phase 0 complete)

- [ ] `sbt compile test` passes with no errors
- [ ] Total `.gwt.xml` count: 130 or below
- [ ] `grep -r "guava-gwt" build.sbt` → nothing
- [ ] `grep -r "replace-with\|generate-with" wave/src/main/resources/ --include="*.gwt.xml"` → nothing
- [ ] `grep -r "wave.client.state.BlipReadStateMonitor" wave/src/main/java/org/waveprotocol/wave/model/` → nothing
- [ ] `grep -rn "gadget\|Gadget\|GadgetState\|GADGET" wave/src/main/java/org/waveprotocol/wave/client/gadget/` → directory does not exist
- [ ] `grep -r "GadgetState\|gadgetState" wave/src/main/java/org/waveprotocol/wave/model/supplement/ --include="*.java"` → nothing
- [ ] `grep -r "gadgetlist\|GadgetProvider" wave/src/main/java/org/waveprotocol/box/server/ --include="*.java"` → nothing
- [ ] `com.google.wave.api.Gadget` still exists at its original FQN (not moved, just `@Deprecated(forRemoval=true)`)

---

## What NOT to do in Phase 0

- Do NOT touch the J2CL toolchain, Maven, Closure Compiler, or any new build infrastructure
- Do NOT migrate any GWT APIs to JsInterop or Elemental2
- Do NOT modify the production server runtime behavior
- Do NOT delete `com.google.wave.api.*` robot API types — only deprecate them in-place
- Do NOT touch the OT engine (`wave/concurrencycontrol/**`) or wave model core beyond the gadget-state supplement cleanup and the `WaveContext` dependency inversion
- Do NOT start Phase 1 work (J2CL sidecar pom.xml, SBT tasks) until all Phase 0 acceptance criteria pass

---

## Commit strategy

Make one commit per task (Tasks 1-8). Each commit must leave `sbt compile` passing. Label commits:

```
refactor(j2cl-phase0): delete gadget client trees
refactor(j2cl-phase0): remove gadget bootstrap coupling in StageTwo and doodad layer
refactor(j2cl-phase0): strip gadget-state APIs from wave/model/supplement
refactor(j2cl-phase0): remove gadget endpoints and deprecate robot API gadget types
refactor(j2cl-phase0): invert WaveContext BlipReadStateMonitor dependency
refactor(j2cl-phase0): replace Guava client usage and remove guava-gwt
refactor(j2cl-phase0): remove deferred binding from 6 GWT modules
refactor(j2cl-phase0): retire dead .gwt.xml modules to reach 130-module target
```

Open a PR against `main` with title: `refactor: J2CL Phase 0 — gadget removal, guava cleanup, deferred binding elimination`

---

## Reference

Full migration plan: `docs/superpowers/plans/j2cl-full-migration-plan.md`
Prior inventory: `docs/j2cl-gwt3-inventory.md`
Prior preparatory work notes: `docs/j2cl-preparatory-work.md`
