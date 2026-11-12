// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Typed multi-app entry previously expressed as an untyped `ext.appMap` / `bb4.appMap` row.
 */
abstract class Bb4AppEntry @Inject constructor(private val entryName: String) : Named {
    abstract val mainClass: Property<String>
    abstract val title: Property<String>
    abstract val command: Property<String>
    abstract val description: Property<String>
    abstract val longDescription: Property<String>
    abstract val imageName: Property<String>
    abstract val maxHeap: Property<String>
    abstract val args: ListProperty<String>
    abstract val jvmArgs: ListProperty<String>
    abstract val startOnFirstThreadMac: Property<Boolean>
    abstract val deploy: Property<Boolean>
    abstract val console: Property<Boolean>
    abstract val macPackageName: Property<String>
    abstract val flag: Property<String>
    abstract val panelClass: Property<String>
    abstract val applicationName: Property<String>
    abstract val script: Property<String>
    abstract val resourcePath: Property<String>
    /** When false, skip generated CreateStartScripts for this app (run* still registered). */
    abstract val createStartScript: Property<Boolean>

    override fun getName(): String = entryName
}

/**
 * NamedDomainObjectContainer wrapper that keeps Groovy
 * `apps.put('sudoku', [mainClass: '…', mx: '8G'])` working.
 */
class Bb4AppsContainer(
    private val inner: NamedDomainObjectContainer<Bb4AppEntry>,
    private val pendingSpec: ThreadLocal<Map<String, Any?>?>,
) : NamedDomainObjectContainer<Bb4AppEntry> by inner {

    fun put(name: String, spec: Map<String, Any?>): Bb4AppEntry {
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
        fun create(objects: ObjectFactory): Bb4AppsContainer {
            val pendingSpec = ThreadLocal<Map<String, Any?>?>()
            val container = objects.domainObjectContainer(Bb4AppEntry::class.java) { name ->
                objects.newInstance(Bb4AppEntry::class.java, name).also { entry ->
                    pendingSpec.get()?.let { applySpec(entry, it) }
                }
            }
            return Bb4AppsContainer(container, pendingSpec)
        }

        fun applySpec(entry: Bb4AppEntry, spec: Map<String, Any?>) {
            stringProp(spec, "mainClass")?.let { entry.mainClass.set(it) }
            stringProp(spec, "title")?.let { entry.title.set(it) }
            stringProp(spec, "command")?.let { entry.command.set(it) }
            stringProp(spec, "description")?.let { entry.description.set(it) }
            stringProp(spec, "longDescription")?.let { entry.longDescription.set(it) }
            stringProp(spec, "imageName")?.let { entry.imageName.set(it) }
            stringProp(spec, "maxHeap")?.let { entry.maxHeap.set(it) }
                ?: stringProp(spec, "mx")?.let { entry.maxHeap.set(it) }
            stringProp(spec, "macPackageName")?.let { entry.macPackageName.set(it) }
            stringProp(spec, "flag")?.let { entry.flag.set(it) }
            stringProp(spec, "panelClass")?.let { entry.panelClass.set(it) }
                ?: stringProp(spec, "panel_class")?.let { entry.panelClass.set(it) }
            stringProp(spec, "applicationName")?.let { entry.applicationName.set(it) }
            stringProp(spec, "script")?.let { entry.script.set(it) }
            stringProp(spec, "resourcePath")?.let { entry.resourcePath.set(it) }

            if (spec.containsKey("startOnFirstThreadMac")) {
                entry.startOnFirstThreadMac.set(spec["startOnFirstThreadMac"] == true)
            }
            if (spec.containsKey("deploy")) {
                entry.deploy.set(spec["deploy"] != false)
            }
            if (spec.containsKey("console")) {
                entry.console.set(spec["console"] == true)
            }
            if (spec.containsKey("createStartScript")) {
                entry.createStartScript.set(spec["createStartScript"] != false)
            }
            listProp(spec, "args")?.let { entry.args.set(it) }
            listProp(spec, "jvmArgs")?.let { entry.jvmArgs.set(it) }
        }

        private fun stringProp(spec: Map<String, Any?>, key: String): String? {
            if (!spec.containsKey(key) || spec[key] == null) {
                return null
            }
            return spec[key].toString()
        }

        private fun listProp(spec: Map<String, Any?>, key: String): List<String>? {
            if (!spec.containsKey(key)) {
                return null
            }
            return when (val value = spec[key]) {
                null -> emptyList()
                is Collection<*> -> value.map { it.toString() }
                else -> listOf(value.toString())
            }
        }
    }
}

/**
 * JVM-arg defaults and app-model helpers shared by run/start-script generation.
 */
object Bb4AppSupport {

    @JvmStatic
    fun capitalizeAppId(appId: String): String =
        appId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    @JvmStatic
    @JvmOverloads
    fun defaultJvmArgs(
        maxHeap: String?,
        startOnFirstThreadMac: Boolean,
        osName: String?,
        extra: List<String> = emptyList(),
    ): List<String> {
        val heap = maxHeap?.takeIf { it.isNotBlank() } ?: "256m"
        val args = mutableListOf("-ea", "-Xss128m", "-Xmx$heap")
        if (startOnFirstThreadMac && Bb4JpackageSupport.isMac(osName)) {
            args.add(0, "-XstartOnFirstThread")
        }
        args.addAll(extra)
        return args
    }

