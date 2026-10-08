package com.example.consumer;

import eu.nitonfx.processors.delegation.Delegator;

/**
 * Anchor requesting the generation of {@code RepositoryDelegator} in this package.
 *
 * <p>The anchor itself carries no behaviour; it only tells the processor where to put the generated
 * interface.
 */
@Delegator(Repository.class)
public final class GeneratedDelegators {
    private GeneratedDelegators() {}
}
