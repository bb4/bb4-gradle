// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

class Bb4BasePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaBasePlugin)
        project.extensions.getByType(JavaPluginExtension).toolchain { spec ->
            spec.languageVersion.set(JavaLanguageVersion.of(21))
        }
        Bb4Conventions.configureRepositories(project.repositories)
        Bb4Conventions.configureResolutionStrategy(project)
        Bb4Conventions.configureOssrhDefaults(project)
    }
}
