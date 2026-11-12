// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider

/**
 * Applies CycloneDX and attaches a Direct SBOM JSON sidecar to Maven publications.
 */
final class Bb4CycloneDxSupport {

    static final String PLUGIN_ID = 'org.cyclonedx.bom'
    static final String DIRECT_BOM_TASK = 'cyclonedxDirectBom'

    private Bb4CycloneDxSupport() {}

    static void configure(Project project) {
        project.pluginManager.apply(PLUGIN_ID)
    }

    /**
     * Attach the project's Direct SBOM to a publication. Safe to call from afterEvaluate
     * when publications are created.
     */
    static void attachBomToPublication(Project project, MavenPublication pub) {
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

    /**
     * Attach SBOM to every existing Maven publication (used by this repo's Kotlin publishing script path).
     */
    static void attachBomToAllPublications(Project project) {
        def publishing = project.extensions.findByType(PublishingExtension)
        if (publishing == null || !project.tasks.names.contains(DIRECT_BOM_TASK)) {
            return
        }
        def bomTask = project.tasks.named(DIRECT_BOM_TASK)
        publishing.publications.withType(MavenPublication).configureEach { MavenPublication pub ->
            attachBom(pub, bomTask)
        }
    }

    private static void attachBom(MavenPublication pub, TaskProvider bomTask) {
        pub.artifact(bomTask.map { task ->
            if (task.hasProperty('jsonOutput')) {
                def jsonOutput = task.jsonOutput
                if (jsonOutput != null && jsonOutput.metaClass.respondsTo(jsonOutput, 'isPresent') && jsonOutput.present) {
                    return jsonOutput.get().asFile
                }
                if (jsonOutput != null && jsonOutput.metaClass.respondsTo(jsonOutput, 'get')) {
                    try {
                        def value = jsonOutput.get()
                        if (value != null && value.metaClass.hasProperty(value, 'asFile')) {
                            return value.asFile
                        }
                        if (value instanceof File) {
                            return value
                        }
                    } catch (Exception ignored) {
                        // fall through to outputs scan
                    }
                }
            }
            def json = task.outputs.files.files.find { it.name.endsWith('.json') }
            if (json != null) {
                return json
            }
            throw new IllegalStateException("CycloneDX task ${task.path} produced no JSON SBOM output")
        }) { artifact ->
            artifact.classifier = 'cyclonedx'
            artifact.extension = 'json'
            artifact.builtBy(bomTask)
        }
    }
}
