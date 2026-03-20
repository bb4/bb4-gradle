# bb4-gradle

Published **Gradle convention plugins** for Barry’s bb4 Scala/Java projects. This repo replaces the old model (JAR of `apply from` Groovy scripts) with standard **`plugins { id("…") }`** IDs.

## Requirements

| Item | Version |
|------|---------|
| Gradle (wrapper) | **8.14.x** (see `gradle/wrapper/gradle-wrapper.properties`) |
| Java toolchain | **21** (enforced by `com.barrybecker4.bb4.base`) |
| Scala (default in plugins) | **3.8.2** (override via `bb4 { scalaVersion.set("…") }` if needed) |

## Published plugins

| Plugin ID | Purpose |
|-----------|---------|
| `com.barrybecker4.bb4.base` | Java 21 toolchain, shared repositories, resolution defaults, OSSRH property placeholders |
| `com.barrybecker4.bb4.scala-library` | `java-library` + Scala, bb4 source layouts, test deps (JUnit 5, ScalaTest), jar/manifest conventions |
| `com.barrybecker4.bb4.publish` | `maven-publish` + signing, Sonatype OSSRH, sources/javadoc/scaladoc jars; optional `jarMap` multi-artifact mode |
| `com.barrybecker4.bb4.application` | `application` plugin, `run` stdin, website deploy tasks, distribution tweaks |

Plugin implementations live under `src/main/groovy/`.

**Dependency versions** (Scala, ScalaTest, JUnit, etc. applied to your projects by these plugins) are maintained in one place: [`Bb4DependencyVersions.groovy`](src/main/groovy/com/barrybecker4/gradle/plugins/Bb4DependencyVersions.groovy).

## Consume in another project

Use the **Gradle Plugin Portal** and/or your Maven repo where you publish `bb4-gradle`, plus matching **plugin marker** artifacts (`*.gradle.plugin`).

**settings.gradle** (or `.kts`):

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Required while using 2.0-SNAPSHOT from OSSRH (until a 2.0.x release hits Central):
        maven { url 'https://s01.oss.sonatype.org/content/repositories/snapshots/' }
        // Legacy namespace: try https://oss.sonatype.org/content/repositories/snapshots/ instead if resolution fails
    }
}
```

**build.gradle** (Groovy example):

```groovy
plugins {
    id 'com.barrybecker4.bb4.scala-library' version '2.0-SNAPSHOT' // or '2.0.0' when released
    id 'com.barrybecker4.bb4.publish' version '2.0-SNAPSHOT'   // if this module publishes
    id 'com.barrybecker4.bb4.application' version '2.0-SNAPSHOT' // optional, for apps
}

group = 'com.barrybecker4'
version = '1.0-SNAPSHOT'

bb4 {
    archivesBaseName.set('my-artifact')  // optional; defaults to project.name
    mainClass.set('com.example.Main')    // optional jar manifest / application main
    // scalaVersion.set('3.8.2')        // optional override
}
```

Apply only what you need:

- **Library** → `com.barrybecker4.bb4.scala-library` (applies **base** conventions internally: Java 21 toolchain, repos, resolution). Use **`com.barrybecker4.bb4.base` alone** only if you want toolchain/repos without applying Scala.
- **Publish to Sonatype** → add `com.barrybecker4.bb4.publish`.
- **CLI / dist / deploy** → add `com.barrybecker4.bb4.application`.

Source layout expected by `scala-library`:

- Java: `source/`, tests: `test/`
- Scala: `scala-source/`, tests: `scala-test/`

## Publish this project (`bb4-gradle`)

1. Set version in `build.gradle.kts` (release = no `-SNAPSHOT`).
2. Configure credentials in `~/.gradle/gradle.properties` (or env vars): `ossrhToken`, `ossrhTokenPassword` (or `OSSRH_USERNAME` / `OSSRH_PASSWORD`).
3. Run:

```bash
./gradlew publishArtifacts
```

Snapshots use OSSRH snapshot URL; releases use staging deploy (signing required for non-SNAPSHOT).

Details, troubleshooting (e.g. HTTP 405), and optional URL overrides: [docs/publishing-sonatype.md](docs/publishing-sonatype.md).

## Migration from script JAR (1.x)

Legacy `buildscript { classpath 'com.barrybecker4:bb4-gradle:…' }` + `apply from: …getResource('bb4.gradle')` is **removed** in 2.x. See [docs/migration-from-scripts.md](docs/migration-from-scripts.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
