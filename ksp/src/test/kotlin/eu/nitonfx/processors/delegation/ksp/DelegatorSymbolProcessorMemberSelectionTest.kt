package eu.nitonfx.processors.delegation.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Verifies which interface members do and do not end up in the generated delegator. */
@DisplayName("DelegatorSymbolProcessor: member selection")
internal class DelegatorSymbolProcessorMemberSelectionTest {

    @Test
    @DisplayName("skips members that already have an implementation")
    fun skipsImplementedMembers() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Mixed.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Mixed {
                    fun abstractFunction()

                    fun defaultFunction() = Unit

                    val abstractProperty: Int

                    val derivedProperty: Int get() = abstractProperty * 2
                }

                @Delegator(Mixed::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        val generated = run.generated("com.example.MixedDelegator")

        assertThat(generated)
            .contains("abstractFunction")
            .contains("abstractProperty")
            .doesNotContain("defaultFunction")
            .doesNotContain("derivedProperty")
    }

    @Test
    @DisplayName("skips Any members, which a Kotlin interface may not implement")
    fun skipsAnyMembers() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Redeclaring.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Redeclaring {
                    override fun toString(): String

                    override fun equals(other: Any?): Boolean

                    override fun hashCode(): Int

                    fun real()
                }

                @Delegator(Redeclaring::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        // The generated interface is compiled as part of the run, so a leaked Any member would
        // surface here as a compile error.
        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        val generated = run.generated("com.example.RedeclaringDelegator")

        assertThat(generated)
            .contains("real")
            .doesNotContain("toString")
            .doesNotContain("equals")
            .doesNotContain("hashCode")
    }

    @Test
    @DisplayName("delegates overloads separately")
    fun delegatesOverloads() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Overloaded.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Overloaded {
                    fun render(value: Int): String
                    fun render(value: String): String
                    fun render(value: Int, suffix: String): String
                }

                @Delegator(Overloaded::class)
                class Anchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        val generated = run.generated("com.example.OverloadedDelegator")

        assertThat(generated)
            .contains("override fun render(`value`: Int): String")
            .contains("override fun render(`value`: String): String")
            .contains("override fun render(`value`: Int, suffix: String): String")
    }

    @Test
    @DisplayName("generates the delegator once when several anchors in a package request it")
    fun deduplicatesAcrossAnchors() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchors.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Greeter {
                    fun greet(name: String): String
                }

                @Delegator(Greeter::class)
                class FirstAnchor

                @Delegator(Greeter::class)
                class SecondAnchor
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generatedFiles.keys).containsExactly("com.example.GreeterDelegator")
    }

    @Test
    @DisplayName("the generated interface is implementable by a Kotlin class")
    fun generatedInterfaceIsImplementable() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Usage.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Greeter {
                    val language: String
                    fun greet(name: String): String
                }

                @Delegator(Greeter::class)
                class Anchor

                class Shouting(override val greeterDelegate: Greeter) : GreeterDelegator {
                    override fun greet(name: String): String =
                        greeterDelegate.greet(name).uppercase()
                }
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
    }
}
