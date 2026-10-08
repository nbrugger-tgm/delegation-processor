package com.example.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@code RepositoryDelegator} interface that the annotation processor generated
 * during this module's compilation.
 *
 * <p>Unlike the processor unit tests, which inspect generated <em>source</em>, this verifies the
 * generated code actually behaves as a delegation layer at runtime.
 */
@DisplayName("Generated RepositoryDelegator")
class RepositoryDelegatorTest {

    /** Forwards everything; only the delegate accessor is implemented. */
    private record PassThrough(Repository<String> getRepositoryDelegate)
            implements RepositoryDelegator<String> {}

    /** Overrides a single method while inheriting the rest of the delegation. */
    private record UpperCasing(Repository<String> getRepositoryDelegate)
            implements RepositoryDelegator<String> {

        @Override
        public List<String> findAll() {
            return getRepositoryDelegate().findAll().stream()
                    .map(String::toUpperCase)
                    .toList();
        }
    }

    @Test
    @DisplayName("forwards every abstract method to the delegate")
    void forwardsToDelegate() {
        InMemoryRepository delegate = new InMemoryRepository();
        Repository<String> repository = new PassThrough(delegate);

        repository.save("alpha");
        repository.save("beta");

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findAll()).containsExactly("alpha", "beta");
        assertThat(repository.findById("beta")).contains("beta");
        assertThat(repository.findById("missing")).isEmpty();
    }

    @Test
    @DisplayName("keeps the interface's own default methods working")
    void keepsDefaultMethods() {
        Repository<String> repository = new PassThrough(new InMemoryRepository());

        assertThat(repository.isEmpty()).isTrue();
        repository.save("alpha");
        assertThat(repository.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("lets implementors override individual methods")
    void allowsSelectiveOverride() {
        InMemoryRepository delegate = new InMemoryRepository();
        delegate.save("alpha");
        Repository<String> repository = new UpperCasing(delegate);

        assertThat(repository.findAll()).containsExactly("ALPHA");
        // Not overridden, so still delegated verbatim.
        assertThat(repository.findById("alpha")).contains("alpha");
    }

    @Test
    @DisplayName("is usable through the delegated interface type")
    void isAssignableToTheDelegatedInterface() {
        Repository<String> repository = new PassThrough(new InMemoryRepository());

        Optional<String> result = repository.findById("nope");

        assertThat(repository).isInstanceOf(Repository.class);
        assertThat(result).isEmpty();
    }
}
