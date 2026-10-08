import eu.nitonfx.gradle.BuildVersions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("nitonfx.java-conventions")
    id("nitonfx.test-conventions")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

description = "End to end check that ksp(...) produces usable delegators."

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(BuildVersions.JAVA_RELEASE.toString())
        allWarningsAsErrors = true
    }
}

dependencies {
    ksp(project(":delegation-processor-ksp"))
    compileOnly(project(":delegation-annotations"))
}
