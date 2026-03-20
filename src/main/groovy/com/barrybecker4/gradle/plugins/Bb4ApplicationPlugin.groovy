// Copyright by Barry G. Becker, 2018–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.application.tasks.CreateStartScripts

import java.io.File
import java.util.regex.Matcher

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

        def bb4 = project.extensions.findByType(Bb4Extension)
        def appExt = project.extensions.getByName('application')
        if (bb4 != null) {
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
            copy.from('source/html') {
                include '**/*.*'
                exclude '**/*.png', '**/*.jpg', '**/*.PNG', '**/*.JPG'
                filter ReplaceTokens, tokens: [version: project.version.toString()]
                filter { String line ->
                    line.contains('@table_rows@')
                        ? createTableRows(project, line.substring(0, line.indexOf('@table_rows@'))) ?: line
                        : line
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
            copy.dependsOn project.tasks.named('build')
            copy.description = 'Copy zip of jars and run scripts'
            copy.from 'build/distributions'
            copy.into projectDistributionDir
            copy.include 'bb4-*.zip'
            copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        project.tasks.register('deploy') {
            group = 'distribution'
            description = "Deploy all web-deployment files to $distributionDir"
            dependsOn 'build', 'deployHtml', 'deploySchemas', 'deployZip'
        }

        project.tasks.withType(CreateStartScripts).configureEach { CreateStartScripts scripts ->
            scripts.doLast {
                def win = startScriptOutputFile(scripts.windowsScript)
                win.text = win.text.replaceFirst(/(set CLASSPATH=%APP_HOME%\\lib\\).*/, '$1*')
                def unix = startScriptOutputFile(scripts.unixScript)
                // String pattern (not slashy) avoids $APP_HOME being resolved on the task delegate; quote the
                // replacement so $ in the shell script is not treated as a Matcher group back-reference.
                unix.text = unix.text.replaceFirst(
                    '(CLASSPATH=)(\\$APP_HOME/lib/).*',
                    Matcher.quoteReplacement('CLASSPATH=$(echo $APP_HOME/lib/*.jar | tr \' \' \':\')'),
                )
            }
        }

        appExt.applicationDistribution.from('build/libs') {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            exclude '*.asc'
            exclude '*-sources.jar'
            exclude '*-scaladoc.jar'
            exclude '*-javadoc.jar'
            into 'lib'
        }
    }

    /** Gradle 8.14+ exposes script locations as {@link File}; older versions used a lazy {@code RegularFileProperty}. */
    private static File startScriptOutputFile(Object windowsOrUnixScript) {
        if (windowsOrUnixScript instanceof File) {
            return windowsOrUnixScript as File
        }
        def located = windowsOrUnixScript.get()
        return located instanceof File ? located as File : located.asFile as File
    }

    private static String createTableRows(Project project, String indent) {
        if (!project.hasProperty('appMap')) {
            return null
        }
        def appMap = project.property('appMap')
        if (!(appMap instanceof Map)) {
            return null
        }
        def rows = new StringBuilder()
        appMap.each { String key, Map value ->
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
