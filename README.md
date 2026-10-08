# delegation-processor

Compile-time generation of **delegator interfaces** for Java and Kotlin.

Annotate a type with `@Delegator(SomeInterface.class)` and the processor generates
`SomeInterfaceDelegator` — an interface that implements `SomeInterface` by forwarding every
abstract member to a single delegate instance. Implementing the generated interface reduces a
wide interface to one accessor, while leaving every member individually overridable.

Two interchangeable implementations are published:

| Artifact                                               | Toolchain                     | Generates |
|--------------------------------------------------------|-------------------------------|-----------|
| `eu.nitonfx.processors:delegation-annotations`           | –                             | –         |
| `eu.nitonfx.processors:delegation-processor`             | `javac` annotation processing | Java      |
| `eu.nitonfx.processors:delegation-processor-ksp`         | Kotlin Symbol Processing      | Kotlin    |

---

## Why

Interface delegation by hand is mechanical, verbose, and silently rots when the delegated
interface grows a new method. Java has no language support for it at all. Kotlin's `by` works on
*classes*, but cannot express "a reusable interface that pre-wires the delegation and can be mixed
in" — which is what you need when several implementations should share the same forwarding
behaviour and each override a different subset.

```java
// Without
public class CachingRepository implements Repository<User> {
    private final Repository<User> delegate;
    @Override public Optional<User> findById(String id) { return delegate.findById(id); }
    @Override public List<User> findAll()               { return delegate.findAll(); }
    @Override public void save(User entity)             { delegate.save(entity); }
    @Override public int count()                        { return delegate.count(); }
    // ... and one more every time Repository grows
}

// With
public record CachingRepository(Repository<User> getRepositoryDelegate)
        implements RepositoryDelegator<User> {
    @Override public List<User> findAll() { return cached(getRepositoryDelegate()::findAll); }
}
```

---

## Install

### Java (annotation processing)

```kotlin
dependencies {
    implementation("eu.nitonfx.processors:delegation-processor:<version>")
    annotationProcessor("eu.nitonfx.processors:delegation-processor:<version>")
}
```

The `delegation-processor` artifact exposes `delegation-annotations` as an `api` dependency, so the
single coordinate above provides both `@Delegator` and the processor.

<details>
<summary>Maven</summary>

```xml
<dependency>
  <groupId>eu.nitonfx.processors</groupId>
  <artifactId>delegation-annotations</artifactId>
  <version>${delegation.version}</version>
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
    implementation("eu.nitonfx.processors:delegation-processor-ksp:<version>")
    ksp("eu.nitonfx.processors:delegation-processor-ksp:<version>")
}
```

> **Why a separate `-ksp` artifact instead of one coordinate?**
> Gradle picks variants using *attributes*, and KSP's `ksp` configuration requests exactly the same
> attributes as `annotationProcessor` (`org.gradle.usage=java-runtime`,
> `org.gradle.category=library`, `org.gradle.libraryelements=jar`). There is no attribute that
> distinguishes the two, so a single coordinate cannot resolve to the APT jar for
> `annotationProcessor(...)` and the KSP jar for `ksp(...)`. A suffixed artifact is the only
> approach that works reliably across KSP versions and for non-Kotlin consumers.

---

## Usage

```java
public interface Repository<T> {
    Optional<T> findById(String id);
    List<T> findAll();
    void save(T entity);
    int count();

    default boolean isEmpty() { return count() == 0; }
}

// Anchor: tells the processor *where* to generate. Carries no behaviour.
@Delegator(Repository.class)
public final class GeneratedDelegators {
    private GeneratedDelegators() {}
}
```

generates, in the **anchor's** package:

```java
public interface RepositoryDelegator<T> extends Repository<T> {
    Repository<T> getRepositoryDelegate();

    @Override default int count()                     { return getRepositoryDelegate().count(); }
    @Override default List<T> findAll()               { return getRepositoryDelegate().findAll(); }
    @Override default Optional<T> findById(String id) { return getRepositoryDelegate().findById(id); }
    @Override default void save(T entity)             { getRepositoryDelegate().save(entity); }
}
```

The Kotlin/KSP equivalent generates a property accessor instead, which compiles to the same
`getRepositoryDelegate()` JVM method:

```kotlin
public interface RepositoryDelegator<T> : Repository<T> {
    public val repositoryDelegate: Repository<T>

    override val size: Int
        get() = repositoryDelegate.size

    override fun findAll(): List<T> = repositoryDelegate.findAll()
}
```

### Naming

For an interface with simple name `X`:

