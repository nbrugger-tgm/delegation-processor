package eu.nitonfx.processors.delegation.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Verifies that misuse of `@Delegator` is reported as a readable compile error. */
@DisplayName("DelegatorSymbolProcessor: validation")
internal class DelegatorSymbolProcessorValidationTest {

    @Test
    @DisplayName("rejects a class as the delegated type")
    fun rejectsClassValue() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchor.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                class NotAnInterface

                @Delegator(NotAnInterface::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("@Delegator value must be an interface")
        assertThat(run.generatedFiles).isEmpty()
    }

    @Test
    @DisplayName("rejects an object as the delegated type")
    fun rejectsObjectValue() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchor.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                object Singleton

                @Delegator(Singleton::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("@Delegator value must be an interface")
    }

    @Test
    @DisplayName("rejects an enum as the delegated type")
    fun rejectsEnumValue() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchor.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                enum class Color { RED }

                @Delegator(Color::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("@Delegator value must be an interface")
    }

    @Test
    @DisplayName("is a no-op when the annotation is absent")
    fun noAnnotationIsANoOp() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Plain.kt",
                """
                package com.example

                class Plain
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generatedFiles).isEmpty()
    }

    @Test
    @DisplayName("logs the generated type when the verbose option is set")
    fun verboseOption() {
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
            processorOptions = mapOf(DelegatorSymbolProcessorProvider.OPTION_VERBOSE to "true"),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.messages).contains("Generated com.example.GreeterDelegator")
    }
}
