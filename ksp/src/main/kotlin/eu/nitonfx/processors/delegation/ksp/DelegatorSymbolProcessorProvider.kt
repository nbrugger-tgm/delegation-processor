package eu.nitonfx.processors.delegation.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Entry point registered via `META-INF/services`, instantiated by KSP once per compilation.
 *
 * Add the processor to a Kotlin project with:
 *
 * ```kotlin
 * plugins { id("com.google.devtools.ksp") }
 *
 * dependencies {
 *     ksp("eu.nitonfx.processors:delegation-processor-ksp:<version>")
 *     implementation("eu.nitonfx.processors:delegation-processor-ksp:<version>")
 * }
 * ```
 *
 * @see DelegatorSymbolProcessor
 */
public class DelegatorSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        DelegatorSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            verbose = environment.options[OPTION_VERBOSE].toBoolean(),
        )

    public companion object {
        /** KSP option (`ksp { arg("delegation.verbose", "true") }`) enabling info level logging. */
        public const val OPTION_VERBOSE: String = "delegation.verbose"
    }
}
