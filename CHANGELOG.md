# Changelog

All notable changes to this project are documented in this file.

## [2.0.0] - Unreleased

### Breaking

- **Removed** shipping Groovy scripts as classpath resources (`bb4.gradle`, `bb4-publish.gradle`, `bb4-deploy.gradle`). Downstream projects must use published **plugin IDs** instead of `apply from:`.
- **Removed** old Gradle 7.x wrapper; minimum supported Gradle is **8.14.x** (aligned with current wrapper).
- **Java baseline** is **21** (toolchain), replacing compile `release`/target **17** from 1.x scripts.
- **Scala** default bumped to **3.8.2** with updated test stack (JUnit 5, ScalaTest 3.2.19, etc.).
- **`bb4.base`** is explicit; **`bb4.scala-library`** applies base conventions internally (same as before logically, but ids are separate artifacts).

### Added

- Convention plugins: `com.barrybecker4.bb4.base`, `com.barrybecker4.bb4.scala-library`, `com.barrybecker4.bb4.publish`, `com.barrybecker4.bb4.application`.
- `bb4 { }` extension: `archivesBaseName`, `mainClass`, `scalaVersion`.
- Foojay toolchain resolver convention in `settings.gradle.kts` for JDK provisioning.
- Dependency versions for consumers are defined only in `Bb4DependencyVersions.groovy` (no duplicate version catalog).
- `publishArtifacts` task (root project and when `bb4.publish` applied).

### Fixed

- Publishing: single **`pluginMaven`** publication for the `bb4-gradle` artifact (no duplicate overwrite with `mavenJava`).
- Maven deploy defaults updated for **Central Publisher Portal** after [OSSRH sunset](https://central.sonatype.org/pages/ossrh-eol/): snapshots → `central.sonatype.com/repository/maven-snapshots/`, releases → `ossrh-staging-api.central.sonatype.com` staging deploy. Optional `bb4.ossrh.snapshotUrl` / `bb4.ossrh.releaseStagingUrl` in `gradle.properties`.

### Docs

- [docs/publishing-sonatype.md](docs/publishing-sonatype.md) — Portal user tokens, default URLs, manual upload note for `maven-publish`, 405/401 troubleshooting.
- README rewritten for plugin consumption; snapshot repo URL for consumers.
- [docs/migration-from-scripts.md](docs/migration-from-scripts.md) for 1.x → 2.x.
- Scaladoc jar sources use scaladoc output (not javadoc) in publish plugin.
- Dropped invalid publishing of raw `.asc` files as extra artifacts.

[2.0.0]: https://github.com/barrybecker4/bb4-gradle/compare/1.9.x...HEAD
