// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class Bb4SiblingBuildsTest {

    @TempDir
    Path parent

    @Test
    void 'directories finds bb4 siblings with settings and skips gradle and self'() {
        def consumer = parent.resolve('bb4-app')
        Files.createDirectories(consumer)
        consumer.resolve('settings.gradle').toFile().text = "rootProject.name = 'bb4-app'\n"

        def common = parent.resolve('bb4-common')
        Files.createDirectories(common)
        common.resolve('settings.gradle').toFile().text = "rootProject.name = 'bb4-common'\n"

        def gradleSibling = parent.resolve('bb4-gradle')
        Files.createDirectories(gradleSibling)
        gradleSibling.resolve('settings.gradle.kts').toFile().text = 'rootProject.name = "bb4-gradle"\n'

        def noSettings = parent.resolve('bb4-empty')
        Files.createDirectories(noSettings)

        def found = Bb4SiblingBuilds.directories(consumer.toFile(), true)
        assertEquals(['bb4-common'], found*.name)
    }

    @Test
    void 'directories returns empty when includeSiblings is false'() {
        def consumer = parent.resolve('bb4-app')
        Files.createDirectories(consumer)
        def common = parent.resolve('bb4-common')
        Files.createDirectories(common)
        common.resolve('settings.gradle').toFile().text = "rootProject.name = 'bb4-common'\n"

        assertTrue(Bb4SiblingBuilds.directories(consumer.toFile(), false).isEmpty())
    }

    @Test
    void 'gradle floor treats nine zero as below nine one'() {
        assertTrue(GradleVersion.version('9.0') < GradleVersion.version('9.1'))
        assertTrue(GradleVersion.version('9.1') >= GradleVersion.version('9.1'))
        assertTrue(GradleVersion.version('9.8.2') >= GradleVersion.version('9.1'))
    }
}
