// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Functional tests for plugin composition and application order independence.
 * Tests the fixes for issue #18 - ensures plugins work regardless of application order.
 */
class PluginOrderingTest extends Specification {

    @TempDir
    File testProjectDir
    File buildFile
    File settingsFile

    def setup() {
        buildFile = new File(testProjectDir, 'build.gradle')
        settingsFile = new File(testProjectDir, 'settings.gradle')
        settingsFile << "rootProject.name = 'test-project'\n"
    }

    def "application plugin applied before scala-library still wires bb4.mainClass"() {
        given:
        buildFile << """
            plugins {
                id 'com.barrybecker4.bb4.application'
                id 'com.barrybecker4.bb4.scala-library'
            }
            
            group = 'com.test'
            version = '1.0'
            
            bb4 {
                mainClass.set('com.test.Main')
            }
        """
        createMinimalScalaSource()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('jar', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':jar').outcome == TaskOutcome.SUCCESS
        def jarFile = new File(testProjectDir, 'build/libs/test-project-1.0.jar')
        jarFile.exists()
        
        // Verify the manifest contains the mainClass
        def jarManifest = readJarManifest(jarFile)
        jarManifest.contains('Main-Class: com.test.Main')
    }

    def "scala-library applied before application plugin still wires bb4.mainClass"() {
        given:
        buildFile << """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            
            group = 'com.test'
            version = '1.0'
            
            bb4 {
                mainClass.set('com.test.Main')
            }
        """
        createMinimalScalaSource()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('jar', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':jar').outcome == TaskOutcome.SUCCESS
        def jarFile = new File(testProjectDir, 'build/libs/test-project-1.0.jar')
        jarFile.exists()
        
        def jarManifest = readJarManifest(jarFile)
        jarManifest.contains('Main-Class: com.test.Main')
    }

    def "publish plugin applied before scala-library works correctly"() {
        given:
        buildFile << """
            plugins {
                id 'com.barrybecker4.bb4.publish'
                id 'com.barrybecker4.bb4.scala-library'
            }
            
            group = 'com.test'
            version = '1.0'
        """
        createMinimalScalaSource()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('build', 'sourcesJar', 'javadocJar', 'scaladocJar', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':build').outcome == TaskOutcome.SUCCESS
        result.task(':sourcesJar').outcome == TaskOutcome.SUCCESS
        result.task(':javadocJar').outcome == TaskOutcome.SUCCESS
        result.task(':scaladocJar').outcome == TaskOutcome.SUCCESS
        // Scaladoc task should exist since scala plugin is applied
        def scaladocJarFile = new File(testProjectDir, 'build/libs/test-project-1.0-scaladoc.jar')
        scaladocJarFile.exists()
    }

    def "scala-library applied before publish plugin works correctly"() {
        given:
        buildFile << """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.test'
            version = '1.0'
        """
        createMinimalScalaSource()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('build', 'sourcesJar', 'javadocJar', 'scaladocJar', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':build').outcome == TaskOutcome.SUCCESS
        result.task(':sourcesJar').outcome == TaskOutcome.SUCCESS
        result.task(':javadocJar').outcome == TaskOutcome.SUCCESS
        result.task(':scaladocJar').outcome == TaskOutcome.SUCCESS
        // Scaladoc task should exist since scala plugin is applied
        def scaladocJarFile = new File(testProjectDir, 'build/libs/test-project-1.0-scaladoc.jar')
        scaladocJarFile.exists()
    }

    def "publish plugin works with Java-only project without Scala"() {
        given:
        buildFile << """
            plugins {
                id 'com.barrybecker4.bb4.base'
                id 'java-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.test'
            version = '1.0'
        """
        createMinimalJavaSource()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('sourcesJar', 'javadocJar', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':sourcesJar').outcome == TaskOutcome.SUCCESS
        result.task(':javadocJar').outcome == TaskOutcome.SUCCESS
        // scaladocJar task should not exist for Java-only projects
        !result.tasks.any { it.path == ':scaladocJar' }
    }

    def "base plugin alone provides toolchain and repositories"() {
        given:
        buildFile << """
            plugins {
                id 'com.barrybecker4.bb4.base'
                id 'java-library'
            }
            
            group = 'com.test'
            version = '1.0'
        """
        createMinimalJavaSource()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('build', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':build').outcome == TaskOutcome.SUCCESS
    }

    def "all plugins work together in any order"() {
        given:
        buildFile << """
            plugins {
                id 'com.barrybecker4.bb4.application'
                id 'com.barrybecker4.bb4.publish'
                id 'com.barrybecker4.bb4.scala-library'
            }
            
            group = 'com.test'
            version = '1.0'
            
            bb4 {
                mainClass.set('com.test.Main')
            }
        """
        createMinimalScalaSource()

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments('build', 'sourcesJar', 'javadocJar', 'scaladocJar', '--stacktrace')
            .withPluginClasspath()
            .build()

        then:
        result.task(':build').outcome == TaskOutcome.SUCCESS
        result.task(':sourcesJar').outcome == TaskOutcome.SUCCESS
        result.task(':javadocJar').outcome == TaskOutcome.SUCCESS
        result.task(':scaladocJar').outcome == TaskOutcome.SUCCESS
        // Verify scaladoc jar was created
        def scaladocJarFile = new File(testProjectDir, 'build/libs/test-project-1.0-scaladoc.jar')
        scaladocJarFile.exists()
    }

    private void createMinimalScalaSource() {
        def scalaSourceDir = new File(testProjectDir, 'scala-source/com/test')
        scalaSourceDir.mkdirs()
        new File(scalaSourceDir, 'Main.scala') << '''
            package com.test
            
            object Main {
              def main(args: Array[String]): Unit = {
                println("Hello, World!")
              }
            }
        '''
        
        def scalaTestDir = new File(testProjectDir, 'scala-test/com/test')
        scalaTestDir.mkdirs()
        new File(scalaTestDir, 'MainTest.scala') << '''
            package com.test
            
            import org.scalatest.funsuite.AnyFunSuite
            
            class MainTest extends AnyFunSuite {
              test("dummy test") {
                assert(true)
              }
            }
        '''
    }

    private void createMinimalJavaSource() {
        def javaSourceDir = new File(testProjectDir, 'source/com/test')
        javaSourceDir.mkdirs()
        new File(javaSourceDir, 'Main.java') << '''
            package com.test;
            
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
        '''
    }

    private String readJarManifest(File jarFile) {
        def process = "jar -xf ${jarFile.absolutePath} META-INF/MANIFEST.MF".execute(null, testProjectDir)
        process.waitFor()
        def manifestFile = new File(testProjectDir, 'META-INF/MANIFEST.MF')
        return manifestFile.exists() ? manifestFile.text : ''
    }
}
