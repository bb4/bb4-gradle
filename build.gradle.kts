import java.net.URI
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
// Copyright by Barry G. Becker, 2021–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

/** Legacy OSSRH hosts return 405 after EOL; ~/.gradle/gradle.properties often still overrides with these. */
private fun Project.resolveBb4SnapshotRepoUrl(): String {
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

private fun Project.resolveBb4ReleaseStagingRepoUrl(): String {
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

plugins {
    groovy
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = "com.barrybecker4"
version = "2.0.0"

description = "Common Gradle convention plugins for bb4 Scala/Java projects"

java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

gradlePlugin {
    website = URI("https://github.com/barrybecker4/bb4-gradle").toString()
    vcsUrl = "https://github.com/barrybecker4/bb4-gradle.git"
    plugins {
        create("bb4Base") {
            id = "com.barrybecker4.bb4.base"
            displayName = "bb4 base conventions"
            description = "Java 21 toolchain, repositories, and resolution defaults for bb4 projects"
            tags = listOf("bb4", "scala", "java", "convention")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4BasePlugin"
        }
        create("bb4ScalaLibrary") {
            id = "com.barrybecker4.bb4.scala-library"
            displayName = "bb4 Scala library conventions"
            description = "Scala 3 + java-library layout, dependencies, and jar conventions"
            tags = listOf("bb4", "scala", "convention")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4ScalaLibraryPlugin"
        }
        create("bb4Publish") {
            id = "com.barrybecker4.bb4.publish"
            displayName = "bb4 Maven publishing"
            description = "Maven Central / Central Portal publishing with sources/javadoc/scaladoc jars"
            tags = listOf("bb4", "publish", "maven")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4PublishPlugin"
        }
        create("bb4Application") {
            id = "com.barrybecker4.bb4.application"
            displayName = "bb4 application conventions"
            description = "Application plugin, distributions, and website deploy helpers"
            tags = listOf("bb4", "application", "distribution")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4ApplicationPlugin"
        }
    }
}

repositories {
    mavenCentral()
    // Resolve bb4 and other SNAPSHOTs published via Central Portal (OSSRH EOL June 2025).
    maven { url = URI("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
    implementation(localGroovy())
}

val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")

publishing {
    repositories {
        maven {
            // Central Publisher Portal (OSSRH sunset). See docs/publishing-sonatype.md
            // Override via bb4.ossrh.snapshotUrl / bb4.ossrh.releaseStagingUrl (or bb4.central.*).
            val snapshotRepoUrl = resolveBb4SnapshotRepoUrl()
            val releasesRepoUrl = resolveBb4ReleaseStagingRepoUrl()
            url = URI(if (version.toString().endsWith("-SNAPSHOT")) snapshotRepoUrl else releasesRepoUrl)
            credentials {
                username = providers.gradleProperty("ossrhToken").orNull
                    ?: System.getenv("OSSRH_USERNAME")
                    ?: ""
                password = providers.gradleProperty("ossrhTokenPassword").orNull
                    ?: System.getenv("OSSRH_PASSWORD")
                    ?: ""
            }
        }
    }
}

// Publications (including plugin markers from java-gradle-plugin) are registered after
// our script runs; configure Central-required POM metadata on every Maven publication.
// Only register signing for non-SNAPSHOT: if `signing.sign(...)` runs while `isRequired` is false
// and no GPG key is configured, Gradle can fail evaluating the Sign task's onlyIf predicate.
afterEvaluate {
    publishing.publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            groupId = "com.barrybecker4"
            artifactId = "bb4-gradle"
        }
        // Plugin marker POMs also need these or Central rejects the deployment.
        pom {
            name.set(project.name)
            description.set(project.description ?: project.name)
            url.set("https://github.com/barrybecker4/${project.name}")
            scm {
                url.set("scm:git@github.com:barrybecker4/${project.name}.git")
                connection.set("scm:git@github.com:barrybecker4/${project.name}.git")
                developerConnection.set("scm:git@github.com:barrybecker4/${project.name}.git")
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
        signing.sign(publishing.publications)
    }
}

signing {
    isRequired = isReleaseVersion
}

tasks.register<Task>("publishArtifacts") {
    description =
        "Publish artifacts to Central snapshot or staging repository. " +
            "For releases, also POST …/manual/upload/defaultRepository/com.barrybecker4 " +
            "(see docs/publishing-sonatype.md) or the Portal stays empty."
    group = "publishing"
    dependsOn(tasks.named("publish"))
}
