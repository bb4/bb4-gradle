// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler

final class Bb4Conventions {

    /**
     * Configure repositories for bb4 projects.
     * - mavenCentral(): all stable releases (JUnit, ScalaTest, Scala, vecmath, etc.)
     * - Central snapshots: bb4 SNAPSHOTs and upstream library SNAPSHOTs published via Central Portal.
     *   Restricted to com.barrybecker4 and org.scalatest to limit scope; extend if other upstream
     *   libraries publish SNAPSHOTs here.
     */
    static void configureRepositories(RepositoryHandler repositories) {
        repositories.mavenCentral()
        repositories.maven {
            url = 'https://central.sonatype.com/repository/maven-snapshots/'
            mavenContent {
                includeGroup 'com.barrybecker4'
                includeGroup 'org.scalatest'
            }
        }
    }

    /**
     * Configure resolution strategy for bb4 projects.
     * Uses Gradle's normal cache behavior (24 hours for dynamic versions, default changing module cache).
     * For explicit SNAPSHOT freshness during CI upstream-update workflows, use --refresh-dependencies
     * (already configured in .github/workflows/ci.yml for repository_dispatch events).
     */
    static void configureResolutionStrategy(Project project) {
        // Gradle defaults:
        // - cacheDynamicVersionsFor: 24 hours
        // - cacheChangingModulesFor: 24 hours
        // These are reasonable for local development and most CI builds.
        // The previous zero-second cache disabled all caching for changing modules
        // and dynamic versions, causing repeated remote metadata checks.
        //
        // For bb4's snapshot cascade workflow (upstream library updates trigger downstream builds),
        // CI explicitly passes --refresh-dependencies on repository_dispatch events, which is
        // the recommended approach for forcing fresh resolution when needed.
    }

    static void configureOssrhDefaults(Project project) {
        def ext = project.extensions.extraProperties
        if (!ext.has('ossrhToken')) ext.set('ossrhToken', '')
        if (!ext.has('ossrhTokenPassword')) ext.set('ossrhTokenPassword', '')
    }

    static void addScalaLibraryDependenciesExceptScalaLibrary(DependencyHandler dependencies) {
        dependencies.add('implementation', "java3d:vecmath:${Bb4DependencyVersions.VECMATH}")
        dependencies.add('testImplementation', "org.junit.jupiter:junit-jupiter:${Bb4DependencyVersions.JUNIT}")
        dependencies.add('testImplementation', "org.scalatest:scalatest_3:${Bb4DependencyVersions.SCALATEST}")
        dependencies.add('testRuntimeOnly', "org.scalatestplus:junit-5-13_3:${Bb4DependencyVersions.SCALATEST_PLUS_JUNIT5}")
        dependencies.add('testRuntimeOnly', "org.scala-lang.modules:scala-xml_3:${Bb4DependencyVersions.SCALA_XML}")
        dependencies.add('testRuntimeOnly', "org.pegdown:pegdown:${Bb4DependencyVersions.PEGDOWN}")
        dependencies.add('testRuntimeOnly', "com.vladsch.flexmark:flexmark-all:${Bb4DependencyVersions.FLEXMARK}")
    }

    private Bb4Conventions() {}
}
