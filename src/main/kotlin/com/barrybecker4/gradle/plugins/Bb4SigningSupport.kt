// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

/**
 * Configures release-only signing, preferring in-memory ASCII-armored keys when present.
 *
 * Properties (also via `ORG_GRADLE_PROJECT_*` env):
 * `signingKey`, `signingPassword`, optional `signingKeyId`.
 * Falls back to Gradle keyring props (`signing.secretKeyRingFile`, etc.).
 */
object Bb4SigningSupport {

    @JvmStatic
    fun isReleaseVersion(project: Project): Boolean =
        !project.version.toString().endsWith("-SNAPSHOT")

    @JvmStatic
    fun configure(project: Project, publishing: PublishingExtension) {
        val isRelease = isReleaseVersion(project)
        val signing = project.extensions.getByType(SigningExtension::class.java)

        signing.isRequired = isRelease
        if (!isRelease) {
            return
        }

        val signingKey = project.findProperty("signingKey")?.toString()
        val signingPassword = project.findProperty("signingPassword")?.toString()
        val signingKeyId = project.findProperty("signingKeyId")?.toString()

        if (!signingKey.isNullOrEmpty()) {
            if (!signingKeyId.isNullOrEmpty()) {
                signing.useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            } else {
                signing.useInMemoryPgpKeys(signingKey, signingPassword)
            }
        }

        signing.sign(publishing.publications)
    }
}
