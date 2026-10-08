package eu.nitonfx.processors.delegation.apt;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static com.google.testing.compile.Compiler.javac;

/** Small helpers shared by the {@link DelegatorProcessor} tests. */
final class CompilationFixture {

    private CompilationFixture() {}

    /** Compiles {@code sources} with the {@link DelegatorProcessor} attached. */
    static Compilation compile(JavaFileObject... sources) {
        return javac().withProcessors(new DelegatorProcessor()).compile(sources);
    }

    /** Creates an in-memory Java source file. */
    static JavaFileObject source(String qualifiedName, String code) {
        return JavaFileObjects.forSourceString(qualifiedName, code);
    }

    /**
     * Loads the expected generated source for {@code qualifiedName} from
     * {@code src/test/resources/golden/}.
     *
     * <p>Golden files are compared as ASTs, so formatting differences are irrelevant; they exist to
     * make the generated shape reviewable in a diff.
     */
    static JavaFileObject golden(String qualifiedName) {
        String path = "/golden/" + qualifiedName.replace('.', '/') + ".java";
        try (InputStream in = CompilationFixture.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing golden file: " + path);
            }
            return JavaFileObjects.forSourceString(qualifiedName, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the text of a generated source file, failing if it was not generated. */
    static String generatedSource(Compilation compilation, String qualifiedName) {
        JavaFileObject file = compilation
                .generatedSourceFile(qualifiedName)
                .orElseThrow(() -> new AssertionError("No source generated for " + qualifiedName));
        try {
            return file.getCharContent(false).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
