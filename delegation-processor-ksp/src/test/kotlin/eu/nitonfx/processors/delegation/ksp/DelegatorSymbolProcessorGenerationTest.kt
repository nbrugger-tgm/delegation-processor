package eu.nitonfx.processors.delegation.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies the exact shape of the generated delegator interfaces against the golden files in
 * `src/test/resources/golden/`.
 */
@DisplayName("DelegatorSymbolProcessor: generated source")
internal class DelegatorSymbolProcessorGenerationTest {

    @Test
    @DisplayName("delegates a single function interface")
    fun simpleInterface() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Greeter.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Greeter {
                    fun greet(name: String): String
                }

                @Delegator(Greeter::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.GreeterDelegator")
    }

    @Test
    @DisplayName("delegates abstract properties, both val and var")
    fun properties() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Settings.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Settings {
                    val name: String
                    var volume: Int
                    val optional: String?
                }

                @Delegator(Settings::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.SettingsDelegator")
    }

    @Test
    @DisplayName("propagates interface and function type parameters")
    fun generics() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Box.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Box<T> {
                    val value: T

                    fun set(value: T)

                    fun <R> map(mapper: (T) -> R): Box<R>
                }

                @Delegator(Box::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.BoxDelegator")
    }

    @Test
    @DisplayName("preserves suspend, vararg, nullability and Unit returns")
    fun signatureDetails() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Client.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Client {
                    suspend fun fetch(url: String): String?

                    fun log(vararg lines: String)

                    fun close()
                }

                @Delegator(Client::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.ClientDelegator")
    }

    @Test
    @DisplayName("delegates member extension functions through the delegate's scope")
    fun memberExtensionFunctions() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Formatter.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Formatter {
                    fun Int.format(): String
                }

                @Delegator(Formatter::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.FormatterDelegator")
    }

    @Test
    @DisplayName("collapses members inherited through several superinterfaces")
    fun diamondInheritance() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Child.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Base {
                    fun base()
                }

                interface Left : Base {
                    fun left()
                }

                interface Right : Base {
                    fun right()
                }

                interface Child : Left, Right

                @Delegator(Child::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.ChildDelegator")
    }

    @Test
    @DisplayName("generates into the package of the annotated anchor")
    fun generatesIntoAnchorPackage() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Greeter.kt",
                """
                package com.other

                interface Greeter {
                    fun greet(name: String): String
                }
                """.trimIndent(),
            ),
            KspFixture.source(
                "Anchor.kt",
                """
                package com.example

                import com.other.Greeter
                import eu.nitonfx.processors.delegation.Delegator

                @Delegator(Greeter::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generatedFiles.keys).containsExactly("com.example.GreeterDelegator")
    }
}
