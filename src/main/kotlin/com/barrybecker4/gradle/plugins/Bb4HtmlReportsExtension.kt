// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Opt-in ScalaTest HTML reporter dependencies under `bb4.htmlReports { }`.
 * When [enabled] is true, scala-xml and flexmark-all are added as `testRuntimeOnly`.
 */
open class Bb4HtmlReportsExtension @Inject constructor(objects: ObjectFactory) {

    val enabled: Property<Boolean> =
        objects.property(Boolean::class.javaObjectType).convention(false)
}
