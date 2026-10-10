import eu.nitonfx.gradle.BuildVersions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("nitonfx.java-conventions")
    id("nitonfx.test-conventions")
    id("nitonfx.publish-conventions")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

description = "Kotlin Symbol Processor generating delegator interfaces for @Delegator."

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(BuildVersions.JAVA_RELEASE.toString())
        allWarningsAsErrors = true
        freeCompilerArgs.add("-Xjdk-release=${BuildVersions.JAVA_RELEASE}")
    }
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


dependencies {
    ksp(libs.auto.service.ksp)
    compileOnly(libs.auto.service.annotations)

    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    // The processor matches @Delegator by qualified name only, so the annotation is needed
    // just for compiling the test fixtures.
    testImplementation(project(":delegation-annotations"))
    testImplementation(libs.kctfork.core)
    testImplementation(libs.kctfork.ksp)

}


// kotlin-compile-testing drives the Kotlin compiler in-process through its experimental API.
tasks.named<KotlinJvmCompile>("compileTestKotlin") {
    compilerOptions.optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
}

// The embedded Kotlin compiler used by kotlin-compile-testing reaches into JDK internals.
tasks.test {
    // Golden files live in the source tree so `-Dgolden.update=true` can rewrite them.
    systemProperty("golden.dir", layout.projectDirectory.dir("src/test/resources/golden").asFile.path)
    providers.systemProperty("golden.update").orNull?.let { systemProperty("golden.update", it) }

    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    )
}
