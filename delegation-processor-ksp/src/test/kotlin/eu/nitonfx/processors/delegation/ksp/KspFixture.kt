package eu.nitonfx.processors.delegation.ksp

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.assertj.core.api.Assertions.assertThat
import java.io.File

/** Outcome of running the delegation symbol processor over a set of Kotlin sources. */
internal class KspRun(
    private val result: JvmCompilationResult,
    private val generatedRoot: File,
) {
    val exitCode: KotlinCompilation.ExitCode get() = result.exitCode
    val messages: String get() = result.messages
    val succeeded: Boolean get() = exitCode == KotlinCompilation.ExitCode.OK

    /** Every generated Kotlin file, keyed by qualified name. */
    val generatedFiles: Map<String, String> by lazy {
        val kotlinRoot = File(generatedRoot, "kotlin")
        kotlinRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { file ->
                file.relativeTo(kotlinRoot).invariantSeparatorsPath.removeSuffix(".kt").replace('/', '.') to
                    file.readText().normalise()
            }
    }

    fun generated(qualifiedName: String): String =
        generatedFiles[qualifiedName]
            ?: error("No source generated for $qualifiedName. Generated: ${generatedFiles.keys}\n$messages")
}

internal object KspFixture {

    /**
     * Directory holding the expected outputs, supplied by Gradle. Run
     * `./gradlew :ksp:test -Dgolden.update=true` to rewrite them from the current behaviour; always
     * review the resulting diff.
     */
    private val goldenDir = File(System.getProperty("golden.dir") ?: "src/test/resources/golden")
    private val updateGoldens = System.getProperty("golden.update").toBoolean()

    /** Compiles [sources] with the delegation symbol processor attached. */
    fun compile(vararg sources: SourceFile, processorOptions: Map<String, String> = emptyMap()): KspRun {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders += DelegatorSymbolProcessorProvider()
                this.processorOptions.putAll(processorOptions)
                // Compile the generated sources in the same run, so every test also proves that
                // the generated Kotlin is valid.
                withCompilation = true
            }
        }
        return KspRun(compilation.compile(), compilation.kspSourcesDir)
    }

    fun source(name: String, contents: String): SourceFile = SourceFile.kotlin(name, contents)

    fun javaSource(name: String, contents: String): SourceFile = SourceFile.java(name, contents)

    /** Asserts that the generated source for [qualifiedName] matches its golden file. */
    fun assertGolden(run: KspRun, qualifiedName: String) {
        val actual = run.generated(qualifiedName)
        val file = File(goldenDir, "${qualifiedName.replace('.', '/')}.kt")

        if (updateGoldens) {
            file.parentFile.mkdirs()
            file.writeText(actual + "\n")
            return
        }

        val expected = file.takeIf { it.isFile }?.readText()?.normalise()
            ?: throw AssertionError(
                "Missing golden file ${file.path}. Re-run with -Dgolden.update=true to create it.\n\n$actual",
            )

        assertThat(actual)
            .describedAs("Generated %s (re-run with -Dgolden.update=true to update)", qualifiedName)
            .isEqualTo(expected)
    }
}

internal fun String.normalise(): String = replace("\r\n", "\n").trimEnd()
