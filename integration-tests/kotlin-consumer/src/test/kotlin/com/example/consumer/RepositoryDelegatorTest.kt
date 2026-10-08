package com.example.consumer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Exercises the `RepositoryDelegator` interface that KSP generated during this module's
 * compilation.
 *
 * Unlike the processor unit tests, which inspect generated *source*, this verifies the generated
 * code actually behaves as a delegation layer at runtime.
 */
@DisplayName("Generated RepositoryDelegator (KSP)")
class RepositoryDelegatorTest {

    /** Forwards everything; only the delegate accessor is implemented. */
    private class PassThrough(
        override val repositoryDelegate: Repository<String>,
    ) : RepositoryDelegator<String>

    /** Overrides a single function while inheriting the rest of the delegation. */
    private class UpperCasing(
        override val repositoryDelegate: Repository<String>,
    ) : RepositoryDelegator<String> {
        override fun findAll(): List<String> = repositoryDelegate.findAll().map { it.uppercase() }
    }

    @Test
    @DisplayName("forwards every abstract function to the delegate")
    fun forwardsFunctions() {
        val delegate = InMemoryRepository()
        val repository: Repository<String> = PassThrough(delegate)

        repository.save("alpha")
        repository.save("beta")

        assertThat(repository.findAll()).containsExactly("alpha", "beta")
        assertThat(repository.findById("beta")).isEqualTo("beta")
        assertThat(repository.findById("missing")).isNull()
    }

    @Test
    @DisplayName("forwards abstract properties, including var setters")
    fun forwardsProperties() {
        val delegate = InMemoryRepository()
        val repository: Repository<String> = PassThrough(delegate)

        assertThat(repository.size).isZero()
        repository.save("alpha")
        assertThat(repository.size).isEqualTo(1)

        repository.label = "renamed"
        assertThat(delegate.label).isEqualTo("renamed")
        assertThat(repository.label).isEqualTo("renamed")
    }

    @Test
    @DisplayName("keeps the interface's own default implementations working")
    fun keepsDefaultImplementations() {
        val repository: Repository<String> = PassThrough(InMemoryRepository())

        assertThat(repository.isEmpty()).isTrue()
        repository.save("alpha")
        assertThat(repository.isEmpty()).isFalse()
    }

    @Test
    @DisplayName("lets implementors override individual members")
    fun allowsSelectiveOverride() {
        val delegate = InMemoryRepository()
        delegate.save("alpha")
        val repository: Repository<String> = UpperCasing(delegate)

        assertThat(repository.findAll()).containsExactly("ALPHA")
        // Not overridden, so still delegated verbatim.
        assertThat(repository.findById("alpha")).isEqualTo("alpha")
    }
}
