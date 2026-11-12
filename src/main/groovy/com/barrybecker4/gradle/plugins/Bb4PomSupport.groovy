// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

/**
 * Shared POM / coordinate helpers for Central publishing.
 * Uses {@code project.group}, {@code project.description}, and optional {@code bb4.repository}.
 */
final class Bb4PomSupport {

    private Bb4PomSupport() {}

    static String repositoryName(Project project) {
        def explicit = project.findProperty('bb4.repository')?.toString()?.trim()
        if (explicit) {
            return explicit
        }
        return project.name
    }

    static String githubUrl(Project project) {
        return "https://github.com/bb4/${repositoryName(project)}"
    }

    static String scmConnection(Project project) {
        return "scm:git:https://github.com/bb4/${repositoryName(project)}.git"
    }

    static String scmDeveloperConnection(Project project) {
        return "scm:git:ssh://git@github.com/bb4/${repositoryName(project)}.git"
    }

    static String pomDescription(Project project) {
        def desc = project.description?.toString()?.trim()
        if (desc) {
            return desc
        }
        return project.name
    }

    static String groupId(Project project) {
        return project.group?.toString() ?: ''
    }

    /**
     * Apply Central-required POM metadata (name, description, URL, SCM, license, developers).
     * Does not set groupId/artifactId/version — callers set those on the publication.
     *
     * @param includeRuntimeDependencies when true (jarMap pubs), inject implementation/runtimeOnly deps
     */
    static void configurePom(MavenPublication pub, Project project, boolean includeRuntimeDependencies = false) {
        def repoUrl = githubUrl(project)
        def desc = pomDescription(project)

        pub.pom {
            name = project.name
            packaging = 'jar'
            description = desc
            url = repoUrl
            scm {
                url = repoUrl
                connection = scmConnection(project)
                developerConnection = scmDeveloperConnection(project)
            }
            licenses {
                license {
                    name = 'The MIT license'
                    url = 'http://www.opensource.org/licenses/MIT'
                    distribution = 'repo'
                }
            }
            developers {
                developer {
                    id = 'barrybecker4'
                    name = 'Barry G. Becker'
                    email = 'barrybecker4@gmail.com'
                }
            }

            if (includeRuntimeDependencies) {
                withXml {
                    def dependenciesNode = asNode().appendNode('dependencies')
                    def seenDeps = new HashSet()

                    project.configurations.implementation.allDependencies.each { dep ->
                        if (dep.group != null && dep.name != null && dep.name != 'unspecified') {
                            def key = "${dep.group}:${dep.name}"
                            if (!seenDeps.contains(key)) {
                                seenDeps.add(key)
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', dep.group)
                                dependencyNode.appendNode('artifactId', dep.name)
                                dependencyNode.appendNode('version', dep.version)
                                dependencyNode.appendNode('scope', 'compile')
                            }
                        }
                    }

                    project.configurations.runtimeOnly.allDependencies.each { dep ->
                        if (dep.group != null && dep.name != null && dep.name != 'unspecified') {
                            def key = "${dep.group}:${dep.name}"
                            if (!seenDeps.contains(key)) {
                                seenDeps.add(key)
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', dep.group)
                                dependencyNode.appendNode('artifactId', dep.name)
                                dependencyNode.appendNode('version', dep.version)
                                dependencyNode.appendNode('scope', 'runtime')
                            }
                        }
                    }
                }
            }
        }
    }
}
