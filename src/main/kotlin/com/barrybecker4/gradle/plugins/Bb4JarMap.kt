// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project

/**
 * Shared jarMap resolution for publish and jpackage: prefer `bb4.jarMap`,
 * fall back to legacy `ext.jarMap`.
 */
object Bb4JarMap {

    /**
     * Resolve jarMap from bb4 extension (preferred) or legacy ext.jarMap.
     * Returns a linked map of key → spec map with include/exclude lists.
     */
    @JvmStatic
    fun resolve(project: Project, bb4Extension: Bb4Extension?): LinkedHashMap<String, Map<String, Any?>> {
        val extensionEntries = bb4Extension?.jarMap?.associate { entry ->
            entry.name to mapOf(
                "include" to entry.include.getOrElse(emptyList()),
                "exclude" to entry.exclude.getOrElse(emptyList()),
            )
        } ?: emptyMap()

        val legacyJarMap = if (project.extensions.extraProperties.has("jarMap")) {
            project.extensions.extraProperties.get("jarMap")
        } else {
            null
        }

        if (extensionEntries.isNotEmpty() && legacyJarMap != null) {
            throw IllegalStateException(
                "bb4 Gradle: jarMap configured in both bb4 {} extension and ext.jarMap. " +
                    "Use only bb4 { jarMap.put('key', [...]) } — ext.jarMap is deprecated.",
            )
        }

        if (extensionEntries.isNotEmpty()) {
            return LinkedHashMap(extensionEntries)
        }

        if (legacyJarMap != null) {
            if (legacyJarMap !is Map<*, *>) {
                throw IllegalArgumentException(
                    "bb4 Gradle: ext.jarMap must be a Map, got ${legacyJarMap.javaClass.name}. " +
                        "Use bb4 { jarMap.put('key', [include: [...]]) } instead.",
                )
            }
            val out = LinkedHashMap<String, Map<String, Any?>>()
            for ((k, v) in legacyJarMap) {
                if (k !is String) {
                    throw IllegalArgumentException(
                        "bb4 Gradle: jarMap key must be a String, got ${k?.javaClass?.name} for key: $k",
                    )
                }
                if (v !is Map<*, *>) {
                    throw IllegalArgumentException(
                        "bb4 Gradle: jarMap value for '$k' must be a Map with 'include' and optional 'exclude', " +
                            "got ${v?.javaClass?.name}",
                    )
                }
                @Suppress("UNCHECKED_CAST")
                out[k] = v as Map<String, Any?>
            }
            return out
        }

        return LinkedHashMap()
    }

    /**
     * Validate a single jarMap entry has required 'include' field.
     */
    @JvmStatic
    fun validateEntry(key: String, spec: Map<String, Any?>) {
        if (!spec.containsKey("include")) {
            throw IllegalArgumentException(
                "bb4 Gradle: jarMap entry '$key' must have 'include' field with file patterns. " +
                    "Example: bb4 { jarMap.put('$key', [include: ['com/example/$key/**']]) }",
            )
        }
        val include = spec["include"]
        if (include == null || (include is Collection<*> && include.isEmpty())) {
            throw IllegalArgumentException(
                "bb4 Gradle: jarMap entry '$key' has empty 'include'. " +
                    "Provide at least one file pattern.",
            )
        }
    }

    @JvmStatic
    fun validateEntry(entry: JarMapEntry) {
        if (!entry.include.isPresent) {
            throw IllegalArgumentException(
                "bb4 Gradle: jarMap entry '${entry.name}' must have 'include' field with file patterns. " +
                    "Example: bb4 { jarMap.put('${entry.name}', [include: ['com/example/${entry.name}/**']]) }",
            )
        }
        val include = entry.include.get()
        if (include.isEmpty()) {
            throw IllegalArgumentException(
                "bb4 Gradle: jarMap entry '${entry.name}' has empty 'include'. " +
                    "Provide at least one file pattern.",
            )
        }
    }
}
