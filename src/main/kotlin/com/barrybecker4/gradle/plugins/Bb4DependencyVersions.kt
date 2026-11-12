// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

/**
 * Single source of truth for dependency versions applied by bb4 convention plugins.
 * Values are loaded from the packaged `/libs.versions.toml`.
 */
object Bb4DependencyVersions {
    private val TOML: String = Bb4CatalogToml.loadPackaged()

    @JvmField
    val SCALA: String = Bb4CatalogToml.version(TOML, "scala")

    @JvmField
    val VECMATH: String = Bb4CatalogToml.version(TOML, "vecmath")

    @JvmField
    val SCALATEST: String = Bb4CatalogToml.version(TOML, "scalatest")

    /** Keep in sync with SCALATEST (scalatestplus junit-5-14_3 uses the matching x.y.z.0 release). */
    @JvmField
    val SCALATEST_PLUS_JUNIT5: String = Bb4CatalogToml.version(TOML, "scalatest-plus-junit5")

    @JvmField
    val JUNIT: String = Bb4CatalogToml.version(TOML, "junit")

    @JvmField
    val SCALA_XML: String = Bb4CatalogToml.version(TOML, "scala-xml")

    @JvmField
    val FLEXMARK: String = Bb4CatalogToml.version(TOML, "flexmark")
}
