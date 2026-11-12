# Migration: bb4-gradle 1.x scripts → 2.x plugins

## Summary

| 1.x | 2.x |
|-----|-----|
| `buildscript` + `classpath 'com.barrybecker4:bb4-gradle:…'` | `pluginManagement` + `plugins { id("com.barrybecker4.bb4.…") version "…" }` |
| `apply from: …getResource('bb4.gradle')` | `id("com.barrybecker4.bb4.scala-library")` (and optionally `bb4.base` is applied inside scala-library) |
| `apply from: …'bb4-publish.gradle'` | `id("com.barrybecker4.bb4.publish")` |
| `apply from: …'bb4-deploy.gradle'` | `id("com.barrybecker4.bb4.application")` |
| Java 17 / Scala 3.3.x defaults | Java **25** / Scala **3.10.0** defaults (override via `bb4 { scalaVersion.set("…") }`) |

## Before (1.x)

```groovy
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.barrybecker4:bb4-gradle:1.9.0'
    }
}

apply from: project.buildscript.classLoader.getResource('bb4.gradle').toURI()
// … project-specific config …
apply from: project.buildscript.classLoader.getResource('bb4-publish.gradle').toURI()
apply from: project.buildscript.classLoader.getResource('bb4-deploy.gradle').toURI()
```

## After (2.x)

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

**build.gradle**:

```groovy
plugins {
    id 'com.barrybecker4.bb4.scala-library' version '2.0.0'
    id 'com.barrybecker4.bb4.publish' version '2.0.0'      // if publishing
    id 'com.barrybecker4.bb4.application' version '2.0.0' // if using app + deploy*
}

bb4 {
    archivesBaseName.set('my-lib')
    mainClass.set('com.example.Main')  // if applicable
}

// Remove duplicate declarations now provided by plugins:
// - scala-library, java-library, repositories (unless you need extras)
// - scala3-library / scalatest / junit / vecmath (unless you need different versions)
```

## Canary rollout (one repository at a time)

1. Publish **bb4-gradle 2.x** to Maven local or Sonatype snapshot: `./gradlew publishToMavenLocal` or `publishArtifacts`.
2. In **one** downstream repo:
   - Upgrade **Gradle wrapper** to **9.8.2** (or the version in bb4-gradle’s `gradle-wrapper.properties`).
   - Replace `buildscript` / `apply from` with `plugins { }` as above.
   - Delete redundant `dependencies` / `compileOptions` that duplicate plugin defaults.
   - Run `./gradlew clean test jar` (and `distZip` / `run` for apps).
   - If publishing a **release** (non-SNAPSHOT): after `./gradlew publish`, run the Staging API
     **promote** curl and click **Publish** in the Central Portal — see
     [docs/publishing-sonatype.md](../docs/publishing-sonatype.md). Same process as releasing bb4-gradle.
3. Repeat for each bb4 repo (libraries first, then applications).

## Compatibility matrix

| bb4-gradle | Gradle | Java | Scala (default) |
|-------------|--------|------|-----------------|
| 2.0.x | 8.14+ | 21 | 3.10.0 |
| 2.1.x | 9.1+ | 25 | 3.10.0 |
| 3.0.x | 9.1+ | 25 | 3.10.0 |

**Note on Java 25**: The Java 25 toolchain requirement applies both to compiling `bb4-gradle` itself and to the Gradle daemon JVM running the plugins. Consumer projects default to Java 25 bytecode unless you explicitly set a `--release` flag to target older JVMs.

## Migration to 3.0 (settings plugin + catalog)

3.0 adds `com.barrybecker4.bb4.settings`, `bb4-platform`, and `bb4-catalog`. Prefer:

```groovy
pluginManagement {
    def sibling = new File(settings.settingsDir, '../bb4-gradle')
    if (sibling.directory && gradle.parent == null) includeBuild(sibling)
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
    }
}
plugins {
    id 'com.barrybecker4.bb4.settings' version '3.0-SNAPSHOT'
}
```

```groovy
plugins {
    alias(bb4.plugins.scala.library)
    alias(bb4.plugins.publish)
}
dependencies {
    implementation bb4.common
}
```

Adopting the settings plugin replaces per-repo Foojay lines and hand-written library `includeBuild` blocks. **`bb4-puzzles` and `bb4-simulations` must delete those `includeBuild` blocks** when they switch, or Gradle rejects a duplicate included build. Pass `-Pbb4.includeSiblings=false` to keep snapshot resolution without sibling substitution.

## `jarMap` (multi-jar publications)

When `bb4.publish` is applied and you need to publish multiple artifacts from one project, configure `jarMap` in the `bb4 {}` extension block **after** `plugins {}`:

### Dependency Metadata

Each `jarMap` publication automatically includes runtime dependencies in its POM:
- **`implementation`** dependencies → `compile` scope (available at compile-time and runtime for consumers)
- **`runtimeOnly`** dependencies → `runtime` scope (available only at runtime for consumers)

This ensures that consumers of split artifacts (e.g., `bb4-common`, `bb4-ui`) receive required external dependencies (Scala library, Guava, etc.) transitively without manual reconstruction.

**Recommendation for new projects**: Consider using Gradle's native multi-module structure instead of `jarMap` for better IDE support, clearer dependency boundaries, and simpler configuration. The `jarMap` feature exists for legacy compatibility with existing published artifacts.
