# Publishing bb4-gradle (Maven Central / Central Publisher Portal)

**OSSRH was shut down** (June 30, 2025). All publishing goes through the **Central Publisher Portal** and the documented APIs. See [OSSRH Sunset](https://central.sonatype.org/pages/ossrh-eol/) and [Portal OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/).

## Credentials: Central Portal user token

1. Sign in at [central.sonatype.com](https://central.sonatype.com/).
2. Create a **User Token** (not an old OSSRH-only token).
3. In `~/.gradle/gradle.properties` (never commit):

   ```properties
   ossrhToken=<token username from Portal>
   ossrhTokenPassword=<token password from Portal>
   ```

   Or use `OSSRH_USERNAME` / `OSSRH_PASSWORD` environment variables.

Publishing with an **old OSSRH token** typically yields **401**; wrong or legacy repository URLs often yield **405** or other errors.

### HTTP **403 Forbidden** on `central.sonatype.com/repository/maven-snapshots/`

The deploy URL is correct, but the server is refusing the upload. Most often:

1. **SNAPSHOT publishing is not enabled for your namespace** (required for Central Portal snapshots). In [central.sonatype.com → Publishing → Namespaces](https://central.sonatype.com/publishing/namespaces), open your namespace’s menu and choose **“Enable SNAPSHOTs”**, then confirm. Until this is done, deploys to `maven-snapshots` typically return **403**. See [Publish Portal Snapshots](https://central.sonatype.org/publish/publish-portal-snapshots/).
2. **User token** — username and password must be the **Central Portal user token** pair (Profile → User Token), copied exactly into `ossrhToken` / `ossrhTokenPassword`.
3. If you still see **403** after enabling SNAPSHOTs and verifying the token, see Sonatype’s [403 FAQ](https://central.sonatype.org/faq/403-error) and contact [Central Support](mailto:central-support@sonatype.com) if needed.

## Default repository URLs (this project)

| Purpose | Default URL |
|---------|----------------|
| **SNAPSHOT** deploy | `https://central.sonatype.com/repository/maven-snapshots/` |
| **Release** staging (non-SNAPSHOT) | `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/` |

Override in `gradle.properties` if Sonatype documents a different endpoint for your account:

```properties
bb4.ossrh.snapshotUrl=https://central.sonatype.com/repository/maven-snapshots/
bb4.ossrh.releaseStagingUrl=https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/
```

You can also use `bb4.central.snapshotUrl` / `bb4.central.releaseStagingUrl` as aliases.

If you still have **old** values pointing at `oss.sonatype.org` or `s01.oss.sonatype.org` (often in `~/.gradle/gradle.properties`), bb4-gradle **ignores** them and uses the Central defaults, with a Gradle warning — those hosts return **405** after OSSRH EOL.

## Publish from this repo

```bash
./gradlew publish
```

Same as `./gradlew publishArtifacts`.

- **SNAPSHOT** builds: signing is usually **skipped** (`isReleaseVersion = false`).
- **Release** builds: signing is **required**; configure GPG per Gradle signing docs.

## Gradle `maven-publish` and the Portal (releases)

When using the **OSSRH Staging API** URL above, Gradle only performs Maven-style `PUT` uploads. For deployments to show in the [Central Publisher UI](https://central.sonatype.com/publishing), Sonatype may require an extra step after upload (same IP as the build), e.g.:

`POST .../manual/upload/defaultRepository/<namespace>`

where `<namespace>` is your group (e.g. `com.barrybecker4`). See **“Ensuring Deployment Visibility In The Central Publisher Portal”** in the [OSSRH Staging API guide](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/).

Snapshot flows may differ; check the current **Snapshots** section under [Publishing](https://central.sonatype.org/publish/).

## Verify artifacts

- [central.sonatype.com/publishing](https://central.sonatype.com/publishing) after a successful deploy (and any required manual step).
- Snapshot consumption: resolve from **`https://central.sonatype.com/repository/maven-snapshots/`** in `pluginManagement` until a release is on Maven Central.

## Legacy hosts (not recommended)

Old `oss.sonatype.org` / `s01.oss.sonatype.org` deploy URLs are **obsolete** for post-OSSRH migration. Use overrides only if Central Support instructs you to.

## HTTP 403 / 405 / 401 troubleshooting

1. **403 to maven-snapshots:** enable **Enable SNAPSHOTs** on your namespace (see above).
2. Use a **Portal user token**, not a pre-2025 OSSRH-only token.
3. Use the **default URLs** in this doc (or overrides Central documents).
4. Confirm namespace **`com.barrybecker4`** under your Portal account.
5. For release uploads, complete any **manual Portal / Staging API** step Sonatype requires after `gradle publish`.
