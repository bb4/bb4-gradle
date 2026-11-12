// Copyright by Barry G. Becker, 2021–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.UUID
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.gradle.plugins.signing.SigningExtension

/**
 * Central publishing for this repo (root + :platform).
 * Mirrors Bb4PublishPlugin contract: snapshots → maven-snapshots; releases → Publisher API bundle.
 * Cannot apply Bb4PublishPlugin to this project while building it (java-gradle-plugin owns publications).
 */

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

fun Project.bb4RepositoryName(): String {
    val explicit = (findProperty("bb4.repository") as String?)?.trim().orEmpty()
    return if (explicit.isNotEmpty()) explicit else name
}

fun Project.bb4OssrhUsername(): String =
    providers.gradleProperty("ossrhToken").orNull
        ?: System.getenv("OSSRH_USERNAME")
        ?: ""

fun Project.bb4OssrhPassword(): String =
    providers.gradleProperty("ossrhTokenPassword").orNull
        ?: System.getenv("OSSRH_PASSWORD")
        ?: ""

fun Project.bb4PublishingType(): String {
    val explicit = (findProperty("bb4.central.publishingType") as String?)?.trim().orEmpty()
    return if (explicit.isNotEmpty()) explicit.uppercase() else "AUTOMATIC"
}

val projectRef = project
val isReleaseVersion = !projectRef.version.toString().endsWith("-SNAPSHOT")
val publishingExt = projectRef.extensions.getByType(PublishingExtension::class.java)
val signingExt = projectRef.extensions.getByType(SigningExtension::class.java)
val localRepoName = "bb4CentralLocal"
val stagingDir = projectRef.rootProject.layout.buildDirectory.dir("bb4-central-staging")

if (isReleaseVersion) {
    publishingExt.repositories.maven {
        name = localRepoName
        // Resolved later is fine; directory is created when publishing runs.
        url = stagingDir.get().asFile.toURI()
    }
} else {
    publishingExt.repositories.maven {
        name = "bb4Sonatype"
        url = URI(projectRef.resolveBb4SnapshotRepoUrl())
        credentials {
            username = projectRef.bb4OssrhUsername()
            password = projectRef.bb4OssrhPassword()
        }
    }
}

signingExt.isRequired = isReleaseVersion