| | Java | Kotlin |
|---|---|---|
| Generated type | `XDelegator` | `XDelegator` |
| Delegate accessor | `getXDelegate()` | `val xDelegate` (→ `getXDelegate()`) |

The generated type always goes in the **package of the annotated anchor**, not of the delegated
interface. This lets you generate a delegator for a third-party interface.

Both names are overridable:

```java
@Delegator(value = Greeter.class, name = "GreeterMixin", accessor = "delegate")
public final class Anchors {}
```

| Member | Default | Effect |
|---|---|---|
| `name` | `""` → `<X>Delegator` | simple name of the generated interface |
| `accessor` | `""` → `get<X>Delegate` / `<x>Delegate` | name of the abstract delegate member, used verbatim |

Notes:

- `name` is a *simple* name; the package is always the anchor's. A value that is not a valid
  identifier is a compile error.
- `accessor` becomes a method name in Java and a property name in Kotlin, so a shared value such
  as `"delegate"` yields `Greeter delegate()` and `val delegate: Greeter` respectively.
- In Kotlin, an accessor that collides with a soft keyword (`delegate`, `value`, `field`, ...) is
  emitted with backticks. That is a legal declaration and callers need not use backticks.
- Two anchors requesting the same generated `name` with a *different* configuration is a compile
  error rather than a silently dropped generation.

---

## Semantics

| Member | Delegated? | Why |
|---|---|---|
| `abstract` methods | yes | the point of the processor |
| `default` / `static` / `private` interface methods | no | they already have a body |
| Kotlin members with a body or accessor | no | same |
| Kotlin abstract `val` / `var` | yes | setters are delegated too |
| `equals` / `hashCode` / `toString` | no | neither Java nor Kotlin permits an interface to implement them |
| Inherited members | yes | collected across the whole superinterface hierarchy |
| Diamond-inherited members | once | collapsed by erased signature |
| Generic interfaces & methods | yes | type parameters are propagated |
| `varargs`, `throws`, `suspend`, member extension functions | yes | preserved |

Other guarantees:

- **Deterministic output.** Members are emitted in a stable sorted order, so the generated source
  is reproducible and build-cache friendly regardless of the order the compiler reports members in.
- **Idempotent per package.** Several anchors in the same package requesting the same delegator
  produce exactly one file; conflicting requests for the same name are an error.
- **Errors, not crashes.** A non-interface `value` is reported as a compile error pointing at the
  annotated element.

### Options

| Option | APT | KSP |
|---|---|---|
| Log every generated type | `-Adelegation.verbose=true` | `ksp { arg("delegation.verbose", "true") }` |

### Limitations

- Context parameters and explicit backing fields (Kotlin preview features) are not modelled; the
  KSP processor deliberately does not opt in to them.
- The KSP processor reads Kotlin declarations only.

---

## Building

Requires nothing but a JDK to bootstrap the wrapper — the build provisions its own daemon JVM and
toolchains.

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
annotations/                      @Delegator                          → delegation-annotations
processor/                        javac annotation processor          → delegation-processor
ksp/                              Kotlin Symbol Processor             → delegation-processor-ksp
integration-tests/java-consumer   consumes via annotationProcessor(...)
integration-tests/kotlin-consumer consumes via ksp(...)
build-logic/                      Gradle convention plugins
```

### Build setup

- Gradle 9.8.1 with **daemon JVM provisioning** (`gradle/gradle-daemon-jvm.properties`) — the
  correct JDK is downloaded automatically if missing.
- **Java toolchain provisioning** via the foojay resolver; built with JDK 25, compiled with
  `--release 21`, so consumers only need JDK 21+.
- **Build cache** and **configuration cache** enabled, **parallel** execution, reproducible
  archives.
- Version catalog (`gradle/libs.versions.toml`) and convention plugins in `build-logic/`.
- No incubating Gradle features are enabled.

### Testing

| Layer | Tool | What it proves |
|---|---|---|
| `processor` | [compile-testing](https://github.com/google/compile-testing) + golden files | generated Java source matches exactly; misuse produces the right diagnostics |
| `ksp` | [kotlin-compile-testing (kctfork)](https://github.com/ZacSweers/kotlin-compile-testing) + golden files | generated Kotlin source matches exactly **and compiles** (`withCompilation = true`) |
| `integration-tests/*` | JUnit 5 | the generated code actually delegates at runtime, through a real Gradle `annotationProcessor(...)` / `ksp(...)` wiring |

---

## Licence

Apache License 2.0 — see [LICENSE](LICENSE).
