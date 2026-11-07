// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Regression test for issue #14: jarMap configuration timing and version capture.
 * Verifies that:
 * 1. jarMap can be configured after plugins {} block
 * 2. Publication versions reflect the final project.version
 * 3. Malformed jarMap configurations fail with clear errors
 * 4. Multi-artifact tasks and publications are created correctly
 */
class JarMapTimingTest extends Specification {

    @TempDir
    Path testProjectDir

    Path buildFile
    Path settingsFile
    Path sourceDir

    def setup() {
        buildFile = testProjectDir.resolve('build.gradle')
        settingsFile = testProjectDir.resolve('settings.gradle')
        sourceDir = testProjectDir.resolve('src/main/scala/com/example')
        Files.createDirectories(sourceDir)
        
        // Create minimal source files
        Files.writeString(sourceDir.resolve('Common.scala'), '''
            package com.example.common
            object Common { def version = "1.0" }
        ''')
        Files.writeString(sourceDir.resolve('Ui.scala'), '''
            package com.example.ui
            object Ui { def version = "1.0" }
        ''')
        
        settingsFile.toFile().text = '''
            pluginManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-multijar'
        '''
    }

    def "jarMap configured after plugins block creates correct publications with final version"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.barrybecker4'
            version = '1.2.3-SNAPSHOT'
            
            bb4 {
                jarMap.put('common', [include: ['com/example/common/**']])
                jarMap.put('ui', [include: ['com/example/ui/**']])
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks', '--all', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':tasks').outcome == TaskOutcome.SUCCESS
        result.output.contains('commonJar')
        result.output.contains('uiJar')
        result.output.contains('commonSourcesJar')
        result.output.contains('uiSourcesJar')
        result.output.contains('commonJavadocJar')
        result.output.contains('uiJavadocJar')
    }

    def "publications use final project version not early unspecified"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.barrybecker4'
            version = '1.2.3-SNAPSHOT'
            
            bb4 {
                jarMap.put('common', [include: ['com/example/common/**']])
            }
            
            task checkPublicationVersion {
                doLast {
                    def pub = publishing.publications.mavenCommon
                    assert pub.version == '1.2.3-SNAPSHOT', "Expected version 1.2.3-SNAPSHOT but got \${pub.version}"
                    println "Publication version: \${pub.version}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('checkPublicationVersion', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':checkPublicationVersion').outcome == TaskOutcome.SUCCESS
        result.output.contains('Publication version: 1.2.3-SNAPSHOT')
    }

    def "generated POM contains correct coordinates for multi-jar publications"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.barrybecker4'
            version = '1.2.3-SNAPSHOT'
            
            bb4 {
                jarMap.put('common', [include: ['com/example/common/**']])
                jarMap.put('ui', [include: ['com/example/ui/**']])
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('generatePomFileForMavenCommonPublication', 
                          'generatePomFileForMavenUiPublication', 
                          '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':generatePomFileForMavenCommonPublication').outcome == TaskOutcome.SUCCESS
        result.task(':generatePomFileForMavenUiPublication').outcome == TaskOutcome.SUCCESS
        
        def commonPom = testProjectDir.resolve('build/publications/mavenCommon/pom-default.xml').toFile()
        def uiPom = testProjectDir.resolve('build/publications/mavenUi/pom-default.xml').toFile()
        
        commonPom.exists()
        uiPom.exists()
        
        commonPom.text.contains('<artifactId>bb4-common</artifactId>')
        commonPom.text.contains('<version>1.2.3-SNAPSHOT</version>')
        commonPom.text.contains('<groupId>com.barrybecker4</groupId>')
        
        uiPom.text.contains('<artifactId>bb4-ui</artifactId>')
        uiPom.text.contains('<version>1.2.3-SNAPSHOT</version>')
    }

    def "single-jar publication is created when jarMap is empty"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.barrybecker4'
            version = '1.0.0'
            
            task checkPublications {
                doLast {
                    assert publishing.publications.size() == 1
                    assert publishing.publications.mavenJava != null
                    println "Single publication created: mavenJava"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('checkPublications', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':checkPublications').outcome == TaskOutcome.SUCCESS
        result.output.contains('Single publication created: mavenJava')
    }

    def "jarMap entry without include field fails with clear error"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            version = '1.0.0'
            
            bb4 {
                jarMap.put('common', [:])
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks', '--stacktrace')
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.output.contains("jarMap entry 'common' must have 'include' field")
    }

    def "jarMap entry with empty include fails with clear error"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            version = '1.0.0'
            
            bb4 {
                jarMap.put('common', [include: []])
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks', '--stacktrace')
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.output.contains("jarMap entry 'common' has empty 'include'")
    }

    def "conflicting bb4.jarMap and ext.jarMap fails with clear error"() {
        given:
        settingsFile.toFile().text = '''
            pluginManagement {
                repositories {
                    mavenLocal()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-multijar'
        '''
        
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            ext.jarMap = [common: [include: ['com/example/common/**']]]
            version = '1.0.0'
            
            bb4 {
                jarMap.put('ui', [include: ['com/example/ui/**']])
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('tasks', '--stacktrace')
            .withPluginClasspath()
            .buildAndFail()

        then:
        result.output.contains("jarMap configured in both bb4 {} extension and ext.jarMap")
    }

    def "legacy ext.jarMap still works but uses final version"() {
        given:
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            ext.jarMap = [common: [include: ['com/example/common/**']]]
            
            group = 'com.barrybecker4'
            version = '2.0.0-SNAPSHOT'
            
            task checkLegacyVersion {
                doLast {
                    def pub = publishing.publications.mavenCommon
                    assert pub.version == '2.0.0-SNAPSHOT'
                    println "Legacy jarMap publication version: \${pub.version}"
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('checkLegacyVersion', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':checkLegacyVersion').outcome == TaskOutcome.SUCCESS
        result.output.contains('Legacy jarMap publication version: 2.0.0-SNAPSHOT')
    }
}