projectRef.afterEvaluate {
    val repoName = projectRef.bb4RepositoryName()
    val githubUrl = "https://github.com/bb4/$repoName"
    val scmConnection = "scm:git:https://github.com/bb4/$repoName.git"
    val scmDeveloperConnection = "scm:git:ssh://git@github.com/bb4/$repoName.git"
    val pomDescription = projectRef.description?.takeIf { it.isNotBlank() } ?: projectRef.name

    publishingExt.publications.withType(MavenPublication::class.java).configureEach {
        if (name == "pluginMaven") {
            groupId = projectRef.group.toString()
            artifactId = "bb4-gradle"
        }
        pom {
            name.set(projectRef.name)
            description.set(pomDescription)
            url.set(githubUrl)
            scm {
                url.set(githubUrl)
                connection.set(scmConnection)
                developerConnection.set(scmDeveloperConnection)
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

        // Attach CycloneDX Direct SBOM when the plugin/task is present.
        if (projectRef.plugins.hasPlugin("org.cyclonedx.bom") &&
            projectRef.tasks.names.contains("cyclonedxDirectBom")
        ) {
            val bomTask = projectRef.tasks.named("cyclonedxDirectBom")
            artifact(
                bomTask.map { task ->
                    task.outputs.files.files.firstOrNull { it.name.endsWith(".json") }
                        ?: error("CycloneDX task ${task.path} produced no JSON SBOM")
                },
            ) {
                classifier = "cyclonedx"
                extension = "json"
                builtBy(bomTask)
            }
        }
    }

    if (isReleaseVersion) {
        val signingKey = projectRef.findProperty("signingKey") as String?
        val signingPassword = projectRef.findProperty("signingPassword") as String?
        val signingKeyId = projectRef.findProperty("signingKeyId") as String?
        if (!signingKey.isNullOrBlank()) {
            if (!signingKeyId.isNullOrBlank()) {
                signingExt.useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            } else {
                signingExt.useInMemoryPgpKeys(signingKey, signingPassword)
            }
        }
        signingExt.sign(publishingExt.publications)
    }

    // Root project owns aggregated publishArtifacts (root + :platform).
    if (projectRef == projectRef.rootProject &&
        projectRef.tasks.names.contains("publishArtifacts").not()
    ) {
        registerRootPublishArtifacts(projectRef, isReleaseVersion, localRepoName, stagingDir)
    }
}

fun registerRootPublishArtifacts(
    root: Project,
    isRelease: Boolean,
    localRepoName: String,
    stagingDir: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
) {
    val projects = listOf(root, root.project(":platform"))

    if (!isRelease) {
        root.tasks.register("publishArtifacts") {
            group = "publishing"
            description = "Publish SNAPSHOT artifacts (root + :platform) to Central maven-snapshots."
            projects.forEach { dependsOn(it.tasks.named("publish")) }
        }
        return
    }

    val publishToLocalName = "publishAllPublicationsTo${localRepoName.replaceFirstChar { it.uppercase() }}Repository"
    val zipTask = root.tasks.register("zipBb4CentralBundle", Zip::class.java) {
        group = "publishing"
        description = "Zip aggregated signed Maven layout for Central Publisher API upload"
        projects.forEach { dependsOn(it.tasks.named(publishToLocalName)) }
        archiveFileName.set("bb4-central-bundle.zip")
        destinationDirectory.set(root.layout.buildDirectory)
        from(stagingDir)
        includeEmptyDirs = false
    }

    val uploadTask = root.tasks.register("uploadBb4CentralBundle") {
        group = "publishing"
        description = "Upload the aggregated Central deployment bundle via Publisher API"
        dependsOn(zipTask)
        inputs.file(zipTask.flatMap { it.archiveFile })
        outputs.upToDateWhen { false }
        doLast {
            val user = root.bb4OssrhUsername()
            val pass = root.bb4OssrhPassword()
            require(user.isNotBlank() && pass.isNotBlank()) {
                "Missing Central Portal token. Set ossrhToken/ossrhTokenPassword or OSSRH_USERNAME/OSSRH_PASSWORD."
            }
            val bundle = zipTask.get().archiveFile.get().asFile
            val publishingType = root.bb4PublishingType()
            val publicationName = "${root.group}:${root.name}:${root.version}"
            val deploymentId = uploadCentralBundle(bundle, user, pass, publishingType, publicationName)
            logger.lifecycle("Uploaded Central deployment id={} publishingType={}", deploymentId, publishingType)
            if (publishingType == "AUTOMATIC") {
                val state = waitForCentralDeployment(deploymentId, user, pass)
                logger.lifecycle("Central deployment {} finished with state={}", deploymentId, state)
            } else {
                logger.lifecycle(
                    "publishingType=USER_MANAGED: open https://central.sonatype.com/publishing to Publish after VALIDATED.",
                )
            }
        }
    }

    root.tasks.register("publishArtifacts") {
        group = "publishing"
        description = "Build a signed Central deployment bundle (root + platform) and upload via Publisher API."
        dependsOn(uploadTask)
    }
}

fun uploadCentralBundle(
    bundle: java.io.File,
    username: String,
    password: String,
    publishingType: String,
    publicationName: String,
): String {
    val boundary = "----bb4Central" + UUID.randomUUID().toString().replace("-", "")
    val fileBytes = bundle.readBytes()
    val preamble = buildString {
        append("--").append(boundary).append("\r\n")
        append("Content-Disposition: form-data; name=\"bundle\"; filename=\"")
            .append(bundle.name).append("\"\r\n")
        append("Content-Type: application/octet-stream\r\n\r\n")
    }.toByteArray(StandardCharsets.UTF_8)
    val epilogue = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
    val body = preamble + fileBytes + epilogue

    val query = "publishingType=${URLEncoder.encode(publishingType, StandardCharsets.UTF_8)}" +
        "&name=${URLEncoder.encode(publicationName, StandardCharsets.UTF_8)}"
    val uri = URI.create("https://central.sonatype.com/api/v1/publisher/upload?$query")
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    val request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMinutes(10))
        .header("Authorization", "Bearer $token")
        .header("Content-Type", "multipart/form-data; boundary=$boundary")
        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    require(response.statusCode() == 201) {
        "Central Publisher upload failed HTTP ${response.statusCode()}: ${response.body()}"
    }
    val deploymentId = response.body()?.trim().orEmpty()
    require(deploymentId.isNotEmpty()) { "Central Publisher upload returned empty deployment id" }
    return deploymentId
}

fun waitForCentralDeployment(deploymentId: String, username: String, password: String): String {
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    val deadline = System.nanoTime() + Duration.ofMinutes(30).toNanos()
    while (true) {
        val uri = URI.create(
            "https://central.sonatype.com/api/v1/publisher/status?id=" +
                URLEncoder.encode(deploymentId, StandardCharsets.UTF_8),
        )
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer $token")
            .header("accept", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) {
            "Central Publisher status failed HTTP ${response.statusCode()}: ${response.body()}"
        }
        val body = response.body().orEmpty()
        val state = Regex(""""deploymentState"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
        println("Central deployment $deploymentId state=$state")
        when (state) {
            "PUBLISHED", "VALIDATED" -> return state
            "FAILED" -> error("Central deployment $deploymentId FAILED: $body")
        }
        check(System.nanoTime() < deadline) {
            "Timed out waiting for Central deployment $deploymentId (last state=$state)"
        }
        Thread.sleep(Duration.ofSeconds(15).toMillis())
    }
}
