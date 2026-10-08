pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Not published; the subprojects are named after their artifact ids so that Maven coordinates
// (including those of project dependencies in generated POMs) need no overriding.
rootProject.name = "delegation"

include(
    "delegation-annotations",
    "delegation-processor",
    "delegation-processor-ksp",
    "integration-tests:java-consumer",
    "integration-tests:kotlin-consumer",
)

buildCache {
    local {
        isEnabled = true
    }
}
