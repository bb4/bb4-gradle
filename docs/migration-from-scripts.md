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
    // repository.set('bb4-my-lib')   // optional; overrides project name for POM GitHub URLs
    // (bb4 {} is created by bb4.base, which scala-library applies)
}

// Remove duplicate declarations now provided by plugins:
// - scala-library, java-library, repositories (unless you need extras)
// - scala3-library / scalatest / junit (unless you need different versions)
// Declare domain deps yourself when needed:
//   api bb4Libs.vecmath
//   bb4 { htmlReports { enabled = true } }   // ScalaTest HTML reporter (scala-xml + flexmark)
//   implementation platform(bb4Libs.bb4.platform)  // e.g. align transitive gson
```

## Canary rollout (one repository at a time)

1. Publish **bb4-gradle 2.x** to Maven local or Sonatype snapshot: `./gradlew publishToMavenLocal` or `publishArtifacts`.
2. In **one** downstream repo:
   - Upgrade **Gradle wrapper** to **9.8.2** (or the version in bb4-gradle’s `gradle-wrapper.properties`).
   - Replace `buildscript` / `apply from` with `plugins { }` as above.
   - Delete redundant `dependencies` / `compileOptions` that duplicate plugin defaults.
   - Run `./gradlew clean test jar` (and `distZip` / `run` for apps).
   - If publishing: `./gradlew publishArtifacts` (one task for snapshots and releases; see
     [docs/publishing-sonatype.md](../docs/publishing-sonatype.md)).
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
    if (sibling.directory && gradle.parent == null) includeBuild(sibling.canonicalFile)
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
    }
    plugins {
        id 'com.barrybecker4.bb4.settings' version '3.0-SNAPSHOT'
    }
}
plugins {
    id 'com.barrybecker4.bb4.settings'
}
```

```groovy
plugins {
    alias(bb4Libs.plugins.scala.library)
    alias(bb4Libs.plugins.publish)
}
dependencies {
    implementation bb4Libs.common
}
```

Adopting the settings plugin replaces per-repo Foojay lines and hand-written library `includeBuild` blocks. **`bb4-puzzles` and `bb4-simulations` must delete those `includeBuild` blocks** when they switch, or Gradle rejects a duplicate included build. Pass `-Pbb4.includeSiblings=false` to keep snapshot resolution without sibling substitution.

## `bb4.apps` (multi-app run / start scripts / deploy)

With `bb4.application`, declare apps in `bb4 {}` instead of hand-rolling `ext.appMap` loops:

```groovy
bb4 {
    mainClass.set('com.example.DefaultMain')
    apps.put('sudoku', [
        title: 'Sudoku',
        mainClass: 'com.example.Sudoku',
        command: 'bb4-sudoku',
        description: '…',
        longDescription: '…',
        mx: '8G',                    // accepted as maxHeap
        // startOnFirstThreadMac: true,
        // panel_class: 'com.example.Panel',  // default args: -panel_class …
    ])
}
```

The plugin registers `runSudoku`, `createSudokuApp`, fills deployHtml from the same model, and (when `jpackage.enabled`) builds installers. Delete local `appMap.each { JavaExec / CreateStartScripts }` and jarMap `dependsOn` blocks. Schemas live under `source/schema` (or `bb4.schemaDirectory`); do not disable `deploySchemas` just to avoid a whole-project scan.

Release installers: call the org reusable workflow instead of copying matrix YAML:

```yaml
jobs:
  installers:
    uses: bb4/.github/.github/workflows/jpackage-release.yml@main
    permissions:
      contents: write
```

## `jarMap` (multi-jar publications) — deprecated / compat only

`jarMap` slices one compilation into many Maven artifacts. It remains temporarily for
legacy multi-artifact publishing. Prefer **one artifact per library repo**. App /
installer-only repos should not use `jarMap` or `bb4.publish` at all — jpackage uses
the project `jar` when `jarMap` is empty.

When `bb4.publish` is applied and you still need multiple artifacts from one project,
configure `jarMap` in the `bb4 {}` extension block **after** `plugins {}`:

### Dependency Metadata

Each `jarMap` publication automatically includes runtime dependencies in its POM:
- **`implementation`** dependencies → `compile` scope (available at compile-time and runtime for consumers)
- **`runtimeOnly`** dependencies → `runtime` scope (available only at runtime for consumers)

This ensures that consumers of split artifacts receive required external dependencies
transitively without manual reconstruction.

**Recommendation**: Do not start new `jarMap` usage. Library repos publish one artifact;
app repos drop publish entirely (see below).

## Migrating app repos off publish + jarMap (#42)

For each app repo (`bb4-games`, `bb4-puzzles`, `bb4-simulations`, `bb4-adventure`,
`bb4-aikido-app`, `bb4-Q-learning`, `bb4-experimentation`):

1. Require a `bb4-gradle` version that supports jpackage without jarMap.
2. Remove `alias(bb4Libs.plugins.publish)` / `bb4.publish`.
3. Delete all `jarMap` / `ext.jarMap` and Sonatype publish-throttle `afterEvaluate` blocks.
4. Keep `scala-library` + `application` (or existing custom `run*` tasks).
5. For jpackage: remove `mainJarKey` if it only pointed at a jarMap key; keep `apps`, icons,
   `packageIdentifierPrefix`.
6. CI: stop `publishArtifacts` / Central credentials; keep build/test and installer workflows.
7. README: describe as app / installers only, not published to Sonatype.

After all app repos migrate, upgrade `bb4-gradle` again to a release that removes jarMap entirely.
Optional: update `bb4-website` so leaf apps are not described as deployed to Sonatype.
