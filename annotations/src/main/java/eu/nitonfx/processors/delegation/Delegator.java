package eu.nitonfx.processors.delegation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests generation of a <em>delegator interface</em> for the interface passed as {@link #value()}.
 *
 * <p>The annotated type is only an <em>anchor</em>: it tells the processor <em>where</em> (in which
 * package) the delegator should be generated. The annotated type itself is never modified, and it
 * does not have to implement {@link #value()}.
 *
 * <p>For {@code @Delegator(Greeter.class)} placed on a type in package {@code com.example}, where
 * {@code Greeter} is:
 *
 * {@snippet lang = "java":
 * public interface Greeter {
 *     String greet(String name);
 * }
 *}
 *
 * <p>the processor generates {@code com.example.GreeterDelegator}:
 *
 * {@snippet lang = "java":
 * public interface GreeterDelegator extends Greeter {
 *     Greeter getGreeterDelegate();
 *
 *     @Override
 *     default String greet(String name) {
 *         return getGreeterDelegate().greet(name);
 *     }
 * }
 *}
 *
 * <p>Implementing {@code GreeterDelegator} therefore reduces the implementation of {@code Greeter}
 * to supplying a single delegate instance, while still allowing any individual method to be
 * overridden.
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li>{@link #value()} <strong>must</strong> be an interface; anything else is a compile error.
 *   <li>The annotation may be placed on a class, record, interface or enum.
 *   <li>All inherited {@code abstract} methods are delegated. {@code default}, {@code static} and
 *       {@code private} interface methods are left alone, as are the {@link Object} methods
 *       ({@code equals}, {@code hashCode}, {@code toString}, ...), which cannot legally be given a
 *       {@code default} implementation.
 *   <li>Generic interfaces are supported: {@code @Delegator(Box.class)} for
 *       {@code interface Box<T>} generates {@code interface BoxDelegator<T> extends Box<T>}.
 *   <li>Annotating several types in the <em>same package</em> with the same {@link #value()}
 *       generates the delegator exactly once. Requesting the same generated {@link #name()} twice
 *       with a different configuration is a compile error.
 * </ul>
 *
 * <h2>Naming</h2>
 *
 * <p>Given an interface with simple name {@code X}, the generated interface is named
 * {@code XDelegator} and the abstract accessor it declares is named {@code getXDelegate()} (Java)
 * or {@code val xDelegate} (Kotlin, which compiles to the same {@code getXDelegate()} method).
 *
 * <p>Both names can be overridden with {@link #name()} and {@link #accessor()}:
 *
 * {@snippet lang = "java":
 * @Delegator(value = Greeter.class, name = "GreeterMixin", accessor = "delegate")
 * public final class Anchors {}
 *}
 *
 * <p>generates {@code interface GreeterMixin extends Greeter} with the accessor
 * {@code Greeter delegate()}.
 *
 * @see <a href="https://github.com/nitonfx/delegation-processor">Project documentation</a>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Delegator {
    /**
     * The interface to generate a delegator for.
     *
     * @return the delegated interface; must be an interface type
     */
    Class<?> value();

    /**
     * Simple name of the generated interface.
     *
     * <p>Defaults to the simple name of {@link #value()} suffixed with {@code Delegator}. The
     * generated type is always placed in the package of the annotated element, so this is a simple
     * name, not a qualified one; supplying anything that is not a valid identifier is a compile
     * error.
     *
     * @return the generated interface name, or {@code ""} for the default
     */
    String name() default "";

    /**
     * Name of the abstract member that returns the delegate.
     *
     * <p>Defaults to {@code get<X>Delegate} for Java and {@code <x>Delegate} for Kotlin, where
     * {@code X} is the simple name of {@link #value()}. The value is used verbatim: in Java it
     * becomes a method name, in Kotlin a property name. Supplying anything that is not a valid
     * identifier is a compile error.
     *
     * @return the delegate accessor name, or {@code ""} for the default
     */
    String accessor() default "";
}
