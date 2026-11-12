// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project

/**
 * Resolves the Central Portal maven-snapshots deploy URL.
 * Legacy `oss.sonatype.org` overrides are ignored (405 after OSSRH EOL).
 */
object Bb4SnapshotRepoSupport {

    const val DEFAULT_SNAPSHOT_URL = "https://central.sonatype.com/repository/maven-snapshots/"

    @JvmStatic
    fun resolveSnapshotRepoUrl(project: Project): String {
        val explicit = (
            project.findProperty("bb4.ossrh.snapshotUrl")
                ?: project.findProperty("bb4.central.snapshotUrl")
            )?.toString()?.trim()
        if (explicit.isNullOrEmpty()) {
            return DEFAULT_SNAPSHOT_URL
        }
        if (explicit.contains("oss.sonatype.org")) {
            project.logger.warn(
                "bb4 Gradle: ignoring legacy bb4.ossrh.snapshotUrl (OSSRH EOL): {} — using {}",
                explicit,
                DEFAULT_SNAPSHOT_URL,
            )
            return DEFAULT_SNAPSHOT_URL
        }
        return explicit
    }
}
