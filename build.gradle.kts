import java.net.URI
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
// Copyright by Barry G. Becker, 2021–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

plugins {
    groovy
    `java-gradle-plugin`
    `maven-publish`
    signing
}

group = "com.barrybecker4"
version = "2.0-SNAPSHOT"

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
            description = "Sonatype OSSRH publishing with sources/javadoc/scaladoc jars"
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
    maven { url = URI("https://oss.sonatype.org/content/groups/staging") }
    maven { url = URI("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    implementation(localGroovy())
}

val isReleaseVersion = !version.toString().endsWith("SNAPSHOT")

publishing {
    repositories {
        maven {
            // Defaults: s01 (Central migration). Override via bb4.ossrh.snapshotUrl / bb4.ossrh.releaseStagingUrl in gradle.properties.
            val snapshotRepoUrl = (findProperty("bb4.ossrh.snapshotUrl") as String?)
                ?: "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            val releasesRepoUrl = (findProperty("bb4.ossrh.releaseStagingUrl") as String?)
                ?: "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            url = URI(if (version.toString().endsWith("SNAPSHOT")) snapshotRepoUrl else releasesRepoUrl)
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

// `pluginMaven` is registered by java-gradle-plugin after ours; configure and sign there.
afterEvaluate {
    publishing.publications.named<MavenPublication>("pluginMaven") {
        groupId = "com.barrybecker4"
        artifactId = "bb4-gradle"
        pom {
            name = project.name
            packaging = "jar"
            description = project.description ?: project.name
            url = "https://github.com/barrybecker4/${project.name}"
            scm {
                url = "scm:git@github.com:barrybecker4/${project.name}.git"
                connection = "scm:git@github.com:barrybecker4/${project.name}.git"
                developerConnection = "scm:git@github.com:barrybecker4/${project.name}.git"
            }
            licenses {
                license {
                    name = "The MIT license"
                    url = "http://www.opensource.org/licenses/MIT"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = "barrybecker4"
                    name = "Barry G. Becker"
                    email = "barrybecker4@gmail.com"
                }
            }
        }
    }
    signing.sign(publishing.publications.getByName("pluginMaven"))
}

signing {
    isRequired = isReleaseVersion
}

tasks.register<Task>("publishArtifacts") {
    description = "Publish artifacts to Sonatype repository"
    group = "publishing"
    dependsOn(tasks.named("publish"))
}
