# bb4-gradle

Shared build logic for bb4 projects. There are 3 main components:
 - `bb4.gradle` - general build configuration for scala or java projects.
 - `bb4-deploy` - used to deploy projects to a website.
 - `bb4-publish` - used to deploy projects to sonatype, and from there release to a maven repository so that they can be shared.

To deploy, udpate the versoin in `build.gradle`, and run <br>
`./gradlew publishArtifacts`<br>
To use, include something like the following in your projects build.gradle file.

```
buildscript {
    repositories {
        // retrieve the shared gradle build scripts from here
        maven { url "https://oss.sonatype.org/content/repositories/releases" }
    }
    dependencies {
        classpath 'com.barrybecker4:bb4-gradle:1.6.5'
    }
}

apply from: project.buildscript.classLoader.getResource('bb4.gradle').toURI()

:
: <your build logic here>
:

apply from: project.buildscript.classLoader.getResource('bb4-publish.gradle').toURI()
apply from: project.buildscript.classLoader.getResource('bb4-deploy.gradle').toURI()
```
