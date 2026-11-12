// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

/**
 * Single source of truth for dependency versions applied by bb4 convention plugins to downstream projects.
 * Values are loaded from the packaged {@code /libs.versions.toml}.
 */
final class Bb4DependencyVersions {
    private static final String TOML = Bb4CatalogToml.loadPackaged()

    static final String SCALA = Bb4CatalogToml.version(TOML, 'scala')
    static final String VECMATH = Bb4CatalogToml.version(TOML, 'vecmath')
    static final String SCALATEST = Bb4CatalogToml.version(TOML, 'scalatest')
    /** Keep in sync with SCALATEST (scalatestplus junit-5-14_3 uses the matching x.y.z.0 release). */
    static final String SCALATEST_PLUS_JUNIT5 = Bb4CatalogToml.version(TOML, 'scalatest-plus-junit5')
    static final String JUNIT = Bb4CatalogToml.version(TOML, 'junit')
    static final String SCALA_XML = Bb4CatalogToml.version(TOML, 'scala-xml')
    static final String PEGDOWN = Bb4CatalogToml.version(TOML, 'pegdown')
    static final String FLEXMARK = Bb4CatalogToml.version(TOML, 'flexmark')

    private Bb4DependencyVersions() {}
}
