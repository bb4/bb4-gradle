# Retire jarMap; app repos stop publishing (#42) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make jpackage work on the single project jar, then remove `jarMap` from `bb4-gradle`, so app repos can drop Maven publishing.

**Architecture:** Keep a short compat window where jarMap still works for publish + jpackage, while empty jarMap uses the normal `jar` task as `--main-jar`. After app repos can migrate, delete jarMap APIs, publish multi-artifact wiring, and related tests. Consumer repo PRs are follow-on work outside this repository (checklist in migration docs).

**Tech Stack:** Gradle 9 convention plugins (Kotlin), Spock/JUnit TestKit functional tests, Groovy DSL consumers.

**Spec:** [docs/superpowers/specs/2026-11-12-jarMap-app-repos-design.md](../specs/2026-11-12-jarMap-app-repos-design.md)

## Global Constraints

- Library vs app: publish to Central only when another repo may depend on the coordinates; app repos do not apply `bb4.publish`.
- Do not invest in typed jarMap `dependOn` or real subproject generators.
- Abandoned Central coordinates stay frozen (no delete/republish).
- Catalog/BOM needs no app-coord removals (none listed).
- This plan implements **bb4-gradle only**; app-repo migrations are follow-on PRs after the compat release.

## File map

| File | Responsibility |
|------|----------------|
| `src/main/kotlin/.../Bb4ApplicationPlugin.kt` | jpackage/`installDist` main-jar resolution with and without jarMap |
| `src/main/kotlin/.../Bb4JpackageExtension.kt` | Document optional `mainJarKey`; default stays app-id for jarMap mode |
| `src/main/kotlin/.../Bb4PublishPlugin.kt` | (later) remove multi-jar / jarMap publication path |
| `src/main/kotlin/.../Bb4Extension.kt` | (later) remove `jarMap` / `JarMapEntry` / `Bb4JarMapContainer` |
| `src/main/kotlin/.../Bb4JarMap.kt` | (later) delete |
| `src/test/groovy/.../Bb4JpackageFunctionalTest.groovy` | TestKit: jpackage without jarMap or publish |
| `src/functionalTest/groovy/.../JarMapTimingTest.groovy` | (later) delete with jarMap |
| `README.md`, `docs/migration-from-scripts.md`, `CHANGELOG.md` | Library-vs-app rule; jarMap removal notes |

---

### Task 1: jpackage works without jarMap

**Files:**
- Modify: `src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4ApplicationPlugin.kt` (`registerJpackageTasks`)
- Modify: `src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4JpackageExtension.kt` (KDoc on `mainJarKey`)
- Test: `src/test/groovy/com/barrybecker4/gradle/plugins/Bb4JpackageFunctionalTest.groovy`

**Interfaces:**
- Consumes: `Bb4JarMap.resolve`, project `jar` task (`org.gradle.api.tasks.bundling.Jar`), `installDist` lib dir `build/install/<project.name>/lib`
- Produces: When jarMap is empty, jpackage uses `project.tasks.named("jar").get().archiveFile.get().asFile.name` as `--main-jar` and does not require `bb4.publish` or jarMap; when jarMap is non-empty, existing `mainJarKey` → `${key}Jar` behavior is unchanged

- [ ] **Step 1: Write the failing TestKit test**

Add to `Bb4JpackageFunctionalTest.groovy`:

```groovy
@Test
void 'jpackage works without jarMap or publish plugin'() {
    settingsFile.toFile().text = settingsGradle()
    writeIcons('demo')
    buildFile.toFile().text = """
        plugins {
            id 'com.barrybecker4.bb4.scala-library'
            id 'com.barrybecker4.bb4.application'
        }
        group = 'com.test'
        version = '1.0-SNAPSHOT'
        bb4 {
            archivesBaseName.set('bb4-demo')
            mainClass.set('com.test.Main')
            apps.put('demo', [
                title: 'Demo App', mainClass: 'com.test.Main',
                command: 'demo', description: 'A demo', longDescription: 'd'
            ])
            jpackage {
                enabled = true
                packageIdentifierPrefix = 'com.test.demo'
                jpackageExecutable = file('${stubJpackage.toAbsolutePath().toString().replace('\\', '/')}')
                defaultMainClass = 'com.test.Main'
            }
        }
    """

    def result = runGradle('jpackageDemo', '--stacktrace')
    assertEquals(TaskOutcome.SUCCESS, result.task(':jpackageDemo').outcome)

    def outDir = testProjectDir.resolve('build/jpackage/demo').toFile()
    def renamed = new File(outDir, "bb4-demo-1.0.${installerType}")
    assertTrue(renamed.isFile(), "expected renamed installer ${renamed}")

    def argsText = new File(outDir, 'jpackage-args.txt').text
    assertTrue(argsText.contains('--main-jar'))
    assertTrue(
        argsText.contains('bb4-demo') || argsText.contains('jpackage-test'),
        "expected project jar name in --main-jar, got:\\n${argsText}"
    )
    assertFalse(argsText.contains('demoJar'), "must not require jarMap demoJar task")
}
```

