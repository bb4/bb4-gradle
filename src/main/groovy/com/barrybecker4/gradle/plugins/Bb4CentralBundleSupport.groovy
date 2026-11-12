// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.bundling.Zip

import java.nio.file.Files
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Wires snapshot deploy vs release bundle upload for {@code publishArtifacts}.
 */
final class Bb4CentralBundleSupport {

    static final String LOCAL_REPO_NAME = 'bb4CentralLocal'
    static final String STAGING_DIR = 'bb4-central-staging'
    static final String BUNDLE_ZIP = 'bb4-central-bundle.zip'
    static final String ZIP_TASK = 'zipBb4CentralBundle'
    static final String UPLOAD_TASK = 'uploadBb4CentralBundle'
    static final String DEFAULT_PUBLISHING_TYPE = 'AUTOMATIC'

    private Bb4CentralBundleSupport() {}

    static String publishingType(Project project) {
        def explicit = project.findProperty('bb4.central.publishingType')?.toString()?.trim()
        if (explicit) {
            return explicit.toUpperCase()
        }
        return DEFAULT_PUBLISHING_TYPE
    }

    static String ossrhUsername(Project project) {
        return project.providers.gradleProperty('ossrhToken').orNull
            ?: System.getenv('OSSRH_USERNAME')
            ?: ''
    }

    static String ossrhPassword(Project project) {
        return project.providers.gradleProperty('ossrhTokenPassword').orNull
            ?: System.getenv('OSSRH_PASSWORD')
            ?: ''
    }

    /**
     * Configure repositories and {@code publishArtifacts} for a single project that owns publications.
     */
    static void configure(Project project) {
        def publishing = project.extensions.getByType(PublishingExtension)
        def isSnapshot = project.version.toString().endsWith('-SNAPSHOT')
        def stagingDir = project.layout.buildDirectory.dir(STAGING_DIR)

        if (isSnapshot) {
            publishing.repositories.maven {
                name = 'bb4Sonatype'
                url = project.uri(Bb4SnapshotRepoSupport.resolveSnapshotRepoUrl(project))
                credentials {
                    username = ossrhUsername(project)
                    password = ossrhPassword(project)
                }
            }
            project.tasks.register('publishArtifacts') {
                group = 'publishing'
                description = 'Publish SNAPSHOT artifacts to Central maven-snapshots.'
                dependsOn 'publish'
            }
            return
        }

        // Releases: local file repo → zip → Publisher API (no OSSRH Staging API).
        publishing.repositories.maven {
            name = LOCAL_REPO_NAME
            url = stagingDir.get().asFile.toURI()
        }

        def publishToLocal = "publishAllPublicationsTo${LOCAL_REPO_NAME.capitalize()}Repository"

        def zipTask = project.tasks.register(ZIP_TASK, Zip) { Zip zip ->
            zip.group = 'publishing'
            zip.description = 'Zip signed Maven layout for Central Publisher API upload'
            zip.dependsOn publishToLocal
            zip.archiveFileName.set(BUNDLE_ZIP)
            zip.destinationDirectory.set(project.layout.buildDirectory)
            zip.from(stagingDir)
            // Central expects repo root entries (com/...), not a top-level folder wrapper.
            zip.includeEmptyDirs = false
        }

        def uploadTask = project.tasks.register(UPLOAD_TASK) { Task task ->
            task.group = 'publishing'
            task.description = 'Upload the Central deployment bundle via Publisher API'
            task.dependsOn zipTask
            task.inputs.file(zipTask.flatMap { it.archiveFile })
            task.outputs.upToDateWhen { false }
            task.doLast {
                def user = ossrhUsername(project)
                def pass = ossrhPassword(project)
                if (!user || !pass) {
                    throw new IllegalStateException(
                        'Missing Central Portal token. Set ossrhToken/ossrhTokenPassword or OSSRH_USERNAME/OSSRH_PASSWORD.'
                    )
                }
                def bundle = zipTask.get().archiveFile.get().asFile.toPath()
                def type = publishingType(project)
                def name = "${project.group}:${project.name}:${project.version}"
                def client = new Bb4CentralPortalClient()
                def deploymentId = client.uploadBundle(bundle, user, pass, type, name)
                project.logger.lifecycle("Uploaded Central deployment id={} publishingType={}", deploymentId, type)
                if (type == 'AUTOMATIC') {
                    def state = client.waitForDeployment(
                        deploymentId, user, pass,
                        Duration.ofMinutes(30), Duration.ofSeconds(15),
                        { msg -> project.logger.lifecycle(msg) },
                    )
                    project.logger.lifecycle("Central deployment {} finished with state={}", deploymentId, state)
                    if (state == 'VALIDATED') {
                        project.logger.lifecycle(
                            'Deployment VALIDATED (USER_MANAGED semantics). Publish from the Portal UI if needed.'
                        )
                    }
                } else {
                    project.logger.lifecycle(
                        'publishingType=USER_MANAGED: open https://central.sonatype.com/publishing to Publish after VALIDATED.'
                    )
                }
            }
        }

        project.tasks.register('publishArtifacts') {
            group = 'publishing'
            description = 'Build a signed Central deployment bundle and upload it via the Publisher API.'
            dependsOn uploadTask
        }
    }

