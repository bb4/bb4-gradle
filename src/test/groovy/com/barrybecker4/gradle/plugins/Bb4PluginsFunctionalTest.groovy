package com.barrybecker4.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

class Bb4PluginsFunctionalTest {

    @TempDir
    Path testProjectDir

    Path settingsFile
    Path buildFile
    Path localRepo

    @BeforeEach
    void setup() {
        settingsFile = testProjectDir.resolve('settings.gradle')
        buildFile = testProjectDir.resolve('build.gradle')
        def mavenLocalRepo = System.getProperty('user.home') + '/.m2/repository'
        localRepo = new File(mavenLocalRepo).toPath()
    }

    @Test
    void 'base plugin applies and sets Java 25 toolchain'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-base'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.base'
            }
            
            repositories {
                mavenCentral()
            }
            
            task printToolchain {
                doLast {
                    def javaExt = project.extensions.getByType(JavaPluginExtension)
                    println "Toolchain version: " + javaExt.toolchain.languageVersion.get()
                }
            }
        """

        def result = runGradle('printToolchain')

        assertTrue(result.output.contains('Toolchain version: 25'))
        assertEquals(TaskOutcome.SUCCESS, result.task(':printToolchain').outcome)
    }

    @Test
    void 'base alone creates bb4 extension for java-library publish'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'plain-java-lib'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.base'
                id 'java-library'
                id 'com.barrybecker4.bb4.publish'
            }

            group = 'com.test'
            version = '1.0-SNAPSHOT'

            bb4 {
                archivesBaseName = 'plain-java'
                repository = 'bb4-example'
            }

            publishing {
                repositories {
                    mavenLocal()
                }
            }
        """

        def sourceDir = testProjectDir.resolve('src/main/java/com/test')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Sample.java').toFile().text = '''
            package com.test;
            public class Sample {}
        '''

        def result = runGradle('generatePomFileForMavenJavaPublication', 'jar')

        assertEquals(TaskOutcome.SUCCESS, result.task(':generatePomFileForMavenJavaPublication').outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(':jar').outcome)

        def pomFile = testProjectDir.resolve('build/publications/mavenJava/pom-default.xml').toFile()
        assertTrue(pomFile.exists(), 'POM file should be generated')
        def pomContent = pomFile.text
        assertTrue(pomContent.contains('<url>https://github.com/bb4/bb4-example</url>'),
            'POM should use bb4.repository from the extension')
        assertTrue(pomContent.contains('<connection>scm:git:https://github.com/bb4/bb4-example.git</connection>'))
    }

    @Test
    void 'scala-library plugin configures source sets and test engines'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-scala-lib'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
            }
            
            group = 'com.test'
            version = '1.0-SNAPSHOT'
        """

        def sourceDir = testProjectDir.resolve('source')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Example.java').toFile().text = '''
            public class Example {
                public String getMessage() {
                    return "Hello";
                }
            }
        '''

        def scalaSourceDir = testProjectDir.resolve('scala-source')
        Files.createDirectories(scalaSourceDir)
        scalaSourceDir.resolve('ScalaExample.scala').toFile().text = '''
            class ScalaExample {
              def getMessage: String = "Hello from Scala"
            }
        '''

        def testDir = testProjectDir.resolve('test')
        Files.createDirectories(testDir)
        testDir.resolve('ExampleTest.java').toFile().text = '''
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.*;
            
            class ExampleTest {
                @Test
                void testMessage() {
                    assertEquals("Hello", new Example().getMessage());
                }
            }
        '''

        def scalaTestDir = testProjectDir.resolve('scala-test')
        Files.createDirectories(scalaTestDir)
        scalaTestDir.resolve('ScalaExampleTest.scala').toFile().text = '''
            import org.scalatest.funsuite.AnyFunSuite
            
            class ScalaExampleTest extends AnyFunSuite {
              test("getMessage returns correct value") {
                assert(new ScalaExample().getMessage == "Hello from Scala")
              }
            }
        '''

        def result = runGradle('test')

        assertEquals(TaskOutcome.SUCCESS, result.task(':test').outcome)
        def testReportDir = testProjectDir.resolve('build/test-results/test')
        assertTrue(Files.exists(testReportDir), "Test results directory should exist")
    }

    @Test
    void 'plugins can be applied in different orders'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-plugin-order'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.test'
            version = '1.0-SNAPSHOT'
        """

        def result = runGradle('tasks', '--group=publishing')

        assertEquals(TaskOutcome.SUCCESS, result.task(':tasks').outcome)
        assertTrue(result.output.contains('publish') || result.output.contains('Publishing tasks'))
    }

    @Test
    void 'publish plugin creates sources and javadoc jars'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-publish'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.test'
            version = '1.0-SNAPSHOT'
            
            publishing {
                repositories {
                    mavenLocal()
                }
            }
        """

        def sourceDir = testProjectDir.resolve('source')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Sample.java').toFile().text = '''
            public class Sample {
                public void hello() {}
            }
        '''

        def result = runGradle('publishToMavenLocal')

        assertEquals(TaskOutcome.SUCCESS, result.task(':sourcesJar').outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(':javadocJar').outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(':scaladocJar').outcome)
    }

    @Test
    void 'publish plugin handles SNAPSHOT versions without signing'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-snapshot'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.test'
            version = '1.0-SNAPSHOT'
            
            publishing {
                repositories {
                    mavenLocal()
                }
            }
        """

        def sourceDir = testProjectDir.resolve('source')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Sample.java').toFile().text = '''
            public class Sample {}
        '''

        def result = runGradle('publishToMavenLocal')

        assertEquals(TaskOutcome.SUCCESS, result.task(':publishToMavenLocal').outcome)
        assertFalse(result.output.contains('sign'))
    }

    @Test
    void 'publish plugin generates POM with correct GitHub organization URLs'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-pom-urls'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.test'
            version = '1.0-SNAPSHOT'
            
            publishing {
                repositories {
                    mavenLocal()
                }
            }
        """

        def sourceDir = testProjectDir.resolve('source')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Sample.java').toFile().text = '''
            public class Sample {}
        '''

        def result = runGradle('generatePomFileForMavenJavaPublication')

        assertEquals(TaskOutcome.SUCCESS, result.task(':generatePomFileForMavenJavaPublication').outcome)
        
        def pomFile = testProjectDir.resolve('build/publications/mavenJava/pom-default.xml').toFile()
        assertTrue(pomFile.exists(), "POM file should be generated")
        
        def pomContent = pomFile.text
        assertTrue(pomContent.contains('<url>https://github.com/bb4/test-pom-urls</url>'), 
            "POM should contain correct project URL")
        assertTrue(pomContent.contains('<url>https://github.com/bb4/test-pom-urls</url>') &&
            pomContent.contains('<connection>scm:git:https://github.com/bb4/test-pom-urls.git</connection>') &&
            pomContent.contains('<developerConnection>scm:git:ssh://git@github.com/bb4/test-pom-urls.git</developerConnection>'),
            "POM should contain correct SCM URLs")
        assertFalse(pomContent.contains('barrybecker4/test-pom-urls'), 
            "POM should not contain old barrybecker4 organization references")
    }

    @Test
    void 'publish plugin multi-artifact mode creates separate publications'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-multi-jar'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.publish'
            }
            
            group = 'com.test'
            version = '1.0-SNAPSHOT'
            
            bb4 {
                jarMap.put('common', [include: ['com/test/common/**']])
                jarMap.put('utils', [include: ['com/test/utils/**']])
            }
            
            publishing {
                repositories {
                    mavenLocal()
                }
            }
        """

        def sourceDir = testProjectDir.resolve('source/com/test')
        Files.createDirectories(sourceDir.resolve('common'))
        Files.createDirectories(sourceDir.resolve('utils'))
        sourceDir.resolve('common/Common.java').toFile().text = '''
            package com.test.common;
            public class Common {}
        '''
        sourceDir.resolve('utils/Utils.java').toFile().text = '''
            package com.test.utils;
            public class Utils {}
        '''

        def result = runGradle('commonJar', 'utilsJar')

        assertTrue(result.task(':commonJar').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE])
        assertTrue(result.task(':utilsJar').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE])
        assertTrue(Files.exists(testProjectDir.resolve('build/libs/bb4-common-1.0-SNAPSHOT.jar')))
        assertTrue(Files.exists(testProjectDir.resolve('build/libs/bb4-utils-1.0-SNAPSHOT.jar')))
    }

    @Test
    void 'application plugin creates distributions with start scripts'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-app'
        """
        buildFile.toFile().text = """
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

        def sourceDir = testProjectDir.resolve('source/com/test')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Main.java').toFile().text = '''
            package com.test;
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
        '''

        def result = runGradle('distZip')

        assertEquals(TaskOutcome.SUCCESS, result.task(':distZip').outcome)
        assertTrue(Files.exists(testProjectDir.resolve('build/distributions/test-app-1.0.zip')))
    }

    @Test
    void 'application plugin handles paths with spaces'() {
        def projectWithSpaces = testProjectDir.resolve('project with spaces')
        Files.createDirectories(projectWithSpaces)
        
        def localSettings = projectWithSpaces.resolve('settings.gradle')
        def localBuild = projectWithSpaces.resolve('build.gradle')
        
        localSettings.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-app-spaces'
        """
        localBuild.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            
            group = 'com.test'
            version = '1.0'
            
            bb4 {
                mainClass.set('Main')
            }
        """

        def sourceDir = projectWithSpaces.resolve('source')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Main.java').toFile().text = '''
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Works");
                }
            }
        '''

        def result = GradleRunner.create()
            .withProjectDir(projectWithSpaces.toFile())
            .withArguments('startScripts', '--stacktrace')
            .withPluginClasspath()
            .withDebug(true)
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(':startScripts').outcome)
    }

    @Test
    void 'application plugin works with custom build directory'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-custom-build'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            
            group = 'com.test'
            version = '1.0'
            
            layout.buildDirectory = file('custom-build')
            
            bb4 {
                mainClass.set('Main')
            }
        """

        def sourceDir = testProjectDir.resolve('source')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Main.java').toFile().text = '''
            public class Main {
                public static void main(String[] args) {}
            }
        '''

        def result = runGradle('assemble')

        assertEquals(TaskOutcome.SUCCESS, result.task(':assemble').outcome)
        assertTrue(Files.exists(testProjectDir.resolve('custom-build')))
    }

    @Test
    void 'extension values can be customized'() {
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-custom-extension'
        """
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
            }
            
            group = 'com.test'
            version = '1.0'
            
            bb4 {
                archivesBaseName.set('custom-name')
                scalaVersion.set('3.10.0')
            }
        """

        def sourceDir = testProjectDir.resolve('source')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Sample.java').toFile().text = '''
            public class Sample {}
        '''

        def result = runGradle('jar')

        assertEquals(TaskOutcome.SUCCESS, result.task(':jar').outcome)
        assertTrue(Files.exists(testProjectDir.resolve('build/libs/custom-name-1.0.jar')))
    }

    private def runGradle(String... args) {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(args + ['--stacktrace'])
            .withPluginClasspath()
            .withDebug(true)
            .build()
    }
}
