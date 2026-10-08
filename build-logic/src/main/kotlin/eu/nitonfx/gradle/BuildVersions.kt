package eu.nitonfx.gradle

/**
 * Single source of truth for the JVM levels used across the build.
 *
 * Kept in Kotlin (rather than the version catalog) so that the convention plugins can
 * consume it in a type safe way without the `LibrariesForLibs` reflection workaround.
 */
object BuildVersions {
    /**
     * JDK used to *run* javac/kotlinc.
     */
    const val JAVA_TOOLCHAIN = 25

    /**
     * Bytecode/API level the published artifacts are compiled against (`javac --release`).
     * Consumers only need a JDK of at least this version.
     */
    const val JAVA_RELEASE = 21
}
