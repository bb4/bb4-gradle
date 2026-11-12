// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.scala.ScalaDoc
import java.io.File
import javax.inject.Inject

class Bb4PublishPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("maven-publish")
        project.pluginManager.apply("signing")
        Bb4Conventions.configureOssrhDefaults(project)
        Bb4CycloneDxSupport.configure(project)

        project.pluginManager.withPlugin("java") {
            configurePublishing(project)
        }
    }

    private fun configurePublishing(project: Project) {
        val main = project.extensions.getByType(SourceSetContainer::class.java).named("main")
        val javadocTask = project.tasks.named("javadoc", Javadoc::class.java)

        javadocTask.configure { javadoc ->
            val copyAction = project.objects.newInstance(CopyPackageImages::class.java)
            copyAction.sourceFiles.from(main.map { it.allSource })
            copyAction.destinationDir.set(javadoc.destinationDirectory)
            javadoc.doLast(copyAction)
        }

        val sourcesJar = project.tasks.register("sourcesJar", Jar::class.java) { jar ->
            jar.description = "Source jar for Sonatype"
            // allSource unions language sources with resources; when resource roots
            // overlap source roots (bb4 flat layout), co-located assets like
            // package.png appear twice. EXCLUDE is only for that overlap.
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            jar.archiveClassifier.set("sources")
            jar.from(main.map { it.allSource })
            jar.dependsOn(project.tasks.named(JavaPlugin.CLASSES_TASK_NAME))
        }

        val javadocJar = project.tasks.register("javadocJar", Jar::class.java) { jar ->
            jar.description = "Javadoc jar for Sonatype"
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            jar.archiveClassifier.set("javadoc")
            jar.from(javadocTask.map { documentationOutputDir(it) })
            jar.dependsOn(javadocTask)
        }

        var scaladocJar: TaskProvider<Jar>? = null
        project.pluginManager.withPlugin("scala") {
            val scaladocTask = project.tasks.named("scaladoc", ScalaDoc::class.java)
            scaladocJar = project.tasks.register("scaladocJar", Jar::class.java) { jar ->
                jar.description = "Scaladoc jar for Sonatype"
                jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                jar.archiveClassifier.set("scaladoc")
                jar.from(scaladocTask.map { documentationOutputDir(it) })
                jar.dependsOn(scaladocTask)
            }
        }

        val publishing = project.extensions.getByType(PublishingExtension::class.java)

        // Defer mavenJava until afterEvaluate so group/version set after plugins {} are visible.
        fun createMavenJavaPublication() {
            if (publishing.publications.findByName("mavenJava") != null) {
                return
            }
            publishing.publications.create("mavenJava", MavenPublication::class.java) { pub ->
                pub.artifactId = project.name
                pub.groupId = Bb4PomSupport.groupId(project)
                pub.version = project.version.toString()
                pub.from(project.components.getByName("java"))
                pub.artifact(sourcesJar)
                pub.artifact(javadocJar)
                val scalaJar = scaladocJar
                if (scalaJar != null) {
                    pub.artifact(scalaJar)
                } else {
                    project.pluginManager.withPlugin("scala") {
                        scaladocJar?.let { pub.artifact(it) }
                    }
                }
                Bb4PomSupport.configurePom(pub, project)
                Bb4CycloneDxSupport.attachBomToPublication(project, pub)
            }
        }

        project.afterEvaluate {
            createMavenJavaPublication()
            publishing.publications.withType(MavenPublication::class.java).configureEach { pub ->
                val gid = Bb4PomSupport.groupId(project)
                require(gid.isNotBlank()) {
                    "bb4.publish: project.group must be set to a non-empty Maven groupId " +
                        "(publication '${pub.name}'). Set `group = '…'` after plugins {}."
                }
                pub.groupId = gid
                pub.version = project.version.toString()
            }
            Bb4SigningSupport.configure(project, publishing)
            Bb4CentralBundleSupport.configure(project)
        }
    }

    companion object {
        private fun documentationOutputDir(docTask: Any): File? {
            try {
                val destinationDirMethod = docTask.javaClass.methods
                    .firstOrNull { it.name == "getDestinationDir" && it.parameterCount == 0 }
                val dir = destinationDirMethod?.invoke(docTask)
                if (dir is java.io.File) {
                    return dir
                }
            } catch (_: Exception) {
                // fall through
            }
            try {
                val ddMethod = docTask.javaClass.methods
                    .firstOrNull { it.name == "getDestinationDirectory" && it.parameterCount == 0 }
                val dd = ddMethod?.invoke(docTask)
                if (dd is DirectoryProperty && dd.isPresent) {
                    return dd.get().asFile
                }
            } catch (_: Exception) {
                // fall through
            }
            return null
        }
    }

    /**
     * Task action that copies package.png images from source directories to javadoc output.
     */
    abstract class CopyPackageImages : Action<Task> {
        @get:Inject
        abstract val fileSystemOperations: FileSystemOperations

        abstract val sourceFiles: ConfigurableFileCollection

        abstract val destinationDir: DirectoryProperty

        override fun execute(task: Task) {
            if (!destinationDir.isPresent) {
                return
            }
            fileSystemOperations.copy {
                it.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                it.from(sourceFiles)
                it.into(destinationDir)
                it.include("**/package.png")
            }
        }
    }
}
