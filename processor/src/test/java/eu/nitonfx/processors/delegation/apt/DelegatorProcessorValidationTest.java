package eu.nitonfx.processors.delegation.apt;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.compile;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.source;

/** Verifies that misuse of {@code @Delegator} is reported as a readable compile error. */
@DisplayName("DelegatorProcessor: validation")
class DelegatorProcessorValidationTest {

    @Test
    @DisplayName("rejects a class as the delegated type")
    void rejectsClassValue() {
        Compilation compilation = compile(source(
                "com.example.Anchor",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator(String.class)
                public class Anchor {}
                """));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("@Delegator value must be an interface, but 'java.lang.String' is a CLASS");
    }

    @Test
    @DisplayName("rejects an enum as the delegated type")
    void rejectsEnumValue() {
        Compilation compilation = compile(
                source(
                        "com.example.Color",
                        """
                        package com.example;

                        public enum Color { RED }
                        """),
                source(
                        "com.example.Anchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Color.class)
                        public class Anchor {}
                        """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Delegator value must be an interface");
    }

    @Test
    @DisplayName("rejects an annotation type as the delegated type")
    void rejectsAnnotationValue() {
        Compilation compilation = compile(
                source(
                        "com.example.Marker",
                        """
                        package com.example;

                        public @interface Marker {}
                        """),
                source(
                        "com.example.Anchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Marker.class)
                        public class Anchor {}
                        """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Delegator value must be an interface");
    }

    @Test
    @DisplayName("reports the error on the annotated element")
    void reportsErrorOnAnnotatedElement() {
        Compilation compilation = compile(source(
                "com.example.Anchor",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                @Delegator(String.class)
                public class Anchor {}
                """));

        assertThat(compilation).failed();
        assertThat(compilation)
                .hadErrorContaining("@Delegator value must be an interface")
                .inFile(compilation.sourceFiles().stream()
                        .filter(file -> file.getName().endsWith("Anchor.java"))
                        .findFirst()
                        .orElseThrow());
    }

    @Test
    @DisplayName("does not fail the compilation when no annotation is present")
    void noAnnotationIsANoOp() {
        Compilation compilation = compile(source(
                "com.example.Plain",
                """
                package com.example;

                public class Plain {}
                """));

        assertThat(compilation).succeededWithoutWarnings();
        org.assertj.core.api.Assertions.assertThat(compilation.generatedSourceFiles())
                .isEmpty();
    }
}
