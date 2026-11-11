// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

/**
 * Pure helpers for jpackage CLI assembly and naming. Unit-tested without invoking jpackage.
 */
final class Bb4JpackageSupport {

    static final String ADD_MODULES =
        'java.desktop,java.logging,java.xml,java.management,java.naming,java.prefs,java.datatransfer,jdk.unsupported,jdk.crypto.ec,jdk.localedata'

    static final String JLINK_OPTIONS =
        '--strip-debug --no-header-files --no-man-pages --ignore-signing-information'

    static final String VENDOR = 'Barry Becker'
    static final String LINUX_DEB_MAINTAINER = 'barrybecker4@gmail.com'

    private Bb4JpackageSupport() {}

    static String sanitizeText(String text) {
        (text ?: '')
            .replaceAll(/<[^>]+>/, '')
            .replaceAll(/["']/, '')
            .replaceAll(/\s+/, ' ')
            .trim()
    }

    /**
     * jpackage --app-version rejects -SNAPSHOT and other non x[.y[.z]] strings.
     */
    static String sanitizeAppVersion(Object projectVersion) {
        def raw = projectVersion?.toString() ?: ''
        def stripped = raw.replaceAll(/-SNAPSHOT$/, '')
        (stripped ==~ /\d+(\.\d+){0,2}/) ? stripped : '0.0.1'
    }

    static String displayNameFor(Map propMap) {
        (propMap.get('title') as String)?.replaceAll(/['"]/, '')?.trim() ?: ''
    }

    static String linuxPackageNameFor(String appId) {
        "bb4-${appId.toLowerCase()}"
    }

    static String macPackageNameFor(Map propMap) {
        if (propMap.containsKey('macPackageName')) {
            def explicit = (propMap.get('macPackageName') as String).trim()
            if (explicit.length() > 16) {
                throw new IllegalArgumentException(
                    "macPackageName exceeds 16 characters: '${explicit}'")
            }
            return explicit
        }
        def cleaned = (propMap.get('title') as String)?.replaceAll(/[^A-Za-z0-9 ]/, '')?.trim() ?: ''
        cleaned.length() <= 16 ? cleaned : cleaned.substring(0, 16).trim()
    }

    static boolean isConsoleApp(Map propMap) {
        propMap.containsKey('console') && propMap.get('console') == true
    }

    static boolean isDeployable(Map propMap) {
        !(propMap.containsKey('deploy') && propMap.get('deploy') == false)
    }

    /**
     * Detect installer type from {@code os.name}. Returns {@code dmg}, {@code msi}, or {@code deb}.
     */
    static String installerTypeForOs(String osName) {
        def lower = (osName ?: '').toLowerCase()
        if (lower.contains('win')) {
            return 'msi'
        }
        if (lower.contains('mac') || lower.contains('darwin')) {
            return 'dmg'
        }
        return 'deb'
    }

    static boolean isWindows(String osName) {
        (osName ?: '').toLowerCase().contains('win')
    }

    static boolean isMac(String osName) {
        def lower = (osName ?: '').toLowerCase()
        lower.contains('mac') || lower.contains('darwin')
    }

    static String iconFileNameForOs(String osName) {
        if (isWindows(osName)) {
            return 'icon.ico'
        }
        if (isMac(osName)) {
            return 'icon.icns'
        }
        return 'icon.png'
    }

    static String defaultJpackageBinaryName(String osName) {
        isWindows(osName) ? 'jpackage.exe' : 'jpackage'
    }

    /**
     * Invoke a configured closure or callable with (appId, props).
     */
    static Object invokeConfigured(Object configured, String appId, Map props) {
        if (configured == null) {
            return null
        }
        if (configured instanceof Closure) {
            return configured.call(appId, props)
        }
        if (configured.metaClass.respondsTo(configured, 'call', String, Map)) {
            return configured.call(appId, props)
        }
        throw new IllegalArgumentException(
            "bb4.jpackage callback must be a Closure (appId, props) -> …, got ${configured.getClass().name}")
    }

    /**
     * Build the jpackage argument list (without the executable path).
     *
     * @param osName value of {@code System.getProperty('os.name')}
     * @param resourceDir absolute path for Linux console {@code --resource-dir}, or null
     */
    static List<String> buildCli(Map args) {
        String appId = args.appId as String
        Map propMap = args.propMap as Map
        String installLib = args.installLib as String
        String destDir = args.destDir as String
        String displayName = args.displayName as String
        String appVersion = args.appVersion as String
        String description = args.description as String
        String licenseFile = args.licenseFile as String
        String icon = args.icon as String
        String mainJarName = args.mainJarName as String
        String mainClassName = args.mainClassName as String
        String installerType = args.installerType as String
        String packageIdentifierPrefix = args.packageIdentifierPrefix as String
        String osName = args.osName as String
        List<String> javaOpts = (args.javaOptions as List) ?: []
        List<String> appArgs = (args.arguments as List) ?: []
        String resourceDir = args.resourceDir as String

        def cli = [
            '--input', installLib,
            '--dest', destDir,
            '--name', displayName,
            '--app-version', appVersion,
            '--description', description,
            '--vendor', VENDOR,
            '--license-file', licenseFile,
            '--icon', icon,
            '--main-jar', mainJarName,
            '--main-class', mainClassName,
            '--type', installerType,
            '--add-modules', ADD_MODULES,
            '--jlink-options', JLINK_OPTIONS,
        ]

        javaOpts.each { String opt ->
            cli.addAll(['--java-options', opt])
        }
        appArgs.each { String arg ->
            cli.addAll(['--arguments', arg])
        }

        if (isMac(osName)) {
            cli.addAll([
                '--mac-package-identifier', "${packageIdentifierPrefix}.${appId}".toString(),
                '--mac-package-name', macPackageNameFor(propMap),
            ])
        } else if (isWindows(osName)) {
            cli.addAll(['--win-menu', '--win-shortcut', '--win-dir-chooser'])
            if (isConsoleApp(propMap)) {
                cli.add('--win-console')
            }
        } else {
            cli.addAll([
                '--linux-shortcut',
                '--linux-package-name', linuxPackageNameFor(appId),
                '--linux-deb-maintainer', LINUX_DEB_MAINTAINER,
            ])
            if (resourceDir) {
                cli.addAll(['--resource-dir', resourceDir])
            }
        }

        return cli.collect { it.toString() }
    }

    static String artifactBaseName(String appId, String appVersion) {
        "bb4-${appId.toLowerCase()}-${appVersion}"
    }
}
