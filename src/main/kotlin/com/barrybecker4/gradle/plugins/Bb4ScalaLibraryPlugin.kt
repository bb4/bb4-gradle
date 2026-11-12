// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

class Bb4ScalaLibraryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(Bb4BasePlugin::class.java)
        val bb4 = project.extensions.getByType(Bb4Extension::class.java)

        project.pluginManager.apply(JavaLibraryPlugin::class.java)
        project.pluginManager.apply(ScalaPlugin::class.java)

        project.pluginManager.apply("project-report")
        project.pluginManager.apply("idea")
        project.pluginManager.apply("eclipse")

        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        sourceSets.named("main").configure { main: SourceSet ->
            main.java.setSrcDirs(listOf("source"))
            main.extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java)
                .setSrcDirs(listOf("scala-source"))
            main.resources.setSrcDirs(listOf("source", "scala-source"))
        }
        sourceSets.named("test").configure { test: SourceSet ->
            test.java.setSrcDirs(listOf("test"))
            test.extensions.getByType(org.gradle.api.tasks.ScalaSourceDirectorySet::class.java)
                .setSrcDirs(listOf("scala-test"))
            test.resources.setSrcDirs(listOf("test", "scala-test"))
        }

        project.dependencies.add(
            "implementation",
            bb4.scalaVersion.map { "org.scala-lang:scala3-library_3:$it" },
        )
        Bb4Conventions.addScalaLibraryDependenciesExceptScalaLibrary(project.dependencies)

        project.tasks.withType(Test::class.java).configureEach { test ->
            test.useJUnitPlatform {
                it.includeEngines("scalatest", "junit-jupiter")
            }
        }

        project.tasks.register("copyToLib", Copy::class.java) { task ->
            task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            task.description = "Copies runtime dependencies into build/libs for deployment"
            task.into(project.layout.buildDirectory.dir("libs"))
            task.from(project.configurations.named("runtimeClasspath"))
            task.dependsOn(project.tasks.named("jar"))
        }

        val baseName = bb4.archivesBaseName.map { name ->
            name.ifEmpty { project.name }
        }
        project.extensions.getByType(BasePluginExtension::class.java).archivesName.set(baseName)

        project.tasks.named("jar", Jar::class.java).configure { jar ->
            jar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            jar.archiveFileName.set(
                baseName.flatMap { name ->
                    project.provider { "$name-${project.version}.jar" }
                },
            )
            jar.manifest { manifest ->
                manifest.attributes(
                    mapOf(
                        "Implementation-Title" to (project.description ?: project.name),
                        "Implementation-Version" to project.provider { project.version.toString() },
                        "Build-By" to "Barry G Becker",
                        "Main-Class" to bb4.mainClass,
                        "Build-Jdk" to
                            "${System.getProperty("java.version")} (${System.getProperty("java.vendor")} ${System.getProperty("java.vm.version")})",
                        "provider" to "gradle",
                    ),
                )
            }
        }

        project.defaultTasks = listOf("build")
    }
}
