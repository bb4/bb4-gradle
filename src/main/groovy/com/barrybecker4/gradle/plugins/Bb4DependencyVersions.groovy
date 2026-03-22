// Copyright by Barry G. Becker, 2013–2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

/**
 * Single source of truth for dependency versions applied by bb4 convention plugins to downstream projects.
 * Update here when bumping Scala, ScalaTest, JUnit, etc.
 */
final class Bb4DependencyVersions {
    static final String SCALA = '3.8.2'
    static final String VECMATH = '1.3.1'
    static final String SCALATEST = '3.2.19'
    /** Keep in sync with SCALATEST (scalatestplus junit-5-13_3 uses the matching x.y.z.0 release). */
    static final String SCALATEST_PLUS_JUNIT5 = '3.2.19.0'
    static final String JUNIT = '5.11.4'
    static final String SCALA_XML = '2.3.0'
    static final String PEGDOWN = '1.6.0'
    static final String FLEXMARK = '0.64.8'

    private Bb4DependencyVersions() {}
}
