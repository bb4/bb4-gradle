// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Opt-in native installer configuration under `bb4.jpackage { }`.
 * Tasks are registered only when [enabled] is true.
 */
open class Bb4JpackageExtension @Inject constructor(objects: ObjectFactory) {

    val enabled: Property<Boolean> =
        objects.property(Boolean::class.javaObjectType).convention(false)

    /**
     * Prefix for macOS package identifiers: `<prefix>.<appId>`.
     * Required when enabled.
     */
    val packageIdentifierPrefix: Property<String> =
        objects.property(String::class.java).convention("")

    /**
     * Used when an appMap entry has no `mainClass`.
     */
    val defaultMainClass: Property<String> =
        objects.property(String::class.java).convention("")

    /**
     * Closure `(String appId, Map props) -> List<String>` of JVM options
     * (without the `--java-options` flag).
     */
    var javaOptions: Any = { _: String, _: Map<*, *> ->
        listOf("-ea", "-Dfile.encoding=UTF-8")
    }

    /**
     * Closure `(String appId, Map props) -> List<String>` of application
     * arguments (each element becomes one `--arguments` value).
     */
    var arguments: Any = { _: String, _: Map<*, *> -> emptyList<String>() }

    /**
     * Optional Linux `.desktop` template for `console: true` apps.
     */
    val consoleDesktopTemplate: RegularFileProperty = objects.fileProperty()

    /**
     * Path to the `jpackage` binary. Defaults to `$java.home/bin/jpackage`.
     * Tests may override with a stub executable.
     */
    val jpackageExecutable: RegularFileProperty = objects.fileProperty()
}
