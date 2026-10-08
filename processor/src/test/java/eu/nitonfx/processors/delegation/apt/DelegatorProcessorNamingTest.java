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

/** Covers the optional {@code name} and {@code accessor} members of {@code @Delegator}. */
@DisplayName("DelegatorProcessor: name overrides")
class DelegatorProcessorNamingTest {

    private static final JavaFileObject GREETER = source(
            "com.example.Greeter",
            """
            package com.example;

            public interface Greeter {
                String greet(String name);
            }
            """);

    private static JavaFileObject anchor(String annotation) {
        return source(
                "com.example.Anchor",
                """
                package com.example;

                import eu.nitonfx.processors.delegation.Delegator;

                %s
                public class Anchor {}
                """
                        .formatted(annotation));
    }

    @Test
    @DisplayName("uses a custom generated interface name")
    void customName() {
        Compilation compilation = compile(GREETER, anchor("@Delegator(value = Greeter.class, name = \"GreeterMixin\")"));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.GreeterMixin"))
                .contains("public interface GreeterMixin extends Greeter")
                .contains("Greeter getGreeterDelegate()");
    }

    @Test
    @DisplayName("uses a custom accessor name")
    void customAccessor() {
        Compilation compilation = compile(GREETER, anchor("@Delegator(value = Greeter.class, accessor = \"delegate\")"));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.GreeterDelegator"))
                .contains("Greeter delegate()")
                .contains("return delegate().greet(name)")
                .doesNotContain("getGreeterDelegate");
    }

    @Test
    @DisplayName("combines both overrides")
    void customNameAndAccessor() {
        Compilation compilation = compile(
                GREETER, anchor("@Delegator(value = Greeter.class, name = \"GreeterMixin\", accessor = \"delegate\")"));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.GreeterMixin"))
                .contains("public interface GreeterMixin extends Greeter")
                .contains("Greeter delegate()");
    }

    @Test
    @DisplayName("the renamed interface is still implementable")
    void renamedInterfaceCompiles() {
        Compilation compilation = compile(
                GREETER,
                anchor("@Delegator(value = Greeter.class, name = \"GreeterMixin\", accessor = \"delegate\")"),
                source(
                        "com.example.Impl",
                        """
                        package com.example;

                        public record Impl(Greeter delegate) implements GreeterMixin {}
                        """));

        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("empty overrides fall back to the defaults")
    void emptyOverridesUseDefaults() {
        Compilation compilation = compile(GREETER, anchor("@Delegator(value = Greeter.class, name = \"\", accessor = \"\")"));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.GreeterDelegator"))
                .contains("Greeter getGreeterDelegate()");
    }

    @Test
    @DisplayName("rejects a name that is not an identifier")
    void rejectsInvalidName() {
        Compilation compilation = compile(GREETER, anchor("@Delegator(value = Greeter.class, name = \"Not A Name\")"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Delegator name 'Not A Name' is not a valid Java identifier");
    }

    @Test
    @DisplayName("rejects a qualified name")
    void rejectsQualifiedName() {
        Compilation compilation = compile(GREETER, anchor("@Delegator(value = Greeter.class, name = \"com.other.Mixin\")"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("is not a valid Java identifier");
    }

    @Test
    @DisplayName("rejects an accessor that is a Java keyword")
    void rejectsKeywordAccessor() {
        Compilation compilation = compile(GREETER, anchor("@Delegator(value = Greeter.class, accessor = \"class\")"));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Delegator accessor 'class' is not a valid Java identifier");
    }

    @Test
    @DisplayName("generates distinct files for distinct names")
    void distinctNamesGenerateDistinctFiles() {
        Compilation compilation = compile(
                GREETER,
                source(
                        "com.example.First",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(value = Greeter.class, name = "GreeterMixin")
                        public class First {}
                        """),
                source(
                        "com.example.Second",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(Greeter.class)
                        public class Second {}
                        """));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(compilation.generatedSourceFiles())
                .extracting(JavaFileObject::getName)
                .anyMatch(fileName -> fileName.endsWith("GreeterMixin.java"))
                .anyMatch(fileName -> fileName.endsWith("GreeterDelegator.java"));
    }

    @Test
    @DisplayName("rejects two anchors requesting the same name with different configuration")
    void rejectsConflictingRequests() {
        Compilation compilation = compile(
                GREETER,
                source(
                        "com.example.First",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(value = Greeter.class, name = "Shared", accessor = "a")
                        public class First {}
                        """),
                source(
                        "com.example.Second",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(value = Greeter.class, name = "Shared", accessor = "b")
                        public class Second {}
                        """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Conflicting @Delegator requests for 'com.example.Shared'");
    }
}
