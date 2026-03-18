# Developer Setup (JDK 17, Gradle, Protobuf)

This guide helps you build and run the Apache Wave server (and later the GWT client) on JDK 17.

Prerequisites
- Java: JDK 17 installed and discoverable by Gradle toolchains
  - Suggested: sdkman
    - curl -s "https://get.sdkman.io" | bash
    - sdk install java 17.0.12-zulu
- Git
- macOS or Linux shell

Notes
- Gradle wrapper (./gradlew) will use Java toolchains to compile with JDK 17 automatically for Java modules.
- Protobuf compiler (protoc) is provided via the Gradle protobuf plugin; no system install needed.

Quick start (server only)
- From repo root:
  - ./gradlew --no-daemon --warning-mode all :pst:build :wave:build
  - ./gradlew :wave:installDist
  - ./wave/build/install/wave/bin/wave
- Visit http://localhost:9898/

Client (GWT)
- GWT compilation has been decoupled from the default server build and will be modernized in a later phase.
- To try compiling (may fail until Phase 4 completes):
  - ./gradlew :wave:compileGwt

Troubleshooting
- If Gradle cannot find a suitable JDK, ensure JAVA_HOME is set to a JDK 17 and/or install via sdkman.
- For protobuf errors, ensure you ran :pst:shadowJar before :wave:generateMessages if building tasks individually.