    @JvmStatic
    fun defaultJvmArgsFromProps(propMap: Map<String, Any?>, osName: String?): List<String> {
        val maxHeap = (propMap["maxHeap"] ?: propMap["mx"])?.toString()
        val startOnFirst = propMap["startOnFirstThreadMac"] == true
        @Suppress("UNCHECKED_CAST")
        val extra = when (val raw = propMap["jvmArgs"]) {
            is Collection<*> -> raw.map { it.toString() }
            null -> emptyList()
            else -> listOf(raw.toString())
        }
        return defaultJvmArgs(maxHeap, startOnFirst, osName, extra)
    }

    @JvmStatic
    fun programArgsFromProps(propMap: Map<String, Any?>): List<String> {
        when (val raw = propMap["args"]) {
            is Collection<*> -> {
                if (raw.isNotEmpty()) {
                    return raw.map { it.toString() }
                }
            }
            null -> Unit
            else -> return listOf(raw.toString())
        }
        val panel = (propMap["panelClass"] ?: propMap["panel_class"])?.toString()?.takeIf { it.isNotBlank() }
        if (panel != null) {
            return listOf("-panel_class", panel)
        }
        val args = mutableListOf<String>()
        propMap["script"]?.toString()?.takeIf { it.isNotBlank() }?.let { args.add(it) }
        propMap["resourcePath"]?.toString()?.takeIf { it.isNotBlank() }?.let { args.add(it) }
        return args
    }

    @JvmStatic
    fun applicationNameFor(appId: String, propMap: Map<String, Any?>): String {
        propMap["applicationName"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val command = propMap["command"]?.toString()?.trim().orEmpty()
        // HTML command lines often include args; CreateStartScripts needs a single token.
        if (command.isNotEmpty() && !command.contains(' ')) {
            return command
        }
        return "bb4-$appId"
    }

    @JvmStatic
    fun shouldCreateStartScript(propMap: Map<String, Any?>): Boolean =
        !(propMap.containsKey("createStartScript") && propMap["createStartScript"] == false)

    @JvmStatic
    fun toPropMap(entry: Bb4AppEntry): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        entry.mainClass.orNull?.takeIf { it.isNotBlank() }?.let { map["mainClass"] = it }
        entry.title.orNull?.takeIf { it.isNotBlank() }?.let { map["title"] = it }
        entry.command.orNull?.takeIf { it.isNotBlank() }?.let { map["command"] = it }
        entry.description.orNull?.takeIf { it.isNotBlank() }?.let { map["description"] = it }
        entry.longDescription.orNull?.takeIf { it.isNotBlank() }?.let { map["longDescription"] = it }
        entry.imageName.orNull?.takeIf { it.isNotBlank() }?.let { map["imageName"] = it }
        entry.maxHeap.orNull?.takeIf { it.isNotBlank() }?.let {
            map["maxHeap"] = it
            map["mx"] = it
        }
        entry.macPackageName.orNull?.takeIf { it.isNotBlank() }?.let { map["macPackageName"] = it }
        entry.flag.orNull?.takeIf { it.isNotBlank() }?.let { map["flag"] = it }
        entry.panelClass.orNull?.takeIf { it.isNotBlank() }?.let {
            map["panelClass"] = it
            map["panel_class"] = it
        }
        entry.applicationName.orNull?.takeIf { it.isNotBlank() }?.let { map["applicationName"] = it }
        entry.script.orNull?.takeIf { it.isNotBlank() }?.let { map["script"] = it }
        entry.resourcePath.orNull?.takeIf { it.isNotBlank() }?.let { map["resourcePath"] = it }
        if (entry.startOnFirstThreadMac.isPresent) {
            map["startOnFirstThreadMac"] = entry.startOnFirstThreadMac.get()
        }
        if (entry.deploy.isPresent) {
            map["deploy"] = entry.deploy.get()
        }
        if (entry.console.isPresent) {
            map["console"] = entry.console.get()
        }
        if (entry.createStartScript.isPresent) {
            map["createStartScript"] = entry.createStartScript.get()
        }
        val args = entry.args.getOrElse(emptyList())
        if (args.isNotEmpty()) {
            map["args"] = args
        }
        val jvmArgs = entry.jvmArgs.getOrElse(emptyList())
        if (jvmArgs.isNotEmpty()) {
            map["jvmArgs"] = jvmArgs
        }
        return map
    }

    /**
     * Prefer `bb4.apps`, then `bb4.appMap`, then legacy `ext.appMap`.
     */
    @JvmStatic
    fun resolveAppMap(project: org.gradle.api.Project): Map<String, Map<String, Any?>> {
        val bb4 = project.extensions.findByType(Bb4Extension::class.java)
        val fromApps = bb4?.apps?.associate { it.name to toPropMap(it) }.orEmpty()
        if (fromApps.isNotEmpty()) {
            return LinkedHashMap(fromApps)
        }
        val fromExtension = bb4?.appMap?.orNull ?: emptyMap()
        if (fromExtension.isNotEmpty()) {
            return fromExtension
        }
        val raw = project.findProperty("appMap")
        return if (raw is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            raw as Map<String, Map<String, Any?>>
        } else {
            emptyMap()
        }
    }
}