Keep existing jarMap-based tests unchanged (compat window).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.barrybecker4.gradle.plugins.Bb4JpackageFunctionalTest.jpackage\\ works\\ without\\ jarMap\\ or\\ publish\\ plugin`

Expected: FAIL — configuration error containing `jarMap is empty` (or task `:jpackageDemo` missing / build failed for that reason).

- [ ] **Step 3: Implement empty-jarMap path in `registerJpackageTasks`**

In `Bb4ApplicationPlugin.kt`, replace the hard failure when `jarMap.isEmpty()` with dual-path logic. Sketch of the intended structure inside `registerJpackageTasks` after `appMap` is non-empty:

```kotlin
val jarMap = Bb4JarMap.resolve(project, bb4)
val useJarMap = jarMap.isNotEmpty()
val jarMapJarTaskNames = if (useJarMap) jarMap.keys.map { key -> "${key}Jar" } else emptyList()

project.extensions.extraProperties.set("bb4.jpackage.registered", true)
// ... osName / jpackageType / jpackageVersion / jpackageBin as today ...

if (useJarMap) {
    project.tasks.named("installDist").configure { it.dependsOn(jarMapJarTaskNames) }
    project.tasks.named("startScripts").configure { it.dependsOn(jarMapJarTaskNames) }
} else {
    project.tasks.named("installDist").configure { it.dependsOn("jar") }
}

fun syncJarMapIntoInstallLib(installLib: java.io.File) {
    if (!useJarMap) {
        return
    }
    jarMap.keys.forEach { key ->
        // existing copy of ${key}Jar into installLib
    }
}

fun resolveMainJarName(appId: String, propMap: Map<String, Any?>, installLib: java.io.File): String {
    if (!useJarMap) {
        val projectJar = project.tasks.named("jar", org.gradle.api.tasks.bundling.Jar::class.java)
            .get().archiveFile.get().asFile
        val mainJarName = projectJar.name
        val mainJarFile = java.io.File(installLib, mainJarName)
        if (!mainJarFile.isFile) {
            throw GradleException(
                "jpackage $appId: missing $mainJarName in $installLib. " +
                    "Run installDist so the project jar is in lib/.",
            )
        }
        return mainJarName
    }
    val mainJarKey = Bb4JpackageSupport.invokeConfigured(
        jp.mainJarKey,
        appId,
        propMap,
    ) as String
    if (!jarMap.containsKey(mainJarKey)) {
        throw GradleException(
            "jpackage $appId: no jarMap entry for mainJarKey '$mainJarKey'",
        )
    }
    val mainJarName = project.tasks.named(
        "${mainJarKey}Jar",
        org.gradle.api.tasks.bundling.Jar::class.java,
    ).get().archiveFile.get().asFile.name
    val mainJarFile = java.io.File(installLib, mainJarName)
    if (!mainJarFile.isFile) {
        throw GradleException(
            "jpackage $appId: missing $mainJarName in $installLib. " +
                "Run installDist after all jarMap jars are built.",
        )
    }
    return mainJarName
}
```

In each jpackage task `doFirst`, call `syncJarMapIntoInstallLib(installLib)` then `val mainJarName = resolveMainJarName(appId, propMap, installLib)` instead of the current inline mainJarKey lookup. Task `dependsOn` should include `jarMapJarTaskNames` only when `useJarMap` is true (still `dependsOn("installDist")` always).

Update `Bb4JpackageExtension` KDoc for `mainJarKey`:

```kotlin
/**
 * Closure `(String appId, Map props) -> String` selecting the jarMap key
 * whose jar is passed as `--main-jar` when jarMap is non-empty.
 * Ignored when jarMap is empty — jpackage uses the project `jar` task output.
 * Defaults to the app id.
 */
var mainJarKey: Any = { appId: String, _: Map<*, *> -> appId }
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew test --tests com.barrybecker4.gradle.plugins.Bb4JpackageFunctionalTest
```

Expected: all `Bb4JpackageFunctionalTest` methods PASS (new test + existing jarMap stub tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4ApplicationPlugin.kt \
  src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4JpackageExtension.kt \
  src/test/groovy/com/barrybecker4/gradle/plugins/Bb4JpackageFunctionalTest.groovy
git commit -m "$(cat <<'EOF'
Allow jpackage to use the project jar when jarMap is empty.

EOF
)"
```

---

