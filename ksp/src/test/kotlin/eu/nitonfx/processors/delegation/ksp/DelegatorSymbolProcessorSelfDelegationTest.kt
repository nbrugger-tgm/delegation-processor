package eu.nitonfx.processors.delegation.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Covers `@Delegator` without a `value`, which delegates the annotated interface. */
@DisplayName("DelegatorSymbolProcessor: self delegation")
internal class DelegatorSymbolProcessorSelfDelegationTest {

    @Test
    @DisplayName("delegates the annotated interface when no value is given")
    fun delegatesAnnotatedInterface() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Greeter.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                @Delegator
                interface Greeter {
                    fun greet(name: String): String
                }
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.GreeterDelegator")
    }

    @Test
    @DisplayName("propagates type parameters of the annotated interface")
    fun delegatesGenericAnnotatedInterface() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Box.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                @Delegator
                interface Box<T> {
                    val `value`: T

                    fun `set`(`value`: T)

                    fun <R> map(mapper: (T) -> R): Box<R>
                }
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        KspFixture.assertGolden(run, "com.example.BoxDelegator")
    }

    @Test
    @DisplayName("honours name and accessor overrides")
    fun honoursNameOverrides() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Greeter.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                @Delegator(name = "GreeterMixin", accessor = "backing")
                interface Greeter {
                    fun greet(name: String): String
                }

                class PassThrough(override val backing: Greeter) : GreeterMixin
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterMixin"))
            .contains("public interface GreeterMixin : Greeter")
            .contains("public val backing: Greeter")
    }

    @Test
    @DisplayName("the generated interface is implementable")
    fun generatedInterfaceIsImplementable() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Greeter.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                @Delegator
                interface Greeter {
                    fun greet(name: String): String
                }

                class Shouting(override val greeterDelegate: Greeter) : GreeterDelegator {
                    override fun greet(name: String): String = greeterDelegate.greet(name).uppercase()
                }
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
    }

    @Test
    @DisplayName("an explicit value still wins over self delegation")
    fun explicitValueWins() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Greeter.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Other {
                    fun ping()
                }

                @Delegator(Other::class)
                interface Greeter {
                    fun greet(name: String): String
                }
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generatedFiles.keys).containsExactly("com.example.OtherDelegator")
    }

    @Test
    @DisplayName("rejects a bare @Delegator on a class")
    fun rejectsBareAnnotationOnClass() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchor.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                @Delegator
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages)
            .contains("@Delegator without a value delegates the annotated type itself")
            .contains("com.example.Anchor is a CLASS")
    }

    @Test
    @DisplayName("rejects a bare @Delegator on an object")
    fun rejectsBareAnnotationOnObject() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchor.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                @Delegator
                object Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("must be placed on an interface")
    }

    @Test
    @DisplayName("rejects a bare @Delegator on an enum")
    fun rejectsBareAnnotationOnEnum() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchor.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                @Delegator
                enum class Anchor { INSTANCE }
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("must be placed on an interface")
    }
}
