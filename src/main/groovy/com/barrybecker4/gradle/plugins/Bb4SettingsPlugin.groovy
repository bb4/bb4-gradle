// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.file.FileOperations
import org.gradle.util.GradleVersion

/**
 * Settings conventions for bb4 projects: Gradle floor, Foojay, Central snapshots,
 * the {@code bb4Libs} version catalog, and sibling {@code includeBuild} substitution.
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

        settings.pluginManagement.repositories.mavenLocal()
        settings.pluginManagement.repositories.gradlePluginPortal()
        settings.pluginManagement.repositories.mavenCentral()
        settings.pluginManagement.repositories.maven {
            url = 'https://central.sonatype.com/repository/maven-snapshots/'
        }

        // Default versions for versionless catalog plugin aliases when includeBuild is not used.
        def pluginVersion = Bb4CatalogToml.version(toml, 'bb4-gradle')
        settings.pluginManagement.plugins {
            id('com.barrybecker4.bb4.base') version pluginVersion
            id('com.barrybecker4.bb4.scala-library') version pluginVersion
            id('com.barrybecker4.bb4.publish') version pluginVersion
            id('com.barrybecker4.bb4.application') version pluginVersion
            id('com.barrybecker4.bb4.settings') version pluginVersion
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

        // Catalog name must not be 'bb4' — that conflicts with the project `bb4 {}` extension.
        // Import via from(file) so versionless [plugins] entries work with includeBuild.
        def catalogDir = new File(settings.gradle.gradleUserHomeDir, "caches/bb4-catalog/${pluginVersion}")
        catalogDir.mkdirs()
        def catalogFile = new File(catalogDir, 'libs.versions.toml')
        if (!catalogFile.exists() || catalogFile.getText('UTF-8') != toml) {
            catalogFile.setText(toml, 'UTF-8')
        }
        def fileOps = settings.services.get(FileOperations)
        settings.dependencyResolutionManagement.versionCatalogs.create('bb4Libs') { builder ->
            builder.from(fileOps.configurableFiles(catalogFile))
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
