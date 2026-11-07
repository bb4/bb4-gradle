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

        // Defer publication creation, deploy URL, and signing until after the project (and gradle.properties)
        // have set version and jarMap — both are often assigned after the plugins {} block in consuming builds.
        project.afterEvaluate {
            def bb4Extension = project.extensions.getByType(Bb4Extension)
            def jarMap = resolveJarMap(project, bb4Extension)
            def multiJar = !jarMap.isEmpty()

            if (multiJar) {
                project.tasks.named('jar', Jar).configure { it.enabled = false }
                sourcesJar.configure { it.enabled = false }
                javadocJar.configure { it.enabled = false }
                scaladocJar.configure { it.enabled = false }

                jarMap.each { String key, Map spec ->
                    validateJarMapEntry(key, spec)
                    def includes = spec['include'] as Iterable<?> ?: []
                    def excludes = (spec['exclude'] as Iterable<?>)?.collect { it.toString() } ?: []

                    project.tasks.register("${key}Jar", Jar) { Jar jar ->
                        jar.archiveBaseName.set("bb4-$key")
                        jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        jar.from main.output
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
                    project.tasks.register("${key}ScaladocJar", Jar) { Jar jar ->
                        jar.description = "Scaladoc jar for $key"
                        jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        jar.archiveClassifier.set('scaladoc')
                        jar.archiveBaseName.set("bb4-$key")
                        jar.from scaladocTask.map { scaladocDestination(it) }
                        includes.each { jar.include it.toString() }
                        jar.dependsOn scaladocTask
                    }
                    project.tasks.register("${key}SourcesJar", Jar) { Jar jar ->
                        jar.description = "Sources jar for $key"
                        jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        jar.archiveClassifier.set('sources')
                        jar.archiveBaseName.set("bb4-$key")
                        jar.from main.allSource
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
                    pub.groupId = 'com.barrybecker4'
                    pub.from project.components.java
                    pub.artifact sourcesJar
                    pub.artifact javadocJar
                    pub.artifact scaladocJar
                    addPom(pub, project)
                }
            } else {
                def finalVersion = project.version.toString()
                jarMap.each { String key, Map ignored ->
                    evaluatedPublishing.publications.create("maven${key.capitalize()}", MavenPublication) { MavenPublication pub ->
                        pub.groupId = 'com.barrybecker4'
                        pub.artifactId = "bb4-$key"
                        pub.version = finalVersion
                        pub.artifact project.tasks.named("${key}Jar", Jar)
                        pub.artifact project.tasks.named("${key}JavadocJar", Jar)
                        pub.artifact project.tasks.named("${key}ScaladocJar", Jar)
                        pub.artifact project.tasks.named("${key}SourcesJar", Jar)
                        addPom(pub, project)
                    }
                }
            }

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

    /**
     * Resolve jarMap from bb4 extension (preferred) or legacy ext.jarMap.
     * Validates the structure and provides clear error messages.
     */
    private static Map<String, Map> resolveJarMap(Project project, Bb4Extension bb4Extension) {
        def extensionJarMap = bb4Extension.jarMap.getOrElse([:])
        def legacyJarMap = project.extensions.extraProperties.has('jarMap') ? 
            project.extensions.extraProperties.get('jarMap') : null
        
        if (!extensionJarMap.isEmpty() && legacyJarMap != null) {
            throw new IllegalStateException(
                "bb4 Gradle: jarMap configured in both bb4 {} extension and ext.jarMap. " +
                "Use only bb4 { jarMap.put('key', [...]) } — ext.jarMap is deprecated."
            )
        }
        
        if (!extensionJarMap.isEmpty()) {
            return new LinkedHashMap<String, Map>(extensionJarMap)
        }
        
        if (legacyJarMap != null) {
            if (!(legacyJarMap instanceof Map)) {
                throw new IllegalArgumentException(
                    "bb4 Gradle: ext.jarMap must be a Map, got ${legacyJarMap.getClass().name}. " +
                    "Use bb4 { jarMap.put('key', [include: [...]]) } instead."
                )
            }
            def out = new LinkedHashMap<String, Map>()
            legacyJarMap.each { k, v ->
                if (!(k instanceof String)) {
                    throw new IllegalArgumentException(
                        "bb4 Gradle: jarMap key must be a String, got ${k.getClass().name} for key: $k"
                    )
                }
                if (!(v instanceof Map)) {
                    throw new IllegalArgumentException(
                        "bb4 Gradle: jarMap value for '$k' must be a Map with 'include' and optional 'exclude', " +
                        "got ${v.getClass().name}"
                    )
                }
                out[k as String] = v as Map
            }
            return out
        }
        
        return [:]
    }
    
    /**
     * Validate a single jarMap entry has required 'include' field.
     */
    private static void validateJarMapEntry(String key, Map spec) {
        if (!spec.containsKey('include')) {
            throw new IllegalArgumentException(
                "bb4 Gradle: jarMap entry '$key' must have 'include' field with file patterns. " +
                "Example: bb4 { jarMap.put('$key', [include: ['com/example/$key/**']]) }"
            )
        }
        def include = spec['include']
        if (include == null || (include instanceof Collection && ((Collection) include).isEmpty())) {
            throw new IllegalArgumentException(
                "bb4 Gradle: jarMap entry '$key' has empty 'include'. " +
                "Provide at least one file pattern."
            )
        }
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
