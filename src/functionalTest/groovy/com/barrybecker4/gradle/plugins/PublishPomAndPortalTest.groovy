// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Issue #39: POM from project.group/description/bb4.repository, CycloneDX sidecar,
 * and publishArtifacts wiring for snapshot vs release.
 */
class PublishPomAndPortalTest extends Specification {

    @TempDir
    Path testProjectDir

    Path buildFile
    Path settingsFile

    def setup() {
        buildFile = testProjectDir.resolve('build.gradle')
        settingsFile = testProjectDir.resolve('settings.gradle')
        def sourceDir = testProjectDir.resolve('src/main/java/com/example')
        Files.createDirectories(sourceDir)
        Files.writeString(sourceDir.resolve('Sample.java'), '''
            package com.example;
            public class Sample {}
        ''')
        settingsFile.toFile().text = '''
            pluginManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'bb4-puzzle'
        '''
    }

    def "POM uses project.group description and bb4.repository"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'java-library'
                id 'com.barrybecker4.bb4.publish'
            }

            group = 'com.example'
            version = '1.0.0-SNAPSHOT'
            description = 'Puzzle library'
            ext['bb4.repository'] = 'bb4-puzzles'

            publishing {
                repositories {
                    mavenLocal()
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('generatePomFileForMavenJavaPublication', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':generatePomFileForMavenJavaPublication').outcome == TaskOutcome.SUCCESS
        def pom = testProjectDir.resolve('build/publications/mavenJava/pom-default.xml').toFile()
        pom.exists()
        pom.text.contains('<groupId>com.example</groupId>')
        pom.text.contains('<description>Puzzle library</description>')
        pom.text.contains('https://github.com/bb4/bb4-puzzles')
        !pom.text.contains('bb4-puzzle.git') || pom.text.contains('bb4-puzzles.git')
        pom.text.contains('bb4-puzzles.git')
    }

    def "snapshot publishArtifacts depends on publish not upload"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'java-library'
                id 'com.barrybecker4.bb4.publish'
            }

            group = 'com.example'
            version = '1.0.0-SNAPSHOT'

            task assertPublishArtifacts {
                doLast {
                    def t = tasks.named('publishArtifacts').get()
                    def deps = t.taskDependencies.getDependencies(t)*.path
                    assert deps.contains(':publish'), "expected depends on :publish, got \${deps}"
                    assert !deps.any { it.contains('uploadBb4CentralBundle') }
                    println 'snapshot publishArtifacts OK'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('assertPublishArtifacts', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':assertPublishArtifacts').outcome == TaskOutcome.SUCCESS
        result.output.contains('snapshot publishArtifacts OK')
    }

    def "release publishArtifacts depends on uploadBb4CentralBundle"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'java-library'
                id 'com.barrybecker4.bb4.publish'
            }

            group = 'com.example'
            version = '1.0.0'

            task assertPublishArtifacts {
                doLast {
                    def t = tasks.named('publishArtifacts').get()
                    def deps = t.taskDependencies.getDependencies(t)*.path
                    assert deps.contains(':uploadBb4CentralBundle'), "expected upload dep, got \${deps}"
                    assert tasks.findByName('zipBb4CentralBundle') != null
                    assert tasks.findByName('signMavenJavaPublication') != null
                    println 'release publishArtifacts OK'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('assertPublishArtifacts', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':assertPublishArtifacts').outcome == TaskOutcome.SUCCESS
        result.output.contains('release publishArtifacts OK')
    }

    def "maven publication includes cyclonedx classifier artifact"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'java-library'
                id 'com.barrybecker4.bb4.publish'
            }

            group = 'com.example'
            version = '1.0.0-SNAPSHOT'

            task assertCycloneDx {
                doLast {
                    def pub = publishing.publications.mavenJava
                    def hasBom = pub.artifacts.any { it.classifier == 'cyclonedx' && it.extension == 'json' }
                    assert hasBom : "expected cyclonedx json artifact on mavenJava, got \${pub.artifacts*.classifier}"
                    assert tasks.findByName('cyclonedxDirectBom') != null
                    println 'cyclonedx artifact OK'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('assertCycloneDx', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':assertCycloneDx').outcome == TaskOutcome.SUCCESS
        result.output.contains('cyclonedx artifact OK')
    }
}
