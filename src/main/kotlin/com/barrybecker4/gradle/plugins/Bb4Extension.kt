// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Multi-artifact jarMap entry with typed include/exclude patterns.
 */
abstract class JarMapEntry @Inject constructor(private val entryName: String) : Named {
    abstract val include: ListProperty<String>
    abstract val exclude: ListProperty<String>

    override fun getName(): String = entryName
}

/**
 * NamedDomainObjectContainer wrapper that keeps Groovy
 * `jarMap.put('key', [include: [...], exclude: [...]])` working.
 *
 * Specs are applied in the container factory *before* `all`/`whenObjectAdded`
 * callbacks fire, so include/exclude are visible to publish wiring.
 */
class Bb4JarMapContainer(
    private val inner: NamedDomainObjectContainer<JarMapEntry>,
    private val pendingSpec: ThreadLocal<Map<String, Any?>?>,
) : NamedDomainObjectContainer<JarMapEntry> by inner {

    /**
     * Groovy-compatible put from an untyped map spec.
     */
    fun put(name: String, spec: Map<String, Any?>): JarMapEntry {
        Bb4JarMap.validateEntry(name, spec)
        val existing = inner.findByName(name)
        if (existing != null) {
            applySpec(existing, spec)
            return existing
        }
        pendingSpec.set(spec)
        try {
            return inner.create(name)
        } finally {
            pendingSpec.remove()
        }
    }

    operator fun set(name: String, spec: Map<String, Any?>) {
        put(name, spec)
    }

    companion object {
        fun applySpec(entry: JarMapEntry, spec: Map<String, Any?>) {
            if (spec.containsKey("include")) {
                val includeVal = spec["include"]
                when (includeVal) {
                    is Collection<*> -> entry.include.set(includeVal.map { it.toString() })
                    null -> entry.include.set(emptyList())
                    else -> entry.include.set(listOf(includeVal.toString()))
                }
            }
            if (spec.containsKey("exclude")) {
                val excludeVal = spec["exclude"]
                when (excludeVal) {
                    is Collection<*> -> entry.exclude.set(excludeVal.map { it.toString() })
                    null -> entry.exclude.set(emptyList())
                    else -> entry.exclude.set(listOf(excludeVal.toString()))
                }
            }
        }
    }
}

open class Bb4Extension @Inject constructor(objects: ObjectFactory) {

    val archivesBaseName: Property<String> =
        objects.property(String::class.java).convention("")

    val mainClass: Property<String> =
        objects.property(String::class.java).convention("")

    val scalaVersion: Property<String> =
        objects.property(String::class.java).convention(Bb4DependencyVersions.SCALA)

    /**
     * GitHub repository name under the bb4 org (POM url/scm). Prefer this over
     * the legacy `bb4.repository` extra/gradle property.
     */
    val repository: Property<String> =
        objects.property(String::class.java).convention("")

    val jarMap: Bb4JarMapContainer = run {
        val pendingSpec = ThreadLocal<Map<String, Any?>?>()
        val container = objects.domainObjectContainer(JarMapEntry::class.java) { name ->
            objects.newInstance(JarMapEntry::class.java, name).also { entry ->
                pendingSpec.get()?.let { Bb4JarMapContainer.applySpec(entry, it) }
            }
        }
        Bb4JarMapContainer(container, pendingSpec)
    }

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
}
