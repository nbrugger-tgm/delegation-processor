# delegation-processor

Compile-time generation of **delegator interfaces** for Java and Kotlin.

Allows generating java/kotlin compliant delegators (compared to solutions such as lombok.@Delegate) at compile time,
so that the generated code can be used in IDEs and is compatible with all build tools.

Two interchangeable implementations are published:

| Artifact                                         | Toolchain                     | Generates |
|--------------------------------------------------|-------------------------------|-----------|
| `eu.nitonfx.processors:delegation-annotations`   | –                             | –         |
| `eu.nitonfx.processors:delegation-processor`     | `javac` annotation processing | Java      |
| `eu.nitonfx.processors:delegation-processor-ksp` | Kotlin Symbol Processing      | Kotlin    |

---

## Usage

Simply annotate the interface you want to delegate with `@Delegator`:

```java
@Delegator
public interface Repository<T> {

    Optional<T> findById(String id);

    List<T> findAll();

    void save(T entity);

    int count();

    default boolean isEmpty() {
        return count() == 0;
    }
}
```

If you cannot annotate the interface (because its from a library for example), you can use the annotation on a separate
type or `package-info.java` instead.

```java
// Anchor: tells the processor *where* to generate. Carries no behaviour.
@Delegator(Repository.class)
public final class GeneratedDelegators {

    private GeneratedDelegators() {
    }
}

//package-info.java
@Delegator(Repository.class)
package eu.acme.helloworld;
```

Either way the result is:

```java
public interface RepositoryDelegator<T> extends Repository<T> {

    Repository<T> getRepositoryDelegate();

    @Override
    default int count() {
        return getRepositoryDelegate().count();
    }

    @Override
    default List<T> findAll() {
        return getRepositoryDelegate().findAll();
    }

    @Override
    default Optional<T> findById(String id) {
        return getRepositoryDelegate().findById(id);
    }

    @Override
    default void save(T entity) {
        getRepositoryDelegate().save(entity);
    }
}
```

The Kotlin/KSP equivalent generates a property accessor instead, which compiles to the same
`getRepositoryDelegate()` JVM method:

```kotlin
public interface RepositoryDelegator<T> : Repository<T> {
    val repositoryDelegate: Repository<T>

    override val size: Int
        get() = repositoryDelegate.size

    override fun findAll(): List<T> = repositoryDelegate.findAll()
}
```

---

## Why

Interface delegation by hand verbose and annoying but sometimes necessary. Java has no language support for it at all.
Kotlin's `by` has several problems depending on what you want to do, for example
- The field that "backs" the delegation is hidden and unmodifiable, you have no control over it
- The field that "backs" the delegation is set at construction time and cannot be changed later so
  ```
  class WeatherFacade(private var delegate: WeatherService) : WeatherService by delegate {
      fun setDelegate(newDelegate: WeatherService) {
          delegate = newDelegate
      }
  }
  ```
  would not work as expected since `delegate` is not the field used for delegation but a copy of the value it had at construction time.
- When debugging it is intransparent why a method is called on the delegate and not on the delegating class itself since there is no
  method actually forwarding to the delegate, the compiler just generates a method that calls the delegate directly.

---

## Install

### Java (annotation processing)

```kotlin
dependencies {
    compileOnly("eu.nitonfx.processors:delegation-annotations:<version>")
    annotationProcessor("eu.nitonfx.processors:delegation-processor:<version>")
}
```

<details>
<summary>Maven</summary>

