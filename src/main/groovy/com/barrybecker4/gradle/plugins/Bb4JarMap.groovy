// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.Project

/**
 * Shared jarMap resolution for publish and jpackage: prefer {@code bb4.jarMap},
 * fall back to legacy {@code ext.jarMap}.
 */
final class Bb4JarMap {

    private Bb4JarMap() {}

    /**
     * Resolve jarMap from bb4 extension (preferred) or legacy ext.jarMap.
     * Validates the structure and provides clear error messages.
     */
    static Map<String, Map> resolve(Project project, Bb4Extension bb4Extension) {
        def extensionJarMap = bb4Extension != null ? bb4Extension.jarMap.getOrElse([:]) : [:]
        def legacyJarMap = project.extensions.extraProperties.has('jarMap') ?
            project.extensions.extraProperties.get('jarMap') : null

        if (!extensionJarMap.isEmpty() && legacyJarMap != null) {
            throw new IllegalStateException(
                "bb4 Gradle: jarMap configured in both bb4 {} extension and ext.jarMap. " +
                "Use only bb4 { jarMap.put('key', [...]) } — ext.jarMap is deprecated."
            )
        }

        if (!extensionJarMap.isEmpty()) {
            return new LinkedHashMap<String, Map>(extensionJarMap)
        }

        if (legacyJarMap != null) {
            if (!(legacyJarMap instanceof Map)) {
                throw new IllegalArgumentException(
                    "bb4 Gradle: ext.jarMap must be a Map, got ${legacyJarMap.getClass().name}. " +
                    "Use bb4 { jarMap.put('key', [include: [...]]) } instead."
                )
            }
            def out = new LinkedHashMap<String, Map>()
            legacyJarMap.each { k, v ->
                if (!(k instanceof String)) {
                    throw new IllegalArgumentException(
                        "bb4 Gradle: jarMap key must be a String, got ${k.getClass().name} for key: $k"
                    )
                }
                if (!(v instanceof Map)) {
                    throw new IllegalArgumentException(
                        "bb4 Gradle: jarMap value for '$k' must be a Map with 'include' and optional 'exclude', " +
                        "got ${v.getClass().name}"
                    )
                }
                out[k as String] = v as Map
            }
            return out
        }

        return [:]
    }

    /**
     * Validate a single jarMap entry has required 'include' field.
     */
    static void validateEntry(String key, Map spec) {
        if (!spec.containsKey('include')) {
            throw new IllegalArgumentException(
                "bb4 Gradle: jarMap entry '$key' must have 'include' field with file patterns. " +
                "Example: bb4 { jarMap.put('$key', [include: ['com/example/$key/**']]) }"
            )
        }
        def include = spec['include']
        if (include == null || (include instanceof Collection && ((Collection) include).isEmpty())) {
            throw new IllegalArgumentException(
                "bb4 Gradle: jarMap entry '$key' has empty 'include'. " +
                "Provide at least one file pattern."
            )
        }
    }
}
