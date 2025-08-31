# Jakarta migration epic

This document tracks the staged migration from javax to jakarta for Apache Wave, aligned with the Jetty migration path.

Status
- Current baseline: Jetty 9.4.x, Java 17, javax.* APIs
- Target: Jetty 12.x (EE 10/11), Java 17+, jakarta.* APIs
- Strategy: Two-step migration
  1) Stabilize on Jetty 9.4 with modern infra (JDK 17, TLS, HTTP/2, logging, access logs, caching, health endpoints)
  2) Introduce a jakarta layer (Jetty 12) and migrate servlets/filters to jakarta.servlet.*

Scope and subtasks
1) Spike: Replace guice-servlet with programmatic registration (keep Guice DI)
   - Goal: decouple from javax.servlet Filter/Servlet bindings in guice-servlet
   - Approach:
     - Introduce an experimental flag experimental.native_servlet_registration
     - Use native registration in ServerRpcProvider when the flag is enabled
     - Ensure Filters/Servlets are bound as @Singleton in Guice; instantiate via child injector
     - Verify URL mappings and init params parity with the current module
   - Exit criteria: parity smoke checks pass with native registration enabled

2) Prepare for jakarta namespace switch
   - Identify packages to migrate: servlets, filters, WebSockets, any javax.* usages
   - Add a compatibility layer or branches for javax vs jakarta builds
   - Plan dependency swaps: javax.servlet-api -> jakarta.servlet-api; Jetty 9.4 -> Jetty 12 modules

3) Jetty 12 migration
   - Introduce Jetty 12 dependencies guarded by a build profile/branch
   - Swap to jakarta.servlet.* imports
   - Adjust any Jetty APIs that changed between 9.4 and 12
   - Update WebSocket modules to Jetty 12 variants

4) Logging and metrics
   - Completed: slf4j 1.7.x + logback 1.2.13 (low-risk)
   - Future: migrate to slf4j 2.x + logback 1.5.x once Jetty 12 baseline is green

5) CI and build
   - Ensure Gradle toolchains target Java 17+; pin wrapper to compatible version
   - Checkstyle configured to exclude generated sources; allow disabling in CI for large logs
   - Add a Jetty 12 build job (allowed to fail) for early signal

6) Docs and rollout
   - Update docs/jetty-migration.md with the path and decisions (done)
   - Document experimental flag usage and fallback path
   - Provide operator notes for TLS, HTTP/2, and access logs

Operator flags and configs
- experimental.native_servlet_registration: boolean (default false)
- security.enable_ssl: boolean
- security.ssl_keystore_path: string
- security.ssl_keystore_password: string or env var (e.g., ${?WAVE_SSL_KEYSTORE_PASSWORD})

Open questions
- Do we need to retain guice-servlet at all under Jetty 12?
- Should we dual-publish distributions for javax and jakarta for a transition period?

Timeline (proposed)
- Week 1-2: Spike native registration; merge behind flag
- Week 3-4: Jetty 12 branch with jakarta compile; basic smoke checks
- Week 5+: Complete migration and remove javax path, or maintain both via profiles