```xml

<dependency>
    <groupId>eu.nitonfx.processors</groupId>
    <artifactId>delegation-annotations</artifactId>
    <version>${delegation.version}</version>
    <scope>provided</scope>
</dependency>
```

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>eu.nitonfx.processors</groupId>
                <artifactId>delegation-processor</artifactId>
                <version>${delegation.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

</details>

### Kotlin (KSP)

```kotlin
plugins {
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    compileOnly("eu.nitonfx.processors:delegation-annotations:<version>")
    ksp("eu.nitonfx.processors:delegation-processor-ksp:<version>")
}
```

The processor belongs on `ksp(...)` only: it is a build-time tool, not a library you compile
against. It matches `@Delegator` by qualified name, so it does not depend on the annotation
artifact at all.

---

### Naming

For an interface with simple name `X`:

|                   | Java             | Kotlin                               |
|-------------------|------------------|--------------------------------------|
| Generated type    | `XDelegator`     | `XDelegator`                         |
| Delegate accessor | `getXDelegate()` | `val xDelegate` (→ `getXDelegate()`) |

The generated type always goes in the package of the annotated element.

Both names are overridable:

```java

@Delegator(name = "GreeterMixin", accessor = "delegate")
public interface Greeter { ...
}
```

| Member     | Default                          | Effect                                              |
|------------|----------------------------------|-----------------------------------------------------|
| `value`    | the annotated type itself        | the interface to delegate                           |
| `name`     | `<X>Delegator`                   | simple name of the generated interface              |
| `accessor` | `get<X>Delegate` / `<x>Delegate` | name of the abstract delegate member, used verbatim |

Notes:

`accessor` becomes a method name in Java and a property name in Kotlin, so a shared value such
as `"delegate"` yields `Greeter delegate()` and `val delegate: Greeter` respectively.

---

## Semantics

What is delegated and what is not:

| Member                                             | Delegated? | Why                                                 |
|----------------------------------------------------|------------|-----------------------------------------------------|
| `abstract` methods                                 | yes        | the point of the processor                          |
| `default` / `static` / `private` interface methods | no         | they already have a body                            |
| Kotlin members with a body or accessor             | no         | same                                                |
| Kotlin abstract `val` / `var`                      | yes        | setters are delegated too                           |
| Inherited members                                  | yes        | collected across the whole superinterface hierarchy |
| Diamond-inherited members                          | once       | collapsed by erased signature                       |
| Generic interfaces & methods                       | yes        | type parameters are propagated                      |

Other guarantees:

- **Deterministic output.** The generated source is reproducible and build-cache friendly.

### Options

| Option                   | APT                         | KSP                                         |
|--------------------------|-----------------------------|---------------------------------------------|
| Log every generated type | `-Adelegation.verbose=true` | `ksp { arg("delegation.verbose", "true") }` |

### Limitations

- Context parameters and explicit backing fields (Kotlin preview features) are not modelled; the
  KSP processor deliberately does not opt in to them.
- The KSP processor reads Kotlin declarations only.

---

## Building

To build this project you need some JRE (not JDK) 11+

```bash
./gradlew build          # compile, test, assemble everything
./gradlew test           # tests only
./gradlew publishToMavenLocal
```

Golden files for the KSP processor can be regenerated after an intentional change:

```bash
./gradlew :ksp:test -Dgolden.update=true   # then review the diff
```

### Layout

```
delegation-annotations/           @Delegator
delegation-processor/             javac annotation processor
delegation-processor-ksp/         Kotlin Symbol Processor
integration-tests/java-consumer   consumes via annotationProcessor(...)
integration-tests/kotlin-consumer consumes via ksp(...)
build-logic/                      Gradle convention plugins
```

### Testing

| Layer                 | Tool                                                                                                   | What is tested                                                                                                         |
|-----------------------|--------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `processor`           | [compile-testing](https://github.com/google/compile-testing) + golden files                            | generated Java source matches exactly; misuse produces the right diagnostics                                           |
| `ksp`                 | [kotlin-compile-testing (kctfork)](https://github.com/ZacSweers/kotlin-compile-testing) + golden files | generated Kotlin source matches exactly **and compiles** (`withCompilation = true`)                                    |
| `integration-tests/*` | JUnit 5                                                                                                | the generated code actually delegates at runtime, through a real Gradle `annotationProcessor(...)` / `ksp(...)` wiring |

---

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).
