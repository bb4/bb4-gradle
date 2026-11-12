// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import java.io.File

/**
 * Applies CycloneDX and attaches a Direct SBOM JSON sidecar to Maven publications.
 */
object Bb4CycloneDxSupport {

    const val PLUGIN_ID = "org.cyclonedx.bom"
    const val DIRECT_BOM_TASK = "cyclonedxDirectBom"

    @JvmStatic
    fun configure(project: Project) {
        project.pluginManager.apply(PLUGIN_ID)
    }

    @JvmStatic
    fun attachBomToPublication(project: Project, pub: MavenPublication) {
        if (!project.plugins.hasPlugin(PLUGIN_ID)) {
            project.pluginManager.apply(PLUGIN_ID)
        }
        if (!project.tasks.names.contains(DIRECT_BOM_TASK)) {
            project.logger.warn(
                "bb4 Gradle: CycloneDX task '{}' not found; skipping SBOM for publication '{}'",
                DIRECT_BOM_TASK,
                pub.name,
            )
            return
        }
        attachBom(pub, project.tasks.named(DIRECT_BOM_TASK))
    }

    @JvmStatic
    fun attachBomToAllPublications(project: Project) {
        val publishing = project.extensions.findByType(PublishingExtension::class.java)
        if (publishing == null || !project.tasks.names.contains(DIRECT_BOM_TASK)) {
            return
        }
        val bomTask = project.tasks.named(DIRECT_BOM_TASK)
        publishing.publications.withType(MavenPublication::class.java).configureEach { pub ->
            attachBom(pub, bomTask)
        }
    }

    private fun attachBom(pub: MavenPublication, bomTask: TaskProvider<Task>) {
        pub.artifact(
            bomTask.map { task -> resolveJsonOutput(task) },
        ) { artifact ->
            artifact.classifier = "cyclonedx"
            artifact.extension = "json"
            artifact.builtBy(bomTask)
        }
    }

    private fun resolveJsonOutput(task: Task): File {
        try {
            val jsonOutputProp = task.javaClass.methods
                .firstOrNull { it.name == "getJsonOutput" && it.parameterCount == 0 }
                ?.invoke(task)
            if (jsonOutputProp != null) {
                val present = jsonOutputProp.javaClass.methods
                    .firstOrNull { it.name == "isPresent" && it.parameterCount == 0 }
                    ?.invoke(jsonOutputProp) as? Boolean
                if (present == true) {
                    val value = jsonOutputProp.javaClass.methods
                        .firstOrNull { it.name == "get" && it.parameterCount == 0 }
                        ?.invoke(jsonOutputProp)
                    when (value) {
                        is RegularFile -> return value.asFile
                        is File -> return value
                        else -> {
                            val asFile = value?.javaClass?.methods
                                ?.firstOrNull { it.name == "getAsFile" && it.parameterCount == 0 }
                                ?.invoke(value)
                            if (asFile is File) {
                                return asFile
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // fall through to outputs scan
        }
        val json = task.outputs.files.files.firstOrNull { it.name.endsWith(".json") }
        if (json != null) {
            return json
        }
        throw IllegalStateException("CycloneDX task ${task.path} produced no JSON SBOM output")
    }
}