    /**
     * Root-project variant that stages publications from multiple projects into one bundle.
     * @param projects projects whose {@code publishAllPublicationsToBb4CentralLocalRepository} tasks contribute
     */
    static void configureAggregatingRoot(Project rootProject, List<Project> projects) {
        def isSnapshot = rootProject.version.toString().endsWith('-SNAPSHOT')

        if (isSnapshot) {
            rootProject.tasks.register('publishArtifacts') {
                group = 'publishing'
                description = 'Publish SNAPSHOT artifacts (root + listed projects) to Central maven-snapshots.'
                projects.each { Project p ->
                    dependsOn p.tasks.named('publish')
                }
            }
            return
        }

        def stagingDir = rootProject.layout.buildDirectory.dir(STAGING_DIR)
        def localPublishTasks = []

        projects.each { Project p ->
            def publishing = p.extensions.getByType(PublishingExtension)
            // Avoid duplicating if already configured
            def existing = publishing.repositories.findByName(LOCAL_REPO_NAME)
            if (existing == null) {
                publishing.repositories.maven {
                    name = LOCAL_REPO_NAME
                    url = stagingDir.get().asFile.toURI()
                }
            } else {
                existing.setUrl(stagingDir.get().asFile.toURI())
            }
            localPublishTasks << p.tasks.named("publishAllPublicationsTo${LOCAL_REPO_NAME.capitalize()}Repository")
        }

        def zipTask = rootProject.tasks.register(ZIP_TASK, Zip) { Zip zip ->
            zip.group = 'publishing'
            zip.description = 'Zip aggregated signed Maven layout for Central Publisher API upload'
            localPublishTasks.each { zip.dependsOn it }
            zip.archiveFileName.set(BUNDLE_ZIP)
            zip.destinationDirectory.set(rootProject.layout.buildDirectory)
            zip.from(stagingDir)
            zip.includeEmptyDirs = false
        }

        def uploadTask = rootProject.tasks.register(UPLOAD_TASK) { Task task ->
            task.group = 'publishing'
            task.description = 'Upload the aggregated Central deployment bundle via Publisher API'
            task.dependsOn zipTask
            task.inputs.file(zipTask.flatMap { it.archiveFile })
            task.outputs.upToDateWhen { false }
            task.doLast {
                def user = ossrhUsername(rootProject)
                def pass = ossrhPassword(rootProject)
                if (!user || !pass) {
                    throw new IllegalStateException(
                        'Missing Central Portal token. Set ossrhToken/ossrhTokenPassword or OSSRH_USERNAME/OSSRH_PASSWORD.'
                    )
                }
                def bundle = zipTask.get().archiveFile.get().asFile.toPath()
                def type = publishingType(rootProject)
                def name = "${rootProject.group}:${rootProject.name}:${rootProject.version}"
                def client = new Bb4CentralPortalClient()
                def deploymentId = client.uploadBundle(bundle, user, pass, type, name)
                rootProject.logger.lifecycle("Uploaded Central deployment id={} publishingType={}", deploymentId, type)
                if (type == 'AUTOMATIC') {
                    def state = client.waitForDeployment(
                        deploymentId, user, pass,
                        Duration.ofMinutes(30), Duration.ofSeconds(15),
                        { msg -> rootProject.logger.lifecycle(msg) },
                    )
                    rootProject.logger.lifecycle("Central deployment {} finished with state={}", deploymentId, state)
                } else {
                    rootProject.logger.lifecycle(
                        'publishingType=USER_MANAGED: open https://central.sonatype.com/publishing to Publish after VALIDATED.'
                    )
                }
            }
        }

        rootProject.tasks.register('publishArtifacts') {
            group = 'publishing'
            description = 'Build a signed Central deployment bundle (root + platform) and upload via Publisher API.'
            dependsOn uploadTask
        }
    }

    /** Zip a directory's children (Maven layout) into {@code outZip}. Used by unit tests. */
    static void zipDirectoryContents(File sourceDir, File outZip) {
        outZip.parentFile.mkdirs()
        new ZipOutputStream(new FileOutputStream(outZip)).withCloseable { zos ->
            sourceDir.eachFileRecurse { File file ->
                if (file.isDirectory()) {
                    return
                }
                def relative = sourceDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                zos.putNextEntry(new ZipEntry(relative))
                Files.copy(file.toPath(), zos)
                zos.closeEntry()
            }
        }
    }
}
