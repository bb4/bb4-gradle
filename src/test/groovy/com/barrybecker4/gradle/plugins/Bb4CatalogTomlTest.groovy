// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT
package com.barrybecker4.gradle.plugins

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class Bb4CatalogTomlTest {

    private static final String SAMPLE = '''\
[versions]
bb4-gradle = "0.0.0"
math = "3.0.1"
scala = "3.10.0"
junit = "5.14.4"

[libraries]
math = { module = "com.barrybecker4:bb4-math", version.ref = "math" }
'''

    @Test
    void 'version returns present key'() {
        assertEquals('3.10.0', Bb4CatalogToml.version(SAMPLE, 'scala'))
        assertEquals('5.14.4', Bb4CatalogToml.version(SAMPLE, 'junit'))
    }

    @Test
    void 'version throws for missing key'() {
        def ex = assertThrows(IllegalArgumentException) {
            Bb4CatalogToml.version(SAMPLE, 'missing')
        }
        assertTrue(ex.message.contains('missing'))
    }

    @Test
    void 'render replaces only bb4-gradle line'() {
        def rendered = Bb4CatalogToml.render(SAMPLE, '3.0-SNAPSHOT')
        assertEquals('3.0-SNAPSHOT', Bb4CatalogToml.version(rendered, 'bb4-gradle'))
        assertEquals('3.0.1', Bb4CatalogToml.version(rendered, 'math'))
        assertEquals('3.10.0', Bb4CatalogToml.version(rendered, 'scala'))
    }

    @Test
    void 'render throws when bb4-gradle line absent'() {
        def without = SAMPLE.replace('bb4-gradle = "0.0.0"\n', '')
        assertThrows(IllegalArgumentException) {
            Bb4CatalogToml.render(without, '3.0-SNAPSHOT')
        }
    }

    @Test
    void 'dependency versions load scala and junit from packaged toml'() {
        assertEquals('3.10.0', Bb4DependencyVersions.SCALA)
        assertEquals('5.14.4', Bb4DependencyVersions.JUNIT)
    }

    @Test
    void 'packaged rendered toml uses project version for bb4-gradle and keeps library versions'() {
        def packaged = Bb4CatalogToml.loadPackaged()
        assertEquals('3.0-SNAPSHOT', Bb4CatalogToml.version(packaged, 'bb4-gradle'))
        assertEquals('3.0-SNAPSHOT', Bb4CatalogToml.version(packaged, 'math'))
        assertEquals('3.0-SNAPSHOT', Bb4CatalogToml.version(packaged, 'common'))
    }

    @Test
    void 'applyTo registers versions libraries and plugins on a builder'() {
        def versions = [:]
        def libraries = []
        def plugins = []
        def builder = new Object() {
            def version(String alias, String ver) { versions[alias] = ver }
            def library(String alias, String group, String name) {
                def entry = [alias: alias, group: group, name: name]
                libraries << entry
                return new Object() {
                    def versionRef(String ref) { entry.versionRef = ref }
                }
            }
            def plugin(String alias, String id) {
                def entry = [alias: alias, id: id]
                plugins << entry
                return new Object() {
                    def versionRef(String ref) { entry.versionRef = ref }
                    def version(String ver) { entry.version = ver }
                }
            }
        }
        Bb4CatalogToml.applyTo(builder, SAMPLE + '''
[plugins]
base = { id = "com.barrybecker4.bb4.base" }
publish = { id = "com.barrybecker4.bb4.publish", version.ref = "bb4-gradle" }
''')
        assertEquals('3.10.0', versions.scala)
        assertEquals('com.barrybecker4', libraries.find { it.alias == 'math' }.group)
        assertEquals('math', libraries.find { it.alias == 'math' }.versionRef)
        // Id-only plugin rows are skipped by applyTo (use from(toml) for those).
        assertEquals(null, plugins.find { it.alias == 'base' })
        assertEquals('bb4-gradle', plugins.find { it.alias == 'publish' }.versionRef)
    }
}
