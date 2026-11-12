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
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.scala.ScalaDoc

import javax.inject.Inject

class Bb4PublishPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply('maven-publish')
        project.pluginManager.apply('signing')
        Bb4Conventions.configureOssrhDefaults(project)
        Bb4CycloneDxSupport.configure(project)

        // Wait for java-library or java plugin to provide the main source set
        project.pluginManager.withPlugin('java') {
            configurePublishing(project)
        }
    }

    private void configurePublishing(Project project) {
        def main = project.extensions.getByType(SourceSetContainer).named('main')

        def javadocTask = project.tasks.named('javadoc', Javadoc)

        javadocTask.configure { Javadoc javadoc ->
            def copyAction = project.objects.newInstance(CopyPackageImages)
            copyAction.sourceFiles.from(main.map { it.allSource })
            copyAction.destinationDir.set(javadoc.destinationDirectory)
            javadoc.doLast(copyAction)
        }

        def sourcesJar = project.tasks.register('sourcesJar', Jar) { Jar jar ->
            jar.description = 'Source jar for Sonatype'
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            jar.archiveClassifier.set('sources')
            jar.from main.map { it.allSource }
            jar.dependsOn project.tasks.named(JavaPlugin.CLASSES_TASK_NAME)
        }

        def javadocJar = project.tasks.register('javadocJar', Jar) { Jar jar ->
            jar.description = 'Javadoc jar for Sonatype'
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            jar.archiveClassifier.set('javadoc')
            jar.from javadocTask.map { javadocDestination(it) }
            jar.dependsOn javadocTask
        }

        // Register scaladocJar only when the scala plugin is applied
        def scaladocJarHolder = [taskProvider: null]
        project.pluginManager.withPlugin('scala') {
            def scaladocTask = project.tasks.named('scaladoc', ScalaDoc)
            scaladocJarHolder.taskProvider = project.tasks.register('scaladocJar', Jar) { Jar jar ->
                jar.description = 'Scaladoc jar for Sonatype'
                jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                jar.archiveClassifier.set('scaladoc')
                jar.from scaladocTask.map { scaladocDestination(it) }
                jar.dependsOn scaladocTask
            }
        }

        // Defer publication creation, deploy URL, and signing until after the project (and gradle.properties)
        // have set version and jarMap — both are often assigned after the plugins {} block in consuming builds.
        project.afterEvaluate {
            def bb4Extension = project.extensions.findByType(Bb4Extension)
            def jarMap = Bb4JarMap.resolve(project, bb4Extension)
            def multiJar = !jarMap.isEmpty()
            def hasScala = project.plugins.hasPlugin('scala')

            if (multiJar) {
                project.tasks.named('jar', Jar).configure { it.enabled = false }
                sourcesJar.configure { it.enabled = false }
                javadocJar.configure { it.enabled = false }
                if (scaladocJarHolder.taskProvider != null) {
                    scaladocJarHolder.taskProvider.configure { it.enabled = false }
                }

                jarMap.each { String key, Map spec ->
                    Bb4JarMap.validateEntry(key, spec)
                    def includes = spec['include'] as Iterable<?> ?: []
                    def excludes = (spec['exclude'] as Iterable<?>)?.collect { it.toString() } ?: []

                    project.tasks.register("${key}Jar", Jar) { Jar jar ->
                        jar.archiveBaseName.set("bb4-$key")
                        jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        jar.from main.map { it.output }
                        includes.each { jar.include it.toString() }
                        excludes.each { jar.exclude it }
                        jar.manifest {
                            it.attributes(
                                'Implementation-Title': "$key code",
                                'Implementation-Version': project.version.toString(),
                                'Application-Name': key,
                                'Built-By': System.getProperty('user.name'),
                                provider: 'gradle',
                            )
                        }
                    }
                    project.tasks.register("${key}JavadocJar", Jar) { Jar jar ->
                        jar.description = "Javadoc jar for $key"
                        jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        jar.archiveClassifier.set('javadoc')
                        jar.archiveBaseName.set("bb4-$key")
                        jar.from javadocTask.map { javadocDestination(it) }
                        includes.each { jar.include it.toString() }
                        jar.dependsOn javadocTask
                    }
                    if (hasScala) {
                        def scaladocTask = project.tasks.named('scaladoc', ScalaDoc)
                        project.tasks.register("${key}ScaladocJar", Jar) { Jar jar ->
                            jar.description = "Scaladoc jar for $key"
                            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                            jar.archiveClassifier.set('scaladoc')
                            jar.archiveBaseName.set("bb4-$key")
                            jar.from scaladocTask.map { scaladocDestination(it) }
                            includes.each { jar.include it.toString() }
                            jar.dependsOn scaladocTask
                        }
                    }
                    project.tasks.register("${key}SourcesJar", Jar) { Jar jar ->
                        jar.description = "Sources jar for $key"
                        jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        jar.archiveClassifier.set('sources')
                        jar.archiveBaseName.set("bb4-$key")
                        jar.from main.map { it.allSource }
                        includes.each { jar.include it.toString() }
                        excludes.each { jar.exclude it }
                        jar.dependsOn project.tasks.named(JavaPlugin.CLASSES_TASK_NAME)
                    }
                }
            }

            PublishingExtension evaluatedPublishing = project.extensions.getByType(PublishingExtension)

            if (!multiJar) {
                evaluatedPublishing.publications.create('mavenJava', MavenPublication) { MavenPublication pub ->
                    pub.artifactId = project.name
                    pub.groupId = Bb4PomSupport.groupId(project)
                    pub.from project.components.java
                    pub.artifact sourcesJar
                    pub.artifact javadocJar
                    if (hasScala && scaladocJarHolder.taskProvider != null) {
                        pub.artifact scaladocJarHolder.taskProvider
                    }
                    Bb4PomSupport.configurePom(pub, project)
                    Bb4CycloneDxSupport.attachBomToPublication(project, pub)
                }
            } else {
                def finalVersion = project.version.toString()
                jarMap.each { String key, Map ignored ->
                    evaluatedPublishing.publications.create("maven${key.capitalize()}", MavenPublication) { MavenPublication pub ->
                        pub.groupId = Bb4PomSupport.groupId(project)
                        pub.artifactId = "bb4-$key"
                        pub.version = finalVersion
                        pub.artifact project.tasks.named("${key}Jar", Jar)
                        pub.artifact project.tasks.named("${key}JavadocJar", Jar)
                        if (hasScala) {
                            pub.artifact project.tasks.named("${key}ScaladocJar", Jar)
                        }
                        pub.artifact project.tasks.named("${key}SourcesJar", Jar)
                        Bb4PomSupport.configurePom(pub, project, true)
                        Bb4CycloneDxSupport.attachBomToPublication(project, pub)
                    }
                }
            }

            Bb4SigningSupport.configure(project, evaluatedPublishing)
            Bb4CentralBundleSupport.configure(project)
        }
    }

    /**
     * Resolve Javadoc output directory across Gradle versions.
     * Gradle 8.14+: prefer {@code destinationDir} / {@code getDestinationDir()} (still populated for {@link Javadoc});
     * Older Gradle: fall back to {@code destinationDirectory} when it is a present {@link org.gradle.api.file.DirectoryProperty}.
     */
    private static File javadocDestination(Javadoc javadoc) {
        return documentationOutputDir(javadoc)
    }

    /** Same strategy as {@link #javadocDestination(Javadoc)} for {@link ScalaDoc}. */
    private static File scaladocDestination(ScalaDoc scaladoc) {
        return documentationOutputDir(scaladoc)
    }

    private static File documentationOutputDir(Object docTask) {
        if (docTask.hasProperty('destinationDir')) {
            def dir = docTask.destinationDir
            if (dir != null) {
                return dir as File
            }
        }
        if (docTask.metaClass.respondsTo(docTask, 'getDestinationDir')) {
            def dir = docTask.getDestinationDir()
            if (dir != null) {
                return dir as File
            }
        }
        if (docTask.hasProperty('destinationDirectory')) {
            def dd = docTask.destinationDirectory
            if (dd instanceof DirectoryProperty && dd.present) {
                return dd.get().asFile
            }
        }
        return null
    }

    /**
     * Task action that copies package.png images from source directories to javadoc output.
     * Uses injected FileSystemOperations instead of capturing Project at execution time.
     */
    static abstract class CopyPackageImages implements Action<Task> {
        @Inject
        abstract FileSystemOperations getFileSystemOperations()

        abstract ConfigurableFileCollection getSourceFiles()

        abstract DirectoryProperty getDestinationDir()

        @Override
        void execute(Task task) {
            if (!destinationDir.present) {
                return
            }
            fileSystemOperations.copy {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from sourceFiles
                into destinationDir
                include '**/package.png'
            }
        }
    }
}
