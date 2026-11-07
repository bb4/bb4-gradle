// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
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
        // Store the task provider in a holder that can be accessed later
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

        def jarMap = readJarMap(project)
        def explicitJarMap = project.extensions.extraProperties.has('jarMap') || project.findProperty('jarMap') != null
        def multiJar = !jarMap.isEmpty()

        if (explicitJarMap && !multiJar) {
            project.tasks.named('jar', Jar).configure { it.enabled = false }
            sourcesJar.configure { it.enabled = false }
            javadocJar.configure { it.enabled = false }
            if (scaladocJarHolder.taskProvider != null) {
                scaladocJarHolder.taskProvider.configure { it.enabled = false }
            }
        }

        if (multiJar) {
            project.tasks.named('jar', Jar).configure { it.enabled = false }
            sourcesJar.configure { it.enabled = false }
            javadocJar.configure { it.enabled = false }
            if (scaladocJarHolder.taskProvider != null) {
                scaladocJarHolder.taskProvider.configure { it.enabled = false }
            }

            jarMap.each { String key, Map spec ->
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
                // Register Scaladoc jar for multi-jar when scala plugin is present
                project.pluginManager.withPlugin('scala') {
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

        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)

        // Use afterEvaluate to determine if Scala plugin is present and add scaladoc to publications
        project.afterEvaluate {
            def hasScala = project.plugins.hasPlugin('scala')
            
            if (!multiJar) {
                publishing.publications.create('mavenJava', MavenPublication) { MavenPublication pub ->
                    pub.artifactId = project.name
                    pub.groupId = 'com.barrybecker4'
                    pub.from project.components.java
                    pub.artifact sourcesJar
                    pub.artifact javadocJar
                    if (hasScala && scaladocJarHolder.taskProvider != null) {
                        pub.artifact scaladocJarHolder.taskProvider
                    }
                    addPom(pub, project)
                }
            } else {
                jarMap.each { String key, Map ignored ->
                    publishing.publications.create("maven${key.capitalize()}", MavenPublication) { MavenPublication pub ->
                        pub.groupId = 'com.barrybecker4'
                        pub.artifactId = "bb4-$key"
                        pub.version = project.version.toString()
                        pub.artifact project.tasks.named("${key}Jar", Jar)
                        pub.artifact project.tasks.named("${key}JavadocJar", Jar)
                        if (hasScala) {
                            pub.artifact project.tasks.named("${key}ScaladocJar", Jar)
                        }
                        pub.artifact project.tasks.named("${key}SourcesJar", Jar)
                        addPom(pub, project)
                    }
                }
            }
        }

        // Defer deploy URL and signing until after the project (and gradle.properties) have set
        // version — it is often assigned after the plugins {} block in consuming builds.
        project.afterEvaluate {
            PublishingExtension evaluatedPublishing = project.extensions.getByType(PublishingExtension)
            def releasesRepoUrl = resolveBb4ReleaseStagingRepoUrl(project)
            def snapshotRepoUrl = resolveBb4SnapshotRepoUrl(project)
            def versionString = project.version.toString()
            def isSnapshot = versionString.endsWith('-SNAPSHOT')

            evaluatedPublishing.repositories.maven {
                name = 'bb4Sonatype'
                url = project.uri(isSnapshot ? snapshotRepoUrl : releasesRepoUrl)
                credentials {
                    username = project.providers.gradleProperty('ossrhToken').orNull ?: System.getenv('OSSRH_USERNAME') ?: ''
                    password = project.providers.gradleProperty('ossrhTokenPassword').orNull ?: System.getenv('OSSRH_PASSWORD') ?: ''
                }
            }

            def isRelease = !isSnapshot
            project.signing {
                required = isRelease
                // Only register Sign tasks for releases; with required=false and no GPG key, Gradle can
                // fail evaluating Sign.onlyIf ("Signing is required, or signatory is set").
                if (isRelease) {
                    sign evaluatedPublishing.publications
                }
            }
        }

        project.tasks.register('publishArtifacts') {
            group = 'publishing'
            description = 'Publish artifacts to Central snapshot or staging repository. ' +
                'For releases, also POST …/manual/upload/defaultRepository/<namespace> ' +
                '(see bb4-gradle docs/publishing-sonatype.md) or the Portal stays empty.'
            dependsOn 'publish'
        }
    }

    /** Legacy OSSRH hosts return 405 after EOL; gradle.properties may still override with these. */
    private static String resolveBb4SnapshotRepoUrl(Project project) {
        def defaultUrl = 'https://central.sonatype.com/repository/maven-snapshots/'
        def explicit = (project.findProperty('bb4.ossrh.snapshotUrl') ?: project.findProperty('bb4.central.snapshotUrl'))?.toString()?.trim()
        if (!explicit) return defaultUrl
        if (explicit.contains('oss.sonatype.org')) {
            project.logger.warn("bb4 Gradle: ignoring legacy bb4.ossrh.snapshotUrl (OSSRH EOL): {} — using {}", explicit, defaultUrl)
            return defaultUrl
        }
        return explicit
    }

    private static String resolveBb4ReleaseStagingRepoUrl(Project project) {
        def defaultUrl = 'https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/'
        def explicit = (project.findProperty('bb4.ossrh.releaseStagingUrl') ?: project.findProperty('bb4.central.releaseStagingUrl'))?.toString()?.trim()
        if (!explicit) return defaultUrl
        if (explicit.contains('oss.sonatype.org')) {
            project.logger.warn("bb4 Gradle: ignoring legacy bb4.ossrh.releaseStagingUrl (OSSRH EOL): {} — using {}", explicit, defaultUrl)
            return defaultUrl
        }
        return explicit
    }

    private static void addPom(MavenPublication pub, Project project) {
        pub.pom {
            name = project.name
            packaging = 'jar'
            description = "${project.name} java code."
            url = "https://github.com/barrybecker4/${project.name}"
            scm {
                url = "scm:git@github.com:barrybecker4/${project.name}.git"
                connection = "scm:git@github.com:barrybecker4/${project.name}.git"
                developerConnection = "scm:git@github.com:barrybecker4/${project.name}.git"
            }
            licenses {
                license {
                    name = 'The MIT license'
                    url = 'http://www.opensource.org/licenses/MIT'
                    distribution = 'repo'
                }
            }
            developers {
                developer {
                    id = 'barrybecker4'
                    name = 'Barry G. Becker'
                    email = 'barrybecker4@gmail.com'
                }
            }
        }
    }

    /**
     * Resolve Javadoc output directory across Gradle versions.
     * Gradle 8.14+: prefer {@code destinationDir} / {@code getDestinationDir()} (still populated for {@link Javadoc});
     * {@link org.gradle.api.file.DirectoryProperty#present} can misbehave during lazy {@code jar.from} wiring.
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
            if (dd instanceof org.gradle.api.file.DirectoryProperty && dd.present) {
                return dd.get().asFile
            }
        }
        return null
    }

    private static Map<String, Map> readJarMap(Project project) {
        def raw = project.findProperty('jarMap')
        if (!(raw instanceof Map)) {
            return [:]
        }
        def out = new LinkedHashMap<String, Map>()
        raw.each { k, v ->
            if (k instanceof String && v instanceof Map) {
                out[k] = v as Map
            }
        }
        return out
    }

    /**
     * Task action that copies package.png images from source directories to javadoc output.
     * Uses injected FileSystemOperations instead of capturing Project at execution time.
     */
    static abstract class CopyPackageImages implements org.gradle.api.Action<org.gradle.api.Task> {
        @Inject
        abstract FileSystemOperations getFileSystemOperations()

        abstract org.gradle.api.file.ConfigurableFileCollection getSourceFiles()

        abstract org.gradle.api.file.DirectoryProperty getDestinationDir()

        @Override
        void execute(org.gradle.api.Task task) {
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
