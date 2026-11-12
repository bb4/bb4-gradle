// Copyight by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import java.io.File

/**
 * Discovers sibling bb4 checkouts for `includeBuild` substitution.
 */
object Bb4SiblingBuilds {

    /**
     * Returns directories under `rootDir.parentFile` whose names start with `bb4-`,
     * excluding the current root and `bb4-gradle`, that contain a settings file.
     */
    @JvmStatic
    fun directories(rootDir: File, includeSiblings: Boolean): List<File> {
        if (!includeSiblings) {
            return emptyList()
        }
        val parent = rootDir.parentFile
        if (parent == null || !parent.isDirectory) {
            return emptyList()
        }
        val currentName = rootDir.name
        return parent.listFiles()
            ?.filter { dir ->
                dir.isDirectory &&
                    dir.name.startsWith("bb4-") &&
                    dir.name != currentName &&
                    dir.name != "bb4-gradle" &&
                    (File(dir, "settings.gradle").isFile || File(dir, "settings.gradle.kts").isFile)
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }
}
