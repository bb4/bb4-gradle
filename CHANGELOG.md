# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Changed

- **Published Maven POM metadata:** Updated project URL and SCM URLs to point to the new `bb4` GitHub organization (`https://github.com/bb4/`) instead of the legacy `barrybecker4` user account. Maven groupId, plugin IDs, package names, and developer identities remain unchanged.

### Added

- **Platform ([#37](https://github.com/bb4/bb4-gradle/issues/37)):** `com.barrybecker4:bb4-platform` (BOM) and `com.barrybecker4:bb4-catalog` (version catalog) from the `:platform` subproject. Per-library version lines live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml); the plugin version key `bb4-gradle` is rewritten from this project's version at publish.
- **`com.barrybecker4.bb4.settings`:** Settings plugin that enforces Gradle **9.1+**, applies Foojay 1.0.0, adds the Central snapshots repository, installs the `bb4Libs` catalog, and `includeBuild`s sibling `bb4-*` checkouts (disable with `-Pbb4.includeSiblings=false`).
- **Single 3.0 floor:** Gradle **9.1+**, Java **25**, wrapper **9.8.2**.
- **`bb4.application`:** Opt-in native installers via `bb4.jpackage { enabled = true … }`. Registers `jpackage<AppId>` and `jpackageAllApps` from `ext.appMap` (skipping `deploy: false`), syncs `jarMap` jars into `installDist` `lib/`, and renames artifacts to `bb4-<appId>-<version>.{dmg,msi,deb}`. Configure `packageIdentifierPrefix`, `mainJarKey`, `defaultMainClass`, `javaOptions`, `arguments`, optional `consoleDesktopTemplate`, and optional `jpackageExecutable` (for tests). App-specific launch args stay in the consuming project.

### Fixed

- **`bb4.application`:** Removed unsafe classpath wildcard manipulation from generated start scripts. The previous implementation replaced Gradle's properly quoted explicit classpath with `CLASSPATH=$(echo $APP_HOME/lib/*.jar | tr ' ' ':')`, which broke when installation paths or JAR names contained spaces and picked up unintended JARs from `lib/`. Now uses Gradle's default explicit classpath generation that works correctly with spaces and only includes declared runtime dependencies ([#16](https://github.com/bb4/bb4-gradle/issues/16)).

## [2.0.0] - 2026-08-02

### Breaking

- **Removed** shipping Groovy scripts as classpath resources (`bb4.gradle`, `bb4-publish.gradle`, `bb4-deploy.gradle`). Downstream projects must use published **plugin IDs** instead of `apply from:`.
- **Removed** old Gradle 7.x wrapper; minimum supported Gradle is **8.14.x** (aligned with current wrapper).
- **Java baseline** is **21** (toolchain), replacing compile `release`/target **17** from 1.x scripts.
- **Scala** default bumped to **3.8.2** with updated test stack (JUnit 5, ScalaTest 3.2.19, etc.). (Later updated to 3.10.0, ScalaTest 3.2.20, JUnit 5.14.4, scala-xml 2.5.0 — see issue #13)
- **`bb4.base`** is explicit; **`bb4.scala-library`** applies base conventions internally (same as before logically, but ids are separate artifacts).

### Added

- Convention plugins: `com.barrybecker4.bb4.base`, `com.barrybecker4.bb4.scala-library`, `com.barrybecker4.bb4.publish`, `com.barrybecker4.bb4.application`.
- `bb4 { }` extension: `archivesBaseName`, `mainClass`, `scalaVersion`.
- Foojay toolchain resolver convention in `settings.gradle.kts` for JDK provisioning.
- Dependency versions for consumers are defined only in `Bb4DependencyVersions.groovy` (no duplicate version catalog).
- `publishArtifacts` task (root project and when `bb4.publish` applied).

### Fixed

- **`bb4.publish`:** use `signing.required = …` (property assignment) instead of deprecated `required { … }` for Gradle 8+ `SigningExtension`.
- Publishing: single **`pluginMaven`** publication for the `bb4-gradle` artifact (no duplicate overwrite with `mavenJava`).
- Maven deploy defaults updated for **Central Publisher Portal** after [OSSRH sunset](https://central.sonatype.org/pages/ossrh-eol/): snapshots → `central.sonatype.com/repository/maven-snapshots/`, releases → `ossrh-staging-api.central.sonatype.com` staging deploy. Optional `bb4.ossrh.snapshotUrl` / `bb4.ossrh.releaseStagingUrl` in `gradle.properties`.
- Apply Central-required POM metadata (URL, license, SCM, developers) to **all** Maven publications, including Gradle **plugin marker** POMs — marker-only omissions caused Portal **FAILED** validation on first 2.0.0 upload.

### Docs

- [docs/publishing-sonatype.md](docs/publishing-sonatype.md) — end-to-end release checklist; required **`manual/upload`** promote after `gradle publish` (empty Deployments otherwise); POM / marker rules; drop-and-retry for FAILED deployments; 403/401/405 troubleshooting.
- README publish section mirrors the promote + Portal **Publish** steps.
- [docs/migration-from-scripts.md](docs/migration-from-scripts.md) for 1.x → 2.x.
- Scaladoc jar sources use scaladoc output (not javadoc) in publish plugin.
- Dropped invalid publishing of raw `.asc` files as extra artifacts.
[2.0.0]: https://github.com/bb4/bb4-gradle/compare/1.9.x...HEAD
