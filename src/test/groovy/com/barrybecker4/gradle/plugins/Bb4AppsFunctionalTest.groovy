// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

import static org.junit.jupiter.api.Assertions.*

class Bb4AppsFunctionalTest {

    @TempDir
    Path testProjectDir

    Path settingsFile
    Path buildFile

    @BeforeEach
    void setup() {
        settingsFile = testProjectDir.resolve('settings.gradle')
        buildFile = testProjectDir.resolve('build.gradle')
        settingsFile.toFile().text = """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'test-apps'
        """
    }

    @Test
    void 'apps container registers run and createStartScripts tasks'() {
        writeMain('com.test.Foo')
        writeMain('com.test.Bar', 'Bar')
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            group = 'com.test'
            version = '1.0'
            bb4 {
                mainClass.set('com.test.Foo')
                apps.put('foo', [
                    title: 'Foo',
                    mainClass: 'com.test.Foo',
                    command: 'bb4-foo',
                    description: 'Foo app',
                    longDescription: 'Foo long',
                    mx: '512m'
                ])
                apps.put('bar', [
                    title: 'Bar',
                    mainClass: 'com.test.Bar',
                    command: 'bb4-bar',
                    description: 'Bar app',
                    longDescription: 'Bar long'
                ])
            }
        """

        def tasks = runGradle('tasks', '--group=application')
        assertTrue(tasks.output.contains('runFoo'))
        assertTrue(tasks.output.contains('runBar'))

        def result = runGradle('createFooApp', 'createBarApp', 'startScripts')
        assertEquals(TaskOutcome.SUCCESS, result.task(':createFooApp').outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(':createBarApp').outcome)

        def scriptsDir = testProjectDir.resolve('build/scripts').toFile()
        assertTrue(new File(scriptsDir, 'bb4-foo').exists() || new File(scriptsDir, 'bb4-foo.bat').exists())
        assertTrue(new File(scriptsDir, 'bb4-bar').exists() || new File(scriptsDir, 'bb4-bar.bat').exists())
    }

    @Test
    void 'deploySchemas ignores xsd outside schemaDirectory'() {
        writeMain('com.test.Main')
        Files.createDirectories(testProjectDir.resolve('source/schema'))
        testProjectDir.resolve('source/schema/ok.xsd').toFile().text = '<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>'
        testProjectDir.resolve('decoy.xsd').toFile().text = '<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>'

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

        def result = runGradle('deploySchemas')
        assertEquals(TaskOutcome.SUCCESS, result.task(':deploySchemas').outcome)
        assertTrue(Files.exists(testProjectDir.resolve('dist/schema/ok.xsd')))
        assertFalse(Files.exists(testProjectDir.resolve('dist/schema/decoy.xsd')))
    }

    @Test
    void 'deployHtml reads apps container'() {
        writeMain('com.test.Main')
        def htmlDir = testProjectDir.resolve('source/html')
        Files.createDirectories(htmlDir)
        htmlDir.resolve('index.html').toFile().text = '''
            <html><body>
            @table_rows@
            </body></html>
        '''

        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            group = 'com.test'
            version = '1.0'
            bb4 {
                mainClass.set('com.test.Main')
                apps.put('demo', [
                    title: 'Demo',
                    mainClass: 'com.test.Main',
                    command: 'bb4-demo',
                    description: 'd',
                    longDescription: 'Long demo',
                    imageName: 'demo.jpg'
                ])
            }
        """

        def result = runGradle('deployHtml')
        assertEquals(TaskOutcome.SUCCESS, result.task(':deployHtml').outcome)
        def html = testProjectDir.resolve('dist/test-apps/index.html').toFile().text
        assertTrue(html.contains('Demo'))
        assertTrue(html.contains('bb4-demo'))
        assertTrue(html.contains('Long demo'))
    }

    @Test
    void 'copyToLib task is not registered'() {
        writeMain('com.test.Main')
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
            }
            group = 'com.test'
            version = '1.0'
            bb4 {
                archivesBaseName.set('lib')
            }
        """

        def result = runGradle('tasks')
        assertFalse(result.output.contains('copyToLib'))
    }

    @Test
    void 'distZip includes per-app start scripts from apps'() {
        writeMain('com.test.Foo')
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            group = 'com.test'
            version = '1.0'
            bb4 {
                mainClass.set('com.test.Foo')
                apps.put('foo', [
                    title: 'Foo',
                    mainClass: 'com.test.Foo',
                    command: 'bb4-foo',
                    description: 'Foo app',
                    longDescription: 'Foo long'
                ])
            }
        """

        def result = runGradle('distZip')
        assertEquals(TaskOutcome.SUCCESS, result.task(':distZip').outcome)
        def zip = testProjectDir.resolve('build/distributions/test-apps-1.0.zip').toFile()
        assertTrue(zip.isFile())
        def names = new ZipFile(zip).entries().collect { it.name }
        assertTrue(names.any { it.contains('bin/bb4-foo') }, "expected bb4-foo in $names")
    }

    private void writeMain(String fqcn, String simpleName = null) {
        def parts = fqcn.split('\\.')
        def className = simpleName ?: parts[-1]
        def pkgPath = parts.length > 1 ? parts[0..-2].join('/') : ''
        def dir = pkgPath ? testProjectDir.resolve("source/$pkgPath") : testProjectDir.resolve('source')
        Files.createDirectories(dir)
        def pkgDecl = pkgPath ? "package ${parts[0..-2].join('.')};\n" : ''
        dir.resolve("${className}.java").toFile().text = """
            ${pkgDecl}public class ${className} {
                public static void main(String[] args) {
                    System.out.println("${className}");
                }
            }
        """
    }

    private def runGradle(String... args) {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments((args as List) + ['--stacktrace'])
            .withPluginClasspath()
            .withDebug(true)
            .build()
    }
}
