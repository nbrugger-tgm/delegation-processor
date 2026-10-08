package eu.nitonfx.processors.delegation.apt;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.compile;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.golden;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.source;

/**
 * Verifies the exact shape of the generated delegator interfaces against golden files in
 * {@code src/test/resources/golden/}.
 */
@DisplayName("DelegatorProcessor: generated source")
class DelegatorProcessorGenerationTest {

    private static final JavaFileObject ANCHOR = source(
            "com.example.Anchor",
            """
            package com.example;

            import eu.nitonfx.processors.delegation.Delegator;

            @Delegator(Greeter.class)
            public class Anchor {}
            """);

    @Test
    @DisplayName("delegates a single method interface")
    void simpleInterface() {
        Compilation compilation = compile(
                source(
                        "com.example.Greeter",
                        """
                        package com.example;

                        public interface Greeter {
                            String greet(String name);
                        }
                        """),
                ANCHOR);

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("com.example.GreeterDelegator")
                .hasSourceEquivalentTo(golden("com.example.GreeterDelegator"));
    }

    @Test
    @DisplayName("propagates interface and method type parameters")
    void genericInterface() {
        Compilation compilation = compile(
                source(
                        "com.example.Box",
                        """
                        package com.example;

                        import java.util.function.Function;

                        public interface Box<T> {
                            T get();

                            void set(T value);

                            <R> Box<R> map(Function<? super T, ? extends R> mapper);
                        }
                        """),
                source(
                        "com.example.BoxAnchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Box.class)
                        public class BoxAnchor {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("com.example.BoxDelegator")
                .hasSourceEquivalentTo(golden("com.example.BoxDelegator"));
    }

    @Test
    @DisplayName("preserves varargs, checked exceptions, primitives, arrays and void returns")
    void signatureDetails() {
        Compilation compilation = compile(
                source(
                        "com.example.Calculator",
                        """
                        package com.example;

                        import java.io.IOException;

                        public interface Calculator {
                            int sum(int... values);

                            double divide(double a, double b) throws ArithmeticException, IOException;

                            void reset();

                            String[] history();
                        }
                        """),
                source(
                        "com.example.CalculatorAnchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Calculator.class)
                        public class CalculatorAnchor {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("com.example.CalculatorDelegator")
                .hasSourceEquivalentTo(golden("com.example.CalculatorDelegator"));
    }

    @Test
    @DisplayName("collapses methods inherited through several superinterfaces")
    void diamondInheritance() {
        Compilation compilation = compile(
                source(
                        "com.example.Base",
                        """
                        package com.example;

                        public interface Base {
                            void base();
                        }
                        """),
                source(
                        "com.example.Left",
                        """
                        package com.example;

                        public interface Left extends Base {
                            void left();
                        }
                        """),
                source(
                        "com.example.Right",
                        """
                        package com.example;

                        public interface Right extends Base {
                            void right();
                        }
                        """),
                source(
                        "com.example.Child",
                        """
                        package com.example;

                        public interface Child extends Left, Right {}
                        """),
                source(
                        "com.example.ChildAnchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Child.class)
                        public class ChildAnchor {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedSourceFile("com.example.ChildDelegator")
                .hasSourceEquivalentTo(golden("com.example.ChildDelegator"));
    }

    @Test
    @DisplayName("generates into the package of the annotated anchor, not of the interface")
    void generatesIntoAnchorPackage() {
        Compilation compilation = compile(
                source(
                        "com.other.Greeter",
                        """
                        package com.other;

                        public interface Greeter {
                            String greet(String name);
                        }
                        """),
                source(
                        "com.example.Anchor",
                        """
                        package com.example;

                        import com.other.Greeter;
                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Greeter.class)
                        public class Anchor {}
                        """));

        assertThat(compilation).succeeded();
        assertThat(compilation).generatedSourceFile("com.example.GreeterDelegator");
    }

    @Nested
    @DisplayName("accepted anchor kinds")
    class AnchorKinds {

        @Test
        @DisplayName("record")
        void record() {
            assertAnchorAccepted("public record Anchor() {}");
        }

        @Test
        @DisplayName("interface")
        void anInterface() {
            assertAnchorAccepted("public interface Anchor {}");
        }

        @Test
        @DisplayName("enum")
        void anEnum() {
            assertAnchorAccepted("public enum Anchor { INSTANCE }");
        }

        private void assertAnchorAccepted(String anchorDeclaration) {
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
                            %s
                            """
                                    .formatted(anchorDeclaration)));

            assertThat(compilation).succeeded();
            assertThat(compilation)
                    .generatedSourceFile("com.example.GreeterDelegator")
                    .hasSourceEquivalentTo(golden("com.example.GreeterDelegator"));
        }
    }
}
