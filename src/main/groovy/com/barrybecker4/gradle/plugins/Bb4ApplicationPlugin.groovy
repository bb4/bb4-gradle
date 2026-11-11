// Copyright by Barry G. Becker, 2018–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.language.base.plugins.LifecycleBasePlugin

import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
            project.afterEvaluate {
                registerJpackageTasks(project, bb4)
            }
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

    private static void registerJpackageTasks(Project project, Bb4Extension bb4) {
        def jp = bb4.jpackage
        if (!jp.enabled.getOrElse(false)) {
            return
        }

        def prefix = jp.packageIdentifierPrefix.getOrElse('').trim()
        if (!prefix) {
            throw new GradleException(
                "bb4.jpackage.enabled is true but packageIdentifierPrefix is not set. " +
                    "Example: bb4 { jpackage { packageIdentifierPrefix = 'com.barrybecker4.puzzle' } }")
        }

        def rawAppMap = project.findProperty('appMap')
        if (!(rawAppMap instanceof Map) || rawAppMap.isEmpty()) {
            throw new GradleException(
                'bb4.jpackage.enabled is true but ext.appMap is missing or empty')
        }
        Map<String, Object> appMap = rawAppMap as Map<String, Object>

        def jarMap = Bb4JarMap.resolve(project, bb4)
        if (jarMap.isEmpty()) {
            throw new GradleException(
                'bb4.jpackage.enabled is true but jarMap is empty (bb4.jarMap or ext.jarMap)')
        }

        def osName = System.getProperty('os.name')
        def jpackageType = Bb4JpackageSupport.installerTypeForOs(osName)
        def jpackageVersion = Bb4JpackageSupport.sanitizeAppVersion(project.version)
        project.extensions.extraProperties.set('jpackageVersion', jpackageVersion)

        def jarMapJarTaskNames = jarMap.keySet().collect { String key -> "${key}Jar" }

        // jarMap disables the default :jar task; installDist/startScripts need the split jars.
        project.tasks.named('installDist').configure { it.dependsOn jarMapJarTaskNames }
        project.tasks.named('startScripts').configure { it.dependsOn jarMapJarTaskNames }

        def jpackageBin
        if (jp.jpackageExecutable.present) {
            jpackageBin = jp.jpackageExecutable.get().asFile
        } else {
            def home = System.getProperty('java.home')
            jpackageBin = new File(home, 'bin/' + Bb4JpackageSupport.defaultJpackageBinaryName(osName))
        }

        def syncJarMapIntoInstallLib = { File installLib ->
            jarMap.keySet().each { String key ->
                def jarFile = project.tasks.named("${key}Jar").get().archiveFile.get().asFile
                if (!jarFile.isFile()) {
                    throw new GradleException("jpackage: ${jarFile.name} not built; run ${key}Jar first")
                }
                def target = new File(installLib, jarFile.name)
                if (!target.isFile() || target.lastModified() < jarFile.lastModified()) {
                    Files.copy(
                        jarFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        def jpackageAppIds = appMap.findAll { String ignored, Object entry ->
            Bb4JpackageSupport.isDeployable(entry as Map)
        }.keySet()

        def jpackageTasks = jpackageAppIds.collect { String appId ->
            Map propMap = appMap.get(appId) as Map
            def taskName = "jpackage${appId.capitalize()}"
            project.tasks.register(taskName, Exec) { Exec exec ->
                exec.group = 'distribution'
                exec.description = "Build a native ${jpackageType} installer for ${propMap.get('title')}"
                exec.dependsOn 'installDist', jarMapJarTaskNames

                def destDir = project.layout.buildDirectory.dir("jpackage/${appId}")
                exec.outputs.dir(destDir)
                exec.executable = jpackageBin.absolutePath
                exec.args '--version'

                exec.doFirst {
                    destDir.get().asFile.deleteDir()
                    destDir.get().asFile.mkdirs()
                    if (!jpackageBin.exists()) {
                        throw new GradleException(
                            "jpackage not found at ${jpackageBin}. JDK 14+ is required.")
                    }
                    def iconName = Bb4JpackageSupport.iconFileNameForOs(osName)
                    def icon = project.file("packaging/icons/${appId}/${iconName}")
                    if (!icon.exists()) {
                        throw new GradleException("Missing jpackage icon for ${appId}: ${icon}")
                    }
                    project.logger.lifecycle(
                        "jpackage ${appId}: --app-version ${jpackageVersion} (raw project.version=${project.version})")

                    def installLib = project.layout.buildDirectory
                        .dir("install/${project.name}/lib").get().asFile
                    syncJarMapIntoInstallLib(installLib)

                    def mainJarKey = Bb4JpackageSupport.invokeConfigured(
                        jp.mainJarKey, appId, propMap) as String
                    if (!jarMap.containsKey(mainJarKey)) {
                        throw new GradleException(
                            "jpackage ${appId}: no jarMap entry for mainJarKey '${mainJarKey}'")
                    }
                    def mainJarName = project.tasks.named("${mainJarKey}Jar")
                        .get().archiveFile.get().asFile.name
                    def mainJarFile = new File(installLib, mainJarName)
                    if (!mainJarFile.isFile()) {
                        throw new GradleException(
                            "jpackage ${appId}: missing ${mainJarName} in ${installLib}. " +
                                "Run installDist after all jarMap jars are built.")
                    }

                    def mainClassName = (propMap.get('mainClass')
                        ?: jp.defaultMainClass.getOrElse('')) as String
                    if (!mainClassName) {
                        throw new GradleException(
                            "jpackage ${appId}: no mainClass on appMap entry and defaultMainClass is unset")
                    }

                    def displayName = Bb4JpackageSupport.displayNameFor(propMap)
                    def javaOpts = Bb4JpackageSupport.invokeConfigured(
                        jp.javaOptions, appId, propMap) as List
                    def appArgs = Bb4JpackageSupport.invokeConfigured(
                        jp.arguments, appId, propMap) as List

                    String resourceDirPath = null
                    if (!Bb4JpackageSupport.isMac(osName)
                        && !Bb4JpackageSupport.isWindows(osName)
                        && Bb4JpackageSupport.isConsoleApp(propMap)) {
                        if (!jp.consoleDesktopTemplate.present) {
                            throw new GradleException(
                                "jpackage ${appId}: console app on Linux requires " +
                                    "bb4.jpackage.consoleDesktopTemplate")
                        }
                        def template = jp.consoleDesktopTemplate.get().asFile
                        if (!template.isFile()) {
                            throw new GradleException(
                                "jpackage ${appId}: console desktop template not found: ${template}")
                        }
                        def resourceDir = project.layout.buildDirectory
                            .dir("jpackage-resources/${appId}").get().asFile
                        resourceDir.mkdirs()
                        def desktop = new File(resourceDir, "${displayName}.desktop")
                        Files.copy(
                            template.toPath(),
                            desktop.toPath(),
                            StandardCopyOption.REPLACE_EXISTING)
                        resourceDirPath = resourceDir.absolutePath
                    }

                    def cli = Bb4JpackageSupport.buildCli(
                        appId: appId,
                        propMap: propMap,
                        installLib: installLib.absolutePath,
                        destDir: destDir.get().asFile.absolutePath,
                        displayName: displayName,
                        appVersion: jpackageVersion,
                        description: Bb4JpackageSupport.sanitizeText(propMap.get('description') as String),
                        licenseFile: project.file('LICENSE').absolutePath,
                        icon: icon.absolutePath,
                        mainJarName: mainJarName,
                        mainClassName: mainClassName,
                        installerType: jpackageType,
                        packageIdentifierPrefix: prefix,
                        osName: osName,
                        javaOptions: javaOpts,
                        arguments: appArgs,
                        resourceDir: resourceDirPath,
                    )
                    exec.args = cli
                }

                exec.doLast {
                    def outDir = destDir.get().asFile
                    def installers = outDir.listFiles()?.findAll { File f ->
                        f.name.toLowerCase().endsWith(".${jpackageType}")
                    } ?: []
                    if (installers.size() != 1) {
                        throw new GradleException(
                            "Expected one .${jpackageType} in ${outDir}, found: ${installers*.name}")
                    }
                    def artifactBase = Bb4JpackageSupport.artifactBaseName(appId, jpackageVersion)
                    def target = new File(outDir, "${artifactBase}.${jpackageType}")
                    def source = installers[0]
                    if (source.canonicalFile != target.canonicalFile) {
                        if (target.exists()) {
                            target.delete()
                        }
                        if (!source.renameTo(target)) {
                            Files.move(
                                source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        project.logger.lifecycle("Renamed installer -> ${target.name}")
                    }
                }
            }
            return taskName
        }

        project.tasks.register('jpackageAllApps') {
            group = 'distribution'
            description = "Build native ${jpackageType} installers for every deployable app"
            dependsOn jpackageTasks
        }
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
