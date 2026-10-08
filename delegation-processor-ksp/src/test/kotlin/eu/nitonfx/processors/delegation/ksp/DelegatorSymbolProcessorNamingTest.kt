package eu.nitonfx.processors.delegation.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Covers the optional `name` and `accessor` members of `@Delegator`. */
@DisplayName("DelegatorSymbolProcessor: name overrides")
internal class DelegatorSymbolProcessorNamingTest {

    private fun compileWith(annotation: String, extra: String = "") = KspFixture.compile(
        KspFixture.source(
            "Greeter.kt",
            """
            package com.example

            import eu.nitonfx.processors.delegation.Delegator

            interface Greeter {
                val language: String
                fun greet(name: String): String
            }

            $annotation
            class Anchor

            $extra
            """.trimIndent(),
        ),
    )

    @Test
    @DisplayName("uses a custom generated interface name")
    fun customName() {
        val run = compileWith("""@Delegator(value = Greeter::class, name = "GreeterMixin")""")

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterMixin"))
            .contains("public interface GreeterMixin : Greeter")
            .contains("public val greeterDelegate: Greeter")
    }

    @Test
    @DisplayName("uses a custom accessor name")
    fun customAccessor() {
        val run = compileWith("""@Delegator(value = Greeter::class, accessor = "backing")""")

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterDelegator"))
            .contains("public val backing: Greeter")
            .contains("backing.greet(name)")
            .doesNotContain("greeterDelegate")
    }

    @Test
    @DisplayName("escapes an accessor that is a Kotlin soft keyword")
    fun escapesSoftKeywordAccessor() {
        // `delegate` is an annotation use-site target, so KotlinPoet emits it with backticks.
        // That is still a legal declaration and callers may refer to it without backticks.
        val run = compileWith(
            annotation = """@Delegator(value = Greeter::class, accessor = "delegate")""",
            extra = """
                class PassThrough(override val delegate: Greeter) : GreeterDelegator
            """.trimIndent(),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterDelegator"))
            .contains("public val `delegate`: Greeter")
    }

    @Test
    @DisplayName("combines both overrides and stays implementable")
    fun customNameAndAccessor() {
        val run = compileWith(
            annotation = """@Delegator(value = Greeter::class, name = "GreeterMixin", accessor = "backing")""",
            extra = """
                class Shouting(override val backing: Greeter) : GreeterMixin {
                    override fun greet(name: String): String = backing.greet(name).uppercase()
                }
            """.trimIndent(),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterMixin"))
            .contains("public interface GreeterMixin : Greeter")
            .contains("public val backing: Greeter")
    }

    @Test
    @DisplayName("empty overrides fall back to the defaults")
    fun emptyOverridesUseDefaults() {
        val run = compileWith("""@Delegator(value = Greeter::class, name = "", accessor = "")""")

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterDelegator"))
            .contains("public val greeterDelegate: Greeter")
    }

    @Test
    @DisplayName("rejects a name that is not an identifier")
    fun rejectsInvalidName() {
        val run = compileWith("""@Delegator(value = Greeter::class, name = "Not A Name")""")

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("@Delegator name 'Not A Name' is not a valid identifier")
    }

    @Test
    @DisplayName("rejects a qualified name")
    fun rejectsQualifiedName() {
        val run = compileWith("""@Delegator(value = Greeter::class, name = "com.other.Mixin")""")

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("is not a valid identifier")
    }

    @Test
    @DisplayName("rejects an accessor that is not an identifier")
    fun rejectsInvalidAccessor() {
        val run = compileWith("""@Delegator(value = Greeter::class, accessor = "my delegate")""")

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("@Delegator accessor 'my delegate' is not a valid identifier")
    }

    @Test
    @DisplayName("rejects two anchors requesting the same name with different configuration")
    fun rejectsConflictingRequests() {
        val run = KspFixture.compile(
            KspFixture.source(
                "Anchors.kt",
                """
                package com.example

                import eu.nitonfx.processors.delegation.Delegator

                interface Greeter {
                    fun greet(name: String): String
                }

                @Delegator(value = Greeter::class, name = "Shared", accessor = "a")
                class First

                @Delegator(value = Greeter::class, name = "Shared", accessor = "b")
                class Second
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("Conflicting @Delegator requests for 'com.example.Shared'")
    }
}
