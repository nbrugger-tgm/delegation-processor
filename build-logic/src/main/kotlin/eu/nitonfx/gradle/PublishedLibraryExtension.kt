package eu.nitonfx.gradle

import org.gradle.api.provider.Property

/**
 * Configures the Maven coordinates of a published module.
 *
 * Gradle project names are deliberately short (`annotations`, `processor`, `ksp`) so that
 * directory layout stays readable; the published artifact ids are set explicitly here.
 */
abstract class PublishedLibraryExtension {
    /** Maven `artifactId`, e.g. `delegation-processor`. */
    abstract val artifactId: Property<String>

    /** Human readable one-liner used as the POM `<description>`. */
    abstract val description: Property<String>
}
