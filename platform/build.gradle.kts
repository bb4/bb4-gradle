// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
import java.util.regex.Pattern

plugins {
    `java-platform`
    `version-catalog`
    `maven-publish`
    signing
    id("org.cyclonedx.bom") version "3.5.1"
}

group = "com.barrybecker4"
version = rootProject.version
description = "bb4 recommended library and plugin versions (BOM + version catalog)"

extra["bb4.repository"] = "bb4-gradle"

val renderedCatalog = rootProject.layout.buildDirectory.file("generated/bb4-libs.versions.toml")

catalog {
    versionCatalog {
        from(files(renderedCatalog))
    }
}

tasks.named("generateCatalogAsToml") {
    dependsOn(rootProject.tasks.named("generateBb4Catalog"))
}

fun loadLibraryConstraints(tomlText: String): List<Pair<String, String>> {
    val versionsSection = tomlText.substringAfter("[versions]").substringBefore("[libraries]")
    val versionPattern = Pattern.compile("""(?m)^([a-zA-Z0-9_-]+)\s*=\s*"([^"]+)"\s*$""")
    val versions = linkedMapOf<String, String>()
    val versionMatcher = versionPattern.matcher(versionsSection)
    while (versionMatcher.find()) {
        versions[versionMatcher.group(1)] = versionMatcher.group(2)
    }

    val librariesSection = tomlText.substringAfter("[libraries]").substringBefore("[plugins]")
    val libraryPattern = Pattern.compile(
        """(?m)^[a-zA-Z0-9_-]+\s*=\s*\{\s*module\s*=\s*"([^"]+)"\s*,\s*version\.ref\s*=\s*"([^"]+)"\s*\}""",
    )
    val constraints = mutableListOf<Pair<String, String>>()
    val matcher = libraryPattern.matcher(librariesSection)
    while (matcher.find()) {
        val module = matcher.group(1)
        // Avoid self-constraint on the platform BOM publication.
        if (module == "com.barrybecker4:bb4-platform") {
            continue
        }
        val versionRef = matcher.group(2)
        val moduleVersion = versions[versionRef]
            ?: error("Missing version.ref '$versionRef' for module $module in catalog toml")
        constraints += module to moduleVersion
    }
    return constraints
}

// Source toml is always present; library versions are not rewritten by generateBb4Catalog.
val sourceConstraints = loadLibraryConstraints(rootProject.file("gradle/libs.versions.toml").readText())

dependencies {
    constraints {
        sourceConstraints.forEach { (module, moduleVersion) ->
            api("$module:$moduleVersion")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("bb4Platform") {
            artifactId = "bb4-platform"
            from(components["javaPlatform"])
        }
        create<MavenPublication>("bb4Catalog") {
            artifactId = "bb4-catalog"
            from(components["versionCatalog"])
        }
    }
}

apply(from = rootProject.file("gradle/bb4-central-publishing.gradle.kts"))
