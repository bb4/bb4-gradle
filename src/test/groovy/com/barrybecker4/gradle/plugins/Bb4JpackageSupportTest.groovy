// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class Bb4JpackageSupportTest {

    @Test
    void 'sanitizeText strips tags quotes and collapses whitespace'() {
        assertEquals('Hello world', Bb4JpackageSupport.sanitizeText('  Hello <b>"world"</b>  '))
        assertEquals('', Bb4JpackageSupport.sanitizeText(null))
    }

    @Test
    void 'sanitizeAppVersion strips SNAPSHOT and rejects non semver'() {
        assertEquals('3.0', Bb4JpackageSupport.sanitizeAppVersion('3.0-SNAPSHOT'))
        assertEquals('1.2.3', Bb4JpackageSupport.sanitizeAppVersion('1.2.3'))
        assertEquals('0.0.1', Bb4JpackageSupport.sanitizeAppVersion('not-a-version'))
        assertEquals('0.0.1', Bb4JpackageSupport.sanitizeAppVersion(null))
    }

    @Test
    void 'macPackageName truncates title and honors explicit name'() {
        assertEquals('Short Title', Bb4JpackageSupport.macPackageNameFor(title: 'Short Title'))
        assertEquals('A Very Long Titl', Bb4JpackageSupport.macPackageNameFor(
            title: 'A Very Long Title Indeed'))
        assertEquals('Ludlow Text', Bb4JpackageSupport.macPackageNameFor(
            title: 'Ludlow Text Adventure', macPackageName: 'Ludlow Text'))
        def ex = assertThrows(IllegalArgumentException) {
            Bb4JpackageSupport.macPackageNameFor(macPackageName: 'ThisNameIsWayTooLong')
        }
        assertTrue(ex.message.contains('16 characters'))
    }

    @Test
    void 'deployable and console helpers'() {
        assertTrue(Bb4JpackageSupport.isDeployable([:]))
        assertFalse(Bb4JpackageSupport.isDeployable(deploy: false))
        assertTrue(Bb4JpackageSupport.isConsoleApp(console: true))
        assertFalse(Bb4JpackageSupport.isConsoleApp([:]))
    }

    @Test
    void 'installer type and icon name follow os name'() {
        assertEquals('dmg', Bb4JpackageSupport.installerTypeForOs('Mac OS X'))
        assertEquals('msi', Bb4JpackageSupport.installerTypeForOs('Windows 11'))
        assertEquals('deb', Bb4JpackageSupport.installerTypeForOs('Linux'))
        assertEquals('icon.icns', Bb4JpackageSupport.iconFileNameForOs('Mac OS X'))
        assertEquals('icon.ico', Bb4JpackageSupport.iconFileNameForOs('Windows 10'))
        assertEquals('icon.png', Bb4JpackageSupport.iconFileNameForOs('Linux'))
        assertEquals('jpackage.exe', Bb4JpackageSupport.defaultJpackageBinaryName('Windows 10'))
        assertEquals('jpackage', Bb4JpackageSupport.defaultJpackageBinaryName('Linux'))
    }

    @Test
    void 'buildCli includes mac flags'() {
        def cli = Bb4JpackageSupport.buildCli(
            appId: 'hiq',
            propMap: [title: 'Hi-Q', description: 'desc'],
            installLib: '/tmp/lib',
            destDir: '/tmp/out',
            displayName: 'Hi-Q',
            appVersion: '3.0',
            description: 'desc',
            licenseFile: '/tmp/LICENSE',
            icon: '/tmp/icon.icns',
            mainJarName: 'bb4-hiq-3.0.jar',
            mainClassName: 'com.example.HiQ',
            installerType: 'dmg',
            packageIdentifierPrefix: 'com.barrybecker4.puzzle',
            osName: 'Mac OS X',
            javaOptions: ['-ea', '-Xmx1g'],
            arguments: [],
            resourceDir: null,
        )
        assertTrue(cli.contains('--mac-package-identifier'))
        assertTrue(cli.contains('com.barrybecker4.puzzle.hiq'))
        assertTrue(cli.contains('--mac-package-name'))
        assertTrue(cli.contains('--java-options'))
        assertTrue(cli.contains('-Xmx1g'))
        assertFalse(cli.contains('--win-menu'))
        assertFalse(cli.contains('--linux-shortcut'))
    }

    @Test
    void 'buildCli includes windows console flag'() {
        def cli = Bb4JpackageSupport.buildCli(
            appId: 'textAdventure',
            propMap: [title: 'Text', console: true],
            installLib: 'C:\\lib',
            destDir: 'C:\\out',
            displayName: 'Text',
            appVersion: '3.0',
            description: 'desc',
            licenseFile: 'C:\\LICENSE',
            icon: 'C:\\icon.ico',
            mainJarName: 'bb4-adventure-3.0.jar',
            mainClassName: 'com.example.Text',
            installerType: 'msi',
            packageIdentifierPrefix: 'com.barrybecker4.puzzle.adventure',
            osName: 'Windows 11',
            javaOptions: ['-ea'],
            arguments: ['script.xml'],
            resourceDir: null,
        )
        assertTrue(cli.contains('--win-menu'))
        assertTrue(cli.contains('--win-console'))
        assertTrue(cli.contains('--arguments'))
        assertTrue(cli.contains('script.xml'))
        assertFalse(cli.contains('--mac-package-identifier'))
    }

    @Test
    void 'buildCli includes linux resource dir for console apps'() {
        def cli = Bb4JpackageSupport.buildCli(
            appId: 'textAdventure',
            propMap: [title: 'Text', console: true],
            installLib: '/tmp/lib',
            destDir: '/tmp/out',
            displayName: 'Text',
            appVersion: '3.0',
            description: 'desc',
            licenseFile: '/tmp/LICENSE',
            icon: '/tmp/icon.png',
            mainJarName: 'bb4-adventure-3.0.jar',
            mainClassName: 'com.example.Text',
            installerType: 'deb',
            packageIdentifierPrefix: 'com.barrybecker4.puzzle.adventure',
            osName: 'Linux',
            javaOptions: ['-ea'],
            arguments: [],
            resourceDir: '/tmp/jpackage-resources/textAdventure',
        )
        assertTrue(cli.contains('--linux-shortcut'))
        assertTrue(cli.contains('--linux-package-name'))
        assertTrue(cli.contains('bb4-textadventure'))
        assertTrue(cli.contains('--resource-dir'))
        assertTrue(cli.contains('/tmp/jpackage-resources/textAdventure'))
    }

    @Test
    void 'artifactBaseName lowercases app id'() {
        assertEquals('bb4-hiq-3.0', Bb4JpackageSupport.artifactBaseName('hiq', '3.0'))
        assertEquals('bb4-textadventure-1.0', Bb4JpackageSupport.artifactBaseName('textAdventure', '1.0'))
    }
}
