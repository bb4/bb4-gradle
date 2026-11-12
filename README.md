# bb4-gradle

📊 [Build status for all bb4 projects](https://github.com/bb4/.github)

Published **Gradle convention plugins** for Barry’s bb4 Scala/Java projects.

## Requirements

| Item | Version |
|------|---------|
| Gradle (minimum) | **9.1+** (enforced by `com.barrybecker4.bb4.settings`) |
| Gradle (wrapper in this repo) | **9.8.2** (see `gradle/wrapper/gradle-wrapper.properties`) |
| Java toolchain | **25** (enforced by `com.barrybecker4.bb4.base`) |
| Scala (default in plugins) | **3.10.0** (override via `bb4 { scalaVersion.set("…") }` if needed) |

### Java Toolchain and Bytecode Compatibility

The **Java 25 toolchain** requirement means:

1. **This plugin** (`bb4-gradle`) is compiled with Java 25, and requires a Gradle daemon running on Java 25 or later to execute.
2. **Consumer projects** that apply `com.barrybecker4.bb4.base` or `com.barrybecker4.bb4.scala-library` will default to Java 25 toolchains for compilation.

**Bytecode target**: By default, consumer projects produce **Java 25 bytecode**. If your downstream consumers must run on older JVMs, you can override the bytecode/API level using `--release`:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.release = 21  // or 17, etc.
}
```

For Scala projects targeting older runtimes, configure the Scala compiler's `-release` or `-target` flag similarly. Note that using a newer toolchain with an older release target is the recommended approach for forward compatibility while maintaining backward runtime support.

**Minimum supported:** Gradle **9.1+** and JDK **25**.

## Published plugins

| Plugin ID | Purpose |
|-----------|---------|
| `com.barrybecker4.bb4.settings` | Settings plugin: Gradle 9.1 floor, Foojay, Central snapshots repo, `bb4Libs` version catalog, sibling `includeBuild` substitution |
| `com.barrybecker4.bb4.base` | Java 25 toolchain, shared repositories, resolution defaults, Central Portal credential property placeholders |
| `com.barrybecker4.bb4.scala-library` | `java-library` + Scala, bb4 source layouts, core test deps (JUnit 5.14 + ScalaTest, no Vintage), default Scala flags (`-deprecation`, `-feature`, `-Wunused:imports`), quiet failure test logging with assertions, jar/manifest conventions; optional `bb4.htmlReports` |
| `com.barrybecker4.bb4.publish` | `maven-publish` + signing, Maven Central / Central Portal, sources/javadoc/scaladoc jars (single artifact) |
| `com.barrybecker4.bb4.application` | `application` plugin, `run` stdin, `bb4.apps` → `run*` / `create*App`, website deploy tasks, distribution tweaks; optional `bb4.jpackage` native installers |

Also published from this repo:

| Coordinate | Purpose |
|------------|---------|
| `com.barrybecker4:bb4-platform` | Maven BOM of recommended bb4 library and third-party versions |
| `com.barrybecker4:bb4-catalog` | The same versions as a Gradle version catalog |

Plugin implementations live under [`src/main/kotlin/`](src/main/kotlin/com/barrybecker4/gradle/plugins/). Version numbers live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) (packaged into the plugin jar; [`Bb4DependencyVersions`](src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4DependencyVersions.kt) reads the third-party keys).

## Consume in another project

Plugins publish to Maven Central / snapshots (not only the Plugin Portal). Gradle resolves the settings plugin before that plugin can add repositories, so a short `pluginManagement` block remains:

**settings.gradle** (or `.kts`):

```groovy
pluginManagement {
    def sibling = new File(settings.settingsDir, '../bb4-gradle')
    // Included builds must not include bb4-gradle again.
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

The settings plugin installs the `bb4Libs` catalog (not named `bb4`, which is the project extension), applies Foojay, and `includeBuild`s neighboring `bb4-*` checkouts. Pass `-Pbb4.includeSiblings=false` to disable sibling substitution.

**build.gradle** (Groovy example):

```text
plugins {
    alias(bb4Libs.plugins.scala.library)
    alias(bb4Libs.plugins.publish)       // if this module publishes
    alias(bb4Libs.plugins.application)   // optional, for apps
}

group = 'com.barrybecker4'
version = '3.0-SNAPSHOT'

dependencies {
    implementation bb4Libs.common
    implementation bb4Libs.math
    // Domain / alignment (not applied by scala-library):
    // api bb4Libs.vecmath
    // implementation bb4Libs.gson
    // implementation platform(bb4Libs.bb4.platform)
    // Or without the catalog:
    // implementation platform('com.barrybecker4:bb4-platform:3.0-SNAPSHOT')
    // implementation 'com.barrybecker4:bb4-common'
}

bb4 {
    archivesBaseName.set('my-artifact')  // optional; defaults to project.name
    mainClass.set('com.example.Main')    // optional jar manifest / application main
    // repository.set('bb4-my-lib')      // optional GitHub repo name for POM url/scm
    // scalaVersion.set('3.10.0')        // optional override (scala-library)

    // Optional: ScalaTest HTML reporter runtime deps (scala-xml + flexmark-all)
    // htmlReports {
    //     enabled = true
    // }
    
    // Optional: multi-app model (run*, create*App start scripts, deployHtml, jpackage).
    // Prefer apps over appMap / ext.appMap. Map key mx is accepted as maxHeap.
    // apps.put('demo', [title: 'Demo', mainClass: 'com.example.Main', command: 'bb4-demo',
    //                   description: 'd', longDescription: 'd', mx: '512m'])

    // Optional: schema root for deploySchemas (default source/schema)
    // schemaDirectory.set(layout.projectDirectory.dir('source/schema'))

    // Optional: native installers (requires apps/appMap + LICENSE + packaging/icons/<appId>/)
    // Uses the project jar as --main-jar.
    // jpackage {
    //     enabled = true
    //     packageIdentifierPrefix = 'com.example'
    //     defaultMainClass = 'com.example.Main'
    //     javaOptions = { appId, props -> ['-ea', '-Dfile.encoding=UTF-8'] }
    //     arguments = { appId, props -> [] }               // each element -> --arguments
    //     // consoleDesktopTemplate = layout.projectDirectory.file('packaging/linux-console/template.desktop')
    // }
}
```

Each library keeps its own release version in the catalog. A one-library hotfix is: publish that library, bump its line in `gradle/libs.versions.toml`, and republish `bb4-catalog` / `bb4-platform`. An explicit module version in a consumer build overrides the BOM constraint.

### Library vs app repos

Apply `bb4.publish` only when another project may depend on your Maven coordinates.
App / installer-only repos (`bb4-games`, `bb4-puzzles`, `bb4-simulations`, `bb4-adventure`,
`bb4-aikido-app`, `bb4-Q-learning`, `bb4-experimentation`, and similar leaf apps) should use
`bb4.scala-library` + `bb4.application` (or custom `run*` tasks) **without** `bb4.publish`.

Apply only what you need:

- **Settings** → `com.barrybecker4.bb4.settings` (once per repo). Delete hand-written Foojay and library `includeBuild` blocks when adopting it.
- **Library** → `alias(bb4Libs.plugins.scala.library)` (applies **base** conventions internally). Use **`bb4Libs.plugins.base` alone** only if you want toolchain/repos without applying Scala.
- **Publish to Sonatype** → add `alias(bb4Libs.plugins.publish)` (libraries only).
- **CLI / dist / deploy** → add `alias(bb4Libs.plugins.application)`. Configure `bb4.apps` (or legacy `appMap`) so the plugin registers `run*`, `create*App` start scripts, and deploy helpers. Schemas copy from `bb4.schemaDirectory` (default `source/schema`). Prefer `installDist` over the removed `copyToLib` task.
- **Native installers** → with `bb4.application`, set `bb4.jpackage.enabled = true`. Requires `bb4.apps` (or legacy appMap), a `LICENSE` file, and `packaging/icons/<appId>/icon.{icns,ico,png}`. Uses the project `jar` as `--main-jar` (`bb4.publish` not required). Run `./gradlew jpackageAllApps` (or `jpackage<AppId>`). Release CI can call the reusable workflow [`bb4/.github/.github/workflows/jpackage-release.yml`](https://github.com/bb4/.github/blob/main/.github/workflows/jpackage-release.yml).

### Plugin composition and ordering

The plugins are designed to work in **any application order**:

- **`bb4.publish`** can be applied before or after `bb4.scala-library`. It requires the `java` or `java-library` plugin to be present (applied automatically by `scala-library`). For **Java-only projects** (using only `bb4.base` + `java-library`), `bb4.publish` will publish sources and javadoc jars without requiring Scaladoc.
- **`bb4.application`** can be applied before or after `bb4.scala-library` / `bb4.base`. When `bb4.base` is present, `bb4.mainClass` is wired to `application.mainClass`. The standard `application` extension remains fully usable.
- **`bb4 {}`** is owned by **`bb4.base`** (also applied inside `scala-library`).

**Supported combinations:**

| Plugins applied | Result |
|----------------|--------|
| `bb4.base` alone | Java 25 toolchain, repositories, resolution defaults, and `bb4 {}` extension |
| `bb4.base` + `java-library` | Plain Java library (no Scala) with typed `bb4 {}` |
| `bb4.base` + `java-library` + `bb4.publish` | Plain Java library with Maven Central publishing (sources + javadoc); set `bb4.repository` / `archivesBaseName` on the extension |
| `bb4.scala-library` | Scala + Java library (includes `bb4.base` internally) |
| `bb4.scala-library` + `bb4.publish` | Scala library with publishing (sources + javadoc + scaladoc) |
| `bb4.scala-library` + `bb4.application` | App / installer-only Scala project (`bb4.mainClass` wired; no Central publish) |
| `bb4.scala-library` + `bb4.publish` + `bb4.application` | Scala app that also publishes library coordinates (rare) |

Source layout expected by `scala-library`:

- Java: `source/`, tests: `test/`
- Scala: `scala-source/`, tests: `scala-test/`
- Non-source files in those trees (for example `package.png` beside a package) are packaged as resources; 
 `.java` and `.scala` are excluded from the resource pipeline so they are not duplicated into jars.

## Test this project (`bb4-gradle`)

The plugin project includes a Gradle TestKit functional test suite in `src/test/groovy`. The suite exercises plugin behavior in temporary consumer projects, covering:

- Plugin application and combinations (all four public plugin IDs)
- Java/Scala compilation with toolchain configuration
- Test engine discovery and execution (JUnit Jupiter 5.14 + ScalaTest; failure-only logging, assertions on)
- Default Scala compiler flags (`-deprecation`, `-feature`, `-Wunused:imports`)
- Maven publication coordinates, POM metadata, and signing decisions (SNAPSHOT vs. release)
- Application distributions and scripts (including paths with spaces and custom build directories)
- Extension value customization

Run the tests locally:

```bash
./gradlew test
```

The CI workflow (`.github/workflows/ci.yml`) runs the suite on every push and pull request.

## Publish this project (`bb4-gradle`)

Full runbook: [docs/publishing-sonatype.md](docs/publishing-sonatype.md). Short version:

1. Set `version` in `build.gradle.kts` (release = **no** `-SNAPSHOT`).
2. Credentials + GPG: `ossrhToken` / `ossrhTokenPassword` (or `OSSRH_*` env), plus either
   keyring signing props or in-memory `signingKey` / `signingPassword`
   (Central Portal **user token**, not legacy OSSRH).
3. `./gradlew clean build` then **`./gradlew publishArtifacts`**.
   - Releases: signed bundle → Central Publisher API (`AUTOMATIC` by default).
   - Snapshots: deploy to Central maven-snapshots.
4. After Central is live, commit/tag the release and bump `version` to the next `-SNAPSHOT`.

POM metadata uses `project.group`, `project.description`, and `bb4.repository` (GitHub repo name).
A CycloneDX SBOM (`classifier=cyclonedx`) is attached to every publication.

## Migration from script JAR (1.x)

Legacy `buildscript { classpath 'com.barrybecker4:bb4-gradle:…' }` + `apply from: …getResource('bb4.gradle')` is **removed** in 2.x. See [docs/migration-from-scripts.md](docs/migration-from-scripts.md).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
