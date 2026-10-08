package com.example.consumer;

import eu.nitonfx.processors.delegation.Delegator;

/**
 * Self-delegating interface: {@code @Delegator} without a value generates
 * {@code ClockDelegator} right next to it.
 */
@Delegator
public interface Clock {

    long currentTimeMillis();

    String zoneId();
}
