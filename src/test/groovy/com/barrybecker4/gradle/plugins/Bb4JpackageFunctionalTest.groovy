// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

import static org.junit.jupiter.api.Assertions.*

class Bb4JpackageFunctionalTest {

    @TempDir
    Path testProjectDir

    Path settingsFile
    Path buildFile
    Path stubJpackage
    String installerType

    @BeforeEach
    void setup() {
        settingsFile = testProjectDir.resolve('settings.gradle')
        buildFile = testProjectDir.resolve('build.gradle')
        installerType = Bb4JpackageSupport.installerTypeForOs(System.getProperty('os.name'))
        stubJpackage = createStubJpackage()
        writeCommonProjectFiles()
    }

    @Test
    void 'jpackage tasks are absent when disabled'() {
        settingsFile.toFile().text = settingsGradle()
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            group = 'com.test'
            version = '1.0-SNAPSHOT'
            bb4 {
                mainClass.set('com.test.Main')
                apps.put('demo', [
                    title: 'Demo', mainClass: 'com.test.Main',
                    command: 'demo', description: 'd', longDescription: 'd'
                ])
            }
        """

        def result = runGradle('tasks', '--group=distribution')
        assertFalse(result.output.contains('jpackageAllApps'))
        assertFalse(result.output.contains('jpackageDemo'))
    }

    @Test
    void 'jpackage builds installer with stub and skips deploy false'() {
        settingsFile.toFile().text = settingsGradle()
        writeIcons('demo')
        buildFile.toFile().text = enabledBuild("""
            packageIdentifierPrefix = 'com.test.demo'
            jpackageExecutable = file('${stubJpackage.toAbsolutePath().toString().replace('\\', '/')}')
            defaultMainClass = 'com.test.Main'
            javaOptions = { appId, props -> ['-ea', '-Dfile.encoding=UTF-8', '-Xmx512m'] }
            arguments = { appId, props -> props.containsKey('flag') ? [props.flag as String] : [] }
        """)

        def result = runGradle('jpackageAllApps', '--stacktrace')
        assertEquals(TaskOutcome.SUCCESS, result.task(':jpackageDemo').outcome)
        assertNull(result.task(':jpackageHidden'))

        def outDir = testProjectDir.resolve('build/jpackage/demo').toFile()
        def renamed = new File(outDir, "bb4-demo-1.0.${installerType}")
        assertTrue(renamed.isFile(), "expected renamed installer ${renamed}")

        def argsFile = new File(outDir, 'jpackage-args.txt')
        assertTrue(argsFile.isFile())
        def argsText = argsFile.text
        assertTrue(argsText.contains('--app-version'))
        assertTrue(argsText.contains('1.0'))
        assertTrue(argsText.contains('--main-jar'))
        assertTrue(argsText.contains('bb4-demo-'))
        assertTrue(argsText.contains('-Xmx512m'))
        assertTrue(argsText.contains('--arguments'))
        assertTrue(argsText.contains('go'))
        if (Bb4JpackageSupport.isMac(System.getProperty('os.name'))) {
            assertTrue(argsText.contains('--mac-package-identifier'))
            assertTrue(argsText.contains('com.test.demo.demo'))
        } else if (Bb4JpackageSupport.isWindows(System.getProperty('os.name'))) {
            assertTrue(argsText.contains('--win-menu'))
        } else {
            assertTrue(argsText.contains('--linux-shortcut'))
        }
    }

    @Test
    void 'missing icon fails jpackage task'() {
        settingsFile.toFile().text = settingsGradle()
        // icons directory absent on purpose
        buildFile.toFile().text = enabledBuild("""
            packageIdentifierPrefix = 'com.test.demo'
            jpackageExecutable = file('${stubJpackage.toAbsolutePath().toString().replace('\\', '/')}')
            defaultMainClass = 'com.test.Main'
        """)

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments('jpackageDemo', '--stacktrace')
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()

        assertTrue(result.output.contains('Missing jpackage icon'))
    }

    @Test
    void 'jpackage works without jarMap or publish plugin'() {
        settingsFile.toFile().text = settingsGradle()
        writeIcons('demo')
        buildFile.toFile().text = """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            group = 'com.test'
            version = '1.0-SNAPSHOT'
            bb4 {
                archivesBaseName.set('bb4-demo')
                mainClass.set('com.test.Main')
                apps.put('demo', [
                    title: 'Demo App', mainClass: 'com.test.Main',
                    command: 'demo', description: 'A demo', longDescription: 'd'
                ])
                jpackage {
                    enabled = true
                    packageIdentifierPrefix = 'com.test.demo'
                    jpackageExecutable = file('${stubJpackage.toAbsolutePath().toString().replace('\\', '/')}')
                    defaultMainClass = 'com.test.Main'
                }
            }
        """

        def result = runGradle('jpackageDemo', '--stacktrace')
        assertEquals(TaskOutcome.SUCCESS, result.task(':jpackageDemo').outcome)

        def outDir = testProjectDir.resolve('build/jpackage/demo').toFile()
        def renamed = new File(outDir, "bb4-demo-1.0.${installerType}")
        assertTrue(renamed.isFile(), "expected renamed installer ${renamed}")

        def argsText = new File(outDir, 'jpackage-args.txt').text
        assertTrue(argsText.contains('--main-jar'))
        assertTrue(
            argsText.contains('bb4-demo') || argsText.contains('jpackage-test'),
            "expected project jar name in --main-jar, got:\n${argsText}"
        )
        assertFalse(argsText.contains('demoJar'), "must not require jarMap demoJar task")
    }

    private static String enabledBuild(String jpackageBody) {
        """
            plugins {
                id 'com.barrybecker4.bb4.scala-library'
                id 'com.barrybecker4.bb4.application'
            }
            group = 'com.test'
            version = '1.0-SNAPSHOT'
            bb4 {
                archivesBaseName.set('bb4-demo')
                mainClass.set('com.test.Main')
                apps.put('demo', [
                    title: 'Demo App', mainClass: 'com.test.Main',
                    command: 'demo', description: 'A "demo"', longDescription: 'd',
                    flag: 'go'
                ])
                apps.put('hidden', [
                    title: 'Hidden', mainClass: 'com.test.Main',
                    command: 'hidden', description: 'h', longDescription: 'h',
                    deploy: false
                ])
                jpackage {
                    enabled = true
                    ${jpackageBody}
                }
            }
        """
    }

    private void writeCommonProjectFiles() {
        def sourceDir = testProjectDir.resolve('source/com/test')
        Files.createDirectories(sourceDir)
        sourceDir.resolve('Main.java').toFile().text = '''
            package com.test;
            public class Main {
                public static void main(String[] args) {}
            }
        '''
        testProjectDir.resolve('LICENSE').toFile().text = 'MIT\n'
    }

    private void writeIcons(String appId) {
        def iconDir = testProjectDir.resolve("packaging/icons/${appId}")
        Files.createDirectories(iconDir)
        iconDir.resolve('icon.png').toFile().text = 'png'
        iconDir.resolve('icon.ico').toFile().text = 'ico'
        iconDir.resolve('icon.icns').toFile().text = 'icns'
    }

    private Path createStubJpackage() {
        def stub = testProjectDir.resolve('stub-jpackage')
        stub.toFile().text = '''#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--version" ]]; then
  echo "21.0.0"
  exit 0
fi
dest=""
name="App"
ptype="dmg"
prev=""
for a in "$@"; do
  if [[ "$prev" == "--dest" ]]; then dest="$a"; fi
  if [[ "$prev" == "--name" ]]; then name="$a"; fi
  if [[ "$prev" == "--type" ]]; then ptype="$a"; fi
  prev="$a"
done
mkdir -p "$dest"
printf '%s\\n' "$@" > "$dest/jpackage-args.txt"
# Use a filesystem-safe placeholder for the initial installer name
safe_name=$(printf '%s' "$name" | tr ' /' '__')
touch "$dest/${safe_name}.${ptype}"
'''
        try {
            def perms = Files.getPosixFilePermissions(stub)
            perms.add(PosixFilePermission.OWNER_EXECUTE)
            perms.add(PosixFilePermission.GROUP_EXECUTE)
            perms.add(PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(stub, perms)
        } catch (UnsupportedOperationException ignored) {
            // Windows: no POSIX perms
        }
        return stub
    }

    private static String settingsGradle() {
        '''
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                }
            }
            rootProject.name = 'jpackage-test'
        '''
    }

    private def runGradle(String... args) {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(args)
            .withPluginClasspath()
            .withDebug(true)
            .build()
    }
}
