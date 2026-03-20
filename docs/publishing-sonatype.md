# Publishing bb4-gradle to Sonatype (OSSRH)

## Snapshot (`2.0-SNAPSHOT`)

1. **JDK 21** for running Gradle.
2. **Credentials** (never commit these):
   - `~/.gradle/gradle.properties`:
     - `ossrhToken` — Sonatype user token **username** (or legacy username).
     - `ossrhTokenPassword` — Sonatype user token **password** (or legacy password).
   - Or environment variables: `OSSRH_USERNAME`, `OSSRH_PASSWORD`.

3. From the repo root:

   ```bash
   ./gradlew publish
   ```

   Same as `./gradlew publishArtifacts` (alias task).

4. **Artifacts**: main plugin JAR `com.barrybecker4:bb4-gradle:2.0-SNAPSHOT` plus plugin **marker** POMs (`*.gradle.plugin`) for each plugin id. Signing is **skipped** for `-SNAPSHOT` (expected).

5. **Verify** in Nexus: [s01 OSSRH](https://s01.oss.sonatype.org/) (or legacy [oss.sonatype.org](https://oss.sonatype.org/) if your namespace still uses it) → **Browse** → **snapshots** → `com.barrybecker4`.

## Repository URLs (s01 vs legacy)

Many namespaces use **`s01.oss.sonatype.org`** after the Central migration. This project defaults to:

- Snapshots: `https://s01.oss.sonatype.org/content/repositories/snapshots/`
- Release staging: `https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/`

If deploy fails, try **legacy** hosts (some older groups still publish there):

Override in **`~/.gradle/gradle.properties`** (or project `gradle.properties`, not committed with secrets):

```properties
bb4.ossrh.snapshotUrl=https://oss.sonatype.org/content/repositories/snapshots/
bb4.ossrh.releaseStagingUrl=https://oss.sonatype.org/service/local/staging/deploy/maven2/
```

`build.gradle.kts` and `Bb4PublishPlugin` read these properties when set.

## HTTP 405 Not Allowed on PUT

Common causes:

1. **Wrong host** for your namespace — try the override URLs above or switch between s01 and legacy.
2. **Invalid or missing token** — empty username/password sometimes surfaces as 405 instead of 401.
3. **Wrong token type** — use a **User Token** from Sonatype / Central Portal for your OSSRH user.
4. **Group not authorized** — confirm `com.barrybecker4` deploy rights in Central Portal.

## Release (non-SNAPSHOT, e.g. `2.0.0`)

1. Set `version` in `build.gradle.kts` to a release without `-SNAPSHOT`.
2. **Signing** is required (`isReleaseVersion = true`). Configure signing key in `~/.gradle/gradle.properties` per Gradle signing plugin docs.
3. `./gradlew publish`, then **Close** and **Release** the staging repository in the Nexus UI (or Central Portal workflow).
