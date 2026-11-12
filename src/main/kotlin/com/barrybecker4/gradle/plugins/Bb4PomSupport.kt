// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.publish.maven.MavenPublication

/**
 * Shared POM / coordinate helpers for Central publishing.
 * Uses `project.group`, `project.description`, and `bb4.repository` (extension or property).
 */
object Bb4PomSupport {

    @JvmStatic
    fun repositoryName(project: Project): String {
        val bb4 = project.extensions.findByType(Bb4Extension::class.java)
        val fromExtension = bb4?.repository?.orNull?.trim()
        if (!fromExtension.isNullOrEmpty()) {
            return fromExtension
        }
        val explicit = project.findProperty("bb4.repository")?.toString()?.trim()
        if (!explicit.isNullOrEmpty()) {
            return explicit
        }
        return project.name
    }

    @JvmStatic
    fun githubUrl(project: Project): String =
        "https://github.com/bb4/${repositoryName(project)}"

    @JvmStatic
    fun scmConnection(project: Project): String =
        "scm:git:https://github.com/bb4/${repositoryName(project)}.git"

    @JvmStatic
    fun scmDeveloperConnection(project: Project): String =
        "scm:git:ssh://git@github.com/bb4/${repositoryName(project)}.git"

    @JvmStatic
    fun pomDescription(project: Project): String {
        val desc = project.description?.trim()
        return if (!desc.isNullOrEmpty()) desc else project.name
    }

    @JvmStatic
    fun groupId(project: Project): String =
        project.group.toString()

    /**
     * Apply Central-required POM metadata.
     * @param includeRuntimeDependencies when true (jarMap pubs), inject implementation/runtimeOnly deps
     */
    @JvmStatic
    @JvmOverloads
    fun configurePom(
        pub: MavenPublication,
        project: Project,
        includeRuntimeDependencies: Boolean = false,
    ) {
        pub.pom { pom ->
            pom.name.set(project.name)
            pom.packaging = "jar"
            // Providers so bb4.repository / description set after plugins {} are visible.
            pom.description.set(project.provider { pomDescription(project) })
            pom.url.set(project.provider { githubUrl(project) })
            pom.scm { scm ->
                scm.url.set(project.provider { githubUrl(project) })
                scm.connection.set(project.provider { scmConnection(project) })
                scm.developerConnection.set(project.provider { scmDeveloperConnection(project) })
            }
            pom.licenses { licenses ->
                licenses.license { license ->
                    license.name.set("The MIT license")
                    license.url.set("http://www.opensource.org/licenses/MIT")
                    license.distribution.set("repo")
                }
            }
            pom.developers { developers ->
                developers.developer { developer ->
                    developer.id.set("barrybecker4")
                    developer.name.set("Barry G. Becker")
                    developer.email.set("barrybecker4@gmail.com")
                }
            }

            if (includeRuntimeDependencies) {
                pom.withXml { xml ->
                    val dependenciesNode = xml.asNode().appendNode("dependencies")
                    val seenDeps = HashSet<String>()

                    fun addDeps(configurationName: String, scope: String) {
                        val configuration = project.configurations.findByName(configurationName) ?: return
                        configuration.allDependencies.forEach { dep: Dependency ->
                            if (dep.group != null && dep.name != "unspecified") {
                                val key = "${dep.group}:${dep.name}"
                                if (seenDeps.add(key)) {
                                    val dependencyNode = dependenciesNode.appendNode("dependency")
                                    dependencyNode.appendNode("groupId", dep.group)
                                    dependencyNode.appendNode("artifactId", dep.name)
                                    dependencyNode.appendNode("version", dep.version)
                                    dependencyNode.appendNode("scope", scope)
                                }
                            }
                        }
                    }

                    addDeps("implementation", "compile")
                    addDeps("runtimeOnly", "runtime")
                }
            }
        }
    }
}
