pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}

plugins {
    // Resolves (and downloads, if missing) JVM toolchains from the foojay disco API.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "delegation-processor"

include(
    "annotations",
    "processor",
    "ksp",
    "integration-tests:java-consumer",
    "integration-tests:kotlin-consumer",
)

// Local (file system) build cache. CI layers a GitHub Actions cache on top of this via
// `gradle/actions/setup-gradle`, which caches the whole Gradle user home.
buildCache {
    local {
        isEnabled = true
    }
}
