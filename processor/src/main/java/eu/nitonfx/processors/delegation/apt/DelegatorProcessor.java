package eu.nitonfx.processors.delegation.apt;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import eu.nitonfx.processors.delegation.Delegator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Generates a delegator interface for every {@link Delegator} annotated type.
 *
 * <p>See {@link Delegator} for the generation contract. This processor is stateless apart from a
 * per-instance bookkeeping set used to avoid emitting the same file twice when several types in the
 * same package request the same delegator.
 *
 * <h2>Options</h2>
 *
 * <ul>
 *   <li>{@code -Adelegation.verbose=true} &mdash; log every generated type as a {@code NOTE}.
 * </ul>
 */
@AutoService(Processor.class)
@SupportedOptions(DelegatorProcessor.OPTION_VERBOSE)
public final class DelegatorProcessor extends AbstractProcessor {

    /** Processor option enabling {@code NOTE} level diagnostics. */
    public static final String OPTION_VERBOSE = "delegation.verbose";

    private static final String DELEGATOR_SUFFIX = "Delegator";
    private static final String DELEGATE_ACCESSOR_PREFIX = "get";
    private static final String DELEGATE_ACCESSOR_SUFFIX = "Delegate";
    private static final String ANNOTATION_VALUE = "value";

    private Elements elements;
    private Types types;
    private Messager messager;
    private Filer filer;
    private boolean verbose;

    /** Fully qualified names already written in this compilation, to keep the {@link Filer} happy. */
    private final Set<String> generated = new HashSet<>();

