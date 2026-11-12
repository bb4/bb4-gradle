// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

class Bb4BasePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(JavaBasePlugin::class.java)
        project.extensions.getByType(JavaPluginExtension::class.java).toolchain { spec ->
            spec.languageVersion.set(JavaLanguageVersion.of(25))
        }
        val bb4 = project.extensions.findByType(Bb4Extension::class.java)
            ?: project.extensions.create("bb4", Bb4Extension::class.java)
        val baseName = bb4.archivesBaseName.map { name -> name.ifEmpty { project.name } }
        project.extensions.getByType(BasePluginExtension::class.java).archivesName.set(baseName)
        Bb4Conventions.configureRepositories(project.repositories)
        Bb4Conventions.configureResolutionStrategy(project)
        Bb4Conventions.configureOssrhDefaults(project)
    }
}
