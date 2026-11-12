// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * Opt-in native installer configuration under {@code bb4.jpackage { }}.
 * Tasks are registered only when {@link #getEnabled()} is true.
 */
class Bb4JpackageExtension {

    final Property<Boolean> enabled

    /**
     * Prefix for macOS package identifiers: {@code <prefix>.<appId>}.
     * Required when enabled.
     */
    final Property<String> packageIdentifierPrefix

    /**
     * Closure {@code (String appId, Map props) -> String} selecting the jarMap key
     * whose jar is passed as {@code --main-jar}. Defaults to the app id.
     */
    Object mainJarKey = { String appId, Map props -> appId }

    /**
     * Used when an appMap entry has no {@code mainClass}.
     */
    final Property<String> defaultMainClass

    /**
     * Closure {@code (String appId, Map props) -> List<String>} of JVM options
     * (without the {@code --java-options} flag). Defaults to {@code -ea} and
     * {@code -Dfile.encoding=UTF-8}.
     */
    Object javaOptions = { String appId, Map props -> ['-ea', '-Dfile.encoding=UTF-8'] }

    /**
     * Closure {@code (String appId, Map props) -> List<String>} of application
     * arguments (each element becomes one {@code --arguments} value). Defaults
     * to an empty list.
     */
    Object arguments = { String appId, Map props -> [] }

    /**
     * Optional Linux {@code .desktop} template for {@code console: true} apps.
     * Copied to {@code build/jpackage-resources/<appId>/<displayName>.desktop}.
     */
    final RegularFileProperty consoleDesktopTemplate

    /**
     * Path to the {@code jpackage} binary. Defaults to {@code $java.home/bin/jpackage}.
     * Tests may override with a stub executable.
     */
    final RegularFileProperty jpackageExecutable

    @Inject
    Bb4JpackageExtension(ObjectFactory objects) {
        enabled = objects.property(Boolean).convention(false)
        packageIdentifierPrefix = objects.property(String).convention('')
        defaultMainClass = objects.property(String).convention('')
        consoleDesktopTemplate = objects.fileProperty()
        jpackageExecutable = objects.fileProperty()
    }
}