    /** Creates a processor instance. Invoked reflectively by javac. */
    public DelegatorProcessor() {
        // Required by the ServiceLoader contract.
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.verbose = Boolean.parseBoolean(processingEnv.getOptions().get(OPTION_VERBOSE));
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Delegator.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Delegator.class)) {
            processAnnotatedElement(element);
        }
        return true;
    }

    private void processAnnotatedElement(Element element) {
        ElementKind kind = element.getKind();
        if (!(element instanceof TypeElement annotatedType)
                || !(kind == ElementKind.CLASS
                        || kind == ElementKind.RECORD
                        || kind == ElementKind.INTERFACE
                        || kind == ElementKind.ENUM)) {
            error(element, "@Delegator is only applicable to classes, records, interfaces and enums, but was applied to a %s", kind);
            return;
        }

        TypeMirror delegateType = readDelegateType(annotatedType);
        if (delegateType == null) {
            return;
        }

        if (delegateType.getKind() != TypeKind.DECLARED) {
            error(annotatedType, "@Delegator value must be an interface, but was '%s'", delegateType);
            return;
        }

        Element delegateElement = types.asElement(delegateType);
        if (!(delegateElement instanceof TypeElement delegate)) {
            error(annotatedType, "Could not resolve the @Delegator value '%s' to a type", delegateType);
            return;
        }

        if (delegate.getKind() != ElementKind.INTERFACE) {
            error(annotatedType, "@Delegator value must be an interface, but '%s' is a %s", delegate.getQualifiedName(), delegate.getKind());
            return;
        }

        generateDelegator(annotatedType, delegate);
    }

    /**
     * Reads the {@code value} member of the {@link Delegator} annotation as a {@link TypeMirror}.
     *
     * <p>{@code Delegator#value()} cannot be read reflectively, because the referenced class is not
     * loadable during compilation; the annotation mirror is inspected instead.
     *
     * @return the delegate type, or {@code null} if it could not be determined (an error has been
     *     reported in that case)
     */
    private TypeMirror readDelegateType(TypeElement annotatedType) {
        String annotationName = Delegator.class.getCanonicalName();
        for (AnnotationMirror mirror : elements.getAllAnnotationMirrors(annotatedType)) {
            TypeElement annotationElement = (TypeElement) mirror.getAnnotationType().asElement();
            if (!annotationElement.getQualifiedName().contentEquals(annotationName)) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                    elements.getElementValuesWithDefaults(mirror).entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals(ANNOTATION_VALUE)
                        && entry.getValue().getValue() instanceof TypeMirror typeMirror) {
                    return typeMirror;
                }
            }
        }
        error(annotatedType, "Could not read the 'value' member of @Delegator");
        return null;
    }

    private void generateDelegator(TypeElement annotatedType, TypeElement delegate) {
        String packageName = elements.getPackageOf(annotatedType).getQualifiedName().toString();
        String simpleName = delegate.getSimpleName() + DELEGATOR_SUFFIX;
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + '.' + simpleName;

        if (!generated.add(qualifiedName)) {
            // Several anchors in the same package requested the same delegator; one file is enough.
            return;
        }

        String accessorName = DELEGATE_ACCESSOR_PREFIX + delegate.getSimpleName() + DELEGATE_ACCESSOR_SUFFIX;

        List<TypeVariableName> typeVariables =
                delegate.getTypeParameters().stream().map(TypeVariableName::get).toList();
        TypeName delegateTypeName = typeVariables.isEmpty()
                ? ClassName.get(delegate)
                : ParameterizedTypeName.get(ClassName.get(delegate), typeVariables.toArray(TypeName[]::new));

        TypeSpec.Builder builder = TypeSpec.interfaceBuilder(simpleName)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariables(typeVariables)
                .addSuperinterface(delegateTypeName)
                .addJavadoc(
                        """
                        Implements {@link $T} by forwarding every method to {@link #$L()}.

                        <p>Generated from the {@code @Delegator} annotation; do not edit.
                        """,
                        ClassName.get(delegate),
                        accessorName);

        builder.addMethod(MethodSpec.methodBuilder(accessorName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(delegateTypeName)
                .addJavadoc("@return the instance every $T method is forwarded to\n", ClassName.get(delegate))
                .build());

        for (ExecutableElement method : delegatableMethods(delegate)) {
            builder.addMethod(delegatingMethod(method, accessorName));
        }

        writeJavaFile(packageName, builder.build(), annotatedType);

        if (verbose) {
            messager.printMessage(
                    Diagnostic.Kind.NOTE,
                    "Generated %s for @Delegator on %s".formatted(qualifiedName, annotatedType.getQualifiedName()));
        }
    }

    /**
     * Collects every method that has to be implemented by an implementor of {@code delegate}.
     *
     * <p>{@code default}, {@code static} and {@code private} interface methods already have a body
     * and are skipped. {@link Object} methods are skipped as well: Java forbids giving them a
     * {@code default} implementation in an interface. Methods inherited through several
     * superinterfaces ("diamonds") are collapsed by erased signature.
     *
     * <p>The result is sorted by erased signature so that the generated source is byte for byte
     * reproducible regardless of the order in which javac reports members.
     */
    private List<ExecutableElement> delegatableMethods(TypeElement delegate) {
        TypeElement objectType = elements.getTypeElement("java.lang.Object");
        List<ExecutableElement> objectMethods =
                objectType == null ? List.of() : ElementFilter.methodsIn(objectType.getEnclosedElements());

        Map<String, ExecutableElement> bySignature = new TreeMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(elements.getAllMembers(delegate))) {
            if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
                continue;
            }
            if (overridesObjectMethod(method, delegate, objectMethods)) {
                continue;
            }
            bySignature.putIfAbsent(erasedSignature(method), method);
        }
        return List.copyOf(bySignature.values());
    }

    private boolean overridesObjectMethod(
            ExecutableElement method, TypeElement owner, List<ExecutableElement> objectMethods) {
        for (ExecutableElement objectMethod : objectMethods) {
            if (elements.overrides(method, objectMethod, owner)) {
                return true;
            }
        }
        return false;
    }

    private String erasedSignature(ExecutableElement method) {
        StringJoiner parameters = new StringJoiner(",", method.getSimpleName() + "(", ")");
        for (VariableElement parameter : method.getParameters()) {
            parameters.add(types.erasure(parameter.asType()).toString());
        }
        return parameters.toString();
    }

    private MethodSpec delegatingMethod(ExecutableElement method, String accessorName) {
        TypeName returnType = TypeName.get(method.getReturnType());
        MethodSpec.Builder builder = MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                .varargs(method.isVarArgs())
                .returns(returnType);

        method.getTypeParameters().stream().map(TypeVariableName::get).forEach(builder::addTypeVariable);
        method.getThrownTypes().stream().map(TypeName::get).forEach(builder::addException);

        List<String> argumentNames = new ArrayList<>();
        for (VariableElement parameter : method.getParameters()) {
            String name = parameter.getSimpleName().toString();
            argumentNames.add(name);
            builder.addParameter(TypeName.get(parameter.asType()), name);
        }

        String arguments = String.join(", ", argumentNames);
        if (returnType.equals(TypeName.VOID)) {
            builder.addStatement("$L().$L($L)", accessorName, method.getSimpleName(), arguments);
        } else {
            builder.addStatement("return $L().$L($L)", accessorName, method.getSimpleName(), arguments);
        }
        return builder.build();
    }

    private void writeJavaFile(String packageName, TypeSpec typeSpec, Element originatingElement) {
        JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
                .addFileComment("Generated by eu.nitonfx.processors:delegation-processor. Do not edit.")
                .skipJavaLangImports(true)
                .indent("    ")
                .build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            error(originatingElement, "Failed to write %s.%s: %s", packageName, typeSpec.name(), e.getMessage());
        }
    }

    private void error(Element element, String format, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, format.formatted(args), element);
    }
}
