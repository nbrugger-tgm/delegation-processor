import eu.nitonfx.gradle.BuildVersions

plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(BuildVersions.JAVA_TOOLCHAIN)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = BuildVersions.JAVA_RELEASE
    options.compilerArgs.addAll(
        listOf(
            // Every javac warning is an error. `-processing` drops "no processor claimed this
            // annotation", `-serial` the missing-serialVersionUID noise. This also applies to the
            // integration tests, so it proves the *generated* code compiles warning free.
            "-Xlint:all,-processing,-serial",
            "-Werror",
        ),
    )
}

tasks.withType<Javadoc>().configureEach {
    // -Xdoclint:all,-missing => validate syntax, HTML and @link/@snippet references, but do not
    // demand a doc comment on every member. addStringOption emits "-<key> <value>", hence -quiet.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

// Reproducible archives: identical inputs produce byte identical jars, which also makes
// the build cache far more effective.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    dirPermissions { unix("rwxr-xr-x") }
    filePermissions { unix("rw-r--r--") }
}
