# Smoke Tests Summary

Date: 2025-08-29
Environment:
- OS: macOS
- Shell: zsh 5.9
- Java: JDK 17 (per local JAVA_HOME)
- Gradle: Wrapper 8.7

Commands executed:
- ./gradlew --no-daemon --warning-mode all :pst:build :wave:build

Results:
- :pst:build: SUCCESS
- :wave:build: SUCCESS (tests previously validated under JDK 17 with additional --add-opens flags)

Notable warnings (to address in Phase 3 deprecation cleanup):
- In pst/build.gradle:
  - Deprecated: org.gradle.api.plugins.Convention
  - Deprecated: org.gradle.api.plugins.JavaPluginConvention
  - Deprecated: org.gradle.util.ConfigureUtil
- In wave/build.gradle:
  - Deprecated: ApplicationPluginConvention (due to applicationDefaultJvmArgs usage)
  - Deprecated: Relying on Test.classpath convention in custom Test task (testGwt)

Other observations:
- CheckStyle: Prior builds have shown numerous style warnings (import order, indentation, line length, missing Javadoc). Not blocking builds.
- GWT: GWT tasks decoupled from default build via gwtBuild; compilation will be addressed in Phase 4.

Artifacts:
- PST JARs under pst/build/libs
- Wave JAR under wave/build/libs; installDist produces runnable distribution

Next actions:
- Phase 3 (in progress): Cleanup remaining Gradle 9 deprecations.
- Phase 4: Upgrade GWT and re-enable client compilation in CI.

