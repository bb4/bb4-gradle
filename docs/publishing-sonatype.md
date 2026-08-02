# Publishing bb4-gradle (Maven Central / Central Publisher Portal)

**OSSRH was shut down** (June 30, 2025). All publishing goes through the **Central Publisher Portal**
and the [Portal OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/).
See also [OSSRH Sunset](https://central.sonatype.org/pages/ossrh-eol/).

## Release checklist (this repo)

Do these steps **in order**. Skipping the promote step is the usual reason the Portal shows
**“No Components Found”** after a successful `./gradlew publish`.

1. **Bump version** in `build.gradle.kts` to a release (no `-SNAPSHOT`), e.g. `2.0.0`.
2. **Update docs** (README examples, CHANGELOG date, migration snippets) to that version.
3. **Build locally:** `./gradlew clean build`
4. **Confirm credentials & GPG** in `~/.gradle/gradle.properties` (see [Credentials](#credentials-central-portal-user-token)).
5. **Upload:** `./gradlew publish` (same as `publishArtifacts`).
   - This only `PUT`s artifacts into the Staging API. It does **not** create a Portal deployment by itself.
6. **Promote staging → Portal** (same machine / same public IP as step 5):

   ```bash
   # Read token from ~/.gradle/gradle.properties (never commit these values)
   TOKEN_USER=$(grep -E '^ossrhToken=' "$HOME/.gradle/gradle.properties" | cut -d= -f2-)
   TOKEN_PASS=$(grep -E '^ossrhTokenPassword=' "$HOME/.gradle/gradle.properties" | cut -d= -f2-)
   AUTH=$(printf '%s:%s' "$TOKEN_USER" "$TOKEN_PASS" | base64 | tr -d '\n')

   curl -sS -w "\nHTTP:%{http_code}\n" -X POST \
     "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.barrybecker4?publishing_type=user_managed" \
     -H "Authorization: Bearer ${AUTH}" \
     -H "accept: */*" \
     -d ''
   ```

   Expect **HTTP 200**. Use `publishing_type=automatic` only if you want the Portal to release
   without a UI click (still requires a valid deployment).

7. **Portal UI:** open [central.sonatype.com/publishing](https://central.sonatype.com/publishing).
   - Find **com.barrybecker4 (via OSSRH Staging API)** (or similar).
   - Status should become **VALIDATED**. If **FAILED**, see [Validation failures](#validation-failures-failed-deployment).
   - Click **Publish** (unless you used `automatic`).
8. **Wait ~10–30 minutes**, then confirm on
   [search.maven.org](https://search.maven.org/artifact/com.barrybecker4/bb4-gradle) /
   Central for `com.barrybecker4:bb4-gradle:<version>` and the plugin markers
   (`com.barrybecker4.bb4.*.gradle.plugin`).
9. **Commit & tag** the release version, then bump `version` to the next SNAPSHOT
   (e.g. `2.1-SNAPSHOT`) for continued development.

### Downstream bb4 libraries / apps

Anything that uses Gradle `maven-publish` against the Staging API URL (including projects that apply
`com.barrybecker4.bb4.publish`) needs the **same promote curl** after `./gradlew publish`, then
Portal **Publish**, before the release is on Maven Central.

---

## Credentials: Central Portal user token

1. Sign in at [central.sonatype.com](https://central.sonatype.com/).
2. Create a **User Token** (Profile → User Token — not an old OSSRH-only token).
3. In `~/.gradle/gradle.properties` (never commit):

   ```properties
   ossrhToken=<token username from Portal>
   ossrhTokenPassword=<token password from Portal>
   signing.keyId=…
   signing.password=…
   signing.secretKeyRingFile=…
   ```

   Or use `OSSRH_USERNAME` / `OSSRH_PASSWORD` environment variables for the token pair.

Publishing with an **old OSSRH token** typically yields **401**; wrong or legacy repository URLs often yield **405**.

### HTTP **403 Forbidden** on `central.sonatype.com/repository/maven-snapshots/`

1. **Enable SNAPSHOTs** for namespace `com.barrybecker4` under
   [Publishing → Namespaces](https://central.sonatype.com/publishing/namespaces).
   See [Publish Portal Snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/).
2. Confirm the **Portal user token** is what is in `ossrhToken` / `ossrhTokenPassword`.
3. If still stuck: [403 FAQ](https://central.sonatype.org/faq/403-error) / [Central Support](mailto:central-support@sonatype.com).

---

## Default repository URLs

| Purpose | Default URL |
|---------|----------------|
| **SNAPSHOT** deploy | `https://central.sonatype.com/repository/maven-snapshots/` |
| **Release** staging (non-SNAPSHOT) | `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/` |

Override in `gradle.properties` if Central Support documents a different endpoint:

```properties
bb4.ossrh.snapshotUrl=https://central.sonatype.com/repository/maven-snapshots/
bb4.ossrh.releaseStagingUrl=https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/
```

Aliases: `bb4.central.snapshotUrl` / `bb4.central.releaseStagingUrl`.

Legacy `oss.sonatype.org` / `s01.oss.sonatype.org` overrides in `~/.gradle/gradle.properties` are
**ignored** (with a Gradle warning); those hosts return **405** after OSSRH EOL.

### Why URL choice is deferred (`afterEvaluate`)

Gradle applies `plugins { }` **before** the rest of the build script, so a `version = …` line below
`plugins { }` is not visible during plugin application. Both this repo and `bb4.publish` set the
deploy URL (and release-only signing) in **`afterEvaluate`** so `-SNAPSHOT` vs release picks the
correct repository.

- **SNAPSHOT** builds: signing usually skipped.
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

In this repo, `build.gradle.kts` applies that metadata to **all** `MavenPublication`s via
`publishing.publications.withType<MavenPublication>()`. Do not configure POM only on
`pluginMaven`; marker-only omissions cause Portal status **FAILED** with errors like
“Project URL is not defined / License information is missing / …”.

Downstream projects using `bb4.publish` get POM metadata from `Bb4PublishPlugin.addPom`.

---

## Why Portal can show “No Components Found” after `publish`

`./gradlew publish` only uploads files with Maven-style `PUT`s. The Staging API does **not** know
when a deployment is “done” until you call:

`POST /manual/upload/defaultRepository/com.barrybecker4`

That call must use the **same public IP** as the upload (run it on the machine that just published).
Only then does a deployment appear under [central.sonatype.com/publishing](https://central.sonatype.com/publishing).

Official detail: [Ensuring Deployment Visibility](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#ensuring-deployment-visibility-in-the-central-publisher-portal).

`publishing_type` options:

| Value | Behavior |
|-------|----------|
| `user_managed` (default) | Portal UI: review → **Publish** or **Drop** |
| `automatic` | Portal validates and releases if validation passes |
| `portal_api` | Upload for status polling via Portal API |

---

## Validation failures (FAILED deployment)

1. Read errors in the Portal UI, or:

   ```bash
   curl -sS "https://central.sonatype.com/api/v1/publisher/deployments" \
     -H "Authorization: Bearer ${AUTH}" -H "accept: application/json"
   ```

2. **Drop** the failed Portal deployment:

   ```bash
   curl -sS -X DELETE \
     "https://central.sonatype.com/api/v1/publisher/deployment/<deploymentId>" \
     -H "Authorization: Bearer ${AUTH}"
   ```

3. List / drop leftover Staging API repositories (keys from search):

   ```bash
   curl -sS "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=com.barrybecker4" \
     -H "Authorization: Bearer ${AUTH}"

   # URL-encode the repository key, then:
   curl -sS -X DELETE \
     "https://ossrh-staging-api.central.sonatype.com/manual/drop/repository/<urlencoded-key>" \
     -H "Authorization: Bearer ${AUTH}"
   ```

4. Fix POMs / artifacts, then **`./gradlew publish`** and the **promote curl** again.
   You can reuse the same version if it never successfully released to Maven Central.

Dropping on the Portal and dropping on the Staging API are **separate** services — clear both when
retrying a failed release.

---

## Verify artifacts

- Portal: [central.sonatype.com/publishing](https://central.sonatype.com/publishing) — state **VALIDATED**, then **Publish**.
- Release consumption: Maven Central / `mavenCentral()` / Plugin Portal (e.g. `2.0.0`).
- Snapshot consumption: add
  `https://central.sonatype.com/repository/maven-snapshots/` in `pluginManagement` while using `-SNAPSHOT`.

---

## HTTP 403 / 405 / 401 troubleshooting

1. **403 to maven-snapshots:** enable **Enable SNAPSHOTs** on the namespace (see above).
2. Use a **Portal user token**, not a pre-2025 OSSRH-only token.
3. Use the **default URLs** in this doc (or overrides Central Support gives you).
4. Confirm namespace **`com.barrybecker4`** under your Portal account.
5. Empty Deployments after a green Gradle publish → you skipped the **promote curl**.
6. **FAILED** validation → fix POMs (including plugin markers), drop, republish, promote again.
