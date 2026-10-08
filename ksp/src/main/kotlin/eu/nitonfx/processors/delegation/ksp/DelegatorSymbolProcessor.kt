package eu.nitonfx.processors.delegation.ksp

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Generates a Kotlin delegator interface for every `@Delegator` annotated declaration.
 *
 * For `@Delegator(Greeter::class)` on a declaration in package `com.example`, where `Greeter` is
 *
 * ```kotlin
 * interface Greeter {
 *     val language: String
 *     fun greet(name: String): String
 * }
 * ```
 *
 * this generates `com.example.GreeterDelegator`:
 *
 * ```kotlin
 * public interface GreeterDelegator : Greeter {
 *     public val greeterDelegate: Greeter
 *
 *     override val language: String
 *         get() = greeterDelegate.language
 *
 *     override fun greet(name: String): String = greeterDelegate.greet(name)
 * }
 * ```
 *
 * Both abstract functions and abstract properties are delegated. Members that already have a body
 * (`fun` with a default implementation, properties with accessors) are left to the interface, and
 * the `Any` members `equals`/`hashCode`/`toString` are skipped because Kotlin does not allow an
 * interface to implement them.
 *
 * Kotlin's built-in `by` delegation covers the common case of delegating a *class*; this processor
 * exists for the case where the delegation has to be expressed as a reusable *interface* that
 * implementors can mix in and selectively override.
 */
