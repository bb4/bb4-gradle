// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

/**
 * Configures release-only signing, preferring in-memory ASCII-armored keys when present.
 *
 * Properties (also via {@code ORG_GRADLE_PROJECT_*} env):
 * {@code signingKey}, {@code signingPassword}, optional {@code signingKeyId}.
 * Falls back to Gradle keyring props ({@code signing.secretKeyRingFile}, etc.).
 */
final class Bb4SigningSupport {

    private Bb4SigningSupport() {}

    static boolean isReleaseVersion(Project project) {
        return !project.version.toString().endsWith('-SNAPSHOT')
    }

    static void configure(Project project, PublishingExtension publishing) {
        def isRelease = isReleaseVersion(project)
        def signing = project.extensions.getByType(SigningExtension)

        signing.required = isRelease
        if (!isRelease) {
            return
        }

        def signingKey = project.findProperty('signingKey')?.toString()
        def signingPassword = project.findProperty('signingPassword')?.toString()
        def signingKeyId = project.findProperty('signingKeyId')?.toString()

        if (signingKey) {
            if (signingKeyId) {
                signing.useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            } else {
                signing.useInMemoryPgpKeys(signingKey, signingPassword)
            }
        }

        signing.sign(publishing.publications)
    }
}
