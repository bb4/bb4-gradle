// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Wires snapshot deploy vs release bundle upload for `publishArtifacts`.
 */
object Bb4CentralBundleSupport {

    const val LOCAL_REPO_NAME = "bb4CentralLocal"
    const val STAGING_DIR = "bb4-central-staging"
    const val BUNDLE_ZIP = "bb4-central-bundle.zip"
    const val ZIP_TASK = "zipBb4CentralBundle"
    const val UPLOAD_TASK = "uploadBb4CentralBundle"
    const val DEFAULT_PUBLISHING_TYPE = "AUTOMATIC"

    @JvmStatic
    fun publishingType(project: Project): String {
        val explicit = project.findProperty("bb4.central.publishingType")?.toString()?.trim()
        return explicit?.uppercase() ?: DEFAULT_PUBLISHING_TYPE
    }

    @JvmStatic
    fun ossrhUsername(project: Project): String =
        project.providers.gradleProperty("ossrhToken").orNull
            ?: System.getenv("OSSRH_USERNAME")
            ?: ""

    @JvmStatic
    fun ossrhPassword(project: Project): String =
        project.providers.gradleProperty("ossrhTokenPassword").orNull
            ?: System.getenv("OSSRH_PASSWORD")
            ?: ""

    /**
     * Configure repositories and `publishArtifacts` for a single project that owns publications.
     */
    @JvmStatic
    fun configure(project: Project) {
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")
        val stagingDir = project.layout.buildDirectory.dir(STAGING_DIR)

        if (isSnapshot) {
            publishing.repositories.maven { repo ->
                repo.name = "bb4Sonatype"
                repo.setUrl(project.uri(Bb4SnapshotRepoSupport.resolveSnapshotRepoUrl(project)))
                repo.credentials { creds ->
                    creds.username = ossrhUsername(project)
                    creds.password = ossrhPassword(project)
                }
            }
            project.tasks.register("publishArtifacts") { task ->
                task.group = "publishing"
                task.description = "Publish SNAPSHOT artifacts to Central maven-snapshots."
                task.dependsOn("publish")
            }
            return
        }

        publishing.repositories.maven { repo ->
            repo.name = LOCAL_REPO_NAME
            repo.setUrl(stagingDir.get().asFile.toURI())
        }

        val publishToLocal = "publishAllPublicationsTo${LOCAL_REPO_NAME.replaceFirstChar { it.uppercase() }}Repository"

        val zipTask = project.tasks.register(ZIP_TASK, Zip::class.java) { zip ->
            zip.group = "publishing"
            zip.description = "Zip signed Maven layout for Central Publisher API upload"
            zip.dependsOn(publishToLocal)
            zip.archiveFileName.set(BUNDLE_ZIP)
            zip.destinationDirectory.set(project.layout.buildDirectory)
            zip.from(stagingDir)
            zip.setIncludeEmptyDirs(false)
        }

        val uploadTask = project.tasks.register(UPLOAD_TASK) { task ->
            task.group = "publishing"
            task.description = "Upload the Central deployment bundle via Publisher API"
            task.dependsOn(zipTask)
            task.inputs.file(zipTask.flatMap { it.archiveFile })
            task.outputs.upToDateWhen { false }
            task.doLast {
                val user = ossrhUsername(project)
                val pass = ossrhPassword(project)
                if (user.isEmpty() || pass.isEmpty()) {
                    throw IllegalStateException(
                        "Missing Central Portal token. Set ossrhToken/ossrhTokenPassword or OSSRH_USERNAME/OSSRH_PASSWORD.",
                    )
                }
                val bundle = zipTask.get().archiveFile.get().asFile.toPath()
                val type = publishingType(project)
                val name = "${project.group}:${project.name}:${project.version}"
                val client = Bb4CentralPortalClient()
                val deploymentId = client.uploadBundle(bundle, user, pass, type, name)
                project.logger.lifecycle("Uploaded Central deployment id={} publishingType={}", deploymentId, type)
                if (type == "AUTOMATIC") {
                    val state = client.waitForDeployment(
                        deploymentId,
                        user,
                        pass,
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(15),
                        object : Closure<Any?>(null) {
                            override fun call(vararg args: Any?): Any? {
                                project.logger.lifecycle(args[0].toString())
                                return null
                            }
                        },
                    )
                    project.logger.lifecycle("Central deployment {} finished with state={}", deploymentId, state)
                    if (state == "VALIDATED") {
                        project.logger.lifecycle(
                            "Deployment VALIDATED (USER_MANAGED semantics). Publish from the Portal UI if needed.",
                        )
                    }
                } else {
                    project.logger.lifecycle(
                        "publishingType=USER_MANAGED: open https://central.sonatype.com/publishing to Publish after VALIDATED.",
                    )
                }
            }
        }

        project.tasks.register("publishArtifacts") { task ->
            task.group = "publishing"
            task.description = "Build a signed Central deployment bundle and upload it via the Publisher API."
            task.dependsOn(uploadTask)
        }
    }

