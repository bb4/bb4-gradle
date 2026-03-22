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
import org.gradle.language.base.plugins.LifecycleBasePlugin

class Bb4ScalaLibraryPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply(Bb4BasePlugin)
        def bb4 = project.extensions.create('bb4', Bb4Extension)

        project.pluginManager.apply(JavaLibraryPlugin)
        project.pluginManager.apply(ScalaPlugin)

        project.pluginManager.apply('project-report')
        project.pluginManager.apply('idea')
        project.pluginManager.apply('eclipse')

        def sourceSets = project.extensions.getByType(SourceSetContainer)
        sourceSets.named('main').configure { SourceSet main ->
            main.java.srcDirs = ['source']
            main.scala.srcDirs = ['scala-source']
            main.resources.srcDirs = ['source', 'scala-source']
        }
        sourceSets.named('test').configure { SourceSet test ->
            test.java.srcDirs = ['test']
            test.scala.srcDirs = ['scala-test']
            test.resources.srcDirs = ['test', 'scala-test']
        }

        project.dependencies.add('implementation', bb4.scalaVersion.map { "org.scala-lang:scala3-library_3:$it" })
        Bb4Conventions.addScalaLibraryDependenciesExceptScalaLibrary(project.dependencies)

        project.tasks.named(LifecycleBasePlugin.CLEAN_TASK_NAME).configure { clean ->
            clean.doFirst {
                project.delete "${project.rootProject.projectDir}/dist"
            }
        }

        project.tasks.withType(Test).configureEach { Test test ->
            test.useJUnitPlatform {
                includeEngines 'scalatest', 'junit-jupiter'
            }
        }

        project.tasks.register('copyToLib', Copy) { Copy task ->
            task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            task.description = 'Copies runtime dependencies into build/libs for deployment'
            task.into 'build/libs'
            task.from project.configurations.named('runtimeClasspath')
            task.dependsOn project.tasks.named('jar')
        }

        project.afterEvaluate {
            def baseName = bb4.archivesBaseName.get() ?: project.name
            project.extensions.getByType(BasePluginExtension).archivesName.set(baseName)
            def mainClassValue = bb4.mainClass.get()
            project.tasks.named('jar', Jar).configure { Jar jar ->
                jar.archiveFileName.set("${baseName}-${project.version}.jar")
                jar.manifest {
                    it.attributes(
                        'Implementation-Title': project.description ?: project.name,
                        'Implementation-Version': project.version.toString(),
                        'Build-By': 'Barry G Becker',
                        'Main-Class': mainClassValue,
                        'Build-Jdk': "${System.getProperty('java.version')} (${System.getProperty('java.vendor')} ${System.getProperty('java.vm.version')})",
                        provider: 'gradle',
                    )
                }
            }
        }

        project.defaultTasks = ['build']
    }
}
