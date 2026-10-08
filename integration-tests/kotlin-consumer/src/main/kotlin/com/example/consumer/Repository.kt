package com.example.consumer

import eu.nitonfx.processors.delegation.Delegator

/** A small generic interface used to exercise the generated delegators end to end. */
interface Repository<T> {

    /** Number of stored entities. */
    val size: Int

    /** Mutable label, to cover `var` delegation. */
    var label: String

    fun findById(id: String): T?

    fun findAll(): List<T>

    fun save(entity: T)

    fun isEmpty(): Boolean = size == 0
}

/**
 * Anchor requesting generation of `RepositoryDelegator` in this package.
 *
 * The anchor carries no behaviour; it only tells the processor where to put the generated
 * interface.
 */
@Delegator(Repository::class)
class GeneratedDelegators private constructor()

/** Trivial in-memory [Repository] used as the delegate in the integration tests. */
class InMemoryRepository : Repository<String> {
    private val entities = mutableListOf<String>()

    override val size: Int get() = entities.size

    override var label: String = "default"

    override fun findById(id: String): String? = entities.firstOrNull { it == id }

    override fun findAll(): List<String> = entities.toList()

    override fun save(entity: String) {
        entities += entity
    }
}