    /**
     * Root-project variant that stages publications from multiple projects into one bundle.
     */
    @JvmStatic
    fun configureAggregatingRoot(rootProject: Project, projects: List<Project>) {
        val isSnapshot = rootProject.version.toString().endsWith("-SNAPSHOT")

        if (isSnapshot) {
            rootProject.tasks.register("publishArtifacts") { task ->
                task.group = "publishing"
                task.description = "Publish SNAPSHOT artifacts (root + listed projects) to Central maven-snapshots."
                projects.forEach { p ->
                    task.dependsOn(p.tasks.named("publish"))
                }
            }
            return
        }

        val stagingDir = rootProject.layout.buildDirectory.dir(STAGING_DIR)
        val localPublishTasks = mutableListOf<Any>()

        projects.forEach { p ->
            val publishing = p.extensions.getByType(PublishingExtension::class.java)
            val existing = publishing.repositories.findByName(LOCAL_REPO_NAME)
            if (existing == null) {
                publishing.repositories.maven { repo ->
                    repo.name = LOCAL_REPO_NAME
                    repo.setUrl(stagingDir.get().asFile.toURI())
                }
            } else {
                (existing as org.gradle.api.artifacts.repositories.MavenArtifactRepository)
                    .setUrl(stagingDir.get().asFile.toURI())
            }
            localPublishTasks.add(
                p.tasks.named(
                    "publishAllPublicationsTo${LOCAL_REPO_NAME.replaceFirstChar { it.uppercase() }}Repository",
                ),
            )
        }

        val zipTask = rootProject.tasks.register(ZIP_TASK, Zip::class.java) { zip ->
            zip.group = "publishing"
            zip.description = "Zip aggregated signed Maven layout for Central Publisher API upload"
            localPublishTasks.forEach { zip.dependsOn(it) }
            zip.archiveFileName.set(BUNDLE_ZIP)
            zip.destinationDirectory.set(rootProject.layout.buildDirectory)
            zip.from(stagingDir)
            zip.setIncludeEmptyDirs(false)
        }

        val uploadTask = rootProject.tasks.register(UPLOAD_TASK) { task ->
            task.group = "publishing"
            task.description = "Upload the aggregated Central deployment bundle via Publisher API"
            task.dependsOn(zipTask)
            task.inputs.file(zipTask.flatMap { it.archiveFile })
            task.outputs.upToDateWhen { false }
            task.doLast {
                val user = ossrhUsername(rootProject)
                val pass = ossrhPassword(rootProject)
                if (user.isEmpty() || pass.isEmpty()) {
                    throw IllegalStateException(
                        "Missing Central Portal token. Set ossrhToken/ossrhTokenPassword or OSSRH_USERNAME/OSSRH_PASSWORD.",
                    )
                }
                val bundle = zipTask.get().archiveFile.get().asFile.toPath()
                val type = publishingType(rootProject)
                val name = "${rootProject.group}:${rootProject.name}:${rootProject.version}"
                val client = Bb4CentralPortalClient()
                val deploymentId = client.uploadBundle(bundle, user, pass, type, name)
                rootProject.logger.lifecycle("Uploaded Central deployment id={} publishingType={}", deploymentId, type)
                if (type == "AUTOMATIC") {
                    val state = client.waitForDeployment(
                        deploymentId,
                        user,
                        pass,
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(15),
                        object : Closure<Any?>(null) {
                            override fun call(vararg args: Any?): Any? {
                                rootProject.logger.lifecycle(args[0].toString())
                                return null
                            }
                        },
                    )
                    rootProject.logger.lifecycle("Central deployment {} finished with state={}", deploymentId, state)
                } else {
                    rootProject.logger.lifecycle(
                        "publishingType=USER_MANAGED: open https://central.sonatype.com/publishing to Publish after VALIDATED.",
                    )
                }
            }
        }

        rootProject.tasks.register("publishArtifacts") { task ->
            task.group = "publishing"
            task.description = "Build a signed Central deployment bundle (root + platform) and upload via Publisher API."
            task.dependsOn(uploadTask)
        }
    }

    /** Zip a directory's children (Maven layout) into `outZip`. Used by unit tests. */
    @JvmStatic
    fun zipDirectoryContents(sourceDir: File, outZip: File) {
        outZip.parentFile.mkdirs()
        ZipOutputStream(FileOutputStream(outZip)).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = sourceDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                zos.putNextEntry(ZipEntry(relative))
                Files.copy(file.toPath(), zos)
                zos.closeEntry()
            }
        }
    }
}
