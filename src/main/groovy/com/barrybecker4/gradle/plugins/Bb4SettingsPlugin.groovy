// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.util.GradleVersion

/**
 * Settings conventions for bb4 projects: Gradle floor, Foojay, Central snapshots,
 * the {@code bb4} version catalog, and sibling {@code includeBuild} substitution.
 */
class Bb4SettingsPlugin implements Plugin<Settings> {

    @Override
    void apply(Settings settings) {
        def toml = Bb4CatalogToml.loadPackaged()
        def minGradle = Bb4CatalogToml.version(toml, 'gradle')
        if (GradleVersion.current() < GradleVersion.version(minGradle)) {
            throw new GradleException(
                "bb4.settings requires Gradle ${minGradle}+ (current: ${GradleVersion.current().version})",
            )
        }

        settings.pluginManagement.repositories.gradlePluginPortal()
        settings.pluginManagement.repositories.mavenCentral()
        settings.pluginManagement.repositories.maven {
            url = 'https://central.sonatype.com/repository/maven-snapshots/'
        }

        settings.pluginManager.apply('org.gradle.toolchains.foojay-resolver-convention')

        settings.dependencyResolutionManagement.repositories.mavenCentral()
        settings.dependencyResolutionManagement.repositories.maven {
            url = 'https://central.sonatype.com/repository/maven-snapshots/'
            mavenContent {
                includeGroup 'com.barrybecker4'
                includeGroup 'org.scalatest'
            }
        }

        // Build the catalog from the packaged (rendered) toml so includeBuild of bb4-gradle
        // works without a prior Maven publish of bb4-catalog. The published bb4-catalog artifact
        // remains available for consumers who import it directly.
        settings.dependencyResolutionManagement.versionCatalogs.create('bb4') { builder ->
            Bb4CatalogToml.applyTo(builder, toml)
        }

        def includeSiblings = settings.providers.gradleProperty('bb4.includeSiblings')
            .map { it != 'false' }
            .orElse(true)
            .get()
        def isIncludedBuild = settings.gradle.parent != null
        if (!isIncludedBuild && includeSiblings) {
            Bb4SiblingBuilds.directories(settings.rootDir, true).each { File dir ->
                settings.includeBuild(dir) {
                    dependencySubstitution {
                        substitute module("com.barrybecker4:${dir.name}") using project(':')
                    }
                }
            }
        }
    }
}
