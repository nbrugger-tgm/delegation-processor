/**
 * Annotations driving compile time generation of delegating interfaces.
 *
 * <p>This module contains no processing logic; add either
 * {@code eu.nitonfx.processors:delegation-processor} (javac annotation processing) or
 * {@code eu.nitonfx.processors:delegation-processor-ksp} (Kotlin Symbol Processing) to actually
 * generate code.
 */
module eu.nitonfx.processors.delegation {
    exports eu.nitonfx.processors.delegation;
}
