package eu.nitonfx.processors.delegation.apt;

import com.google.testing.compile.Compilation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.compile;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.generatedSource;
import static eu.nitonfx.processors.delegation.apt.CompilationFixture.source;

@DisplayName("DelegatorProcessor: package anchors")
class DelegatorProcessorPackageAnchorTest {

    @Test
    @DisplayName("generates a delegator in the annotated package")
    void generatesFromPackageInfo() {
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
                        "com.example.package-info",
                        """
                        @eu.nitonfx.processors.delegation.Delegator(com.other.Greeter.class)
                        package com.example;
                        """));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.GreeterDelegator"))
                .contains("package com.example")
                .contains("import com.other.Greeter")
                .contains("public interface GreeterDelegator extends Greeter");
    }

    @Test
    @DisplayName("supports custom names and accessors")
    void supportsNamingOverrides() {
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
                        "com.example.package-info",
                        """
                        @eu.nitonfx.processors.delegation.Delegator(
                                value = com.other.Greeter.class,
                                name = "GreeterMixin",
                                accessor = "delegate")
                        package com.example;
                        """));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.GreeterMixin"))
                .contains("public interface GreeterMixin extends Greeter")
                .contains("Greeter delegate()");
    }

    @Test
    @DisplayName("propagates generic type parameters")
    void supportsGenericInterfaces() {
        Compilation compilation = compile(
                source(
                        "com.other.Box",
                        """
                        package com.other;

                        public interface Box<T> {
                            T get();
                            void set(T value);
                        }
                        """),
                source(
                        "com.example.package-info",
                        """
                        @eu.nitonfx.processors.delegation.Delegator(com.other.Box.class)
                        package com.example;
                        """));

        assertThat(compilation).succeeded();
        org.assertj.core.api.Assertions.assertThat(generatedSource(compilation, "com.example.BoxDelegator"))
                .contains("interface BoxDelegator<T> extends Box<T>")
                .contains("Box<T> getBoxDelegate()");
    }

    @Test
    @DisplayName("rejects an omitted value because packages cannot self-delegate")
    void rejectsOmittedValue() {
        Compilation compilation = compile(source(
                "com.example.package-info",
                """
                @eu.nitonfx.processors.delegation.Delegator
                package com.example;
                """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("must be placed on an interface");
    }

    @Test
    @DisplayName("rejects a non-interface value")
    void rejectsClassValue() {
        Compilation compilation = compile(source(
                "com.example.package-info",
                """
                @eu.nitonfx.processors.delegation.Delegator(String.class)
                package com.example;
                """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@Delegator value must be an interface");
    }

    @Test
    @DisplayName("detects conflicts with type anchors in the same package")
    void detectsConflictsWithTypeAnchors() {
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
                        "com.example.package-info",
                        """
                        @eu.nitonfx.processors.delegation.Delegator(
                                value = com.example.Greeter.class,
                                name = "Shared",
                                accessor = "first")
                        package com.example;
                        """),
                source(
                        "com.example.Anchor",
                        """
                        package com.example;

                        import eu.nitonfx.processors.delegation.Delegator;

                        @Delegator(value = Greeter.class, name = "Shared", accessor = "second")
                        public class Anchor {}
                        """));

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("Conflicting @Delegator requests for 'com.example.Shared'");
    }
}
