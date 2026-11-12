plugins {
    id("com.barrybecker4.bb4.scala-library")
    id("com.barrybecker4.bb4.publish")
    id("com.barrybecker4.bb4.application")
}

group = "com.barrybecker4.test"
version = "1.0.0-TEST"

description = "Configuration cache validation consumer"

bb4 {
    scalaVersion = "3.10.0"
    archivesBaseName = "bb4-config-cache-test"
    mainClass = "com.barrybecker4.test.Main"
}

// Define appMap to test the deployHtml task's configuration-cache compatibility
ext {
    set("appMap", mapOf(
        "app1" to mapOf(
            "title" to "Test Application 1 - MODIFIED",
            "command" to "java -jar app1.jar",
            "longDescription" to "A test application for configuration cache validation",
            "imageName" to "app1.jpg",
            "deploy" to true
        ),
        "app2" to mapOf(
            "title" to "Test Application 2",
            "command" to "java -jar app2.jar",
            "longDescription" to "Another test application",
            "imageName" to "app2.jpg",
            "deploy" to true
        ),
        "app3" to mapOf(
            "title" to "Test Application 3",
            "command" to "java -jar app3.jar",
            "longDescription" to "A third test application added to verify cache invalidation",
            "imageName" to "app3.jpg",
            "deploy" to true
        )
    ))
}

tasks.register("validateConfigCache") {
    group = "verification"
    description = "Validate that tasks work correctly with configuration cache"
    
    dependsOn("clean", "javadoc", "deployHtml", "deploy")
    
    doLast {
        println("Configuration cache validation completed successfully")
    }
}
