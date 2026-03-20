// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import javax.inject.Inject

class Bb4Extension {

    final Property<String> archivesBaseName
    final Property<String> mainClass
    final Property<String> scalaVersion

    @Inject
    Bb4Extension(ObjectFactory objects) {
        archivesBaseName = objects.property(String).convention('')
        mainClass = objects.property(String).convention('')
        scalaVersion = objects.property(String).convention(Bb4DependencyVersions.SCALA)
    }
}
