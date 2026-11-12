// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

/**
 * Discovers sibling bb4 checkouts for {@code includeBuild} substitution.
 */
final class Bb4SiblingBuilds {

    private Bb4SiblingBuilds() {}

    /**
     * Returns directories under {@code rootDir.parentFile} whose names start with {@code bb4-},
     * excluding the current root and {@code bb4-gradle}, that contain a settings file.
     */
    static List<File> directories(File rootDir, boolean includeSiblings) {
        if (!includeSiblings) {
            return []
        }
        def parent = rootDir.parentFile
        if (parent == null || !parent.isDirectory()) {
            return []
        }
        def currentName = rootDir.name
        return parent.listFiles()?.findAll { File dir ->
            dir.isDirectory() &&
                dir.name.startsWith('bb4-') &&
                dir.name != currentName &&
                dir.name != 'bb4-gradle' &&
                (new File(dir, 'settings.gradle').isFile() || new File(dir, 'settings.gradle.kts').isFile())
        }?.sort { it.name } ?: []
    }
}
