// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI

object Bb4Conventions {

    /**
     * Configure repositories for bb4 projects.
     */
    @JvmStatic
    fun configureRepositories(repositories: RepositoryHandler) {
        repositories.mavenCentral()
        repositories.maven { repo ->
            repo.setUrl(URI("https://central.sonatype.com/repository/maven-snapshots/"))
            repo.mavenContent {
                it.includeGroup("com.barrybecker4")
                it.includeGroup("org.scalatest")
            }
        }
    }

    /**
     * Configure resolution strategy for bb4 projects.
     * Uses Gradle's normal cache behavior; CI passes `--refresh-dependencies` when needed.
     */
    @JvmStatic
    fun configureResolutionStrategy(@Suppress("UNUSED_PARAMETER") project: Project) {
        // Intentionally empty — see Groovy predecessor comments.
    }

    @JvmStatic
    fun configureOssrhDefaults(project: Project) {
        val ext = project.extensions.extraProperties
        if (!ext.has("ossrhToken")) {
            ext.set("ossrhToken", "")
        }
        if (!ext.has("ossrhTokenPassword")) {
            ext.set("ossrhTokenPassword", "")
        }
    }

    @JvmStatic
    fun addScalaLibraryDependenciesExceptScalaLibrary(dependencies: DependencyHandler) {
        dependencies.add("implementation", "java3d:vecmath:${Bb4DependencyVersions.VECMATH}")
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:${Bb4DependencyVersions.JUNIT}")
        dependencies.add("testImplementation", "org.scalatest:scalatest_3:${Bb4DependencyVersions.SCALATEST}")
        dependencies.add(
            "testRuntimeOnly",
            "org.scalatestplus:junit-5-14_3:${Bb4DependencyVersions.SCALATEST_PLUS_JUNIT5}",
        )
        dependencies.add("testRuntimeOnly", "org.scala-lang.modules:scala-xml_3:${Bb4DependencyVersions.SCALA_XML}")
        dependencies.add("testRuntimeOnly", "org.pegdown:pegdown:${Bb4DependencyVersions.PEGDOWN}")
        dependencies.add("testRuntimeOnly", "com.vladsch.flexmark:flexmark-all:${Bb4DependencyVersions.FLEXMARK}")
    }
}
