# bb4-gradle

Shared build logic for bb4 projects.

To use, include something like the following in your projects build.gradle file

```
buildscript {
    repositories {
        // retrieve the shared gradle build scripts from here
        maven { url "https://oss.sonatype.org/content/repositories/snapshots/"}
    }
    dependencies {
        classpath 'com.barrybecker4:bb4-gradle:1.6-SNAPSHOT'
    }
}

apply from: project.buildscript.classLoader.getResource('bb4.gradle').toURI()

:
: <your build logic here>
:

apply from: project.buildscript.classLoader.getResource('bb4-publish.gradle').toURI()
apply from: project.buildscript.classLoader.getResource('bb4-deploy.gradle').toURI()
```
