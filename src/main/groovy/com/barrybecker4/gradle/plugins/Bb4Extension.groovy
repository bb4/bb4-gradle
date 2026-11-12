// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

class Bb4Extension {

    final Property<String> archivesBaseName
    final Property<String> mainClass
    final Property<String> scalaVersion
    
    /**
     * Multi-artifact publishing configuration. When present, creates one publication per entry.
     * Each key is an artifact name; each value is a Map with 'include' (required) and 'exclude' (optional) patterns.
     * Example: jarMap.put('common', [include: ['com/example/common/**']])
     */
    final MapProperty<String, Map<String, Object>> jarMap

    /**
     * Opt-in native installer (jpackage) configuration.
     */
    final Bb4JpackageExtension jpackage

    @Inject
    Bb4Extension(ObjectFactory objects) {
        archivesBaseName = objects.property(String).convention('')
        mainClass = objects.property(String).convention('')
        scalaVersion = objects.property(String).convention(Bb4DependencyVersions.SCALA)
        jarMap = objects.mapProperty(String, Map).convention([:])
        jpackage = objects.newInstance(Bb4JpackageExtension)
    }

    void jpackage(Action<? super Bb4JpackageExtension> action) {
        action.execute(jpackage)
    }
}
