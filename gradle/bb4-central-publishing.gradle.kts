// Copyright by Barry G. Becker, 2021–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
import java.net.URI
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

/** Legacy OSSRH hosts return 405 after EOL; ~/.gradle/gradle.properties often still overrides with these. */
fun Project.resolveBb4SnapshotRepoUrl(): String {
    val defaultUrl = "https://central.sonatype.com/repository/maven-snapshots/"
    val explicit = (findProperty("bb4.ossrh.snapshotUrl") as String?)
        ?: (findProperty("bb4.central.snapshotUrl") as String?)
    val trimmed = explicit?.trim().orEmpty()
    if (trimmed.isEmpty()) return defaultUrl
    if (trimmed.contains("oss.sonatype.org")) {
        logger.warn(
            "bb4-gradle: ignoring legacy bb4.ossrh.snapshotUrl (OSSRH EOL): {} — using {}",
            trimmed,
            defaultUrl,
        )
        return defaultUrl
    }
    return trimmed
}

fun Project.resolveBb4ReleaseStagingRepoUrl(): String {
    val defaultUrl = "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
    val explicit = (findProperty("bb4.ossrh.releaseStagingUrl") as String?)
        ?: (findProperty("bb4.central.releaseStagingUrl") as String?)
    val trimmed = explicit?.trim().orEmpty()
    if (trimmed.isEmpty()) return defaultUrl
    if (trimmed.contains("oss.sonatype.org")) {
        logger.warn(
            "bb4-gradle: ignoring legacy bb4.ossrh.releaseStagingUrl (OSSRH EOL): {} — using {}",
            trimmed,
            defaultUrl,
        )
        return defaultUrl
    }
    return trimmed
}

val projectRef = project
val isReleaseVersion = !projectRef.version.toString().endsWith("-SNAPSHOT")
val publishingExt = projectRef.extensions.getByType(PublishingExtension::class.java)
val signingExt = projectRef.extensions.getByType(SigningExtension::class.java)

publishingExt.repositories.maven {
    // Central Publisher Portal (OSSRH sunset). See docs/publishing-sonatype.md
    // Override via bb4.ossrh.snapshotUrl / bb4.ossrh.releaseStagingUrl (or bb4.central.*).
    val snapshotRepoUrl = projectRef.resolveBb4SnapshotRepoUrl()
    val releasesRepoUrl = projectRef.resolveBb4ReleaseStagingRepoUrl()
    url = URI(if (projectRef.version.toString().endsWith("-SNAPSHOT")) snapshotRepoUrl else releasesRepoUrl)
    credentials {
        username = projectRef.providers.gradleProperty("ossrhToken").orNull
            ?: System.getenv("OSSRH_USERNAME")
            ?: ""
        password = projectRef.providers.gradleProperty("ossrhTokenPassword").orNull
            ?: System.getenv("OSSRH_PASSWORD")
            ?: ""
    }
}

// Publications (including plugin markers from java-gradle-plugin) are registered after
// our script runs; configure Central-required POM metadata on every Maven publication.
// Only register signing for non-SNAPSHOT: if `signing.sign(...)` runs while `isRequired` is false
// and no GPG key is configured, Gradle can fail evaluating the Sign task's onlyIf predicate.
projectRef.afterEvaluate {
    publishingExt.publications.withType(MavenPublication::class.java).configureEach {
        if (name == "pluginMaven") {
            groupId = "com.barrybecker4"
            artifactId = "bb4-gradle"
        }
        // Plugin marker POMs also need these or Central rejects the deployment.
        pom {
            name.set(projectRef.name)
            description.set(projectRef.description ?: projectRef.name)
            url.set("https://github.com/barrybecker4/${projectRef.name}")
            scm {
                url.set("scm:git@github.com:barrybecker4/${projectRef.name}.git")
                connection.set("scm:git@github.com:barrybecker4/${projectRef.name}.git")
                developerConnection.set("scm:git@github.com:barrybecker4/${projectRef.name}.git")
            }
            licenses {
                license {
                    name.set("The MIT license")
                    url.set("http://www.opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("barrybecker4")
                    name.set("Barry G. Becker")
                    email.set("barrybecker4@gmail.com")
                }
            }
        }
    }
    if (isReleaseVersion) {
        signingExt.sign(publishingExt.publications)
    }
}

signingExt.isRequired = isReleaseVersion
