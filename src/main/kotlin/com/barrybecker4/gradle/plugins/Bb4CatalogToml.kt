// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import java.util.regex.Pattern

/**
 * Parse and rewrite the bb4 version-catalog toml shipped with the plugins.
 */
object Bb4CatalogToml {

    private const val BB4_GRADLE_KEY = "bb4-gradle"

    @JvmStatic
    fun version(text: String, key: String): String {
        val pattern = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]+)\"\\s*$")
        val matcher = pattern.matcher(text)
        if (!matcher.find()) {
            throw IllegalArgumentException("Missing version key '$key' in catalog toml")
        }
        return matcher.group(1)
    }

    @JvmStatic
    fun render(text: String, projectVersion: String): String {
        val pattern = Regex("""(?m)^bb4-gradle\s*=\s*"[^"]+"\s*$""")
        if (!pattern.containsMatchIn(text)) {
            throw IllegalArgumentException("Missing version key '$BB4_GRADLE_KEY' in catalog toml")
        }
        return pattern.replaceFirst(text, "bb4-gradle = \"$projectVersion\"")
    }

    @JvmStatic
    fun loadPackaged(): String {
        val stream = Bb4CatalogToml::class.java.getResourceAsStream("/libs.versions.toml")
            ?: throw IllegalStateException("Packaged /libs.versions.toml is missing from the plugin jar")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * Populate a version-catalog builder from toml text.
     * Accepts [VersionCatalogBuilder] or a test double with the same methods.
     */
    @JvmStatic
    fun applyTo(builder: Any, text: String) {
        if (builder is VersionCatalogBuilder) {
            applyToBuilder(builder, text)
            return
        }
        applyToReflective(builder, text)
    }

    private fun applyToBuilder(builder: VersionCatalogBuilder, text: String) {
        val versionsSection = section(text, "[versions]", "[libraries]")
        val librariesSection = section(text, "[libraries]", "[plugins]")
        val pluginsSection = section(text, "[plugins]", null)

        val versionPattern = Pattern.compile("(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\"([^\"]+)\"\\s*$")
        val versionMatcher = versionPattern.matcher(versionsSection)
        while (versionMatcher.find()) {
            builder.version(versionMatcher.group(1), versionMatcher.group(2))
        }

        val libraryPattern = Pattern.compile(
            "(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\\{\\s*module\\s*=\\s*\"([^:]+):([^\"]+)\"\\s*,\\s*version\\.ref\\s*=\\s*\"([^\"]+)\"\\s*}",
        )
        val libraryMatcher = libraryPattern.matcher(librariesSection)
        while (libraryMatcher.find()) {
            val alias = libraryMatcher.group(1)
            val group = libraryMatcher.group(2)
            val name = libraryMatcher.group(3)
            val versionRef = libraryMatcher.group(4)
            builder.library(alias, group, name).versionRef(versionRef)
        }

        // Prefer VersionCatalogBuilder.from(tomlFile) for real catalogs: the
        // programmatic PluginAliasBuilder API cannot express versionless plugins
        // (no withoutVersion), and attaching bb4-gradle versions breaks consumers
        // that already have the plugin jar on the settings classpath.
        // applyTo still supports version.ref plugin rows for tests / tooling.
        val pluginWithVersion = Pattern.compile(
            "(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\\{\\s*id\\s*=\\s*\"([^\"]+)\"\\s*,\\s*version\\.ref\\s*=\\s*\"([^\"]+)\"\\s*}",
        )
        val pluginIdOnly = Pattern.compile(
            "(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\\{\\s*id\\s*=\\s*\"([^\"]+)\"\\s*}",
        )
        val withVersionMatcher = pluginWithVersion.matcher(pluginsSection)
        val seen = HashSet<String>()
        while (withVersionMatcher.find()) {
            val alias = withVersionMatcher.group(1)
            seen.add(alias)
            builder.plugin(alias, withVersionMatcher.group(2)).versionRef(withVersionMatcher.group(3))
        }
        val idOnlyMatcher = pluginIdOnly.matcher(pluginsSection)
        while (idOnlyMatcher.find()) {
            val alias = idOnlyMatcher.group(1)
            if (alias in seen) {
                continue
            }
            // Skip programmatic registration — PluginAliasBuilder requires a version.
            // Callers that need id-only aliases must use from(toml) instead.
        }
    }

    private fun applyToReflective(builder: Any, text: String) {
        val versionsSection = section(text, "[versions]", "[libraries]")
        val librariesSection = section(text, "[libraries]", "[plugins]")
        val pluginsSection = section(text, "[plugins]", null)

        val versionPattern = Pattern.compile("(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\"([^\"]+)\"\\s*$")
        val versionMatcher = versionPattern.matcher(versionsSection)
        while (versionMatcher.find()) {
            invoke(builder, "version", versionMatcher.group(1), versionMatcher.group(2))
        }

        val libraryPattern = Pattern.compile(
            "(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\\{\\s*module\\s*=\\s*\"([^:]+):([^\"]+)\"\\s*,\\s*version\\.ref\\s*=\\s*\"([^\"]+)\"\\s*}",
        )
        val libraryMatcher = libraryPattern.matcher(librariesSection)
        while (libraryMatcher.find()) {
            val libraryBuilder = invoke(
                builder,
                "library",
                libraryMatcher.group(1),
                libraryMatcher.group(2),
                libraryMatcher.group(3),
            )
            invoke(libraryBuilder!!, "versionRef", libraryMatcher.group(4))
        }

        val pluginWithVersion = Pattern.compile(
            "(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\\{\\s*id\\s*=\\s*\"([^\"]+)\"\\s*,\\s*version\\.ref\\s*=\\s*\"([^\"]+)\"\\s*}",
        )
        val withVersionMatcher = pluginWithVersion.matcher(pluginsSection)
        while (withVersionMatcher.find()) {
            val pluginBuilder = invoke(builder, "plugin", withVersionMatcher.group(1), withVersionMatcher.group(2))
            invoke(pluginBuilder!!, "versionRef", withVersionMatcher.group(3))
        }
    }

    private fun invoke(target: Any, method: String, vararg args: Any): Any? {
        val m = target.javaClass.methods.first { candidate ->
            candidate.name == method && candidate.parameterCount == args.size
        }
        return m.invoke(target, *args)
    }

    private fun section(text: String, startMarker: String, endMarker: String?): String {
        val start = text.indexOf(startMarker)
        if (start < 0) {
            return ""
        }
        val from = start + startMarker.length
        if (endMarker == null) {
            return text.substring(from)
        }
        val end = text.indexOf(endMarker, from)
        return if (end < 0) text.substring(from) else text.substring(from, end)
    }
}
