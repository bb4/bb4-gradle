// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import groovy.lang.Closure

/**
 * Pure helpers for jpackage CLI assembly and naming. Unit-tested without invoking jpackage.
 */
object Bb4JpackageSupport {

    const val ADD_MODULES =
        "java.desktop,java.logging,java.xml,java.management,java.naming,java.prefs,java.datatransfer,jdk.unsupported,jdk.crypto.ec,jdk.localedata"

    const val JLINK_OPTIONS =
        "--strip-debug --no-header-files --no-man-pages --ignore-signing-information"

    const val VENDOR = "Barry Becker"
    const val LINUX_DEB_MAINTAINER = "barrybecker4@gmail.com"

    @JvmStatic
    fun sanitizeText(text: String?): String =
        (text ?: "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("[\"']"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * jpackage --app-version rejects -SNAPSHOT and other non x[.y[.z]] strings.
     */
    @JvmStatic
    fun sanitizeAppVersion(projectVersion: Any?): String {
        val raw = projectVersion?.toString() ?: ""
        val stripped = raw.replace(Regex("-SNAPSHOT$"), "")
        return if (stripped.matches(Regex("\\d+(\\.\\d+){0,2}"))) stripped else "0.0.1"
    }

    @JvmStatic
    fun displayNameFor(propMap: Map<*, *>): String =
        (propMap["title"] as? String)?.replace(Regex("['\"]"), "")?.trim() ?: ""

    @JvmStatic
    fun linuxPackageNameFor(appId: String): String =
        "bb4-${appId.lowercase()}"

    @JvmStatic
    fun macPackageNameFor(propMap: Map<*, *>): String {
        if (propMap.containsKey("macPackageName")) {
            val explicit = (propMap["macPackageName"] as String).trim()
            if (explicit.length > 16) {
                throw IllegalArgumentException("macPackageName exceeds 16 characters: '$explicit'")
            }
            return explicit
        }
        val cleaned = (propMap["title"] as? String)?.replace(Regex("[^A-Za-z0-9 ]"), "")?.trim() ?: ""
        return if (cleaned.length <= 16) cleaned else cleaned.substring(0, 16).trim()
    }

    @JvmStatic
    fun isConsoleApp(propMap: Map<*, *>): Boolean =
        propMap.containsKey("console") && propMap["console"] == true

    @JvmStatic
    fun isDeployable(propMap: Map<*, *>): Boolean =
        !(propMap.containsKey("deploy") && propMap["deploy"] == false)

    @JvmStatic
    fun installerTypeForOs(osName: String?): String {
        val lower = (osName ?: "").lowercase()
        return when {
            lower.contains("win") -> "msi"
            lower.contains("mac") || lower.contains("darwin") -> "dmg"
            else -> "deb"
        }
    }

    @JvmStatic
    fun isWindows(osName: String?): Boolean =
        (osName ?: "").lowercase().contains("win")

    @JvmStatic
    fun isMac(osName: String?): Boolean {
        val lower = (osName ?: "").lowercase()
        return lower.contains("mac") || lower.contains("darwin")
    }

    @JvmStatic
    fun iconFileNameForOs(osName: String?): String = when {
        isWindows(osName) -> "icon.ico"
        isMac(osName) -> "icon.icns"
        else -> "icon.png"
    }

    @JvmStatic
    fun defaultJpackageBinaryName(osName: String?): String =
        if (isWindows(osName)) "jpackage.exe" else "jpackage"

    /**
     * Invoke a configured closure or callable with (appId, props).
     */
    @JvmStatic
    fun invokeConfigured(configured: Any?, appId: String, props: Map<*, *>): Any? {
        if (configured == null) {
            return null
        }
        if (configured is Closure<*>) {
            return configured.call(appId, props)
        }
        if (configured is Function2<*, *, *>) {
            @Suppress("UNCHECKED_CAST")
            return (configured as Function2<String, Map<*, *>, Any?>).invoke(appId, props)
        }
        // Kotlin lambda / SAM may arrive as kotlin.jvm.functions.Function2
        try {
            val call = configured.javaClass.methods.firstOrNull { method ->
                method.name == "invoke" && method.parameterCount == 2
            }
            if (call != null) {
                return call.invoke(configured, appId, props)
            }
            val callMethod = configured.javaClass.methods.firstOrNull { method ->
                method.name == "call" && method.parameterCount == 2
            }
            if (callMethod != null) {
                return callMethod.invoke(configured, appId, props)
            }
        } catch (_: Exception) {
            // fall through
        }
        throw IllegalArgumentException(
            "bb4.jpackage callback must be a Closure (appId, props) -> …, got ${configured.javaClass.name}",
        )
    }

    /**
     * Build the jpackage argument list (without the executable path).
     */
    @JvmStatic
    fun buildCli(args: Map<String, Any?>): List<String> {
        val appId = args["appId"] as String
        @Suppress("UNCHECKED_CAST")
        val propMap = args["propMap"] as Map<*, *>
        val installLib = args["installLib"] as String
        val destDir = args["destDir"] as String
        val displayName = args["displayName"] as String
        val appVersion = args["appVersion"] as String
        val description = args["description"] as String
        val licenseFile = args["licenseFile"] as String
        val icon = args["icon"] as String
        val mainJarName = args["mainJarName"] as String
        val mainClassName = args["mainClassName"] as String
        val installerType = args["installerType"] as String
        val packageIdentifierPrefix = args["packageIdentifierPrefix"] as String
        val osName = args["osName"] as String
        @Suppress("UNCHECKED_CAST")
        val javaOpts = (args["javaOptions"] as? List<*>)?.map { it.toString() } ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val appArgs = (args["arguments"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val resourceDir = args["resourceDir"] as String?

        val cli = mutableListOf(
            "--input", installLib,
            "--dest", destDir,
            "--name", displayName,
            "--app-version", appVersion,
            "--description", description,
            "--vendor", VENDOR,
            "--license-file", licenseFile,
            "--icon", icon,
            "--main-jar", mainJarName,
            "--main-class", mainClassName,
            "--type", installerType,
            "--add-modules", ADD_MODULES,
            "--jlink-options", JLINK_OPTIONS,
        )

        javaOpts.forEach { opt ->
            cli.addAll(listOf("--java-options", opt))
        }
        appArgs.forEach { arg ->
            cli.addAll(listOf("--arguments", arg))
        }

        when {
            isMac(osName) -> {
                cli.addAll(
                    listOf(
                        "--mac-package-identifier", "$packageIdentifierPrefix.$appId",
                        "--mac-package-name", macPackageNameFor(propMap),
                    ),
                )
            }
            isWindows(osName) -> {
                cli.addAll(listOf("--win-menu", "--win-shortcut", "--win-dir-chooser"))
                if (isConsoleApp(propMap)) {
                    cli.add("--win-console")
                }
            }
            else -> {
                cli.addAll(
                    listOf(
                        "--linux-shortcut",
                        "--linux-package-name", linuxPackageNameFor(appId),
                        "--linux-deb-maintainer", LINUX_DEB_MAINTAINER,
                    ),
                )
                if (resourceDir != null) {
                    cli.addAll(listOf("--resource-dir", resourceDir))
                }
            }
        }

        return cli
    }

    @JvmStatic
    fun artifactBaseName(appId: String, appVersion: String): String =
        "bb4-${appId.lowercase()}-$appVersion"
}
