# Design: Retire jarMap; app repos stop publishing (#42)

**Date:** 2026-11-12  
**Issue:** [bb4-gradle#42](https://github.com/bb4/bb4-gradle/issues/42)  
**Status:** Approved for implementation planning

## Problem

`jarMap` slices one compilation into many Maven artifacts (`bb4-chess`, `bb4-hiq`, …). That model exists for legacy Central coordinates. For multi-app repos it also feeds jpackage/`installDist`, but every installer already receives every sliced jar, so the slices do not produce lean per-app packages.

Nothing outside those repos depends on the sliced coordinates. Publishing them is pure cost (many Central uploads, throttling workarounds). Issue #42 listed three options; the right choice for these leaf apps is **stop multi-artifact publishing**, not typed jarMap dependencies or real subprojects.

## Decision

**Library vs app rule:** A bb4 repo publishes to Maven Central only if another repo (or an external consumer) may depend on its coordinates. Otherwise it is an **app repo**: build, run, optional installers / website deploy — no `bb4.publish`, no Central uploads.

**#42 outcome:** Delete `jarMap` from `bb4-gradle` once app repos stop using it. Do not invest in typed jarMap `dependOn` (issue option 3) or subproject generators (option 2). Abandoned Central coordinates stay frozen; do not delete or republish them.

## Scope

### App repos (stop publishing)

| Repo | Today | After |
|------|--------|--------|
| `bb4-games` | publish + many jarMap slices + jpackage | no publish; no jarMap; jpackage on project jar |
| `bb4-puzzles` | same | same |
| `bb4-simulations` | same | same |
| `bb4-adventure` | publish + single jarMap entry + jpackage | same pattern as above |
| `bb4-aikido-app` | publish (no jarMap) | drop publish |
| `bb4-Q-learning` | publish (no jarMap; custom `run*`) | drop publish; keep custom runs (optional later move to `application`) |
| `bb4-experimentation` | publish (no jarMap) | drop publish |

Any future leaf app follows the same rule.

### Library repos (keep publishing; no jarMap)

`bb4-common`, `bb4-math`, `bb4-ui`, `bb4-sound`, `bb4-optimization`, `bb4-expression`, `bb4-imageproc`, `bb4-A-star`, `bb4-shortest-paths`, plus `bb4-gradle` / `bb4-platform` / `bb4-catalog`.

These already publish a single artifact per repo. Catalog/`libs.versions.toml` already lists only libraries — no app coords to remove.

## Plugin design (`bb4-gradle`)

### `bb4.application` / jpackage

- When jarMap is empty (or removed): `installDist`, start scripts, and jpackage use the normal project `jar` output plus runtime dependencies in `lib/` as today.
- `mainJarKey` becomes optional; if unset, the project jar is the main jar.
- Per-app entry stays on `apps` (`mainClass`, args, console, icons) — unchanged.
- Fail clearly only if jpackage is enabled and there is no usable main jar / main class.

### `bb4.publish`

- Remove the jarMap multi-publication path (typed container, legacy `ext.jarMap`, mavenJava-vs-jarMap branching, related tests).
- Single-artifact publish remains for library repos.

### Extension / docs

- Drop `bb4.jarMap` / `ext.jarMap` from the extension and migration docs.
- Document the library-vs-app rule: apply `publish` only when something depends on the coords; app repos use `scala-library` + `application` (or custom `run*`) without `publish`.

### Out of scope

- Real Gradle subprojects per app
- Typed jarMap inter-entry `dependOn`
- Deleting or republishing abandoned Central artifacts
- Catalog/BOM changes for app coords (none exist)

## Consumer migration

For each app repo:

1. Remove `alias(bb4Libs.plugins.publish)` / `bb4.publish`.
2. Delete all `jarMap` / `ext.jarMap` and Sonatype publish-throttle `afterEvaluate` blocks.
3. Keep `scala-library` + `application` where `run*` / deploy / jpackage are wanted.
4. For jpackage repos: remove `mainJarKey` once the plugin defaults to the project jar; keep `apps`, icons, `packageIdentifierPrefix`.
5. CI: stop `publishArtifacts` / Central credentials; keep build/test and installer/release workflows.
6. README: describe as app / installers only, not “published to Sonatype.”

Optional separate follow-up: update `bb4-website` dependency diagram so leaf apps are not described as deployed to Sonatype.

## Rollout order

1. **bb4-gradle:** Ship jpackage/`installDist` support that works **without** jarMap (compat window while jarMap still exists).
2. **App repos:** Migrate off `publish` + jarMap (can be parallel once step 1 is released or via `includeBuild`).
3. **bb4-gradle:** Remove jarMap APIs, tests, and docs; close #42.
4. Optional docs/website cleanup PRs.

## Success criteria

- App repos no longer call `publishArtifacts` or configure jarMap.
- Multi-app jpackage still produces installers using the single project jar.
- Library repos still publish one artifact each via `bb4.publish`.
- No jarMap surface remains in the plugin after the removal release.
- Issue #42 closed with this decision recorded.
