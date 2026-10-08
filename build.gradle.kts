plugins {
    base
    // Declared here (without applying) so the versions resolve once for the whole build.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
}

description = "Compile time generation of delegating interfaces for Java (APT) and Kotlin (KSP)."
