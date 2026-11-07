# Migration: bb4-gradle 1.x scripts → 2.x plugins

## Summary

| 1.x | 2.x |
|-----|-----|
| `buildscript` + `classpath 'com.barrybecker4:bb4-gradle:…'` | `pluginManagement` + `plugins { id("com.barrybecker4.bb4.…") version "…" }` |
| `apply from: …getResource('bb4.gradle')` | `id("com.barrybecker4.bb4.scala-library")` (and optionally `bb4.base` is applied inside scala-library) |
| `apply from: …'bb4-publish.gradle'` | `id("com.barrybecker4.bb4.publish")` |
| `apply from: …'bb4-deploy.gradle'` | `id("com.barrybecker4.bb4.application")` |
| Java 17 / Scala 3.3.x defaults | Java **21** / Scala **3.8.2** defaults (override via `bb4 { scalaVersion.set("…") }`) |

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
   - Upgrade **Gradle wrapper** to **8.14.3** (or the version in bb4-gradle’s `gradle-wrapper.properties`).
   - Replace `buildscript` / `apply from` with `plugins { }` as above.
   - Delete redundant `dependencies` / `compileOptions` that duplicate plugin defaults.
   - Run `./gradlew clean test jar` (and `distZip` / `run` for apps).
   - If publishing a **release** (non-SNAPSHOT): after `./gradlew publish`, run the Staging API
     **promote** curl and click **Publish** in the Central Portal — see
     [docs/publishing-sonatype.md](../docs/publishing-sonatype.md). Same process as releasing bb4-gradle.
3. Repeat for each bb4 repo (libraries first, then applications).

## Compatibility matrix (2.x)

| bb4-gradle | Gradle | Java | Scala (default) |
|-------------|--------|------|-----------------|
| 2.0.x | 8.14+ | 21 | 3.8.2 |

## `jarMap` (multi-jar publications)

When `bb4.publish` is applied and you need to publish multiple artifacts from one project, configure `jarMap` in the `bb4 {}` extension block **after** `plugins {}`:

```groovy
plugins {
    id 'com.barrybecker4.bb4.scala-library' version '2.1.0'
    id 'com.barrybecker4.bb4.publish' version '2.1.0'
}

version = '1.2.3-SNAPSHOT'

bb4 {
    jarMap.put('common', [
        include: ['com/example/common/**']
    ])
    jarMap.put('ui', [
        include: ['com/example/ui/**'],
        exclude: ['**/*Test.class']
    ])
}
```

Each entry requires an `include` list of file patterns and optionally accepts `exclude` patterns. Publications are created with artifact IDs like `bb4-common`, `bb4-ui`, using the final `project.version`.

**Legacy:** `ext.jarMap` set before `plugins {}` still works but is deprecated. Use `bb4 { jarMap.put(...) }` instead.
