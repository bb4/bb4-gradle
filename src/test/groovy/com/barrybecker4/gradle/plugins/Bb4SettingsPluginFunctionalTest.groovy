// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Resolves the settings plugin from mavenLocal. The {@code test} task depends on
 * {@code publishToMavenLocal} so markers and the plugin jar are present. TestKit cannot
 * {@code includeBuild} the project under test while that project is itself building.
 */
class Bb4SettingsPluginFunctionalTest {

    @TempDir
    Path parentDir

    Path appDir
    Path commonDir

    @BeforeEach
    void setup() {
        appDir = parentDir.resolve('bb4-app')
        commonDir = parentDir.resolve('bb4-common')
        Files.createDirectories(appDir)
        Files.createDirectories(commonDir)

        // Included sibling also applies the settings plugin; parent != null must prevent cycles.
        commonDir.resolve('settings.gradle').toFile().text = settingsScript('bb4-common')
        commonDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java-library'
            }
            group = 'com.barrybecker4'
            version = '3.0-SNAPSHOT'
            java {
                toolchain { languageVersion = JavaLanguageVersion.of(25) }
            }
        """.stripIndent()

        def commonSrc = commonDir.resolve('src/main/java/com/example')
        Files.createDirectories(commonSrc)
        commonSrc.resolve('CommonType.java').toFile().text = '''
            package com.example;
            public class CommonType {
                public String message() { return "from-sibling"; }
            }
        '''.stripIndent()
    }

    private static String settingsScript(String rootName) {
        return """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                    maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
                }
                plugins {
                    id 'com.barrybecker4.bb4.settings' version '3.0-SNAPSHOT'
                }
            }
            plugins {
                id 'com.barrybecker4.bb4.settings'
            }
            rootProject.name = '${rootName}'
        """.stripIndent()
    }

    private void writeAppSettings() {
        appDir.resolve('settings.gradle').toFile().text = settingsScript('bb4-app')
    }

    private void writeAppBuild() {
        appDir.resolve('build.gradle').toFile().text = """
            plugins {
                id 'java'
            }
            group = 'com.example'
            version = '1.0'
            dependencies {
                implementation bb4Libs.common
            }
            java {
                toolchain { languageVersion = JavaLanguageVersion.of(25) }
            }
        """.stripIndent()

        def appSrc = appDir.resolve('src/main/java/com/example')
        Files.createDirectories(appSrc)
        appSrc.resolve('App.java').toFile().text = '''
            package com.example;
            public class App {
                public String run() { return new CommonType().message(); }
            }
        '''.stripIndent()
    }

    private GradleRunner runner(Path projectDir, String... args) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments((args as List) + ['--stacktrace'])
            .forwardOutput()
    }

    @Test
    void 'settings plugin substitutes sibling bb4-common for compile'() {
        writeAppSettings()
        writeAppBuild()

        def result = runner(appDir, 'compileJava').build()

        assertEquals(TaskOutcome.SUCCESS, result.task(':compileJava').outcome)
    }

    @Test
    void 'includeSiblings false does not use sibling checkout'() {
        writeAppSettings()
        writeAppBuild()

        def result = runner(appDir, 'compileJava', '-Pbb4.includeSiblings=false').buildAndFail()

        // Sibling is not used: either the module fails to resolve, or a published
        // bb4-common from mavenLocal is used and lacks CommonType from the sibling.
        assertTrue(
            result.output.contains('Could not resolve') ||
                result.output.contains('Could not find') ||
                result.output.contains('cannot find symbol') ||
                result.output.contains('Compilation failed'),
            "Expected failure proving sibling was unused, got:\\n${result.output}",
        )
    }

    @Test
    void 'included sibling that applies settings plugin does not cycle'() {
        writeAppSettings()
        writeAppBuild()

        def result = runner(appDir, 'compileJava').build()

        assertEquals(TaskOutcome.SUCCESS, result.task(':compileJava').outcome)
        assertTrue(
            !result.output.toLowerCase().contains('circular') &&
                !result.output.toLowerCase().contains('already included'),
            "Unexpected includeBuild conflict:\\n${result.output}",
        )
    }
}