### Task 2: Document library-vs-app rule and jpackage without jarMap

**Files:**
- Modify: `README.md`
- Modify: `docs/migration-from-scripts.md`
- Modify: `CHANGELOG.md` (`[Unreleased]`)

**Interfaces:**
- Consumes: Task 1 behavior (empty jarMap → project jar)
- Produces: Written library-vs-app rule; jpackage no longer documented as requiring jarMap; consumer migration checklist for follow-on PRs

- [ ] **Step 1: Update README jpackage / publish guidance**

In `README.md`:

1. Where jpackage is described as requiring `jarMap`, change to: jpackage uses the project `jar` by default; `jarMap` is legacy (compat only) for multi-artifact publish.
2. Add a short **Library vs app** note near the plugin recipes table:

```markdown
### Library vs app repos

Apply `bb4.publish` only when another project may depend on your Maven coordinates.
App / installer-only repos (`bb4-games`, `bb4-puzzles`, `bb4-simulations`, `bb4-adventure`,
`bb4-aikido-app`, `bb4-Q-learning`, `bb4-experimentation`, and similar leaf apps) should use
`bb4.scala-library` + `bb4.application` (or custom `run*` tasks) **without** `bb4.publish`.
```

- [ ] **Step 2: Update migration doc**

In `docs/migration-from-scripts.md`:

1. Soften the `jarMap` section: mark as deprecated / compat-only; point to single-jar + no-publish for app repos.
2. Add a **Migrating app repos off publish + jarMap** checklist:

```markdown
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
```

- [ ] **Step 3: CHANGELOG entry**

Under `[Unreleased]`:

```markdown
- **jpackage without jarMap ([#42](https://github.com/bb4/bb4-gradle/issues/42)):**
  When `jarMap` is empty, native installers use the project `jar` as `--main-jar`
  (`bb4.publish` not required). Library-vs-app guidance: leaf app repos should not apply
  `bb4.publish`. `jarMap` remains temporarily for multi-artifact library publishing compat.
```

- [ ] **Step 4: Commit**

```bash
git add README.md docs/migration-from-scripts.md CHANGELOG.md
git commit -m "$(cat <<'EOF'
Document library-vs-app publishing and jpackage without jarMap.

EOF
)"
```

---

### Task 3: Remove jarMap from the plugin

**Do this only after** Task 1 is available to app repos (released or `includeBuild`) **and** prefer after those repos have dropped jarMap. If removing while local consumers still use jarMap, coordinate the consumer PRs in the same change window.

**Files:**
- Modify: `src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4PublishPlugin.kt` — delete multi-jar / `registerJarMapEntry` / jarMap `afterEvaluate` branch; always `createMavenJavaPublication()`
- Modify: `src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4Extension.kt` — remove `JarMapEntry`, `Bb4JarMapContainer`, `jarMap` property
- Modify: `src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4ApplicationPlugin.kt` — remove all jarMap branches; always use project jar for jpackage
- Delete: `src/main/kotlin/com/barrybecker4/gradle/plugins/Bb4JarMap.kt`
- Delete: `src/functionalTest/groovy/com/barrybecker4/gradle/plugins/JarMapTimingTest.groovy`
- Modify: `src/test/groovy/com/barrybecker4/gradle/plugins/Bb4PluginsFunctionalTest.groovy` — remove jarMap cases
- Modify: `src/test/groovy/com/barrybecker4/gradle/plugins/Bb4JpackageFunctionalTest.groovy` — remove jarMap from `enabledBuild` / disabled test; keep project-jar path
- Modify: `src/test/groovy/com/barrybecker4/gradle/plugins/Bb4AppsFunctionalTest.groovy` — drop jarMap if present
- Modify: `src/functionalTest/groovy/com/barrybecker4/gradle/plugins/PublishPomAndPortalTest.groovy` — drop jarMap if present
- Modify: `README.md`, `docs/migration-from-scripts.md`, `CHANGELOG.md` — jarMap removed, not merely deprecated

**Interfaces:**
- Consumes: Task 1 project-jar jpackage path
- Produces: No `bb4.jarMap` / `ext.jarMap` API; `bb4.publish` only single `mavenJava` publication; configuring `jarMap` fails as unknown property / unused ext

- [ ] **Step 1: Strip jarMap from jpackage tests first (fail on leftover API)**

Change `enabledBuild` / disabled test in `Bb4JpackageFunctionalTest` to match the no-jarMap build from Task 1 (no `publish`, no `jarMap.put`). Delete or rewrite any test whose only purpose is jarMap publish timing (`JarMapTimingTest` — delete the file). Remove jarMap assertions from other functional tests.

