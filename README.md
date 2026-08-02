# bb4-gradle

Published **Gradle convention plugins** for Barry’s bb4 Scala/Java projects.

## Requirements

| Item | Version |
|------|---------|
| Gradle (wrapper) | **8.14.x** (see `gradle/wrapper/gradle-wrapper.properties`) |
| Java toolchain | **21** (enforced by `com.barrybecker4.bb4.base`) |
| Scala (default in plugins) | **3.8.2** (override via `bb4 { scalaVersion.set("…") }` if needed) |

## Published plugins

| Plugin ID | Purpose |
|-----------|---------|
| `com.barrybecker4.bb4.base` | Java 21 toolchain, shared repositories, resolution defaults, Central Portal credential property placeholders |
| `com.barrybecker4.bb4.scala-library` | `java-library` + Scala, bb4 source layouts, test deps (JUnit 5, ScalaTest), jar/manifest conventions |
| `com.barrybecker4.bb4.publish` | `maven-publish` + signing, Maven Central / Central Portal, sources/javadoc/scaladoc jars; optional `jarMap` multi-artifact mode |
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
        // Only needed when consuming a -SNAPSHOT plugin build:
        // maven { url 'https://central.sonatype.com/repository/maven-snapshots/' }
    }
}
```

**build.gradle** (Groovy example):

```groovy
plugins {
    id 'com.barrybecker4.bb4.scala-library' version '2.0.0'
    id 'com.barrybecker4.bb4.publish' version '2.0.0'   // if this module publishes
    id 'com.barrybecker4.bb4.application' version '2.0.0' // optional, for apps
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

Full runbook: [docs/publishing-sonatype.md](docs/publishing-sonatype.md). Short version:

1. Set `version` in `build.gradle.kts` (release = **no** `-SNAPSHOT`).
2. Credentials + GPG in `~/.gradle/gradle.properties`: `ossrhToken`, `ossrhTokenPassword`, signing props
   (Central Portal **user token**, not legacy OSSRH).
3. `./gradlew clean build` then `./gradlew publish`.
4. **Required for releases:** promote staging into the Portal (same IP as the upload), or the
   Deployments page stays empty:

   ```bash
   TOKEN_USER=$(grep -E '^ossrhToken=' "$HOME/.gradle/gradle.properties" | cut -d= -f2-)
   TOKEN_PASS=$(grep -E '^ossrhTokenPassword=' "$HOME/.gradle/gradle.properties" | cut -d= -f2-)
   AUTH=$(printf '%s:%s' "$TOKEN_USER" "$TOKEN_PASS" | base64 | tr -d '\n')
   curl -X POST \
     "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.barrybecker4?publishing_type=user_managed" \
     -H "Authorization: Bearer ${AUTH}" -H "accept: */*" -d ''
   ```

5. At [central.sonatype.com/publishing](https://central.sonatype.com/publishing), confirm **VALIDATED**, then **Publish**.
6. After Central is live, commit/tag the release and bump `version` to the next `-SNAPSHOT`.

Snapshots go to the Central snapshot repo (no promote step). Releases use the OSSRH Staging API
deploy URL and **must** use the promote curl above. POM metadata (URL, license, SCM, developers)
is applied to **all** publications including plugin markers — Central rejects marker-only omissions.

## Migration from script JAR (1.x)

Legacy `buildscript { classpath 'com.barrybecker4:bb4-gradle:…' }` + `apply from: …getResource('bb4.gradle')` is **removed** in 2.x. See [docs/migration-from-scripts.md](docs/migration-from-scripts.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
