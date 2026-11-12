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
        val bb4Extension = project.extensions.findByType(Bb4Extension::class.java)

        // Eager single-jar publication; disabled when jarMap entries appear.
        val mavenJava = publishing.publications.create("mavenJava", MavenPublication::class.java) { pub ->
            pub.artifactId = project.name
            pub.groupId = Bb4PomSupport.groupId(project)
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

        var multiJarMode = false

        fun enterMultiJarMode() {
            if (multiJarMode) {
                return
            }
            multiJarMode = true
            project.tasks.named("jar", Jar::class.java).configure { it.isEnabled = false }
            sourcesJar.configure { it.isEnabled = false }
            javadocJar.configure { it.isEnabled = false }
            scaladocJar?.configure { it.isEnabled = false }
            publishing.publications.remove(mavenJava)
        }

        fun registerJarMapEntry(key: String, includes: List<String>, excludes: List<String>) {
            enterMultiJarMode()
            val hasScala = project.plugins.hasPlugin("scala")

            project.tasks.register("${key}Jar", Jar::class.java) { jar ->
                jar.archiveBaseName.set("bb4-$key")
                jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                jar.from(main.map { it.output })
                includes.forEach { jar.include(it) }
                excludes.forEach { jar.exclude(it) }
                jar.manifest { manifest ->
                    manifest.attributes(
                        mapOf(
                            "Implementation-Title" to "$key code",
                            "Implementation-Version" to project.provider { project.version.toString() },
                            "Application-Name" to key,
                            "Built-By" to System.getProperty("user.name"),
                            "provider" to "gradle",
                        ),
                    )
                }
            }
            project.tasks.register("${key}JavadocJar", Jar::class.java) { jar ->
                jar.description = "Javadoc jar for $key"
                jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                jar.archiveClassifier.set("javadoc")
                jar.archiveBaseName.set("bb4-$key")
                jar.from(javadocTask.map { documentationOutputDir(it) })
                includes.forEach { jar.include(it) }
                jar.dependsOn(javadocTask)
            }
            if (hasScala) {
                val scaladocTask = project.tasks.named("scaladoc", ScalaDoc::class.java)
                project.tasks.register("${key}ScaladocJar", Jar::class.java) { jar ->
                    jar.description = "Scaladoc jar for $key"
                    jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                    jar.archiveClassifier.set("scaladoc")
                    jar.archiveBaseName.set("bb4-$key")
                    jar.from(scaladocTask.map { documentationOutputDir(it) })
                    includes.forEach { jar.include(it) }
                    jar.dependsOn(scaladocTask)
                }
            }
            project.tasks.register("${key}SourcesJar", Jar::class.java) { jar ->
                jar.description = "Sources jar for $key"
                jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                jar.archiveClassifier.set("sources")
                jar.archiveBaseName.set("bb4-$key")
                jar.from(main.map { it.allSource })
                includes.forEach { jar.include(it) }
                excludes.forEach { jar.exclude(it) }
                jar.dependsOn(project.tasks.named(JavaPlugin.CLASSES_TASK_NAME))
            }

            val pubName = "maven${key.replaceFirstChar { it.uppercase() }}"
            publishing.publications.create(pubName, MavenPublication::class.java) { pub ->
                pub.groupId = Bb4PomSupport.groupId(project)
                pub.artifactId = "bb4-$key"
                pub.version = project.version.toString()
                pub.artifact(project.tasks.named("${key}Jar", Jar::class.java))
                pub.artifact(project.tasks.named("${key}JavadocJar", Jar::class.java))
                if (hasScala) {
                    pub.artifact(project.tasks.named("${key}ScaladocJar", Jar::class.java))
                }
                pub.artifact(project.tasks.named("${key}SourcesJar", Jar::class.java))
                Bb4PomSupport.configurePom(pub, project, true)
                Bb4CycloneDxSupport.attachBomToPublication(project, pub)
            }
        }

        if (bb4Extension != null) {
            bb4Extension.jarMap.all { entry ->
                Bb4JarMap.validateEntry(entry)
                registerJarMapEntry(
                    entry.name,
                    entry.include.getOrElse(emptyList()),
                    entry.exclude.getOrElse(emptyList()),
                )
            }
        }

        // Legacy ext.jarMap + final group/version for publications (often set after plugins {}).
        // Extension jarMap entries themselves are wired above via container callbacks.
        project.afterEvaluate {
            // Always resolve so dual bb4.jarMap + ext.jarMap conflicts are detected.
            val resolved = Bb4JarMap.resolve(project, bb4Extension)
            if (!multiJarMode && resolved.isNotEmpty()) {
                resolved.forEach { (key, spec) ->
                    Bb4JarMap.validateEntry(key, spec)
                    @Suppress("UNCHECKED_CAST")
                    val includes = (spec["include"] as? Iterable<*>)?.map { it.toString() } ?: emptyList()
                    val excludes = (spec["exclude"] as? Iterable<*>)?.map { it.toString() } ?: emptyList()
                    registerJarMapEntry(key, includes, excludes)
                }
            }
            publishing.publications.withType(MavenPublication::class.java).configureEach { pub ->
                pub.groupId = Bb4PomSupport.groupId(project)
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
