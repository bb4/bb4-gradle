// Copyright by Barry G. Becker, 2021–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
import java.net.URI
import java.util.regex.Pattern

plugins {
    groovy
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("org.cyclonedx.bom") version "3.5.1"
}

group = "com.barrybecker4"
version = "3.0-SNAPSHOT"

description = "Common Gradle convention plugins for bb4 Scala/Java projects"

// Used by Bb4PomSupport / publishing docs (artifact name may differ from GitHub repo name).
extra["bb4.repository"] = "bb4-gradle"

java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

gradlePlugin {
    website = URI("https://github.com/barrybecker4/bb4-gradle").toString()
    vcsUrl = "https://github.com/barrybecker4/bb4-gradle.git"
    plugins {
        create("bb4Base") {
            id = "com.barrybecker4.bb4.base"
            displayName = "bb4 base conventions"
            description = "Java 25 toolchain, repositories, and resolution defaults for bb4 projects"
            tags = listOf("bb4", "scala", "java", "convention")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4BasePlugin"
        }
        create("bb4ScalaLibrary") {
            id = "com.barrybecker4.bb4.scala-library"
            displayName = "bb4 Scala library conventions"
            description = "Scala 3 + java-library layout, dependencies, and jar conventions"
            tags = listOf("bb4", "scala", "convention")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4ScalaLibraryPlugin"
        }
        create("bb4Publish") {
            id = "com.barrybecker4.bb4.publish"
            displayName = "bb4 Maven publishing"
            description = "Maven Central / Central Portal publishing with sources/javadoc/scaladoc jars"
            tags = listOf("bb4", "publish", "maven")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4PublishPlugin"
        }
        create("bb4Application") {
            id = "com.barrybecker4.bb4.application"
            displayName = "bb4 application conventions"
            description = "Application plugin, distributions, and website deploy helpers"
            tags = listOf("bb4", "application", "distribution")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4ApplicationPlugin"
        }
        create("bb4Settings") {
            id = "com.barrybecker4.bb4.settings"
            displayName = "bb4 settings conventions"
            description = "Foojay, Central snapshots, version catalog, and sibling includeBuild for bb4 projects"
            tags = listOf("bb4", "settings", "version-catalog")
            implementationClass = "com.barrybecker4.gradle.plugins.Bb4SettingsPlugin"
        }
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    // Resolve bb4 and other SNAPSHOTs published via Central Portal (OSSRH EOL June 2025).
    maven { url = URI("https://central.sonatype.com/repository/maven-snapshots/") }
}

testing {
    suites {
        val functionalTest = register<JvmTestSuite>("functionalTest") {
            useSpock("2.3-groovy-4.0")
            dependencies {
                implementation(project())
            }
        }
    }
}

gradlePlugin.testSourceSets(sourceSets["functionalTest"], sourceSets["test"])

tasks.named("check") {
    dependsOn(testing.suites.named("functionalTest"))
}

dependencies {
    implementation(localGroovy())
    // Foojay convention plugin so Bb4SettingsPlugin can apply it without a consumer version.
    // Implementation jar (Plugin Portal marker points here).
    implementation("org.gradle.toolchains:foojay-resolver:1.0.0")
    // CycloneDX so Bb4PublishPlugin can apply org.cyclonedx.bom on consumers.
    implementation("org.cyclonedx.bom:org.cyclonedx.bom.gradle.plugin:3.5.1")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Settings functional tests resolve the plugin from mavenLocal (TestKit cannot
    // includeBuild this project while it is itself building).
    dependsOn("publishToMavenLocal")
}

fun renderBb4Catalog(text: String, projectVersion: String): String {
    val pattern = Pattern.compile("""(?m)^bb4-gradle\s*=\s*"[^"]+"\s*$""")
    if (!pattern.matcher(text).find()) {
        error("Missing version key 'bb4-gradle' in catalog toml")
    }
    return pattern.matcher(text).replaceFirst("bb4-gradle = \"$projectVersion\"")
}

val generatedCatalogDir = layout.buildDirectory.dir("generated")
val generatedCatalogFile = generatedCatalogDir.map { it.file("bb4-libs.versions.toml") }

// Write at configuration time so :platform can consume the file via version-catalog `from(files(...))`.
val renderedCatalogText = renderBb4Catalog(
    layout.projectDirectory.file("gradle/libs.versions.toml").asFile.readText(),
    version.toString(),
)
val catalogOut = generatedCatalogFile.get().asFile
catalogOut.parentFile.mkdirs()
catalogOut.writeText(renderedCatalogText)

val generateBb4Catalog = tasks.register("generateBb4Catalog") {
    val source = layout.projectDirectory.file("gradle/libs.versions.toml")
    inputs.file(source)
    inputs.property("projectVersion", version.toString())
    outputs.file(generatedCatalogFile)
    doLast {
        val out = generatedCatalogFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(renderBb4Catalog(source.asFile.readText(), version.toString()))
    }
}

tasks.named<ProcessResources>("processResources") {
    from(generatedCatalogFile) {
        rename("bb4-libs.versions.toml", "libs.versions.toml")
        into(".")
    }
    dependsOn(generateBb4Catalog)
}

apply(from = "gradle/bb4-central-publishing.gradle.kts")