Run: `./gradlew check`  
Expected: FAIL on compile or tests still calling `Bb4JarMap` / `jarMap.put` until production code is updated — or PASS on tests already migrated and FAIL only where production still references deleted helpers. Prefer deleting production jarMap in the next step in the same sitting so `check` ends green.

- [ ] **Step 2: Simplify `registerJpackageTasks` to project jar only**

Remove `Bb4JarMap.resolve`, `syncJarMapIntoInstallLib`, and `mainJarKey` lookup. Always:

```kotlin
project.tasks.named("installDist").configure { it.dependsOn("jar") }
// in doFirst:
val installLib = project.layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
val mainJarName = project.tasks.named("jar", org.gradle.api.tasks.bundling.Jar::class.java)
    .get().archiveFile.get().asFile.name
val mainJarFile = java.io.File(installLib, mainJarName)
if (!mainJarFile.isFile) {
    throw GradleException(
        "jpackage $appId: missing $mainJarName in $installLib. Run installDist first.",
    )
}
```

In `registerAppTasks`, remove jarMap `dependsOn` blocks entirely.

Deprecate-or-remove `mainJarKey` from `Bb4JpackageExtension`: remove the property if nothing reads it; update README accordingly.

- [ ] **Step 3: Simplify `Bb4PublishPlugin` to single-artifact only**

Delete `enterMultiJarMode`, `registerJarMapEntry`, jarMap container `all { }`, and the `afterEvaluate` branch that registers jarMap from `Bb4JarMap.resolve`. Keep deferred `createMavenJavaPublication()` + signing + central bundle. Ensure `jar` / `sourcesJar` / `javadocJar` / `scaladocJar` stay enabled.

- [ ] **Step 4: Remove extension + `Bb4JarMap.kt`**

Delete `JarMapEntry`, `Bb4JarMapContainer`, and `val jarMap` from `Bb4Extension.kt`. Delete `Bb4JarMap.kt`. Fix any remaining imports.

- [ ] **Step 5: Run full verification**

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL.

Optional: `cd config-cache-test && ./gradlew help --configuration-cache` (or project’s usual config-cache smoke) — BUILD SUCCESSFUL / reuse as applicable.

- [ ] **Step 6: Docs + CHANGELOG for removal**

- README / migration: remove jarMap configuration examples; state jarMap was removed in this version; app repos must not configure it.
- CHANGELOG:

```markdown
- **Removed jarMap ([#42](https://github.com/bb4/bb4-gradle/issues/42)):**
  Multi-artifact `jarMap` / `ext.jarMap` publishing is gone. App repos should not publish;
  jpackage always uses the project jar. Library repos keep single-artifact `bb4.publish`.
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
Remove jarMap multi-artifact publishing from bb4-gradle.

EOF
)"
```

---

### Task 4: Close the loop on #42 (tracking only)

**Files:**
- None required in-repo beyond ensuring CHANGELOG links #42 (done in Tasks 2–3)

**Interfaces:**
- Produces: Issue comment / close criteria clear for humans

- [ ] **Step 1: Comment on GitHub issue #42**

Summarize the decision (app repos = no publish; jarMap deleted; abandoned coords frozen) and link:

- Spec: `docs/superpowers/specs/2026-11-12-jarMap-app-repos-design.md`
- Plan: `docs/superpowers/plans/2026-11-12-retire-jarMap-app-repos.md`
- Migration checklist in `docs/migration-from-scripts.md`

List follow-on PRs still needed: games, puzzles, simulations, adventure, aikido-app, Q-learning, experimentation; optional website diagram.

- [ ] **Step 2: Close #42 only after Task 3 is on the branch you consider done**

If Task 3 is merged/committed: close with “Fixed in &lt;sha/version&gt;”. If only Task 1–2 shipped: leave open until jarMap removal lands, or convert to a tracking issue for consumer migrations — prefer closing #42 when jarMap is removed from the plugin, and open/keep a separate checklist issue for app-repo PRs if useful.

- [ ] **Step 3: Commit** — N/A if only GitHub UI; otherwise no repo change.

---

## Follow-on work (not this plan)

Separate PRs in each app repo following the migration checklist. Optional `bb4-website` diagram update. No catalog changes.

## Self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| jpackage without jarMap / project jar | Task 1 |
| `mainJarKey` optional when no jarMap | Task 1 (+ removal Task 3) |
| Library-vs-app rule documented | Task 2 |
| App repo list + migration steps | Task 2 checklist |
| Remove jarMap from publish/extension/tests | Task 3 |
| Single-artifact publish for libraries | Task 3 |
| Abandoned Central coords frozen | Docs / Task 4 (explicit non-action) |
| Catalog unchanged | Global constraints / no task needed |
| Consumer migrations | Follow-on (documented, not implemented here) |
| Close #42 | Task 4 |
