// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import java.util.regex.Pattern

/**
 * Parse and rewrite the bb4 version-catalog toml shipped with the plugins.
 */
final class Bb4CatalogToml {

    private static final String BB4_GRADLE_KEY = 'bb4-gradle'

    private Bb4CatalogToml() {}

    static String version(String text, String key) {
        def pattern = Pattern.compile('(?m)^' + Pattern.quote(key) + '\\s*=\\s*"([^"]+)"\\s*$')
        def matcher = pattern.matcher(text)
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing version key '${key}' in catalog toml")
        }
        return matcher.group(1)
    }

    static String render(String text, String projectVersion) {
        def pattern = ~/(?m)^bb4-gradle\s*=\s*"[^"]+"\s*$/
        if (!(text =~ pattern).find()) {
            throw new IllegalArgumentException("Missing version key '${BB4_GRADLE_KEY}' in catalog toml")
        }
        return text.replaceFirst(pattern, "bb4-gradle = \"${projectVersion}\"")
    }

    static String loadPackaged() {
        def stream = Bb4CatalogToml.class.getResourceAsStream('/libs.versions.toml')
        if (stream == null) {
            throw new IllegalStateException('Packaged /libs.versions.toml is missing from the plugin jar')
        }
        return stream.getText('UTF-8')
    }

    /**
     * Populate a {@link org.gradle.api.initialization.dsl.VersionCatalogBuilder} from toml text.
     * Used by the settings plugin so the catalog works without {@code settings.files(...)}.
     */
    static void applyTo(Object builder, String text) {
        def versionsSection = section(text, '[versions]', '[libraries]')
        def librariesSection = section(text, '[libraries]', '[plugins]')
        def pluginsSection = section(text, '[plugins]', null)

        def versionPattern = Pattern.compile('(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*"([^"]+)"\\s*$')
        def versionMatcher = versionPattern.matcher(versionsSection)
        while (versionMatcher.find()) {
            builder.version(versionMatcher.group(1), versionMatcher.group(2))
        }

        def libraryPattern = Pattern.compile(
            '(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\\{\\s*module\\s*=\\s*"([^:]+):([^"]+)"\\s*,\\s*version\\.ref\\s*=\\s*"([^"]+)"\\s*\\}',
        )
        def libraryMatcher = libraryPattern.matcher(librariesSection)
        while (libraryMatcher.find()) {
            def alias = libraryMatcher.group(1)
            def group = libraryMatcher.group(2)
            def name = libraryMatcher.group(3)
            def versionRef = libraryMatcher.group(4)
            builder.library(alias, group, name).versionRef(versionRef)
        }

        def pluginPattern = Pattern.compile(
            '(?m)^([a-zA-Z0-9_-]+)\\s*=\\s*\\{\\s*id\\s*=\\s*"([^"]+)"\\s*,\\s*version\\.ref\\s*=\\s*"([^"]+)"\\s*\\}',
        )
        def pluginMatcher = pluginPattern.matcher(pluginsSection)
        while (pluginMatcher.find()) {
            def alias = pluginMatcher.group(1)
            def id = pluginMatcher.group(2)
            def versionRef = pluginMatcher.group(3)
            builder.plugin(alias, id).versionRef(versionRef)
        }
    }

    private static String section(String text, String startMarker, String endMarker) {
        def start = text.indexOf(startMarker)
        if (start < 0) {
            return ''
        }
        def from = start + startMarker.length()
        if (endMarker == null) {
            return text.substring(from)
        }
        def end = text.indexOf(endMarker, from)
        return end < 0 ? text.substring(from) : text.substring(from, end)
    }
}
