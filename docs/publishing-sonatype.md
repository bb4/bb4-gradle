# Publishing bb4-gradle (Maven Central / Central Publisher Portal)

**OSSRH was shut down** (June 30, 2025). Releases use the
[Central Publisher API](https://central.sonatype.org/publish/publish-portal-api/)
(one signed deployment bundle). Snapshots still deploy to the Central
[maven-snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/) repository.
See also [OSSRH Sunset](https://central.sonatype.org/pages/ossrh-eol/).

## Release checklist (this repo)

1. **Bump version** in `build.gradle.kts` to a release (no `-SNAPSHOT`), e.g. `3.0.0`.
2. **Update docs** (README examples, CHANGELOG date, migration snippets) to that version.
3. **Build locally:** `./gradlew clean build`
4. **Confirm credentials & GPG** (see [Credentials](#credentials-central-portal-user-token)).
5. **Publish in one step:** `./gradlew publishArtifacts`
   - Stages signed publications locally, zips a Central deployment bundle, and uploads it to
     `https://central.sonatype.com/api/v1/publisher/upload`.
   - Default `bb4.central.publishingType=AUTOMATIC` validates and releases without a Portal click.
   - Use `-Pbb4.central.publishingType=USER_MANAGED` if you want to review in the Portal UI first.
6. **Wait ~10–30 minutes**, then confirm on
   [search.maven.org](https://search.maven.org/artifact/com.barrybecker4/bb4-gradle) /
   Central for `com.barrybecker4:bb4-gradle:<version>` and the plugin markers
   (`com.barrybecker4.bb4.*.gradle.plugin`).
7. **Commit & tag** the release version, then bump `version` to the next SNAPSHOT
   (e.g. `3.1-SNAPSHOT`) for continued development.

### Downstream bb4 libraries / apps

Projects that apply `com.barrybecker4.bb4.publish` get the same one-task flow:

```bash
./gradlew publishArtifacts
```

No promote curl. Snapshots go to maven-snapshots; releases upload a Publisher API bundle.

---

## Credentials: Central Portal user token

1. Sign in at [central.sonatype.com](https://central.sonatype.com/).
2. Create a **User Token** (Profile → User Token — not an old OSSRH-only token).
3. In `~/.gradle/gradle.properties` (never commit):

   ```properties
   ossrhToken=<token username from Portal>
   ossrhTokenPassword=<token password from Portal>
   ```

   Or use `OSSRH_USERNAME` / `OSSRH_PASSWORD` environment variables for the token pair.

### GPG signing

**Local keyring** (classic):

```properties
signing.keyId=…
signing.password=…
signing.secretKeyRingFile=…
```

**In-memory key for CI** (preferred; no secring path):

```properties
signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----…
signingPassword=…
# optional when using a subkey:
# signingKeyId=…
```

Or set `ORG_GRADLE_PROJECT_signingKey` / `ORG_GRADLE_PROJECT_signingPassword`
(and optional `ORG_GRADLE_PROJECT_signingKeyId`) in the environment.

Publishing with an **old OSSRH token** typically yields **401**.

### HTTP **403 Forbidden** on `central.sonatype.com/repository/maven-snapshots/`

1. **Enable SNAPSHOTs** for namespace `com.barrybecker4` under
   [Publishing → Namespaces](https://central.sonatype.com/publishing/namespaces).
   See [Publish Portal Snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/).
2. Confirm the **Portal user token** is what is in `ossrhToken` / `ossrhTokenPassword`.
3. If still stuck: [403 FAQ](https://central.sonatype.org/faq/403-error) / [Central Support](mailto:central-support@sonatype.com).

---

## Repository URLs and properties

| Purpose | Behavior |
|---------|----------|
| **SNAPSHOT** | Deploy to `https://central.sonatype.com/repository/maven-snapshots/` |
| **Release** | Local stage → zip → `POST /api/v1/publisher/upload` (no Staging API) |

Optional overrides:

```properties
bb4.ossrh.snapshotUrl=https://central.sonatype.com/repository/maven-snapshots/
# alias: bb4.central.snapshotUrl

bb4.central.publishingType=AUTOMATIC
# or USER_MANAGED

bb4.repository=bb4-gradle
# GitHub repo name under barrybecker4 (defaults to project.name). Used for POM url/SCM.
```

Legacy `oss.sonatype.org` / `s01.oss.sonatype.org` snapshot overrides are **ignored** (with a Gradle warning).

### Why URL / signing choice is deferred (`afterEvaluate`)

Gradle applies `plugins { }` **before** the rest of the build script, so a `version = …` line below
`plugins { }` is not visible during plugin application. `bb4.publish` configures the snapshot
repository (or release bundle tasks) and release-only signing in **`afterEvaluate`**.

- **SNAPSHOT** builds: signing skipped.
- **Release** builds: signing **required**.

---

## POM metadata (required for every publication)

Maven Central validates **every** published POM — including Gradle **plugin marker** POMs
(`*.gradle.plugin`), not only `com.barrybecker4:bb4-gradle`.

Each POM must include at least:

- project **URL**
- **license**
- **SCM** URL
- **developers**

`bb4.publish` / this repo’s publishing script set:

- `groupId` ← `project.group`
- `description` ← `project.description` (fallback: project name)
- `url` / SCM ← `https://github.com/barrybecker4/${bb4.repository ?: project.name}`

A CycloneDX SBOM JSON sidecar (`classifier=cyclonedx`) is attached to every Maven publication.

---

## Validation failures (FAILED deployment)

1. Read errors in the Portal UI, or:

   ```bash
   TOKEN_USER=$(grep -E '^ossrhToken=' "$HOME/.gradle/gradle.properties" | cut -d= -f2-)
   TOKEN_PASS=$(grep -E '^ossrhTokenPassword=' "$HOME/.gradle/gradle.properties" | cut -d= -f2-)
   AUTH=$(printf '%s:%s' "$TOKEN_USER" "$TOKEN_PASS" | base64 | tr -d '\n')

   curl -sS "https://central.sonatype.com/api/v1/publisher/deployments" \
     -H "Authorization: Bearer ${AUTH}" -H "accept: application/json"
   ```

2. **Drop** the failed Portal deployment:

   ```bash
   curl -sS -X DELETE \
     "https://central.sonatype.com/api/v1/publisher/deployment/<deploymentId>" \
     -H "Authorization: Bearer ${AUTH}"
   ```

3. Fix POMs / artifacts, then run **`./gradlew publishArtifacts`** again.
   You can reuse the same version if it never successfully released to Maven Central.

---

## Verify artifacts

- Portal: [central.sonatype.com/publishing](https://central.sonatype.com/publishing) — with `AUTOMATIC`, look for **PUBLISHED**.
- Release consumption: Maven Central / `mavenCentral()` / Plugin Portal.
- Snapshot consumption: add
  `https://central.sonatype.com/repository/maven-snapshots/` in `pluginManagement` while using `-SNAPSHOT`.

---

## HTTP 403 / 401 troubleshooting

1. **403 to maven-snapshots:** enable **Enable SNAPSHOTs** on the namespace (see above).
2. Use a **Portal user token**, not a pre-2025 OSSRH-only token.
3. Confirm namespace **`com.barrybecker4`** under your Portal account.
4. **FAILED** validation → fix POMs (including plugin markers), drop, then `publishArtifacts` again.
