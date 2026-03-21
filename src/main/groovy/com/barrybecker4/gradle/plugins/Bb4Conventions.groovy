// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler

final class Bb4Conventions {

    static void configureRepositories(RepositoryHandler repositories) {
        repositories.mavenCentral()
        repositories.maven { url = 'https://repo1.maven.org/maven2/' }
        repositories.maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
        repositories.maven { url = 'https://repository.jboss.org/nexus/content/repositories/thirdparty-releases' }
    }

    static void configureResolutionStrategy(Project project) {
        project.configurations.configureEach { cfg ->
            cfg.resolutionStrategy {
                if (!project.hasProperty('debugDeps')) {
                    cacheDynamicVersionsFor 0, 'seconds'
                    cacheChangingModulesFor 0, 'seconds'
                }
            }
        }
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
        dependencies.add('testRuntimeOnly', "org.scala-lang.modules:scala-xml_3:${Bb4DependencyVersions.SCALA_XML}")
        dependencies.add('testRuntimeOnly', "org.pegdown:pegdown:${Bb4DependencyVersions.PEGDOWN}")
        dependencies.add('testRuntimeOnly', "com.vladsch.flexmark:flexmark-all:${Bb4DependencyVersions.FLEXMARK}")
    }

    private Bb4Conventions() {}
}
