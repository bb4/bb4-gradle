// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Optional light formatting: Spotless + Scalafmt for bb4 Scala layouts.
 * Not applied by `scala-library` — consumers opt in with `bb4.quality`.
 */
class Bb4QualityPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("com.diffplug.spotless")
        val scalafmtConfig = resolveScalafmtConfig(project)
        project.extensions.configure(SpotlessExtension::class.java) { spotless ->
            spotless.scala { scala ->
                scala.target(
                    "scala-source/**/*.scala",
                    "scala-test/**/*.scala",
                    "src/**/scala/**/*.scala",
                )
                scala.scalafmt(SCALAFMT_VERSION)
                    .scalaMajorVersion("3")
                    .configFile(scalafmtConfig)
            }
        }
        // Spotless already wires spotlessCheck into check; keep that default.
    }

    private fun resolveScalafmtConfig(project: Project): File {
        val projectConfig = project.layout.projectDirectory.file(".scalafmt.conf").asFile
        if (projectConfig.isFile) {
            return projectConfig
        }
        // Prefer .gradle/ over build/ so clean does not remove the default config.
        val generated = project.layout.projectDirectory.file(".gradle/bb4-scalafmt.conf").asFile
        generated.parentFile.mkdirs()
        val resource = javaClass.classLoader.getResourceAsStream(PACKAGED_CONF)
            ?: error("Missing packaged $PACKAGED_CONF on bb4.quality classpath")
        resource.use { input -> generated.outputStream().use { input.copyTo(it) } }
        return generated
    }

    companion object {
        /** Matches Spotless 8.11 default Scalafmt; pin for reproducibility. */
        const val SCALAFMT_VERSION: String = "3.11.5"
        const val PACKAGED_CONF: String = "bb4-scalafmt.conf"
    }
}
