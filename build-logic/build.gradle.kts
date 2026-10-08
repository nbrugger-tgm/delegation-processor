plugins {
    `kotlin-dsl`
}

// No third party plugins are needed on the build-logic classpath: the Kotlin and KSP plugins are
// applied directly by the modules that need them. Putting the Kotlin Gradle Plugin here would
// conflict with the (strictly pinned) Kotlin version embedded in `kotlin-dsl`.