internal class DelegatorSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val verbose: Boolean,
) : SymbolProcessor {

    /** Configurations already generated, keyed by qualified name. */
    private val generated = mutableMapOf<String, GeneratedDelegator>()

    /** What was generated for a qualified name, used to detect conflicting requests. */
    private data class GeneratedDelegator(val delegate: String, val accessor: String)

    /** The resolved form of a single `@Delegator` annotation. */
    private data class DelegatorConfig(
        /** `null` when `value` was omitted, meaning "the annotated type itself". */
        val delegate: KSClassDeclaration?,
        val name: String,
        val accessor: String,
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(DELEGATOR_ANNOTATION).toList()
        // `enableNewFeatures = false`: this processor does not model context parameters or
        // explicit backing fields, so it deliberately does not opt in to them.
        val (valid, deferred) = symbols.partition {
            it.validate(predicate = { _, _ -> true }, enableNewFeatures = false)
        }

        valid.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                generateFor(symbol)
            } else {
                logger.error("@Delegator is only applicable to class-like declarations", symbol)
            }
        }
        return deferred
    }

    private fun generateFor(anchor: KSClassDeclaration) {
        val config = readConfig(anchor) ?: return
        val delegate = config.delegate ?: anchor

        if (delegate.classKind != ClassKind.INTERFACE) {
            if (config.delegate == null) {
                logger.error(
                    "@Delegator without a value delegates the annotated type itself, so it must be " +
                        "placed on an interface, but ${anchor.qualifiedName?.asString()} is a ${anchor.classKind}",
                    anchor,
                )
            } else {
                logger.error(
                    "@Delegator value must be an interface, but ${delegate.qualifiedName?.asString()} " +
                        "is a ${delegate.classKind}",
                    anchor,
                )
            }
            return
        }

        if (config.name.isNotEmpty() && !config.name.isValidIdentifier()) {
            logger.error("@Delegator name '${config.name}' is not a valid identifier", anchor)
            return
        }
        if (config.accessor.isNotEmpty() && !config.accessor.isValidIdentifier()) {
            logger.error("@Delegator accessor '${config.accessor}' is not a valid identifier", anchor)
            return
        }

        val packageName = anchor.packageName.asString()
        val delegateSimpleName = delegate.simpleName.asString()
        val generatedName = config.name.ifEmpty { delegateSimpleName + DELEGATOR_SUFFIX }
        val qualifiedName = if (packageName.isEmpty()) generatedName else "$packageName.$generatedName"

        val accessorName = config.accessor.ifEmpty {
            delegateSimpleName.replaceFirstChar { it.lowercase() } + DELEGATE_SUFFIX
        }

        val request = GeneratedDelegator(delegate.qualifiedName?.asString().orEmpty(), accessorName)
        generated.putIfAbsent(qualifiedName, request)?.let { previous ->
            if (previous != request) {
                logger.error(
                    "Conflicting @Delegator requests for '$qualifiedName': already generated for " +
                        "'${previous.delegate}' with accessor '${previous.accessor}'",
                    anchor,
                )
            }
            // Otherwise several anchors requested the same delegator; one file is enough.
            return
        }

        val typeParameterResolver = delegate.typeParameters.toTypeParameterResolver()
        val delegateType = delegate.asSuperinterfaceType(typeParameterResolver)

        val typeSpec = TypeSpec.interfaceBuilder(generatedName)
            .addKdoc(
                "Implements [%T] by forwarding every member to [%N].\n\n" +
                    "Generated from the `@Delegator` annotation; do not edit.",
                delegate.toClassName(),
                accessorName,
            )
            .addTypeVariables(delegate.typeParameters.map { it.toTypeVariableName(typeParameterResolver) })
            .addSuperinterface(delegateType)
            .addProperty(
                PropertySpec.builder(accessorName, delegateType)
                    .addKdoc("The instance every [%T] member is forwarded to.", delegate.toClassName())
                    .build(),
            )
            .apply {
                // Sorted by signature so that the generated source is reproducible regardless of
                // the order in which KSP reports members.
                delegate.getAllProperties()
                    .filter { it.isAbstract() }
                    .sortedBy { it.simpleName.asString() }
                    .forEach { addProperty(delegatingProperty(it, accessorName, typeParameterResolver)) }

                delegate.getAllFunctions()
                    .filter { it.isAbstract && !it.isConstructor() && !it.isAnyMember() }
                    .sortedBy { it.signatureKey() }
                    .forEach { addFunction(delegatingFunction(it, accessorName, typeParameterResolver)) }
            }
            .build()

        FileSpec.builder(packageName, generatedName)
            .addFileComment("Generated by eu.nitonfx.processors:delegation-processor-ksp. Do not edit.")
            .addType(typeSpec)
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = false, *listOfNotNull(anchor.containingFile).toTypedArray()))

        if (verbose) {
            logger.info("Generated $qualifiedName for @Delegator on ${anchor.qualifiedName?.asString()}")
        }
    }

    /**
     * Reads the `@Delegator` annotation off [anchor].
     *
     * `value` is declared in Java as `Class<?>`, which KSP surfaces as a [KSType]. An omitted
     * value (the [SELF_SENTINEL] marker) is reported as a `null` delegate.
     */
    private fun readConfig(anchor: KSClassDeclaration): DelegatorConfig? {
        val annotation: KSAnnotation? = anchor.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == DELEGATOR_ANNOTATION
        }
        if (annotation == null) {
            logger.error("Could not read the @Delegator annotation", anchor)
            return null
        }

        fun argument(name: String): Any? = annotation.arguments.firstOrNull { it.name?.asString() == name }?.value

        val value = argument(MEMBER_VALUE)
        val declaration = (value as? KSType)?.declaration as? KSClassDeclaration
        if (value != null && declaration == null) {
            logger.error("Could not resolve the @Delegator value '$value' to a type", anchor)
            return null
        }

        val isSelf = declaration == null ||
            declaration.qualifiedName?.asString() == SELF_SENTINEL

        return DelegatorConfig(
            delegate = declaration.takeUnless { isSelf },
            name = (argument(MEMBER_NAME) as? String).orEmpty().trim(),
            accessor = (argument(MEMBER_ACCESSOR) as? String).orEmpty().trim(),
        )
    }

    private fun delegatingProperty(
        property: KSPropertyDeclaration,
        accessorName: String,
        parentResolver: TypeParameterResolver,
    ): PropertySpec {
        val name = property.simpleName.asString()
        val type = property.type.toTypeName(parentResolver)

        return PropertySpec.builder(name, type, KModifier.OVERRIDE)
            .mutable(property.isMutable)
            .getter(
                FunSpec.getterBuilder()
                    .addStatement("return %N.%N", accessorName, name)
                    .build(),
            )
            .apply {
                if (property.isMutable) {
                    setter(
                        FunSpec.setterBuilder()
                            .addParameter("value", type)
                            .addStatement("%N.%N = value", accessorName, name)
                            .build(),
                    )
                }
            }
            .build()
    }

    private fun delegatingFunction(
        function: KSFunctionDeclaration,
        accessorName: String,
        parentResolver: TypeParameterResolver,
    ): FunSpec {
        val resolver = function.typeParameters.toTypeParameterResolver(parentResolver)
        val name = function.simpleName.asString()

        val builder = FunSpec.builder(name).addModifiers(KModifier.OVERRIDE)
        if (Modifier.SUSPEND in function.modifiers) builder.addModifiers(KModifier.SUSPEND)

        builder.addTypeVariables(function.typeParameters.map { it.toTypeVariableName(resolver) })

        val extensionReceiver = function.extensionReceiver?.toTypeName(resolver)
        extensionReceiver?.let(builder::receiver)

        val returnType = function.returnType?.toTypeName(resolver) ?: UNIT
        builder.returns(returnType)

        function.parameters.forEach { parameter ->
            val modifiers = buildList {
                if (parameter.isVararg) add(KModifier.VARARG)
                if (parameter.isCrossInline) add(KModifier.CROSSINLINE)
                if (parameter.isNoInline) add(KModifier.NOINLINE)
            }
            builder.addParameter(
                ParameterSpec.builder(
                    parameter.name?.asString() ?: "arg${function.parameters.indexOf(parameter)}",
                    parameter.type.toTypeName(resolver),
                    modifiers,
                ).build(),
            )
        }

        val arguments = CodeBlock.builder()
        function.parameters.forEachIndexed { index, parameter ->
            if (index > 0) arguments.add(", ")
            if (parameter.isVararg) arguments.add("*")
            arguments.add("%N", parameter.name?.asString() ?: "arg$index")
        }

        val call = CodeBlock.of("%N.%N(%L)", accessorName, name, arguments.build())
        when {
            // A member extension function must be invoked with the delegate as *dispatch* receiver
            // while keeping the original extension receiver, hence the `run` block.
            extensionReceiver != null -> {
                val extensionCall =
                    CodeBlock.of("%N.run { receiver.%N(%L) }", accessorName, name, arguments.build())
                builder.addStatement("val receiver = this")
                if (returnType == UNIT) {
                    builder.addStatement("%L", extensionCall)
                } else {
                    builder.addStatement("return %L", extensionCall)
                }
            }

            returnType == UNIT -> builder.addStatement("%L", call)
            else -> builder.addStatement("return %L", call)
        }
        return builder.build()
    }

    /** `equals`, `hashCode` and `toString` cannot be implemented by a Kotlin interface. */
    private fun KSFunctionDeclaration.isAnyMember(): Boolean =
        when (simpleName.asString()) {
            "equals" -> parameters.size == 1
            "hashCode", "toString" -> parameters.isEmpty()
            else -> false
        }

    /** Stable ordering key; also collapses nothing, overloads keep distinct keys. */
    private fun KSFunctionDeclaration.signatureKey(): String =
        parameters.joinToString(
            separator = ",",
            prefix = "${simpleName.asString()}(",
            postfix = ")",
        ) { it.type.resolve().declaration.qualifiedName?.asString() ?: it.type.toString() }

    private fun KSClassDeclaration.asSuperinterfaceType(resolver: TypeParameterResolver): TypeName {
        val className: ClassName = toClassName()
        if (typeParameters.isEmpty()) return className
        val variables: List<TypeVariableName> = typeParameters.map { it.toTypeVariableName(resolver) }
        return className.parameterizedBy(variables)
    }

    private companion object {
        const val DELEGATOR_ANNOTATION = "eu.nitonfx.processors.delegation.Delegator"
        const val DELEGATOR_SUFFIX = "Delegator"
        const val DELEGATE_SUFFIX = "Delegate"
        const val MEMBER_VALUE = "value"
        const val MEMBER_NAME = "name"
        const val MEMBER_ACCESSOR = "accessor"

        /** Qualified name of the marker standing in for an omitted `value`. */
        const val SELF_SENTINEL = "eu.nitonfx.processors.delegation.Delegator.Self"

        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

        fun String.isValidIdentifier(): Boolean = IDENTIFIER.matches(this)
    }
}
