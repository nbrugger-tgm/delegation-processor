package eu.nitonfx.processors.delegation.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DelegatorSymbolProcessor: package anchors")
internal class DelegatorSymbolProcessorPackageAnchorTest {

    @Test
    @DisplayName("generates a delegator from a Java package-info anchor")
    fun generatesFromPackageInfo() {
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
            KspFixture.javaSource(
                "package-info.java",
                """
                @eu.nitonfx.processors.delegation.Delegator(com.other.Greeter.class)
                package com.example;
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterDelegator"))
            .contains("package com.example")
            .contains("import com.other.Greeter")
            .contains("public interface GreeterDelegator : Greeter")
    }

    @Test
    @DisplayName("supports custom names and accessors")
    fun supportsNamingOverrides() {
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
            KspFixture.javaSource(
                "package-info.java",
                """
                @eu.nitonfx.processors.delegation.Delegator(
                    value = com.other.Greeter.class,
                    name = "GreeterMixin",
                    accessor = "backing")
                package com.example;
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).describedAs(run.messages).isTrue()
        assertThat(run.generated("com.example.GreeterMixin"))
            .contains("public interface GreeterMixin : Greeter")
            .contains("public val backing: Greeter")
    }

    @Test
    @DisplayName("rejects an omitted value because packages cannot self-delegate")
    fun rejectsOmittedValue() {
        val run = KspFixture.compile(
            KspFixture.javaSource(
                "package-info.java",
                """
                @eu.nitonfx.processors.delegation.Delegator
                package com.example;
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("@Delegator on a package requires an explicit interface value")
    }

    @Test
    @DisplayName("rejects a non-interface value")
    fun rejectsClassValue() {
        val run = KspFixture.compile(
            KspFixture.javaSource(
                "package-info.java",
                """
                @eu.nitonfx.processors.delegation.Delegator(String.class)
                package com.example;
                """.trimIndent(),
            ),
        )

        assertThat(run.succeeded).isFalse()
        assertThat(run.messages).contains("@Delegator value must be an interface")
    }
}
