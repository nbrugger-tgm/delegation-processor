package eu.nitonfx.processors.delegation.apt;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.compile;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.generatedSource;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.source;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies which interface members do and do not end up in the generated delegator. */
@DisplayName("DelegatorProcessor: member selection")
class DelegatorProcessorMemberSelectionTest {

    private static JavaFileObject anchorFor(String interfaceName) {
        return source(
                "com.example.Anchor",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator(%s.class)
                public class Anchor {}
                """
                        .formatted(interfaceName));
    }

    @Test
    @DisplayName("skips default, static and private interface methods")
    void skipsMethodsWithBodies() {
        Compilation compilation = compile(
                source(
                        "com.example.Mixed",
                        """
                        package com.example;

                        public interface Mixed {
                            void abstractMethod();

                            default void defaultMethod() {
                                privateMethod();
                            }

                            static void staticMethod() {}

                            private void privateMethod() {}
                        }
                        """),
                anchorFor("Mixed"));

        assertThat(compilation.errors()).isEmpty();
        String generated = generatedSource(compilation, "com.example.MixedDelegator");

        assertThat(generated)
                .contains("abstractMethod")
                .doesNotContain("defaultMethod")
                .doesNotContain("staticMethod")
                .doesNotContain("privateMethod");
    }

    @Test
    @DisplayName("skips Object methods, even when the interface redeclares them")
    void skipsObjectMethods() {
        Compilation compilation = compile(
                source(
                        "com.example.Redeclaring",
                        """
                        package com.example;

                        public interface Redeclaring {
                            @Override
                            String toString();

                            @Override
                            boolean equals(Object other);

                            @Override
                            int hashCode();

                            void real();
                        }
                        """),
                anchorFor("Redeclaring"));

        // Giving an Object method a `default` body is a compile error, so these must be skipped.
        assertThat(compilation.errors()).isEmpty();
        String generated = generatedSource(compilation, "com.example.RedeclaringDelegator");

        assertThat(generated)
                .contains("real")
                .doesNotContain("toString")
                .doesNotContain("equals")
                .doesNotContain("hashCode");
    }

    @Test
    @DisplayName("delegates overloads separately")
    void delegatesOverloads() {
        Compilation compilation = compile(
                source(
                        "com.example.Overloaded",
                        """
                        package com.example;

                        public interface Overloaded {
                            String render(int value);

                            String render(String value);

                            String render(int value, String suffix);
                        }
                        """),
                anchorFor("Overloaded"));

        assertThat(compilation.errors()).isEmpty();
        String generated = generatedSource(compilation, "com.example.OverloadedDelegator");

        assertThat(generated)
                .contains("default String render(int value)")
                .contains("default String render(String value)")
                .contains("default String render(int value, String suffix)");
    }

    @Test
    @DisplayName("generates the delegator once when several anchors in a package request it")
    void deduplicatesAcrossAnchors() {
        Compilation compilation = compile(
                source(
                        "com.example.Greeter",
                        """
                        package com.example;

                        public interface Greeter {
                            String greet(String name);
                        }
                        """),
                source(
                        "com.example.FirstAnchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Greeter.class)
                        public class FirstAnchor {}
                        """),
                source(
                        "com.example.SecondAnchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Greeter.class)
                        public class SecondAnchor {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation.generatedSourceFiles().stream()
                        .filter(file -> file.getName().endsWith("GreeterDelegator.java"))
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the generated interface compiles and is implementable")
    void generatedInterfaceCompiles() {
        Compilation compilation = compile(
                source(
                        "com.example.Greeter",
                        """
                        package com.example;

                        public interface Greeter {
                            String greet(String name);
                        }
                        """),
                source(
                        "com.example.Anchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Greeter.class)
                        public class Anchor {}
                        """),
                source(
                        "com.example.Impl",
                        """
                        package com.example;

                        public record Impl(Greeter getGreeterDelegate) implements GreeterDelegator {}
                        """));

        assertThat(compilation).succeededWithoutWarnings();
    }
}
