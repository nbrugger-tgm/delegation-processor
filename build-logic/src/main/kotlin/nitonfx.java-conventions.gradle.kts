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
    // Compile against the public API of the baseline JDK instead of the toolchain JDK,
    // so artifacts stay usable on JDK 21+ even though they are built with a newer JDK.
    options.release = BuildVersions.JAVA_RELEASE
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all,-processing,-serial",
            "-Werror",
            // Keep parameter names in the class files; useful for generated delegation code.
            "-parameters",
        ),
    )
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:all,-missing", "-quiet")
        links("https://docs.oracle.com/en/java/javase/${BuildVersions.JAVA_RELEASE}/docs/api/")
    }
}

// Reproducible archives: identical inputs produce byte identical jars, which also makes
// the build cache far more effective.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    dirPermissions { unix("rwxr-xr-x") }
    filePermissions { unix("rw-r--r--") }
}
