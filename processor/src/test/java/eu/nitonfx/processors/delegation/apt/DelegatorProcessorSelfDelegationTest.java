package eu.nitonfx.processors.delegation.apt;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.compile;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.generatedSource;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.golden;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.source;

/** Covers {@code @Delegator} without a {@code value}, which delegates the annotated interface. */
@DisplayName("DelegatorProcessor: self delegation")
class DelegatorProcessorSelfDelegationTest {

    @Test
    @DisplayName("delegates the annotated interface when no value is given")
    void delegatesAnnotatedInterface() {
        Compilation compilation = compile(source(
                "com.example.Greeter",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator
                public interface Greeter {
                    String greet(String name);
                }
                """));

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("com.example.GreeterDelegator")
                .hasSourceEquivalentTo(golden("com.example.GreeterDelegator"));
    }

    @Test
    @DisplayName("propagates type parameters of the annotated interface")
    void delegatesGenericAnnotatedInterface() {
        Compilation compilation = compile(source(
                "com.example.Box",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;
                import java.util.function.Function;

                @Delegator
                public interface Box<T> {
                    T get();

                    void set(T value);

                    <R> Box<R> map(Function<? super T, ? extends R> mapper);
                }
                """));

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("com.example.BoxDelegator")
                .hasSourceEquivalentTo(golden("com.example.BoxDelegator"));
    }

    @Test
    @DisplayName("honours name and accessor overrides")
    void honoursNameOverrides() {
        Compilation compilation = compile(source(
                "com.example.Greeter",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator(name = "GreeterMixin", accessor = "delegate")
                public interface Greeter {
                    String greet(String name);
                }
                """));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.GreeterMixin"))
                .contains("public interface GreeterMixin extends Greeter")
                .contains("Greeter delegate()");
    }

    @Test
    @DisplayName("the generated interface is implementable")
    void generatedInterfaceCompiles() {
        Compilation compilation = compile(
                source(
                        "com.example.Greeter",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator
                        public interface Greeter {
                            String greet(String name);
                        }
                        """),
                source(
                        "com.example.Impl",
                        """
                        package com.example;

                        public record Impl(Greeter getGreeterDelegate) implements GreeterDelegator {}
                        """));

        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("an explicit value still wins over self delegation")
    void explicitValueWins() {
        Compilation compilation = compile(
                source(
                        "com.example.Other",
                        """
                        package com.example;

                        public interface Other {
                            void ping();
                        }
                        """),
                source(
                        "com.example.Greeter",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Other.class)
                        public interface Greeter {
                            String greet(String name);
                        }
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("com.example.OtherDelegator");
        org.assertj.core.api.Assertions.assertThat(compilation.generatedSourceFiles())
                .noneMatch(file -> file.getName().endsWith("GreeterDelegator.java"));
    }

    @Test
    @DisplayName("rejects a bare @Delegator on a class")
    void rejectsBareAnnotationOnClass() {
        Compilation compilation = compile(source(
                "com.example.Anchor",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator
                public class Anchor {}
                """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Delegator without a value delegates the annotated type itself");
        assertThat(compilation).hadErrorContaining("'com.example.Anchor' is a CLASS");
    }

    @Test
    @DisplayName("rejects a bare @Delegator on a record")
    void rejectsBareAnnotationOnRecord() {
        Compilation compilation = compile(source(
                "com.example.Anchor",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator
                public record Anchor() {}
                """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("must be placed on an interface");
    }

    @Test
    @DisplayName("rejects a bare @Delegator on an enum")
    void rejectsBareAnnotationOnEnum() {
        Compilation compilation = compile(source(
                "com.example.Anchor",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator
                public enum Anchor { INSTANCE }
                """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("must be placed on an interface");
    }
}
