// Copyright by Barry G. Becker, 2026. Licensed under MIT License: http://www.opensource.org/licenses/MIT

package com.barrybecker4.gradle.plugins

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class Bb4AppSupportTest {

    @Test
    void 'defaultJvmArgs includes heap and assertion flags'() {
        def args = Bb4AppSupport.defaultJvmArgs('8G', false, 'Linux')
        assertEquals(['-ea', '-Xss128m', '-Xmx8G'], args)
    }

    @Test
    void 'defaultJvmArgs defaults heap to 256m'() {
        def args = Bb4AppSupport.defaultJvmArgs(null, false, 'Windows 11')
        assertEquals(['-ea', '-Xss128m', '-Xmx256m'], args)
    }

    @Test
    void 'defaultJvmArgs prepends startOnFirstThread on mac'() {
        def args = Bb4AppSupport.defaultJvmArgs('1g', true, 'Mac OS X')
        assertEquals(['-XstartOnFirstThread', '-ea', '-Xss128m', '-Xmx1g'], args)
    }

    @Test
    void 'defaultJvmArgs skips startOnFirstThread on non-mac'() {
        def args = Bb4AppSupport.defaultJvmArgs('1g', true, 'Linux')
        assertEquals(['-ea', '-Xss128m', '-Xmx1g'], args)
    }

    @Test
    void 'defaultJvmArgs appends extras'() {
        def args = Bb4AppSupport.defaultJvmArgs('512m', false, 'Linux', ['-Dfile.encoding=UTF-8'])
        assertEquals(['-ea', '-Xss128m', '-Xmx512m', '-Dfile.encoding=UTF-8'], args)
    }

    @Test
    void 'programArgsFromProps uses panel_class when args empty'() {
        def args = Bb4AppSupport.programArgsFromProps([panel_class: 'com.example.Panel'])
        assertEquals(['-panel_class', 'com.example.Panel'], args)
    }

    @Test
    void 'programArgsFromProps prefers explicit args'() {
        def args = Bb4AppSupport.programArgsFromProps([args: ['a', 'b'], panelClass: 'x.Y'])
        assertEquals(['a', 'b'], args)
    }

    @Test
    void 'applicationNameFor prefers command'() {
        assertEquals('bb4-sudoku', Bb4AppSupport.applicationNameFor('sudoku', [command: 'bb4-sudoku']))
        assertEquals('bb4-maze', Bb4AppSupport.applicationNameFor('maze', [:]))
    }

    @Test
    void 'capitalizeAppId capitalizes first letter only'() {
        assertEquals('Sudoku', Bb4AppSupport.capitalizeAppId('sudoku'))
        assertEquals('Hiq', Bb4AppSupport.capitalizeAppId('hiq'))
    }
}
