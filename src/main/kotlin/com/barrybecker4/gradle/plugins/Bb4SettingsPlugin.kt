// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.initialization.Settings
import org.gradle.util.GradleVersion
import java.net.URI

/**
 * Settings conventions for bb4 projects: Gradle floor, Foojay, Central snapshots,
 * the `bb4Libs` version catalog, and sibling `includeBuild` substitution.
 */
class Bb4SettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val toml = Bb4CatalogToml.loadPackaged()
        val minGradle = Bb4CatalogToml.version(toml, "gradle")
        if (GradleVersion.current() < GradleVersion.version(minGradle)) {
            throw GradleException(
                "bb4.settings requires Gradle $minGradle+ (current: ${GradleVersion.current().version})",
            )
        }

        settings.pluginManagement.repositories.mavenLocal()
        settings.pluginManagement.repositories.gradlePluginPortal()
        settings.pluginManagement.repositories.mavenCentral()
        settings.pluginManagement.repositories.maven { repo ->
            repo.setUrl(URI("https://central.sonatype.com/repository/maven-snapshots/"))
        }

        val pluginVersion = Bb4CatalogToml.version(toml, "bb4-gradle")
        settings.pluginManagement.plugins {
            it.id("com.barrybecker4.bb4.base").version(pluginVersion)
            it.id("com.barrybecker4.bb4.scala-library").version(pluginVersion)
            it.id("com.barrybecker4.bb4.publish").version(pluginVersion)
            it.id("com.barrybecker4.bb4.application").version(pluginVersion)
            it.id("com.barrybecker4.bb4.settings").version(pluginVersion)
        }

        settings.pluginManager.apply("org.gradle.toolchains.foojay-resolver-convention")

        settings.dependencyResolutionManagement.repositories.mavenCentral()
        settings.dependencyResolutionManagement.repositories.maven { repo ->
            repo.setUrl(URI("https://central.sonatype.com/repository/maven-snapshots/"))
            repo.mavenContent {
                it.includeGroup("com.barrybecker4")
                it.includeGroup("org.scalatest")
            }
        }

        // Catalog name must not be 'bb4' — that conflicts with the project `bb4 {}` extension.
        // Populate via applyTo (avoids internal FileOperations) so versionless plugin aliases work.
        settings.dependencyResolutionManagement.versionCatalogs.create("bb4Libs") { builder ->
            Bb4CatalogToml.applyTo(builder, toml)
        }

        val includeSiblings = settings.providers.gradleProperty("bb4.includeSiblings")
            .map { it != "false" }
            .orElse(true)
            .get()
        val isIncludedBuild = settings.gradle.parent != null
        if (!isIncludedBuild && includeSiblings) {
            Bb4SiblingBuilds.directories(settings.rootDir, true).forEach { dir ->
                settings.includeBuild(dir) { spec ->
                    spec.dependencySubstitution { substitutions: DependencySubstitutions ->
                        substitutions.substitute(
                            substitutions.module("com.barrybecker4:${dir.name}"),
                        ).using(substitutions.project(":"))
                    }
                }
            }
        }
    }
}
