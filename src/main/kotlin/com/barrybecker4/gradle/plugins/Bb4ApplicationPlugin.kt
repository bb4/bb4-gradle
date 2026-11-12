// Copyright by Barry G. Becker, 2018–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Bb4ApplicationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(ApplicationPlugin::class.java)

        val distributionDir = "dist"
        val projectDistributionDir = project.layout.projectDirectory.dir("$distributionDir/${project.name}")
        val schemaDistributionDir = project.layout.projectDirectory.dir("$distributionDir/schema")

        project.extensions.extraProperties.set("distributionDir", distributionDir)
        project.extensions.extraProperties.set("projectDistributionDir", "$distributionDir/${project.name}")
        project.extensions.extraProperties.set("schemaDistributionDir", "$distributionDir/schema")

        // Declarative deletes via DirectoryProperty — configuration-cache safe.
        project.tasks.named(LifecycleBasePlugin.CLEAN_TASK_NAME, Delete::class.java).configure { clean ->
            clean.delete(projectDistributionDir, schemaDistributionDir)
        }

        val appExt = project.extensions.getByType(JavaApplication::class.java)

        // Wire bb4.mainClass whenever base (which owns bb4 {}) is present.
        project.pluginManager.withPlugin("com.barrybecker4.bb4.base") {
            val bb4 = project.extensions.getByType(Bb4Extension::class.java)
            appExt.mainClass.set(bb4.mainClass)

            // Register jpackage when the nested jpackage {} block finishes configuring.
            bb4.whenJpackageConfigured {
                registerJpackageTasks(project, bb4)
            }

            // Also cover: enabled set without block, or apps/appMap/jarMap filled after jpackage {}.
            // Legacy ext.appMap is often assigned after the bb4 {} block.
            project.afterEvaluate {
                registerAppTasks(project, bb4, appExt)
                registerJpackageTasks(project, bb4)
            }
        }

        // Application without base can still use legacy ext.appMap.
        project.afterEvaluate {
            if (project.extensions.findByType(Bb4Extension::class.java) == null) {
                registerAppTasks(project, null, appExt)
            }
        }

        project.tasks.named(ApplicationPlugin.TASK_RUN_NAME, JavaExec::class.java).configure { run ->
            run.standardInput = System.`in`
        }

        project.tasks.register("deployHtml", Copy::class.java) { copy ->
            copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            copy.group = "distribution"
            copy.description = "Copy html related files to dist/${project.name}"
            copy.into(projectDistributionDir)

            val versionProvider = project.provider { project.version.toString() }
            val appMapProvider = project.provider { Bb4AppSupport.resolveAppMap(project) }

            copy.inputs.property("versionString", versionProvider)
            copy.inputs.property("appMap", appMapProvider)

            val versionValue = versionProvider.get()
            val appMapValue = appMapProvider.get()

            copy.from("source/html") { spec ->
                spec.include("**/*.*")
                spec.exclude("**/*.png", "**/*.jpg", "**/*.PNG", "**/*.JPG")
                spec.filter(mapOf("tokens" to mapOf("version" to versionValue)), ReplaceTokens::class.java)
                spec.filter { line: String ->
                    if (line.contains("@table_rows@")) {
                        val indent = line.substring(0, line.indexOf("@table_rows@"))
                        createTableRows(appMapValue, indent) ?: line
                    } else {
                        line
                    }
                }
            }
            copy.from("source/html") { spec ->
                spec.include("**/*.png", "**/*.jpg", "**/*.PNG", "**/*.JPG")
            }
        }

        project.tasks.register("deploySchemas", Copy::class.java) { copy ->
            copy.group = "distribution"
            copy.description = "Copy schema files to dist/schema"
            val schemaSource = project.provider {
                val bb4 = project.extensions.findByType(Bb4Extension::class.java)
                if (bb4 != null && bb4.schemaDirectory.isPresent) {
                    bb4.schemaDirectory.get()
                } else {
                    project.layout.projectDirectory.dir("source/schema")
                }
            }
            copy.from(schemaSource) { spec ->
                spec.include("**/*.dtd", "**/*.xsd")
            }
            copy.into(schemaDistributionDir)
            copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        project.tasks.register("deployZip", Copy::class.java) { copy ->
            copy.group = "distribution"
            copy.description = "Copy zip of jars and run scripts"
            val distZipTask = project.tasks.named("distZip", org.gradle.api.tasks.bundling.Zip::class.java)
            copy.from(distZipTask.map { it.archiveFile })
            copy.into(projectDistributionDir)
            copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        project.tasks.register("deploy") { task ->
            task.group = "distribution"
            task.description = "Deploy all web-deployment files to $distributionDir"
            task.dependsOn("build", "deployHtml", "deploySchemas", "deployZip")
        }
    }

    companion object {
        private fun registerAppTasks(project: Project, bb4: Bb4Extension?, appExt: JavaApplication) {
            if (project.extensions.extraProperties.has("bb4.apps.tasks.registered")) {
                return
            }
            val appMap = Bb4AppSupport.resolveAppMap(project)
            if (appMap.isEmpty()) {
                return
            }
            project.extensions.extraProperties.set("bb4.apps.tasks.registered", true)

            val osName = System.getProperty("os.name")
            val mainSourceSet = project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            val jarMap = Bb4JarMap.resolve(project, bb4)
            val jarMapJarTaskNames = jarMap.keys.map { key -> "${key}Jar" }

            if (jarMapJarTaskNames.isNotEmpty()) {
                project.tasks.named("installDist").configure { it.dependsOn(jarMapJarTaskNames) }
                project.tasks.named("startScripts").configure { it.dependsOn(jarMapJarTaskNames) }
            }

            appMap.forEach { (appId, propMap) ->
                val capitalized = Bb4AppSupport.capitalizeAppId(appId)
                val mainClassName = propMap["mainClass"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: bb4?.mainClass?.orNull?.takeIf { it.isNotBlank() }
                    ?: appExt.mainClass.orNull
                    ?: ""
                val description = propMap["description"]?.toString() ?: "Run $appId"
                val jvmArgs = Bb4AppSupport.defaultJvmArgsFromProps(propMap, osName)
                val programArgs = Bb4AppSupport.programArgsFromProps(propMap)
                val applicationName = Bb4AppSupport.applicationNameFor(appId, propMap)

                project.tasks.register("run$capitalized", JavaExec::class.java) { run ->
                    run.group = "application"
                    run.description = description
                    run.mainClass.set(mainClassName)
                    run.classpath = mainSourceSet.runtimeClasspath
                    run.jvmArgs = jvmArgs
                    run.args = programArgs
                    run.standardInput = System.`in`
                }

                if (Bb4AppSupport.shouldCreateStartScript(propMap)) {
                    val createTask = project.tasks.register(
                        "create${capitalized}App",
                        CreateStartScripts::class.java,
                    ) { scripts ->
                        scripts.description = description
                        scripts.mainClass.set(mainClassName)
                        scripts.classpath = project.tasks.named("startScripts", CreateStartScripts::class.java)
                            .get().classpath
                        scripts.outputDir = project.tasks.named("startScripts", CreateStartScripts::class.java)
                            .get().outputDir
                        scripts.applicationName = applicationName
                        scripts.defaultJvmOpts = jvmArgs
                        if (jarMapJarTaskNames.isNotEmpty()) {
                            scripts.dependsOn(jarMapJarTaskNames)
                        }
                    }

                    appExt.applicationDistribution.into("bin") { copy ->
                        copy.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                        copy.from(createTask)
                        copy.filePermissions { perms ->
                            perms.unix("755")
                        }
                    }
                }
            }
        }

        private fun registerJpackageTasks(project: Project, bb4: Bb4Extension) {
            if (project.extensions.extraProperties.has("bb4.jpackage.registered")) {
                return
            }
            val jp = bb4.jpackage
            if (!jp.enabled.getOrElse(false)) {
                return
            }

            val prefix = jp.packageIdentifierPrefix.getOrElse("").trim()
            if (prefix.isEmpty()) {
                throw GradleException(
                    "bb4.jpackage.enabled is true but packageIdentifierPrefix is not set. " +
                        "Example: bb4 { jpackage { packageIdentifierPrefix = 'com.barrybecker4.puzzle' } }",
                )
            }

            val appMap = Bb4AppSupport.resolveAppMap(project)
            if (appMap.isEmpty()) {
                // May still be pending (legacy ext.appMap after bb4 {}); afterEvaluate will retry.
                return
            }

            val jarMap = Bb4JarMap.resolve(project, bb4)
            if (jarMap.isEmpty()) {
                throw GradleException(
                    "bb4.jpackage.enabled is true but jarMap is empty (bb4.jarMap or ext.jarMap)",
                )
            }

            project.extensions.extraProperties.set("bb4.jpackage.registered", true)

            val osName = System.getProperty("os.name")
            val jpackageType = Bb4JpackageSupport.installerTypeForOs(osName)
            val jpackageVersion = Bb4JpackageSupport.sanitizeAppVersion(project.version)
            project.extensions.extraProperties.set("jpackageVersion", jpackageVersion)

            val jarMapJarTaskNames = jarMap.keys.map { key -> "${key}Jar" }

            project.tasks.named("installDist").configure { it.dependsOn(jarMapJarTaskNames) }
            project.tasks.named("startScripts").configure { it.dependsOn(jarMapJarTaskNames) }

            val jpackageBin = if (jp.jpackageExecutable.isPresent) {
                jp.jpackageExecutable.get().asFile
            } else {
                val home = System.getProperty("java.home")
                java.io.File(home, "bin/" + Bb4JpackageSupport.defaultJpackageBinaryName(osName))
            }

            fun syncJarMapIntoInstallLib(installLib: java.io.File) {
                jarMap.keys.forEach { key ->
                    val jarFile = project.tasks.named("${key}Jar", org.gradle.api.tasks.bundling.Jar::class.java)
                        .get().archiveFile.get().asFile
                    if (!jarFile.isFile) {
                        throw GradleException("jpackage: ${jarFile.name} not built; run ${key}Jar first")
                    }
                    val target = java.io.File(installLib, jarFile.name)
                    if (!target.isFile || target.lastModified() < jarFile.lastModified()) {
                        Files.copy(jarFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }

            val jpackageAppIds = appMap.filter { (_, entry) ->
                Bb4JpackageSupport.isDeployable(entry)
            }.keys

            val jpackageTasks = jpackageAppIds.map { appId ->
                val propMap = appMap.getValue(appId)
                val taskName = "jpackage${Bb4AppSupport.capitalizeAppId(appId)}"
                project.tasks.register(taskName, Exec::class.java) { exec ->
                    exec.group = "distribution"
                    exec.description = "Build a native $jpackageType installer for ${propMap["title"]}"
                    exec.dependsOn("installDist")
                    exec.dependsOn(jarMapJarTaskNames)

                    val destDir = project.layout.buildDirectory.dir("jpackage/$appId")
                    exec.outputs.dir(destDir)
                    exec.executable = jpackageBin.absolutePath
                    exec.args("--version")

                    exec.doFirst {
                        destDir.get().asFile.deleteRecursively()
                        destDir.get().asFile.mkdirs()
                        if (!jpackageBin.exists()) {
                            throw GradleException("jpackage not found at $jpackageBin. JDK 14+ is required.")
                        }
                        val iconName = Bb4JpackageSupport.iconFileNameForOs(osName)
                        val icon = project.file("packaging/icons/$appId/$iconName")
                        if (!icon.exists()) {
                            throw GradleException("Missing jpackage icon for $appId: $icon")
                        }
                        project.logger.lifecycle(
                            "jpackage $appId: --app-version $jpackageVersion (raw project.version=${project.version})",
                        )

                        val installLib = project.layout.buildDirectory
                            .dir("install/${project.name}/lib").get().asFile
                        syncJarMapIntoInstallLib(installLib)

                        val mainJarKey = Bb4JpackageSupport.invokeConfigured(
                            jp.mainJarKey,
                            appId,
                            propMap,
                        ) as String
                        if (!jarMap.containsKey(mainJarKey)) {
                            throw GradleException(
                                "jpackage $appId: no jarMap entry for mainJarKey '$mainJarKey'",
                            )
                        }
                        val mainJarName = project.tasks.named(
                            "${mainJarKey}Jar",
                            org.gradle.api.tasks.bundling.Jar::class.java,
                        ).get().archiveFile.get().asFile.name
                        val mainJarFile = java.io.File(installLib, mainJarName)
                        if (!mainJarFile.isFile) {
                            throw GradleException(
                                "jpackage $appId: missing $mainJarName in $installLib. " +
                                    "Run installDist after all jarMap jars are built.",
                            )
                        }

                        val mainClassName = (
                            propMap["mainClass"]
                                ?: jp.defaultMainClass.getOrElse("")
                            ) as String
                        if (mainClassName.isEmpty()) {
                            throw GradleException(
                                "jpackage $appId: no mainClass on app entry and defaultMainClass is unset",
                            )
                        }

                        val displayName = Bb4JpackageSupport.displayNameFor(propMap)
                        @Suppress("UNCHECKED_CAST")
                        val javaOpts = Bb4JpackageSupport.invokeConfigured(
                            jp.javaOptions,
                            appId,
                            propMap,
                        ) as List<*>
                        @Suppress("UNCHECKED_CAST")
                        val appArgs = Bb4JpackageSupport.invokeConfigured(
                            jp.arguments,
                            appId,
                            propMap,
                        ) as List<*>

                        var resourceDirPath: String? = null
                        if (!Bb4JpackageSupport.isMac(osName) &&
                            !Bb4JpackageSupport.isWindows(osName) &&
                            Bb4JpackageSupport.isConsoleApp(propMap)
                        ) {
                            if (!jp.consoleDesktopTemplate.isPresent) {
                                throw GradleException(
                                    "jpackage $appId: console app on Linux requires " +
                                        "bb4.jpackage.consoleDesktopTemplate",
                                )
                            }
                            val template = jp.consoleDesktopTemplate.get().asFile
                            if (!template.isFile) {
                                throw GradleException(
                                    "jpackage $appId: console desktop template not found: $template",
                                )
                            }
                            val resourceDir = project.layout.buildDirectory
                                .dir("jpackage-resources/$appId").get().asFile
                            resourceDir.mkdirs()
                            val desktop = java.io.File(resourceDir, "$displayName.desktop")
                            Files.copy(
                                template.toPath(),
                                desktop.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                            resourceDirPath = resourceDir.absolutePath
                        }

                        val cli = Bb4JpackageSupport.buildCli(
                            mapOf(
                                "appId" to appId,
                                "propMap" to propMap,
                                "installLib" to installLib.absolutePath,
                                "destDir" to destDir.get().asFile.absolutePath,
                                "displayName" to displayName,
                                "appVersion" to jpackageVersion,
                                "description" to Bb4JpackageSupport.sanitizeText(propMap["description"] as String?),
                                "licenseFile" to project.file("LICENSE").absolutePath,
                                "icon" to icon.absolutePath,
                                "mainJarName" to mainJarName,
                                "mainClassName" to mainClassName,
                                "installerType" to jpackageType,
                                "packageIdentifierPrefix" to prefix,
                                "osName" to osName,
                                "javaOptions" to javaOpts,
                                "arguments" to appArgs,
                                "resourceDir" to resourceDirPath,
                            ),
                        )
                        exec.args = cli
                    }

                    exec.doLast {
                        val outDir = destDir.get().asFile
                        val installers = outDir.listFiles()?.filter { f ->
                            f.name.lowercase().endsWith(".$jpackageType")
                        } ?: emptyList()
                        if (installers.size != 1) {
                            throw GradleException(
                                "Expected one .$jpackageType in $outDir, found: ${installers.map { it.name }}",
                            )
                        }
                        val artifactBase = Bb4JpackageSupport.artifactBaseName(appId, jpackageVersion)
                        val target = java.io.File(outDir, "$artifactBase.$jpackageType")
                        val source = installers[0]
                        if (source.canonicalFile != target.canonicalFile) {
                            if (target.exists()) {
                                target.delete()
                            }
                            if (!source.renameTo(target)) {
                                Files.move(
                                    source.toPath(),
                                    target.toPath(),
                                    StandardCopyOption.REPLACE_EXISTING,
                                )
                            }
                            project.logger.lifecycle("Renamed installer -> ${target.name}")
                        }
                    }
                }
                taskName
            }

            project.tasks.register("jpackageAllApps") { task ->
                task.group = "distribution"
                task.description = "Build native $jpackageType installers for every deployable app"
                task.dependsOn(jpackageTasks)
            }
        }

        @JvmStatic
        fun createTableRows(appMapValue: Map<String, Map<String, Any?>>?, indent: String): String? {
            if (appMapValue.isNullOrEmpty()) {
                return null
            }
            val rows = StringBuilder()
            appMapValue.forEach { (key, value) ->
                val imageName = if (value.containsKey("imageName")) value["imageName"].toString() else "$key.jpg"
                val deploy = if (value.containsKey("deploy")) value["deploy"] as Boolean else true
                if (deploy) {
                    val title = value["title"].toString()
                    val command = value["command"].toString()
                    val longDescription = value["longDescription"].toString()
                    rows.append(indent).append("<tr>\n")
                    rows.append(indent).append("  <td>\n")
                    rows.append(indent).append("    <a name=\"$key\"><h3>$title</h3></a>\n")
                    rows.append(indent).append("    <pre>$command</pre>\n")
                    rows.append(indent).append("    <p>$longDescription</p>\n")
                    rows.append(indent).append("  </td>\n")
                    rows.append(indent).append("  <td style=\"vertical-align: top;\">\n")
                    rows.append(indent).append("    <img src=\"images/$imageName\" title=\"$title\" alt=\"$title\">\n")
                    rows.append(indent).append("  </td>\n")
                    rows.append(indent).append("</tr>\n")
                }
            }
            return rows.toString()
        }
    }
}
