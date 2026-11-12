// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class Bb4Extension @Inject constructor(objects: ObjectFactory) {

    val archivesBaseName: Property<String> =
        objects.property(String::class.java).convention("")

    val mainClass: Property<String> =
        objects.property(String::class.java).convention("")

    val scalaVersion: Property<String> =
        objects.property(String::class.java).convention(Bb4DependencyVersions.SCALA)

    /**
     * Optional bytecode / API level for Java and Scala (`javac --release` /
     * scalac `-release:`). Unset keeps toolchain-only defaults (Java 25).
     */
    val release: Property<Int> = objects.property(Int::class.javaObjectType)

    /**
     * GitHub repository name under the bb4 org (POM url/scm). Prefer this over
     * the legacy `bb4.repository` extra/gradle property.
     */
    val repository: Property<String> =
        objects.property(String::class.java).convention("")

    /**
     * Typed multi-app model. Prefer this over [appMap] / legacy `ext.appMap`.
     * Drives `run*`, `create*App` start scripts, deployHtml, and jpackage.
     */
    val apps: Bb4AppsContainer = Bb4AppsContainer.create(objects)

    /**
     * Application metadata previously set via `ext.appMap`. Prefer [apps];
     * this map and legacy `ext.appMap` remain as fallbacks when `apps` is empty.
     */
    @Suppress("UNCHECKED_CAST")
    val appMap: MapProperty<String, Map<String, Any?>> =
        objects.mapProperty(String::class.java, Map::class.java as Class<Map<String, Any?>>)
            .convention(emptyMap())

    /**
     * Root directory for `deploySchemas` (`.dtd` / `.xsd` only under this tree).
     * Convention set by plugins to `source/schema` via project layout.
     */
    val schemaDirectory: DirectoryProperty = objects.directoryProperty()

    val jpackage: Bb4JpackageExtension =
        objects.newInstance(Bb4JpackageExtension::class.java)

    private var onJpackageConfigured: (() -> Unit)? = null

    internal fun whenJpackageConfigured(callback: () -> Unit) {
        onJpackageConfigured = callback
    }

    fun jpackage(action: Action<in Bb4JpackageExtension>) {
        action.execute(jpackage)
        onJpackageConfigured?.invoke()
    }

    val htmlReports: Bb4HtmlReportsExtension =
        objects.newInstance(Bb4HtmlReportsExtension::class.java)

    private var onHtmlReportsConfigured: (() -> Unit)? = null

    internal fun whenHtmlReportsConfigured(callback: () -> Unit) {
        onHtmlReportsConfigured = callback
    }

    fun htmlReports(action: Action<in Bb4HtmlReportsExtension>) {
        action.execute(htmlReports)
        onHtmlReportsConfigured?.invoke()
    }
}
