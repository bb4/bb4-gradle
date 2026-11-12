# Kotlin rewrite of bb4 convention plugins (#44)

> Implemented. See [issue #44](https://github.com/bb4/bb4-gradle/issues/44) and `CHANGELOG.md` `[Unreleased]`.

**Goal:** Kotlin convention plugins with typed lazy `bb4 {}` owned by `bb4.base`, without breaking Groovy consumers.

**Done:**

- Plugin sources live under `src/main/kotlin/com/barrybecker4/gradle/plugins/`
- `bb4 {}` created by `bb4.base` (`archivesBaseName`, `mainClass`, `repository`, typed `jarMap`, `appMap`)
- Groovy `jarMap.put('key', [include: …])` and legacy `ext.jarMap` / `ext.appMap` still work
- Docs updated in `README.md`, `CHANGELOG.md`, `docs/migration-from-scripts.md`
- Verified with `./gradlew check` and `config-cache-test` (`--configuration-cache` reuse)
