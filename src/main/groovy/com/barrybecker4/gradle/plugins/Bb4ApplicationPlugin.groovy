// Copyright by Barry G. Becker, 2018–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.language.base.plugins.LifecycleBasePlugin

class Bb4ApplicationPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply(ApplicationPlugin)

        def distributionDir = 'dist'
        def projectDistributionDir = "$distributionDir/${project.name}"
        def schemaDistributionDir = "$distributionDir/schema"

        project.extensions.extraProperties.set('distributionDir', distributionDir)
        project.extensions.extraProperties.set('projectDistributionDir', projectDistributionDir)
        project.extensions.extraProperties.set('schemaDistributionDir', schemaDistributionDir)

        project.tasks.named(LifecycleBasePlugin.CLEAN_TASK_NAME).configure { clean ->
            clean.doFirst {
                project.delete project.file(projectDistributionDir)
                project.delete project.file(schemaDistributionDir)
            }
        }

        def appExt = project.extensions.getByName('application')
        
        // Wire bb4.mainClass to application.mainClass when bb4.scala-library is applied,
        // regardless of plugin application order
        project.pluginManager.withPlugin('com.barrybecker4.bb4.scala-library') {
            def bb4 = project.extensions.getByType(Bb4Extension)
            appExt.mainClass.set(bb4.mainClass)
        }

        project.tasks.named(ApplicationPlugin.TASK_RUN_NAME, JavaExec).configure { JavaExec run ->
            run.standardInput = System.in
        }

        project.tasks.register('deployHtml', Copy) { Copy copy ->
            copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            copy.group = 'distribution'
            copy.description = "Copy html related files to $projectDistributionDir"
            copy.into projectDistributionDir

            def versionProvider = project.provider { project.version.toString() }
            def appMapProvider = project.provider {
                def raw = project.findProperty('appMap')
                (raw instanceof Map) ? raw as Map<String, Map> : Collections.emptyMap()
            }

            copy.inputs.property('versionString', versionProvider)
            copy.inputs.property('appMap', appMapProvider)

            // Resolve values during configuration and capture them as plain objects
            def versionValue = versionProvider.get()
            def appMapValue = appMapProvider.get()

            copy.from('source/html') {
                include '**/*.*'
                exclude '**/*.png', '**/*.jpg', '**/*.PNG', '**/*.JPG'
                filter ReplaceTokens, tokens: [version: versionValue]
                filter { String line ->
                    if (line.contains('@table_rows@')) {
                        def indent = line.substring(0, line.indexOf('@table_rows@'))
                        def rows = Bb4ApplicationPlugin.createTableRows(appMapValue, indent)
                        return rows ?: line
                    }
                    return line
                }
            }
            copy.from('source/html') {
                include '**/*.png', '**/*.jpg', '**/*.PNG', '**/*.JPG'
            }
        }

        project.tasks.register('deploySchemas', Copy) { Copy copy ->
            copy.group = 'distribution'
            copy.description = "Copy schema files to $schemaDistributionDir"
            copy.from project.fileTree(dir: project.projectDir, include: ['**/*.dtd', '**/*.xsd'])
            copy.into schemaDistributionDir
            copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        project.tasks.register('deployZip', Copy) { Copy copy ->
            copy.group = 'distribution'
            copy.description = 'Copy zip of jars and run scripts'
            def distZipTask = project.tasks.named('distZip')
            copy.from distZipTask.map { it.archiveFile }
            copy.into projectDistributionDir
            copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        project.tasks.register('deploy') {
            group = 'distribution'
            description = "Deploy all web-deployment files to $distributionDir"
            dependsOn 'build', 'deployHtml', 'deploySchemas', 'deployZip'
        }

        // Removed custom classpath manipulation: Gradle's default CreateStartScripts generates a properly
        // quoted explicit classpath that works correctly with spaces in paths and JAR names. The previous
        // wildcard replacement broke paths containing spaces and picked up unintended JARs from lib/.

    }

    private static String createTableRows(Map<String, Map> appMapValue, String indent) {
        if (appMapValue == null || appMapValue.isEmpty()) {
            return null
        }
        def rows = new StringBuilder()
        appMapValue.each { String key, Map value ->
            def imageName = value.containsKey('imageName') ? value.imageName.toString() : "${key}.jpg"
            def deploy = value.containsKey('deploy') ? value.deploy as boolean : true
            if (deploy) {
                def title = value.title.toString()
                def command = value.command.toString()
                def longDescription = value.longDescription.toString()
                rows << indent << '<tr>\n'
                rows << indent << '  <td>\n'
                rows << indent << "    <a name=\"${key}\"><h3>${title}</h3></a>\n"
                rows << indent << "    <pre>${command}</pre>\n"
                rows << indent << "    <p>${longDescription}</p>\n"
                rows << indent << '  </td>\n'
                rows << indent << '  <td style="vertical-align: top;">\n'
                rows << indent << "    <img src=\"images/${imageName}\" title=\"${title}\" alt=\"${title}\">\n"
                rows << indent << '  </td>\n'
                rows << indent << '</tr>\n'
            }
        }
        return rows.toString()
    }
}
